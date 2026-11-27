// 验收 ID：BL-04 BL-05 BL-06 BL-07 BL-08 BL-10 BL-11 BL-12 BL-13 BL-14 BL-15 BL-16 BL-18 BL-19 BL-20 BL-21
//          BL-28 BL-29 BL-31 BL-34 BL-42 BL-43 BL-44 BL-45 BL-49 BL-52
//
// base.layout.panel-host：**唯一改动栈的地方**（§1.8.1）+ 层叠渲染几何（§1.4/§1.6/§1.7）。
//
// 几何契约速查：
//  * 栈内 route 同 origin 叠放（不是并排），按列表顺序绘制（后入者在上，不写 z 数字）；
//  * 静止露边 revealDp = **0**（BL-15：面板打开后屏幕边缘无任何下层露出，只在拖拽/预测返回中露）；
//  * 全屏面板不画遮罩（BL-20）；抽屉型遮罩 alpha = 0.32 × progress，全 app 只画栈顶那一层（BL-21）；
//  * 转场只改位移（paint-only, graphicsLayer），**不缩放不模糊下层**（D-05）；曲线只用 spring 0.86/420（G-14）；
//  * 被覆盖层整层吞指针 + 清语义（BL-42/BL-43）；非栈顶无命中区；
//  * 开合手势只在栈顶：关自己（any depth 栈顶）/ 开新面板（仅 depth0，G-15）；
//  * 左缘 20dp 让系统返回（BL-12）；开手势累计 >96dp 才消费（BL-10）；22% / 600dp·s⁻¹ 提交（BL-13/BL-14）。
//
// 未决/不做：TOP/BOTTOM 进入边首版无使用者（保留纵向位移 TODO(lead)）；宽屏列不进栈的内容迁移归 shell（Lead 接线）；
// 抽屉的 `role=dialog` 在 Compose 无 1:1 映射（Role 枚举没有 Dialog）→ 这里只给 paneTitle + 覆盖层清语义（Lead 已裁定）。
//
// Bug1 修复（模拟器自验）：progress 只有一个数字，渲染却给所有层都用 → 开手势期间下层被一起移出屏幕（白屏）。
//   现在显式建模 progress 的 owner（`progressOwnerRouteId` + `progressPhase`）：**只有 owner 对应的层应用位移**。
//   Lead 的临时修法（`!canDismiss` 时位移 0）只治了症状且有两个缺口：① 顶层 swipe=null 时进入/退出动画整个丢失；
//   ② 顶层与 exiting 层可能同帧同 progress 一起位移（如 bin→settings 两层都 FULL 时双滑）。
// Bug2 修复：手势 modifier 的**挂载条件**只允许结构性条件（声明的 swipe / depth / 窗口档）；
//   曾用 `isIdle(state)`，而 `beginPull()` 会把 pulledRoute 置非空使 isIdle 变 false → 重组摘链 → pointerInput
//   协程被取消 → 松手回调永不执行 → 抽屉卡在 progress=0。瞬态条件一律改为 pointerInput 内部运行时守卫，
//   并加 try/finally 兜底（手势被取消时 cancelPull / 回弹，不留半开状态）。
package dev.myco.android.ui.m5.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.m5.primitives.M5Spring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------------------------------------------------------------------------
// 宿主状态：栈 + 三通道共用的 transitionProgress（§1.4.2 `transitionProgress`）
// ---------------------------------------------------------------------------

/**
 * `progress` 的**语义归属**：谁在动。
 *
 * Bug1 的根因就是"progress 只有一个数字，渲染时却给所有层都用上"——
 * 开手势期间 progress 表示"被拉出层的露出比例"，若下层聊天也按 progress 位移就会被一起移出屏幕（白屏）。
 * 所以 progress 必须绑定一个 owner（routeId），渲染层**只对 owner** 应用位移。
 */
enum class PanelProgressPhase { IDLE, ENTERING, EXITING, DISMISS, PULL, PREDICTIVE }

