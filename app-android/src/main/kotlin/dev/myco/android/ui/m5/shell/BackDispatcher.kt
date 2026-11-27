// 验收 ID：BL-22 BL-23 BL-24 BL-25 BL-26 BL-27 BL-28 BL-29 BL-30 BL-31 BL-79
//
// base.layout.back.dispatcher：**单一**返回 dispatcher（§1.6 `back.dispatcher` 字段 5 的优先级表）。
// 全 app 只有一个 dispatcher；返回键 / 系统返回手势 / route.back / 预测返回 invoked 四个来源都进这里。
//
// 优先级（写死为 [BackDispatcher.priorityTable]，单测断言顺序）：
//   0 防重入 → 1 IME → 2 栈顶层浮层 → 3 栈顶 route → 4 栈底 ROOT（交系统）
//
// 登记协议（字段 5）：
//   * `base.primitives` 浮层管理器在 show() 时 [BackDispatcher.registerOverlay]、dismiss() 时 [BackDispatcher.unregisterOverlay]；
//   * `panel-host` 在栈顶变化时 [BackDispatcher.registerRoute]、失顶/销毁时 [BackDispatcher.unregisterRoute]；
//   * shell 常驻 [BackDispatcher.registerRoot]；
//   * IME **不进表**：每次 dispatch 实时读 `BackDispatchInput.imeVisible`。
//
// 未决（见回复「待 Lead 裁决」）：面板内浮层当前用独立 window（Dialog）承载 → 平台返回由 Dialog 自己吃掉，
// dispatcher 的第二级只在"浮层以应用内层渲染"时才被系统返回命中。要严格单一入口需把浮层改回应用内宿主，
// 本文件先把优先级与登记协议写对，接线口径留 TODO(lead)。
package dev.myco.android.ui.m5.shell

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException

// ---------------------------------------------------------------------------
// 数据
// ---------------------------------------------------------------------------

/** 浮层家族（§1.6 z 阶梯：modal/sheet/lightbox=1000，menu=1100，toast=overlay 20）。 */
enum class OverlayFamily { MODAL, BOTTOM_SHEET, MENU, LIGHTBOX, TOAST }

/** dispatcher 的层级（order 即优先级，小的先被尝试）。 */
enum class BackStage(val order: Int) {
    /** 0：防重入（预测手势进行中 / 上一条 dispatch 未完成）。 */
    REENTRANT(0),

    /** 1：IME（收键盘，不 pop；BT-22）。 */
    IME(1),

    /** 2：栈顶层的面板内浮层（BT-23）。 */
    OVERLAY(2),

    /** 3：栈顶 route（BT-24）。 */
    ROUTE(3),

    /** 4：栈底（BT-25）。 */
    ROOT(4),
}

/** 已登记浮层。token 单调递增 = 打开顺序（同一层多个浮层按打开逆序关闭）。 */
data class OverlayEntry(
    val token: Long,
    val ownerRouteId: String,
    val family: OverlayFamily,
    val label: String = "",
)

/** 每次 dispatch 的实时输入（不缓存 IME 状态：规格要求 IME 不进表、实时读）。 */
data class BackDispatchInput(
    val imeVisible: Boolean = false,
    val topRouteId: String? = null,
    val stackDepth: Int = 0,
    val topReturnMode: ReturnMode = ReturnMode.POP,
    val topRouteExiting: Boolean = false,
    val topRouteBackPolicy: BackDepthPolicy = BackDepthPolicy.CONSUME_WHEN_VISIBLE,
)

/** dispatcher 决定"谁来消费 + 消费什么"。 */
sealed interface BackAction {
    /** 防重入：什么都不做（`consumed=true`，避免系统在转场中把 app 退出）。 */
    data object Ignore : BackAction

    /** 收起 IME，**不 pop**（BL-22）。 */
    data object HideIme : BackAction

    /** 关掉该浮层（BL-23）。 */
    data class DismissOverlay(val entry: OverlayEntry) : BackAction

    /** pop 栈顶 route（BL-24）。 */
    data class PopRoute(val routeId: String) : BackAction

    /** 把这次返回交给系统预测手势（returnMode=HAND_TO_GESTURE）。 */
    data object HandToPredictiveGesture : BackAction

    /** 回根（栈深 >1 且栈顶不消费时；BL-25）。 */
    data object PopToRoot : BackAction

    /** 交系统（栈底）→ 系统退出 app；**不消费**。 */
    data object NotConsumed : BackAction
}

data class BackDispatchResult(
    val stage: BackStage,
    val action: BackAction,
    val consumed: Boolean,
    val note: String = "",
)

// ---------------------------------------------------------------------------
// dispatcher
// ---------------------------------------------------------------------------

