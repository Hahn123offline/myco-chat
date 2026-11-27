// 验收 ID（JVM 单测覆盖）：BL-01 BL-02 BL-03 BL-04 BL-05（按钮入口 push / pop 回来源）
//
// base.layout.panel-graph 的 L1/L2/L3 + 深链祖先链合成 + §1.5.4 命令入口。
// 只测纯数据（PanelGraph 零 Compose/Android 依赖），不改栈的图校验与栈不变量分开断言。
package dev.myco.android.ui.m5.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun graphRoute(
    id: String,
    panel: String = id,
    from: String? = null,
    edge: Edge? = null,
    width: WidthSpec = WidthSpec.Full,
    coverMode: CoverMode = CoverMode.OVER_OPAQUE,
) = PanelRoute(
    routeId = id,
    panelId = panel,
    from = from,
    edge = edge,
    widthSpec = width,
    coverMode = coverMode,
)

private val gChat = graphRoute("r-chat", "chat")
private val gSessions = graphRoute(
    "r-sessions",
    "sessions",
    from = "chat",
    edge = Edge.LEFT,
    width = WidthSpec.MinMax(),
    coverMode = CoverMode.OVER_DIM,
)
private val gSettings = graphRoute("r-settings", "settings", from = "chat", edge = Edge.RIGHT)

private fun graphStackOf(vararg routes: PanelRoute) = PanelStackState(routes = routes.toList())

// ---------------------------------------------------------------------------
// BL-01：内置图通过 L2/L3；5 类坏图 → L2 回退 / L3 fail-fast
// ---------------------------------------------------------------------------

class PanelGraphBl01Test {

    @Test
    fun `内置 chat-profile v1 通过 L2 与 L3`() {
        val graph = PanelGraphs.chatProfileV1()
        assertEquals("chat-profile/v1", graph.schemaVersion)
        assertEquals("chat", graph.root)
        assertEquals(setOf("chat", "sessions", "settings", "bin"), graph.panelIds)
        assertTrue(graph.schemaSupported)
        assertTrue(PanelGraph.SUPPORTED_SCHEMA_VERSIONS.contains(graph.schemaVersion))
        // L3：无结构违规（根存在 / 可达 / 无自环 / 无重复边 / to 全在白名单）
        assertEquals(emptyList<String>(), graph.structureViolations())
    }

    @Test
    fun `坏图1：缺根 → L3 fail-fast`() {
        val bad = PanelGraph(
            schemaVersion = "chat-profile/v1",
            root = "ghost-root",
            specs = listOf(chatLikeSpec("chat")),
        )
        assertTrue(bad.structureViolations().any { it.contains("根不存在") })
        try {
            bad.checkStructure()
            fail("缺根必须 L3 fail-fast")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("L3 fail-fast"))
        }
    }

    @Test
    fun `坏图2：自环 → L3 fail-fast`() {
        val bad = PanelGraph(
            schemaVersion = "chat-profile/v1",
            root = "chat",
            specs = listOf(
                chatLikeSpec(
                    "chat",
                    edges = listOf(PanelEdge("chat", Edge.LEFT, "chat")),
                ),
            ),
        )
        assertTrue(bad.structureViolations().any { it.contains("自环") })
        try {
            bad.checkStructure()
            fail("自环必须 L3 fail-fast")
        } catch (expected: IllegalStateException) {
            // ok
        }
    }

    @Test
    fun `坏图3：重复 (from,edge) → L3 fail-fast`() {
        val bad = PanelGraph(
            schemaVersion = "chat-profile/v1",
            root = "chat",
            specs = listOf(
                chatLikeSpec(
                    "chat",
                    edges = listOf(
                        PanelEdge("chat", Edge.LEFT, "sessions"),
                        PanelEdge("chat", Edge.LEFT, "settings"),
                    ),
                ),
                chatLikeSpec("sessions"),
                chatLikeSpec("settings"),
            ),
        )
        assertTrue(bad.structureViolations().any { it.contains("重复 (from,edge)") })
        try {
            bad.checkStructure()
            fail("重复边必须 L3 fail-fast")
        } catch (expected: IllegalStateException) {
            // ok
        }
    }

    @Test
    fun `坏图4：to 不在白名单 → L3 fail-fast`() {
        val bad = PanelGraph(
            schemaVersion = "chat-profile/v1",
            root = "chat",
            specs = listOf(
                chatLikeSpec(
                    "chat",
                    edges = listOf(PanelEdge("chat", Edge.LEFT, "ghost-target")),
                ),
            ),
        )
        assertTrue(bad.structureViolations().any { it.contains("不在白名单") })
        try {
            bad.checkStructure()
            fail("未知 target 必须 L3 fail-fast")
        } catch (expected: IllegalStateException) {
            // ok
        }
    }

    @Test
    fun `坏图5：未知 schemaVersion → L2 回退内置图（不白屏不崩）`() {
        // 未知版本不抛异常：回退内置默认图
        val fallback = PanelGraphs.load("chat-profile/v99")
        assertEquals(PanelGraphs.CHAT_PROFILE_V1, fallback.schemaVersion)
        assertEquals(emptyList<String>(), fallback.structureViolations())
        // 未知版本的裸图本身 schemaSupported=false（调用方据此回退）
        val raw = PanelGraph("chat-profile/v99", "chat", listOf(chatLikeSpec("chat")))
        assertTrue(!raw.schemaSupported)
        assertTrue(raw.structureViolations().any { it.contains("未知 schemaVersion") })
    }
}

