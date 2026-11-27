// 验收 ID：CMP-01 CMP-02 CMP-04 CMP-07 CMP-09 CMP-10 CMP-11 CMP-12 CMP-13 CMP-14 CMP-15 CMP-17
//          CMP-18 CMP-19 CMP-20 CMP-21 CMP-44 CMP-58 CMP-70 CMP-75（纯逻辑/公式部分）
//
// `ComposerState.kt`（composer 面板纯状态机 + 高度公式 + 命中几何）的 JVM 单测。
package dev.myco.android.ui.m5.composer

import dev.myco.android.ui.m5.attachment.AttachmentGate
import dev.myco.android.ui.m5.attachment.AttachmentGateBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerStateTest {

    // ---------------------------------------------------------------- §1.4 高度公式

    @Test
    fun `idle empty seat is 104dp`() {
        // CMP-09：H = 0 + 0 + (48 + 48) + 8 = 104
        val h = ComposerMetricsCalculator.seatHeightDp(
            lineCount = 1, queueCount = 0, hasRail = false, hasHint = false, lineHeightDp = 24f,
        )
        assertEquals(104f, h, 0.001f)
    }

    @Test
    fun `rail adds exactly 72dp`() {
        val without = ComposerMetricsCalculator.seatHeightDp(1, 0, false, false, 24f)
        val with = ComposerMetricsCalculator.seatHeightDp(1, 0, true, false, 24f)
        assertEquals(72f, with - without, 0.001f) // CMP-26/27
    }

    @Test
    fun `queue strip height is 8 plus capped rows and 4dp gap`() {
        assertEquals(56f, ComposerMetricsCalculator.queueHeightDp(1), 0.001f)
        assertEquals(104f, ComposerMetricsCalculator.queueHeightDp(2), 0.001f)
        assertEquals(152f, ComposerMetricsCalculator.queueHeightDp(3), 0.001f)
        // q>3 → cap 152（条内滚）
        assertEquals(152f, ComposerMetricsCalculator.queueHeightDp(4), 0.001f)
        assertEquals(152f, ComposerMetricsCalculator.queueHeightDp(20), 0.001f)

        // 含 4dp 间隔
        assertEquals(60f, ComposerMetricsCalculator.queueBlockHeightDp(1), 0.001f)
        // CMP-58：q=1 → H = 104 + 60 = 164
        assertEquals(164f, ComposerMetricsCalculator.seatHeightDp(1, 1, false, false, 24f), 0.001f)
    }

    @Test
    fun `draft height grows by line height`() {
        assertEquals(48f, ComposerMetricsCalculator.draftHeightDp(1, 24f), 0.001f)
        assertEquals(96f, ComposerMetricsCalculator.draftHeightDp(3, 24f), 0.001f)
        // CMP-04：6 行封顶（D(6) = 48 + 5×24 = 168）
        assertEquals(168f, ComposerMetricsCalculator.draftHeightDp(6, 24f), 0.001f)
        // 超出行数在调用方 clamp，这里再 clamp 一次防越界
        assertEquals(168f, ComposerMetricsCalculator.draftHeightDp(99, 24f), 0.001f)
    }

    @Test
    fun `line height follows content font size`() {
        // E-13/CMP-07：LH = 24 + (字号 − 16)
        assertEquals(24f, ComposerMetricsCalculator.lineHeightSp(16f), 0.001f)
        assertEquals(30f, ComposerMetricsCalculator.lineHeightSp(22f), 0.001f)
        assertEquals(18f, ComposerMetricsCalculator.lineHeightSp(10f), 0.001f)

        // 大字号 22sp：D(6) = 48 + 5×30 = 198（卡变高，不裁切）
        assertEquals(198f, ComposerMetricsCalculator.draftHeightDp(6, 30f), 0.001f)
        // 上下内边距 = (48 − LH)/2，大字号归零不裁切
        assertEquals(12f, ComposerMetricsCalculator.draftVerticalPaddingDp(24f), 0.001f)
        assertEquals(0f, ComposerMetricsCalculator.draftVerticalPaddingDp(52f), 0.001f)
    }

    @Test
    fun `line cap matches the device tier table`() {
        // §1.4 表：A = 屏高 − statusBar − 顶栏 − IME − navBar
        // 小屏 360×640 / IME 280 → A=256 → 1 行
        assertEquals(1, ComposerMetricsCalculator.lineCap(256f, hasRail = false, queueCount = 0, lineHeightDp = 24f))
        // 主流 360×800 / IME 300 → A=396 → 3 行
        assertEquals(3, ComposerMetricsCalculator.lineCap(396f, false, 0, 24f))
        // 主流 + 有轨(72) → 1 行
        assertEquals(1, ComposerMetricsCalculator.lineCap(396f, true, 0, 24f))
        // 大屏 412×915 / IME 320 → A=491 → 5 行
        assertEquals(5, ComposerMetricsCalculator.lineCap(491f, false, 0, 24f))
        // 大屏 + 有轨 → 2 行
        assertEquals(2, ComposerMetricsCalculator.lineCap(491f, true, 0, 24f))
        // 横屏 915×412 / IME 200 → A=108 → 1 行（0.4A 装不下 2 行时让位给 1 行）
        assertEquals(1, ComposerMetricsCalculator.lineCap(108f, false, 0, 24f))
        // 键盘收起 800 → A=696 → 6 行（绝对上限）
        assertEquals(6, ComposerMetricsCalculator.lineCap(696f, false, 0, 24f))
        // 队列块也吃预算：q=3 时 396 → 1 行
        assertEquals(1, ComposerMetricsCalculator.lineCap(396f, false, 3, 24f))
        // 大字号把行高抬到 30 → 同样 A 下行数降档
        assertTrue(ComposerMetricsCalculator.lineCap(491f, false, 0, 30f) <= 5)
    }

    @Test
    fun `metrics clamps line count to cap and publishes same-frame formula values`() {
        val metrics = ComposerMetricsCalculator.metrics(
            lineCount = 99,
            queueCount = 2,
            hasRail = true,
            hasHint = true,
            lineCap = 2,
            lineHeightDp = 24f,
            visualState = ComposerVisualState.IDLE,
        )
        assertEquals(2, metrics.lineCount) // clamped
        assertEquals(2, metrics.lineCap)
        // C(n) = (48 + 1×24) + 48 = 120；+72 轨 +56 提示条 = 248
        assertEquals(248f, metrics.cardHeightDp, 0.001f)
        // H = Q(2)+4=108 + 248 + 8 = 364
        assertEquals(364f, metrics.seatHeightDp, 0.001f)
        assertEquals(108f, metrics.cardTopDp, 0.001f)
    }

    @Test
    fun `palette height clamps between one row and five rows`() {
        // 5 条 × 52 = 260（A 足够）
        assertEquals(260f, ComposerMetricsCalculator.paletteHeightDp(5, 800f, 104f), 0.001f)
        // 屏幕装不下 → 收窄但至少 1 行（CMP-52 的"面板不占位、不改 H"由 metrics 不含 palette 保证）
        assertEquals(52f, ComposerMetricsCalculator.paletteHeightDp(5, 300f, 240f), 0.001f)
        // 0 条不渲染
        assertEquals(0f, ComposerMetricsCalculator.paletteHeightDp(0, 800f, 104f), 0.001f)
    }

    // ---------------------------------------------------------------- 主键语义（CMP-10/11/12/14）

    private val idle = ComposerEnv()

    @Test
    fun `single key - idle empty is ignored`() {
        assertEquals(
            ComposerPrimaryIntent.Ignored,
            ComposerMachine.primaryIntent(idle, hasContent = false),
        )
    }

    @Test
    fun `single key - idle with content sends`() {
        assertEquals(
            ComposerPrimaryIntent.Send,
            ComposerMachine.primaryIntent(idle, hasContent = true),
        )
    }

    @Test
    fun `single key - running empty stops`() {
        // CMP-11
        assertEquals(
            ComposerPrimaryIntent.Stop,
            ComposerMachine.primaryIntent(idle.copy(running = true), hasContent = false),
        )
    }

    @Test
    fun `single key - running with content enqueues locally`() {
        // CMP-12/79：生成中发送 = 入本地队列（**不发请求、不做 steer**）
        assertEquals(
            ComposerPrimaryIntent.Enqueue,
            ComposerMachine.primaryIntent(idle.copy(running = true), hasContent = true),
        )
    }

    @Test
    fun `single key - submitting locked offline are ignored`() {
        assertTrue(ComposerMachine.primaryIntent(idle.copy(submitting = true), true) is ComposerPrimaryIntent.Ignored)
        assertTrue(ComposerMachine.primaryIntent(idle.copy(locked = true), true) is ComposerPrimaryIntent.Ignored)
        assertTrue(ComposerMachine.primaryIntent(idle.copy(connected = false), true) is ComposerPrimaryIntent.Ignored)
        assertTrue(ComposerMachine.primaryIntent(idle.copy(initializing = true), true) is ComposerPrimaryIntent.Ignored)
    }

    @Test
    fun `single key - processing gate blocks sending but keeps stop`() {
        val processingGate = AttachmentGate(
            canSend = false,
            processingCount = 1,
            reason = "还有 1 个附件在处理中",
            block = AttachmentGateBlock.PROCESSING,
        )
        // 有内容 → 禁发（ATT-17）
        assertEquals(
            ComposerPrimaryIntent.Ignored,
            ComposerMachine.primaryIntent(idle, hasContent = true, gate = processingGate),
        )
        // 空草稿 + 生成中 → 仍然是停止（单键语义不允许因附件处理中丢掉停止能力）
        assertEquals(
            ComposerPrimaryIntent.Stop,
            ComposerMachine.primaryIntent(idle.copy(running = true), hasContent = false, gate = processingGate),
        )
    }

    @Test
    fun `single key - failed attachment blocks with toast guidance`() {
        val failedGate = AttachmentGate(
            canSend = false,
            failedCount = 1,
            reason = "有 1 个附件失败，请移除或重试",
            block = AttachmentGateBlock.FAILED,
        )
        val intent = ComposerMachine.primaryIntent(idle, hasContent = true, gate = failedGate)
        assertTrue(intent is ComposerPrimaryIntent.Blocked)
        val blocked = intent as ComposerPrimaryIntent.Blocked
        assertTrue(blocked.toast.contains("移除"))
        assertTrue(blocked.toast.contains("重试"))
    }

    // ---------------------------------------------------------------- 主键语义表（§3.4 字段 11）

    @Test
    fun `primary ui - idle empty is disabled with reason`() {
        val ui = ComposerMachine.primaryUi(idle, hasContent = false)
        assertEquals(PrimaryIcon.SEND, ui.icon)
        assertFalse(ui.interactive)
        assertTrue(ui.dimmed)
        assertEquals("发送", ui.contentDescription)
        assertEquals("草稿为空，无法发送", ui.stateDescription)
    }

    @Test
    fun `primary ui - running empty is stop`() {
        val ui = ComposerMachine.primaryUi(idle.copy(running = true), hasContent = false)
        assertEquals(PrimaryIcon.STOP, ui.icon)
        assertTrue(ui.interactive)
        assertFalse(ui.dimmed)
        assertEquals("停止生成", ui.contentDescription)
        assertEquals("正在生成", ui.stateDescription)
    }

    @Test
    fun `primary ui - running with content says it will queue`() {
        val ui = ComposerMachine.primaryUi(idle.copy(running = true), hasContent = true)
        assertEquals(PrimaryIcon.SEND, ui.icon)
        assertTrue(ui.interactive)
        assertEquals("发送", ui.contentDescription)
        assertTrue(ui.stateDescription!!.contains("排队"))
        assertTrue(ui.stateDescription!!.contains("本轮结束后自动发出"))
    }

    @Test
    fun `primary ui - submitting is busy and announced once`() {
        val ui = ComposerMachine.primaryUi(idle.copy(submitting = true), hasContent = true)
        assertEquals(PrimaryIcon.BUSY, ui.icon)
        assertFalse(ui.interactive)
        assertEquals("正在提交", ui.stateDescription)
        assertTrue(ui.liveRegion) // CMP-17：liveRegion=Polite 只播一次
    }

    @Test
    fun `primary ui - disabled reasons for locked and offline`() {
        assertEquals("当前会话只读", ComposerMachine.primaryUi(idle.copy(locked = true), true).stateDescription)
        assertEquals("未连接，暂不能发送", ComposerMachine.primaryUi(idle.copy(connected = false), true).stateDescription)
        assertEquals("连接后才能发送", ComposerMachine.primaryUi(idle.copy(initializing = true), true).stateDescription)
    }

    @Test
    fun `primary ui - attachment processing and failure both surface reasons`() {
        val processing = ComposerMachine.primaryUi(
            idle,
            hasContent = true,
            gate = AttachmentGate(false, processingCount = 1, reason = "还有 1 个附件在处理中", block = AttachmentGateBlock.PROCESSING),
        )
        assertFalse(processing.interactive)
        assertEquals("附件处理中，暂不能发送", processing.stateDescription)

        val failed = ComposerMachine.primaryUi(
            idle,
            hasContent = true,
            gate = AttachmentGate(false, failedCount = 1, reason = "有 1 个附件失败，请移除或重试", block = AttachmentGateBlock.FAILED),
        )
        assertTrue("失败态保留点击以便弹 toast（ATT-18）", failed.interactive)
        assertTrue(failed.dimmed)
        assertTrue(failed.stateDescription!!.contains("移除"))
    }

    @Test
    fun `no second stop key - only plus capsule primary exist in tools row`() {
        // 模型胶囊始终可点；`+` 与主键受状态影响 —— 工具行只有 3 个可点原子（CMP-12 的负向断言口径）
        val geometry = ComposerGeometry
        val plan = geometry.capsulePlan(availableWidthDp = 304f, nameWidthDp = 40f, reasoningWidthDp = 20f, hasReasoning = true)
        val layout = geometry.toolsRowLayout(0f, 0f, 304f, plan)
        assertTrue(layout.plus.widthDp > 0f)
        assertTrue(layout.capsule.widthDp > 0f)
        assertTrue(layout.primary.widthDp > 0f)
    }

    // ---------------------------------------------------------------- 提交防重入（BUG-004 / CMP-15/17）

    @Test
    fun `submit guard enters exactly once for ten rapid presses`() {
        // CMP-15：连点 2 次 / 快速 10 次 → 1 次提交（单测计数）
        val guard = SubmitGuard()
        var entered = 0
        repeat(10) { if (guard.tryEnter()) entered++ }
        assertEquals(1, entered)
        assertTrue(guard.inFlight)
    }

    @Test
    fun `submit guard resets after settle for a new snapshot`() {
        val guard = SubmitGuard()
        assertTrue(guard.tryEnter())
        guard.reset()
        assertFalse(guard.inFlight)
        assertTrue("失败/成功后允许用新快照重试", guard.tryEnter())
    }

    // ---------------------------------------------------------------- 视觉态 / `+` 可用性

    @Test
    fun `visual state priority`() {
        val processing = AttachmentGate(false, processingCount = 1, block = AttachmentGateBlock.PROCESSING)
        assertEquals(ComposerVisualState.SUBMITTING, ComposerMachine.visualState(idle.copy(submitting = true), processing))
        assertEquals(ComposerVisualState.LOCKED, ComposerMachine.visualState(idle.copy(locked = true), AttachmentGate.OPEN))
        assertEquals(ComposerVisualState.OFFLINE, ComposerMachine.visualState(idle.copy(connected = false), AttachmentGate.OPEN))
        assertEquals(ComposerVisualState.RUNNING, ComposerMachine.visualState(idle.copy(running = true), processing))
        assertEquals(ComposerVisualState.PROCESSING, ComposerMachine.visualState(idle, processing))
        assertEquals(ComposerVisualState.IDLE, ComposerMachine.visualState(idle, AttachmentGate.OPEN))
    }

    @Test
    fun `attach trigger disabled reasons`() {
        val processing = AttachmentGate(false, processingCount = 1, block = AttachmentGateBlock.PROCESSING)
        assertFalse(ComposerMachine.attachTriggerEnabled(idle.copy(submitting = true), AttachmentGate.OPEN))
        assertFalse(ComposerMachine.attachTriggerEnabled(idle, processing))
        assertFalse(ComposerMachine.attachTriggerEnabled(idle.copy(locked = true), AttachmentGate.OPEN))
        assertFalse(ComposerMachine.attachTriggerEnabled(idle.copy(connected = false), AttachmentGate.OPEN))
        assertTrue(ComposerMachine.attachTriggerEnabled(idle, AttachmentGate.OPEN))
        assertNotNull(ComposerMachine.attachTriggerDisabledReason(idle.copy(submitting = true), AttachmentGate.OPEN))
        assertNull(ComposerMachine.attachTriggerDisabledReason(idle, AttachmentGate.OPEN))
    }

    // ---------------------------------------------------------------- 粘贴 / 长文阈值（CMP-44）

    @Test
    fun `long text threshold chars boundary`() {
        assertFalse(PasteLogic.shouldOfferAttachment(chars = 5999, lines = 1))
        assertTrue(PasteLogic.shouldOfferAttachment(chars = 6000, lines = 1))
    }

    @Test
    fun `long text threshold lines boundary`() {
        assertFalse(PasteLogic.shouldOfferAttachment(chars = 100, lines = 119))
        assertTrue(PasteLogic.shouldOfferAttachment(chars = 100, lines = 120))
    }

    @Test
    fun `paste insertion detection handles plain insert and appends`() {
        val insertion = PasteLogic.detectInsertion("hello ", "hello world")
        assertNotNull(insertion)
        assertEquals(6, insertion!!.start)
        assertEquals("world", insertion.inserted)

        val prepend = PasteLogic.detectInsertion("world", "hello world")
        assertEquals(0, prepend!!.start)
        assertEquals("hello ", prepend.inserted)

        // 删除/替换不是"纯插入" → 不触发长文提示
        assertNull(PasteLogic.detectInsertion("hello world", "hello"))
        assertNull(PasteLogic.detectInsertion("abc", "axc"))
        assertNull(PasteLogic.detectInsertion("same", "same"))
    }

    @Test
    fun `paste insertion counts lines correctly`() {
        val text = (1..120).joinToString("\n")
        val insertion = PasteLogic.detectInsertion("", text)!!
        assertEquals(120, insertion.lines)
        assertEquals(text.length, insertion.chars)
    }

    @Test
    fun `remove insertion deletes only pasted range`() {
        val draft = "前缀 " + "x".repeat(6000) + " 后缀"
        val insertion = PasteLogic.detectInsertion("前缀 ", "前缀 " + "x".repeat(6000))!!
        val hint = LongTextHint(insertion.inserted, insertion.start, insertion.chars, insertion.lines)
        assertEquals("前缀 ", PasteLogic.removeInsertion("前缀 ", hint))
        // 文本已变化（越界/不匹配）→ 安全降级不删
        assertEquals(draft, PasteLogic.removeInsertion(draft, LongTextHint("zzz", 0, 3, 1)))
    }

    @Test
    fun `paste routes files and text separately`() {
        // CMP-41：图片 + 文本 → 文件入轨 + 文本照常插入
        val both = PasteLogic.route(hasFiles = true, text = "说明")
        assertTrue(both is PasteRoute.FilesAndText)
        assertEquals("说明", (both as PasteRoute.FilesAndText).text)

        // CMP-42：纯文本 → 不产生附件
        val textOnly = PasteLogic.route(hasFiles = false, text = "只有文字")
        assertTrue(textOnly is PasteRoute.TextOnly)

        val filesOnly = PasteLogic.route(hasFiles = true, text = "")
        assertTrue(filesOnly is PasteRoute.FilesOnly)

        assertTrue(PasteLogic.route(hasFiles = false, text = null) is PasteRoute.None)
    }
}
