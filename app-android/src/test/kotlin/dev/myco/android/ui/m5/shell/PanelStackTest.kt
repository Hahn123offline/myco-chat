// 验收 ID（JVM 单测覆盖）：
//  BL-01…BL-09（栈 reducer / 保活 / 快照）、BL-22…BL-27 BL-30 BL-31（返回 dispatcher 优先级）、
//  BP-01 BP-13 BP-18 BP-37 BP-67 BP-73 BP-79（命中区判定式与不相交）、BL-19 BL-20 BL-21 BL-50（z/遮罩 token）、
//  BL-35…BL-38（window-class）、BL-40 BL-41（insets 单次 union）、BP-63 BP-74（动效策略）。
//
// 本文件把三个可判定的"纯逻辑"钉死：栈 reducer、返回优先级表、命中区判定式。
// 需要真机/截图/TalkBack 的项（BL-10…BL-18、BL-28/29、BP-02…BP-08 等）不在这里冒充通过。
package dev.myco.android.ui.m5.shell

import dev.myco.android.ui.m5.primitives.HitCategory
import dev.myco.android.ui.m5.primitives.HitDegradation
import dev.myco.android.ui.m5.primitives.HitRect
import dev.myco.android.ui.m5.primitives.HitTargets
import dev.myco.android.ui.m5.primitives.M5ExpansionMotion
import dev.myco.android.ui.m5.primitives.M5MotionKind
import dev.myco.android.ui.m5.primitives.M5MotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// ---------------------------------------------------------------------------
// 测试夹具
// ---------------------------------------------------------------------------

private fun route(
    id: String,
    panel: String = id,
    edge: Edge? = null,
    from: String? = null,
    width: WidthSpec = WidthSpec.Full,
    coverMode: CoverMode = CoverMode.OVER_OPAQUE,
    returnMode: ReturnMode = ReturnMode.POP,
    params: Map<String, Any> = emptyMap(),
    swipe: SwipeSpec? = null,
) = PanelRoute(
    routeId = id,
    panelId = panel,
    params = params,
    from = from,
    edge = edge,
    widthSpec = width,
    coverMode = coverMode,
    returnMode = returnMode,
    swipe = swipe,
)

private val drawerWidth = WidthSpec.MinMax()
private val chat = route("r-chat", "chat")
private val sessions = route(
    "r-sessions",
    "sessions",
    edge = Edge.LEFT,
    from = "chat",
    width = drawerWidth,
    coverMode = CoverMode.OVER_DIM,
    swipe = SwipeSpec(edge = Edge.LEFT),
)
private val settings = route("r-settings", "settings", edge = Edge.RIGHT, from = "chat", returnMode = ReturnMode.TO_ROOT)
private val bin = route("r-bin", "bin", edge = Edge.RIGHT, from = "settings")

private fun stackOf(vararg routes: PanelRoute) = PanelStackState(routes = routes.toList())

private fun PanelStackState.depthIds(): List<String> = routes.map { it.routeId }

// ---------------------------------------------------------------------------
// 1. 栈 reducer（§1.8.1 / BL-04…BL-09）
// ---------------------------------------------------------------------------

class PanelStackTest {

    @Test
    fun `push 后 depth 派生、栈顶 ACTIVE、下层按规则保活`() {
        val s0 = stackOf(chat)
        val result = s0.push(sessions)
        assertTrue(result.applied)
        val s1 = result.resultState
        assertEquals(2, s1.depth)
        assertEquals("r-sessions", s1.top?.routeId)
        assertEquals(KeepAlive.KEEP, s1.keepAliveAt(0))
        assertEquals(KeepAlive.ACTIVE, s1.keepAliveAt(1))
        // push 前置：同帧关闭栈顶层浮层（BL-27） + 进入转场
        val effects = (result as StackResult.Applied).effects
        assertEquals(StackEffect.DismissOverlays("r-chat"), effects[0])
        assertTrue(effects.any { it is StackEffect.Enter && it.routeId == "r-sessions" && it.edge == Edge.LEFT })
    }

