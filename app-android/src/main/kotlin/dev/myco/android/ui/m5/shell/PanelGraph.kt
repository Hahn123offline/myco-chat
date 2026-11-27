// 验收 ID：BL-01 BL-02 BL-03 BL-04（按钮入口 push）/ BL-05（pop 回来源）
//
// base.layout.panel-graph（§1.4.1 / §1.5）：**纯数据邻接图** + 四层校验（L1–L3）+ 深链祖先链合成。
//
// 契约（逐条来自规格）：
//  * 节点 = `PanelSpec.specId`；边 = `(fromPanelId, edge) → target`，只描述"**可打开**"；
//    **返回不是边**（返回 = pop，由 `from` 决定）——所以图里不写反向边（§1.5.1）；
//  * 首版唯一启用图 = 内置 `chat-profile` v1（§1.5.2），`root = chat`；
//  * L1 参数校验（每次 OpenRequest）：target 白名单 / params schema / edge 必须匹配 `edgesOf(from)` / 无自环；
//    失败 = **拒绝**：不 push、零转场、depth 不变（§1.5.3），release 由调用方 Toast（Toast 归 primitives）；
//  * L2 图版本：`schemaVersion ∈ SUPPORTED_VERSIONS`，未知字段拒绝；失败 → **回退内置默认图**（不白屏）；
//    （内置图是编译期 Kotlin 常量，字段级"未知字段"由 schemaVersion 演进负责；将来 M6 从 JSON 载入时
//    未知字段由 L2 解析层拒绝——E-23=A：首版图内置、编辑器归 M6。）
//  * L3 结构：根存在 / 边界可达 / 无自环 / 无重复 `(from,edge)` / 所有 `to` 在白名单 → **fail-fast 崩溃**
//    （配置错误不许静默，§1.5.3）；
//  * 深链落到栈（§1.5.5）：反向 BFS 求 `root → … → target` 祖先链，**不可达 → 拒绝整条**（栈保持原样）；
//    `NONE` 边参与可达性（按钮入口同样可达）；`NONE` 边取 `edge=null`（转场用淡入 ≤120ms）；
//  * 命令入口（§1.5.4）不要求图边，首版两条，**必须在实现里显式写死、不许面板自己改栈**：
//    `closeThenPush`（抽屉 footer → settings）与 `closeThenSelect`（会话行点击）。
//
// 本文件零 Compose / 零 Android 依赖（JVM 单测见 PanelGraphTest.kt）。
package dev.myco.android.ui.m5.shell

// ---------------------------------------------------------------------------
// 数据模型
// ---------------------------------------------------------------------------

/** 一条「可打开」边（§1.5.1）。`openSource=NONE` = 只能按钮/命令打开（图仍是白名单）。 */
data class PanelEdge(
    val from: String,
    val edge: Edge,
    val target: String,
    /** 打开方式：只有 CONTENT_DRAG 能滑动；SCREEN_EDGE 首版不做（D-06）。 */
    val openSource: SwipeOpenSource = SwipeOpenSource.NONE,
    /** 手势允许的层：开手势 ROOT_ONLY（深层禁开，G-15）。 */
    val swipeDepth: SwipeEnabledWhen = SwipeEnabledWhen.ROOT_ONLY,
)

/**
 * 图节点（§1.4.1 `PanelSpec` 的落地子集；`edges` 用 List 而非 Map 才能让 L3 检查"重复 (from,edge)"）。
 */
data class PanelSpec(
    val specId: String,
    val kind: PanelKind,
    val presentation: PanelPresentation = PanelPresentation.ROUTE_FULL,
    val widthSpec: WidthSpec = WidthSpec.Full,
    val coverMode: CoverMode = CoverMode.OVER_OPAQUE,
    val returnMode: ReturnMode = ReturnMode.POP,
    /** 出边（不含"返回"、不含自环）——每张卡一次定义，是图的一部分。 */
    val edges: List<PanelEdge> = emptyList(),
    /** 参数 schema（null = 不校验；非 null → 未知字段拒绝，§1.5.3 L1②）。 */
    val allowedParams: Set<String>? = null,
    /** 该面板实例的缺省滑动接口（抽屉 OUT / 全屏 OUT；root 无关闭手势 → null）。 */
    val defaultSwipe: SwipeSpec? = null,
    val a11yRoleDialog: Boolean = false,
)

/** 打开请求（§1.5.2 末：按钮/命令/深链统一入口）。 */
data class OpenRequest(
    val from: String?,
    val edge: Edge?,
    val target: String,
    val params: Map<String, Any> = emptyMap(),
)

