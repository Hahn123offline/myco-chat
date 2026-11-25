package dev.myco.core.llm

import dev.myco.core.MycoHome
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.RetryProfile
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** 可编程 HTTP 服务器：每次请求调用 responder(attempt) 返回 (status, body)。 */
private class CountingHttpServer(
    private val responder: (attempt: Int) -> Pair<Int, String>,
) : AutoCloseable {
    private val server = ServerSocket(0)
    private val counter = AtomicInteger(0)
    val port: Int = server.localPort

    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) {
            runCatching {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
                    reader.readLine() // request line
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val attempt = counter.incrementAndGet()
                    val (status, body) = responder(attempt)
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    val out = socket.getOutputStream()
                    val reason = if (status == 200) "OK" else "ERROR"
                    val header = "HTTP/1.1 $status $reason\r\nContent-Type: text/event-stream\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray(StandardCharsets.ISO_8859_1))
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    fun requests(): Int = counter.get()

    override fun close() {
        runCatching { server.close() }
        worker.join(1000)
    }
}

class UsageCostTest {
    @Test
    fun `computes cost from pricing and cached tokens`() {
        val usage = dev.myco.core.spec.Usage(
            inputTokens = 1000, outputTokens = 200, cachedTokens = 500,
        )
        val pricing = dev.myco.core.spec.Pricing(
            currency = "USD",
            inputPerMillion = 1.0,
            outputPerMillion = 2.0,
            cachedInputPerMillion = 0.1,
        )
        val cost = UsageCost.compute(usage, pricing)!!
        // (500*1 + 500*0.1 + 200*2) / 1_000_000
        assertEquals(0.00095, cost, 1e-9)
    }

    @Test
    fun `no pricing means no cost guess`() {
        val usage = dev.myco.core.spec.Usage(inputTokens = 10, outputTokens = 10)
        assertEquals(null, UsageCost.compute(usage, null))
    }
}

class LlmCancellationTest {
    @Test
    fun `cancelling collection aborts the request without a Done`() = runBlocking {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\n\n"
        CountingHttpServer { attempt ->
            if (attempt == 1) {
                Thread.sleep(5_000) // 故意拖住：取消要能立刻断开
            }
            Pair(200, body)
        }.use { srv ->
            val provider = ProviderProfile(
                id = "local", kind = "openai-completions", baseUrl = "http://127.0.0.1:${srv.port}",
                auth = AuthProfile.None,
                models = listOf(ModelProfile(id = "m", contextWindow = 1024, maxTokens = 128)),
            )
            val client = OpenAiCompatibleClient(provider, CredentialResolver(MycoHome(java.nio.file.Files.createTempDirectory("myco-cancel")), emptyMap()))
            val received = mutableListOf<ChatChunk>()
            val job = launch {
                client.stream(ChatRequest("m", listOf(ChatMessage("user", "hi")))).collect { received += it }
            }
            kotlinx.coroutines.delay(300)
            val started = System.nanoTime()
            job.cancelAndJoin()
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("cancel took ${elapsedMs}ms", elapsedMs < 3_000)
            assertTrue(received.none { it is ChatChunk.Done })
        }
    }
}

class ToolCallParsingTest {
    private fun home() = MycoHome(java.nio.file.Files.createTempDirectory("myco-tools"))

    private fun provider(port: Int, kind: String) = ProviderProfile(
        id = "local", kind = kind, baseUrl = "http://127.0.0.1:$port",
        auth = AuthProfile.None,
        models = listOf(ModelProfile(id = "m", contextWindow = 1024, maxTokens = 128)),
    )

    @Test
    fun `openai accumulates tool_call arguments across deltas`() = runBlocking {
        val sse = buildString {
            append("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\",\"function\":{\"name\":\"time_now\",\"arguments\":\"{\\\"a\\\"\"}}]}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\":1}\"}}]}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        CountingHttpServer { Pair(200, sse) }.use { srv ->
            val chunks = OpenAiCompatibleClient(provider(srv.port, "openai-completions"), CredentialResolver(home(), emptyMap()))
                .stream(ChatRequest("m", listOf(ChatMessage("user", "几点了")), tools = listOf(
                    ToolSpec("time_now", "读取时间", kotlinx.serialization.json.JsonObject(emptyMap()))
                ))).toList()
            val call = chunks.filterIsInstance<ChatChunk.ToolRequest>().single()
            assertEquals("call_a", call.id)
            assertEquals("time_now", call.name)
            assertEquals("1", call.arguments["a"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `anthropic emits tool_use after input_json_delta`() = runBlocking {
        val sse = buildString {
            append("event: content_block_start\n")
            append("data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"time_now\"}}\n\n")
            append("event: content_block_delta\n")
            append("data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":1}\"}}\n\n")
            append("event: content_block_stop\n")
            append("data: {\"type\":\"content_block_stop\",\"index\":0}\n\n")
            append("event: message_stop\n")
            append("data: {\"type\":\"message_stop\"}\n\n")
        }
        CountingHttpServer { Pair(200, sse) }.use { srv ->
            val h = home()
            val cred = CredentialResolver(h, emptyMap())
            cred.set("TEST_KEY", "sk-test")
            val p = ProviderProfile(
                id = "local", kind = "deepseek-messages", baseUrl = "http://127.0.0.1:${srv.port}",
                auth = AuthProfile.ApiKey("TEST_KEY"),
                models = listOf(ModelProfile(id = "m", contextWindow = 1024, maxTokens = 128)),
            )
            val chunks = AnthropicCompatibleClient(p, cred)
                .stream(ChatRequest("m", listOf(ChatMessage("user", "几点了")), tools = listOf(
                    ToolSpec("time_now", "读取时间", kotlinx.serialization.json.JsonObject(emptyMap()))
                ))).toList()
            val reqs = chunks.filterIsInstance<ChatChunk.ToolRequest>()
            assertTrue("chunks=$chunks", reqs.isNotEmpty())
            val call = reqs.single()
            assertEquals("toolu_1", call.id)
            assertEquals("time_now", call.name)
            assertEquals("1", call.arguments["a"]?.jsonPrimitive?.content)
            assertTrue(chunks.any { it is ChatChunk.Done })
        }
    }
}

class LlmRetryTest {
    private fun home() = MycoHome(java.nio.file.Files.createTempDirectory("myco-retry"))

    private fun openAi(port: Int, maxRetries: Int) = ProviderProfile(
        id = "local", kind = "openai-completions", baseUrl = "http://127.0.0.1:$port",
        auth = AuthProfile.None,
        models = listOf(ModelProfile(id = "m", contextWindow = 1024, maxTokens = 128)),
        retry = RetryProfile(mode = "normal", maxRetries = maxRetries),
    )

    private val okSse = "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\ndata: [DONE]\n\n"

    @Test
    fun `retries 5xx then succeeds`() = runBlocking {
        CountingHttpServer { attempt -> if (attempt <= 2) Pair(500, "boom") else Pair(200, okSse) }.use { srv ->
            val chunks = OpenAiCompatibleClient(openAi(srv.port, maxRetries = 3), CredentialResolver(home(), emptyMap()))
                .stream(ChatRequest("m", listOf(ChatMessage("user", "hi")))).toList()
            assertEquals(3, srv.requests())
            assertTrue(chunks.any { it is ChatChunk.Done })
            assertTrue(chunks.none { it is ChatChunk.Failed })
        }
    }

    @Test
    fun `retry exhaustion surfaces retriable failure`() = runBlocking {
        CountingHttpServer { Pair(503, "unavailable") }.use { srv ->
            val chunks = OpenAiCompatibleClient(openAi(srv.port, maxRetries = 1), CredentialResolver(home(), emptyMap()))
                .stream(ChatRequest("m", listOf(ChatMessage("user", "hi")))).toList()
            assertEquals(2, srv.requests())
            val failed = chunks.filterIsInstance<ChatChunk.Failed>().single()
            assertTrue(failed.retriable)
            assertTrue(failed.message.contains("503"))
        }
    }

    @Test
    fun `400 is not retried`() = runBlocking {
        CountingHttpServer { Pair(400, "bad request") }.use { srv ->
            val chunks = OpenAiCompatibleClient(openAi(srv.port, maxRetries = 3), CredentialResolver(home(), emptyMap()))
                .stream(ChatRequest("m", listOf(ChatMessage("user", "hi")))).toList()
            assertEquals(1, srv.requests())
            val failed = chunks.filterIsInstance<ChatChunk.Failed>().single()
            assertFalse(failed.retriable)
        }
    }

    @Test
    fun `anthropic client retries then parses usage`() = runBlocking {
        val sse = buildString {
            append("event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"model\":\"deepseek-flash\",\"usage\":{\"input_tokens\":7,\"output_tokens\":0}}}\n\n")
            append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"嗨\"}}\n\n")
            append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        }
        CountingHttpServer { attempt -> if (attempt == 1) Pair(500, "boom") else Pair(200, sse) }.use { srv ->
            val provider = ProviderProfile(
                id = "ds", kind = "deepseek-messages", baseUrl = "http://127.0.0.1:${srv.port}",
                auth = AuthProfile.ApiKey("K"),
                models = listOf(ModelProfile(id = "deepseek-flash", contextWindow = 1000, maxTokens = 100)),
                retry = RetryProfile(mode = "normal", maxRetries = 2),
            )
            val chunks = AnthropicCompatibleClient(provider, CredentialResolver(home(), mapOf("K" to "sk-test")))
                .stream(ChatRequest("deepseek-flash", listOf(ChatMessage("user", "hi")))).toList()
            assertEquals(2, srv.requests())
            assertEquals("嗨", chunks.filterIsInstance<ChatChunk.Delta>().joinToString("") { it.text })
            assertEquals(7L, chunks.filterIsInstance<ChatChunk.Done>().single().usage?.inputTokens)
        }
    }
}


