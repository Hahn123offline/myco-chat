// CHAT-09, CHAT-10, CHAT-12, CHAT-13, CHAT-33, CHAT-36, CHAT-37, CHAT-38, CHAT-39, CHAT-40, CHAT-53
package dev.myco.android.ui.m5.chat.model

/**
 * chat 列表滚动跟随的**纯状态机**（`docs/spec/panels/chat.md` §3.1 / §3.2 / §3.6 / G-04）。
 *
 * 四套阈值（互不混用，数值全部来自规格）：
 * 1. **会话跟随**：判据 = 距底 ≤ **25dp**（DSH `FOLLOW_THRESHOLD=24` + 1）；读者移动且距底 >25dp → 停跟随；
 *    停跟随后 500ms 采样定时器按 25dp 重判；回到距底 < **1dp** 立即恢复跟随（§3.1 字段 5）。
 * 2. **回底钮迟滞**：距底 **>160dp 显示**、**≤24dp 隐藏**，两阈值之间保持原状态，不反复显隐（§3.2 字段 5）。
 * 3. **组内跟尾**：距**组底** ≤ **1dp**（与会话 25dp 互相独立，§3.6 字段 5/8）；拖动即暂停跟尾。
 * 4. **程序化滚动不算读者移动**：点回底 / 发送后贴底 / 版本切换回位都不取消跟随（§3.1 字段 4 / §3.2 字段 5）。
 *
 * 另含 G-04「内滚到边全量吸收、新手势才交外层」的仲裁器 [NestedScrollArbiter]。
 *
 * 本文件不依赖 Android/Compose（纯 JVM 可测）；UI 层只负责把 LazyListState 的
 * `distanceToBottom`、手势事件、帧回调喂进来，并按结果决定 `scrollToItem` / 绘制回底钮。
 */

/** 非跟随状态下保存的语义锚点（§3.1 字段 5：视口首块 key + 块内偏移）。 */
data class ScrollAnchor(val blockKey: String, val offsetDp: Float)

/** 组内跟尾状态（§3.6 字段 5：拖动即暂停、滚回距组底 ≤1dp 恢复）。 */
data class GroupFollowState(
    val following: Boolean = true,
    val dragging: Boolean = false,
    val distanceToGroupBottomDp: Float = 0f,
)

/** 会话滚动跟随的完整状态。 */
data class ScrollFollowState(
    val following: Boolean = true,
    val readerInteracting: Boolean = false,
    val loadingHistory: Boolean = false,
    val distanceToBottomDp: Float = 0f,
    /** §3.2 迟滞中间态：两阈值之间保持原值。 */
    val toBottomVisible: Boolean = false,
    /** 非跟随时保存的语义锚点（§3.1 字段 5 / CHAT-40）。 */
    val anchor: ScrollAnchor? = null,
    val group: GroupFollowState = GroupFollowState(),
) {
    /** 回底钮出现条件 = 非跟随（§3.2 字段 1；"列表有内容"由 UI 侧再判）。 */
    val toBottomButtonVisible: Boolean get() = !following && toBottomVisible

    /** 跟随时新内容应贴底（布局期同帧重定位，§3.1 字段 9：禁止帧后 `jumpTo`）。 */
    val shouldStickToBottom: Boolean get() = following && !loadingHistory
}

/**
 * §3.1–§3.6 的跟随策略（无内部可变状态；每次事件返回新 [ScrollFollowState]）。
 *
 * @param followThresholdDp 会话跟随判据 25dp。
 * @param resumeAtBottomDp 距底 <1dp 立即恢复跟随。
 * @param sampleIntervalMs 停跟随后的重判采样间隔 500ms。
 * @param showToBottomDp 回底钮显示阈值 160dp。
 * @param hideToBottomDp 回底钮隐藏阈值 24dp。
 * @param groupFollowThresholdDp 组内跟尾判据 1dp。
 */
