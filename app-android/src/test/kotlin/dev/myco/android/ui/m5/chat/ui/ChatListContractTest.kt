// 验收 ID：CHAT-07 CHAT-36 CHAT-38 CHAT-39 CHAT-48 CHAT-50 CHAT-57 CHAT-59
package dev.myco.android.ui.m5.chat.ui

import dev.myco.android.ui.m5.chat.model.NestedScrollArbiter
import dev.myco.android.ui.m5.chat.model.ScrollFollowPolicy
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表级纯契约单测（`docs/spec/panels/chat.md` §1.3 网格 / §1.6 cap / §3.1 跟随 / §3.2 回底钮 / §3.11 节流）。
 *
 * 只测"可测的纯逻辑/稳定契约"；UI 视觉由截图/真机验收（本模块无 Compose UI 测试依赖）。
 */
class ChatListContractTest {

    private val policy = ScrollFollowPolicy()

    // ------------------------------------------------------------ §1.3 网格

    @Test
    fun `content column width is min of column minus 40 and 752`() {
        // 手机：360 − 40 = 320
        assertEquals(320f, ChatListMetrics.contentColumnWidthDp(360f), 0.001f)
        // 宽屏：列宽 1000 → min(960, 752) = 752
        assertEquals(752f, ChatListMetrics.contentColumnWidthDp(1000f), 0.001f)
        // 三列壳给的聊天列（§1.1 D-04）：840 − 40 = 800 → 钳到 752
        assertEquals(752f, ChatListMetrics.contentColumnWidthDp(840f), 0.001f)
        // 列宽 = 792 → 752（正好）
        assertEquals(752f, ChatListMetrics.contentColumnWidthDp(792f), 0.001f)
        // 异常窄列不产生负数
        assertEquals(0f, ChatListMetrics.contentColumnWidthDp(20f), 0.001f)
    }

    @Test
    fun `content box max width is 752 plus two 20dp gutters`() {
        assertEquals(792f, ChatListMetrics.contentBoxMaxWidthDp(), 0.001f)
    }

    @Test
    fun `list bottom padding is composer height plus 24`() {
        // §1.3：底部留白 = 24dp + composer 实时高（E-02）
        assertEquals(24f, ChatListMetrics.listBottomPaddingDp(0f), 0.001f)
        assertEquals(144f, ChatListMetrics.listBottomPaddingDp(120f), 0.001f)
        assertEquals(24f, ChatListMetrics.listBottomPaddingDp(-10f), 0.001f)
    }

    @Test
    fun `to bottom button sits composer height plus 16 above the bottom`() {
        // §3.2 字段 2：bottom = composer 实时高 + 16dp（不用魔数）
        assertEquals(16f, ChatListMetrics.toBottomBottomOffsetDp(0f), 0.001f)
        assertEquals(136f, ChatListMetrics.toBottomBottomOffsetDp(120f), 0.001f)
        assertEquals(44f, ChatListMetrics.TO_BOTTOM_SIZE_DP, 0.001f)
        assertEquals(16f, ChatListMetrics.TO_BOTTOM_END_DP, 0.001f)
    }

    @Test
    fun `user bubble max width is min of 0_86 screen and 560`() {
        // §3.13：min(0.86×屏宽, 560dp)
        assertEquals(309.6f, ChatListMetrics.userBubbleMaxWidthDp(360f), 0.01f)
        assertEquals(560f, ChatListMetrics.userBubbleMaxWidthDp(700f), 0.001f)
        assertEquals(560f, ChatListMetrics.userBubbleMaxWidthDp(1000f), 0.001f)
        assertEquals(0f, ChatListMetrics.userBubbleMaxWidthDp(0f), 0.001f)
    }

    // ------------------------------------------------------------ §1.3 Δ（大字号）

    @Test
    fun `process row height keeps 32 base until text line exceeds it`() {
        // CHAT-48：行高 = max(基准, 文字实测行高 + 上下内边距 8)
        assertEquals(32f, ChatListMetrics.processRowHeightDp(20f), 0.001f) // 常规 13/20
        assertEquals(32f, ChatListMetrics.processRowHeightDp(24f), 0.001f) // 正文 16/24
        assertEquals(38f, ChatListMetrics.processRowHeightDp(30f), 0.001f) // 大字号 22sp 行高
        assertEquals(33f, ChatListMetrics.processControlHeightDp(24f), 0.001f)
        assertEquals(38f, ChatListMetrics.processControlHeightDp(30f), 0.001f)
    }

    // ------------------------------------------------------------ §1.6 cap

