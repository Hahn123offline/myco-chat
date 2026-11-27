// 验收 ID：BL-04 BL-05 BL-06 BL-07 BL-08 BL-09 BL-32 BL-33 BL-34 BL-35 BL-36 BL-37 BL-38
//
// base.layout 的纯数据层：PanelSpec/Route 字段级模型（§1.4）+ 邻接图之外的栈 reducer（§1.8.1）
// + 保活/冻结计算（§1.8.2）+ 宽窄档（window-class，§1.3.3）+ saveable 快照（BL-09）。
//
// 设计约束（来自规格与任务书）：
//  * depth / keepAlive / cover / scrim 是**派生值**，外部只读；本文件把它们写成计算函数，
//    不让调用方写入（PanelRoute 里没有这几个字段，避免"外部写派生字段"）。
//  * 本文件**零 Compose / 零 Android 依赖**：全部判定都能在 JVM 单测里跑。
//  * 唯一改栈入口 = push / pop / popTo / popToRoot / replace（§1.8.1）。
package dev.myco.android.ui.m5.shell

// ---------------------------------------------------------------------------
// §1.4.1 枚举与取值
// ---------------------------------------------------------------------------

/** 进入边 / 拖出边（§1.4.2 `edge`）。LEFT/RIGHT 为横向，TOP/BOTTOM 首版无使用者（保留）。 */
enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

/** 面板类型（§1.4.1 `kind`）。首版只有 4 个真面板，其余走 OTHER 占位。 */
enum class PanelKind { CHAT, SESSIONS, SETTINGS, BIN, OTHER }

/** 呈现形态（§1.4.1 `presentation`）。DOCKED_COLUMN 只在 EXPANDED 生效且**不进栈**。 */
enum class PanelPresentation { ROUTE_FULL, ROUTE_DRAWER, DOCKED_COLUMN }

/** 遮罩取值（§1.4.1 `scrim`）。alpha 唯一值 0.32 是 token，见 PanelZLayout.SCRIM_ALPHA。 */
enum class ScrimMode { NONE, DIM }

/**
 * 覆盖方式（§1.4.1 `coverMode`）。`scrim` 由本枚举推导，不允许面板手写
 * （BL-20：OVER_OPAQUE 必须推导出 NONE）。
 */
enum class CoverMode(private val derivedScrim: ScrimMode) {
    OVER_OPAQUE(ScrimMode.NONE),
    OVER_DIM(ScrimMode.DIM),
    PUSH_COLUMN(ScrimMode.NONE);

    /** 缺省遮罩（§1.4.1 `scrim` 推导规则）。 */
    fun defaultScrim(): ScrimMode = derivedScrim
}

/** 覆盖区是否等于屏宽（**派生**，不可手写；§1.4.2 `cover`）。 */
enum class Cover { FULL, PARTIAL }

/** 返回语义（§1.4.1 `returnMode`）。 */
enum class ReturnMode { POP, TO_ROOT, HAND_TO_GESTURE }

/** 返回键策略（§1.4.1 `backDepthPolicy`）。首版全部 CONSUME_WHEN_VISIBLE。 */
enum class BackDepthPolicy { CONSUME_WHEN_VISIBLE, PASS_THROUGH }

/** 生效保活态（**派生**；§1.8.2）。 */
enum class KeepAlive { ACTIVE, KEEP, FROZEN }

/** 滑动接口方向（§1.4.4）。 */
enum class SwipeDirection { IN, OUT, BOTH }

/** 开手势来源（§1.4.4）。首版只有 CONTENT_DRAG；不做 SCREEN_EDGE（D-06）。 */
enum class SwipeOpenSource { CONTENT_DRAG, SCREEN_EDGE, NONE }

/** 手势允许的层（§1.4.4）。 */
enum class SwipeEnabledWhen { ROOT_ONLY, TOP_LAYER_ONLY }

/**
 * 宽度规则（§1.4.3）。
 * `MIN_MAX(minDp, frac, maxInsetDp)` 的解析式是 `min(minDp + startInset, W×frac, W−maxInsetDp)`。
 */
sealed interface WidthSpec {
    data object Full : WidthSpec

    data class MinMax(
        val minDp: Float = 320f,
        val frac: Float = 0.86f,
        val maxInsetDp: Float = 56f,
    ) : WidthSpec

