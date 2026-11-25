package dev.myco.core.provider

import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

data class DiscoveredModel(
    val id: String,
    val displayName: String? = null,
    val contextWindow: Long? = null,
    val maxOutputTokens: Long? = null,
)

class ModelDiscoveryException(message: String) : RuntimeException(message)

/**
 * "获取可用模型"：显式动作，只在用户点按钮时跑（保存 provider 时绝不自动探测）。
 * 支持 OpenAI 兼容的 `GET /models` 与 Anthropic 的 `GET /v1/models`。
 */
object ModelDiscovery {

    fun discover(provider: ProviderProfile, credentials: CredentialResolver): List<DiscoveredModel> {
        val base = provider.baseUrl?.takeIf { it.isNotBlank() }
            ?: throw ModelDiscoveryException("provider ${provider.id} 缺少 baseUrl")
        val key = credentials.resolveFor(provider.auth)
        val normalized = base.trimEnd('/')
        // DeepSeek 的 Anthropic 兼容基址是 /anthropic，但模型清单在站点根 /models；
        // 两类路径都试，404 才换下一条（鉴权/网络错误立刻抛出）。
        val origin = normalized.removeSuffix("/anthropic")
        val candidates = (when (provider.kind) {
            "deepseek-messages" -> listOf("/models", "/v1/models")
            else -> if (normalized.endsWith("/v1")) listOf("/models") else listOf("/models", "/v1/models")
        }).flatMap { path ->
            if (origin != normalized && !normalized.contains("/v1")) listOf(origin + path, normalized + path) else listOf(normalized + path)
        }.distinct()
        var lastError: String? = null
        for (url in candidates) {
            val result = runCatching { fetch(url, provider, key) }
            if (result.isSuccess) return result.getOrThrow()
            val e = result.exceptionOrNull()
            lastError = e?.message
            // 只有 404 才换下一条路径；鉴权/网络错误立刻抛出
            if (e !is ModelDiscoveryException || !(e.message?.contains("HTTP 404") == true)) {
                throw e ?: ModelDiscoveryException("未知错误")
            }
        }
        throw ModelDiscoveryException("无法获取模型列表：${lastError ?: "所有候选端点都失败"}")
    }

    private fun fetch(url: String, provider: ProviderProfile, key: String?): List<DiscoveredModel> {
        val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            when (provider.kind) {
                "deepseek-messages" -> {
                    if (key != null) setRequestProperty("x-api-key", key)
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                else -> if (key != null) setRequestProperty("Authorization", "Bearer $key")
            }
            provider.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = runCatching {
                    (conn.errorStream ?: conn.inputStream)?.use {
                        BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText().take(300)
                    }
                }.getOrNull().orEmpty()
                throw ModelDiscoveryException("HTTP $code @ $url: $body")
            }
            val text = conn.inputStream.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }
            val root = Json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            return data.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                DiscoveredModel(
                    id = id,
                    displayName = o["display_name"]?.jsonPrimitive?.content ?: o["name"]?.jsonPrimitive?.content,
                    contextWindow = o["context_window"]?.jsonPrimitive?.longOrNull
                        ?: o["max_input_tokens"]?.jsonPrimitive?.longOrNull,
                    maxOutputTokens = o["max_output_tokens"]?.jsonPrimitive?.longOrNull,
                )
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