    @Test
    fun `process group cap follows the spec table`() {
        // §1.6 换算表：360×800 → 300；412×915 → 357；平板/横屏 ≥800 → 400
        val small = dev.myco.android.ui.m5.chat.model.ChatCap(
            dev.myco.android.ui.m5.chat.model.ChatCapSpec.estimateAStableViewportHeightDp(
                screenHeightDp = 640f, systemInsetDp = 24f, topBarDp = 56f, composerHeightDp = 96f,
            ),
        )
        assertEquals(232f, small.capDp, 4f)
        val main = dev.myco.android.ui.m5.chat.model.ChatCap(600f)
        assertEquals(300f, main.capDp, 0.001f)
        assertEquals(357f, dev.myco.android.ui.m5.chat.model.ChatCap(715f).capDp, 0.5f)
        assertEquals(400f, dev.myco.android.ui.m5.chat.model.ChatCap(900f).capDp, 0.001f)
    }

    @Test
    fun `ime toggle never changes cap`() {
        // §1.6：键盘开合不改变 cap（否则过程组高度突变）
        val cap = dev.myco.android.ui.m5.chat.model.ChatCap(600f)
        assertEquals(cap, cap.withImeVisibility(true))
        assertEquals(cap.capDp, cap.withImeVisibility(false).capDp, 0.001f)
    }

    // ------------------------------------------------------------ §3.1 距底测量

    @Test
    fun `distance to bottom is last item end minus viewport end in dp`() {
        assertEquals(0f, ChatScrollMetrics.distanceToBottomDp(1200, 1200, 2f), 0.001f)
        assertEquals(25f, ChatScrollMetrics.distanceToBottomDp(1250, 1200, 2f), 0.001f)
        // 内容短于视口（lastEnd < viewportEnd）→ 0，不返回负数
        assertEquals(0f, ChatScrollMetrics.distanceToBottomDp(800, 1200, 2f), 0.001f)
        // 密度异常兜底
        assertEquals(0f, ChatScrollMetrics.distanceToBottomDp(1300, 1200, 0f), 0.001f)
    }

    @Test
    fun `follow and to bottom thresholds match the policy`() {
        // §3.1 25dp 跟随判据；§3.2 160 显示 / 24 隐藏
        assertTrue(ChatScrollMetrics.isNearBottom(25f, policy))
        assertFalse(ChatScrollMetrics.isNearBottom(25.1f, policy))
        assertFalse(ChatScrollMetrics.shouldShowToBottom(160f, policy))
        assertTrue(ChatScrollMetrics.shouldShowToBottom(160.1f, policy))
        // 纯契约对象不另立数值：与 ScrollFollowPolicy 默认参数一致
        assertEquals(policy.followThresholdDp, ChatScrollMetrics.FOLLOW_THRESHOLD_DP, 0.001f)
        assertEquals(policy.sampleIntervalMs, ChatScrollMetrics.SAMPLE_INTERVAL_MS)
    }

    // ------------------------------------------------------------ G-04 到边吸收

    @Test
    fun `mid gesture edge absorbs residual`() {
        // CHAT-09：手势开始时可滚 → 到边后的残余全量吸收（返回 true = 吞掉）
        val gesture = NestedScrollArbiter.beginGesture(canScrollForward = true, canScrollBackward = true)
        assertTrue(NestedScrollAbsorb.absorb(gesture, 40f))
        assertTrue(NestedScrollAbsorb.absorb(gesture, -40f))
    }

    @Test
    fun `new gesture started at edge hands over to outer list`() {
        // CHAT-10：手势一开始就在边缘 → 交外层（不吸收）
        val atBottom = NestedScrollArbiter.beginGesture(canScrollForward = false, canScrollBackward = true)
        assertFalse(NestedScrollAbsorb.absorb(atBottom, 40f))
        val atTop = NestedScrollArbiter.beginGesture(canScrollForward = true, canScrollBackward = false)
        assertFalse(NestedScrollAbsorb.absorb(atTop, -40f))
    }

    @Test
    fun `idle or zero delta never absorbs`() {
        assertFalse(NestedScrollAbsorb.absorb(NestedScrollArbiter.endGesture(), 40f))
        val active = NestedScrollArbiter.beginGesture(true, true)
        assertFalse(NestedScrollAbsorb.absorb(active, 0f))
    }

    // ------------------------------------------------------------ §3.11 流式节流

    @Test
    fun `markdown throttle formula is clamp of twice render cost`() {
        // 规格：clamp(耗时×2, 50, 2000)（CHAT-59 的区间断言）
        assertEquals(MarkdownStreamThrottle.FLOOR_MS, MarkdownStreamThrottle.intervalMs(0L))
        assertEquals(MarkdownStreamThrottle.FLOOR_MS, MarkdownStreamThrottle.intervalMs(10L))
        assertEquals(200L, MarkdownStreamThrottle.intervalMs(100L))
        assertEquals(700L, MarkdownStreamThrottle.intervalMs(350L))
        assertEquals(MarkdownStreamThrottle.CEIL_MS, MarkdownStreamThrottle.intervalMs(2000L))
        assertEquals(MarkdownStreamThrottle.CEIL_MS, MarkdownStreamThrottle.intervalMs(999_999L))
    }