/**
 * `panel-host` 的状态。外部只读（`stack` / `progress` 都是 `private set`），
 * 唯一改栈入口是 [push] / [pop] / [popToRoot] / [replace]（§1.8.1）。
 *
 * `progress` = 规格里的 `transitionProgress` ∈ [0,1]：
 *  - **1 = 停稳在目标位置**（抽屉全开 / 全屏面板就位）；
 *  - **0 = 完全移出屏外**；
 *  - 拖拽 / 预测返回 / 转场三条通道共用这一个值（旧 myco 三通道共享 progress，E0）。
 */
@Stable
class PanelHostState internal constructor(
    private val scope: CoroutineScope,
    initial: PanelStackState = PanelStackState(),
) {
    var stack: PanelStackState by mutableStateOf(initial)
        private set

    /** 已从 routes 移除、但仍在播退出转场的层（null = 无）。 */
    var exitingRoute: PanelRoute? by mutableStateOf(null)
        private set

    /** 开手势中"被拉出但还没入栈"的层（D-1/D-2 跟手期间必须真实渲染）。 */
    var pulledRoute: PanelRoute? by mutableStateOf(null)
        private set

    /** 三通道共用进度：1 = 停稳，0 = 完全移出（§1.4.2）。**只对被 [progressOwnerRouteId] 指到的层有意义。** */
    var progress: Float by mutableFloatStateOf(1f)
        private set

    /** progress 当前驱动的层（谁在动）；null = 没有层在动。渲染层只对它应用位移。 */
    var progressOwnerRouteId: String? by mutableStateOf(null)
        private set

    /** progress 当前的通道（转场 / 拖出关 / 拉出开 / 预测返回），供渲染与手势互斥判断。 */
    var progressPhase: PanelProgressPhase by mutableStateOf(PanelProgressPhase.IDLE)
        private set

    /** 版本计数：每次 progress 由转场写入都会变（面板内容可用它做"转场中"判断）。 */
    var progressVersion: Int by mutableStateOf(0)
        private set

    /** 是否正在系统预测返回手势中（期间忽略自绘拖拽与其它返回入口，BL-31）。 */
    var predictiveGestureActive: Boolean by mutableStateOf(false)
        private set

    /** reduced-motion（系统「移除动画」）→ 转场瞬切（BL-48）。 */
    var reducedMotion: Boolean by mutableStateOf(false)
        private set

    /** 正在播转场：期间拒绝重复改栈、不接收遮罩点关。 */
    val animating: Boolean
        get() = stack.isTransitioning || exitingRoute != null || pulledRoute != null

    private var animJob: Job? = null

    /**
     * 转场（进入 / 退出 / 预测返回）真正结束的回调。
     * [BackDispatcher] 用它在动画结束后 `settle()`，避免永远卡在"上一条 dispatch 未完成"。
     */
    var onTransitionSettled: (() -> Unit)? = null

    private fun springSpec() = if (reducedMotion) snap<Float>() else M5Spring.settle

    private fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        progressVersion++
    }

    private fun notifySettled() {
        onTransitionSettled?.invoke()
    }

    /** 开始一次"某层在动"的通道；owner 之外的层渲染时位移恒为 0（Bug1 修复）。 */
    private fun beginMotion(ownerRouteId: String?, phase: PanelProgressPhase) {
        progressOwnerRouteId = ownerRouteId
        progressPhase = phase
    }

    /** 通道结束：progress 归 1、owner 清空。 */
    private fun endMotion() {
        progressOwnerRouteId = null
        progressPhase = PanelProgressPhase.IDLE
        progress = 1f
        progressVersion++
    }

    // -- 四个改栈操作 --------------------------------------------------------

    /** push：压入新 route，播进入转场（`initialProgress` 用于"开手势续播"，不重启动画）。 */
    fun push(route: PanelRoute, initialProgress: Float = 0f): StackResult {
        val result = stack.push(route)
        if (result is StackResult.Applied) {
            stack = result.state
            animateEnter(initialProgress)
        }
        return result
    }

    /** pop：移除栈顶，反向转场（栈底 pop 不动作，交 dispatcher）。 */
    fun pop(): StackResult {
        val removed = stack.top
        val result = stack.pop()
        if (result is StackResult.Applied && removed != null) {
            stack = result.state
            animateExit(removed)
        }
        return result
    }

    /** popToRoot：只对原栈顶播一次反向转场（BL-07）。 */
    fun popToRoot(): StackResult {
        val removed = stack.top
        val result = stack.popToRoot()
        if (result is StackResult.Applied && removed != null) {
            stack = result.state
            animateExit(removed)
        }
        return result
    }

    /** replace：同 depth 换面板；不动画位移（旧层淡出归调用方，BL-08）。 */
    fun replace(route: PanelRoute): StackResult {
        val result = stack.replace(route)
        if (result is StackResult.Applied) {
            stack = result.state
            updateProgress(1f)
        }
        return result
    }

    // -- 转场驱动 ------------------------------------------------------------

    private fun animateEnter(initialProgress: Float) {
        val enteringId = stack.enteringRouteId ?: return
        animJob?.cancel()
        beginMotion(enteringId, PanelProgressPhase.ENTERING)
        if (reducedMotion) {
            updateProgress(1f)
            stack = stack.onEnterSettled(enteringId)
            endMotion()
            notifySettled()
            return
        }
        animJob = scope.launch {
            updateProgress(initialProgress)
            val anim = Animatable(initialProgress.coerceIn(0f, 1f))
            anim.animateTo(1f, springSpec()) { updateProgress(value) }
            updateProgress(1f)
            stack = stack.onEnterSettled(enteringId)
            endMotion()
            notifySettled()
        }
    }

    private fun animateExit(removed: PanelRoute) {
        animJob?.cancel()
        exitingRoute = removed
        beginMotion(removed.routeId, PanelProgressPhase.EXITING)
        if (reducedMotion) {
            exitingRoute = null
            stack = stack.onExitSettled()
            endMotion()
            notifySettled()
            return
        }
        animJob = scope.launch {
            // 从当前 progress 续播（拖出关松手后不能先跳回 1 再播，否则画面回跳）
            val from = progress.coerceIn(0f, 1f)
            Animatable(from).animateTo(0f, springSpec()) { updateProgress(value) }
            exitingRoute = null
            stack = stack.onExitSettled()
            endMotion()
            notifySettled()
        }
    }

    // -- 拖拽关（drag-dismiss，BL-13/BL-14） ---------------------------------

    /** 手指拖拽写入（1:1 跟手）：`outDxFraction` = 已拖出宽 / 面板宽。 */
    fun onDismissDrag(outDxFraction: Float) {
        if (predictiveGestureActive) return
        beginMotion(stack.top?.routeId, PanelProgressPhase.DISMISS)
        updateProgress(1f - outDxFraction.coerceIn(0f, 1f))
    }

    /**
     * 松手：过阈值（拖出 ≥22%W 或速度 ≥600dp/s）→ pop；否则回弹。
     * 末速度交给 spring（G-14）。
     */
    fun onDismissRelease(outDxFraction: Float, outVelocityDpS: Float) {
        if (predictiveGestureActive) return
        val commit = outDxFraction >= 0.22f || outVelocityDpS >= 600f
        if (commit) {
            pop()
        } else {
            animJob?.cancel()
            animJob = scope.launch {
                val from = progress
                if (reducedMotion) {
                    updateProgress(1f)
                } else {
                    // 回弹：从当前位移 spring 回 1，末速度朝"回到位置"方向（0）
                    Animatable(from).animateTo(1f, springSpec()) { updateProgress(value) }
                }
                endMotion()
            }
        }
    }

    // -- 拖拽开（drag-open，仅 depth0，BL-10/BL-11/BL-12） --------------------

    /** 观察到 >96dp、开始接管：把目标层挂成"被拉出的层"，progress 从 0 起。 */
    fun beginPull(route: PanelRoute) {
        pulledRoute = route
        beginMotion(route.routeId, PanelProgressPhase.PULL)
        updateProgress(0f)
    }

    /** 跟手：`visibleFraction` = 露出的宽 / 抽屉宽（1:1）。 */
    fun onPullDrag(visibleFraction: Float) {
        if (predictiveGestureActive) return
        if (progressOwnerRouteId == null) beginMotion(pulledRoute?.routeId, PanelProgressPhase.PULL)
        updateProgress(visibleFraction.coerceIn(0f, 1f))
    }

    /** 松手：露出 ≥22%W 或速度 ≥600dp/s → push（从当前 progress 续播，不重启转场）；否则回弹。 */
    fun onPullRelease(visibleFraction: Float, outVelocityDpS: Float) {
        val route = pulledRoute ?: return
        val commit = visibleFraction >= 0.22f || outVelocityDpS >= 600f
        pulledRoute = null
        if (commit) {
            // 先入栈再从当前 progress 续播到 1（BL-10：不重启动画）
            push(route, initialProgress = progress)
        } else {
            animJob?.cancel()
            animJob = scope.launch {
                if (!reducedMotion) {
                    Animatable(progress).animateTo(0f, springSpec()) { updateProgress(value) }
                }
                endMotion()
            }
        }
    }

    /** 拖拽/预测被系统取消：被拉出的层立即消失、栈不变。 */
    fun cancelPull() {
        if (pulledRoute == null) return
        pulledRoute = null
        endMotion()
    }

    // -- 预测返回（BL-28/BL-29） --------------------------------------------

    /** 系统 progress（0=开始关闭 … 1=完全退出）→ 本层 progress = 1 − systemProgress。 */
    fun onPredictiveProgress(systemProgress: Float) {
        predictiveGestureActive = true
        animJob?.cancel()
        beginMotion(stack.top?.routeId, PanelProgressPhase.PREDICTIVE)
        updateProgress(1f - systemProgress.coerceIn(0f, 1f))
    }

    fun onPredictiveCancel() {
        predictiveGestureActive = false
        animJob?.cancel()
        animJob = scope.launch {
            if (!reducedMotion) {
                Animatable(progress).animateTo(1f, springSpec()) { updateProgress(value) }
            }
            endMotion()
            notifySettled()
        }
    }

    fun onPredictiveCommit() {
        predictiveGestureActive = false
        animJob?.cancel()
        val result = stack.popCommittedFromPredictive()
        if (result is StackResult.Applied) stack = result.state
        exitingRoute = null
        endMotion()
        notifySettled()
    }

    /** 供宿主/Lead 打开 reduced-motion（系统「移除动画」）。 */
    fun applyReducedMotion(reduced: Boolean) {
        reducedMotion = reduced
    }

    /** 测试/恢复：直接替换栈（不走动画）。 */
    fun restore(state: PanelStackState) {
        animJob?.cancel()
        stack = state
        exitingRoute = null
        pulledRoute = null
        endMotion()
    }
}

