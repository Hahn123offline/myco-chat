package dev.myco.core.llm

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
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
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenAI Chat Completions 兼容客户端（SSE 流式）。
 * S0 只支持 kind=openai-completions；deepseek-messages（Anthropic 协议）在 I1 实现。
 */
class OpenAiCompatibleClient(
    private val provider: ProviderProfile,
    private val credentials: CredentialResolver,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val path: String = "/chat/completions",
) : ChatClient {

    override fun stream(request: ChatRequest): Flow<ChatChunk> = channelFlow {
        if (provider.kind != "openai-completions") {
            send(
                ChatChunk.Failed(
                    "S0 尚未实现 kind=${provider.kind}（I1 补 deepseek-messages / responses）",
                    retriable = false
                )
            )
            close()
            return@channelFlow
        }
        val base = provider.baseUrl ?: run {
            send(ChatChunk.Failed("provider ${provider.id} 缺少 baseUrl", retriable = false)); close(); return@channelFlow
        }
        val key = credentials.resolveFor(provider.auth).orEmpty().ifBlank { null }
        val timeout = Duration.ofMillis(provider.limits?.timeoutMs ?: 120_000)
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            request.maxTokens?.let { put("max_tokens", it) }
            request.temperature?.let { put("temperature", it) }
            put("messages", buildJsonArray {
                request.messages.forEach { m ->
                    add(buildJsonObject { put("role", m.role); put("content", m.content) })
                }
            })
        }.toString()

        val job = launch(Dispatchers.IO) {
            try {
                val builder = HttpRequest.newBuilder(URI.create(base.trimEnd('/') + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                if (key != null) builder.header("Authorization", "Bearer $key")
                provider.headers.forEach { (k, v) -> builder.header(k, v) }

                val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofLines())
                if (response.statusCode() !in 200..299) {
                    val detail = response.body().use { lines -> lines.limit(5).toList().joinToString(" ") }
                    send(
                        ChatChunk.Failed(
                            "HTTP ${response.statusCode()}: ${detail.take(300)}",
                            retriable = response.statusCode() == 429 || response.statusCode() >= 500
                        )
                    )
                    return@launch
                }

                val parser = SseParser()
                val full = StringBuilder()
                var usage: Usage? = null
                var model: String? = provider.model()?.id
                response.body().use { lines ->
                    for (line in lines) {
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
                        val delta = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("delta")?.jsonObject
                        val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
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
            } catch (e: Exception) {
                send(ChatChunk.Failed(e.message ?: e::class.simpleName ?: "请求失败", retriable = true))
            }
        }
        job.join()
    }
}
