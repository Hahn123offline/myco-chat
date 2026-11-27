// CHAT-06, CHAT-18, CHAT-22, CHAT-28, CHAT-41, CHAT-43, CHAT-44, CHAT-46, CHAT-47, CHAT-53, CHAT-54, CHAT-57
package dev.myco.android.ui.m5.chat.model

import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.model.ChatMessageUi
import dev.myco.android.ui.model.TurnMetaUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TurnGrouping`（§1.4 顺序/分段）+ `TurnStatus`（G-03 形态 D）的 JVM 单测。
 *
 * 注：为防止新增文件超出 P4 写作用域白名单，`TurnStatus` 的断言并入本文件（见文末）。
 */
class TurnGroupingTest {

    // ---------------------------------------------------------------- fixtures

    private fun user(id: String, turn: Long, text: String = "提问") = ChatMessageUi(
        role = "user",
        text = text,
        messageId = id,
        turn = turn,
    )

    private fun assistant(
        id: String,
        turn: Long,
        text: String = "回答",
        nodeId: String? = "node-$id",
        finishReason: String? = "end_turn",
    ) = ChatMessageUi(
        role = "assistant",
        text = text,
        messageId = id,
        nodeId = nodeId,
        turn = turn,
        version = 1,
        finishReason = finishReason,
    )

    private fun reasoning(text: String, running: Boolean = false) =
        TimelineEntry(kind = ProcessEntryKind.REASONING, text = text, running = running)

    private fun tool(name: String, ok: Boolean? = true, running: Boolean = false) =
        TimelineEntry(kind = ProcessEntryKind.TOOL, name = name, ok = ok, running = running)

    private fun source(url: String, title: String = url) = SourceRef(title = title, url = url)

    private fun okMeta(durationMs: Long = 41_000L) =
        TurnMetaUi(status = "ok", steps = 2, durationMs = durationMs, costUsd = null)

    private fun kindOf(block: TurnBlock): String = when (block) {
        is UserMessageBlock -> "user"
        is ProcessControlBlock -> "control"
        is GroupHeaderBlock -> "group-header"
        is GroupBodyStreamingBlock -> "group-body"
        is GroupMemberBlock -> "member:${block.member.kind}"
        is ReplyInlineReasoningBlock -> "inline-reasoning"
        is ReplyMarkdownBlock -> "reply"
        is MaxTokensBlock -> "max-tokens"
        is TurnErrorBlock -> "error"
        is SourcesRowBlock -> "sources"
        is ActionsBarBlock -> "actions"
        is StreamStatusBlock -> "stream-status"
        is LoadHistoryBlock -> "load-history"
    }

    private fun mixedInput() = ChatTimelineInput(
        messages = listOf(user("u1", 1), assistant("a1", 1)),
        timelines = mapOf(
            1L to listOf(reasoning("想一下"), tool("web_search"), reasoning("再想"), tool("read_url")),
        ),
        metas = mapOf(1L to okMeta()),
        turnSources = mapOf(
            1L to listOf(source("https://a.example"), source("https://a.example"), source("https://b.example")),
        ),
    )

    // ---------------------------------------------------------------- §1.4 顺序

    @Test
    fun `mixed turn follows section 1_4 order keys and spacing`() {
        val blocks = TurnGrouping.group(mixedInput(), TurnExpansionState.expandedProcess(1L))

        assertEquals(
            listOf(
                "user",
                "control",
                "group-header",
                "member:reasoning",
                "member:tool",
                "member:reasoning",
                "member:tool",
                "reply",
                "sources",
                "actions",
            ),
            blocks.map(::kindOf),
        )

        // §1.4 稳定 key（CHAT-06 / CHAT-57）
        assertEquals(
            listOf(
                "1/user",
                "1/process-control",
                "1/group:0/header",
                "1/group:0/member:0",
                "1/group:0/member:1",
                "1/group:0/member:2",
                "1/group:0/member:3",
                "1/reply",
                "1/sources",
                "1/actions",
            ),
            blocks.map { it.key },
        )

        // turn.process-control 锚在第一个过程成员之前（CHAT-06）
        val controlIndex = blocks.indexOfFirst { it is ProcessControlBlock }
        val firstMemberIndex = blocks.indexOfFirst { it is GroupMemberBlock }
        assertTrue(controlIndex >= 0)
        assertTrue(firstMemberIndex > controlIndex)
        assertEquals("1/user", blocks[controlIndex - 1].key)

        // §1.4 前间距 / §1.5 间距总表
        assertEquals(
            listOf(12, 12, 16, 6, 6, 6, 6, 12, 12, 6),
            blocks.map { it.spacingBeforeDp },
        )

        // 控制条文案（§3.4 字段 10）：41 秒不钳
        assertEquals("已完成，用时 41 秒", (blocks[1] as ProcessControlBlock).label)
        // 来源 N = 按 URL 去重数（CHAT-54）
        assertEquals(2, (blocks[8] as SourcesRowBlock).count)
        assertEquals(
            listOf("https://a.example", "https://b.example"),
            (blocks[8] as SourcesRowBlock).sources.map { it.url },
        )
        // 轮尾最后一条助手消息 → 操作条（CHAT-28）
        assertTrue((blocks[9] as ActionsBarBlock).regenerateEnabled)
    }

    @Test
    fun `closed ok turn is collapsed by default and renders no group header`() {
        // C-M3-01 / §2 状态 3：定稿轮过程默认折叠 → 只剩控制条（总开关）
        val blocks = TurnGrouping.group(mixedInput())

        assertEquals(
            listOf("user", "control", "reply", "sources", "actions"),
            blocks.map(::kindOf),
        )
        assertTrue(blocks.none { it is GroupHeaderBlock })
        assertFalse((blocks[1] as ProcessControlBlock).expanded)
    }

    @Test
    fun `multiple reasoning blocks stay in one group and preserve order`() {
        val input = mixedInput().copy(
            timelines = mapOf(
                1L to listOf(reasoning("第一段"), reasoning("第二段"), tool("web_search"), reasoning("第三段")),
            ),
        )
        val blocks = TurnGrouping.group(input, TurnExpansionState.expandedProcess(1L))
        val members = blocks.filterIsInstance<GroupMemberBlock>()

        assertEquals(4, members.size)
        assertEquals(
            listOf("reasoning", "reasoning", "tool", "reasoning"),
            members.map { it.member.kind },
        )
        assertEquals(listOf(0, 1, 2, 3), members.map { it.memberIndex })
        assertEquals(listOf("第一段", "第二段", "第三段"), members.filter { it.member.kind == "reasoning" }.map { it.member.text })
        // 同一段组：segmentIndex 全为 0
        assertEquals(setOf(0), members.map { it.segmentIndex }.toSet())
    }

    @Test
    fun `multi segment projection compatibility keeps 16dp between groups`() {
        // §1.4「多段只是投影兼容」：显式段入口
        val input = mixedInput().copy(
            timelines = emptyMap(),
            processSegments = mapOf(
                1L to listOf(listOf(reasoning("段 0")), listOf(tool("read_url"))),
            ),
        )
        val blocks = TurnGrouping.group(input, TurnExpansionState.expandedProcess(1L))
        val headers = blocks.filterIsInstance<GroupHeaderBlock>()

        assertEquals(listOf(0, 1), headers.map { it.segmentIndex })
        // 段 0 在控制条后 = 16；段 i>0 = 16（§1.4 第 3 行 / §1.5）
        assertEquals(listOf(16, 16), headers.map { it.spacingBeforeDp })
        assertEquals(2, blocks.count { it is GroupMemberBlock })
        // 第二个组头紧跟第一组最后一个成员之后
        val secondHeaderIndex = blocks.indexOf(headers[1])
        assertTrue(blocks[secondHeaderIndex - 1] is GroupMemberBlock)
    }

    // ---------------------------------------------------------------- 流式 / 空窗

    @Test
    fun `live turn uses capped group body and streaming reply`() {
        val input = ChatTimelineInput(
            messages = listOf(user("u1", 1)),
            live = LiveTurnInput(
                turnId = 1L,
                entries = listOf(reasoning("在想", running = true), tool("web_search", ok = null, running = true)),
                streamingText = "回答正在流式……",
            ),
        )
        val blocks = TurnGrouping.group(input)

        assertEquals(
            listOf("user", "group-header", "group-body", "reply", "actions"),
            blocks.map(::kindOf),
        )
        val header = blocks[1] as GroupHeaderBlock
        assertTrue(header.live)
        assertTrue(header.expanded)
        assertTrue(header.runningTool)
        assertTrue(header.runningReasoning)

        val body = blocks[2] as GroupBodyStreamingBlock
        assertEquals(2, body.members.size)
        assertEquals(0, body.spacingBeforeDp)

        val reply = blocks[3] as ReplyMarkdownBlock
        assertTrue(reply.streaming)
        assertFalse(reply.interrupted)

        val actions = blocks[4] as ActionsBarBlock
        assertTrue(actions.streaming)
        // §3.16 字段 5 / CHAT-32：生成中重新生成/版本切换禁用，复制可用
        assertFalse(actions.regenerateEnabled)
        assertFalse(actions.versionSwitchEnabled)
        assertTrue(actions.generatingHintVisible)
        // 流式期不出现控制条（只 closed 才出现，§3.4 字段 1）
        assertTrue(blocks.none { it is ProcessControlBlock })
    }

    @Test
    fun `pending first token renders stream status mutually exclusive with content`() {
        val input = ChatTimelineInput(
            live = LiveTurnInput(turnId = null, userText = "在吗"),
        )
        val blocks = TurnGrouping.group(input)

        assertEquals(listOf("user", "stream-status"), blocks.map(::kindOf))
        assertEquals("live/user", blocks[0].key)
        assertTrue((blocks[0] as UserMessageBlock).live)
        assertEquals("live/pending", blocks[1].key)
        assertEquals(listOf(12, 12), blocks.map { it.spacingBeforeDp })
    }

    // ---------------------------------------------------------------- 错误 / 停止 / 上限

    @Test
    fun `error turn keeps process visible without control bar`() {
        val input = mixedInput().copy(
            metas = mapOf(1L to TurnMetaUi(status = "error", steps = 2, durationMs = null, costUsd = null)),
            turnErrors = mapOf(
                1L to TurnErrorUi(code = "NETWORK_TIMEOUT", text = "网络超时", retriable = false),
            ),
        )
        val blocks = TurnGrouping.group(input)

        // CHAT-44：失败轮不渲染控制条、过程保持展开；错误行是独立根（在回答之后）
        assertTrue(blocks.none { it is ProcessControlBlock })
        assertTrue(blocks.any { it is GroupHeaderBlock })
        assertEquals(
            listOf("user", "group-header", "member:reasoning", "member:tool", "member:reasoning", "member:tool", "reply", "error", "sources", "actions"),
            blocks.map(::kindOf),
        )
        val error = blocks.filterIsInstance<TurnErrorBlock>().single()
        assertEquals("1/error", error.key)
        assertEquals("NETWORK_TIMEOUT", error.code)
        // CHAT-46：retriable=false → 不给重试按钮
        assertFalse(error.showRetry)
    }

    @Test
    fun `retriable is opt in only when explicitly true`() {
        fun errorBlock(retriable: Boolean?): TurnErrorBlock {
            val input = mixedInput().copy(
                metas = mapOf(1L to TurnMetaUi("error", 1, null, null)),
                turnErrors = mapOf(1L to TurnErrorUi(code = "E", text = "x", retriable = retriable)),
            )
            return TurnGrouping.group(input).filterIsInstance<TurnErrorBlock>().single()
        }
        assertTrue(errorBlock(true).showRetry)
        assertFalse(errorBlock(false).showRetry)
        assertFalse(errorBlock(null).showRetry)
    }

    @Test
    fun `stopped turn appends interrupted suffix to reply and uses stopped control label`() {
        val input = mixedInput().copy(
            metas = mapOf(1L to TurnMetaUi(status = "stopped", steps = 2, durationMs = 8_000L, costUsd = null)),
        )
        val blocks = TurnGrouping.group(input)

        // 停止轮：不渲染控制条（§3.4 字段 1），正文末行尾标记「已停止」（CHAT-43）
        assertTrue(blocks.none { it is ProcessControlBlock })
        val reply = blocks.filterIsInstance<ReplyMarkdownBlock>().single()
        assertTrue(reply.interrupted)
        // 控制条文案规则本身（即使不渲染）也是「已停止」
        assertEquals(
            "已停止",
            TurnStatus.project(1L, TurnMetaUi("stopped", 2, 8_000L, null), live = false)
                .processControlLabel(TurnMetaUi("stopped", 2, 8_000L, null)),
        )
    }

    @Test
    fun `max tokens renders independent root without continue button`() {
        val input = mixedInput().copy(
            metas = mapOf(1L to TurnMetaUi(status = "truncated", steps = 2, durationMs = 5_000L, costUsd = null)),
        )
        val blocks = TurnGrouping.group(input)

        val maxTokens = blocks.filterIsInstance<MaxTokensBlock>().single()
        assertEquals("1/max-tokens", maxTokens.key)
        // CHAT-47：独立根行，在回答之后、错误/来源之前
        val kindOrder = blocks.map(::kindOf)
        assertTrue(kindOrder.indexOf("max-tokens") > kindOrder.indexOf("reply"))
        assertTrue(blocks.none { it is TurnErrorBlock })
        // 控制条仍出现（truncated 不是 aborted/error）
        assertTrue(blocks.any { it is ProcessControlBlock })
    }

    // ---------------------------------------------------------------- 来源 / 操作条 / 加载

    @Test
    fun `sources row absent when turn has no sources`() {
        val input = mixedInput().copy(turnSources = emptyMap(), timelines = emptyMap())
        val blocks = TurnGrouping.group(input, TurnExpansionState.expandedProcess(1L))
        assertTrue(blocks.none { it is SourcesRowBlock })
    }

    @Test
    fun `actions bar only renders on the last assistant turn`() {
        val input = ChatTimelineInput(
            messages = listOf(
                user("u1", 1), assistant("a1", 1),
                user("u2", 2), assistant("a2", 2),
            ),
            metas = mapOf(1L to okMeta(), 2L to okMeta()),
        )
        val blocks = TurnGrouping.group(input)
        val actions = blocks.filterIsInstance<ActionsBarBlock>()

        assertEquals(1, actions.size) // CHAT-28
        assertEquals(2L, actions.single().turnId)
        // §1.5：操作条 → 下一轮 user 24
        val secondUser = blocks.filterIsInstance<UserMessageBlock>().last()
        assertEquals(24, secondUser.spacingBeforeDp)
        // 第一轮无过程内容 → 不渲染空组头（§1.4）
        assertTrue(blocks.none { it is GroupHeaderBlock })
    }

    @Test
    fun `load history block comes first and does not break first block spacing`() {
        val input = ChatTimelineInput(
            messages = listOf(user("u1", 1), assistant("a1", 1)),
            loadingHistory = true,
        )
        val blocks = TurnGrouping.group(input)
        val first = blocks.first()

        assertTrue(first is LoadHistoryBlock)
        assertEquals("list/load-history", first.key)
        assertEquals(0, first.spacingBeforeDp)
        assertEquals(12, blocks[1].spacingBeforeDp) // 首块顶部仍 12
    }

    @Test
    fun `keys stay stable when a live turn grows`() {
        // CHAT-57：流式追加时已渲染块的 key 不重建
        val baseInput = mixedInput()
        val base = TurnGrouping.group(baseInput, TurnExpansionState.expandedProcess(1L))
        val baseKeys = base.map { it.key }

        val grown = baseInput.copy(
            messages = baseInput.messages + user("u2", 2),
            live = LiveTurnInput(
                turnId = 2L,
                entries = listOf(tool("read_url")),
                streamingText = "新回答",
            ),
        )
        val grownKeys = TurnGrouping.group(grown, TurnExpansionState.expandedProcess(1L)).map { it.key }

        // 历史块 key 原样保留（新轮只会追加；操作条归属轮尾，可能随新轮移动）
        val historicalKeys = base.filterNot { it is ActionsBarBlock }.map { it.key }
        assertTrue(grownKeys.containsAll(historicalKeys))
        assertTrue(baseKeys.isNotEmpty())
        // 同输入两次分组结果一致（纯函数）
        assertEquals(baseKeys, TurnGrouping.group(baseInput, TurnExpansionState.expandedProcess(1L)).map { it.key })
    }

    @Test
    fun `inline reasoning is flushed out of the group and defaults collapsed`() {
        val input = mixedInput().copy(
            timelines = mapOf(1L to listOf(reasoning("属于回答 step 的思考"), tool("web_search"))),
            inlineReasoning = setOf(TimelineEntryRef(turnId = 1L, index = 0)),
        )
        val blocks = TurnGrouping.group(input, TurnExpansionState.expandedProcess(1L))

        val inline = blocks.filterIsInstance<ReplyInlineReasoningBlock>().single()
        assertEquals("1/reply:reasoning", inline.key)
        assertFalse(inline.expanded) // 默认折叠（§3.7 字段 1）
        assertEquals(12, inline.spacingBeforeDp)
        // 内联 reasoning 不再进组
        val members = blocks.filterIsInstance<GroupMemberBlock>()
        assertEquals(listOf("tool"), members.map { it.member.kind })
        // 在回答之前
        val kindOrder = blocks.map(::kindOf)
        assertTrue(kindOrder.indexOf("inline-reasoning") < kindOrder.indexOf("reply"))
    }

    // ---------------------------------------------------------------- TurnStatus（形态 D）

    @Test
    fun `form D switches capped streaming to flat settled`() {
        val streaming = TurnStatus.project(1L, meta = null, live = true, liveHasContent = true)
        assertEquals(TurnForm.CAPPED_STREAMING, streaming.form)
        assertEquals(1, streaming.innerVerticalScrollLayers(hasProcessContent = true)) // CHAT-17：仅组体一层
        assertFalse(streaming.groupHeaderSticky)

        val settled = TurnStatus.project(1L, meta = okMeta(), live = false)
        assertEquals(TurnForm.FLAT_SETTLED, settled.form)
        assertEquals(0, settled.innerVerticalScrollLayers(hasProcessContent = true)) // CHAT-11：拍平无内滚
        assertTrue(settled.groupHeaderSticky)
    }

    @Test
    fun `process control visible only for closed non aborted turns`() {
        assertTrue(TurnStatus.project(1L, okMeta(), live = false).processControlVisible(hasProcessContent = true))
        assertFalse(TurnStatus.project(1L, okMeta(), live = false).processControlVisible(hasProcessContent = false))
        assertFalse(
            TurnStatus.project(1L, TurnMetaUi("error", 0, null, null), live = false)
                .processControlVisible(hasProcessContent = true),
        )
        assertFalse(
            TurnStatus.project(1L, TurnMetaUi("stopped", 0, null, null), live = false)
                .processControlVisible(hasProcessContent = true),
        )
        // 直播轮没有控制条
        assertFalse(TurnStatus.project(1L, null, live = true, liveHasContent = true).processControlVisible(true))
    }

    @Test
    fun `control label clamps duration to at least one second`() {
        val settled = TurnStatus.project(1L, okMeta(durationMs = 300L), live = false)
        assertEquals("已完成，用时 1 秒", settled.processControlLabel(okMeta(durationMs = 300L)))
        assertEquals("已完成，用时 41 秒", settled.processControlLabel(okMeta(durationMs = 41_000L)))
        assertEquals("已完成", settled.processControlLabel(TurnMetaUi("ok", 1, null, null)))
        assertNull(TurnStatus.project(1L, TurnMetaUi("error", 0, null, null), live = false).processControlLabel(null))
    }

    @Test
    fun `interrupted suffix only for stopped turns with reply text`() {
        val stopped = TurnStatus.project(1L, TurnMetaUi("stopped", 1, 1_000L, null), live = false)
        assertTrue(stopped.interruptedSuffixVisible(hasReplyText = true))
        assertFalse(stopped.interruptedSuffixVisible(hasReplyText = false))
        assertFalse(TurnStatus.project(1L, okMeta(), live = false).interruptedSuffixVisible(true))
    }

    @Test
    fun `auto expanded process collapses on settle but manual expansion survives`() {
        val settled = TurnStatus.project(1L, okMeta(), live = false)
        // CHAT-18
        assertFalse(settled.processExpandedAfterSettle(ExpansionOrigin.AUTO))
        assertTrue(settled.processExpandedAfterSettle(ExpansionOrigin.MANUAL))
        assertFalse(settled.reasoningExpandedAfterSettle(ExpansionOrigin.AUTO))
        assertTrue(settled.reasoningExpandedAfterSettle(ExpansionOrigin.MANUAL))
    }

    @Test
    fun `tool row states map from timeline entry and turn status`() {
        val running = TurnStatus.project(1L, okMeta(), live = true, liveHasContent = true)
        val closedOk = TurnStatus.project(1L, okMeta(), live = false)
        val stopped = TurnStatus.project(1L, TurnMetaUi("stopped", 1, null, null), live = false)

        assertEquals(ToolRowState.RUNNING, running.toolRowState(tool("web_search", ok = null, running = true)))
        assertEquals(ToolRowState.SUCCEEDED, closedOk.toolRowState(tool("web_search", ok = true)))
        assertEquals(ToolRowState.FAILED, closedOk.toolRowState(tool("web_search", ok = false)))
        // 被打断且工具仍显示运行中 → 「已停止」（琥珀）
        assertEquals(ToolRowState.STOPPED, stopped.toolRowState(tool("web_search", ok = null, running = true)))
        // 有行无结果 → 「准备中」，不可展开
        assertEquals(ToolRowState.PREPARING, closedOk.toolRowState(tool("web_search", ok = null, running = false)))
    }
}
