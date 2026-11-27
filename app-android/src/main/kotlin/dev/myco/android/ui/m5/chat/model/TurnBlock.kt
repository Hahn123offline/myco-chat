// CHAT-06, CHAT-28, CHAT-32, CHAT-41, CHAT-43, CHAT-47, CHAT-54, CHAT-57
package dev.myco.android.ui.m5.chat.model

import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.model.ChatMessageUi

/**
 * chat 轮内可渲染 block 的类型与字段（`docs/spec/panels/chat.md` §1.4 / §1.5）。
 *
 * - 列表实现 = **扁平 block 级 LazyColumn**（§1.4 开头："不是每轮一个 item"），所以这里每种 block
 *   对应一个（或一组）lazy item；`key` 就是 `LazyListState` 的稳定 key（CHAT-57：流式追加 token 时
 *   已渲染块的 key 不变）。
 * - `spacingBeforeDp` = 该 block 与上一个 block 的间距（§1.4 「前间距」列 / §1.5 间距总表）。
 * - 本文件只描述**结构与数值**，不依赖 Android/Compose（纯 JVM 可测）。
 *
 * 实现边界（规格裁决点，见回复/`TODO(lead)`）：
 * - §1.4 第 2 行控制条（`turn.process-control`）与第 3 行组头（`group-header`）的关系：
 *   本实现按 §3.4 字段 5「展开 = 该轮全部过程段（组头+成员）放回列表流」把控制条当**该轮过程区的总开关**，
 *   控制条折叠时不渲染组头/成员。§1.4 第 3 行条件未写这层依赖，留 `TODO(lead)` 确认。
 * - `TimelineEntry.kind == "text"`（工具循环中间步正文）在 §1.4 block 表里**没有对应节点**；
 *   本实现把它当过程组成员（与现有 UI `TimelineBody` 一致），见 `ProcessEntryKind`。
 */

/** §1.4 / §1.5 的间距数值（dp，全部来自规格，未标"我方取值"的均为规格给定值）。 */
object ChatBlockSpacing {
    /** §1.4 第 1 行：user 首条 12；§1.5 首块顶部 12（我方取值 P4 校准）。 */
    const val FIRST_TOP_DP = 12

    /** §1.4 第 1 行 / §1.5：操作条 → 下一轮 user 24。 */
    const val USER_AFTER_PREVIOUS_TURN_DP = 24

    /** §1.4 第 2 行：user → turn.process-control 12。 */
    const val PROCESS_CONTROL_DP = 12

    /** §1.5：turn.process-control → 第一个组 16（§1.4 第 3 行"段 0：16（control 后）"）。 */
    const val GROUP_HEADER_AFTER_CONTROL_DP = 16

    /** §1.5：user → 本轮过程 12（该轮无控制条时，组头直接跟在 user 后面）。 */
    const val GROUP_HEADER_FIRST_NO_CONTROL_DP = 12

    /** §1.5：多过程段之间 16（我方取值 P4 校准；§1.4 第 3 行"段 i>0：16"）。 */
    const val GROUP_HEADER_AFTER_GROUP_DP = 16

    /** §1.4 第 4 行：组体紧接 header，0。 */
    const val GROUP_BODY_STREAMING_DP = 0

    /** §1.4 第 5 行 / §1.5：组成员之间 6；组头 → 首成员 6。 */
    const val GROUP_MEMBER_DP = 6

    /** §1.4 第 6 行：过程段 → reply-inline-reasoning 12。 */
    const val REPLY_INLINE_REASONING_DP = 12

    /** §1.5：过程组 → 最终回答 12。 */
    const val REPLY_DP = 12

    /** §1.4 第 8 行：12。 */
    const val MAX_TOKENS_DP = 12

    /** §1.4 第 9 行：12。 */
    const val TURN_ERROR_DP = 12

    /** §1.5：最终回答 → 来源行 12（我方取值 P4 校准）。 */
    const val SOURCES_DP = 12