    data class Fixed(val dp: Float) : WidthSpec

    /** 派生 cover：FULL → Full，其余 → PARTIAL（§1.4.2 要求不可手写）。 */
    fun cover(): Cover = when (this) {
        is Full -> Cover.FULL
        is MinMax, is Fixed -> Cover.PARTIAL
    }

    /**
     * 解析成本次实际宽度（dp）。`startInsetDp` = displayCutout 左侧 inset（§1.3.2-⑤）。
     * 结果下限 0：极窄屏 < 320dp 时 MIN_MAX 仍可算出负值，必须夹住（BL-37）。
     */
    fun resolveDp(screenWidthDp: Float, startInsetDp: Float = 0f): Float = when (this) {
        is Full -> screenWidthDp
        is MinMax -> minOf(minDp + startInsetDp, screenWidthDp * frac, screenWidthDp - maxInsetDp)
            .coerceAtLeast(0f)
        is Fixed -> dp
    }
}

/** 本次有效滑动接口（§1.4.4；数值全部是 D-06/D-07 拍板值）。 */
data class SwipeSpec(
    val edge: Edge,
    val direction: SwipeDirection = SwipeDirection.BOTH,
    val openSource: SwipeOpenSource = SwipeOpenSource.NONE,
    val enabledWhen: SwipeEnabledWhen = SwipeEnabledWhen.TOP_LAYER_ONLY,
    /** 静止露边：Q-BL-02=A / E-01 → 0（BL-15）。 */
    val revealDp: Float = 0f,
    /** 跟手系数：1:1（D-06）。 */
    val follow: Float = 1f,
    /** 提交比例：0.22 × 屏宽（D-07）。 */
    val commitRatio: Float = 0.22f,
    /** 提交速度：600 dp/s（D-07，G-14）。 */
    val commitVelocityDpS: Int = 600,
    /** 开手势"只观察不消费"的距离：96dp（D-06，G-15）。 */
    val consumeDp: Int = 96,
    /** 左缘让系统返回：20dp（D-06，G-15）。 */
    val systemEdgeReserveDp: Int = 20,
)

// ---------------------------------------------------------------------------
// §1.4.2 PanelRoute：一次打开产生的实例（derived 字段除外）
// ---------------------------------------------------------------------------

/**
 * 面板实例（§1.4.2）。
 *
 * **不包含** `cover` / `keepAlive` / `depth` / `transitionProgress` / `scrim` 这些派生字段
 * ——它们由 [PanelStackState] 与 `PanelHost` 计算，外部只读（规格：不可外部写）。
 * `scrim` 也由 [coverMode] 推导，避免"面板自己说要遮罩"。
 */
data class PanelRoute(
    val routeId: String,
    val panelId: String,
    val params: Map<String, Any> = emptyMap(),
    val from: String? = null,
    val edge: Edge? = null,
    val widthSpec: WidthSpec = WidthSpec.Full,
    val coverMode: CoverMode = CoverMode.OVER_OPAQUE,
    val returnMode: ReturnMode = ReturnMode.POP,
    val kind: PanelKind = PanelKind.OTHER,
    val presentation: PanelPresentation = PanelPresentation.ROUTE_FULL,
    val swipe: SwipeSpec? = null,
    val backDepthPolicy: BackDepthPolicy = BackDepthPolicy.CONSUME_WHEN_VISIBLE,
) {
    /** 派生的 cover（§1.4.2 口径）。 */
    val cover: Cover get() = widthSpec.cover()

    /** 派生的 scrim（§1.4.1 口径）。 */
    val scrim: ScrimMode get() = coverMode.defaultScrim()

    /** 是否抽屉型（PARTIAL + OVER_DIM）——只有它才画遮罩（BL-16/BL-20）。 */
    val isDrawer: Boolean
        get() = cover == Cover.PARTIAL && coverMode == CoverMode.OVER_DIM
}

// ---------------------------------------------------------------------------
// §1.8.1 栈状态 + 派生计算
// ---------------------------------------------------------------------------

/**
 * 栈状态。`enteringRouteId` / `exitingRouteId` 是转场协调标记（不是 route 数据）：
 *  - `enteringRouteId`：正在播进入转场的 route（ENTERING 中重复同一请求 → 忽略，BL-06）；
 *  - `exitingRouteId`：正在播退出转场的 route（EXITING 中重复 pop → 忽略）。
 */
