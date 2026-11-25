package dev.myco.core.llm

import dev.myco.core.provider.CredentialResolver
import dev.myco.core.spec.ProviderProfile

/** 按 provider.kind 选方言；未知 kind 明确报错，不静默降级。 */
object ChatClients {
    fun create(provider: ProviderProfile, credentials: CredentialResolver): ChatClient = when (provider.kind) {
        "openai-completions" -> OpenAiCompatibleClient(provider, credentials)
        "deepseek-messages" -> AnthropicCompatibleClient(provider, credentials)
        "openai-responses" -> throw IllegalArgumentException(
            "openai-responses 方言尚未实现（I1 之后）；provider=${provider.id}"
        )
        else -> throw IllegalArgumentException("未知 provider.kind: ${provider.kind}")
    }

    /** 失败必须吵：不支持的方言不偷偷换成假模型，而是流式返回一条明确错误。 */
    fun createOrFailed(provider: ProviderProfile, credentials: CredentialResolver): ChatClient =
        runCatching { create(provider, credentials) }.getOrElse { e ->
            object : ChatClient {
                override fun stream(request: ChatRequest): kotlinx.coroutines.flow.Flow<ChatChunk> =
                    kotlinx.coroutines.flow.flowOf(
                        ChatChunk.Failed(e.message ?: "无法创建 ${provider.kind} 客户端", retriable = false)
                    )
            }
        }
}