private fun chatLikeSpec(specId: String, edges: List<PanelEdge> = emptyList()) = PanelSpec(
    specId = specId,
    kind = PanelKind.OTHER,
    edges = edges,
)

// ---------------------------------------------------------------------------
// BL-02：非法边请求被拒且栈不变
// ---------------------------------------------------------------------------

class PanelGraphBl02Test {

    private val graph = PanelGraphs.chatProfileV1()

    @Test
    fun `非法边 bin-RIGHT 到 settings 被拒（bin 是叶子）`() {
        val result = graph.validate(OpenRequest(from = "bin", edge = Edge.RIGHT, target = "settings"))
        assertTrue(result is GraphResult.Rejected)
        assertEquals(GraphRejectReason.EDGE_NOT_ALLOWED, (result as GraphResult.Rejected).reason)
    }

    @Test
    fun `非法边 chat-TOP 到 bin 被拒（方向不在图里）`() {
        val result = graph.validate(OpenRequest(from = "chat", edge = Edge.TOP, target = "bin"))
        assertTrue(result is GraphResult.Rejected)
        assertEquals(GraphRejectReason.EDGE_NOT_ALLOWED, (result as GraphResult.Rejected).reason)
    }

    @Test
    fun `edge 指向不匹配（chat-LEFT 到 settings）被拒`() {
        val result = graph.validate(OpenRequest(from = "chat", edge = Edge.LEFT, target = "settings"))
        assertTrue(result is GraphResult.Rejected)
        assertEquals(GraphRejectReason.EDGE_NOT_ALLOWED, (result as GraphResult.Rejected).reason)
    }

    @Test
    fun `自环、未知 target、未知来源都被拒`() {
        assertEquals(
            GraphRejectReason.SELF_LOOP,
            (graph.validate(OpenRequest(from = "chat", edge = null, target = "chat")) as GraphResult.Rejected).reason,
        )
        assertEquals(
            GraphRejectReason.UNKNOWN_TARGET,
            (graph.validate(OpenRequest(from = "chat", edge = null, target = "ghost")) as GraphResult.Rejected).reason,
        )
        assertEquals(
            GraphRejectReason.UNKNOWN_SOURCE,
            (graph.validate(OpenRequest(from = "ghost", edge = null, target = "settings")) as GraphResult.Rejected).reason,
        )
    }

