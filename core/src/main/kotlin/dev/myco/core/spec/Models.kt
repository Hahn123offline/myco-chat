package dev.myco.core.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 与 specs 目录下的 JSON Schema 一一对应的运行时类型（S0 只覆盖 chat MVP 需要的部分）。 */

val MycoJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    // 可选字段为 null 时直接省略（schema 只约束"出现时必须是 string"，null 不应写进日志）
    explicitNulls = false
    prettyPrint = false
}

@Serializable
data class SessionMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val app: String,          // "chat" | "work"
    val preset: String,
    val formatVersion: Int = 1,
    val workspaceId: String? = null,
    val deviceId: String? = null,
)

@Serializable
data class Usage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
    val costUsd: Double = 0.0,
)

@Serializable
sealed class AuthProfile {
    @Serializable
    @SerialName("api-key")
    data class ApiKey(val ref: String) : AuthProfile()

    @Serializable
    @SerialName("none")
    data object None : AuthProfile()
}

@Serializable
data class ModelProfile(
    val id: String,
    val name: String? = null,
    val contextWindow: Long,
    val maxTokens: Long,
    val input: List<String> = listOf("text"),
    val reasoning: ReasoningProfile? = null,
)

@Serializable
data class ReasoningProfile(
    val efforts: Map<String, String?> = emptyMap(),
    val default: String? = null,
)

@Serializable
data class CompatProfile(
    val supportsDeveloperRole: Boolean? = null,
    val maxTokensField: String? = null,
    val supportsReasoningEffort: Boolean? = null,
    val thinkingFormat: String? = null,
)

@Serializable
data class ProviderLimits(
    val timeoutMs: Long? = null,
    val streamIdleTimeoutMs: Long? = null,
)

@Serializable
data class RetryProfile(
    val mode: String = "normal",
    val maxRetries: Int = 0,
)

/**
 * 定价（每 1M token）。峰谷价差先不做乘法，字段留出来：
 * 以后加 peakWindows 时只改 UsageCost，不动 provider 结构。
 */
@Serializable
data class Pricing(
    val currency: String = "USD",
    val inputPerMillion: Double? = null,
    val outputPerMillion: Double? = null,
    val cachedInputPerMillion: Double? = null,
)

/** chat 家族的 provider 档案（对应 specs/provider-profile.schema.json）。 */
@Serializable
data class ProviderProfile(
    val id: String,
    val kind: String,                       // deepseek-messages | openai-completions | openai-responses
    val displayName: String? = null,
    val baseUrl: String? = null,
    val auth: AuthProfile,
    val models: List<ModelProfile>,
    val roles: Map<String, String> = emptyMap(),
    val compat: CompatProfile? = null,
    val headers: Map<String, String> = emptyMap(),
    val limits: ProviderLimits? = null,
    val retry: RetryProfile? = null,
    val pricing: Pricing? = null,
    val enabled: Boolean = true,
) {
    fun model(role: String = "chat"): ModelProfile? {
        val id = roles[role] ?: models.firstOrNull()?.id
        return models.firstOrNull { it.id == id }
    }
}

/** 能力 provider（search/fetch/ocr/asr/tts），S0 只解析不使用。 */
@Serializable
data class CapabilityProvider(
    val id: String,
    val vendor: String,
    val displayName: String? = null,
    val auth: AuthProfile,
    val capabilities: Map<String, CapabilityEndpoint>,
    val enabled: Boolean = true,
)

@Serializable
data class CapabilityEndpoint(
    val endpoint: String,
    val priceUnit: String,
    val maxSeconds: Int? = null,
    val voices: List<String> = emptyList(),
)

@Serializable
data class ProviderFile(val providers: List<ProviderProfile> = emptyList())
