// 验收 ID：CHAT-04 CHAT-16 CHAT-21 CHAT-22 CHAT-25 CHAT-29 CHAT-32 CHAT-33 CHAT-45 CHAT-46 CHAT-47
//          CHAT-20 CHAT-54 CHAT-67
package dev.myco.android.ui.m5.chat.ui

import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.m5.chat.model.ActionsBarBlock
import dev.myco.android.ui.m5.chat.model.TurnErrorBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 各 block 渲染契约的纯逻辑单测（§3.5 组头文案 / §3.7 摘要 / §3.8 工具摘要 / §3.9 井截断 /
 * §3.16 操作条 / §3.18 状态行）。视觉与命中盒的真机验收不在本文件（无 Compose UI 测试依赖）。
 */
class ChatBlockContractTest {

    // ------------------------------------------------------------ §3.5 组头文案（CHAT-16）

    @Test
    fun `closed group title lists categories without counts`() {
        val members = listOf(
            TimelineEntry(kind = "tool", name = "web_search", ok = true),
            TimelineEntry(kind = "tool", name = "read_url", ok = true),
            TimelineEntry(kind = "reasoning", text = "想一下", ok = true),
        )
        val title = GroupHeaderText.closedTitle(members)
        assertEquals("已搜索网页、读取网页", title)
        // CHAT-16：不出现计数（"2 个"/"3 类"）
        assertFalse(title.contains("个"))
        assertFalse(title.contains("2"))
        assertFalse(title.contains("3"))
    }

    @Test
    fun `closed group title collapses more than three categories with a trailing marker`() {
        val members = listOf(
            TimelineEntry(kind = "tool", name = "web_search"),
            TimelineEntry(kind = "tool", name = "read_url"),
            TimelineEntry(kind = "tool", name = "ocr_image"),
            TimelineEntry(kind = "tool", name = "time_now"),
        )
        val title = GroupHeaderText.closedTitle(members)
        assertEquals("已搜索网页、读取网页、识别图片 等", title)
        assertFalse(title.contains("4"))
    }

    @Test
    fun `closed group with only reasoning falls back to a neutral category`() {
        val title = GroupHeaderText.closedTitle(listOf(TimelineEntry(kind = "reasoning", text = "x")))
        assertEquals(GroupHeaderText.SETTLED_FALLBACK, title)
        assertFalse(title.contains("1"))
    }

    @Test
    fun `live group title reflects what is running`() {
        assertEquals(
            GroupHeaderText.LIVE_TOOL,
            GroupHeaderText.liveTitle(listOf(TimelineEntry(kind = "tool", name = "web_search", running = true))),
        )
        assertEquals(
            GroupHeaderText.LIVE_REASONING,
            GroupHeaderText.liveTitle(listOf(TimelineEntry(kind = "reasoning", text = "x", running = true))),
        )
        // 工具 preparing：ok == null && !running
        assertEquals(
            GroupHeaderText.LIVE_PREPARING,
            GroupHeaderText.liveTitle(listOf(TimelineEntry(kind = "tool", name = "read_url", ok = null, running = false))),
        )
        assertEquals(
            GroupHeaderText.LIVE_IDLE,
            GroupHeaderText.liveTitle(listOf(TimelineEntry(kind = "text", text = "中间步"))),
        )
    }

    @Test
    fun `live title wins over closed title`() {
        val members = listOf(TimelineEntry(kind = "tool", name = "web_search", running = true))
        assertEquals(GroupHeaderText.LIVE_TOOL, GroupHeaderText.title(members, live = true))
        assertEquals("已搜索网页", GroupHeaderText.title(members, live = false))
    }

    @Test
    fun `unknown tool category uses the raw name without counts`() {
        assertEquals("调用 custom_tool", GroupHeaderText.categoryOf("custom_tool"))
    }

    // ------------------------------------------------------------ §3.7 思考行摘要（CHAT-04）

    @Test
    fun `settled reasoning summary is the first non empty line without bold markers`() {
        val text = "\n\n**先看需求**，再决定\n第二行"
        assertEquals("先看需求，再决定", ReasoningSummary.of(text, running = false))
    }

    @Test
    fun `running reasoning summary uses the last completed paragraph`() {
        // 流式中最后一段还在长 → 取它之前最后一个完成段落的首行
        val text = "第一段完成\n\n**第二段**完成\n\n第三段还在"
        assertEquals("第二段完成", ReasoningSummary.of(text, running = true))
    }

