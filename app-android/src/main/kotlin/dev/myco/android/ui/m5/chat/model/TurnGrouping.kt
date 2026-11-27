// CHAT-06, CHAT-28, CHAT-41, CHAT-43, CHAT-44, CHAT-47, CHAT-53, CHAT-54, CHAT-57
package dev.myco.android.ui.m5.chat.model

import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.isToolStepPlaceholder
import dev.myco.android.ui.model.ChatMessageUi
import dev.myco.android.ui.model.TurnMetaUi

/**
 * 轮内节点顺序与分组：现有会话模型 → `List<TurnBlock>`（`docs/spec/panels/chat.md` §1.4 / §1.5）。
 *
 * 输入**复用现有模型**，不另造一套：
 * - `ChatContentState.messages` → [ChatTimelineInput.messages]（`ChatMessageUi`）；
 * - `ChatContentState.timelines` / `turnSources` / `metas` → 同名字段（`TimelineEntry` / `SourceRef` / `TurnMetaUi`）；
 * - `MainActivity` 的 `streamingTurn` / `liveEntries` / `streamingText` / `optimisticUser` → [LiveTurnInput]。
 *
 * 排序规则（§1.4「合并/切段规则」）：
 * - 连续过程成员（reasoning / 工具 / 工具循环中间步正文）→ 同一段组；
 * - `turn.process-control` 锚在该轮**第一个过程成员之前**（紧接 user 之后）；
 * - reasoning 进组（除非被 `P-CHAT-02` 标成"最终回答 step"→ 内联到回答前，默认折叠）；
 * - 最终回答**先 flush 再独立**；`turn-error` / `turn-max-tokens` 是独立根，不落进组；
 * - 无过程内容 → 不渲染控制条/组头（不渲染空组）。
 *
 * `TODO(lead)`：§1.4 第 3 行组头条件没写"依赖控制条展开"，而 §3.4 字段 5 写"展开 = 该轮全部过程段
 * （组头+成员）放回列表流"。本实现按 §3.4（控制条是总开关）执行：控制条折叠 → 组头/成员都不渲染。
 */

/** 直播轮输入（对应 `MainActivity` 的 `streamingTurn` / `liveEntries` / `streamingText` / `optimisticUser`）。 */
data class LiveTurnInput(
    /** 当前流式 turn（`streamingTurn`）；首 token 前可能尚为 null。 */
    val turnId: Long? = null,
    /** 乐观发送、尚未落盘的用户消息文本（`optimisticUser`）。 */
    val userText: String? = null,
    /** `liveEntries`（reasoning / tool / text）。 */
    val entries: List<TimelineEntry> = emptyList(),
    /** `streamingText`（当前轮已到的助手正文增量）。 */
    val streamingText: String? = null,
) {
    /** 是否已有任意内容块（false = 首 token 空窗，§2 状态 4）。 */
    val hasContent: Boolean get() = entries.isNotEmpty() || !streamingText.isNullOrBlank()
}

/**
 * `P-CHAT-02` 数据前提：标识某轮时间线里"属于最终回答 step"的 reasoning（§1.4 第 6 行 / §3.7 字段 1）。
 *
 * 现有 `TimelineEntry` **没有**该标记；投影层补上之前本集合传空，所有 reasoning 进过程组。
 */
data class TimelineEntryRef(val turnId: Long, val index: Int)

/**
 * 展开状态（G-07：本地态、绑轮次版本；UI 层持有，分组只读）。
 *
 * 默认值 = 规范默认：定稿轮的过程区默认折叠（C-M3-01）→ [expandedProcessTurns] 为空；
 * 直播轮/aborted/error 轮的过程区默认展开，不需要放进 [expandedProcessTurns]。
 */
data class TurnExpansionState(
    /** 用户点开了 `turn.process-control` 的轮（定稿轮才需要）。 */
    val expandedProcessTurns: Set<Long> = emptySet(),
    /** 用户点收起了某个组头（key = `turnId/group:i/header`）。 */
    val collapsedGroupKeys: Set<String> = emptySet(),
    /** 用户点开了内联 reasoning（key = `turnId/reply:reasoning`）。 */
    val expandedInlineReasoningKeys: Set<String> = emptySet(),
) {
    companion object {
        /** 便捷构造：把某轮过程区全部展开（单测/调试用）。 */
        fun expandedProcess(turnId: Long): TurnExpansionState =
            TurnExpansionState(expandedProcessTurns = setOf(turnId))
    }
}

