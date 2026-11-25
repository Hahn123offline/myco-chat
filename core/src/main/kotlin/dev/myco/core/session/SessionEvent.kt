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
        data class Data(val text: String, val source: String? = null)

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
        data class Data(val text: String, val model: String? = null, val usage: Usage? = null)

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
        data class Data(val name: String, val args: JsonObject = JsonObject(emptyMap()))

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
        data class Data(val name: String, val ok: Boolean, val text: String, val reason: String? = null)

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
        data class Data(val turn: Long, val steps: Int, val costUsd: Double? = null)

        override fun withSeq(seq: Long) = copy(seq = seq)
        override fun withAt(at: Long) = copy(at = at)
    }
}
