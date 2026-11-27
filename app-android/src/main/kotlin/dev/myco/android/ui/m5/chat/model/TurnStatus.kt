// CHAT-11, CHAT-17, CHAT-18, CHAT-22, CHAT-32, CHAT-43, CHAT-44, CHAT-46, CHAT-47
package dev.myco.android.ui.m5.chat.model

import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.model.TurnMetaUi

/**
 * 轮/消息状态与 G-03 **形态 D**（state-switched：流式期 B 封顶内滚 → 定稿 A 不封顶 + 组头 sticky）的切换判据。
 *
 * 数据来源 = 现有模型：`dev.myco.android.ui.model.TurnMetaUi`（`turn/end` 投影）+
 * 直播轮输入（`LiveTurnInput`，来自 `MainActivity` 的 `streamingTurn`/`liveEntries`/`streamingText`）。
 *
 * 本文件全部是纯函数，不依赖 Android/Compose。
 */

/** 一轮的生命周期阶段（§2 状态矩阵 3/4/5/7/8/8b）。 */
enum class TurnPhase {
    /** 流式·首 token 前（§2 状态 4）：该出现 stream-status 三点块。 */
    PENDING_FIRST_TOKEN,

    /** 流式·有内容（§2 状态 5）。 */
    STREAMING,

    /** 已完成（`turn/end.status = ok`）。 */
    CLOSED_OK,

    /** 被打断（`status = stopped`，§2 状态 8b / CHAT-43）。 */
    CLOSED_STOPPED,

    /** 本轮失败（`status = error`，§2 状态 7 / CHAT-44）。 */
    CLOSED_ERROR,

    /** 达到输出 token 上限（`status = truncated`，§2 状态 8 / CHAT-47）。 */
    CLOSED_TRUNCATED,
}

/** G-03 形态 D 的两个状态，三选一里 D 的展开形态（同一时刻只允许一个）。 */
enum class TurnForm {
    /** 流式期：`B. capped-inner-scroll`（封顶 + 组内跟尾，cap = [ChatCap]）。 */
    CAPPED_STREAMING,

    /** 定稿后：`A. inline-uncapped + sticky header`（成员拍平进列表流，组头 sticky）。 */
    FLAT_SETTLED,
}

/** 展开状态的来源：自动展开（流式默认）vs 用户手动展开（G-07 家族，CHAT-18）。 */
enum class ExpansionOrigin {
    /** 流式期自动展开 / live 默认展开。 */
    AUTO,

    /** 用户手动点开的。 */
    MANUAL,
}

/** 工具行视觉/交互状态（§3.8 字段 6 / CHAT-22）。 */
enum class ToolRowState {
    /** `preparing`：不可展开，行尾「准备中」，点击零行为。 */
    PREPARING,

    /** 运行中：行尾 spinner。 */
    RUNNING,

    /** 成功：不染色、无行尾文字。 */
    SUCCEEDED,

    /** 失败：摘要红 + 行尾「失败」。 */
    FAILED,

    /** 被打断：琥珀 + 「已停止」。 */
    STOPPED,
}

/**
 * 轮级错误数据（`turn-error` 行）。
 *
 * 数据前提（`TODO(lead)`）：现有渲染模型 `ChatContentState` **没有**轮级错误字段——
 * `BridgeEvent.Failed(code, message)` 只写到 `MainActivity` 的全局 `error`，`turn/end` 只有 `status`。
 * 本类型是给投影层补数据用的最小载体：`code` = 错误码（等宽显示）、`text` = 分类文案、
 * `retriable` = 是否给重试按钮（未定义/`false` 一律不给，CHAT-46）。
 */
data class TurnErrorUi(
    val code: String? = null,
    val text: String = "",
    val retriable: Boolean? = null,
) {
    /** §3.19：`retriable=true` 才渲染重试（`false`/未定义 → 不渲染，CHAT-46）。 */
    val showRetry: Boolean get() = retriable == true
}

/**
 * 一轮的状态投影结果。
 *
 * @param turnId 轮号；`null` = 直播轮尚未拿到 `turnId`（首 token 前）。
 * @param phase 生命周期阶段。
 */