    @Test
    fun `连点同一入口 5 次只入栈 1 层（BL-06）`() {
        var s = stackOf(chat)
        repeat(5) { s = s.push(sessions).resultState }
        assertEquals(2, s.depth)
        assertEquals(listOf("r-chat", "r-sessions"), s.depthIds())
    }

    @Test
    fun `ENTERING 中重复同一 panelId 被忽略`() {
        var s = stackOf(chat).push(sessions).resultState
        // 进入转场未结束（enteringRouteId 仍在），再推一个 sessions 的另一个实例
        val again = s.push(route("r-sessions-2", "sessions"))
        assertFalse(again.applied)
        assertEquals(2, again.resultState.depth)
        // 转场结束后允许再入栈（不同 panel 或不同 params）
        s = s.onEnterSettled("r-sessions")
        val ok = s.push(route("r-settings-2", "settings"))
        assertTrue(ok.applied)
    }

    @Test
    fun `同 panel 不同 params 是不同层`() {
        var s = stackOf(chat).push(route("r-a", "settings", params = mapOf("tab" to "general"))).resultState
        s = s.onEnterSettled("r-a") // 进入转场结束（ENTERING 期间同 panel 会被去重）
        val next = s.push(route("r-b", "settings", params = mapOf("tab" to "bin")))
        assertTrue(next.applied)
        assertEquals(3, next.resultState.depth)
    }

    @Test
    fun `深度 0-1 保活、深度 2 起冻结`() {
        val s = stackOf(chat, route("r-a"), route("r-b"), route("r-c"))
        assertEquals(KeepAlive.KEEP, s.keepAliveAt(0))
        assertEquals(KeepAlive.KEEP, s.keepAliveAt(1)) // depth ≤ 1 保活（E-01=A）
        assertEquals(KeepAlive.FROZEN, s.keepAliveAt(2)) // depth ≥ 2 冻结
        assertEquals(KeepAlive.ACTIVE, s.keepAliveAt(3))
    }

    @Test
    fun `栈顶是 PARTIAL 时其下一层强制 KEEP（BL-34）`() {
        val s = stackOf(chat, route("r-a"), route("r-b"), sessions)
        // 默认 index=2 会 FROZEN；因为栈顶是抽屉（PARTIAL）→ 强制 KEEP
        assertEquals(KeepAlive.KEEP, s.keepAliveAt(2))
        // 去掉抽屉后恢复 FROZEN
        val normalized = stackOf(chat, route("r-a"), route("r-b"), route("r-c"))
        assertEquals(KeepAlive.FROZEN, normalized.keepAliveAt(2))
    }

    @Test
    fun `FROZEN 层不在渲染集合内、KEEP 层在`() {
        val s = stackOf(chat, route("r-a"), route("r-b"), route("r-c"))
        assertEquals(listOf("r-chat", "r-a", "r-c"), s.visibleRoutes().map { it.routeId })
        assertEquals(listOf("r-b"), s.frozenRoutes().map { it.routeId })
    }

    @Test
    fun `pop 移除栈顶并播一次反向转场（BL-05）`() {
        val s = stackOf(chat, settings)
        val result = s.pop()
        assertTrue(result.applied)
        val next = result.resultState
        assertEquals(1, next.depth)
        assertEquals("r-chat", next.top?.routeId)
        assertEquals("r-settings", next.exitingRouteId)
        val effects = (result as StackResult.Applied).effects
        assertEquals(1, effects.count { it is StackEffect.Exit })
        assertTrue(effects.any { it is StackEffect.Precompose && it.routeId == "r-chat" })
        // 退出转场结束后清标记
        assertNull(next.onExitSettled().exitingRouteId)
    }

    @Test
    fun `栈底 pop 不动作、交 dispatcher（BL-25）`() {
        val s = stackOf(chat)
        val result = s.pop()
        assertFalse(result.applied)
        assertEquals(1, result.resultState.depth)
    }

    @Test
    fun `EXITING 中重复 pop 被忽略（BL-06 幂等）`() {
        val exiting = stackOf(chat, settings).pop().resultState
        val again = exiting.pop()
        assertFalse(again.applied)
        assertEquals(1, exiting.depth)
    }