    @Test
    fun `running reasoning with a single unfinished paragraph falls back to it`() {
        val text = "只有一个未完成段落"
        assertEquals("只有一个未完成段落", ReasoningSummary.of(text, running = true))
    }

    @Test
    fun `blank reasoning summary is empty and hides dots`() {
        assertEquals("", ReasoningSummary.of("   \n\n  ", running = false))
        assertEquals("", ReasoningSummary.of("", running = true))
    }

    @Test
    fun `summary strips inline code ticks and leading hashes`() {
        assertEquals("标题", ReasoningSummary.stripInlineMarkers("## 标题"))
        assertEquals("用 foo 调", ReasoningSummary.stripInlineMarkers("用 `foo` 调"))
    }

    // ------------------------------------------------------------ §3.8 工具行摘要（CHAT-21）

    @Test
    fun `tool summary is display name plus object basename`() {
        val entry = TimelineEntry(
            kind = "tool",
            name = "read_url",
            args = """{"url":"https://example.com/docs/page?q=1"}""",
        )
        // 对象只取 basename（这里 URL → 域名），不显示完整 URL/参数
        assertEquals("读取网页 · example.com", ToolRowText.summary(entry))
    }

    @Test
    fun `tool summary uses path basename for files`() {
        val entry = TimelineEntry(
            kind = "tool",
            name = "read_file",
            args = """{"path":"/home/user/project/src/Main.kt"}""",
        )
        assertEquals("read_file · Main.kt", ToolRowText.summary(entry))
    }

    @Test
    fun `tool summary omits the separator when there is no object`() {
        val entry = TimelineEntry(kind = "tool", name = "time_now", args = "{}")
        assertEquals("读取时间", ToolRowText.summary(entry))
    }

    @Test
    fun `tool summary strips newlines and caps at 100 chars`() {
        val longName = "x".repeat(300)
        val entry = TimelineEntry(kind = "tool", name = longName, args = """{"query":"a\nb"}""")
        val summary = ToolRowText.summary(entry)
        assertTrue(summary.length <= ToolRowText.MAX_SUMMARY_CHARS)
        assertFalse(summary.contains("\n"))
    }

    @Test
    fun `tool summary never leaks the full argument string`() {
        val entry = TimelineEntry(
            kind = "tool",
            name = "web_search",
            args = """{"query":"secret query text","extra":"long parameter payload"}""",
        )
        val summary = ToolRowText.summary(entry)
        assertFalse(summary.contains("extra"))
        assertFalse(summary.contains("payload"))
        // 对象 basename 会保留 query 本身（规格要"对象"），但不含其它参数
        assertTrue(summary.startsWith("搜索网页 · "))
    }

    @Test
    fun `invalid args json degrades to name only`() {
        val entry = TimelineEntry(kind = "tool", name = "read_url", args = "{not json")
        assertEquals("读取网页", ToolRowText.summary(entry))
    }

    // ------------------------------------------------------------ §3.9 井截断（CHAT-25）

    @Test
    fun `web search preview keeps at most eight results and reports hidden count`() {
        val sources = (1..11).map { SourceRef(title = "标题$it", url = "https://site$it.com/a", site = null) }
        val preview = ToolWellPreview.preview(TimelineEntry(kind = "tool", name = "web_search", sources = sources))
        assertEquals(ToolWellPreview.MAX_RESULTS, preview.lines.size)
        assertEquals(3, preview.hidden)
        assertTrue(preview.hasMore)
        assertTrue(preview.lines.first().startsWith("标题1 — site1.com"))
    }

    @Test
    fun `text preview keeps at most eight lines`() {
        val text = (1..12).joinToString("\n") { "line $it" }
        val preview = ToolWellPreview.fromText(text)
        assertEquals(8, preview.lines.size)
        assertEquals(4, preview.hidden)
    }

    @Test
    fun `empty tool output preview is empty`() {
        val preview = ToolWellPreview.fromText("")
        assertTrue(preview.lines.isEmpty())
        assertFalse(preview.hasMore)
    }

    @Test
    fun `unknown tool payload is pretty printed as text`() {
        val preview = ToolWellPreview.fromText("""{"a":1,"b":2}""")
        assertTrue(preview.lines.any { it.contains("\"a\"") })
    }