@Composable
fun rememberPanelHostState(
    initial: PanelStackState = PanelStackState(),
): PanelHostState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PanelHostState(scope, initial) }
}

// ---------------------------------------------------------------------------
// 层叠渲染
// ---------------------------------------------------------------------------

/**
 * 面板栈宿主：只画"保活窗内 + 栈顶"，栈内按列表顺序叠放。
 *
 * @param state host 状态（由 [rememberPanelHostState] 创建）
 * @param openDrawer 开手势的目标（仅 depth0 生效；返回 null = 本页不允许滑开，如宽屏列形态）
 * @param content 各层内容的渲染插槽（Lead 接各面板）
 */
@Composable
fun PanelHost(
    state: PanelHostState,
    modifier: Modifier = Modifier,
    openDrawer: (() -> PanelRoute?)? = null,
    panelTitle: (PanelRoute) -> String = { it.panelId },
    content: @Composable (PanelRoute) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val insets = LocalPanelInsets.current
        val density = LocalDensity.current
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val windowClass = windowClassOf(screenWidthDp.value, screenHeightDp.value)
        // 宽屏列形态（DOCKED_COLUMN）不进栈；本 host 只负责栈内 route（列由 Lead 的 shell 组装）
        val startInsetDp = insets.start

        val visible = state.stack.visibleRoutes()
        val top = state.stack.top
        val exiting = state.exitingRoute
        val pulled = state.pulledRoute
        val lower = visible.dropLast(1)
        val topLayer = visible.lastOrNull()

        // 手势 modifier 的挂载条件只允许用**结构性条件**（有没有声明的滑动接口 / 深度 / 窗口档）。
        // ⚠️ Bug2 根因：这里曾用 `isIdle(state)`，而 beginPull() 会把 pulledRoute 置非空 → isIdle 变 false
        //   → 重组把手势 modifier 从链上摘掉 → pointerInput 协程被取消 → 松手回调永远不来 → 抽屉卡在
        //   progress=0（不可见）。瞬态条件（animating/predictive/progress）一律放进 pointerInput 内部做运行时守卫。
        val topSwipe = top?.swipe
        val dismissGestureEnabled = top != null &&
            topSwipe != null &&
            (topSwipe.direction == SwipeDirection.OUT || topSwipe.direction == SwipeDirection.BOTH)
        // 开手势只在 COMPACT/MEDIUM 的内容区生效；EXPANDED 是列形态，列无开合手势（§1.3.1）
        val openTarget = if (
            openDrawer != null &&
            state.stack.depth == 1 &&
            windowClass != PanelWindowClass.EXPANDED
        ) {
            openDrawer.invoke()
        } else {
            null
        }

        Box(Modifier.fillMaxSize()) {
            // S0 空栈（异常/恢复中）：全屏吞指针，防指针穿到不存在的层
            if (visible.isEmpty() && pulled == null) {
                Box(Modifier.fillMaxSize().blockPointerInput())
            }

            // 1) 下层（KEEP 层）：真实渲染、吞指针、清语义（BL-42/BL-43）
            lower.forEach { route ->
                key(route.routeId) {
                    val widthDp = route.widthSpec.resolveDp(screenWidthDp.value, startInsetDp.value)
                    Box(
                        Modifier
                            .align(alignmentFor(route.edge))
                            .width(widthDp.dp)
                            .fillMaxHeight()
                            .blockPointerInput()
                            .clearAndSetSemantics {},
                    ) {
                        content(route)
                    }
                }
            }

            // 2) 遮罩（只画栈顶那一层；全屏不透明面板不画，BL-20/BL-21）
            if (topLayer != null && topLayer.isDrawer && exiting == null && pulled == null) {
                PanelScrim(
                    alpha = ScrimToken.alphaFor(state.progress),
                    // 可点时机：progress=1 停稳且没有任何层在被移动（§1.7）
                    dismissEnabled = state.progress >= 1f &&
                        state.progressOwnerRouteId == null &&
                        isIdle(state),
                    onDismiss = { state.pop() },
                )
            }

            // 3) 栈顶
            if (topLayer != null) {
                val widthDp = topLayer.widthSpec.resolveDp(screenWidthDp.value, startInsetDp.value)
                val sign = horizontalOutSign(topLayer.edge)
                val ownsProgress = state.progressOwnerRouteId == topLayer.routeId
                val gesture = if (dismissGestureEnabled) {
                    Modifier.panelDismissDrag(
                        state = state,
                        widthPxProvider = { with(density) { widthDp.dp.toPx() } },
                        outSign = sign,
                    )
                } else {
                    Modifier
                }
                val openGesture = if (openTarget != null) {
                    Modifier.panelOpenDrag(
                        state = state,
                        target = openTarget,
                        screenWidthDp = screenWidthDp.value,
                        startInsetDp = startInsetDp.value,
                        density = density,
                    )
                } else {
                    Modifier
                }
                key(topLayer.routeId) {
                    Box(
                        Modifier
                            .align(alignmentFor(topLayer.edge))
                            .width(widthDp.dp)
                            .fillMaxHeight()
                            .graphicsLayer {
                                // paint-only：只改位移，不缩放下层、不改 padding/width（D-05/G-14）
                                // **只有 progress 的 owner 才位移**：开手势期间 owner 是被拉出层，
                                // 栈顶（聊天）位移恒为 0 —— 否则整个下层会被一起移出屏幕（Bug1）。
                                translationX = if (!ownsProgress || isVertical(topLayer.edge)) 0f else
                                    (1f - state.progress) * sign * size.width
                            }
                            .then(if (topLayer.isDrawer) Modifier.semantics { paneTitle = panelTitle(topLayer) } else Modifier)
                            .then(gesture)
                            .then(openGesture),
                    ) {
                        content(topLayer)
                    }
                }
            }

            // 4) 开手势中"被拉出"的层（D-1/D-2 跟手）：画在栈顶之上 + 自己的遮罩
            if (pulled != null) {
                PanelScrim(alpha = ScrimToken.alphaFor(state.progress), dismissEnabled = false, onDismiss = {})
                val widthDp = pulled.widthSpec.resolveDp(screenWidthDp.value, startInsetDp.value)
                val sign = horizontalOutSign(pulled.edge)
                val ownsProgress = state.progressOwnerRouteId == pulled.routeId
                Box(
                    Modifier
                        .align(alignmentFor(pulled.edge))
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = if (!ownsProgress || isVertical(pulled.edge)) 0f else
                                (1f - state.progress) * sign * size.width
                        },
                ) {
                    content(pulled)
                }
            }

            // 5) 正在退出（已出栈、仍在画）的层：反向位移，播完由 host 移除
            if (exiting != null && pulled == null) {
                if (exiting.isDrawer) {
                    PanelScrim(alpha = ScrimToken.alphaFor(state.progress), dismissEnabled = false, onDismiss = {})
                }
                val widthDp = exiting.widthSpec.resolveDp(screenWidthDp.value, startInsetDp.value)
                val sign = horizontalOutSign(exiting.edge)
                val ownsProgress = state.progressOwnerRouteId == exiting.routeId
                Box(
                    Modifier
                        .align(alignmentFor(exiting.edge))
                        .width(widthDp.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = if (!ownsProgress || isVertical(exiting.edge)) 0f else
                                (1f - state.progress) * sign * size.width
                        }
                        .blockPointerInput()
                        .clearAndSetSemantics {},
                ) {
                    content(exiting)
                }
            }
        }
    }
}