    /** §1.5：来源行 → 操作条 6。 */
    const val ACTIONS_DP = 6

    /** §1.4 第 12 行：12。 */
    const val STREAM_STATUS_DP = 12

    /** §1.4 第 13 行：列表顶部 0。 */
    const val LOAD_HISTORY_DP = 0
}

/**
 * 过程时间线里可进段组的成员类别（§1.4「合并/切段规则」：连续过程成员 → 同一段组）。
 *
 * `STEP_TEXT` 是现有投影（`ChatContent.parseChatContent`）在工具循环中间步产生的正文条目；
 * 规格未给它独立 block，按组内成员处理（`TODO(lead)` 确认）。
 */
object ProcessEntryKind {
    const val REASONING = "reasoning"
    const val TOOL = "tool"
    const val STEP_TEXT = "text"

    val ALL: Set<String> = setOf(REASONING, TOOL, STEP_TEXT)
}

/** 该时间线条目是否属于过程段组（reasoning / 工具 / 工具循环中间步正文）。 */
val TimelineEntry.isProcessEntry: Boolean
    get() = kind in ProcessEntryKind.ALL

/**
 * 轮内可渲染 block（§1.4 13 行一一对应）。
 *
 * `turnId == null` 只用于列表级 block（`load-history`）与"尚未拿到 turnId 的直播轮"。
 */
sealed interface TurnBlock {
    /** 稳定 key（§1.4 "稳定 key" 列）。 */
    val key: String

    /** 所属轮；列表级 block 为 null。 */
    val turnId: Long?

    /** 与上一个 block 的间距（dp，§1.4 / §1.5）。 */
    val spacingBeforeDp: Int
}

/** §1.4 第 1 行 `user-message`（key `turnId/user`；长按开菜单，不挂操作条）。 */
data class UserMessageBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val message: ChatMessageUi,
    /** 该消息尚未落盘（乐观发送 / 流式中）。 */
    val live: Boolean = false,
) : TurnBlock

/**
 * §1.4 第 2 行 `turn-process-control`（key `turnId/process-control`）。
 *
 * 只在 `closed` 且非 aborted/error 且有过程内容时出现（§3.4 字段 1，CHAT-44）；
 * 文案由 [TurnStatus.processControlLabel] 给出（CHAT-43：被打断时「已停止」）。
 */
data class ProcessControlBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val label: String,
    val expanded: Boolean,
) : TurnBlock

/**
 * §1.4 第 3 行 `group-header`（key `turnId/group:i/header`）。
 *
 * 定稿展开时走 stickyHeader（§3.5 字段 7 / CHAT-11）；live 时标题 shimmer（UI 层）。
 * 图标/文案合成规则见 §3.5 字段 10（closed 只列类别无计数，CHAT-16 —— 文案合成属 UI 层，
 * 本 block 只需给出 [members] 与 [live]）。
 */
data class GroupHeaderBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val segmentIndex: Int,
    val members: List<TimelineEntry>,
    val live: Boolean,
    val expanded: Boolean,
) : TurnBlock {
    val runningTool: Boolean get() = members.any { it.kind == ProcessEntryKind.TOOL && it.running }
    val runningReasoning: Boolean get() = members.any { it.kind == ProcessEntryKind.REASONING && it.running }
}

/**
 * §1.4 第 4 行 `group-body-streaming`（key `turnId/group:i/body`）：
 * 流式期组体是**单个**封顶内滚 lazy item（G-03 形态 D 的 B 态，cap 见 [ChatCap]）。
 */
data class GroupBodyStreamingBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val segmentIndex: Int,
    val members: List<TimelineEntry>,
) : TurnBlock

/**
 * §1.4 第 5 行 `group-member`（key `turnId/group:i/member:j`）：
 * 定稿展开后成员块**拍平进列表流**（G-03 形态 D 的 A 态，CHAT-11）；
 * 成员自身的展开体（思考正文/工具井）由 UI 按其 key 管（G-07 版本绑定）。
 */
