// 验收 ID：CMP-58 CMP-59 CMP-60 CMP-62 CMP-64 CMP-66 CMP-79 CMP-80 CMP-82 CMP-83（队列纯状态机部分）
//
// `ComposerQueueMachine`（客户端本地队列）的 JVM 单测：
//  * 入队 / 条高公式 / q>3 内滚；
//  * `AssistantDone` 只自动出队 1 条；停止/失败保留并暂停；自动发送失败放回队首；
//  * 编辑（空文本拒绝、顺序不变）、撤回（文字完整回填、不覆盖草稿）、删除 + 6000ms 撤销顺序恢复；
//  * busy 串行化（三动作禁用）。
package dev.myco.android.ui.m5.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStateTest {

    private fun enqueueN(n: Int): ComposerQueue {
        var queue = ComposerQueue()
        repeat(n) { index ->
            queue = ComposerQueueMachine.enqueue(
                queue = queue,
                text = "消息 ${index + 1}",
                attachmentIds = if (index == 0) listOf("att-1") else emptyList(),
                attachmentNames = if (index == 0) listOf("a.png") else emptyList(),
                nowMs = (index + 1) * 100L,
            )
        }
        return queue
    }

    // ---------------------------------------------------------------- 入队 / 条高

    @Test
    fun `enqueue keeps insertion order and increasing ids`() {
        val queue = enqueueN(3)
        assertEquals(3, queue.count)
        assertEquals(listOf(1L, 2L, 3L), queue.items.map { it.id })
        assertEquals(listOf("消息 1", "消息 2", "消息 3"), queue.items.map { it.text })
        assertEquals(listOf("att-1"), queue.items[0].attachmentIds)
        assertFalse(queue.paused)
        assertFalse(queue.busy)
    }

    @Test
    fun `queue height and inner scroll follow the formula`() {
        // CMP-58：条高 8 + min(q,3)×48
        assertEquals(56f, enqueueN(1).heightDp, 0.001f)
        assertEquals(104f, enqueueN(2).heightDp, 0.001f)
        assertEquals(152f, enqueueN(3).heightDp, 0.001f)
        assertEquals(152f, enqueueN(4).heightDp, 0.001f) // cap
        assertFalse(enqueueN(3).scrollable)
        assertTrue(enqueueN(4).scrollable)
        assertEquals(0f, ComposerQueue().heightDp, 0.001f)
    }

    @Test
    fun `preview falls back to attachment names`() {
        var queue = ComposerQueue()
        queue = ComposerQueueMachine.enqueue(
            queue,
            text = "  ",
            attachmentIds = listOf("a"),
            attachmentNames = listOf("report.pdf"),
        )
        assertEquals("附件：report.pdf", queue.items[0].preview)
    }

    // ---------------------------------------------------------------- 自动出队（CMP-79/80）

    @Test
    fun `assistant done dequeues exactly one message and marks busy`() {
        val queue = enqueueN(3)
        val result = ComposerQueueMachine.onAssistantDone(queue)
        assertEquals("消息 1", result.message?.text)
        assertEquals(2, result.queue.count)
        assertTrue("自动发送在飞 → 行内动作禁用", result.queue.busy)
        assertFalse(result.queue.paused)
        // 串行：一次只出队 1 条（不连发）
        assertEquals(listOf("消息 2", "消息 3"), result.queue.items.map { it.text })
    }

    @Test
    fun `assistant done with empty queue returns no message`() {
        val result = ComposerQueueMachine.onAssistantDone(ComposerQueue())
        assertNull(result.message)
        assertEquals(0, result.queue.count)
    }

    @Test
    fun `pause keeps queue and next normal done resumes one`() {
        var queue = enqueueN(2)
        queue = ComposerQueueMachine.pause(queue)
        assertTrue(queue.paused)
        assertEquals("本轮结束后继续", queue.pausedHint)
        assertFalse(queue.canAutoSend)

        // 下一次本轮正常结束 → 解除暂停并只出队 1 条（CMP-80）
        val resumed = ComposerQueueMachine.onAssistantDone(queue)
        assertEquals("消息 1", resumed.message?.text)
        assertFalse(resumed.queue.paused)
        assertEquals(1, resumed.queue.count)
    }

    @Test
    fun `auto send settle clears busy`() {
        val queue = ComposerQueueMachine.onAssistantDone(enqueueN(2)).queue
        val settled = ComposerQueueMachine.onAutoSendSettled(queue)
        assertFalse(settled.busy)
        assertTrue(settled.actionsEnabled)
    }

    @Test
    fun `auto send failure puts message back at front and pauses`() {
        val queue = enqueueN(2)
        val first = queue.items.first()
        val failed = ComposerQueueMachine.onAutoSendFailed(queue, first)
        assertEquals(3, failed.count) // 放回队首，不丢消息
        assertEquals(first.id, failed.items.first().id)
        assertTrue(failed.paused)
        assertFalse(failed.busy)
    }

    @Test
    fun `busy disables all queue actions`() {
        val busy = ComposerQueueMachine.setBusy(enqueueN(2), true)
        assertFalse(busy.actionsEnabled) // CMP-62：busy 时三动作全禁用
        assertTrue(ComposerQueueMachine.setBusy(busy, false).actionsEnabled)
    }

    // ---------------------------------------------------------------- 编辑（CMP-64/66）

    @Test
    fun `edit updates text and keeps order`() {
        val queue = enqueueN(3)
        val result = ComposerQueueMachine.edit(queue, id = 2L, newText = "改过的")
        assertTrue(result.changed)
        assertEquals(listOf("消息 1", "改过的", "消息 3"), result.queue.items.map { it.text })
        assertEquals(listOf(1L, 2L, 3L), result.queue.items.map { it.id })
    }

    @Test
    fun `edit rejects blank text`() {
        val queue = enqueueN(1)
        assertFalse(ComposerQueueMachine.edit(queue, 1L, "   ").changed)
        assertFalse(ComposerQueueMachine.edit(queue, 99L, "找不到").changed)
    }

    // ---------------------------------------------------------------- 撤回（CMP-82）

    @Test
    fun `recall returns text to draft without overwriting`() {
        val queue = enqueueN(2)
        val recalled = ComposerQueueMachine.recall(queue, id = 1L, draft = "我正在打的字")
        assertEquals("消息 1", recalled.recalled?.text)
        assertEquals(1, recalled.queue.count)
        // 草稿非空 → 换行追加，不覆盖（CMP-82）
        assertEquals("我正在打的字\n消息 1", recalled.draft)
    }

    @Test
    fun `recall into blank draft inserts text directly`() {
        val queue = enqueueN(1)
        val recalled = ComposerQueueMachine.recall(queue, id = 1L, draft = "  ")
        assertEquals("消息 1", recalled.draft)
        assertEquals(0, recalled.queue.count)
    }

    @Test
    fun `recall unknown id is a no-op`() {
        val queue = enqueueN(1)
        val recalled = ComposerQueueMachine.recall(queue, 42L, "draft")
        assertNull(recalled.recalled)
        assertEquals(1, recalled.queue.count)
        assertEquals("draft", recalled.draft)
    }

    // ---------------------------------------------------------------- 删除 + 撤销（CMP-83）

    @Test
    fun `delete removes item and undo restores original order within 6000ms window`() {
        val queue = enqueueN(3)
        val deleted = ComposerQueueMachine.delete(queue, id = 2L)
        assertEquals("消息 2", deleted.deleted?.text)
        assertEquals(1, deleted.index)
        assertEquals(listOf("消息 1", "消息 3"), deleted.queue.items.map { it.text })

        // 6000ms 内撤销（计时由 M5Toast 负责；这里验证顺序恢复）
        val restored = ComposerQueueMachine.undoDelete(deleted.queue, deleted.deleted!!, deleted.index)
        assertEquals(listOf("消息 1", "消息 2", "消息 3"), restored.items.map { it.text })
    }

    @Test
    fun `delete unknown or double delete is idempotent`() {
        val queue = enqueueN(1)
        assertNull(ComposerQueueMachine.delete(queue, 99L).deleted)
        val deleted = ComposerQueueMachine.delete(queue, 1L)
        assertNull(ComposerQueueMachine.delete(deleted.queue, 1L).deleted)
    }

    @Test
    fun `undo delete is idempotent`() {
        val queue = enqueueN(2)
        val deleted = ComposerQueueMachine.delete(queue, 1L)
        val restored = ComposerQueueMachine.undoDelete(deleted.queue, deleted.deleted!!, deleted.index)
        val again = ComposerQueueMachine.undoDelete(restored, deleted.deleted!!, deleted.index)
        assertEquals(2, again.count) // 不重复插入
    }

    @Test
    fun `delete out-of-range index clamps on undo`() {
        val queue = enqueueN(2)
        val deleted = ComposerQueueMachine.delete(queue, 1L)
        val restored = ComposerQueueMachine.undoDelete(deleted.queue, deleted.deleted!!, index = 99)
        assertEquals(2, restored.count)
        assertEquals(deleted.deleted!!.id, restored.items.last().id)
    }
}