/** 深链链路的单节（§1.5.5）：`edge` = 从上一节进来的边；NONE 边 → null。 */
data class PanelChainLink(
    val panelId: String,
    val from: String?,
    val edge: Edge?,
    val openSource: SwipeOpenSource,
)

/** L1 拒绝原因（release 可映射成"该入口不可用"，dev 断言用 code）。 */
enum class GraphRejectReason {
    UNKNOWN_TARGET,
    UNKNOWN_SOURCE,
    EDGE_NOT_ALLOWED,
    SELF_LOOP,
    PARAM_NOT_ALLOWED,
    TARGET_NOT_REACHABLE,
    UNSUPPORTED_SCHEMA,
}

/** L1 结果。 */
sealed interface GraphResult {
    data object Accepted : GraphResult

    data class Rejected(
        val reason: GraphRejectReason,
        val detail: String,
    ) : GraphResult

    val accepted: Boolean get() = this is Accepted
}

// ---------------------------------------------------------------------------
// 图
// ---------------------------------------------------------------------------

/**
 * 邻接图（不可变；换图 = 换 `schemaVersion`）。
 *
 * 构造**不抛异常**（L2 失败要能回退）；结构错误由 [structureViolations] / [checkStructure] 显式检查（L3 fail-fast）。
 */
