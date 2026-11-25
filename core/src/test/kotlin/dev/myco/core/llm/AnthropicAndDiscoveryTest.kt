package dev.myco.core.llm

import dev.myco.core.MycoHome
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ModelDiscovery
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/** 极简 HTTP/1.1 测试服务器：读请求头（含 path），写固定响应后关闭。 */
private class RawHttpServer(
    private val handler: (path: String) -> Pair<Int, String>,
) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int = server.localPort
    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) {
            runCatching {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
                    val requestLine = reader.readLine() ?: return@use
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val path = requestLine.split(" ").getOrNull(1) ?: "/"
                    val (code, body) = handler(path)
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 $code OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        worker.join(1000)
    }
}

class AnthropicCompatibleClientTest {
    private fun provider(port: Int) = ProviderProfile(
        id = "deepseek",
        kind = "deepseek-messages",
        baseUrl = "http://127.0.0.1:$port",
        auth = AuthProfile.ApiKey("DEEPSEEK_API_KEY"),
        models = listOf(ModelProfile(id = "deepseek-flash", contextWindow = 1_000_000, maxTokens = 384_000)),
    )

    private fun home() = MycoHome(java.nio.file.Files.createTempDirectory("myco-anthropic"))

    @Test
    fun `parses anthropic sse into deltas and usage`() = runBlocking {
        val sse = buildString {
            append("event: message_start").append("\n")
            append("""data: {"type":"message_start","message":{"id":"m1","model":"deepseek-flash","usage":{"input_tokens":12,"output_tokens":0}}}""").append("\n\n")
            append("event: content_block_delta").append("\n")
            append("""data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你"}}""").append("\n\n")
            append("event: content_block_delta").append("\n")
            append("""data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"好"}}""").append("\n\n")
            append("event: message_delta").append("\n")
            append("""data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}""").append("\n\n")
            append("event: message_stop").append("\n")
            append("""data: {"type":"message_stop"}""").append("\n\n")
        }
        RawHttpServer { Pair(200, sse) }.use { server ->
            val creds = CredentialResolver(home(), mapOf("DEEPSEEK_API_KEY" to "sk-test"))
            val client = AnthropicCompatibleClient(provider(server.port), creds)
            val chunks = client.stream(ChatRequest("deepseek-flash", listOf(ChatMessage("user", "hi")))).toList()

            assertEquals("你好", chunks.filterIsInstance<ChatChunk.Delta>().joinToString("") { it.text })
            val done = chunks.filterIsInstance<ChatChunk.Done>().single()
            assertEquals("你好", done.text)
            assertEquals("deepseek-flash", done.model)
            assertEquals(12L, done.usage?.inputTokens)
            assertEquals(2L, done.usage?.outputTokens)
        }
    }

    @Test
    fun `missing credential fails loudly without network`() = runBlocking {
        val creds = CredentialResolver(home(), emptyMap())
        val client = AnthropicCompatibleClient(provider(1), creds)
        val chunks = client.stream(ChatRequest("deepseek-flash", listOf(ChatMessage("user", "hi")))).toList()
        val failed = chunks.filterIsInstance<ChatChunk.Failed>().single()
        assertFalse(failed.retriable)
        assertTrue(failed.message.contains("DEEPSEEK_API_KEY"))
    }
}

class ModelDiscoveryTest {
    private fun home() = MycoHome(java.nio.file.Files.createTempDirectory("myco-discover"))

    private fun provider(baseUrl: String) = ProviderProfile(
        id = "local",
        kind = "openai-completions",
        baseUrl = baseUrl,
        auth = AuthProfile.None,
        models = listOf(ModelProfile(id = "placeholder", contextWindow = 1, maxTokens = 1)),
    )

    @Test
    fun `discovers models and metadata`() {
        val body = """{"object":"list","data":[{"id":"m1","context_window":8192},{"id":"m2","display_name":"Model Two","max_output_tokens":2048}]}"""
        RawHttpServer { Pair(200, body) }.use { server ->
            val models = ModelDiscovery.discover(provider("http://127.0.0.1:${server.port}"), CredentialResolver(home(), emptyMap()))
            assertEquals(listOf("m1", "m2"), models.map { it.id })
            assertEquals(8192L, models.first().contextWindow)
            assertEquals("Model Two", models[1].displayName)
            assertEquals(2048L, models[1].maxOutputTokens)
        }
    }

    @Test
    fun `falls back to v1 models when root returns 404`() {
        RawHttpServer { path ->
            if (path == "/v1/models") Pair(200, """{"data":[{"id":"m3"}]}""")
            else Pair(404, """{"error":"not found"}""")
        }.use { server ->
            val models = ModelDiscovery.discover(provider("http://127.0.0.1:${server.port}"), CredentialResolver(home(), emptyMap()))
            assertEquals(listOf("m3"), models.map { it.id })
        }
    }
}