    @Test
    fun `popToRoot 只播 1 个反向转场、中间层直接销毁（BL-07）`() {
        val s = stackOf(chat, settings, bin)
        val result = s.popToRoot()
        assertTrue(result.applied)
        val effects = (result as StackResult.Applied).effects
        assertEquals(1, effects.count { it is StackEffect.Exit })
        val destroyed = effects.filterIsInstance<StackEffect.Destroyed>().map { it.routeId }
        assertEquals(listOf("r-settings"), destroyed) // 只销毁中间层；栈顶由 Exit 处理
        assertEquals(listOf("r-chat"), result.resultState.depthIds())
        assertEquals("r-bin", result.resultState.exitingRouteId)
    }

    @Test
    fun `replace 栈长不变、depth 不变、from 与 edge 继承（BL-08）`() {
        val s = stackOf(chat, settings)
        val replacement = route("r-settings-v2", "settings", params = mapOf("tab" to "advanced"))
        val result = s.replace(replacement)
        assertTrue(result.applied)
        val next = result.resultState
        assertEquals(2, next.depth)
        assertEquals("r-settings-v2", next.top?.routeId)
        // from / edge 继承旧层（settings 的 from 是 chat、edge 是 RIGHT）
        assertEquals("chat", next.top?.from)
        assertEquals(Edge.RIGHT, next.top?.edge)
        assertEquals(mapOf("tab" to "advanced"), next.top?.params)
    }

    @Test
    fun `popTo 深链目标回到该层`() {
        val s = stackOf(chat, settings, bin)
        val result = s.popTo("r-settings")
        assertTrue(result.applied)
        assertEquals(listOf("r-chat", "r-settings"), result.resultState.depthIds())
        assertFalse(s.popTo("missing").applied)
        assertFalse(s.popTo("r-bin").applied) // 已在栈顶
    }

    @Test
    fun `空栈是异常态但 reducer 不崩`() {
        val empty = PanelStackState()
        assertEquals(0, empty.depth)
        assertNull(empty.top)
        assertFalse(empty.pop().applied)
        assertFalse(empty.popToRoot().applied)
        assertTrue(empty.push(chat).applied)
    }

    @Test
    fun `遮罩只画栈顶一层、全屏不画（BL-20 BL-21）`() {
        // 全屏面板在顶 → 没有遮罩层
        assertNull(stackOf(chat, settings).scrimLayerIndex())
        // 抽屉在顶 → 恰好 1 层
        val drawerTop = stackOf(chat, route("r-a"), sessions)
        assertEquals(2, drawerTop.scrimLayerIndex())
        assertEquals(0.32f, drawerTop.scrimAlphaBase(), 0.0001f)
    }

    @Test
    fun `抽屉宽度公式 min(320+inset, 0_86W, W-56)`() {
        assertEquals(320f, drawerWidth.resolveDp(390f, 0f), 0.001f)
        assertEquals(304f, drawerWidth.resolveDp(360f, 0f), 0.001f)
        assertEquals(320f, drawerWidth.resolveDp(840f, 0f), 0.001f)
        // 横屏左挖孔：startInset 参与，但 0.86W / W−56 仍是上限（390×0.86=335.4、390−56=334）
        assertEquals(334f, drawerWidth.resolveDp(390f, 24f), 0.001f)
        // 极窄屏不能为负
        assertTrue(drawerWidth.resolveDp(100f, 0f) >= 0f)
        assertEquals(Cover.FULL, WidthSpec.Full.cover())
        assertEquals(Cover.PARTIAL, drawerWidth.cover())
    }

    @Test
    fun `进程重建快照只恢复栈字段、不恢复转场（BL-09）`() {
        val s = stackOf(chat, sessions).push(settings).resultState
        val restored = s.toSnapshot().fromSnapshot()
        assertEquals(listOf("r-chat", "r-sessions", "r-settings"), restored.depthIds())
        assertEquals(Edge.LEFT, restored.routes[1].edge)
        assertEquals("chat", restored.routes[1].from)
        assertEquals(CoverMode.OVER_DIM, restored.routes[1].coverMode)
        assertNull(restored.enteringRouteId)
        assertNull(restored.exitingRouteId)
        // 未知枚举值 → 该层丢弃（不整栈崩）
        val broken = PanelStackSnapshot(
            routes = listOf(
                RouteSnapshot("r-chat", "chat"),
                RouteSnapshot("r-x", "x", kind = "NOT_A_KIND"),
            ),
        )
        assertEquals(listOf("r-chat"), broken.fromSnapshot().depthIds())
    }
}