    // ------------------------------------------------------------ 全文页截断（§3.9 / CHAT-25）

    @Test
    fun `short tool output is not truncated`() {
        val result = ToolFullText.truncate((1..10).joinToString("\n"))
        assertFalse(result.truncated)
        assertEquals(0, result.hiddenLines)
    }

    @Test
    fun `long tool output keeps head 60 and tail 140 with an explicit omitted marker`() {
        val lines = (1..400).map { "L$it" }
        val result = ToolFullText.truncate(lines.joinToString("\n"))
        assertTrue(result.truncated)
        assertEquals(400 - ToolFullText.HEAD_LINES - ToolFullText.TAIL_LINES, result.hiddenLines)
        assertTrue(result.text.contains("L1\n")) // 头保留
        assertTrue(result.text.contains("L400")) // 尾保留
        assertFalse(result.text.contains("L61\n")) // 中间被省略
        // 不静默截断：必须有「省略 N 行」提示
        assertTrue(result.text.contains("省略 ${result.hiddenLines} 行"))
        assertTrue(result.text.length <= ToolFullText.MAX_CHARS)
    }

    @Test
    fun `tool output over the char budget is clipped to 16000`() {
        val result = ToolFullText.truncate("x".repeat(40_000))
        assertTrue(result.truncated)
        assertTrue(result.text.length <= ToolFullText.MAX_CHARS)
    }

    // ------------------------------------------------------------ §3.16 操作条契约（CHAT-29/32/33）

    @Test
    fun `action bar key order and sizes are frozen`() {
        assertEquals(
            listOf(
                MessageActionsContract.Key.COPY,
                MessageActionsContract.Key.REGENERATE,
                MessageActionsContract.Key.VERSION,
            ),
            MessageActionsContract.KEY_ORDER,
        )
        assertEquals(40f, MessageActionsContract.KEY_SIZE_DP, 0.001f)
        assertEquals(20f, MessageActionsContract.ICON_SIZE_DP, 0.001f)
        assertEquals(40f, MessageActionsContract.BAR_HEIGHT_DP, 0.001f)
        assertEquals(40f, ChatListMetrics.ACTION_KEY_SIZE_DP, 0.001f)
    }

    @Test
    fun `only copy stays enabled while streaming`() {
        // CHAT-32：生成中复制可用，重新生成/版本切换禁用
        assertTrue(MessageActionsContract.enabledWhileStreaming(MessageActionsContract.Key.COPY))
        assertFalse(MessageActionsContract.enabledWhileStreaming(MessageActionsContract.Key.REGENERATE))
        assertFalse(MessageActionsContract.enabledWhileStreaming(MessageActionsContract.Key.VERSION))
        // model 层 block 的开关必须与契约一致
        val streaming = ActionsBarBlock("t/actions", 1L, 6, message = null, streaming = true)
        assertTrue(streaming.generatingHintVisible)
        assertFalse(streaming.regenerateEnabled)
        assertFalse(streaming.versionSwitchEnabled)
        val settled = ActionsBarBlock("t/actions", 1L, 6, message = null, streaming = false)
        assertFalse(settled.generatingHintVisible)
        assertTrue(settled.regenerateEnabled)
        assertTrue(settled.versionSwitchEnabled)
    }

    @Test
    fun `generating hint is visible only while streaming`() {
        assertEquals(MessageActionsContract.GENERATING_HINT, MessageActionsContract.visibleReason(true))
        assertEquals(null, MessageActionsContract.visibleReason(false))
    }