    @Test
    fun `params schema：未知字段拒绝、声明过的字段放行`() {
        val ok = graph.validate(
            OpenRequest(from = "chat", edge = Edge.RIGHT, target = "settings", params = mapOf("tab" to "advanced")),
        )
        assertTrue(ok.accepted)
        val bad = graph.validate(
            OpenRequest(from = "chat", edge = Edge.RIGHT, target = "settings", params = mapOf("bogus" to 1)),
        )
        assertEquals(GraphRejectReason.PARAM_NOT_ALLOWED, (bad as GraphResult.Rejected).reason)
    }

    @Test
    fun `白名单内的合法请求放行（含按钮入口 from=null）`() {
        assertTrue(graph.validate(OpenRequest(from = "chat", edge = Edge.LEFT, target = "sessions")).accepted)
        assertTrue(graph.validate(OpenRequest(from = "chat", edge = Edge.RIGHT, target = "settings")).accepted)
        assertTrue(graph.validate(OpenRequest(from = "settings", edge = Edge.RIGHT, target = "bin")).accepted)
        // 按钮/命令入口不要求 from/edge
        assertTrue(graph.validate(OpenRequest(from = null, edge = null, target = "settings")).accepted)
    }

    @Test
    fun `非法请求不 push：栈深与栈顶不变`() {
        var stack = graphStackOf(gChat, gSettings)
        val before = stack.routes.toList()
        val request = OpenRequest(from = "chat", edge = Edge.TOP, target = "bin")
        // L1 拒绝 → host 不调用 push（BL-02 判据：栈不变、零转场）
        if (graph.validate(request).accepted) {
            stack = stack.push(graph.routeFor(request, "r-illegal")!!).resultState
        }
        assertEquals(2, stack.depth)
        assertEquals(before, stack.routes)
    }

    @Test
    fun `routeFor 只为合法请求物化 PanelRoute`() {
        assertNull(graph.routeFor(OpenRequest(from = "chat", edge = Edge.TOP, target = "bin"), "r-x"))
        val route = graph.routeFor(OpenRequest(from = "chat", edge = Edge.LEFT, target = "sessions"), "r-sessions")
        assertNotNull(route)
        assertEquals("sessions", route!!.panelId)
        assertEquals("chat", route.from)
        assertEquals(Edge.LEFT, route.edge)
        assertEquals(CoverMode.OVER_DIM, route.coverMode)
        assertEquals(Cover.PARTIAL, route.cover)
        assertEquals(SwipeDirection.OUT, route.swipe?.direction)
    }
}

// ---------------------------------------------------------------------------
// BL-03：深链按图补祖先链（一次 commit [root,…,target]；不可达 → 拒绝整条）
// ---------------------------------------------------------------------------

class PanelGraphBl03Test {

    private val graph = PanelGraphs.chatProfileV1()

    @Test
    fun `深链 bin 得到 chat、settings、bin 三节链`() {
        val chain = graph.deepLinkChain("bin")
        assertNotNull(chain)
        assertEquals(listOf("chat", "settings", "bin"), chain!!.map { it.panelId })
        // 链头是根（无 from/edge）
        assertNull(chain[0].from)
        assertNull(chain[0].edge)
        // 后两节来自 NONE 边（按钮入口）→ §1.5.5-3：edge 取 null（转场淡入）
        assertEquals("chat", chain[1].from)
        assertNull(chain[1].edge)
        assertEquals(SwipeOpenSource.NONE, chain[1].openSource)
        assertEquals("settings", chain[2].from)
        assertNull(chain[2].edge)
    }

    @Test
    fun `深链 sessions 得到 chat、sessions 两节链且滑动边保留方向`() {
        val chain = graph.deepLinkChain("sessions")!!
        assertEquals(listOf("chat", "sessions"), chain.map { it.panelId })
        assertEquals(Edge.LEFT, chain[1].edge)
        assertEquals(SwipeOpenSource.CONTENT_DRAG, chain[1].openSource)
    }

    @Test
    fun `深链 target=root → 只有根一节`() {
        val chain = graph.deepLinkChain("chat")!!
        assertEquals(1, chain.size)
        assertEquals("chat", chain[0].panelId)
    }

