package dev.myco.core.llm

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI Chat Completions 兼容客户端（SSE 流式）。
 *
 * - 用 [HttpURLConnection] 而不是 java.net.http（Android 没有后者），桌面与 Android 共用。
 * - 重试：provider.retry { mode, maxRetries }；只对 429/5xx/连接异常重试，指数退避（0.4s 起，封顶 8s）。
 * - 取消：flow 取消时 disconnect 当前连接（阻塞读会被打断），不把"停止"变成"假装完成"。
 * - S0 只支持 kind=openai-completions；deepseek-messages 走 AnthropicCompatibleClient。
 */
class OpenAiCompatibleClient(
    private val provider: ProviderProfile,
    private val credentials: CredentialResolver,
    private val path: String = "/chat/completions",
) : ChatClient {

    override fun stream(request: ChatRequest): Flow<ChatChunk> = channelFlow {
        if (provider.kind != "openai-completions") {
            send(ChatChunk.Failed("S0 尚未实现 kind=${provider.kind}（I1 补 deepseek-messages / responses）", retriable = false))
            close(); return@channelFlow
        }
        val base = provider.baseUrl
        if (base.isNullOrBlank()) {
            send(ChatChunk.Failed("provider ${provider.id} 缺少 baseUrl", retriable = false)); close(); return@channelFlow
        }
        val key = credentials.resolveFor(provider.auth)?.takeIf { it.isNotBlank() }
        val body = buildRequestBody(request)
        val activeConn = AtomicReference<HttpURLConnection?>(null)
        // flow 被取消（用户点"停止"）→ 切断连接，让阻塞读立刻退出
        currentCoroutineContext()[Job]?.invokeOnCompletion {
            activeConn.getAndSet(null)?.let { runCatching { it.disconnect() } }
        }

        val job = launch(Dispatchers.IO) {
            val maxAttempts = 1 + (provider.retry?.maxRetries ?: 0).coerceIn(0, 5)
            var attempt = 0
            while (true) {
                attempt++
                var conn: HttpURLConnection? = null
                try {
                    conn = open(base, body, key).also { activeConn.set(it) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        parseStream(conn)
                        return@launch
                    }
                    val detail = readError(conn)
                    val retriable = code == 429 || code >= 500
                    if (retriable && attempt < maxAttempts) {
                        delay(backoffMs(attempt))
                        continue
                    }
                    send(ChatChunk.Failed("HTTP $code: ${detail.take(300)}", retriable = retriable))
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempt < maxAttempts) {
                        delay(backoffMs(attempt))
                        continue
                    }
                    send(ChatChunk.Failed(e.message ?: e::class.simpleName ?: "请求失败", retriable = true))
                    return@launch
                } finally {
                    runCatching { conn?.disconnect() }
                    activeConn.compareAndSet(conn, null)
                }
            }
        }
        job.join()
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<ChatChunk>.parseStream(conn: HttpURLConnection) {
        val parser = SseParser()
        val full = StringBuilder()
        var usage: Usage? = null
        var model: String? = provider.model()?.id
        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val frame = parser.feed(line) ?: continue
                if (frame.data == "[DONE]") break
                val obj = runCatching { Json.parseToJsonElement(frame.data).jsonObject }.getOrNull() ?: continue
                obj["model"]?.jsonPrimitive?.contentOrNull?.let { model = it }
                obj["usage"]?.jsonObject?.let { u ->
                    usage = Usage(
                        inputTokens = u["prompt_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                        outputTokens = u["completion_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                        cachedTokens = u["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")
                            ?.jsonPrimitive?.longOrNull ?: 0,
                    )
                }
                val content = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrEmpty()) {
                    full.append(content)
                    send(ChatChunk.Delta(content))
                }
            }
        }
        parser.finish()?.let { rest ->
            if (rest.data != "[DONE]") {
                val content = runCatching {
                    Json.parseToJsonElement(rest.data).jsonObject["choices"]?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!content.isNullOrEmpty()) { full.append(content); send(ChatChunk.Delta(content)) }
            }
        }
        send(ChatChunk.Done(full.toString(), usage, model))
    }

    private fun buildRequestBody(request: ChatRequest): String = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        request.maxTokens?.let { put("max_tokens", it) }
        request.temperature?.let { put("temperature", it) }
        put("messages", buildJsonArray {
            request.messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
        })
    }.toString()

    private fun open(base: String, body: String, key: String?): HttpURLConnection =
        (URI.create(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = (provider.limits?.timeoutMs ?: 120_000L).toInt()
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            key?.let { setRequestProperty("Authorization", "Bearer $it") }
            provider.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }

    private fun readError(conn: HttpURLConnection): String = runCatching {
        (conn.errorStream ?: conn.inputStream)?.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readLines().take(5).joinToString(" ")
        }
    }.getOrNull().orEmpty()

    private fun backoffMs(attempt: Int): Long = minOf(8_000L, 400L shl (attempt - 1).coerceIn(0, 4))
}
