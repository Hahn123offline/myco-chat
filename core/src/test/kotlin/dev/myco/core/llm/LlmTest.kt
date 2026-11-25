package dev.myco.core.llm

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.MycoHome
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class SseParserTest {
    @Test
    fun `multi line data is joined on blank line`() {
        val parser = SseParser()
        assertNull(parser.feed("data: a"))
        assertNull(parser.feed("data: b"))
        val frame = parser.feed("")
        assertEquals("a\nb", frame?.data)
    }

    @Test
    fun `comment and event fields`() {
        val parser = SseParser()
        assertNull(parser.feed(": keep-alive"))
        assertNull(parser.feed("event: delta"))
        assertNull(parser.feed("data: x"))
        val frame = parser.feed("")
        assertEquals("delta", frame?.event)
        assertEquals("x", frame?.data)
    }

    @Test
    fun `finish flushes pending data`() {
        val parser = SseParser()
        parser.feed("data: tail")
        assertEquals("tail", parser.finish()?.data)
        assertNull(parser.finish())
    }
}

class OpenAiCompatibleClientTest {
    @Test
    fun `streams deltas and done from a real SSE response`() = runBlocking {
        val body = buildString {
            append("""data: {"model":"fake-chat","choices":[{"delta":{"content":"你"}}]}""").append("\n\n")
            append("""data: {"model":"fake-chat","choices":[{"delta":{"content":"好"}}]}""").append("\n\n")
            append("""data: {"model":"fake-chat","choices":[],"usage":{"prompt_tokens":12,"completion_tokens":2,"prompt_tokens_details":{"cached_tokens":8}}}""").append("\n\n")
            append("data: [DONE]").append("\n\n")
        }
        val server = ServerSocket(0)
        val port = server.localPort
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = socket.getOutputStream()
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray(StandardCharsets.ISO_8859_1))
                    out.write(body.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
                server.close()
            }
        }

        val home = MycoHome(java.nio.file.Files.createTempDirectory("myco-sse"))
        val provider = ProviderProfile(
            id = "local", kind = "openai-completions",
            baseUrl = "http://127.0.0.1:$port/v1",
            auth = AuthProfile.None,
            models = listOf(ModelProfile(id = "fake-chat", contextWindow = 1024, maxTokens = 128)),
        )
        val client = OpenAiCompatibleClient(provider, CredentialResolver(home, emptyMap()))
        val chunks = client.stream(ChatRequest("fake-chat", listOf(ChatMessage("user", "hi")))).toList()

        val deltas = chunks.filterIsInstance<ChatChunk.Delta>().joinToString("") { it.text }
        assertEquals("你好", deltas)
        val done = chunks.filterIsInstance<ChatChunk.Done>().single()
        assertEquals("你好", done.text)
        assertEquals(8L, done.usage?.cachedTokens)
        assertTrue(chunks.none { it is ChatChunk.Failed })
    }
}
