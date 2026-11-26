package dev.myco.android.ui.model

/** 抽屉/会话列表的展示模型：与 Bridge/后端解耦，组件只认这个（低耦合原则）。 */
data class SessionItemUi(
    val id: String,
    val title: String,
    val timeLabel: String,
    val snippet: String? = null,
    val group: SessionGroup = SessionGroup.TODAY,
    val pinned: Boolean = false,
    val unread: Boolean = false,
)

enum class SessionGroup(val label: String) {
    PINNED("置顶"),
    TODAY("今天"),
    YESTERDAY("昨天"),
    WEEK("本周"),
    EARLIER("更早"),
}

/** 聊天渲染模型：与 Bridge JSON 解耦。 */
data class ChatMessageUi(
    val role: String, // user | assistant
    val text: String,
    val messageId: String? = null,
    val nodeId: String? = null,
    val version: Int = 1,
    val turn: Long? = null,
    val finishReason: String? = null,
    val reasoning: String? = null,
    val streaming: Boolean = false,
)

data class ToolStepUi(val name: String, val ok: Boolean?, val text: String)

data class ChatProcessUi(val steps: List<ToolStepUi>)

data class TurnMetaUi(val status: String, val steps: Int, val durationMs: Long?, val costUsd: Double?)
