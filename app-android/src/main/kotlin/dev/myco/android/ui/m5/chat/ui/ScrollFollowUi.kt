// 验收 ID：CHAT-09 CHAT-10 CHAT-12 CHAT-13 CHAT-33 CHAT-36 CHAT-37 CHAT-38 CHAT-39 CHAT-40
//          CHAT-53 CHAT-58
//
// chat 列表滚动跟随（`docs/spec/panels/chat.md` §3.1 `chat.list.scroll-follow` /
// §3.2 `chat.list.to-bottom` / §3.6 组内跟尾 / G-04 到边吸收）。
//
// 分工：
//  * **策略在 model 层**：`ScrollFollowPolicy`（25dp 判据 / 500ms 采样 / 160–24dp 迟滞 / 1dp 组内跟尾 /
//    程序化滚动不算读者移动）已由 58 条单测覆盖，本文件**不重写状态机**，只负责喂事件与执行滚动；
//  * **本文件负责**：LazyListState → distanceToBottom 的测量、读者手势观察（当帧停跟随，CHAT-37）、
//    布局后贴底执行、回底钮（44×44 / overlay 不占位 / 160ms）、G-04 到边吸收判定；
//  * 纯几何与判定抽成 [ChatScrollMetrics] / [NestedScrollAbsorb] 两个 object，JVM 单测直接覆盖。
//
// 硬约束：
//  * 回底钮 = overlay，**不占列表布局**（G-01）：放在列表之上的 Box 里，出现/消失不产生高度；
//  * 程序化滚动（回底 / 发送后贴底 / 版本切换回位）**不取消跟随**（§3.1 字段 4 / CHAT-39）；
//  * 首屏回放期间不跟底（否则回放逐帧滚屏），回放完成一次性跟底（§3.1 字段 10 / CHAT-53）。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.chat.model.NestedScrollGesture
import dev.myco.android.ui.m5.chat.model.ScrollAnchor
import dev.myco.android.ui.m5.chat.model.ScrollFollowPolicy
import dev.myco.android.ui.m5.chat.model.ScrollFollowState
import dev.myco.android.ui.m5.primitives.reducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * §3.1 / §3.2 的**测量与判定**纯契约（JVM 单测覆盖；不依赖 Android）。
 *
 * 数值与 `ScrollFollowPolicy` 的默认参数保持单一真源：这里只暴露"从 layoutInfo 原始量换算"的函数，
 * 阈值本身不复制第二份（调用处直接用 policy 的字段）。
 */
object ChatScrollMetrics {
    /** §3.1 字段 5：跟随即"距底 ≤25dp"（DSH FOLLOW_THRESHOLD=24 + 1）。 */
    const val FOLLOW_THRESHOLD_DP: Float = 25f

    /** §3.1 字段 5：停跟随后 500ms 采样重判。 */
    const val SAMPLE_INTERVAL_MS: Long = 500L

    /** §3.3 三点块高 34dp（`chat.msg.stream-status`，TurnBlockUi 用；放这里单一真源）。 */
    const val STREAM_STATUS_HEIGHT_DP: Float = 34f

    /**
     * 距底（dp）。`lastItemEndPx` = 最后一个可见项底边（item 坐标系）；
     * `viewportEndOffsetPx` = 视口可读区底边（LazyListLayoutInfo.viewportEndOffset，已扣底部 contentPadding）。
     * 贴底时两者相等 → 0。
     */
    fun distanceToBottomDp(lastItemEndPx: Int, viewportEndOffsetPx: Int, density: Float): Float {
        if (density <= 0f) return 0f
        return ((lastItemEndPx - viewportEndOffsetPx).coerceAtLeast(0)).toFloat() / density
    }

    /** §3.2 字段 5：距底 >160dp 显示回底钮、≤24dp 隐藏（迟滞在 policy 内实现，这里只做判定辅助）。 */
    fun shouldShowToBottom(distanceDp: Float, policy: ScrollFollowPolicy = ScrollFollowPolicy()): Boolean =
        distanceDp > policy.showToBottomDp

    /** 贴底判据（§3.1）：距底 ≤25dp 视为跟随目标位。 */
    fun isNearBottom(distanceDp: Float, policy: ScrollFollowPolicy = ScrollFollowPolicy()): Boolean =
        distanceDp <= policy.followThresholdDp
}