data class PanelStackState(
    val routes: List<PanelRoute> = emptyList(),
    val enteringRouteId: String? = null,
    val exitingRouteId: String? = null,
) {
    /** 栈深（0 = 空栈异常态 S0）。 */
    val depth: Int get() = routes.size

    /** 栈顶（唯一可交互层）。 */
    val top: PanelRoute? get() = routes.lastOrNull()

    /** 只有根（或空栈）。 */
    val isAtRoot: Boolean get() = routes.size <= 1

    /** 是否正在播转场（用于手势/返回键忽略窗口）。 */
    val isTransitioning: Boolean get() = enteringRouteId != null || exitingRouteId != null

    /** 栈底 route（根）。 */
    val root: PanelRoute? get() = routes.firstOrNull()

    /**
     * §1.8.2 保活规则：
     *  - 栈顶 → ACTIVE；
     *  - 非栈顶且 `depth ≤ 1` → KEEP（组合树保留）；
     *  - 非栈顶且 `depth ≥ 2` → FROZEN（释放组合，走 rememberSaveable）；
     *  - 栈顶是 PARTIAL（抽屉）时，其下一层**强制 KEEP**（BL-34：露出的条带必须真实渲染）。
     *
     * 注意 `depth` = 该 route 在栈里的下标（根 = 0），与规格"深度 0–1 保活"一致。
     */
    fun keepAliveAt(index: Int): KeepAlive {
        if (index !in routes.indices) return KeepAlive.FROZEN
        if (index == routes.lastIndex) return KeepAlive.ACTIVE
        if (index <= 1) return KeepAlive.KEEP
        val next = routes.getOrNull(index + 1)
        val topIsPartial = next != null && next.cover == Cover.PARTIAL
        if (topIsPartial && index == routes.lastIndex - 1) return KeepAlive.KEEP
        return KeepAlive.FROZEN
    }

    /** 按 routeId 查保活态（找不到 → null）。 */
    fun keepAliveOf(routeId: String): KeepAlive? {
        val index = routes.indexOfFirst { it.routeId == routeId }
        return if (index < 0) null else keepAliveAt(index)
    }

    /**
     * 参与渲染的 route（保活窗 = 栈顶 + KEEP 层）。FROZEN 层不在组合树内（§1.8.2）。
     * 顺序 = 栈序（后入者在上），供 `PanelHost` 依序绘制。
     */
    fun visibleRoutes(): List<PanelRoute> = routes.filterIndexed { index, _ ->
        keepAliveAt(index) != KeepAlive.FROZEN
    }

    /** 需要释放 UI 组合的层（≥2 的深非栈顶层）。 */
    fun frozenRoutes(): List<PanelRoute> = routes.filterIndexed { index, _ ->
        keepAliveAt(index) == KeepAlive.FROZEN
    }

    /** 唯一画遮罩的层下标：只有栈顶且是抽屉型才画（BL-21：多层只一层）。 */
    fun scrimLayerIndex(): Int? {
        val top = top ?: return null
        return if (top.isDrawer) routes.lastIndex else null
    }

    /** 栈顶遮罩 alpha 的基准（progress=1 时）；不画时 0。 */
    fun scrimAlphaBase(): Float = if (scrimLayerIndex() != null) 0.32f else 0f
}

// ---------------------------------------------------------------------------
// §1.8.1 操作结果 / 副作用（host 依此播转场、收浮层、预组合）
// ---------------------------------------------------------------------------

/** reducer 的副作用：`PanelHost` 按此播转场 / 清浮层 / 预组合。顺序即执行顺序。 */
sealed interface StackEffect {
    /** push 前置：同帧无动画关闭并注销栈顶层的所有面板内浮层（BL-27）。 */
    data class DismissOverlays(val ownerRouteId: String) : StackEffect

    /** 播进入转场（`animate=false` = 瞬时构建，深链中间层用）。 */
    data class Enter(val routeId: String, val edge: Edge?, val animate: Boolean) : StackEffect

    /** 播退出转场（`reverse=true` 为反向 spring；预测返回 commit 时 `animate=false`）。 */
    data class Exit(val routeId: String, val animate: Boolean, val reverse: Boolean) : StackEffect