class PanelGraph(
    val schemaVersion: String,
    val root: String,
    val specs: List<PanelSpec>,
) {
    private val specsByIdLazy = lazy { specs.groupBy { it.specId } }

    /** 重复 specId 在 L3 报错；查询取第一个。 */
    private val specsById: Map<String, PanelSpec>
        get() = specsByIdLazy.value.mapValues { it.value.first() }

    val panelIds: Set<String> get() = specsById.keys

    fun specOf(panelId: String): PanelSpec? = specsById[panelId]

    /** §1.5.1 `edgesOf(panelId)`。 */
    fun edgesOf(panelId: String): List<PanelEdge> = specOf(panelId)?.edges ?: emptyList()

    /** (from, edge) → target（重复边以第一条为准；L3 会报错）。 */
    fun edgeTargets(panelId: String): Map<Edge, String> {
        val out = LinkedHashMap<Edge, String>()
        edgesOf(panelId).forEach { if (it.edge !in out) out[it.edge] = it.target }
        return out
    }

    /** 该面板的"内容区拖拽打开"边（首版只有 chat → sessions，G-15/D-06）。 */
    fun dragOpenEdge(panelId: String): PanelEdge? =
        edgesOf(panelId).firstOrNull { it.openSource == SwipeOpenSource.CONTENT_DRAG }

    // -- L1 参数校验（每次 OpenRequest） -------------------------------------

    /**
     * L1：① target ∈ 白名单 ② params 符合声明 ③ edge 必须匹配 `edgesOf(from)[edge]` ④ 不得自环。
     * 失败 → 调用方**拒绝**（不 push、零转场、depth 不变），release 提示由调用方给。
     */
    fun validate(request: OpenRequest): GraphResult {
        val targetSpec = specOf(request.target)
            ?: return GraphResult.Rejected(
                GraphRejectReason.UNKNOWN_TARGET,
                "target '${request.target}' 不在白名单",
            )
        if (request.from != null) {
            if (specOf(request.from) == null) {
                return GraphResult.Rejected(
                    GraphRejectReason.UNKNOWN_SOURCE,
                    "from '${request.from}' 不在白名单",
                )
            }
            if (request.from == request.target) {
                return GraphResult.Rejected(GraphRejectReason.SELF_LOOP, "自环 ${request.from} → ${request.target}")
            }
        }
        if (request.edge != null) {
            val from = request.from ?: return GraphResult.Rejected(
                GraphRejectReason.EDGE_NOT_ALLOWED,
                "声明了 edge=${request.edge} 但 from=null",
            )
            val target = edgeTargets(from)[request.edge]
                ?: return GraphResult.Rejected(
                    GraphRejectReason.EDGE_NOT_ALLOWED,
                    "from=$from 没有 ${request.edge} 出边",
                )
            if (target != request.target) {
                return GraphResult.Rejected(
                    GraphRejectReason.EDGE_NOT_ALLOWED,
                    "from=$from 的 ${request.edge} 边指向 '$target'，不是 '${request.target}'",
                )
            }
        }
        val allowed = targetSpec.allowedParams
        if (allowed != null) {
            val unknown = request.params.keys - allowed
            if (unknown.isNotEmpty()) {
                return GraphResult.Rejected(
                    GraphRejectReason.PARAM_NOT_ALLOWED,
                    "target '${request.target}' 不接受参数 $unknown（允许 $allowed）",
                )
            }
        }
        return GraphResult.Accepted
    }

    /** L1 通过后把请求物化成 `PanelRoute`（host 用它 push；保证"面板不自己改栈"）。 */
    fun routeFor(request: OpenRequest, routeId: String): PanelRoute? {
        if (!validate(request).accepted) return null
        val spec = specOf(request.target) ?: return null
        return PanelRoute(
            routeId = routeId,
            panelId = spec.specId,
            params = request.params,
            from = request.from,
            edge = request.edge,
            widthSpec = spec.widthSpec,
            coverMode = spec.coverMode,
            returnMode = spec.returnMode,
            kind = spec.kind,
            presentation = spec.presentation,
            swipe = spec.defaultSwipe,
        )
    }

    // -- L2 / L3 -------------------------------------------------------------

    /** L2：`schemaVersion` 是否受支持（未知版本 → 调用方回退内置图）。 */
    val schemaSupported: Boolean get() = schemaVersion in SUPPORTED_SCHEMA_VERSIONS

    /**
     * L3 结构校验（返回全部违规，便于启动期一次性报出来）。
     * 覆盖：根存在、重复 specId、边 owner 正确、to 在白名单、无自环、无重复 (from,edge)、全边界可达。
     */
    fun structureViolations(): List<String> {
        val violations = mutableListOf<String>()
        if (!schemaSupported) {
            violations += "未知 schemaVersion '$schemaVersion'（支持 $SUPPORTED_SCHEMA_VERSIONS）"
        }
        val duplicates = specs.groupBy { it.specId }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) violations += "重复 specId: $duplicates"
        if (root !in specsById) violations += "根不存在: '$root'"
        val seenEdges = mutableSetOf<Pair<String, Edge>>()
        for (spec in specs) {
            for (edge in spec.edges) {
                if (edge.from != spec.specId) {
                    violations += "边 (${edge.from},${edge.edge}) 声明在 '${spec.specId}' 上，owner 不一致"
                }
                if (edge.target !in specsById) violations += "边 (${edge.from},${edge.edge}) → '${edge.target}' 不在白名单"
                if (edge.from == edge.target) violations += "自环 (${edge.from},${edge.edge})"
                if (!seenEdges.add(edge.from to edge.edge)) {
                    violations += "重复 (from,edge) = (${edge.from},${edge.edge})"
                }
            }
        }
        // 全部节点必须由 root 沿边可达（openSource=NONE 的边也参与）
        val reachable = reachableFrom(root)
        val unreachable = panelIds - reachable
        if (unreachable.isNotEmpty()) violations += "由根不可达: $unreachable"
        return violations
    }

    /** L3：结构错误 → **fail-fast 崩溃**（配置错误不许静默）。 */
    fun checkStructure(): PanelGraph {
        val violations = structureViolations()
        check(violations.isEmpty()) {
            "内置图结构非法（L3 fail-fast）：${violations.joinToString("; ")}"
        }
        return this
    }

    private fun reachableFrom(start: String): Set<String> {
        val seen = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in edgesOf(current)) {
                if (seen.add(edge.target)) queue.addLast(edge.target)
            }
        }
        return seen
    }

    // -- §1.5.5 深链：反向 BFS 祖先链 ----------------------------------------

    /**
     * 反向 BFS：从 [target] 沿"谁有一条边指向我"走到 [root]，返回 `root → … → target` 的链路。
     *
     * - `NONE` 边参与（按钮入口同样可达）；
     * - **不可达 → null**（调用方拒绝整条深链，栈保持原样）；
     * - target 已在栈中时由调用方 popTo / replace（§1.5.5-5，本函数只给链）。
     */
    fun deepLinkChain(target: String): List<PanelChainLink>? {
        if (specOf(target) == null) return null
        if (target == root) {
            return listOf(PanelChainLink(root, null, null, SwipeOpenSource.NONE))
        }
        // 反向邻接：target → 指向它的 (from, edge, openSource)
        val incoming = mutableMapOf<String, MutableList<PanelEdge>>()
        for (spec in specs) {
            for (edge in spec.edges) {
                incoming.getOrPut(edge.target) { mutableListOf() } += edge
            }
        }
        // BFS（步数最少），记录 parent
        val parent = mutableMapOf<String, PanelEdge>()
        val visited = linkedSetOf(target)
        val queue = ArrayDeque(listOf(target))
        var found = false
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == root) {
                found = true
                break
            }
            for (edge in incoming[current].orEmpty()) {
                if (visited.add(edge.from)) {
                    parent[edge.from] = edge
                    queue.addLast(edge.from)
                }
            }
        }
        if (!found) return null
        // 回溯 root → … → target
        val reversed = mutableListOf<PanelChainLink>()
        var cursor = root
        while (cursor != target) {
            val edge = parent[cursor] ?: return null
            reversed += PanelChainLink(
                panelId = edge.target,
                from = edge.from,
                // §1.5.5-3：NONE 边取 null（转场用淡入 ≤120ms）；滑动边取该边方向。
                edge = if (edge.openSource == SwipeOpenSource.NONE) null else edge.edge,
                openSource = edge.openSource,
            )
            cursor = edge.target
        }
        // 链头 = root（edge=null）
        return listOf(PanelChainLink(root, null, null, SwipeOpenSource.NONE)) + reversed
    }

    /**
     * 深链物化成 route 列表（§1.5.5-2：一次 commit 压整条链；只对 target 播进入转场由 host 决定）。
     * 不可达 / 参数非法 → null（拒绝整条，栈不变）。
     */
    fun chainRoutes(
        target: String,
        params: Map<String, Any> = emptyMap(),
    ): List<PanelRoute>? {
        val chain = deepLinkChain(target) ?: return null
        // 末节才允许带 params（中间层不带参数，避免污染历史）；params schema 单独校验
        val targetSpec = specOf(target) ?: return null
        val allowed = targetSpec.allowedParams
        if (allowed != null && !allowed.containsAll(params.keys)) return null
        return chain.map { link ->
            val spec = specOf(link.panelId) ?: return null
            PanelRoute(
                routeId = "deep-${link.panelId}",
                panelId = spec.specId,
                params = if (link.panelId == target) params else emptyMap(),
                from = link.from,
                edge = link.edge,
                widthSpec = spec.widthSpec,
                coverMode = spec.coverMode,
                returnMode = spec.returnMode,
                kind = spec.kind,
                presentation = spec.presentation,
                swipe = spec.defaultSwipe,
            )
        }
    }

    companion object {
        /** 支持的图版本（L2 白名单；未知版本 → 回退内置图，不白屏）。 */
        val SUPPORTED_SCHEMA_VERSIONS: Set<String> = setOf(PanelGraphs.CHAT_PROFILE_V1)
    }
}

