package dev.myco.fakesse

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * 本地假模型服务：OpenAI Chat Completions 兼容的最小 SSE 实现。
 * 只用于 S0 开发/测量，不联网、不调任何真实模型。
 */
fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8787
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.createContext("/v1/models") { ex -> json(ex, """{"object":"list","data":[{"id":"fake-chat","object":"model"}]}""") }
    server.createContext("/v1/chat/completions") { ex -> stream(ex) }
    server.createContext("/health") { ex -> json(ex, """{"ok":true}""") }
    server.executor = java.util.concurrent.Executors.newCachedThreadPool()
    server.start()
    println("fake-sse listening on http://127.0.0.1:$port  (POST /v1/chat/completions, GET /v1/models)")
}

private fun json(ex: HttpExchange, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    ex.responseHeaders.add("Content-Type", "application/json")
    ex.sendResponseHeaders(200, bytes.size.toLong())
    ex.responseBody.use { it.write(bytes) }
}

private fun stream(ex: HttpExchange) {
    val request = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
    val userText = Regex("\"content\"\\s*:\\s*\"([^\"]*)\"").findAll(request).lastOrNull()?.groupValues?.get(1) ?: ""
    val reply = "我在。你说的是：「$userText」。这是 fake-sse 的流式回复，用来验证 S0 的 SSE 解析与 UI 增量。"
    ex.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
    ex.responseHeaders.add("Cache-Control", "no-cache")
    ex.sendResponseHeaders(200, 0)
    val out = ex.responseBody
    try {
        var i = 0
        while (i < reply.length) {
            val piece = reply.substring(i, minOf(i + 2, reply.length))
            i += 2
            val data = """{"id":"fake-1","object":"chat.completion.chunk","model":"fake-chat","choices":[{"index":0,"delta":{"content":"${escape(piece)}"}}]}"""
            out.write("data: $data\n\n".toByteArray(StandardCharsets.UTF_8))
            out.flush()
            Thread.sleep(20)
        }
        val usage = """{"id":"fake-1","model":"fake-chat","choices":[],"usage":{"prompt_tokens":16,"completion_tokens":${reply.length / 2},"prompt_tokens_details":{"cached_tokens":0}}}"""
        out.write("data: $usage\n\n".toByteArray(StandardCharsets.UTF_8))
        out.write("data: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8))
        out.flush()
    } finally {
        out.close()
    }
}

private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