    /** 退出转场开始前预组合的新栈顶（S7：避免先白一帧）。 */
    data class Precompose(val routeId: String) : StackEffect

    /** 直接销毁（回根时的中间层：不逐层连播，BL-07）。 */
    data class Destroyed(val routeId: String) : StackEffect
}

/** 改栈结果。`Ignored` 不是错误：幂等/去重/忙碌都走它（BL-06）。 */
sealed interface StackResult {
    data class Applied(
        val state: PanelStackState,
        val effects: List<StackEffect> = emptyList(),
    ) : StackResult

    data class Ignored(val state: PanelStackState, val reason: String) : StackResult

    /** 便捷取值：两种情况都返回"结果状态"。 */
    val resultState: PanelStackState
        get() = when (this) {
            is Applied -> state
            is Ignored -> state
        }

    val applied: Boolean get() = this is Applied
}

// ---------------------------------------------------------------------------
// §1.8.1 四个操作（唯一改栈入口）
// ---------------------------------------------------------------------------

/**
 * push：压入新 route，depth = stack.size。
 *
 * 幂等/去重（BL-06）：
 *  1. `EXITING` 中 → 忽略；
 *  2. `ENTERING` 中重复同一 panelId → 忽略；
 *  3. 栈顶已是同 panelId + 同 params → 忽略（0.3s 连点 5 次只入栈 1 层）。
 *
 * 前置副作用：对**当前栈顶层**发 [StackEffect.DismissOverlays]（BL-27：浮层同帧关闭并注销）。
 */
fun PanelStackState.push(route: PanelRoute): StackResult {
    if (exitingRouteId != null) return StackResult.Ignored(this, "exiting-in-progress")
    if (enteringRouteId != null && top?.panelId == route.panelId) {
        return StackResult.Ignored(this, "duplicate-while-entering")
    }
    val currentTop = top
    if (currentTop != null && currentTop.panelId == route.panelId && currentTop.params == route.params) {
        return StackResult.Ignored(this, "duplicate-top")
    }
    val effects = buildList {
        if (currentTop != null) add(StackEffect.DismissOverlays(currentTop.routeId))
        add(StackEffect.Enter(route.routeId, route.edge, animate = true))
        if (currentTop != null) add(StackEffect.Precompose(currentTop.routeId))
    }
    return StackResult.Applied(
        state = copy(routes = routes + route, enteringRouteId = route.routeId),
        effects = effects,
    )
}

/** 进入转场播完：清 ENTERING 标记。 */
fun PanelStackState.onEnterSettled(routeId: String): PanelStackState =
    if (enteringRouteId == routeId) copy(enteringRouteId = null) else this

/** 退出转场播完：清 EXITING 标记。 */
fun PanelStackState.onExitSettled(): PanelStackState = copy(exitingRouteId = null)

/**
 * pop：移除栈顶，新栈顶 ACTIVE（FREEZE 层先恢复）。
 * 栈底 pop 不动作（交 dispatcher 下一级，BL-25）。`EXITING` 中忽略。
 */
fun PanelStackState.pop(): StackResult {
    if (exitingRouteId != null) return StackResult.Ignored(this, "exiting-in-progress")
    if (routes.size <= 1) return StackResult.Ignored(this, "at-root")
    val removed = routes.last()
    val rest = routes.dropLast(1)
    val newTop = rest.last()
    val effects = listOf(
        StackEffect.DismissOverlays(removed.routeId),
        StackEffect.Exit(removed.routeId, animate = true, reverse = true),
        StackEffect.Precompose(newTop.routeId),
    )
    return StackResult.Applied(copy(routes = rest, exitingRouteId = removed.routeId), effects)
}

/**
 * 预测返回 commit：pop 但**不再播**退出动画（画面已到目标态，BL-29）。
 */
fun PanelStackState.popCommittedFromPredictive(): StackResult {
    if (routes.size <= 1) return StackResult.Ignored(this, "at-root")
    val removed = routes.last()
    val rest = routes.dropLast(1)
    val effects = listOf(
        StackEffect.DismissOverlays(removed.routeId),
        StackEffect.Exit(removed.routeId, animate = false, reverse = true),
        StackEffect.Precompose(rest.last().routeId),
    )
    return StackResult.Applied(copy(routes = rest, exitingRouteId = null), effects)
}