    @Test
    fun `version switcher hides at one version and describes count accessibly`() {
        // CHAT-33：M=1 不渲染；计数可读「第 N 版，共 M 版」
        assertFalse(MessageVersion(index = 1, total = 1).visible)
        assertTrue(MessageVersion(index = 2, total = 3).visible)
        assertEquals("2/3", MessageActionsContract.versionLabel(2, 3))
        assertEquals("第 2 版，共 3 版", MessageActionsContract.versionDescription(2, 3))
        val first = MessageVersion(index = 1, total = 3)
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)
        val last = MessageVersion(index = 3, total = 3)
        assertTrue(last.hasPrevious)
        assertFalse(last.hasNext)
    }

    @Test
    fun `copy feedback window is 1000ms`() {
        assertEquals(1000L, MessageActionsContract.COPY_FEEDBACK_MS)
    }

    // ------------------------------------------------------------ §3.18 / §3.19 状态行（CHAT-43/46/47）

    @Test
    fun `retry is only shown for explicit retriable true`() {
        // CHAT-46：false 或缺失 → 不给按钮
        assertTrue(StatusRowContract.showRetry(true))
        assertFalse(StatusRowContract.showRetry(false))
        assertFalse(StatusRowContract.showRetry(null))
        val block = TurnErrorBlock("t/error", 1L, 12, code = null, text = "x", retriable = null)
        assertFalse(block.showRetry)
        assertTrue(TurnErrorBlock("t/error", 1L, 12, null, "x", true).showRetry)
        assertEquals(false, TurnErrorBlock("t/error", 1L, 12, null, "x", false).showRetry)
    }

    @Test
    fun `error row height is 36 without retry and 40 with retry`() {
        assertEquals(36f, StatusRowContract.errorRowHeightDp(false), 0.001f)
        assertEquals(40f, StatusRowContract.errorRowHeightDp(true), 0.001f)
        assertEquals(40f, StatusRowContract.RETRY_SIZE_DP, 0.001f)
        assertEquals(8f, StatusRowContract.DOT_SIZE_DP, 0.001f)
    }

    @Test
    fun `error and max tokens copy is frozen`() {
        assertEquals("本轮运行失败", StatusRowContract.ERROR_TITLE)
        assertEquals("已达到输出 token 上限，回答被截断", StatusRowContract.MAX_TOKENS_TITLE)
        assertEquals("发送「继续」可让模型接着输出", StatusRowContract.MAX_TOKENS_HINT)
        assertEquals("已停止", StatusRowContract.INTERRUPTED_LABEL)
        // CHAT-47 / Q-09：输出上限不给「继续」按钮（常量里只有建议文案，没有按钮）
        assertFalse(StatusRowContract.MAX_TOKENS_HINT.contains("点击"))
    }

    @Test
    fun `retry description explains idempotent resend without a new version`() {
        // §3.19 字段 11：可读名含「不新增版本」
        assertTrue(StatusRowContract.RETRY_DESCRIPTION.contains("不新增版本"))
        assertTrue(StatusRowContract.RETRY_DESCRIPTION.contains("重试"))
    }

    // ------------------------------------------------------------ 尺寸常量一致性（跨文件单一真源）

    @Test
    fun `shared size constants agree across contracts`() {
        // 操作条 / 过程行 / 来源行 / 回底钮 全是 §1.3 的规格值，避免两处数字漂移
        assertEquals(32f, ChatListMetrics.PROCESS_ROW_BASE_DP, 0.001f)
        assertEquals(33f, ChatListMetrics.PROCESS_CONTROL_BASE_DP, 0.001f)
        assertEquals(44f, ChatListMetrics.SOURCES_ROW_HEIGHT_DP, 0.001f)
        assertEquals(44f, ChatListMetrics.TO_BOTTOM_SIZE_DP, 0.001f)
        assertEquals(40f, ChatListMetrics.ACTIONS_BAR_HEIGHT_DP, 0.001f)
        assertEquals(MessageActionsContract.BAR_HEIGHT_DP, ChatListMetrics.ACTIONS_BAR_HEIGHT_DP, 0.001f)
    }

    @Test
    fun `process row hit box bleeds 8dp past the content column`() {
        // G-16 / CHAT-20：内容列 20dp 内边距 + 8dp Bleed → 命中盒 x=12..W−12
        assertEquals(8f, ChatListMetrics.PROCESS_ROW_BLEED_DP, 0.001f)
        assertEquals(12f, ChatListMetrics.processRowHitInsetDp(), 0.001f)
        assertEquals(12f, ChatListMetrics.processRowHitInsetDp(20f), 0.001f)
        // 内边距不足 Bleed 时不为负
        assertEquals(0f, ChatListMetrics.processRowHitInsetDp(4f), 0.001f)
    }

    @Test
    fun `live group header title dwells at least 150ms`() {
        // CHAT-15：live 标题最短驻留 150ms
        assertEquals(150L, GroupHeaderDwell.DWELL_MS)
    }
}