/**
 * 单一返回 dispatcher。
 *
 * 线程/时序契约：动作是**同步发起**的（pop / dismiss 的回调在 dispatch 内被调用），
 * 动画或异步注销（浮层关闭动画、IME 收起）由调用方在完成后调 [settle]；
 * 未 settle 前的 dispatch 走 [BackStage.REENTRANT] 被忽略（字段 4"上一条 dispatch 未完成"）。
 */
class BackDispatcher {

    /** 优先级表（只读；单测断言顺序）。 */
    val priorityTable: List<BackStage> = listOf(
        BackStage.REENTRANT,
        BackStage.IME,
        BackStage.OVERLAY,
        BackStage.ROUTE,
        BackStage.ROOT,
    )

    private val overlayEntries = LinkedHashMap<Long, OverlayEntry>()
    private val overlayDismissers = LinkedHashMap<Long, () -> Unit>()
    private val routeHandlers = LinkedHashMap<String, () -> Unit>()
    private var rootHandler: (() -> Unit)? = null
    private var nextToken = 1L

    /** 上一条 dispatch 是否还在完成中（防重入）。 */
    var inFlight: Boolean = false
        private set

    /** 累计 dispatch 次数（诊断/测试用）。 */
    var dispatchCount: Int = 0
        private set

    // -- 登记协议 ------------------------------------------------------------

    /**
     * 浮层 show() 时登记。[onDismiss] 由浮层管理器提供（应播放关闭动画并最终 [unregisterOverlay]）。
     * 返回 token；同一 ownerRouteId 可登记多个（后登记者在上，按打开逆序关）。
     */
    fun registerOverlay(
        ownerRouteId: String,
        family: OverlayFamily,
        label: String = "",
        onDismiss: () -> Unit = {},
    ): OverlayEntry {
        val entry = OverlayEntry(nextToken++, ownerRouteId, family, label)
        overlayEntries[entry.token] = entry
        overlayDismissers[entry.token] = onDismiss
        return entry
    }

    /** 浮层 dismiss() 时注销（dev：漏注销 → [assertNoOrphanOverlays] 会抓）。 */
    fun unregisterOverlay(token: Long) {
        overlayEntries.remove(token)
        overlayDismissers.remove(token)
        // 注销 = 浮层关闭完成 → 解除防重入（"浮层关闭动画中再按 → 忽略直到注销完成"，字段 4）
        if (inFlight) inFlight = false
    }

    /** push 前置：同帧无动画关闭并注销该层全部浮层（BL-27）。返回被清掉的条数。 */
    fun clearOverlaysFor(ownerRouteId: String): Int {
        val tokens = overlayEntries.values.filter { it.ownerRouteId == ownerRouteId }.map { it.token }
        tokens.forEach { unregisterOverlay(it) }
        return tokens.size
    }

    /** panel-host 在栈顶变化时登记"pop 这一层"的处理者。 */
    fun registerRoute(routeId: String, onPop: () -> Unit) {
        routeHandlers[routeId] = onPop
    }

    fun unregisterRoute(routeId: String) {
        routeHandlers.remove(routeId)
    }

    /** shell 常驻 ROOT 处理者（回根）。 */
    fun registerRoot(onRoot: () -> Unit) {
        rootHandler = onRoot
    }

    fun unregisterRoot() {
        rootHandler = null
    }

    // -- 查询 ----------------------------------------------------------------

    /** 该层最上面的浮层（没有 → null）。被覆盖层的浮层不允许存在，所以只查栈顶层。 */
    fun topOverlayFor(ownerRouteId: String?): OverlayEntry? {
        if (ownerRouteId == null) return null
        return overlayEntries.values.lastOrNull { it.ownerRouteId == ownerRouteId }
    }

    fun overlayCount(): Int = overlayEntries.size

    /** 当前是否有任何已登记浮层（栈顶层判断用）。 */
    fun hasOverlay(): Boolean = overlayEntries.isNotEmpty()

    /** 所有已登记浮层（诊断）。 */
    fun registeredOverlays(): List<OverlayEntry> = overlayEntries.values.toList()

    /**
     * 对"当前存活层集合"做一次泄漏审计：登记了浮层但所属层已不在栈里 →
     * dev 断言失败（BL-26 字段 5），release 只记录。返回孤儿浮层。
     */
    fun orphanOverlays(liveRouteIds: Set<String>): List<OverlayEntry> =
        overlayEntries.values.filter { it.ownerRouteId !in liveRouteIds }

    /** dev 断言：层销毁未注销 → 抛 [IllegalStateException]。 */
    fun assertNoOrphanOverlays(liveRouteIds: Set<String>) {
        val orphans = orphanOverlays(liveRouteIds)
        check(orphans.isEmpty()) {
            "浮层泄漏：所属层已销毁但未注销 → ${orphans.map { "${it.family}@${it.ownerRouteId}" }}"
        }
    }

