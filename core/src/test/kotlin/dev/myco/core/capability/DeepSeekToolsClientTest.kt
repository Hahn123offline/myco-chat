package dev.myco.core.capability

import dev.myco.core.MycoHome
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

/** 最小可编程 HTTP 服务器：responder 拿到 method/path/body，返回 (status, body)。 */
private class MockToolServer(
    private val contentType: String = "application/json",
    private val responder: (method: String, path: String, body: String) -> Pair<Int, ByteArray>,
) : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int = server.localPort
    val requests = AtomicInteger(0)
    val lastPath = java.util.concurrent.atomic.AtomicReference("")
    val lastBody = java.util.concurrent.atomic.AtomicReference("")
    val lastAuth = java.util.concurrent.atomic.AtomicReference("")

    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    fun readLine(): String {
                        val sb = StringBuilder()
                        while (true) {
                            val b = input.read()
                            if (b < 0 || b == '\n'.code) break
                            if (b != '\r'.code) sb.append(b.toChar())
                        }
                        return sb.toString()
                    }
                    val requestLine = readLine()
                    if (requestLine.isBlank()) return@use
                    val parts = requestLine.split(' ')
                    val method = parts.getOrElse(0) { "" }
                    val path = parts.getOrElse(1) { "" }
                    var contentLength = 0
                    var auth = ""
                    while (true) {
                        val line = readLine()
                        if (line.isEmpty()) break
                        val lower = line.lowercase()
                        if (lower.startsWith("content-length:")) contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                        if (lower.startsWith("authorization:")) auth = line.substringAfter(':').trim()
                    }
                    val bodyBytes = ByteArray(contentLength)
                    var off = 0
                    while (off < contentLength) {
                        val n = input.read(bodyBytes, off, contentLength - off)
                        if (n < 0) break
                        off += n
                    }
                    val body = String(bodyBytes, StandardCharsets.UTF_8)
                    val (status, bytes) = responder(method, path, body.toString())
                    requests.incrementAndGet()
                    lastPath.set(path); lastBody.set(body.toString()); lastAuth.set(auth)
                    val out = socket.getOutputStream()
                    val header = "HTTP/1.1 $status ${if (status == 200) "OK" else "ERROR"}\r\n" +
                        "Content-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray(StandardCharsets.ISO_8859_1))
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

class DeepSeekToolsClientTest {
    private fun client(port: Int, key: String? = "sk-test") = DeepSeekToolsClient("http://127.0.0.1:$port", key)

    @Test
    fun `search parses results and sends auth plus body`() {
        val json = """
            {"results":[
              {"title":"A","url":"https://a.example","snippet":"aa","summary":"sum","site_name":"A站","date":"2026-11-01","rerank_score":2.1},
              {"title":"B","url":"https://b.example","snippet":"bb"}
            ]}
        """.trimIndent()
        MockToolServer { _, _, _ -> Pair(200, json.toByteArray()) }.use { srv ->
            val hits = client(srv.port).search("测试 查询", count = 5)
            assertEquals(2, hits.size)
            assertEquals("https://a.example", hits[0].url)
            assertEquals("A站", hits[0].siteName)
            assertEquals("/v1/tools/search", srv.lastPath.get())
            assertEquals("Bearer sk-test", srv.lastAuth.get())
            assertTrue(srv.lastBody.get().contains("\"query\":\"测试 查询\""))
            assertTrue(srv.lastBody.get().contains("\"count\":5"))
        }
    }

    @Test
    fun `fetch returns title and markdown content`() {
        MockToolServer { _, _, _ ->
            Pair(200, """{"title":"页面","content":"# 正文\n内容"}""".toByteArray())
        }.use { srv ->
            val page = client(srv.port).fetch("https://x.example/p")
            assertEquals("页面", page.title)
            assertTrue(page.content.contains("正文"))
            assertEquals("/v1/tools/fetch", srv.lastPath.get())
        }
    }

    @Test
    fun `429 is retriable and error message is surfaced`() {
        MockToolServer { _, _, _ ->
            Pair(429, """{"error":{"message":"rate limited"}}""".toByteArray())
        }.use { srv ->
            val e = runCatching { client(srv.port).search("q") }.exceptionOrNull()
            assertTrue(e is CapabilityException)
            val ce = e as CapabilityException
            assertEquals("HTTP_429", ce.code)
            assertTrue(ce.retriable)
            assertTrue(ce.message!!.contains("rate limited"))
            assertEquals(3, srv.requests.get()) // 2 次重试
        }
    }

    @Test
    fun `origin strips anthropic and v1 suffixes`() {
        assertEquals("https://api.deepseek.com", DeepSeekToolsClient.originOf("https://api.deepseek.com/anthropic"))
        assertEquals("https://api.deepseek.com", DeepSeekToolsClient.originOf("https://api.deepseek.com/v1"))
        assertEquals("https://api.deepseek.com", DeepSeekToolsClient.originOf("https://api.deepseek.com"))
    }

    @Test
    fun `cost estimates match published list prices`() {
        assertEquals(0.005, CapabilityCosts.SEARCH_PER_CALL_CNY, 0.0000001)
        assertEquals(0.0015, CapabilityCosts.ocrCost(1), 0.0000001)
        assertTrue(CapabilityCosts.asrCost(60_000) > 0.0)
        assertTrue(CapabilityCosts.ttsCost(100) > 0.0)
    }
}

class CapabilityToolsTest {
    private fun provider() = ProviderProfile(
        id = "ds", kind = "deepseek-messages", baseUrl = "http://127.0.0.1:1",
        auth = AuthProfile.None,
        models = emptyList(),
    )

    @Test
    fun `registers tools with cost notes and web_search works`() = runBlocking {
        val json = """{"results":[{"title":"T","url":"https://t.example","snippet":"S"}]}"""
        MockToolServer { _, _, _ -> Pair(200, json.toByteArray()) }.use { srv ->
            val registry = ToolRegistry()
            val ok = registerDeepSeekCapabilityTools(
                registry,
                provider(),
                CredentialResolver(MycoHome(java.nio.file.Files.createTempDirectory("myco-cap")), emptyMap()),
                clientOverride = DeepSeekToolsClient("http://127.0.0.1:${srv.port}", "k"),
            )
            assertTrue(ok)
            assertEquals(listOf("web_search", "read_url", "ocr_image", "transcribe_audio"), registry.names())
            assertTrue(registry.list().first { it.name == "web_search" }.costNote!!.contains("0.005"))
            val out = registry.execute("web_search", JsonObject(mapOf("query" to kotlinx.serialization.json.JsonPrimitive("x")))).getOrThrow()
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(out).jsonObject
            assertEquals(1, obj["count"]?.jsonPrimitive?.content?.toInt())
            assertEquals("https://t.example", obj["results"]!!.let { it as kotlinx.serialization.json.JsonArray }[0].jsonObject["url"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `skips registration for non deepseek host`() {
        val registry = ToolRegistry()
        val ok = registerDeepSeekCapabilityTools(
            registry,
            provider(),
            CredentialResolver(MycoHome(java.nio.file.Files.createTempDirectory("myco-cap2")), emptyMap()),
        )
        assertFalse(ok)
        assertTrue(registry.names().isEmpty())
    }
}