data class TurnStatus(
    val turnId: Long?,
    val phase: TurnPhase,
) {
    val isStreaming: Boolean
        get() = phase == TurnPhase.PENDING_FIRST_TOKEN || phase == TurnPhase.STREAMING

    val isClosed: Boolean get() = !isStreaming

    /** 被打断（§2 状态 8b）。 */
    val isAborted: Boolean get() = phase == TurnPhase.CLOSED_STOPPED

    /** 本轮失败（§2 状态 7）。 */
    val isError: Boolean get() = phase == TurnPhase.CLOSED_ERROR

    /** 输出上限（§2 状态 8）。 */
    val isTruncated: Boolean get() = phase == TurnPhase.CLOSED_TRUNCATED

    /** G-03 形态 D：流式 → 封顶容器；定稿 → 拍平。 */
    val form: TurnForm
        get() = if (isStreaming) TurnForm.CAPPED_STREAMING else TurnForm.FLAT_SETTLED

    /**
     * §3.4 字段 1：`turn.process-control` 只在 **closed 且非 aborted/error 且有过程内容** 时渲染。
     * CHAT-44：失败/停止轮不渲染控制条，过程保持可见。
     */
    fun processControlVisible(hasProcessContent: Boolean): Boolean =
        hasProcessContent &&
            (phase == TurnPhase.CLOSED_OK || phase == TurnPhase.CLOSED_TRUNCATED)

    /**
     * §3.4 字段 10：控制条文案。`null` = 不渲染。
     * - 缺 `turn.start`/时长 → 「已完成」；
     * - 用时 <1s 钳到 1s（§3.4 字段 10）；
     * - 被打断 → 「已停止」（CHAT-43）；
     * - 失败轮不渲染（返回 null）。
     */
    fun processControlLabel(meta: TurnMetaUi?): String? = when (phase) {
        TurnPhase.CLOSED_STOPPED -> "已停止"
        TurnPhase.CLOSED_ERROR -> null
        TurnPhase.CLOSED_OK, TurnPhase.CLOSED_TRUNCATED -> {
            val durationMs = meta?.durationMs
            if (durationMs == null || durationMs <= 0L) {
                "已完成"
            } else {
                "已完成，用时 ${(durationMs / 1000L).coerceAtLeast(1L)} 秒"
            }
        }
        TurnPhase.PENDING_FIRST_TOKEN, TurnPhase.STREAMING -> null
    }

    /** §3.18 字段 1 / CHAT-43：被打断且该段有正文时，在正文末行尾追加「已停止」。 */
    fun interruptedSuffixVisible(hasReplyText: Boolean): Boolean = isAborted && hasReplyText

    /**
     * §3.16 字段 1：操作条只在该轮是**轮尾最后一条助手消息**且有助手内容时出现（CHAT-28）。
     */
    fun actionsBarVisible(hasReplyText: Boolean, isTailAssistantMessage: Boolean): Boolean =
        hasReplyText && isTailAssistantMessage

    /**
     * 定稿展开时组头走 `stickyHeader`（§3.5 字段 7 / G-06 / CHAT-11）；流式期整组高 ≤ cap，不需要 sticky。
     */
    val groupHeaderSticky: Boolean get() = form == TurnForm.FLAT_SETTLED

    /**
     * CHAT-17：该轮内**纵向可滚动容器数量**。
     * 流式封顶组体 = 1（组体承担唯一内滚）；定稿拍平 = 0（成员随外层列表滚）。
     * 外层会话列表不计入"轮内"。
     */
    fun innerVerticalScrollLayers(hasProcessContent: Boolean): Int =
        if (form == TurnForm.CAPPED_STREAMING && hasProcessContent) 1 else 0

    /**
     * CHAT-18：轮 settle 时"自动展开"的过程区/思考行自动收起；用户**手动展开过**的保持展开。
     */
    fun processExpandedAfterSettle(origin: ExpansionOrigin): Boolean = origin == ExpansionOrigin.MANUAL

    /** CHAT-18（思考行版本，规则同 §3.7 字段 5）。 */
    fun reasoningExpandedAfterSettle(origin: ExpansionOrigin): Boolean = origin == ExpansionOrigin.MANUAL

    /**
     * §3.8 字段 6：工具行状态。`preparing` = 有行无结果（现有投影里 `ok == null && !running`）；
     * 轮被打断且工具仍显示运行中 → `STOPPED`（琥珀「已停止」）。
     */
    fun toolRowState(entry: TimelineEntry): ToolRowState = when {
        entry.running && isAborted -> ToolRowState.STOPPED
        entry.running -> ToolRowState.RUNNING
        entry.ok == true -> ToolRowState.SUCCEEDED
        entry.ok == false -> ToolRowState.FAILED
        else -> ToolRowState.PREPARING
    }

    companion object {
        /**
         * 从现有模型投影一轮状态。
         *
         * @param meta `turn/end` 投影（`ChatContentState.metas[turn]`）；未落盘时为 null。
         * @param live 该轮是否正在直播（`LiveTurnInput != null`）。
         * @param liveHasContent 直播轮是否已有任意内容块（正文/过程）；
         *   false = 首 token 空窗（§2 状态 4）。
         */
        fun project(
            turnId: Long?,
            meta: TurnMetaUi?,
            live: Boolean,
            liveHasContent: Boolean = false,
        ): TurnStatus = TurnStatus(
            turnId = turnId,
            phase = when {
                live -> if (liveHasContent) TurnPhase.STREAMING else TurnPhase.PENDING_FIRST_TOKEN
                else -> closedPhase(meta?.status)
            },
        )

        private fun closedPhase(status: String?): TurnPhase = when (status) {
            "stopped", "aborted", "interrupted" -> TurnPhase.CLOSED_STOPPED
            "error", "failed" -> TurnPhase.CLOSED_ERROR
            "truncated", "length" -> TurnPhase.CLOSED_TRUNCATED
            // null / "ok" / 未知：历史数据没有 turn/end 时按已完成处理（保守，不虚报失败）
            else -> TurnPhase.CLOSED_OK
        }
    }
}