class ScrollFollowPolicy(
    val followThresholdDp: Float = 25f,
    val resumeAtBottomDp: Float = 1f,
    val sampleIntervalMs: Long = 500L,
    val showToBottomDp: Float = 160f,
    val hideToBottomDp: Float = 24f,
    val groupFollowThresholdDp: Float = 1f,
) {
    /** 初始状态：进会话一次性跟底（CHAT-53）；首屏回放期间不跟底。 */
    fun initialState(loadingHistory: Boolean = false): ScrollFollowState =
        ScrollFollowState(following = !loadingHistory, loadingHistory = loadingHistory)

    // ---------------------------------------------------------------- 会话跟随

    /**
     * 读者手势序列的**第一帧**（按下后越过 touch slop）→ 当帧停跟随（§3.1 字段 4 / CHAT-37）。
     * 不等 500ms 采样；UI 必须把"读者手势"与程序化滚动分开喂。
     *
     * @param anchor 当前视口语义锚点（首块 key + 块内偏移）；停跟随后用它补偿上方插入（CHAT-40）。
     */
    fun onReaderGestureStart(
        state: ScrollFollowState,
        distanceToBottomDp: Float,
        anchor: ScrollAnchor? = null,
    ): ScrollFollowState {
        return withDistance(
            state.copy(
                following = false,
                readerInteracting = true,
                anchor = anchor ?: state.anchor,
            ),
            distanceToBottomDp,
        )
    }

    /** 读者手势结束（抬手）：距底 ≤25dp → 恢复跟随；否则保持非跟随并保留锚点。 */
    fun onReaderGestureEnd(state: ScrollFollowState, distanceToBottomDp: Float): ScrollFollowState {
        val next = withDistance(state.copy(readerInteracting = false), distanceToBottomDp)
        val following = !next.loadingHistory && distanceToBottomDp <= followThresholdDp
        return next.copy(
            following = following,
            anchor = if (following) null else next.anchor,
        )
    }

    /**
     * 500ms 采样 tick（§3.1 字段 5：停跟随后的定时器按 25dp 重判）。
     * 读者正在拖动时不抢状态；首屏回放期间不恢复跟底（§3.1 字段 10）。
     */
    fun onSampleTick(state: ScrollFollowState, distanceToBottomDp: Float): ScrollFollowState {
        val next = withDistance(state, distanceToBottomDp)
        if (next.readerInteracting || next.loadingHistory) return next
        val resume = !next.following && distanceToBottomDp <= followThresholdDp
        return if (resume) next.copy(following = true, anchor = null) else next
    }

    /**
     * 内容增长（流式 token / 上方插入 block）。
     *
     * - 跟随时：目标重定向到新底（不跳 floor，§3.1 字段 5/9）；
     * - 非跟随时：**不拽回读者**（CHAT-36：距底 26dp 时新内容到达保持位置）；
     *   除非距底已 <1dp（回到贴底 → 立即恢复跟随）。
     */
    fun onContentGrew(state: ScrollFollowState, distanceToBottomDp: Float): ScrollFollowState {
        val next = withDistance(state, distanceToBottomDp)
        if (next.following) return next
        if (next.readerInteracting || next.loadingHistory) return next
        val resume = distanceToBottomDp < resumeAtBottomDp
        return if (resume) next.copy(following = true, anchor = null) else next
    }

    /**
     * 程序化滚动（点回底 / 发送后贴底 / 版本切换回位）：**不算读者移动**，
     * 不得取消跟随（§3.1 字段 4 / §3.2 字段 5 / CHAT-39）。
     */
    fun onProgrammaticScroll(state: ScrollFollowState): ScrollFollowState = state

    /** 程序化回底：直接到底 + `following=true`（无动画，§3.2 字段 5 / CHAT-39）。 */
    fun onProgrammaticScrollToBottom(state: ScrollFollowState): ScrollFollowState =
        state.copy(
            following = true,
            anchor = null,
            distanceToBottomDp = 0f,
            toBottomVisible = false,
        )

    /**
     * IME 高度变化 = 容器尺寸变化（§3.1 字段 10）：
     * 跟随时贴底、不跟随时保持锚点——状态机**无需变更**，由 UI 按 [ScrollFollowState.shouldStickToBottom]
     * / [ScrollFollowState.anchor] 执行。
     */
    @Suppress("UNUSED_PARAMETER")
    fun onViewportChanged(state: ScrollFollowState, imeVisible: Boolean): ScrollFollowState = state

    /** 首屏回放开始：不跟底（否则回放逐帧滚屏，§3.1 字段 10 / CHAT-53）。 */
    fun onLoadHistoryStarted(state: ScrollFollowState): ScrollFollowState =
        state.copy(loadingHistory = true, following = false)

    /** 首屏回放完成：一次性跟底（CHAT-53）。 */
    fun onLoadHistoryFinished(state: ScrollFollowState): ScrollFollowState =
        state.copy(loadingHistory = false, following = true, anchor = null)

    /** 版本切换开始：先记锚点（§3.1 字段 5 / §3.17 / CHAT-33）。 */
    fun onVersionSwitchStart(state: ScrollFollowState, anchor: ScrollAnchor): ScrollFollowState =
        state.copy(anchor = anchor)

    /** 版本切换完成：布局期同帧按锚点回位（程序化，不算读者移动）。 */
    fun onVersionSwitchDone(state: ScrollFollowState): ScrollFollowState = state

    // ---------------------------------------------------------------- 组内跟尾

    /** 组体内滚动开始：**拖动即暂停跟尾**（§3.6 字段 4 / CHAT-12）。 */
    fun onGroupGestureStart(state: ScrollFollowState): ScrollFollowState =
        state.copy(group = state.group.copy(dragging = true, following = false))

    /** 组体内滚动结束：距组底 ≤1dp → 恢复贴底（CHAT-13）。 */
    fun onGroupGestureEnd(state: ScrollFollowState, distanceToGroupBottomDp: Float): ScrollFollowState =
        state.copy(
            group = state.group.copy(
                dragging = false,
                following = distanceToGroupBottomDp <= groupFollowThresholdDp,
                distanceToGroupBottomDp = distanceToGroupBottomDp,
            ),
        )

    /** 组内内容增长：贴底则保持贴底，非贴底保持偏移（§3.6 字段 5）；滚回 ≤1dp 恢复贴底（CHAT-13）。 */
    fun onGroupContentGrew(state: ScrollFollowState, distanceToGroupBottomDp: Float): ScrollFollowState {
        val group = state.group
        val following = when {
            group.dragging -> false
            group.following -> true
            else -> distanceToGroupBottomDp <= groupFollowThresholdDp
        }
        return state.copy(
            group = group.copy(
                following = following,
                distanceToGroupBottomDp = distanceToGroupBottomDp,
            ),
        )
    }

    /** 组被关闭/重建、或 settle 取消封顶：内滚位置与跟尾重置（§3.6 字段 5）。 */
    fun onGroupSettle(state: ScrollFollowState): ScrollFollowState =
        state.copy(group = GroupFollowState())

    // ---------------------------------------------------------------- 内部

    private fun withDistance(state: ScrollFollowState, distanceToBottomDp: Float): ScrollFollowState {
        val visible = when {
            distanceToBottomDp > showToBottomDp -> true
            distanceToBottomDp <= hideToBottomDp -> false
            else -> state.toBottomVisible
        }
        return state.copy(distanceToBottomDp = distanceToBottomDp, toBottomVisible = visible)
    }
}

