// 验收 ID：ATT-06[candidate] ATT-12 ATT-13 ATT-14 ATT-15 ATT-16 ATT-17 ATT-18 ATT-19 ATT-20 ATT-21
//
// `AttachmentState.kt`（attachment 面板纯状态机）的 JVM 单测：
//  * intake 校验顺序（类型 → 单文件 → 总量）、整批拒绝、**数量不校验**（D-24/P-ATT-04）；
//  * 三态流转 processing→ready / failed→retry→ready；串行（同时仅 1 项 active）；
//  * 门禁：处理中禁发 / 失败项拦发（含"移除 / 重试"指引）；30s 超时；
//  * 移除：ready/failed 立即删、processing 走 discard 且回执后给 orphanToDelete（不产生孤儿）。
package dev.myco.android.ui.m5.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentStateTest {

    private val mb = 1024L * 1024L

    private fun incoming(
        id: String,
        size: Long = 1 * mb,
        mime: String = "image/png",
        name: String = "$id.png",
        kind: AttachmentItemKind = if (mime.startsWith("image/")) AttachmentItemKind.IMAGE else AttachmentItemKind.FILE,
    ) = IncomingAttachment(
        localId = id,
        name = name,
        mime = mime,
        sizeBytes = size,
        kind = kind,
    )

    private fun emptyDraft() = AttachmentDraft()

    private fun acceptAll(vararg items: IncomingAttachment, limits: AttachmentLimits = AttachmentLimits.UNLIMITED): AttachmentDraft {
        val result = AttachmentMachine.addBatch(emptyDraft(), items.toList(), limits, nowMs = 1_000L)
        assertTrue("期望整批接受，实际 ${result.intake}", result.intake is IntakeResult.Accepted)
        return result.draft
    }

    // ---------------------------------------------------------------- intake：顺序与整批拒绝

    @Test
    fun `intake checks type before size`() {
        // 类型不合法 + 尺寸也超限：必须报类型（校验顺序①）
        val limits = AttachmentLimits(maxImageBytes = 1 * mb, mediaTypes = setOf("image/png"))
        val result = AttachmentIntake.validate(
            listOf(incoming("gif", size = 9 * mb, mime = "image/gif")),
            limits,
        )
        assertTrue(result is IntakeResult.Rejected)
        val rejected = result as IntakeResult.Rejected
        assertTrue("应报类型：${rejected.reason}", rejected.reason.contains("不支持的图片类型"))
        assertEquals(0, rejected.failedIndex)
    }

    @Test
    fun `intake rejects oversize image single file`() {
        val limits = AttachmentLimits(maxImageBytes = 8 * mb)
        val result = AttachmentIntake.validate(listOf(incoming("big", size = 9 * mb)), limits)
        assertTrue(result is IntakeResult.Rejected)
        assertTrue((result as IntakeResult.Rejected).reason.contains("单张上限 8 MB"))
    }

    @Test
    fun `intake rejects oversize generic file with file limit`() {
        val limits = AttachmentLimits(maxFileBytes = 8 * mb)
        val result = AttachmentIntake.validate(
            listOf(incoming("doc", size = 9 * mb, mime = "application/pdf")),
            limits,
        )
        assertTrue(result is IntakeResult.Rejected)
        assertTrue((result as IntakeResult.Rejected).reason.contains("单文件上限 8 MB"))
    }

    @Test
    fun `intake rejects batch total over total limit`() {
        val limits = AttachmentLimits(maxTotalBytes = 10 * mb)
        val result = AttachmentIntake.validate(
            listOf(
                incoming("a", size = 6 * mb, mime = "application/pdf"),
                incoming("b", size = 6 * mb, mime = "application/pdf"),
            ),
            limits,
        )
        assertTrue(result is IntakeResult.Rejected)
        assertTrue((result as IntakeResult.Rejected).reason.contains("总大小超过 10 MB"))
        assertEquals(-1, result.failedIndex)
    }

    @Test
    fun `intake uses message image total for pure image batch`() {
        val limits = AttachmentLimits(maxMessageImageBytes = 10 * mb, maxTotalBytes = 100 * mb)
        val result = AttachmentIntake.validate(
            listOf(incoming("a", size = 6 * mb), incoming("b", size = 6 * mb)),
            limits,
        )
        assertTrue("纯图片批应使用 maxMessageImageBytes", result is IntakeResult.Rejected)
    }

    @Test
    fun `intake does not validate count - no client side cap`() {
        // ATT-06[candidate]/ATT-13：首版不设客户端数量上限（连选 30 个测试量，非上限）
        val batch = (1..30).map { incoming("f$it", size = 1_000L, mime = "application/pdf") }
        val result = AttachmentIntake.validate(batch, AttachmentLimits.UNLIMITED)
        assertTrue(result is IntakeResult.Accepted)
        assertEquals(30, (result as IntakeResult.Accepted).items.size)
    }

    @Test
    fun `intake treats zero or negative limits as unlimited`() {
        val limits = AttachmentLimits(maxImageBytes = -1, maxFileBytes = 0, maxTotalBytes = -5)
        val result = AttachmentIntake.validate(
            listOf(incoming("huge", size = 500 * mb)),
            limits,
        )
        assertTrue(result is IntakeResult.Accepted)
    }

    @Test
    fun `rejected batch adds nothing to rail`() {
        val limits = AttachmentLimits(maxImageBytes = 1 * mb)
        val result = AttachmentMachine.addBatch(
            emptyDraft(),
            listOf(
                incoming("ok1", size = 100_000L),
                incoming("big", size = 9 * mb),
                incoming("ok2", size = 100_000L),
            ),
            limits,
        )
        assertTrue(result.intake is IntakeResult.Rejected)
        assertEquals(0, result.draft.items.size) // 整批拒绝：一个都不入队（ATT-12）
    }

    @Test
    fun `accepted items start processing and only first is active`() {
        val draft = acceptAll(incoming("a"), incoming("b"), incoming("c"))
        assertEquals(3, draft.items.size)
        assertTrue(draft.items.all { it.state == AttachmentItemState.PROCESSING })
        assertEquals("a", draft.activeProcessingId) // ATT-15：串行，同时仅 1 项 active
        assertEquals("a", draft.nextToProcess?.id)
        // 视觉三态里排队项同样是"处理中"（唯一 active 由 activeProcessingId 表达）
        assertEquals(3, draft.processingCount)
    }

    @Test
    fun `formatBytes renders projection values without hardcoded cap`() {
        assertEquals("8 MB", AttachmentIntake.formatBytes(8 * mb))
        assertEquals("1.5 MB", AttachmentIntake.formatBytes(mb + mb / 2))
        assertEquals("512 KB", AttachmentIntake.formatBytes(512 * 1024L))
    }

    // ---------------------------------------------------------------- 三态流转 / 串行

    @Test
    fun `ready promotes next queued item serially`() {
        var draft = acceptAll(incoming("a"), incoming("b"), incoming("c"))
        draft = AttachmentMachine.markReady(draft, "a", nowMs = 1_000L).draft
        assertEquals("b", draft.nextToProcess?.id)

        draft = AttachmentMachine.markReady(draft, "b", nowMs = 2_000L).draft
        assertEquals("c", draft.nextToProcess?.id)

        draft = AttachmentMachine.markReady(draft, "c", nowMs = 3_000L).draft
        assertNull(draft.nextToProcess)
        assertTrue(draft.items.all { it.state == AttachmentItemState.READY })
        assertEquals(0, draft.processingCount)
    }

    @Test
    fun `fail also promotes next and keeps order`() {
        var draft = acceptAll(incoming("a"), incoming("b"))
        draft = AttachmentMachine.markFailed(draft, "a", "磁盘写失败", retryable = true, nowMs = 1_000L).draft
        assertEquals(AttachmentItemState.FAILED, draft.items[0].state)
        assertTrue(draft.items[0].retryable)
        assertEquals("b", draft.activeProcessingId)
    }

    @Test
    fun `duplicate receipt is idempotent`() {
        var draft = acceptAll(incoming("a"))
        val first = AttachmentMachine.markReady(draft, "a", nowMs = 1_000L)
        assertTrue(first.changed)
        draft = first.draft
        val second = AttachmentMachine.markReady(draft, "a", nowMs = 2_000L)
        assertFalse(second.changed) // 重复回执幂等忽略
        assertEquals(AttachmentItemState.READY, second.draft.items[0].state)
    }

    @Test
    fun `ready items remote ids are ordered and exclude failed`() {
        var draft = acceptAll(incoming("a"), incoming("b"), incoming("c"))
        draft = AttachmentMachine.markReady(draft, "a", remoteId = "remote-a", nowMs = 1_000L).draft
        draft = AttachmentMachine.markFailed(draft, "b", "坏", retryable = false, nowMs = 2_000L).draft
        draft = AttachmentMachine.markReady(draft, "c", remoteId = "remote-c", nowMs = 3_000L).draft
        assertEquals(listOf("remote-a", "remote-c"), draft.readyRemoteIds())
    }

    @Test
    fun `retry only works for retryable failed item and dedupes in flight`() {
        var draft = acceptAll(incoming("a"))
        draft = AttachmentMachine.markFailed(draft, "a", "IO 失败", retryable = true, nowMs = 1_000L).draft
        val retried = AttachmentMachine.beginRetry(draft, "a", nowMs = 2_000L)
        assertTrue(retried.changed)
        assertEquals(AttachmentItemState.PROCESSING, retried.draft.items[0].state)

        val again = AttachmentMachine.beginRetry(retried.draft, "a", nowMs = 3_000L)
        assertFalse("重试在飞时连点必须被吞掉", again.changed) // CMP-39 同型

        var nonRetryable = acceptAll(incoming("b"))
        nonRetryable = AttachmentMachine.markFailed(nonRetryable, "b", "类型不支持", retryable = false).draft
        assertFalse(AttachmentMachine.beginRetry(nonRetryable, "b").changed)
    }

    @Test
    fun `retry keeps item count and order`() {
        var draft = acceptAll(incoming("a"), incoming("b"))
        draft = AttachmentMachine.markFailed(draft, "a", "IO", retryable = true, nowMs = 1_000L).draft
        val before = draft.items.map { it.id }
        val retried = AttachmentMachine.beginRetry(draft, "a", nowMs = 2_000L).draft
        assertEquals(2, retried.items.size)
        assertEquals(before, retried.items.map { it.id })
        // a 回到处理中、b 仍是处理中；串行 active 仍是先到的 b
        assertEquals(2, retried.processingCount)
        assertEquals("b", retried.activeProcessingId)
    }

    // ---------------------------------------------------------------- 门禁

    @Test
    fun `processing blocks send with count in reason`() {
        val draft = acceptAll(incoming("a"))
        assertFalse(draft.gate.canSend)
        assertEquals(AttachmentGateBlock.PROCESSING, draft.gate.block)
        assertEquals("还有 1 个附件在处理中", draft.gate.reason)
    }

    @Test
    fun `failed blocks send and mentions remove or retry`() {
        var draft = acceptAll(incoming("a"))
        draft = AttachmentMachine.markFailed(draft, "a", "超时", retryable = true, nowMs = 1_000L).draft
        assertFalse(draft.gate.canSend)
        assertEquals(AttachmentGateBlock.FAILED, draft.gate.block)
        assertTrue(draft.gate.reason!!.contains("移除"))
        assertTrue(draft.gate.reason!!.contains("重试"))
    }

    @Test
    fun `all ready opens the gate`() {
        var draft = acceptAll(incoming("a"), incoming("b"))
        draft = AttachmentMachine.markReady(draft, "a", nowMs = 1_000L).draft
        draft = AttachmentMachine.markReady(draft, "b", nowMs = 2_000L).draft
        assertTrue(draft.gate.canSend)
        assertNull(draft.gate.block)
    }

    @Test
    fun `empty rail opens the gate`() {
        assertTrue(emptyDraft().gate.canSend)
    }

    // ---------------------------------------------------------------- 移除 / discard / 超时

    @Test
    fun `remove ready needs immediate delete`() {
        var draft = acceptAll(incoming("a"))
        draft = AttachmentMachine.markReady(draft, "a", nowMs = 1_000L).draft
        val removal = AttachmentMachine.remove(draft, "a")
        assertTrue(removal.changed)
        assertTrue(removal.needsDeleteNow) // ATT-20
        assertFalse(removal.discardOnAck)
        assertEquals(0, removal.draft.items.size)
    }

    @Test
    fun `remove processing discards and deletes on late ack`() {
        var draft = acceptAll(incoming("a"))
        val removal = AttachmentMachine.remove(draft, "a")
        assertFalse(removal.needsDeleteNow)
        assertTrue(removal.discardOnAck) // ATT-21：处理中移除 → discard
        assertTrue(removal.draft.items.isEmpty())

        // 延迟回执：项已不在 → 返回 orphanToDelete，通道据此删文件（不产生残留）
        draft = removal.draft
        val lateAck = AttachmentMachine.markReady(draft, "a", nowMs = 2_000L)
        assertFalse(lateAck.changed)
        assertEquals("a", lateAck.orphanToDelete)
    }

    @Test
    fun `remove is idempotent`() {
        val draft = acceptAll(incoming("a"))
        val first = AttachmentMachine.remove(draft, "a")
        val second = AttachmentMachine.remove(first.draft, "a")
        assertFalse(second.changed)
        assertNull(second.removed)
    }

    @Test
    fun `timeout marks failed retryable after 30s`() {
        var draft = acceptAll(incoming("a"), incoming("b"))
        // 起点 1000ms
        assertEquals(1_000L, draft.items[0].processingStartedAtMs)

        val notYet = AttachmentMachine.tickTimeouts(draft, nowMs = 1_000L + 29_999L)
        assertFalse(notYet.changed)

        val timedOut = AttachmentMachine.tickTimeouts(draft, nowMs = 1_000L + 30_000L)
        assertTrue(timedOut.changed)
        draft = timedOut.draft
        assertEquals(AttachmentItemState.FAILED, draft.items[0].state)
        assertTrue(draft.items[0].retryable)
        assertEquals(AttachmentMachine.TIMEOUT_REASON, draft.items[0].failureReason)
        assertEquals("b", draft.activeProcessingId) // 超时后提升下一项
    }

    @Test
    fun `timed out item can be retried`() {
        var draft = acceptAll(incoming("a"))
        draft = AttachmentMachine.tickTimeouts(draft, nowMs = 31_000L).draft
        assertEquals(AttachmentItemState.FAILED, draft.items[0].state)
        val retried = AttachmentMachine.beginRetry(draft, "a", nowMs = 40_000L)
        assertTrue(retried.changed)
        assertEquals(AttachmentItemState.PROCESSING, retried.draft.items[0].state)
        assertNotEquals(AttachmentItemState.FAILED, retried.draft.items[0].state)
    }

    @Test
    fun `state only becomes ready by receipt`() {
        // 不存在无回执的 ready（attachment.md §3.8 字段 5）
        val draft = acceptAll(incoming("a"))
        assertEquals(AttachmentItemState.PROCESSING, draft.items[0].state)
        assertFalse(draft.readyRemoteIds().isNotEmpty())
    }
}