data class GroupMemberBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val segmentIndex: Int,
    val memberIndex: Int,
    val member: TimelineEntry,
) : TurnBlock

/**
 * §1.4 第 6 行 `reply-inline-reasoning`（key `turnId/reply:reasoning`）：
 * core 标明"属于最终回答 step"的 reasoning，内联在回答前、默认折叠（§3.7 字段 1）。
 * 数据前提 = `P-CHAT-02`（现有模型无此标记，见 [TimelineEntryRef]）。
 */
data class ReplyInlineReasoningBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val text: String,
    val running: Boolean,
    val expanded: Boolean,
) : TurnBlock

/**
 * §1.4 第 7 行 `reply-markdown`（key `turnId/reply`）。
 *
 * - [streaming] = 流式追加中（UI 需加流式光标 + 节流渲染，CHAT-42/CHAT-59）。
 * - [interrupted] = 该轮被打断且已有正文：正文末行尾追加「已停止」（§3.18 字段 1，CHAT-43）。
 */
data class ReplyMarkdownBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val message: ChatMessageUi?,
    val text: String,
    val streaming: Boolean,
    val interrupted: Boolean,
) : TurnBlock

/** §1.4 第 8 行 `turn-max-tokens`（key `turnId/max-tokens`）：无按钮、不做「继续」（CHAT-47）。 */
data class MaxTokensBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
) : TurnBlock

/**
 * §1.4 第 9 行 `turn-error`（key `turnId/error`）：独立根，不落进过程组（CHAT-06/CHAT-44）。
 *
 * [retriable] 只在显式 `true` 时给重试按钮（§3.18 字段 10 / §3.19 / CHAT-46）。
 */
data class TurnErrorBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val code: String?,
    val text: String,
    val retriable: Boolean?,
) : TurnBlock {
    /** `retriable=false` 或字段缺失 → 不渲染重试按钮（保守，CHAT-46）。 */
    val showRetry: Boolean get() = retriable == true
}

/**
 * §1.4 第 10 行 `sources-row`（key `turnId/sources`）：
 * N = 本轮来源**按 URL 去重**后的数量，N=0 不渲染（§3.20 / CHAT-54）。
 */
data class SourcesRowBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val sources: List<SourceRef>,
) : TurnBlock {
    val count: Int get() = sources.size
}

/**
 * §1.4 第 11 行 `actions-bar`（key `turnId/actions`）：
 * 只在该轮是**轮尾最后一条助手消息**且有助手内容时出现（D-19=A / CHAT-28）；
 * 生成中复制可用、重新生成/版本切换禁用并显示「生成中…」（§3.16 字段 5 / CHAT-32）。
 */
data class ActionsBarBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
    val message: ChatMessageUi?,
    val streaming: Boolean,
) : TurnBlock {
    /** 生成中：复制可用，重新生成禁用（CHAT-32）。 */
    val regenerateEnabled: Boolean get() = !streaming

    /** 生成中：版本切换禁用（CHAT-32）。 */
    val versionSwitchEnabled: Boolean get() = !streaming

    /** 生成中：操作条末尾显示可见原因「生成中…」（§3.16 字段 2）。 */
    val generatingHintVisible: Boolean get() = streaming
}

/**
 * §1.4 第 12 行 `stream-status`（key `turnId/pending`）：
 * 首 token 空窗才出现，与内容块**互斥**（§3.10 / CHAT-41）。
 */
data class StreamStatusBlock(
    override val key: String,
    override val turnId: Long?,
    override val spacingBeforeDp: Int,
) : TurnBlock

/** §1.4 第 13 行 `load-history`（key `list/load-history`）：首屏回放未完成时列表顶部（CHAT-53）。 */
data class LoadHistoryBlock(
    override val key: String = LOAD_HISTORY_KEY,
    override val turnId: Long? = null,
    override val spacingBeforeDp: Int = ChatBlockSpacing.LOAD_HISTORY_DP,
) : TurnBlock {
    companion object {
        const val LOAD_HISTORY_KEY = "list/load-history"
    }
}