// ---------------------------------------------------------------------------
// 2. 返回 dispatcher 优先级（§back.dispatcher / BL-22…BL-27 BL-30 BL-31）
// ---------------------------------------------------------------------------

class BackDispatcherTest {

    private fun input(
        ime: Boolean = false,
        top: String? = "r-b",
        depth: Int = 2,
        returnMode: ReturnMode = ReturnMode.POP,
        exiting: Boolean = false,
        policy: BackDepthPolicy = BackDepthPolicy.CONSUME_WHEN_VISIBLE,
    ) = BackDispatchInput(
        imeVisible = ime,
        topRouteId = top,
        stackDepth = depth,
        topReturnMode = returnMode,
        topRouteExiting = exiting,
        topRouteBackPolicy = policy,
    )

    @Test
    fun `优先级表顺序写死：防重入 1 IME 2 浮层 3 route 4 ROOT`() {
        val d = BackDispatcher()
        assertEquals(
            listOf(BackStage.REENTRANT, BackStage.IME, BackStage.OVERLAY, BackStage.ROUTE, BackStage.ROOT),
            d.priorityTable,
        )
        assertEquals(d.priorityTable.map { it.order }, d.priorityTable.map { it.order }.sorted())
    }

    @Test
    fun `IME 优先于浮层与面板（BL-22）`() {
        val d = BackDispatcher()
        var dismissed = false
        d.registerOverlay("r-b", OverlayFamily.BOTTOM_SHEET, onDismiss = { dismissed = true })
        d.registerRoute("r-b") { fail("IME 场景不该 pop 面板") }
        val r = d.dispatch(input(ime = true))
        assertEquals(BackStage.IME, r.stage)
        assertEquals(BackAction.HideIme, r.action)
        assertTrue(r.consumed)
        assertFalse(dismissed)
    }

    @Test
    fun `浮层优先于面板、按打开逆序关（BL-23 BL-26）`() {
        val d = BackDispatcher()
        val first = d.registerOverlay("r-b", OverlayFamily.MODAL, onDismiss = {})
        var closed = ""
        val second = d.registerOverlay("r-b", OverlayFamily.BOTTOM_SHEET, onDismiss = { closed = "second" })
        assertNotNull(first)

        val r1 = d.dispatch(input())
        assertEquals(BackStage.OVERLAY, r1.stage)
        assertEquals(BackAction.DismissOverlay(second), r1.action)
        assertEquals("second", closed)
        // 浮层关闭动画中再按 → 忽略（等 unregister 完成）
        val r2 = d.dispatch(input())
        assertEquals(BackStage.REENTRANT, r2.stage)
        assertEquals(BackAction.Ignore, r2.action)

        d.unregisterOverlay(second.token)
        d.settle()
        val r3 = d.dispatch(input())
        assertEquals(BackAction.DismissOverlay(first), r3.action)
    }

    @Test
    fun `被覆盖层的浮层不消费（只认栈顶层）`() {
        val d = BackDispatcher()
        var popped = ""
        d.registerOverlay("r-covered", OverlayFamily.MODAL, onDismiss = { fail("不该关被覆盖层的浮层") })
        d.registerRoute("r-b") { popped = "r-b" }
        val r = d.dispatch(input(top = "r-b"))
        assertEquals(BackStage.ROUTE, r.stage)
        assertEquals(BackAction.PopRoute("r-b"), r.action)
        assertEquals("r-b", popped)
    }