// ---------------------------------------------------------------------------
// §1.5.4 命令入口（不要求图边；首版两条，显式写死，面板不得自己改栈）
// ---------------------------------------------------------------------------

/**
 * ① `closeThenPush(target, edge, params)` —— 抽屉 footer 的设置入口：
 * **同帧** `pop(sessions)` + `push(settings)`，只允许 host 调用。
 */
fun PanelStackState.closeThenPush(route: PanelRoute): StackResult {
    if (stackTooShallowForCommand()) return StackResult.Ignored(this, "stack-too-shallow")
    val removed = routes.last()
    val rest = routes.dropLast(1)
    val pushed = copy(routes = rest, enteringRouteId = null, exitingRouteId = null).push(route)
    return when (pushed) {
        is StackResult.Ignored -> pushed
        is StackResult.Applied -> StackResult.Applied(
            state = pushed.state,
            effects = buildList {
                add(StackEffect.DismissOverlays(removed.routeId))
                add(StackEffect.Exit(removed.routeId, animate = true, reverse = true))
                addAll(pushed.effects)
            },
        )
    }
}

/**
 * ② `closeThenSelect(sessionId)` —— 会话行点击：**同帧** `pop(sessions)` + 业务层切会话。
 * 切会话本身归业务层（本函数只返回"弹出后"的栈 + 选择事件）。
 */
fun PanelStackState.closeThenSelect(sessionId: String): Pair<StackResult, String> {
    if (stackTooShallowForCommand()) return StackResult.Ignored(this, "stack-too-shallow") to sessionId
    val removed = routes.last()
    val rest = routes.dropLast(1)
    val result = StackResult.Applied(
        state = copy(routes = rest, exitingRouteId = removed.routeId),
        effects = listOf(
            StackEffect.DismissOverlays(removed.routeId),
            StackEffect.Exit(removed.routeId, animate = true, reverse = true),
        ),
    )
    return result to sessionId
}

private fun PanelStackState.stackTooShallowForCommand(): Boolean = routes.size < 2

// ---------------------------------------------------------------------------
// 内置图 `chat-profile` v1（§1.5.2，首版唯一启用图）
// ---------------------------------------------------------------------------

object PanelGraphs {

    /** 首版图版本（L2 白名单成员）。 */
    const val CHAT_PROFILE_V1: String = "chat-profile/v1"

