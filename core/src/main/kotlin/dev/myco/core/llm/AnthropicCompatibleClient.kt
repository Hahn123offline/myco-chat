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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
 * Anthropic Messages 协议客户端（DeepSeek 官方 `/anthropic` 端点走这套）。
 *
 * 关键差异（相对 OpenAI 方言）：
 *  - 鉴权头是 `x-api-key` + `anthropic-version`，不是 Bearer；`max_tokens` 必填；
 *  - 流式事件是 message_start / content_block_delta / message_delta / message_stop；
 *  - DeepSeek 默认开思考：这里默认显式 `{"thinking":{"type":"disabled"}}`，除非 request.thinking=true。
 * 重试/取消语义与 OpenAI 客户端一致（只对 429/5xx/连接异常重试；取消时断开连接）。
 */
class AnthropicCompatibleClient(
    private val provider: ProviderProfile,
    private val credentials: CredentialResolver,
    private val path: String = "/v1/messages",
) : ChatClient {

    override fun stream(request: ChatRequest): Flow<ChatChunk> = channelFlow {
        val base = provider.baseUrl
        if (base.isNullOrBlank()) {
            send(ChatChunk.Failed("provider ${provider.id} 缺少 baseUrl", retriable = false)); close(); return@channelFlow
        }
        val key = credentials.resolveFor(provider.auth)?.takeIf { it.isNotBlank() }
        if (key == null) {
            send(ChatChunk.Failed("provider ${provider.id} 缺少凭据（${credentialRef()}）", retriable = false))
            close(); return@channelFlow
        }
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
        var stopped = false
        val toolIds = LinkedHashMap<Int, String>()
        val toolNames = LinkedHashMap<Int, String>()
        val toolArgs = LinkedHashMap<Int, StringBuilder>()
        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
            while (!stopped) {
                val line = reader.readLine() ?: break
                val frame = parser.feed(line) ?: continue
                val obj = runCatching { Json.parseToJsonElement(frame.data).jsonObject }.getOrNull() ?: continue
                when (frame.event ?: obj["type"]?.jsonPrimitive?.content) {
                    "message_start" -> {
                        val msg = obj["message"]?.jsonObject
                        model = msg?.get("model")?.jsonPrimitive?.content ?: model
                        msg?.get("usage")?.jsonObject?.let { u ->
                            usage = Usage(
                                inputTokens = u["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                                outputTokens = u["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                                cachedTokens = u["cache_read_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                            )
                        }
                    }
                    "content_block_start" -> {
                        val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val block = obj["content_block"]?.jsonObject
                        if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                            block["id"]?.jsonPrimitive?.content?.let { toolIds[index] = it }
                            block["name"]?.jsonPrimitive?.content?.let { toolNames[index] = it }
                            toolArgs.getOrPut(index) { StringBuilder() }
                        }
                    }
                    "content_block_delta" -> {
                        val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val delta = obj["delta"]?.jsonObject
                        when (delta?.get("type")?.jsonPrimitive?.content) {
                            "text_delta" -> {
                                val text = delta["text"]?.jsonPrimitive?.content
                                if (!text.isNullOrEmpty()) {
                                    full.append(text)
                                    send(ChatChunk.Delta(text))
                                }
                            }
                            "input_json_delta" -> {
                                delta["partial_json"]?.jsonPrimitive?.content?.let {
                                    toolArgs.getOrPut(index) { StringBuilder() }.append(it)
                                }
                            }
                            "thinking_delta" -> {
                                delta["thinking"]?.jsonPrimitive?.content?.let {
                                    reasoning.append(it)
                                    send(ChatChunk.ReasoningDelta(it))
                                }
                            }
                        }
                    }
                    "content_block_stop" -> {
                        val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val name = toolNames[index]
                        if (name != null) {
                            val raw = toolArgs[index]?.toString().orEmpty()
                            val args = runCatching { Json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject }
                                .getOrElse { JsonObject(emptyMap()) }
                            send(ChatChunk.ToolRequest(toolIds[index] ?: "tool_$index", name, args))
                            toolNames.remove(index); toolIds.remove(index); toolArgs.remove(index)
                        }
                    }
                    "message_delta" -> {
                        obj["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.content?.let { finishReason = it }
                        obj["usage"]?.jsonObject?.let { u ->
                            usage = (usage ?: Usage()).copy(
                                outputTokens = u["output_tokens"]?.jsonPrimitive?.longOrNull ?: usage?.outputTokens ?: 0
                            )
                        }
                    }
                    "message_stop" -> stopped = true
                    "error" -> {
                        val msg = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "provider error"
                        send(ChatChunk.Failed(msg, retriable = true))
                        return
                    }
                }
            }
        }
        send(ChatChunk.Done(full.toString(), usage?.let { UsageCost.apply(it, provider.pricing) }, model, finishReason))
    }

    private fun buildRequestBody(request: ChatRequest): String {
        val thinkingEnabled = request.thinking == true
        return buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens ?: provider.model()?.maxTokens ?: 8192L)
            put("stream", true)
            put("thinking", buildJsonObject {
                put("type", if (thinkingEnabled) "enabled" else "disabled")
                if (thinkingEnabled) {
                    // Anthropic 需要 budget_tokens；模型/用户映射给数字时直接用，否则给保守默认
                    val budget = request.reasoningEffort?.toLongOrNull() ?: 4096L
                    put("budget_tokens", budget)
                }
            })
            request.temperature?.let { put("temperature", it) }
            put("messages", messagesJson(request.messages))
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.parameters)
                        })
                    }
                })
            }
        }.toString()
    }

    /** Anthropic 要求：assistant 的多个 tool_use 必须由紧随其后的**同一个 user 消息**中的多个 tool_result 应答。 */
    private fun messagesJson(messages: List<ChatMessage>): JsonArray = buildJsonArray {
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            if (m.role == "tool") {
                val blocks = buildJsonArray {
                    while (i < messages.size && messages[i].role == "tool") {
                        val t = messages[i]
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", t.toolCallId ?: "")
                            put("content", t.content)
                        })
                        i++
                    }
                }
                add(buildJsonObject { put("role", "user"); put("content", blocks) })
            } else {
                add(messageJson(m))
                i++
            }
        }
    }
    private fun messageJson(m: ChatMessage) = buildJsonObject {
        when {
            m.role == "tool" -> {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", m.toolCallId ?: "")
                        put("content", m.content)
                    })
                })
            }
            m.toolCalls.isNotEmpty() -> {
                put("role", "assistant")
                put("content", buildJsonArray {
                    if (m.content.isNotEmpty()) add(buildJsonObject { put("type", "text"); put("text", m.content) })
                    m.toolCalls.forEach { tc ->
                        add(buildJsonObject {
                            put("type", "tool_use")
                            put("id", tc.id)
                            put("name", tc.name)
                            put("input", runCatching {
                                Json.parseToJsonElement(tc.argumentsJson).jsonObject
                            }.getOrElse { JsonObject(emptyMap()) })
                        })
                    }
                })
            }
            else -> {
                put("role", m.role)
                if (m.images.isEmpty()) {
                    put("content", m.content)
                } else {
                    put("content", buildJsonArray {
                        if (m.content.isNotEmpty()) {
                            add(buildJsonObject { put("type", "text"); put("text", m.content) })
                        }
                        m.images.forEach { img ->
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", img.mediaType)
                                    put("data", img.base64)
                                })
                            })
                        }
                    })
                }
            }
        }
    }

    private fun open(base: String, body: String, key: String): HttpURLConnection =
        (URI.create(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = (provider.limits?.timeoutMs ?: 120_000L).toInt()
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", "2023-06-01")
            provider.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }

    private fun readError(conn: HttpURLConnection): String = runCatching {
        (conn.errorStream ?: conn.inputStream)?.use {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readLines().take(5).joinToString(" ")
        }
    }.getOrNull().orEmpty()

    private fun backoffMs(attempt: Int): Long = minOf(8_000L, 400L shl (attempt - 1).coerceIn(0, 4))

    private fun credentialRef(): String = when (val a = provider.auth) {
        is dev.myco.core.spec.AuthProfile.ApiKey -> a.ref
        dev.myco.core.spec.AuthProfile.None -> "(none)"
    }
}