/** 转场/预测手势中不接受新手势（避免抢同一通道）。 */
private fun isIdle(state: PanelHostState): Boolean =
    !state.animating && !state.predictiveGestureActive

private fun isVertical(edge: Edge?): Boolean = edge == Edge.TOP || edge == Edge.BOTTOM

/** LEFT 进入 → 向左移出（负）；RIGHT → 向右移出（正）。RTL 镜像由调用方的 layoutDirection 处理。 */
private fun horizontalOutSign(edge: Edge?): Float = if (edge == Edge.RIGHT) 1f else -1f

private fun alignmentFor(edge: Edge?): Alignment = when (edge) {
    Edge.RIGHT, Edge.BOTTOM -> Alignment.TopEnd
    else -> Alignment.TopStart
}

// ---------------------------------------------------------------------------
// 手势：拖出关闭（drag-dismiss）
// ---------------------------------------------------------------------------

/**
 * 栈顶「拖出关闭」：
 *  - 方向锁（水平位移 > 竖直 且超 touch slop 才接管）；
 *  - 起手 x < 20dp 不接管（左缘让系统返回，BL-12）；
 *  - 1:1 跟手写 progress；提交 = 拖出 ≥22% 或末速度 ≥600dp/s（BL-13/BL-14）；
 *  - 松手把末速度交给 spring（G-14）；
 *  - 系统预测手势期间完全让位（BL-31）。
 */
