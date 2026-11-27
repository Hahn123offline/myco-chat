// CHAT-09, CHAT-10, CHAT-12, CHAT-13, CHAT-33, CHAT-36, CHAT-37, CHAT-38, CHAT-39, CHAT-40, CHAT-53
package dev.myco.android.ui.m5.chat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ScrollFollowPolicy`（§3.1 会话跟随 / §3.2 回底钮迟滞 / §3.6 组内跟尾 / G-04 到边吸收）的纯状态机单测。
 *
 * 四套阈值互不混用：25dp（会话跟随）、500ms（重判采样）、160/24dp（回底钮迟滞）、1dp（组内跟尾）。
 */
class ScrollFollowPolicyTest {

    private val policy = ScrollFollowPolicy()
    private val anchor = ScrollAnchor(blockKey = "2/user", offsetDp = 12f)

    private fun initial() = policy.initialState()

    // ---------------------------------------------------------------- §3.1 会话跟随（25dp）

    @Test
    fun `spec thresholds are the frozen numbers`() {
        assertEquals(25f, policy.followThresholdDp, 0.001f) // DSH FOLLOW_THRESHOLD=24 + 1
        assertEquals(1f, policy.resumeAtBottomDp, 0.001f)
        assertEquals(500L, policy.sampleIntervalMs)
        assertEquals(160f, policy.showToBottomDp, 0.001f)
        assertEquals(24f, policy.hideToBottomDp, 0.001f)
        assertEquals(1f, policy.groupFollowThresholdDp, 0.001f) // 与会话 25dp 互相独立
    }

    @Test
    fun `initial state follows bottom`() {
        val state = initial()
        assertTrue(state.following)
        assertTrue(state.shouldStickToBottom)
        assertFalse(state.toBottomButtonVisible)
        assertNull(state.anchor)
    }

    @Test
    fun `reader gesture stops following on first frame`() {
        // CHAT-37：手势第一帧（按下越过 touch slop）当帧停跟随，不等 500ms 采样
        val state = policy.onReaderGestureStart(initial(), distanceToBottomDp = 300f, anchor = anchor)
        assertFalse(state.following)
        assertTrue(state.readerInteracting)
        assertFalse(state.shouldStickToBottom)
        assertEquals(anchor, state.anchor)
        // 300 > 160 → 回底钮出现（CHAT-38 上阈值）
        assertTrue(state.toBottomButtonVisible)
    }

    @Test
    fun `release within 25dp resumes following`() {
        var state = policy.onReaderGestureStart(initial(), distanceToBottomDp = 200f, anchor = anchor)
        state = policy.onReaderGestureEnd(state, distanceToBottomDp = 25f)
        assertTrue(state.following)
        assertFalse(state.readerInteracting)
        assertNull(state.anchor) // 恢复跟随后不再需要锚点
        assertTrue(state.shouldStickToBottom)
    }

    @Test
    fun `release beyond 25dp keeps not following and keeps anchor`() {
        var state = policy.onReaderGestureStart(initial(), distanceToBottomDp = 200f, anchor = anchor)
        state = policy.onReaderGestureEnd(state, distanceToBottomDp = 25.1f)
        assertFalse(state.following)
        assertEquals(anchor, state.anchor)
        assertFalse(state.shouldStickToBottom)
    }

    @Test
    fun `content growth does not drag a reader who is 26dp from bottom`() {
        // CHAT-36 前半：距底 26dp 时新内容到达不拽回（保持位置）
        var state = policy.onReaderGestureStart(initial(), distanceToBottomDp = 100f, anchor = anchor)
        state = policy.onReaderGestureEnd(state, distanceToBottomDp = 26f)
        assertFalse(state.following)
        val grown = policy.onContentGrew(state, distanceToBottomDp = 26f)
        assertFalse(grown.following)
        assertFalse(grown.shouldStickToBottom)
        assertEquals(anchor, grown.anchor) // CHAT-40：锚点不丢
    }

    @Test
    fun `content growth redirects following to the new bottom`() {
        val following = policy.onContentGrew(initial(), distanceToBottomDp = 24f)
        assertTrue(following.following)
        assertTrue(following.shouldStickToBottom)
    }

    @Test
    fun `back within 1dp resumes immediately`() {
        var state = policy.onReaderGestureStart(initial(), 100f, anchor)
        state = policy.onReaderGestureEnd(state, 40f)
        assertFalse(state.following)
        val resumed = policy.onContentGrew(state, distanceToBottomDp = 0.5f)
        assertTrue(resumed.following) // §3.1 字段 5：回到距底 <1dp 立即恢复
        assertNull(resumed.anchor)
    }

    @Test
    fun `500ms sample re-judges by 25dp`() {
        // CHAT-36 后半：停跟随后 500ms 采样按 25dp 重判
        var state = policy.onReaderGestureStart(initial(), 200f, anchor)
        state = policy.onReaderGestureEnd(state, 40f)
        assertFalse(state.following)

        assertFalse(policy.onSampleTick(state, distanceToBottomDp = 30f).following)
        assertFalse(policy.onSampleTick(state, distanceToBottomDp = 25.5f).following)
        val resumed = policy.onSampleTick(state, distanceToBottomDp = 20f)
        assertTrue(resumed.following)
        assertNull(resumed.anchor)
    }

    @Test
    fun `sample tick does not fight an active reader gesture`() {
        val dragging = policy.onReaderGestureStart(initial(), distanceToBottomDp = 120f, anchor = anchor)
        val ticked = policy.onSampleTick(dragging, distanceToBottomDp = 5f)
        assertFalse(ticked.following) // 拖动中不抢状态
        assertTrue(ticked.readerInteracting)
    }

    // ---------------------------------------------------------------- §3.2 回底钮迟滞（160 / 24）

    @Test
    fun `to bottom hysteresis does not flicker between 24 and 160`() {
        // CHAT-38：>160 出现、≤24 消失；24–160 之间保持原状态，来回滚不闪
        var state = policy.onReaderGestureStart(initial(), distanceToBottomDp = 200f, anchor = anchor)
        state = policy.onReaderGestureEnd(state, 200f)
        assertTrue(state.toBottomVisible)
        assertTrue(state.toBottomButtonVisible)

        // 落在迟滞区间 → 保持显示
        listOf(159f, 120f, 60f, 30f, 25f).forEach { distance ->
            state = policy.onContentGrew(state, distance)
            assertTrue("distance=$distance 应保持显示", state.toBottomVisible)
        }
        assertEquals(24f, policy.hideToBottomDp, 0.001f)
        state = policy.onContentGrew(state, 24f)
        assertFalse(state.toBottomVisible) // 回到 ≤24 → 隐藏
        assertFalse(state.toBottomButtonVisible)

        // 再次落在迟滞区间 → 保持隐藏（不闪）
        listOf(25f, 100f, 159f).forEach { distance ->
            state = policy.onContentGrew(state, distance)
            assertFalse("distance=$distance 应保持隐藏", state.toBottomVisible)
        }
        state = policy.onContentGrew(state, 161f)
        assertTrue(state.toBottomVisible) // >160 → 出现
    }

    // ---------------------------------------------------------------- 程序化滚动（不算读者移动）

    @Test
    fun `programmatic scroll never cancels following`() {
        // CHAT-39：点回底/发送后贴底/版本切换回位都不取消跟随
        val state = initial()
        assertSame(state, policy.onProgrammaticScroll(state))
        assertTrue(policy.onProgrammaticScroll(state).following)
    }

    @Test
    fun `programmatic scroll to bottom restores following`() {
        var state = policy.onReaderGestureStart(initial(), distanceToBottomDp = 800f, anchor = anchor)
        state = policy.onReaderGestureEnd(state, 800f)
        assertFalse(state.following)
        assertTrue(state.toBottomButtonVisible)

        val bottom = policy.onProgrammaticScrollToBottom(state)
        assertTrue(bottom.following)
        assertNull(bottom.anchor)
        assertEquals(0f, bottom.distanceToBottomDp, 0.001f)
        assertFalse(bottom.toBottomVisible)
        assertFalse(bottom.toBottomButtonVisible)
    }

    @Test
    fun `version switch records anchor then restores without touching follow`() {
        // CHAT-33 / §3.17：先记锚点，切换后布局期同帧回位（程序化）
        val paused = policy.onReaderGestureEnd(
            policy.onReaderGestureStart(initial(), 300f, anchor = anchor),
            300f,
        )
        val switching = policy.onVersionSwitchStart(paused, anchor)
        assertEquals(anchor, switching.anchor)
        assertFalse(switching.following)
        // 切换完成是程序化的，不改跟随状态
        assertEquals(switching, policy.onVersionSwitchDone(switching))
    }

    // ---------------------------------------------------------------- 边界态（IME / 回放）

    @Test
    fun `ime toggle keeps following and keeps anchor when paused`() {
        // §3.1 字段 10：跟随时贴底、不跟随时保持锚点；状态机本身不变
        val following = initial()
        assertTrue(policy.onViewportChanged(following, imeVisible = true).following)
        assertTrue(policy.onViewportChanged(following, imeVisible = false).following)

        val paused = policy.onReaderGestureEnd(
            policy.onReaderGestureStart(following, 300f, anchor),
            300f,
        )
        val afterIme = policy.onViewportChanged(paused, imeVisible = true)
        assertFalse(afterIme.following)
        assertEquals(anchor, afterIme.anchor)
    }

    @Test
    fun `first screen replay never follows until finished`() {
        // CHAT-53：加载期间不跟底，回放完成后一次性跟底
        var state = policy.initialState(loadingHistory = true)
        assertFalse(state.following)
        assertFalse(state.shouldStickToBottom)
        // 回放期间采样也不恢复
        assertFalse(policy.onSampleTick(state, 0f).following)
        assertFalse(policy.onContentGrew(state, 0f).following)

        state = policy.onLoadHistoryFinished(state)
        assertTrue(state.following)
        assertFalse(state.loadingHistory)
        assertTrue(state.shouldStickToBottom)
    }

    // ---------------------------------------------------------------- §3.6 组内跟尾（1dp）

    @Test
    fun `group drag pauses group follow`() {
        // CHAT-12：组内拖动即暂停跟尾；拖动中内容增长不抢回
        var state = policy.onGroupGestureStart(initial())
        assertTrue(state.group.dragging)
        assertFalse(state.group.following)
        state = policy.onGroupContentGrew(state, distanceToGroupBottomDp = 3f)
        assertFalse(state.group.following)
    }

    @Test
    fun `group follow resumes within 1dp`() {
        // CHAT-13：暂停后滚回距组底 ≤1dp，新 token 到达时恢复贴底
        var state = policy.onGroupGestureStart(initial())
        state = policy.onGroupGestureEnd(state, distanceToGroupBottomDp = 1f)
        assertTrue(state.group.following)
        assertFalse(state.group.dragging)

        state = policy.onGroupGestureStart(state)
        state = policy.onGroupGestureEnd(state, distanceToGroupBottomDp = 1.5f)
        assertFalse(state.group.following)
        state = policy.onGroupContentGrew(state, distanceToGroupBottomDp = 0.5f)
        assertTrue(state.group.following)
    }

    @Test
    fun `group settle resets inner scroll and follow`() {
        var state = policy.onGroupGestureStart(initial())
        state = policy.onGroupGestureEnd(state, distanceToGroupBottomDp = 20f)
        assertFalse(state.group.following)
        val settled = policy.onGroupSettle(state)
        // settle 取消封顶 → 内滚位置与跟尾重置（§3.6 字段 5）
        assertTrue(settled.group.following)
        assertFalse(settled.group.dragging)
        assertEquals(0f, settled.group.distanceToGroupBottomDp, 0.001f)
    }

    // ---------------------------------------------------------------- G-04 到边吸收

    @Test
    fun `inner scroll absorbs residual at edge during the same gesture`() {
        // CHAT-09：一次手势内滑到边后继续上滑，残余速度被吸收，不外泄给外层
        val gesture = NestedScrollArbiter.beginGesture(
            canScrollForward = true,
            canScrollBackward = true,
        )
        val midGesture = NestedScrollArbiter.route(
            gesture = gesture,
            deltaDp = 40f,
            canScrollForward = false, // 已到内滚底边
            canScrollBackward = true,
        )
        assertEquals(ScrollTarget.INNER, midGesture.target)
        assertTrue(midGesture.absorbed)
        assertEquals(40f, midGesture.deltaDp, 0.001f)
    }

    @Test
    fun `new gesture at edge hands over to outer list`() {
        // CHAT-10：到底后抬起再按（新手势）→ 外层开始滚
        val gesture = NestedScrollArbiter.beginGesture(
            canScrollForward = false, // 手势一开始就在边缘
            canScrollBackward = true,
        )
        val decision = NestedScrollArbiter.route(
            gesture = gesture,
            deltaDp = 40f,
            canScrollForward = false,
            canScrollBackward = true,
        )
        assertEquals(ScrollTarget.OUTER, decision.target)
        assertFalse(decision.absorbed)
    }

    @Test
    fun `backward direction follows the same edge rule`() {
        // 顶部到边：新手势下拉 → 外层；中途到顶 → 内层吸收
        val atTop = NestedScrollArbiter.beginGesture(canScrollForward = true, canScrollBackward = false)
        assertEquals(
            ScrollTarget.OUTER,
            NestedScrollArbiter.route(atTop, -30f, canScrollForward = true, canScrollBackward = false).target,
        )
        val interior = NestedScrollArbiter.beginGesture(canScrollForward = true, canScrollBackward = true)
        val absorbed = NestedScrollArbiter.route(interior, -30f, canScrollForward = true, canScrollBackward = false)
        assertEquals(ScrollTarget.INNER, absorbed.target)
        assertTrue(absorbed.absorbed)
    }

    @Test
    fun `inner scroll consumes delta while it can still scroll`() {
        val gesture = NestedScrollArbiter.beginGesture(canScrollForward = true, canScrollBackward = true)
        val decision = NestedScrollArbiter.route(gesture, 20f, canScrollForward = true, canScrollBackward = true)
        assertEquals(ScrollTarget.INNER, decision.target)
        assertFalse(decision.absorbed)
    }

    @Test
    fun `programmatic scroll is not routed through the gesture arbiter`() {
        // 程序化滚动（无手势）不得被当作读者移动，也不该转交外层
        val idle = NestedScrollArbiter.endGesture()
        val decision = NestedScrollArbiter.route(idle, 50f, canScrollForward = true, canScrollBackward = true)
        assertEquals(ScrollTarget.NONE, decision.target)
        assertEquals(0f, decision.deltaDp, 0.001f)
        // 零位移同样不消费
        val gesture = NestedScrollArbiter.beginGesture(true, true)
        assertEquals(ScrollTarget.NONE, NestedScrollArbiter.route(gesture, 0f, true, true).target)
    }
}