/** 滚动意图的消费者。 */
enum class ScrollTarget {
    /** 内层（过程组体 / 工具井 / 代码体）消费。 */
    INNER,

    /** 外层会话列表消费。 */
    OUTER,

    /** 不消费（非手势 / 零位移 / 程序化滚动不走这里）。 */
    NONE,
}

/**
 * 一次手势内某段位移的路由结果。
 *
 * @param absorbed `target=INNER` 且内层已到边：残余位移被**全量吸收**，绝不转交外层（G-04 / CHAT-09）。
 */
data class NestedScrollDecision(
    val target: ScrollTarget,
    val deltaDp: Float,
    val absorbed: Boolean,
)

/** 一次手势开始时的内层可滚能力快照（决定这次手势归谁，而不是"到边那帧"再判）。 */
data class NestedScrollGesture(
    val active: Boolean = false,
    val startedCanScrollForward: Boolean = false,
    val startedCanScrollBackward: Boolean = false,
)

/**
 * G-04：内滚到边**全量吸收**，只有**新手势**才允许外层接管（CHAT-09 / CHAT-10）。
 *
 * 判据按"手势开始时的内层可滚能力"快照：
 * - 手势在可滚方向内开始 → 整段手势都归内层；到边后的残余被吸收（同一手势内绝不外泄速度）；
 * - 手势一开始就在该方向的边缘 → 这次手势归外层（新手势才交外层）。
 *
 * 工具井（§3.9）、代码体（§3.12）复用同一仲裁器：两者都声明"唯一纵向内滚层"。
 */
object NestedScrollArbiter {
    fun beginGesture(
        canScrollForward: Boolean,
        canScrollBackward: Boolean,
    ): NestedScrollGesture = NestedScrollGesture(
        active = true,
        startedCanScrollForward = canScrollForward,
        startedCanScrollBackward = canScrollBackward,
    )

    fun endGesture(): NestedScrollGesture = NestedScrollGesture()

    /**
     * @param deltaDp 本次位移；正值 = 朝内容底部方向滚（手指上滑）。
     * @param canScrollForward 内层当前还能朝底部方向滚。
     * @param canScrollBackward 内层当前还能朝顶部方向滚。
     */
    fun route(
        gesture: NestedScrollGesture,
        deltaDp: Float,
        canScrollForward: Boolean,
        canScrollBackward: Boolean,
    ): NestedScrollDecision {
        if (!gesture.active || deltaDp == 0f) {
            return NestedScrollDecision(ScrollTarget.NONE, 0f, absorbed = false)
        }
        val forward = deltaDp > 0f
        val startedAtEdge = if (forward) {
            !gesture.startedCanScrollForward
        } else {
            !gesture.startedCanScrollBackward
        }
        if (startedAtEdge) {
            // 新手势一开始就在边缘 → 外层接管（到边继续滑动 = 新手势才允许外层滚，CHAT-10）。
            return NestedScrollDecision(ScrollTarget.OUTER, deltaDp, absorbed = false)
        }
        val canScrollNow = if (forward) canScrollForward else canScrollBackward
        // 手势中途到边：残余位移被内层吞掉，不把速度传给外层（CHAT-09）。
        return NestedScrollDecision(ScrollTarget.INNER, deltaDp, absorbed = !canScrollNow)
    }
}