    // -- dispatch ------------------------------------------------------------

    /**
     * 是否能消费这次返回（`enabled` 计算用；纯查询，不改状态）。
     * 注意：栈底且无 IME/浮层 → false（交系统退出，BL-25）。
     */
    fun canConsume(input: BackDispatchInput): Boolean = when {
        inFlight -> true // 防重入期按"消费"处理，避免转场中被系统退出
        input.imeVisible -> true
        topOverlayFor(input.topRouteId) != null -> true
        input.stackDepth > 1 -> true
        else -> false
    }

    /**
     * 唯一返回入口。**返回结果描述谁消费了什么**，并同步发起对应动作回调。
     *
     * @return `consumed=false` 才交系统（退出 app）。
     */
    fun dispatch(input: BackDispatchInput): BackDispatchResult {
        dispatchCount++

        // 0 防重入：预测手势进行中 / 上一条 dispatch 未完成
        if (inFlight) {
            return BackDispatchResult(BackStage.REENTRANT, BackAction.Ignore, consumed = true, note = "in-flight")
        }

        // 1 IME：收键盘，不 pop（BL-22）
        if (input.imeVisible) {
            inFlight = true
            return BackDispatchResult(BackStage.IME, BackAction.HideIme, consumed = true, note = "ime")
        }

        // 2 栈顶层的浮层（BL-23）：只消费栈顶层自己那一层，最上面的那条
        val overlay = topOverlayFor(input.topRouteId)
        if (overlay != null) {
            inFlight = true
            overlayDismissers[overlay.token]?.invoke()
            return BackDispatchResult(
                BackStage.OVERLAY,
                BackAction.DismissOverlay(overlay),
                consumed = true,
                note = "overlay:${overlay.family}",
            )
        }

        // 3 栈顶 route（BL-24）：depth ≥2 才轮到它（depth1 = 根，见 4）
        val routeConsumes = input.stackDepth >= 2 &&
            !input.topRouteExiting &&
            input.topRouteBackPolicy == BackDepthPolicy.CONSUME_WHEN_VISIBLE
        if (routeConsumes) {
            val routeId = input.topRouteId
            if (routeId != null) {
                if (input.topReturnMode == ReturnMode.HAND_TO_GESTURE) {
                    inFlight = true
                    return BackDispatchResult(
                        BackStage.ROUTE,
                        BackAction.HandToPredictiveGesture,
                        consumed = true,
                        note = "hand-to-gesture",
                    )
                }
                inFlight = true
                routeHandlers[routeId]?.invoke()
                return BackDispatchResult(
                    BackStage.ROUTE,
                    BackAction.PopRoute(routeId),
                    consumed = true,
                    note = "route",
                )
            }
        }

        // 4 栈底（BL-25）：不在根 → 回根；已在根 → 交系统
        if (input.stackDepth > 1) {
            inFlight = true
            rootHandler?.invoke()
            return BackDispatchResult(BackStage.ROOT, BackAction.PopToRoot, consumed = true, note = "root-pop-to-root")
        }
        return BackDispatchResult(BackStage.ROOT, BackAction.NotConsumed, consumed = false, note = "hand-to-system")
    }

    /** 动作完成（动画结束 / IME 收起 / 浮层注销）后调用，解除防重入。 */
    fun settle() {
        inFlight = false
    }

    /** 测试/重建用：清空全部登记。 */
    fun reset() {
        overlayEntries.clear()
        overlayDismissers.clear()
        routeHandlers.clear()
        rootHandler = null
        inFlight = false
        dispatchCount = 0
        nextToken = 1L
    }
}

// ---------------------------------------------------------------------------
// Compose 接线（4 个来源里"返回键 / 系统手势"这一路）
// ---------------------------------------------------------------------------

/**
 * 把 dispatcher 接到系统返回通道上：**全 app 只应存在一个**这个 Composable。
 *
 * - 13+ 且栈深 ≥2、无 IME、无浮层 → 走 [PredictiveBackHandler]（BL-28/BL-29）；
 *   手势 progress 交给宿主只写栈顶位移，commit = pop 但不再播动画，cancel = 回弹。
 * - 其余情况走 [BackHandler]（低版本 / 栈底 / 有浮层）；`enabled=false` 时完全不拦截，
 *   系统直接退出 app（BL-25 / BL-30）。
 *
 * 返回键、返回钮、预测返回 invoked 都只会走到 [BackDispatcher.dispatch] 这一条顺序。
 */