/**
 * popToRoot（returnMode=TO_ROOT）：栈只留 depth0。
 * **只对原栈顶播一次反向转场**，中间层直接销毁（BL-07；反证：逐层连播会把回根变成 3 秒动画）。
 */
fun PanelStackState.popToRoot(): StackResult {
    if (routes.size <= 1) return StackResult.Ignored(this, "at-root")
    val oldTop = routes.last()
    val root = routes.first()
    val effects = buildList {
        add(StackEffect.DismissOverlays(oldTop.routeId))
        add(StackEffect.Exit(oldTop.routeId, animate = true, reverse = true))
        add(StackEffect.Precompose(root.routeId))
        for (i in routes.lastIndex - 1 downTo 1) add(StackEffect.Destroyed(routes[i].routeId))
    }
    return StackResult.Applied(
        copy(routes = listOf(root), exitingRouteId = oldTop.routeId),
        effects,
    )
}

/**
 * popTo(targetRouteId)：深链目标已在栈中时回退到该层（§1.5.5-5）。
 * 不存在 / 已在栈顶 → 忽略。
 */
fun PanelStackState.popTo(targetRouteId: String): StackResult {
    if (exitingRouteId != null) return StackResult.Ignored(this, "exiting-in-progress")
    val index = routes.indexOfFirst { it.routeId == targetRouteId }
    if (index < 0) return StackResult.Ignored(this, "target-not-in-stack")
    if (index == routes.lastIndex) return StackResult.Ignored(this, "already-on-top")
    val oldTop = routes.last()
    val target = routes[index]
    val effects = buildList {
        add(StackEffect.DismissOverlays(oldTop.routeId))
        add(StackEffect.Exit(oldTop.routeId, animate = true, reverse = true))
        add(StackEffect.Precompose(target.routeId))
        for (i in routes.lastIndex - 1 downTo index + 1) add(StackEffect.Destroyed(routes[i].routeId))
    }
    return StackResult.Applied(
        copy(routes = routes.take(index + 1), exitingRouteId = oldTop.routeId),
        effects,
    )
}

/**
 * replace：同 depth 换 target/params；栈长与历史位置不变；`from`/`edge` **继承旧层**（BL-08）。
 * 转场：旧层淡出 ≤120ms + 新层直接渲染（不位移）。
 * 前置：空栈忽略（首版唯一调用点 = 深链目标已在栈中且 params 不同）。
 */
fun PanelStackState.replace(route: PanelRoute): StackResult {
    if (routes.isEmpty()) return StackResult.Ignored(this, "empty-stack")
    if (exitingRouteId != null) return StackResult.Ignored(this, "exiting-in-progress")
    val old = routes.last()
    val replaced = route.copy(
        routeId = if (route.routeId == old.routeId) "${old.routeId}#r" else route.routeId,
        from = old.from,
        edge = old.edge,
    )
    val effects = listOf(
        StackEffect.DismissOverlays(old.routeId),
        StackEffect.Exit(old.routeId, animate = true, reverse = false),
        StackEffect.Enter(replaced.routeId, edge = null, animate = false),
    )
    return StackResult.Applied(
        copy(
            routes = routes.dropLast(1) + replaced,
            enteringRouteId = null,
            exitingRouteId = null,
        ),
        effects,
    )
}

// ---------------------------------------------------------------------------
// §1.3.3 窗口类别（window-class）
// ---------------------------------------------------------------------------

enum class PanelWindowClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 断点（§1.3.3 / BL-35…BL-38）：
 *  - `h < 480` → COMPACT（**高度优先**：915×412 横屏必须单栏，BL-38）；
 *  - `w < 600` → COMPACT；
 *  - `600 ≤ w < 840` → MEDIUM；
 *  - `w ≥ 840` → EXPANDED。
 */
fun windowClassOf(widthDp: Float, heightDp: Float): PanelWindowClass = when {
    heightDp < 480f -> PanelWindowClass.COMPACT
    widthDp < 600f -> PanelWindowClass.COMPACT
    widthDp < 840f -> PanelWindowClass.MEDIUM
    else -> PanelWindowClass.EXPANDED
}

// ---------------------------------------------------------------------------
// BL-09：进程重建快照（只恢复 routeId/panelId/params/from/edge 等，不恢复进行中转场）
// ---------------------------------------------------------------------------