    @Test
    fun `markdown throttle never goes below 100ms`() {
        // P4 硬约束：≥100ms 或按块；实际 throttle = max(100, 规格公式)
        assertEquals(100L, MarkdownStreamThrottle.throttleMs(0L))
        assertEquals(100L, MarkdownStreamThrottle.throttleMs(40L))
        assertEquals(200L, MarkdownStreamThrottle.throttleMs(100L))
        assertTrue(MarkdownStreamThrottle.throttleMs(1000L) in 100L..2000L)
    }

    @Test
    fun `should publish respects the throttle window`() {
        assertFalse(MarkdownStreamThrottle.shouldPublish(sinceLastPublishMs = 99L, lastRenderMs = 0L))
        assertTrue(MarkdownStreamThrottle.shouldPublish(sinceLastPublishMs = 100L, lastRenderMs = 0L))
        assertTrue(MarkdownStreamThrottle.shouldPublish(sinceLastPublishMs = 200L, lastRenderMs = 100L))
        assertFalse(MarkdownStreamThrottle.shouldPublish(sinceLastPublishMs = 199L, lastRenderMs = 100L))
    }

    @Test
    fun `stream status block height is 34dp`() {
        assertEquals(34f, ChatScrollMetrics.STREAM_STATUS_HEIGHT_DP, 0.001f) // §3.10 字段 2
    }

    // ------------------------------------------------------------ §3.2 回底钮无障碍（Lead 2026-11-27 真机缺陷）

    @Test
    fun `to bottom button has a centralized non blank accessible label`() {
        // 规格 §3.2 字段 11：`button` + 可调名「回到底部」
        assertTrue(ChatA11y.TO_BOTTOM.isNotBlank())
        assertEquals("回到底部", ChatA11y.TO_BOTTOM)
        // UI 只允许从集中函数取名字（防"图标按钮无 desc"回归）
        assertEquals(ChatA11y.TO_BOTTOM, ChatA11y.toBottomContentDescription())
        assertTrue(ChatA11y.TO_BOTTOM_TEST_TAG.isNotBlank())
        assertTrue(ChatA11y.TO_BOTTOM_TEST_TAG.endsWith("to-bottom"))
    }

    @Test
    fun `to bottom button ui references the centralized label and test tag`() {
        // 测试层拿不到 Compose 语义（无 compose-ui-test 依赖）→ 用静态断言锁"常量被 UI 引用"。
        // 语义树本身的权威验收 = uiautomator（Lead 2026-11-27 已在 host-GPU 模拟器复核，见 ChatA11y KDoc）：
        //   adb shell uiautomator dump → 单个 <node resource-id="chat.to-bottom" content-desc="回到底部" clickable="true"/>。
        // 找不到源码（非预期工作目录）时跳过，避免环境差异导致假红。
        val source = readMainSource("ui/m5/chat/ui/ScrollFollowUi.kt")
        Assume.assumeTrue("找不到 ScrollFollowUi.kt 源码，跳过静态断言", source != null)
        val text = source!!
        assertTrue(
            "回底钮必须用集中常量设置 contentDescription",
            text.contains("contentDescription = ChatA11y.toBottomContentDescription()"),
        )
        assertTrue(
            "回底钮必须带稳定 testTag 供 uiautomator 定位",
            text.contains("ChatA11y.TO_BOTTOM_TEST_TAG"),
        )
        assertTrue(
            "必须打开 testTagsAsResourceId 才能 dump 出 resource-id",
            text.contains("testTagsAsResourceId = true"),
        )
        assertTrue("可调名常量不能在 UI 里被硬编码绕过", !text.contains("contentDescription = \"回到"))
        // 回归防线（真机反证）：mergeDescendants=true 会把 desc 拆成**子节点**，
        // 父节点 `chat.to-bottom` 变成空 content-desc → 自动化假阴性。
        assertFalse(
            "回底钮不得使用合并语义（会被拆成子语义节点）",
            text.contains(".semantics(mergeDescendants"),
        )
        assertFalse("图标不得 clearAndSetSemantics（会多一个语义子节点）", text.contains("clearAndSetSemantics"))
    }

    @Test
    fun `every action bar key has a non blank accessible description`() {
        // 操作条图标键的可调名来源（base `IconAction` 必填 contentDescription，BP-19）。
        MessageActionsContract.KEY_ORDER.forEach { key ->
            assertTrue("$key 缺可调名", MessageActionsContract.keyDescription(key).isNotBlank())
        }
    }

    /** 读主源码（`src/main/kotlin/dev/myco/android/<relative>`）：兼容 Gradle 单测不同工作目录。 */
    private fun readMainSource(relative: String): String? {
        val roots = listOf(".", "app-android", "..", "../app-android", "../../app-android")
        roots.forEach { root ->
            val file = java.io.File(root, "src/main/kotlin/dev/myco/android/$relative")
            if (file.isFile) return file.readText()
        }
        return null
    }
}
