package dev.myco.android.ui

import dev.myco.android.ui.model.ChatMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话渲染路径回归：长会话"打开即闪退"的根因是 activeLeaf 失效时退回渲染全部消息，
 * 工具中间步与正式回答共用同一个 turn，导致 LazyColumn key 重复（Key "ph-1" was already used）。
 */
class VisibleMessagesTest {

    private fun user(id: String, parent: String? = null, turn: Long = 1) = ChatMessageUi(
        role = "user", text = "提问", messageId = id, parentId = parent, turn = turn,
    )

    private fun assistant(
        id: String,
        parent: String? = null,
        node: String? = null,
        turn: Long? = 1,
        version: Int = 1,
        finish: String? = "end_turn",
    ) = ChatMessageUi(
        role = "assistant", text = "回答", messageId = id, parentId = parent,
        nodeId = node, version = version, turn = turn, finishReason = finish,
    )

    private fun toolStep(id: String, turn: Long = 1) = ChatMessageUi(
        role = "assistant", text = "我先搜一下", messageId = id, parentId = null,
        nodeId = null, version = 1, turn = turn, finishReason = "tool_use",
    )

    @Test
    fun `tool placeholders never rendered as body messages`() {
        val all = listOf(
            user("u1"),
            toolStep("t1"),
            assistant("a1", parent = "u1", node = "n1"),
        )
        val visible = resolveVisibleMessages(all, activeLeaf = "a1")
        assertEquals(listOf("u1", "a1"), visible.messages.map { it.messageId })
        assertTrue(visible.fromTree)
    }

    @Test
    fun `invalid activeLeaf falls back to newest branch instead of every message`() {
        // 两次重生成：同一 node 三个版本都挂在 u1 下面；activeLeaf 指向了不存在的 id
        val all = listOf(
            user("u1"),
            toolStep("t1"),
            assistant("a1", parent = "u1", node = "n1", turn = 1, version = 1),
            assistant("a2", parent = "u1", node = "n1", turn = 2, version = 2),
            assistant("a3", parent = "u1", node = "n1", turn = 3, version = 3),
        )
        val visible = resolveVisibleMessages(all, activeLeaf = "missing-leaf")
        assertEquals(listOf("u1", "a3"), visible.messages.map { it.messageId })
        // 同一 turn 的过程组不会出现两次
        val turns = visible.messages.filter { it.role == "assistant" }.mapNotNull { it.turn }
        assertEquals(turns.distinct(), turns)
    }

    @Test
    fun `null activeLeaf picks latest leaf path for new format sessions`() {
        val all = listOf(
            user("u1"),
            assistant("a1", parent = "u1", node = "n1"),
            user("u2", parent = "a1"),
            assistant("a2", parent = "u2", node = "n2", turn = 2),
        )
        val visible = resolveVisibleMessages(all, activeLeaf = null)
        assertEquals(listOf("u1", "a1", "u2", "a2"), visible.messages.map { it.messageId })
        assertTrue(visible.fromTree)
    }

    @Test
    fun `legacy flat sessions keep all messages`() {
        val all = listOf(
            assistant("a1", parent = null, node = null, turn = null, finish = null),
            assistant("a2", parent = null, node = null, turn = null, finish = null),
        )
        val visible = resolveVisibleMessages(all, activeLeaf = null)
        assertEquals(2, visible.messages.size)
        assertFalse(visible.fromTree)
    }

    @Test
    fun `path includes only selected version`() {
        val all = listOf(
            user("u1"),
            assistant("a1", parent = "u1", node = "n1", turn = 1, version = 1),
            assistant("a2", parent = "u1", node = "n1", turn = 2, version = 2),
            assistant("a3", parent = "a1", node = "n2", turn = 3, version = 1),
        )
        val visible = resolveVisibleMessages(all, activeLeaf = "a3")
        assertEquals(listOf("u1", "a1", "a3"), visible.messages.map { it.messageId })
    }
}
