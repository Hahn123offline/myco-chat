package dev.myco.core.llm

import dev.myco.core.spec.Usage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ChatMessage(val role: String, val content: String)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val maxTokens: Long? = null,
    val temperature: Double? = null,
    /** null = 由客户端按 provider 默认值决定；false 显式关闭思考（DeepSeek 默认开，关掉省钱）。 */
    val thinking: Boolean? = null,
)

sealed class ChatChunk {
    /** 流式增量文本。 */
    data class Delta(val text: String) : ChatChunk()

    /** 一轮结束；text 是完整文本（由客户端累积）。 */
    data class Done(val text: String, val usage: Usage? = null, val model: String? = null) : ChatChunk()

    /** 可重试=true 表示网络/限流类，UI 可给"重试"。 */
    data class Failed(val message: String, val retriable: Boolean) : ChatChunk()
}

interface ChatClient {
    fun stream(request: ChatRequest): Flow<ChatChunk>
}

/** 假模型：不联网、可确定复现，用于 S0 demo 与单测。 */
class FakeChatClient(
    private val reply: (ChatRequest) -> String = { lastUser ->
        "我在。你说的是：「${lastUser.messages.lastOrNull()?.content ?: ""}」。"
    },
    private val tickMs: Long = 15,
) : ChatClient {
    override fun stream(request: ChatRequest): Flow<ChatChunk> = flow {
        val full = reply(request)
        val step = 3
        var emitted = ""
        var i = 0
        while (i < full.length) {
            val next = full.substring(i, minOf(i + step, full.length))
            emitted += next
            emit(ChatChunk.Delta(next))
            i += step
            delay(tickMs)
        }
        emit(
            ChatChunk.Done(
                text = emitted,
                usage = Usage(inputTokens = 32, outputTokens = (emitted.length / 2).toLong(), costUsd = 0.0),
                model = request.model,
            )
        )
    }
}