@Composable
fun PanelBackHost(
    dispatcher: BackDispatcher,
    input: () -> BackDispatchInput,
    enabled: Boolean = true,
    predictiveEnabled: Boolean = true,
    onPredictiveProgress: (Float) -> Unit = {},
    onPredictiveCancel: () -> Unit = {},
    onPredictiveCommit: () -> Unit = {},
    onActionDispatched: (BackDispatchResult) -> Unit = {},
) {
    val currentInput = rememberUpdatedState(input)
    val state = currentInput.value()
    val usePredictive = predictiveEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        enabled &&
        state.stackDepth >= 2 &&
        !state.imeVisible &&
        !state.topRouteExiting &&
        dispatcher.topOverlayFor(state.topRouteId) == null

    if (usePredictive) {
        PredictiveBackHandler(enabled = true) { progress ->
            try {
                progress.collect { event -> onPredictiveProgress(event.progress) }
                onPredictiveCommit()
            } catch (cancelled: CancellationException) {
                onPredictiveCancel()
                throw cancelled
            }
        }
    } else {
        BackHandler(enabled = enabled) {
            val result = dispatcher.dispatch(currentInput.value())
            // IME / 浮层的完成信号分别来自 imeVisible=false 与 unregisterOverlay()；
            // 其余（route/root 的 pop 动画）由宿主在转场结束后 settle。
            onActionDispatched(result)
        }
    }
}

/**
 * 把 [BackDispatcher] 与 [PanelHostState] 接起来的**唯一**入口（Lead 接线用；全 app 调一次）。
 *
 * 做的事（就是 `back.dispatcher` 字段 5 的登记协议）：
 *  1. 栈顶变化时登记/注销该层的 pop 处理者（`registerRoute` / `unregisterRoute`）；
 *  2. shell 级常驻 ROOT 处理者（`popToRoot`）；
 *  3. 把系统返回/预测返回接到 [PanelBackHost]：13+ 且栈深 ≥2 → 只写栈顶位移，commit = pop（不再播动画）。
 *
 * @param imeVisible 由 composer/shell 提供（composer 打开 = true）；IME 优先于浮层与面板（BL-22）
 */
@Composable
fun PanelBackWiring(
    state: PanelHostState,
    dispatcher: BackDispatcher,
    imeVisible: Boolean = false,
    predictiveEnabled: Boolean = true,
) {
    val top = state.stack.top
    val topId = top?.routeId

    // 1) 栈顶层登记 pop 处理者（失顶即注销，防跨层误 pop）
    DisposableEffect(dispatcher, topId) {
        if (topId != null) {
            dispatcher.registerRoute(topId) { state.pop() }
        }
        onDispose {
            if (topId != null) dispatcher.unregisterRoute(topId)
        }
    }

    // 2) ROOT 常驻：不在根 → 回根（BL-25 的"栈深>1 不退出"）
    DisposableEffect(dispatcher) {
        dispatcher.registerRoot { state.popToRoot() }
        onDispose { dispatcher.unregisterRoot() }
    }

    // 3) 转场结束 / IME 收起 → 解除 dispatcher 防重入（否则第二次返回会被永远忽略）
    DisposableEffect(dispatcher, state) {
        val previous = state.onTransitionSettled
        state.onTransitionSettled = { dispatcher.settle() }
        onDispose {
            state.onTransitionSettled = previous
            dispatcher.settle()
        }
    }
    LaunchedEffect(imeVisible) {
        if (!imeVisible) dispatcher.settle()
    }

    // 3) 系统返回通道（返回键 / 系统返回手势 / 预测返回 invoked 全走 dispatcher）
    PanelBackHost(
        dispatcher = dispatcher,
        input = {
            BackDispatchInput(
                imeVisible = imeVisible,
                topRouteId = topId,
                stackDepth = state.stack.depth,
                topReturnMode = top?.returnMode ?: ReturnMode.POP,
                topRouteExiting = state.stack.exitingRouteId != null,
                topRouteBackPolicy = top?.backDepthPolicy ?: BackDepthPolicy.CONSUME_WHEN_VISIBLE,
            )
        },
        // 栈底不拦截 → 交系统退出（BL-25）；有 IME 时拦截（先收键盘）
        enabled = state.stack.depth > 1 || imeVisible,
        predictiveEnabled = predictiveEnabled,
        onPredictiveProgress = { systemProgress -> state.onPredictiveProgress(systemProgress) },
        onPredictiveCancel = { state.onPredictiveCancel() },
        onPredictiveCommit = { state.onPredictiveCommit() },
        onActionDispatched = {
            // 没有转场可等（动作被忽略 / 瞬时完成）→ 立即 settle，避免卡在 REENTRANT
            if (!state.animating) dispatcher.settle()
        },
    )
}
