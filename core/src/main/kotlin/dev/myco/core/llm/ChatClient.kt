package dev.myco.core.llm

import dev.myco.core.spec.Usage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/** 一条可提供给模型的工具（schema 进请求前缀，必须计入预算）。 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/** 模型要求调用的一次工具（arguments 原样保留，由 Bridge 决定如何执行）。 */
data class ToolCall(val id: String, val name: String, val argumentsJson: String)

/** 进模型上下文的图片（base64；只在内存里，不写事件日志）。 */
data class ChatImage(val mediaType: String, val base64: String)

/**
 * 上下文里的消息。比纯文本多两类字段：
 *  - toolCalls：assistant 消息里携带的工具调用（OpenAI tool_calls / Anthropic tool_use）
 *  - toolCallId/toolName：role=tool 时指回哪次调用（Anthropic 会转成 user+tool_result）
 *  - images：user 消息里的图片（vision）
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val images: List<ChatImage> = emptyList(),
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val maxTokens: Long? = null,
    val temperature: Double? = null,
    /** null = 由客户端按 provider 默认值决定；false 显式关闭思考（DeepSeek 默认开，关掉省钱）。 */
    val thinking: Boolean? = null,
    /** 模型能力映射后的推理强度 API 值（如 low/medium/high）；null=不传该参数。 */
    val reasoningEffort: String? = null,
    val tools: List<ToolSpec> = emptyList(),
)

sealed class ChatChunk {
    /** 流式增量文本。 */
    data class Delta(val text: String) : ChatChunk()

    /** 思考增量（reasoning_content / thinking_delta）；不进正文，但要落盘并可回传。 */
    data class ReasoningDelta(val text: String) : ChatChunk()

    /** 模型要求调用工具（可能一轮里有多个）。 */
    data class ToolRequest(val id: String, val name: String, val arguments: JsonObject) : ChatChunk()

    /** 一轮结束；text 是完整文本（由客户端累积）。 */
    data class Done(
        val text: String,
        val usage: Usage? = null,
        val model: String? = null,
        /** stop / length / tool_calls / content_filter / error … */
        val finishReason: String? = null,
    ) : ChatChunk()

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
