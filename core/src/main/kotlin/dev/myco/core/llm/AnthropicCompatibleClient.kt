package dev.myco.core.llm

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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

/**
 * Anthropic Messages 协议客户端（DeepSeek 官方的 `/anthropic` 端点走这套）。
 *
 * 关键差异（相对 OpenAI 方言）：
 *  - 鉴权头是 `x-api-key` + `anthropic-version`，不是 Bearer；
 *  - `max_tokens` 必填；
 *  - 流式事件是 message_start / content_block_delta / message_delta / message_stop；
 *  - DeepSeek 默认开思考：这里默认显式 `{"thinking":{"type":"disabled"}}`，除非 request.thinking=true。
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
        val thinkingEnabled = request.thinking == true
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens ?: provider.model()?.maxTokens ?: 8192L)
            put("stream", true)
            put("thinking", buildJsonObject { put("type", if (thinkingEnabled) "enabled" else "disabled") })
            request.temperature?.let { put("temperature", it) }
            put("messages", buildJsonArray {
                request.messages.forEach { m ->
                    add(buildJsonObject { put("role", m.role); put("content", m.content) })
                }
            })
        }.toString()

        val job = launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URI.create(base.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = (provider.limits?.timeoutMs ?: 120_000L).toInt()
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("x-api-key", key)
                    setRequestProperty("anthropic-version", "2023-06-01")
                    provider.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val detail = runCatching {
                        (conn.errorStream ?: conn.inputStream)?.use {
                            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readLines().take(5).joinToString(" ")
                        }
                    }.getOrNull().orEmpty()
                    send(ChatChunk.Failed("HTTP $code: ${detail.take(300)}", retriable = code == 429 || code >= 500))
                    return@launch
                }

                val parser = SseParser()
                val full = StringBuilder()
                var usage: Usage? = null
                var model: String? = provider.model()?.id
                var stopped = false
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
                            "content_block_delta" -> {
                                val delta = obj["delta"]?.jsonObject
                                // text_delta 才进可见文本；thinking_delta 属于推理内容，S0 丢弃
                                if (delta?.get("type")?.jsonPrimitive?.content == "text_delta") {
                                    val text = delta["text"]?.jsonPrimitive?.content
                                    if (!text.isNullOrEmpty()) {
                                        full.append(text)
                                        send(ChatChunk.Delta(text))
                                    }
                                }
                            }
                            "message_delta" -> {
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
                                return@launch
                            }
                        }
                    }
                }
                send(ChatChunk.Done(full.toString(), usage, model))
            } catch (e: Exception) {
                send(ChatChunk.Failed(e.message ?: e::class.simpleName ?: "请求失败", retriable = true))
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
        job.invokeOnCompletion { runCatching { job.cancel() } }
        job.join()
    }

    private fun credentialRef(): String = when (val a = provider.auth) {
        is dev.myco.core.spec.AuthProfile.ApiKey -> a.ref
        dev.myco.core.spec.AuthProfile.None -> "(none)"
    }
}