    @Test
    fun `深链不可达与未知 target 都返回 null（调用方拒绝整条，栈不变）`() {
        assertNull(graph.deepLinkChain("ghost"))
        // 构造一张 root 与孤儿节点不连通的图（绕过 L3，直接测可达性判定）
        val disconnected = PanelGraph(
            schemaVersion = "chat-profile/v1",
            root = "chat",
            specs = listOf(chatLikeSpec("chat"), chatLikeSpec("orphan")),
        )
        assertNull(disconnected.deepLinkChain("orphan"))
    }

    @Test
    fun `chainRoutes 物化三层、末节带 params、中间层不带`() {
        val routes = graph.chainRoutes("bin", params = mapOf("kind" to "archived"))
        assertNotNull(routes)
        assertEquals(listOf("chat", "settings", "bin"), routes!!.map { it.panelId })
        assertEquals("chat", routes[0].panelId)
        // 每节的 from 是链上的上一节（settings 的 from = chat，bin 的 from = settings）
        assertEquals("chat", routes[1].from)
        assertEquals("settings", routes[2].from)
        // 中间层不带参数（避免污染历史）
        assertTrue(routes[1].params.isEmpty())
        assertEquals(mapOf("kind" to "archived"), routes[2].params)
        assertEquals(CoverMode.OVER_OPAQUE, routes[2].coverMode)
        // 参数非法 → 整条拒绝
        assertNull(graph.chainRoutes("bin", params = mapOf("bogus" to 1)))
        assertNull(graph.chainRoutes("ghost"))
    }

    @Test
    fun `链压栈后 depth 0-1-2 且返回可回来源`() {
        val routes = graph.chainRoutes("bin")!!
        var stack = graphStackOf(routes[0])
        for (route in routes.drop(1)) {
            stack = stack.push(route).resultState
            stack = stack.onEnterSettled(stack.enteringRouteId ?: route.routeId)
        }
        assertEquals(3, stack.depth)
        assertEquals("bin", stack.top?.panelId)
        // 返回一次 → settings（from 链正确）
        val popped = stack.pop().resultState
        assertEquals("settings", popped.top?.panelId)
        assertEquals("settings", popped.routes[2 - 1].panelId)
    }
}

// ---------------------------------------------------------------------------
// §1.5.4 命令入口（不要求图边，同帧 pop+push / pop+业务选择）
// ---------------------------------------------------------------------------

class PanelCommandEntryTest {

    @Test
    fun `closeThenPush 同帧 pop 抽屉 + push 设置`() {
        val stack = graphStackOf(gChat, gSessions)
        val result = stack.closeThenPush(gSettings)
        assertTrue(result.applied)
        assertEquals(2, result.resultState.depth)
        assertEquals("settings", result.resultState.top?.panelId)
        assertEquals(listOf("chat", "settings"), result.resultState.routes.map { it.panelId })
        val effects = (result as StackResult.Applied).effects
        // 抽屉必须同帧关闭并从 dispatcher 注销（BL-27）
        assertTrue(effects.any { it is StackEffect.DismissOverlays && it.ownerRouteId == "r-sessions" })
        assertTrue(effects.any { it is StackEffect.Exit && it.routeId == "r-sessions" })
        assertTrue(effects.any { it is StackEffect.Enter && it.routeId == "r-settings" })
    }

    @Test
    fun `closeThenSelect 同帧 pop 抽屉并返回 sessionId`() {
        val stack = graphStackOf(gChat, gSessions)
        val (result, sessionId) = stack.closeThenSelect("session-42")
        assertEquals("session-42", sessionId)
        assertTrue(result.applied)
        assertEquals(1, result.resultState.depth)
        assertEquals("chat", result.resultState.top?.panelId)
    }

    @Test
    fun `命令入口在栈太浅时忽略（根层没有可关的抽屉）`() {
        val stack = graphStackOf(gChat)
        assertTrue(!stack.closeThenPush(gSettings).applied)
        assertTrue(!stack.closeThenSelect("s").first.applied)
    }
}