/**
 * 分组输入（全部来自现有模型/现有 UI 状态，不新增事件模型）。
 *
 * 约定：
 * - [messages] 传**已落盘**消息（`ChatContentState.messages`）；乐观用户消息可放进 [LiveTurnInput.userText]，
 *   也可以仍以 `turn == null` 的 user 消息出现在 [messages] 里（会归属当前直播轮）。
 * - [inlineReasoning] 只对 [timelines] 的索引生效（直播轮条目没有稳定索引，忽略）。
 * - [processSegments] 是 §1.4「多过程段只是投影兼容」的显式入口：myco core 无 steering，
 *   当前投影每轮只有 1 段；给空则按 [timelines] 单段处理。
 */
data class ChatTimelineInput(
    val messages: List<ChatMessageUi> = emptyList(),
    val timelines: Map<Long, List<TimelineEntry>> = emptyMap(),
    val metas: Map<Long, TurnMetaUi> = emptyMap(),
    val turnSources: Map<Long, List<SourceRef>> = emptyMap(),
    val live: LiveTurnInput? = null,
    /** 轮级错误（现有模型缺此数据，见 [TurnErrorUi] 的 `TODO(lead)`）。 */
    val turnErrors: Map<Long, TurnErrorUi> = emptyMap(),
    /** 首屏回放未完成 → 列表顶部「载入历史…」（§3.1 / CHAT-53）。 */
    val loadingHistory: Boolean = false,
    val inlineReasoning: Set<TimelineEntryRef> = emptySet(),
    val processSegments: Map<Long, List<List<TimelineEntry>>> = emptyMap(),
)

/** §1.4 / §1.5 的分组算法（纯函数；同输入必得同输出 → key 稳定，CHAT-57）。 */
object TurnGrouping {

    /** 直播轮在拿到 turnId 之前的 key 前缀。 */
    const val LIVE_TURN_KEY: String = "live"