    @Test
    fun `栈顶 route 逐层 pop、防重入（BL-24 BL-31）`() {
        val d = BackDispatcher()
        val popped = mutableListOf<String>()
        d.registerRoute("r-b") { popped += "r-b" }
        val r1 = d.dispatch(input())
        assertEquals(BackAction.PopRoute("r-b"), r1.action)
        assertTrue(d.inFlight)
        // 未 settle 前的第二次 → 忽略，不会一次退两层
        val r2 = d.dispatch(input(top = "r-a", depth = 1))
        assertEquals(BackStage.REENTRANT, r2.stage)
        d.settle()
        val r3 = d.dispatch(input(top = "r-a", depth = 1))
        assertEquals(BackStage.ROOT, r3.stage)
        assertFalse("栈底必须交系统", r3.consumed)
        assertEquals(listOf("r-b"), popped)
    }

    @Test
    fun `栈底 depth1 交系统、不 pop（BL-25）`() {
        val d = BackDispatcher()
        d.registerRoute("r-chat") { fail("根层不该被 pop") }
        val r = d.dispatch(input(top = "r-chat", depth = 1))
        assertEquals(BackStage.ROOT, r.stage)
        assertEquals(BackAction.NotConsumed, r.action)
        assertFalse(r.consumed)
    }

    @Test
    fun `HAND_TO_GESTURE 交给预测返回（BL-28 BL-30）`() {
        val d = BackDispatcher()
        d.registerRoute("r-b") { fail("HAND_TO_GESTURE 不该由 dispatcher 直接 pop") }
        val r = d.dispatch(input(returnMode = ReturnMode.HAND_TO_GESTURE))
        assertEquals(BackStage.ROUTE, r.stage)
        assertEquals(BackAction.HandToPredictiveGesture, r.action)
        assertTrue(r.consumed)
    }

    @Test
    fun `PASS_THROUGH 的栈顶层跳过、交 ROOT 回根`() {
        val d = BackDispatcher()
        var rooted = false
        d.registerRoot { rooted = true }
        val r = d.dispatch(input(policy = BackDepthPolicy.PASS_THROUGH, depth = 3))
        assertEquals(BackStage.ROOT, r.stage)
        assertEquals(BackAction.PopToRoot, r.action)
        assertTrue(rooted)
    }

    @Test
    fun `EXITING 中的栈顶层不消费`() {
        val d = BackDispatcher()
        val r = d.dispatch(input(exiting = true, depth = 3))
        assertEquals(BackAction.PopToRoot, r.action)
    }

    @Test
    fun `注销后浮层不再被消费（BL-26）`() {
        val d = BackDispatcher()
        val entry = d.registerOverlay("r-b", OverlayFamily.MODAL, onDismiss = {})
        assertEquals(1, d.overlayCount())
        d.unregisterOverlay(entry.token)
        assertEquals(0, d.overlayCount())
        d.registerRoute("r-b") { }
        assertEquals(BackStage.ROUTE, d.dispatch(input()).stage)
    }

    @Test
    fun `clearOverlaysFor 用于 push 前置清理（BL-27）`() {
        val d = BackDispatcher()
        d.registerOverlay("r-settings", OverlayFamily.BOTTOM_SHEET, onDismiss = {})
        d.registerOverlay("r-settings", OverlayFamily.MODAL, onDismiss = {})
        assertEquals(2, d.clearOverlaysFor("r-settings"))
        assertEquals(0, d.overlayCount())
    }

    @Test
    fun `层销毁未注销 → dev 断言失败（BL-26）`() {
        val d = BackDispatcher()
        d.registerOverlay("r-dead", OverlayFamily.MODAL, onDismiss = {})
        try {
            d.assertNoOrphanOverlays(setOf("r-chat", "r-b"))
            fail("应当抛出浮层泄漏断言")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("浮层泄漏"))
        }
        // 存活集合正确时通过
        d.assertNoOrphanOverlays(setOf("r-dead"))
        assertEquals(1, d.orphanOverlays(setOf("r-chat")).size)
    }

    @Test
    fun `canConsume 与 dispatch 的消费口径一致`() {
        val d = BackDispatcher()
        assertFalse(d.canConsume(input(depth = 1, top = "r-chat")))
        assertTrue(d.canConsume(input(depth = 2)))
        assertTrue(d.canConsume(input(ime = true, depth = 1, top = "r-chat")))
    }
}

// ---------------------------------------------------------------------------
// 3. 命中区判定式（G-11 / G-16 / BP-01 BP-13 BP-18 BP-37 BP-67 BP-73 BP-79）
// ---------------------------------------------------------------------------

