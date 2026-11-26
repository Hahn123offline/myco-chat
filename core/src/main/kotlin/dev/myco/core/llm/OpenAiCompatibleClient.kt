package dev.myco.core.llm

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        // flow 被取消（用户点"停止"）→ 切断连接，让阻塞读立刻退出
        val watcher = launch {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                activeConn.getAndSet(null)?.let { runCatching { it.disconnect() } }
            }
        }
        job.join()
        watcher.cancel()
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<ChatChunk>.parseStream(conn: HttpURLConnection) {
        val parser = SseParser()
        val full = StringBuilder()
        val reasoning = StringBuilder()
        var usage: Usage? = null
        var model: String? = provider.model()?.id
        var finishReason: String? = null
        val toolIds = LinkedHashMap<Int, String>()
        val toolNames = LinkedHashMap<Int, String>()
        val toolArgs = LinkedHashMap<Int, StringBuilder>()
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
                val delta = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finish_reason")
                    ?.jsonPrimitive?.contentOrNull?.let { finishReason = it }
                val reasoningPart = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
                if (!reasoningPart.isNullOrEmpty()) {
                    reasoning.append(reasoningPart)
                    send(ChatChunk.ReasoningDelta(reasoningPart))
                }
                val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrEmpty()) {
                    full.append(content)
                    send(ChatChunk.Delta(content))
                }
                delta?.get("tool_calls")?.jsonArray?.forEachIndexed { fallbackIndex, el ->
                    val tc = runCatching { el.jsonObject }.getOrNull() ?: return@forEachIndexed
                    val index = tc["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: fallbackIndex
                    tc["id"]?.jsonPrimitive?.contentOrNull?.let { toolIds[index] = it }
                    tc["function"]?.jsonObject?.let { fn ->
                        fn["name"]?.jsonPrimitive?.contentOrNull?.let { toolNames[index] = it }
                        fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { part ->
                            toolArgs.getOrPut(index) { StringBuilder() }.append(part)
                        }
                    }
                }
            }
        }
        // 工具调用累积完成后再发射（参数是跨 chunk 拼接的 JSON）
        val indexes = (toolIds.keys + toolNames.keys + toolArgs.keys).distinct().sorted()
        for (i in indexes) {
            val name = toolNames[i] ?: continue
            val id = toolIds[i] ?: "call_$i"
            val raw = toolArgs[i]?.toString().orEmpty()
            val args = runCatching { Json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            send(ChatChunk.ToolRequest(id, name, args))
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
        send(ChatChunk.Done(full.toString(), usage?.let { UsageCost.apply(it, provider.pricing) }, model, finishReason))
    }

    private fun buildRequestBody(request: ChatRequest): String = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        request.maxTokens?.let { put("max_tokens", it) }
        request.temperature?.let { put("temperature", it) }
        put("messages", buildJsonArray {
            request.messages.forEach { m -> add(messageJson(m)) }
        })
        if (request.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                request.tools.forEach { t ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("parameters", t.parameters)
                        })
                    })
                }
            })
        }
    }.toString()

    private fun messageJson(m: ChatMessage) = buildJsonObject {
        put("role", m.role)
        when {
            m.role == "tool" -> {
                put("tool_call_id", m.toolCallId ?: "")
                put("content", m.content)
            }
            m.images.isNotEmpty() -> {
                // vision：content 变成 parts 数组
                put("content", buildJsonArray {
                    if (m.content.isNotEmpty()) {
                        add(buildJsonObject { put("type", "text"); put("text", m.content) })
                    }
                    m.images.forEach { img ->
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:${img.mediaType};base64,${img.base64}")
                            })
                        })
                    }
                })
            }
            m.toolCalls.isNotEmpty() -> {
                put("content", if (m.content.isEmpty()) kotlinx.serialization.json.JsonNull else kotlinx.serialization.json.JsonPrimitive(m.content))
                put("tool_calls", buildJsonArray {
                    m.toolCalls.forEach { tc ->
                        add(buildJsonObject {
                            put("id", tc.id)
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tc.name)
                                put("arguments", tc.argumentsJson)
                            })
                        })
                    }
                })
            }
            else -> put("content", m.content)
        }
    }

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