/**
 * G-04 到边吸收的**纯判定**（CHAT-09 / CHAT-10）。
 *
 * 与 `NestedScrollArbiter` 同一语义，但用于 "Compose 内层 scroller 已经消费完、剩下 available 残余"
 * 这一时机：此时内层必然已到边。
 *  - 手势**开始时内层在可滚方向** → 整段手势归内层，到边后的残余**全量吸收**（返回 true）；
 *  - 手势**一开始就在该方向边缘** → 这次是新手势，交外层（返回 false）。
 */
object NestedScrollAbsorb {

    /** @param deltaYDp 正值 = 朝内容底部方向（手指上滑）。 */
    fun absorb(gesture: NestedScrollGesture, deltaYDp: Float): Boolean {
        if (!gesture.active || deltaYDp == 0f) return false
        val forward = deltaYDp > 0f
        val startedAtEdge = if (forward) {
            !gesture.startedCanScrollForward
        } else {
            !gesture.startedCanScrollBackward
        }
        return !startedAtEdge
    }
}

/**
 * 滚动跟随控制器：把 `LazyListState` 的量喂给 model 层 `ScrollFollowPolicy`，并执行贴底滚动。
 *
 * 状态（`state`）是纯数据 [ScrollFollowState]，可被上层读来做回底钮显隐/调试断言。
 */
@Stable
class ChatScrollFollowController internal constructor(
    private val listState: LazyListState,
    val policy: ScrollFollowPolicy,
    private val density: Float,
    private val scope: CoroutineScope,
) {
    var state: ScrollFollowState by mutableStateOf(policy.initialState())
        private set

    /** 最近一次渲染的 block 稳定 key 列表（用于语义锚点 blockKey，§3.1 字段 5）。 */
    private var blockKeys: List<String> = emptyList()

    /** 由 ChatListM5 每帧同步（block key 与列表项一一对应，CHAT-57）。 */
    internal fun updateBlockKeys(keys: List<String>) {
        blockKeys = keys
    }

    /** 非跟随状态下的视口语义锚点（首块 key + 块内偏移），供上方插入补偿（CHAT-40）。 */
    fun currentAnchor(): ScrollAnchor? {
        val firstIndex = listState.firstVisibleItemIndex
        val key = blockKeys.getOrNull(firstIndex) ?: return null
        return ScrollAnchor(blockKey = key, offsetDp = listState.firstVisibleItemScrollOffset / density)
    }

    /** 当前距底（dp）；未测量出内容时给一个"远非贴底"的保守值（不猜）。 */
    fun distanceToBottomDp(): Float {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        if (total == 0) return 0f
        val last = info.visibleItemsInfo.lastOrNull() ?: return Float.MAX_VALUE / 2f
        if (last.index < total - 1) return Float.MAX_VALUE / 2f
        return ChatScrollMetrics.distanceToBottomDp(
            lastItemEndPx = last.offset + last.size,
            viewportEndOffsetPx = info.viewportEndOffset,
            density = density,
        )
    }

    /** 读者手势第一帧（越过 touch slop）→ 当帧停跟随（CHAT-37），并保存语义锚点（CHAT-40）。 */
    fun onReaderGestureStart() {
        state = policy.onReaderGestureStart(state, distanceToBottomDp(), anchor = currentAnchor())
    }

    /** 读者抬手：距底 ≤25dp 恢复跟随，否则保持非跟随（§3.1 字段 5）。 */
    fun onReaderGestureEnd() {
        state = policy.onReaderGestureEnd(state, distanceToBottomDp())
    }

    /**
     * 内容增长（流式 token / 上方插入 block）：
     * 跟随时目标重定向到新底（CHAT-36 后半）；非跟随时不拽回读者（CHAT-36 前半 / CHAT-40）。
     */
    fun onContentGrew() {
        state = policy.onContentGrew(state, distanceToBottomDp())
        if (state.shouldStickToBottom) stickToBottom()
    }

    /** 500ms 采样（停跟随后按 25dp 重判，CHAT-36）。 */
    internal fun sampleTick() {
        state = policy.onSampleTick(state, distanceToBottomDp())
        if (state.shouldStickToBottom) stickToBottom()
    }

    /** 程序化滚动（版本切换回位等）：不算读者移动，不取消跟随（§3.1 字段 4）。 */
    fun onProgrammaticScroll() {
        state = policy.onProgrammaticScroll(state)
    }

    /**
     * 回底钮 / 发送后贴底：直接到底（无动画）+ 恢复跟随（§3.2 字段 5 / CHAT-39）。
     * 这是**程序化**滚动，不进 [chatReaderGestureObserver]，所以不会自己把跟随关掉。
     */
    fun scrollToBottom() {
        state = policy.onProgrammaticScrollToBottom(state)
        stickToBottom()
    }

    /** 首屏回放开始/完成（§3.1 字段 10 / CHAT-53）。 */
    internal fun onLoadHistoryChanged(loading: Boolean) {
        state = if (loading) policy.onLoadHistoryStarted(state) else policy.onLoadHistoryFinished(state)
        if (!loading) stickToBottom()
    }

    /** G-04：内层残余位移是否应被吸收（供内滚容器挂 [Modifier.absorbsInnerEdgeScroll]）。 */
    fun shouldAbsorbInnerResidual(gesture: NestedScrollGesture, deltaYDp: Float): Boolean =
        NestedScrollAbsorb.absorb(gesture, deltaYDp)

    private fun stickToBottom() {
        scope.launch {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 0) return@launch
            // 直接落到底（无动画）：§3.1 字段 9 / §3.2 字段 5。
            listState.scrollToItem(total - 1, Int.MAX_VALUE)
        }
    }
}

