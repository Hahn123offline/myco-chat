package dev.myco.core.session

import dev.myco.core.spec.SessionMeta
import dev.myco.core.spec.Usage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 会话事件（对应 specs/session-events.schema.json）。
 * 序列化时 sealed 类的 discriminator 就是 "type"，与 schema 的信封一致。
 */
@Serializable
sealed class SessionEvent {
    abstract val seq: Long
    abstract val at: Long

    abstract fun withSeq(seq: Long): SessionEvent
    abstract fun withAt(at: Long): SessionEvent

    @Serializable
    @SerialName("session/start")
    data class SessionStart(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: SessionMeta,
    ) : SessionEvent() {
        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("turn/start")
    data class TurnStart(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(val turn: Long)

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("user/message")
    data class UserMessage(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(
            val text: String,
            val source: String? = null,
            val attachments: List<dev.myco.core.spec.AttachmentRef> = emptyList(),
            /** 消息 id（同节点下的用户消息；编辑重发时需要锚点）。 */
            val messageId: String? = null,
            /** 版本链节点 id（同一问题的多次生成共享 nodeId，不同 version）。 */
            val nodeId: String? = null,
            val turn: Long? = null,
        )

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("assistant/message")
    data class AssistantMessage(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(
            val text: String,
            val model: String? = null,
            val usage: Usage? = null,
            val turn: Long? = null,
            /** 这一版消息的唯一 id（重新生成会新增 messageId/version）。 */
            val messageId: String? = null,
            /** 版本链节点；同一 nodeId 下多个 version 互为重生成版本。 */
            val nodeId: String? = null,
            val version: Int = 1,
            /** stop / length / tool_calls / content_filter / error …（provider 原始或归一化） */
            val finishReason: String? = null,
            /** 思考内容（DeepSeek reasoning_content / Anthropic thinking）；带 tools 时需原样回传 */
            val reasoning: String? = null,
        )

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("tool/call")
    data class ToolCall(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(
            val name: String,
            val args: JsonObject = JsonObject(emptyMap()),
            val turn: Long? = null,
        )

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("tool/result")
    data class ToolResult(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(
            val name: String,
            val ok: Boolean,
            val text: String,
            val reason: String? = null,
            val turn: Long? = null,
        )

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("system/inject")
    data class SystemInject(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(val text: String, val tier: String? = null)

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }

    @Serializable
    @SerialName("turn/end")
    data class TurnEnd(
        override val seq: Long = 0,
        override val at: Long = System.currentTimeMillis(),
        val data: Data,
    ) : SessionEvent() {
        @Serializable
        data class Data(
            val turn: Long,
            val steps: Int,
            val costUsd: Double? = null,
            /** ok | stopped | error | truncated */
            val status: String = "ok",
            val durationMs: Long? = null,
            /** 最终 assistant 消息 id（本轮的主回复；工具步骤消息另有 id）。 */
            val messageId: String? = null,
        )

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }
}