    /**
     * L2 加载：版本受支持 → 返回内置图；**未知版本 → 回退内置默认图**（不白屏，§1.5.3）。
     * L3 结构错误在 [chatProfileV1] 里 fail-fast 崩溃。
     */
    fun load(schemaVersion: String): PanelGraph =
        if (schemaVersion in PanelGraph.SUPPORTED_SCHEMA_VERSIONS) chatProfileV1() else chatProfileV1()

    /**
     * 内置图 `chat-profile` v1：
     *
     * | from | edge | to | openSource | 手势层 |
     * |---|---|---|---|---|
     * | chat | LEFT | sessions | CONTENT_DRAG | ROOT_ONLY（depth0） |
     * | chat | RIGHT | settings | NONE | —（顶栏菜单打开） |
     * | settings | RIGHT | bin | NONE | —（settings 上叠 bin） |
     * | sessions / bin | — | — | 叶子 | — |
     */
    fun chatProfileV1(): PanelGraph = PanelGraph(
        schemaVersion = CHAT_PROFILE_V1,
        root = "chat",
        specs = listOf(
            PanelSpec(
                specId = "chat",
                kind = PanelKind.CHAT,
                presentation = PanelPresentation.ROUTE_FULL,
                widthSpec = WidthSpec.Full,
                coverMode = CoverMode.OVER_OPAQUE,
                returnMode = ReturnMode.POP,
                allowedParams = setOf("sessionId", "scrollToSeq", "anchorId"),
                defaultSwipe = null, // 根层：只有"拖开"，没有"拖出关"
                edges = listOf(
                    PanelEdge(
                        from = "chat",
                        edge = Edge.LEFT,
                        target = "sessions",
                        openSource = SwipeOpenSource.CONTENT_DRAG,
                        swipeDepth = SwipeEnabledWhen.ROOT_ONLY,
                    ),
                    PanelEdge(
                        from = "chat",
                        edge = Edge.RIGHT,
                        target = "settings",
                        openSource = SwipeOpenSource.NONE,
                    ),
                ),
            ),
            PanelSpec(
                specId = "sessions",
                kind = PanelKind.SESSIONS,
                presentation = PanelPresentation.ROUTE_DRAWER,
                widthSpec = WidthSpec.MinMax(),            // min(320+inset, 0.86W, W−56)
                coverMode = CoverMode.OVER_DIM,            // 0.32 遮罩
                returnMode = ReturnMode.POP,
                allowedParams = emptySet(),
                defaultSwipe = SwipeSpec(
                    edge = Edge.LEFT,
                    direction = SwipeDirection.OUT,
                    openSource = SwipeOpenSource.NONE,
                    enabledWhen = SwipeEnabledWhen.TOP_LAYER_ONLY,
                ),
                a11yRoleDialog = true,                     // 抽屉：paneTitle + 焦点陷阱（role=dialog 见 B0 待裁决）
            ),
            PanelSpec(
                specId = "settings",
                kind = PanelKind.SETTINGS,
                presentation = PanelPresentation.ROUTE_FULL,
                widthSpec = WidthSpec.Full,
                coverMode = CoverMode.OVER_OPAQUE,         // 全屏不透明 → 无遮罩
                returnMode = ReturnMode.POP,
                allowedParams = setOf("tab", "section"),
                defaultSwipe = SwipeSpec(
                    edge = Edge.RIGHT,
                    direction = SwipeDirection.OUT,        // 全屏只允许"拖出关闭"（§1.3.1）
                    openSource = SwipeOpenSource.NONE,
                    enabledWhen = SwipeEnabledWhen.TOP_LAYER_ONLY,
                ),
                edges = listOf(
                    PanelEdge(
                        from = "settings",
                        edge = Edge.RIGHT,
                        target = "bin",
                        openSource = SwipeOpenSource.NONE,
                    ),
                ),
            ),
            PanelSpec(
                specId = "bin",
                kind = PanelKind.BIN,
                presentation = PanelPresentation.ROUTE_FULL,
                widthSpec = WidthSpec.Full,
                coverMode = CoverMode.OVER_OPAQUE,
                returnMode = ReturnMode.POP,
                allowedParams = setOf("kind", "scrollToSeq"),
                defaultSwipe = SwipeSpec(
                    edge = Edge.RIGHT,
                    direction = SwipeDirection.OUT,
                    openSource = SwipeOpenSource.NONE,
                    enabledWhen = SwipeEnabledWhen.TOP_LAYER_ONLY,
                ),
                a11yRoleDialog = false,
            ),
        ),
    ).checkStructure() // L3 fail-fast（内置图配错 → 启动期崩溃，不静默白屏）
}