/**
 * 接 `ScrollFollowPolicy` 的跟随控制器（组合期创建一次，随 `LazyListState`/密度重建）。
 *
 * 三个后台循环（都只在需要时跑）：
 *  1. 回放完成/首次布局 → 一次性跟底；
 *  2. 停跟随后每 500ms 采样重判（§3.1 字段 5）；
 *  3. 内容增长由 `LaunchedEffect(blocks)` 调 [ChatScrollFollowController.onContentGrew] 驱动
 *     （在 ChatListM5 里接）。
 */
@Composable
fun rememberChatScrollFollow(
    listState: LazyListState,
    loadingHistory: Boolean = false,
    policy: ScrollFollowPolicy = remember { ScrollFollowPolicy() },
): ChatScrollFollowController {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val controller = remember(listState, policy, density) {
        ChatScrollFollowController(listState, policy, density, scope)
    }
    LaunchedEffect(loadingHistory) { controller.onLoadHistoryChanged(loadingHistory) }
    // 首次布局/回放完成：一次性跟底（CHAT-53）。等 items 真正出现再滚，避免空表 scrollToItem 崩溃。
    LaunchedEffect(controller) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        if (controller.state.shouldStickToBottom) controller.scrollToBottom()
    }
    // 停跟随后 500ms 采样重判（§3.1 字段 5）。
    LaunchedEffect(controller.state.following, controller.state.loadingHistory) {
        while (!controller.state.following && !controller.state.loadingHistory) {
            delay(policy.sampleIntervalMs)
            controller.sampleTick()
        }
    }
    return controller
}

/**
 * 读者手势观察（**不消费事件**，只观察）：手势序列第一帧越过 touch slop → `onStart`，
 * 抬手 → `onEnd`。拖动/惯性仍归 LazyColumn（程序化滚动不经过这里）。
 */
internal fun Modifier.chatReaderGestureObserver(
    onStart: () -> Unit,
    onEnd: () -> Unit,
): Modifier = this.pointerInput(onStart, onEnd) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var started = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!started) {
                val dx = abs(change.position.x - down.position.x)
                val dy = abs(change.position.y - down.position.y)
                if (change.isConsumed || max(dx, dy) > viewConfiguration.touchSlop) {
                    started = true
                    onStart()
                }
            }
            if (!change.pressed) break
        }
        if (started) onEnd()
    }
}

/**
 * G-04 到边吸收修饰符：挂在**内滚容器的父盒**上（子 scroller 消费完后，残余位移到 `onPostScroll`）。
 *
 * 同一手势内到边 → 吞掉残余（CHAT-09，绝不把速度传给外层列表）；
 * 手势一开始就在边缘 → 交外层（CHAT-10）。
 */
internal fun Modifier.absorbsInnerEdgeScroll(
    gesture: () -> NestedScrollGesture,
): Modifier = this.nestedScroll(
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: androidx.compose.ui.geometry.Offset,
            available: androidx.compose.ui.geometry.Offset,
            source: NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            if (source != NestedScrollSource.Drag) return androidx.compose.ui.geometry.Offset.Zero
            if (available.y == 0f) return available
            val current = gesture()
            return if (NestedScrollAbsorb.absorb(current, available.y)) {
                available // 全量吸收：外层列表拿不到这段位移
            } else {
                androidx.compose.ui.geometry.Offset.Zero
            }
        }
    },
)