    /**
     * 现有会话模型 → 扁平 block 列表（§1.4 顺序表）。
     *
     * 输出顺序（每轮内）：user → process-control → 组头/组体/成员 → reply-inline-reasoning →
     * reply-markdown → turn-max-tokens → turn-error → sources-row → actions-bar（+ stream-status 互斥）。
     */
    fun group(
        input: ChatTimelineInput,
        expansion: TurnExpansionState = TurnExpansionState(),
    ): List<TurnBlock> {
        val blocks = mutableListOf<TurnBlock>()
        var contentCount = 0

        if (input.loadingHistory) blocks += LoadHistoryBlock()

        // ---- 轮顺序：消息出现顺序；只有过程/元数据没有消息的轮按轮号升序兜底（防御） ----
        val orderedTurnIds = LinkedHashSet<Long>()
        input.messages.forEach { message -> message.turn?.let { orderedTurnIds += it } }
        (input.timelines.keys + input.metas.keys + input.processSegments.keys)
            .filter { it !in orderedTurnIds }
            .sorted()
            .forEach { orderedTurnIds += it }

        val live = input.live
        val liveTurnId = live?.turnId
        if (live != null && liveTurnId != null && liveTurnId !in orderedTurnIds) {
            orderedTurnIds += liveTurnId
        }
        // 乐观发送的用户消息（投影里 turn=null）归属当前直播轮。
        val pendingLiveUsers = input.messages.filter { it.role == "user" && it.turn == null }

        fun userSpacing(): Int =
            if (contentCount == 0) {
                ChatBlockSpacing.FIRST_TOP_DP
            } else {
                ChatBlockSpacing.USER_AFTER_PREVIOUS_TURN_DP
            }

        fun persistedReplyOf(turnId: Long?): ChatMessageUi? = input.messages.lastOrNull { message ->
            message.role == "assistant" &&
                message.turn == turnId &&
                message.nodeId != null &&
                !message.isToolStepPlaceholder()
        }

        // 该轮"最终回答"文本（直播轮优先用未落盘的 streamingText）。
        fun replyTextOf(turnId: Long?): String {
            if (live != null && live.turnId == turnId) {
                live.streamingText?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return persistedReplyOf(turnId)?.text.orEmpty()
        }

        // 操作条只给"轮尾最后一条助手消息"（D-19=A / CHAT-28）：渲染顺序上最后一个有正文的轮。
        val renderTurnKeys = buildList {
            orderedTurnIds.forEach { add(it.toString()) }
            if (live != null && liveTurnId == null) add(LIVE_TURN_KEY)
        }
        val tailTurnKey: String? = renderTurnKeys.lastOrNull { key ->
            val id = if (key == LIVE_TURN_KEY) null else key.toLongOrNull()
            replyTextOf(id).isNotBlank()
        }

        fun emit(block: TurnBlock) {
            blocks += block
            contentCount++
        }

        fun emitTurn(
            turnId: Long?,
            isLiveTurn: Boolean,
            persistedUsers: List<ChatMessageUi>,
        ) {
            val keyBase = turnId?.toString() ?: LIVE_TURN_KEY
            val meta = if (turnId != null) input.metas[turnId] else null
            val liveInput = if (isLiveTurn) live else null
            val liveText = liveInput?.streamingText?.takeIf { it.isNotBlank() }

            // ---- §1.4 第 1 行：用户消息（myco 每轮 1 条；key = turnId/user） ----
            val users = buildList {
                addAll(persistedUsers)
                if (isLiveTurn) {
                    val typed = liveInput?.userText?.takeIf { it.isNotBlank() }
                    if (typed != null) {
                        if (none { it.text == typed }) {
                            add(ChatMessageUi(role = "user", text = typed, turn = turnId))
                        }
                    } else {
                        // UI 未单独传乐观文本时，允许它仍以 turn=null 的 user 消息留在 messages 里。
                        addAll(pendingLiveUsers.filter { pending -> none { it.text == pending.text } })
                    }
                }
            }.distinctBy { (it.messageId ?: "") to it.text }
            users.forEachIndexed { index, message ->
                val key = "$keyBase/user" + if (users.size > 1) ":$index" else ""
                emit(UserMessageBlock(key, turnId, userSpacing(), message, live = isLiveTurn))
            }

            // ---- 过程段：§1.4「连续过程成员 → 同一段组」 ----
            val explicitSegments = if (turnId != null) input.processSegments[turnId] else null
            val inlineEntries = mutableListOf<TimelineEntry>()
            val segments: List<List<TimelineEntry>> = when {
                liveInput != null && liveInput.entries.isNotEmpty() -> listOf(liveInput.entries)
                explicitSegments != null -> explicitSegments.filter { it.isNotEmpty() }
                else -> {
                    val timeline = if (turnId != null) input.timelines[turnId].orEmpty() else emptyList()
                    val inlineIndexes = if (isLiveTurn) {
                        emptySet()
                    } else {
                        input.inlineReasoning.filter { it.turnId == turnId }.map { it.index }.toSet()
                    }
                    timeline.forEachIndexed { index, entry ->
                        if (index in inlineIndexes && entry.kind == ProcessEntryKind.REASONING && entry.text.isNotBlank()) {
                            inlineEntries += entry
                        }
                    }
                    listOfNotNull(
                        timeline.filterIndexed { index, _ -> index !in inlineIndexes }
                            .takeIf { it.isNotEmpty() },
                    )
                }
            }
            val hasProcess = segments.isNotEmpty()
            val status = TurnStatus.project(
                turnId = turnId,
                meta = meta,
                live = liveInput != null,
                liveHasContent = liveInput?.hasContent == true,
            )

            // ---- §1.4 第 2 行：turn.process-control 锚在第一个过程成员之前 ----
            val controlVisible = status.processControlVisible(hasProcess)
            if (controlVisible) {
                emit(
                    ProcessControlBlock(
                        key = "$keyBase/process-control",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.PROCESS_CONTROL_DP,
                        label = status.processControlLabel(meta) ?: "已完成",
                        expanded = turnId != null && turnId in expansion.expandedProcessTurns,
                    ),
                )
            }

            // ---- §1.4 第 3/4/5 行：组头 + 流式组体 或 拍平成员 ----
            val groupsVisible = when {
                !hasProcess -> false
                status.isStreaming -> true // live 组默认展开（§3.5 字段 1）
                status.isAborted || status.isError -> true // CHAT-44：失败/停止轮过程保持可见
                else -> turnId != null && turnId in expansion.expandedProcessTurns // C-M3-01：定稿默认折叠
            }
            if (groupsVisible) {
                segments.forEachIndexed { segmentIndex, members ->
                    val headerKey = "$keyBase/group:$segmentIndex/header"
                    val expanded = headerKey !in expansion.collapsedGroupKeys
                    val headerSpacing = groupHeaderSpacing(segmentIndex, controlVisible)
                    emit(
                        GroupHeaderBlock(
                            key = headerKey,
                            turnId = turnId,
                            spacingBeforeDp = headerSpacing,
                            segmentIndex = segmentIndex,
                            members = members,
                            live = status.isStreaming,
                            expanded = expanded,
                        ),
                    )
                    if (!expanded) return@forEachIndexed
                    if (status.isStreaming) {
                        emit(
                            GroupBodyStreamingBlock(
                                key = "$keyBase/group:$segmentIndex/body",
                                turnId = turnId,
                                spacingBeforeDp = ChatBlockSpacing.GROUP_BODY_STREAMING_DP,
                                segmentIndex = segmentIndex,
                                members = members,
                            ),
                        )
                    } else {
                        members.forEachIndexed { memberIndex, entry ->
                            emit(
                                GroupMemberBlock(
                                    key = "$keyBase/group:$segmentIndex/member:$memberIndex",
                                    turnId = turnId,
                                    spacingBeforeDp = ChatBlockSpacing.GROUP_MEMBER_DP,
                                    segmentIndex = segmentIndex,
                                    memberIndex = memberIndex,
                                    member = entry,
                                ),
                            )
                        }
                    }
                }
            }

            // ---- §1.4 第 6 行：内联到最后回答前的 reasoning（P-CHAT-02） ----
            inlineEntries.forEachIndexed { index, entry ->
                val key = "$keyBase/reply:reasoning" + if (index > 0) ":$index" else ""
                emit(
                    ReplyInlineReasoningBlock(
                        key = key,
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.REPLY_INLINE_REASONING_DP,
                        text = entry.text,
                        running = entry.running,
                        expanded = key in expansion.expandedInlineReasoningKeys,
                    ),
                )
            }

            // ---- §1.4 第 7 行：最终回答（过程先 flush，回答独立成根） ----
            val persistedReply = persistedReplyOf(turnId)
            val replyText: String = if (isLiveTurn && !liveText.isNullOrBlank()) {
                liveText
            } else {
                persistedReply?.text.orEmpty()
            }
            val hasReply = replyText.isNotBlank()
            if (hasReply) {
                emit(
                    ReplyMarkdownBlock(
                        key = "$keyBase/reply",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.REPLY_DP,
                        message = if (isLiveTurn && !liveText.isNullOrBlank()) null else persistedReply,
                        text = replyText,
                        streaming = status.isStreaming,
                        interrupted = status.interruptedSuffixVisible(hasReply),
                    ),
                )
            }

            // ---- §1.4 第 8 行：输出上限（独立根，无「继续」按钮，CHAT-47） ----
            if (status.isTruncated || persistedReply?.finishReason == "length") {
                emit(
                    MaxTokensBlock(
                        key = "$keyBase/max-tokens",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.MAX_TOKENS_DP,
                    ),
                )
            }

            // ---- §1.4 第 9 行：轮错误（独立根，不落进组；retriable 才给重试，CHAT-44/CHAT-46） ----
            val turnError = if (turnId != null) input.turnErrors[turnId] else null
            if (status.isError || turnError != null) {
                emit(
                    TurnErrorBlock(
                        key = "$keyBase/error",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.TURN_ERROR_DP,
                        code = turnError?.code,
                        text = turnError?.text.orEmpty(),
                        retriable = turnError?.retriable,
                    ),
                )
            }

            // ---- §1.4 第 10 行：来源行（按 URL 去重，N=0 不渲染，CHAT-54） ----
            val sources = sourcesOf(turnId, segments, input)
            if (sources.isNotEmpty()) {
                emit(
                    SourcesRowBlock(
                        key = "$keyBase/sources",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.SOURCES_DP,
                        sources = sources,
                    ),
                )
            }

            // ---- §1.4 第 11 行：操作条（仅轮尾最后一条助手消息，CHAT-28） ----
            if (hasReply && tailTurnKey != null && keyBase == tailTurnKey) {
                emit(
                    ActionsBarBlock(
                        key = "$keyBase/actions",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.ACTIONS_DP,
                        message = persistedReply,
                        streaming = status.isStreaming,
                    ),
                )
            }

            // ---- §1.4 第 12 行：首 token 空窗（与内容块互斥，CHAT-41） ----
            if (isLiveTurn && !hasProcess && !hasReply) {
                emit(
                    StreamStatusBlock(
                        key = "$keyBase/pending",
                        turnId = turnId,
                        spacingBeforeDp = ChatBlockSpacing.STREAM_STATUS_DP,
                    ),
                )
            }
        }

        // ---- 逐轮输出 ----
        orderedTurnIds.forEach { turnId ->
            emitTurn(
                turnId = turnId,
                isLiveTurn = live != null && liveTurnId == turnId,
                persistedUsers = input.messages.filter { it.role == "user" && it.turn == turnId },
            )
        }
        // 直播轮尚未拿到 turnId：仍要渲染乐观 user 气泡 + stream-status/正文。
        if (live != null && liveTurnId == null) {
            emitTurn(turnId = null, isLiveTurn = true, persistedUsers = emptyList())
        }
        // 防御：旧扁平会话（无 turn 的消息）按原顺序兜底，至少不丢内容。
        if (live == null) {
            input.messages.forEachIndexed { index, message ->
                if (message.turn != null) return@forEachIndexed
                when {
                    message.role == "user" -> emit(
                        UserMessageBlock("m/user:$index", null, userSpacing(), message),
                    )
                    message.role == "assistant" &&
                        !message.isToolStepPlaceholder() &&
                        message.text.isNotBlank() -> emit(
                        ReplyMarkdownBlock(
                            key = "m/reply:$index",
                            turnId = null,
                            spacingBeforeDp = ChatBlockSpacing.REPLY_DP,
                            message = message,
                            text = message.text,
                            streaming = false,
                            interrupted = false,
                        ),
                    )
                }
            }
        }
        return blocks
    }

    /**
     * §1.4 第 3 行间距：段 0 且控制条可见 → 16；段 0 无控制条（user → 过程）→ 12；段 i>0 → 16。
     */
    fun groupHeaderSpacing(segmentIndex: Int, afterProcessControl: Boolean): Int = when {
        segmentIndex > 0 -> ChatBlockSpacing.GROUP_HEADER_AFTER_GROUP_DP
        afterProcessControl -> ChatBlockSpacing.GROUP_HEADER_AFTER_CONTROL_DP
        else -> ChatBlockSpacing.GROUP_HEADER_FIRST_NO_CONTROL_DP
    }

    /** §3.20 字段 10 / CHAT-54：来源按 URL 去重（保留首次出现顺序）。 */
    fun dedupeSourcesByUrl(sources: List<SourceRef>): List<SourceRef> = sources.distinctBy { it.url }

    private fun sourcesOf(
        turnId: Long?,
        segments: List<List<TimelineEntry>>,
        input: ChatTimelineInput,
    ): List<SourceRef> {
        val raw = buildList {
            if (turnId != null) addAll(input.turnSources[turnId].orEmpty())
            segments.forEach { segment -> segment.forEach { addAll(it.sources) } }
        }
        return dedupeSourcesByUrl(raw)
    }
}