class HitTargetsTest {

    @Test
    fun `判定式一：h + 2e 不小于 F`() {
        // 过程行：视觉 32，F=32 → 不需要扩边
        assertTrue(HitTargets.satisfiesFormula(32f, 0f, 32f, 0f))
        // 正文链接：视觉 22，F=44 → 需 e=11
        assertTrue(HitTargets.satisfiesFormula(22f, 11f, 44f, 0f))
        assertFalse(HitTargets.satisfiesFormula(22f, 10f, 44f, 0f))
        // 行级：视觉 36，F=44 → e=4
        assertTrue(HitTargets.satisfiesFormula(36f, 4f, 44f, 0f))
    }

    @Test
    fun `判定式二：2e 不得吃进相邻间距 g`() {
        // 视觉 36、F=44 → e=4（2e=8）；g=8 恰好可用
        assertTrue(HitTargets.satisfiesFormula(36f, 4f, 44f, 8f))
        // g=7 装不下 2e=8 → 不达标（G-11 禁止硬扩边重叠）
        assertFalse(HitTargets.satisfiesFormula(36f, 4f, 44f, 7f))
    }

    @Test
    fun `plan 按类别产出布局高与降级档位`() {
        // 过程行：F=32，视觉 32 → 布局 32、无需扩边
        val process = HitTargets.plan(HitCategory.PROCESS_ROW, visualHeightDp = 32f, horizontalBleedDp = 8f)
        assertEquals(32f, process.layoutHeightDp, 0.001f)
        assertEquals(0f, process.verticalBleedDp, 0.001f)
        assertEquals(8f, process.horizontalBleedDp, 0.001f)
        assertTrue(process.satisfied)
        assertEquals(HitDegradation.NONE, process.degradation)

        // 行级：视觉 36 → 布局 44
        val row = HitTargets.plan(HitCategory.ROW, visualHeightDp = 36f)
        assertEquals(44f, row.hitHeightDp, 0.001f)
        assertEquals(4f, row.verticalBleedDp, 0.001f)
        assertTrue(row.satisfied)

        // 行级装不下：相邻间距 6 < 2e=8 → 降级 ① 加大间距
        val tight = HitTargets.plan(HitCategory.ROW, visualHeightDp = 36f, neighborGapDp = 6f)
        assertFalse(tight.satisfied)
        assertEquals(HitDegradation.INCREASE_GAP, tight.degradation)
    }

    @Test
    fun `requiredGapDp 是降级 ① 的目标值`() {
        assertEquals(12f, HitTargets.requiredGapDp(HitCategory.ROW, 32f), 0.001f)
        assertEquals(8f, HitTargets.requiredGapDp(HitCategory.INPUT, 40f), 0.001f)
        assertEquals(0f, HitTargets.requiredGapDp(HitCategory.PROCESS_ROW, 32f), 0.001f)
    }