/**
 * chat 面板的无障碍标签 / 测试钩子（**集中常量**，UI 必须引用，单测断言 → 防"图标按钮无 desc"回归）。
 *
 * 背景（Lead 2026-11-27 真机发现）：纯图标按钮在 `uiautomator dump` 里是 `clickable=true` 而
 * `content-desc` 为空，读屏与自动化都只能靠坐标找。规格 §3.2 字段 11 规定可调名「回到底部」；
 * 这里再给一个 `testTag` 并把 `testTagsAsResourceId` 打开 → dump 里能看到
 * `resource-id="chat.to-bottom"`，不再需要猜坐标。
 *
 * 实现约束（**真机反证，勿改回**）：desc/testTag 必须与 `clickable` 落在**同一个 layout node**
 * 且用**非合并**语义（`semantics { }`，不要 `mergeDescendants = true`）。加了 mergeDescendants 后
 * Compose 会把 desc 拆到一个子 SemanticsNode，父节点 `chat.to-bottom` 仍是空 `content-desc`。
 * 真机验证（host GPU 模拟器，420dpi / 1080×2400，`uiautomator dump`）：
 * `<node class="android.widget.Button" resource-id="chat.to-bottom" content-desc="回到底部"
 *  clickable="true" focusable="true" bounds="[917,1985][1043,2111]" />`（自闭合、无子节点）。
 */
object ChatA11y {
    /** §3.2 字段 11 的规格术语：可调名「回到底部」。 */
    const val TO_BOTTOM: String = "回到底部"

    /** uiautomator / 自动化用的稳定选择器（配合 `testTagsAsResourceId` → resource-id）。 */
    const val TO_BOTTOM_TEST_TAG: String = "chat.to-bottom"

    /** 回底钮的可调名（UI 只能从这里取，单测锁死非空 + 与规格术语一致）。 */
    fun toBottomContentDescription(): String = TO_BOTTOM
}

/**
 * 回到底部圆钮（§3.2）。
 *
 * - 44×44dp，`end=16`、`bottom = composer 高 + 16dp`（由调用方算好）；
 * - **overlay 不占位**（G-01）：调用方把它放在列表之上的 Box 里；
 * - 显隐迟滞在 `ScrollFollowPolicy` 里（>160 显示 / ≤24 隐藏），本组件只演 160ms scale+opacity；
 * - 图标 ↓ 20dp、可调名 = [ChatA11y.toBottomContentDescription]（§3.2 字段 11 / G-16 无障碍）；
 *   单击回底并恢复跟随（§3.2 字段 4/5）。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun BackToBottomButtonUi(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = reducedMotion()
    val enter = if (reduced) {
        fadeIn(animationSpec = tween(1))
    } else {
        fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.8f, animationSpec = tween(160))
    }
    val exit = if (reduced) {
        fadeOut(animationSpec = tween(1))
    } else {
        fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.8f, animationSpec = tween(160))
    }
    AnimatedVisibility(visible = visible, enter = enter, exit = exit, modifier = modifier) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier
                .size(ChatListMetrics.TO_BOTTOM_SIZE_DP.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(
                    role = Role.Button,
                    onClickLabel = ChatA11y.TO_BOTTOM,
                    onClick = onClick,
                )
                // 关键：**非合并语义**（不要 mergeDescendants=true）+ 与 clickable/testTag 同一 layout node
                // → 所有语义配置折进同一个 SemanticsNode，uiautomator 的同一个 <node> 上同时有
                // clickable / content-desc / resource-id。
                // 反证（2026-11-27 真机）：加 mergeDescendants=true 会让 desc 单独变成**子节点**，
                // 父节点 `chat.to-bottom` 仍是空 content-desc（自动化假阴性）。
                .semantics {
                    role = Role.Button
                    contentDescription = ChatA11y.toBottomContentDescription()
                    // uiautomator dump 里暴露 resource-id（testTagsAsResourceId 与 testTag 同节点）。
                    testTagsAsResourceId = true
                }
                .testTag(ChatA11y.TO_BOTTOM_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            M5Icon(
                icon = M5Icons.chevronDown,
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant,
                size = 20.dp,
            )
        }
    }
}