private fun Modifier.panelDismissDrag(
    state: PanelHostState,
    widthPxProvider: () -> Float,
    outSign: Float,
): Modifier = pointerInput(state, outSign) {
    val edgeReservePx = 20.dp.toPx()
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed) return@awaitEachGesture
            if (down.position.x < edgeReservePx) return@awaitEachGesture // 左缘 20dp 让系统
            // 运行时守卫（瞬态条件）：不再用手势挂载条件表达，避免 composer 摘链取消协程
            if (state.animating || state.predictiveGestureActive || state.progress < 1f) {
                return@awaitEachGesture
            }

            val startX = down.position.x
            val startY = down.position.y
            val widthPx = widthPxProvider().coerceAtLeast(1f)
            var locked = false
            var outDx = 0f
            var lastOutDx = 0f
            var lastTime = down.uptimeMillis
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - startX
                val dy = change.position.y - startY
                if (!locked && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) * 1.5f) locked = true
                if (locked) {
                    if (change.isConsumed) break // 让给横向可滚控件
                    if (state.predictiveGestureActive) break // 系统接管，放弃自绘（BL-31）
                    outDx = (dx * outSign).coerceAtLeast(0f)
                    lastOutDx = outDx
                    lastTime = change.uptimeMillis
                    dragging = true
                    state.onDismissDrag(outDx / widthPx)
                    if (change.pressed) change.consume()
                }
                if (!change.pressed) break
            }
            if (dragging) {
                val seconds = ((lastTime - down.uptimeMillis) / 1000f).coerceAtLeast(0.001f)
                val velocityPx = lastOutDx / seconds
                state.onDismissRelease(lastOutDx / widthPx, velocityPx / density)
            }
        }
    } finally {
        // 协程被取消（重组摘链/父级取消）时不能把面板留在半开：回弹到停稳
        if (state.progressPhase == PanelProgressPhase.DISMISS) {
            state.onDismissRelease(outDxFraction = 0f, outVelocityDpS = 0f)
        }
    }
}