    @Test
    fun `命中盒不相交是唯一硬约束（BP-01 BP-79）`() {
        // 两行 32 高、节距 36（间距 4）：命中盒 32 高 → 不相交
        val a = HitRect(0f, 0f, 360f, 32f)
        val b = HitRect(0f, 36f, 360f, 68f)
        assertFalse(HitTargets.intersects(a, b))
        assertNull(HitTargets.firstIntersection(listOf(a, b)))
        HitTargets.assertNoIntersection(listOf(a, b))

        // 贴边不算相交（开区间判定）
        val touching = HitRect(0f, 32f, 360f, 64f)
        assertFalse(HitTargets.intersects(a, touching))

        // 若用 offset/绘制层硬扩边（e=6 越过布局盒）→ 命中盒真的相交 → 判定必须抓住
        val expandedA = HitRect(0f, -6f, 360f, 38f)
        val expandedB = HitRect(0f, 26f, 360f, 70f)
        assertTrue(HitTargets.intersects(expandedA, expandedB))
        try {
            HitTargets.assertNoIntersection(listOf(expandedA, expandedB))
            fail("应当捕捉到命中盒相交")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("命中盒相交"))
        }
    }

    @Test
    fun `并排图标键中心距 40、布局盒不重叠（BP-13 BP-18）`() {
        // 每键 40×40、净距 0：6 键一行 = 240，屏宽 360 放得下
        val keys = (0 until 6).map { i -> HitRect(i * 40f, 0f, (i + 1) * 40f, 40f) }
        HitTargets.assertNoIntersection(keys)
        assertEquals(40f, HitTargets.denseIconCenterDistanceDp(40f, 0f), 0.001f)
        // 靠每键 10 内边距形成视觉间距时，中心距仍是 40（布局盒相邻）
        assertEquals(40f, HitTargets.denseIconCenterDistanceDp(20f, 10f), 0.001f)
    }

    @Test
    fun `Toast 动作命中 44、链接命中 44（BP-37 BP-67）`() {
        val toastAction = HitTargets.plan(HitCategory.INLINE_SEGMENT, visualHeightDp = 20f)
        // INLINE_SEGMENT 的 F 是 36；Toast 动作 P2 要求 ≥44，使用方传更高类别
        assertEquals(36f, toastAction.layoutHeightDp, 0.001f)
        val link = HitTargets.plan(HitCategory.TEXT_LINK, visualHeightDp = 22f)
        assertEquals(44f, link.layoutHeightDp, 0.001f)
        assertTrue(link.satisfied)
    }
}

// ---------------------------------------------------------------------------
// 4. window-class / insets / z / 动效策略（都是纯函数，顺手一起钉住）
// ---------------------------------------------------------------------------

class WindowClassTest {

    @Test
    fun `断点与高度兜底（BL-35 BL-36 BL-37 BL-38）`() {
        assertEquals(PanelWindowClass.COMPACT, windowClassOf(390f, 844f))
        assertEquals(PanelWindowClass.COMPACT, windowClassOf(599f, 900f))
        assertEquals(PanelWindowClass.MEDIUM, windowClassOf(600f, 900f))
        assertEquals(PanelWindowClass.MEDIUM, windowClassOf(839f, 900f))
        assertEquals(PanelWindowClass.EXPANDED, windowClassOf(840f, 900f))
        // 915×412 横屏必须 COMPACT（高度优先，不误判平板）
        assertEquals(PanelWindowClass.COMPACT, windowClassOf(915f, 412f))
        assertEquals(PanelWindowClass.COMPACT, windowClassOf(1280f, 479f))
        assertEquals(PanelWindowClass.EXPANDED, windowClassOf(1280f, 480f))
    }
}

class InsetsMathTest {

    @Test
    fun `底部只垫一次 union 不是相加（BL-40 BL-41）`() {
        // navBars 48 + IME 300 → 300（不是 348）
        assertEquals(300f, PanelInsetMath.unionBottom(48f, 300f, 0f), 0.001f)
        // 有挖孔时取三者最大
        assertEquals(52f, PanelInsetMath.unionBottom(48f, 0f, 52f), 0.001f)
        assertEquals(0f, PanelInsetMath.unionBottom(0f, 0f, 0f), 0.001f)
    }

    @Test
    fun `顶部与左右按 cutout 取最大`() {
        assertEquals(30f, PanelInsetMath.unionTop(24f, 30f), 0.001f)
        assertEquals(24f, PanelInsetMath.unionTop(24f, 0f), 0.001f)
        assertEquals(32f, PanelInsetMath.unionStart(32f), 0.001f)
        assertEquals(0f, PanelInsetMath.unionEnd(0f), 0.001f)
    }

    @Test
    fun `列表底部留白 = 输入区实测高（E-02=A）`() {
        assertEquals(96f, PanelInsetMath.listBottomPadding(96f), 0.001f)
        assertEquals(0f, PanelInsetMath.listBottomPadding(-4f), 0.001f)
    }
}

class ZLadderTest {