/**
 * 单层快照。`params` 压成 String（恢复只需"值相等"，业务类型由各面板按声明还原）；
 * `widthSpec` 用 `"FULL"` / `"MIN_MAX:320:0.86:56"` / `"FIXED:280"` 编码。
 * 未知字段（未来升 schemaVersion）一律拒绝 → 该层按重新构建处理，不整栈崩（§1.8.2 字段 10）。
 */
data class RouteSnapshot(
    val routeId: String,
    val panelId: String,
    val kind: String = PanelKind.OTHER.name,
    val params: Map<String, String> = emptyMap(),
    val from: String? = null,
    val edge: String? = null,
    val widthSpec: String = "FULL",
    val coverMode: String = CoverMode.OVER_OPAQUE.name,
    val returnMode: String = ReturnMode.POP.name,
    val presentation: String = PanelPresentation.ROUTE_FULL.name,
)

/** 整栈快照（不含 entering/exiting/transitionProgress）。 */
data class PanelStackSnapshot(
    val routes: List<RouteSnapshot>,
    val activeRouteId: String? = routes.lastOrNull()?.routeId,
)

fun WidthSpec.encode(): String = when (this) {
    is WidthSpec.Full -> "FULL"
    is WidthSpec.MinMax -> "MIN_MAX:$minDp:$frac:$maxInsetDp"
    is WidthSpec.Fixed -> "FIXED:$dp"
}

/** 解析失败 → null（调用方按该层重建处理）。 */
fun decodeWidthSpec(encoded: String): WidthSpec? {
    val parts = encoded.split(':')
    return when (parts.firstOrNull()) {
        "FULL" -> WidthSpec.Full
        "MIN_MAX" -> {
            val min = parts.getOrNull(1)?.toFloatOrNull() ?: return null
            val frac = parts.getOrNull(2)?.toFloatOrNull() ?: return null
            val inset = parts.getOrNull(3)?.toFloatOrNull() ?: return null
            WidthSpec.MinMax(min, frac, inset)
        }
        "FIXED" -> WidthSpec.Fixed(parts.getOrNull(1)?.toFloatOrNull() ?: return null)
        else -> null
    }
}

fun PanelRoute.toSnapshot(): RouteSnapshot = RouteSnapshot(
    routeId = routeId,
    panelId = panelId,
    kind = kind.name,
    params = params.mapValues { it.value.toString() },
    from = from,
    edge = edge?.name,
    widthSpec = widthSpec.encode(),
    coverMode = coverMode.name,
    returnMode = returnMode.name,
    presentation = presentation.name,
)

/** 恢复单层；任何一个枚举/宽度解析不出来都返回 null（该层重建，不整栈崩）。 */
fun RouteSnapshot.toRouteOrNull(): PanelRoute? {
    val kind = PanelKind.entries.firstOrNull { it.name == this.kind } ?: return null
    val coverMode = CoverMode.entries.firstOrNull { it.name == this.coverMode } ?: return null
    val returnMode = ReturnMode.entries.firstOrNull { it.name == this.returnMode } ?: return null
    val presentation = PanelPresentation.entries.firstOrNull { it.name == this.presentation } ?: return null
    val edge = if (this.edge == null) null else Edge.entries.firstOrNull { it.name == this.edge } ?: return null
    val widthSpec = decodeWidthSpec(this.widthSpec) ?: return null
    return PanelRoute(
        routeId = routeId,
        panelId = panelId,
        params = params, // Map<String, String> 是 Map<String, Any> 的子类型（Map 对 V 协变）
        from = from,
        edge = edge,
        widthSpec = widthSpec,
        coverMode = coverMode,
        returnMode = returnMode,
        kind = kind,
        presentation = presentation,
    )
}

fun PanelStackState.toSnapshot(): PanelStackSnapshot =
    PanelStackSnapshot(routes = routes.map { it.toSnapshot() })

/** 恢复后 `transitionProgress` 归零、不恢复进行中的转场（BL-09）。 */
fun PanelStackSnapshot.fromSnapshot(): PanelStackState = PanelStackState(
    routes = routes.mapNotNull { it.toRouteOrNull() },
    enteringRouteId = null,
    exitingRouteId = null,
)