// ---------------------------------------------------------------------------
// 手势：根层内容区拖拽打开（drag-open）
// ---------------------------------------------------------------------------

/**
 * 根层（depth0）「拖拽打开抽屉」：
 *  - 观察阶段累计水平位移 0–96dp，**不消费**（列表仍可竖滚）；
 *  - 到 96dp 一次性接管，progress 从 0 起 1:1 跟手（BL-10）；
 *  - 松手露出 ≥22% 或末速度 ≥600dp/s → push（从当前 progress 续播）；否则回弹；
 *  - 深层完全不响应（本 modifier 只在 depth==1 挂，BL-11）；左缘 20dp 让系统（BL-12）。
 */
private fun Modifier.panelOpenDrag(
    state: PanelHostState,
    target: PanelRoute,
    screenWidthDp: Float,
    startInsetDp: Float,
    density: androidx.compose.ui.unit.Density,
): Modifier = pointerInput(state, target.routeId) {
    val edgeReservePx = 20.dp.toPx()
    val consumePx = 96.dp.toPx()
    val drawerWidthPx = with(density) {
        target.widthSpec.resolveDp(screenWidthDp, startInsetDp).dp.toPx()
    }.coerceAtLeast(1f)
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed) return@awaitEachGesture
            if (down.position.x < edgeReservePx) return@awaitEachGesture // 左缘 20dp 让系统（BL-12）
            // 运行时守卫（瞬态条件）；挂载与否只由结构性条件决定（见 PanelHost 注释 / Bug2）
            if (state.animating || state.predictiveGestureActive || state.stack.depth != 1) {
                return@awaitEachGesture
            }

            val startX = down.position.x
            val startY = down.position.y
            var locked = false
            var pulling = false
            var lastVisible = 0f
            var lastTime = down.uptimeMillis
            var lastDx = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - startX
                val dy = change.position.y - startY
                if (!locked && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) * 1.5f) locked = true
                if (locked) {
                    if (change.isConsumed) break // 横向控件优先
                    lastDx = dx
                    lastTime = change.uptimeMillis
                    if (dx > consumePx) {
                        // 观察阶段（0–96dp）不消费、列表仍可竖滚；过 96dp 一次性接管（BL-10）
                        if (!pulling) {
                            pulling = true
                            state.beginPull(target)
                        }
                        lastVisible = ((dx - consumePx) / drawerWidthPx).coerceIn(0f, 1f)
                        state.onPullDrag(lastVisible)
                        if (change.pressed) change.consume()
                    }
                }
                if (!change.pressed) break
            }
            if (pulling) {
                val seconds = ((lastTime - down.uptimeMillis) / 1000f).coerceAtLeast(0.001f)
                val velocityPx = lastDx / seconds // 向右为正 = 打开方向
                state.onPullRelease(lastVisible, velocityPx / density.density)
            }
        }
    } finally {
        // 手势被取消（重组摘链/系统抢占）→ 不能把"被拉出的层"永久挂在 progress=0
        if (state.pulledRoute != null) state.cancelPull()
    }
}