    @Test
    fun `z 阶梯常量顺序与保留槽（BL-50）`() {
        assertTrue(PanelZ.ROOT_ROUTE < PanelZ.DOCKED_COLUMN)
        assertTrue(PanelZ.DOCKED_COLUMN < PanelZ.OVERLAY_SLOT) // Toast 槽低于面板栈
        assertTrue(PanelZ.OVERLAY_SLOT < PanelZ.NAV_STACK)
        assertTrue(PanelZ.NAV_STACK < PanelZ.FLOATING)
        assertTrue(PanelZ.FLOATING < PanelZ.PORTAL_MENU) // menu 高于 modal
        assertEquals(0f, PanelZ.ROOT_ROUTE, 0f)
        assertEquals(40f, PanelZ.NAV_STACK, 0f)
        assertEquals(1000f, PanelZ.FLOATING, 0f)
        assertEquals(1100f, PanelZ.PORTAL_MENU, 0f)
        // 保留槽必须未被占用
        val used = setOf(
            PanelZ.ROOT_ROUTE, PanelZ.DOCKED_COLUMN, PanelZ.OVERLAY_SLOT,
            PanelZ.NAV_STACK, PanelZ.FLOATING, PanelZ.PORTAL_MENU,
        )
        assertFalse(PanelZ.RESERVED_11 in used)
        assertFalse(PanelZ.RESERVED_15 in used)
        assertFalse(PanelZ.RESERVED_60 in used)
        assertFalse(PanelZ.RESERVED_70 in used)
    }

    @Test
    fun `遮罩 alpha 唯一值 0_32 且随 progress 线性（BL-19 BL-21 BL-47）`() {
        assertEquals(0.32f, ScrimToken.ALPHA, 0.0001f)
        assertEquals(0.32f, ScrimToken.alphaFor(1f), 0.0001f)
        assertEquals(0.16f, ScrimToken.alphaFor(0.5f), 0.0001f)
        assertEquals(0f, ScrimToken.alphaFor(0f), 0.0001f)
        // 拖拽中超出 [0,1] 会被夹住，不会出现 >0.32 的叠加
        assertEquals(0.32f, ScrimToken.alphaFor(1.7f), 0.0001f)
        assertEquals(0f, ScrimToken.alphaFor(-1f), 0.0001f)
    }
}

class MotionPolicyTest {

    @Test
    fun `只有 disclosure 展开允许高度动画、且仅 240dp 以內（G-10 BP-63）`() {
        assertTrue(M5MotionPolicy.allowsHeightAnimation(M5MotionKind.DISCLOSURE_EXPAND))
        assertFalse(M5MotionPolicy.allowsHeightAnimation(M5MotionKind.OVERLAY_ENTER_EXIT))
        assertFalse(M5MotionPolicy.allowsHeightAnimation(M5MotionKind.CONTENT_FOLD))
        assertFalse(M5MotionPolicy.allowsHeightAnimation(M5MotionKind.SETTINGS_HELP))
        assertFalse(M5MotionPolicy.allowsHeightAnimation(M5MotionKind.CHIP))

        assertEquals(
            M5ExpansionMotion.HEIGHT_SPRING,
            M5MotionPolicy.expansionMotion(M5MotionKind.DISCLOSURE_EXPAND, 200f, reducedMotion = false),
        )
        // >240dp：不许高度动画
        assertEquals(
            M5ExpansionMotion.DIRECT_SWITCH,
            M5MotionPolicy.expansionMotion(M5MotionKind.DISCLOSURE_EXPAND, 300f, reducedMotion = false),
        )
        // 非 disclosure 节点即使很矮也不做高度动画
        assertEquals(
            M5ExpansionMotion.DIRECT_SWITCH,
            M5MotionPolicy.expansionMotion(M5MotionKind.SETTINGS_HELP, 40f, reducedMotion = false),
        )
    }

    @Test
    fun `reduced-motion 下所有过渡瞬时完成（BP-74 BL-48）`() {
        M5MotionKind.entries.forEach { kind ->
            assertEquals(
                M5ExpansionMotion.DIRECT_SWITCH,
                M5MotionPolicy.expansionMotion(kind, 100f, reducedMotion = true),
            )
            assertFalse(M5MotionPolicy.allowsEnterAnimation(kind, reducedMotion = true))
        }
        // 阈值常量与 G-10 一致
        assertEquals(240f, M5MotionPolicy.EXPAND_ANIMATION_THRESHOLD_DP, 0.001f)
        assertEquals(120, M5MotionPolicy.FADE_MAX_MS)
    }
}
