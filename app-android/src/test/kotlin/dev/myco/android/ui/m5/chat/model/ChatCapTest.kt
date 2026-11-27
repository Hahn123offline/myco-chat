// CHAT-07, CHAT-08, CHAT-50
package dev.myco.android.ui.m5.chat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ChatCap`（§1.6 封顶换算 + 键盘开合不变）+ `ProcessCapFade`（§3.6 字段 6 / CHAT-08 的 24dp 遮罩几何）。
 *
 * 注：为防止新增文件超出 P4 写作用域白名单，`ProcessCapFade` 的断言并入本文件（同属"过程组封顶"几何）。
 * CHAT-07 的最终判定仍需真机量高（见规格 §4），这里锁定公式与换算表。
 */
class ChatCapTest {

    // ---------------------------------------------------------------- §1.6 换算表

    @Test
    fun `360x800 gives 300dp`() {
        // 主流档：800 − 24(insets) − 56(顶栏) − 120(composer) = 600 → 0.5×600 = 300
        val aStable = ChatCapSpec.estimateAStableViewportHeightDp(
            screenHeightDp = 800f,
            systemInsetDp = 24f,
            topBarDp = 56f,
            composerHeightDp = 120f,
        )
        assertEquals(600f, aStable, 0.01f)
        assertEquals(300f, ChatCap(aStable).capDp, 0.01f)
    }

    @Test
    fun `412x915 gives 357dp within device tolerance`() {
        // 大屏档：915 − 24 − 56 − 120 = 715 → 357.5（规格表写 357；CHAT-07 容差 ±4dp）
        val aStable = ChatCapSpec.estimateAStableViewportHeightDp(915f, 24f, 56f, 120f)
        assertEquals(715f, aStable, 0.01f)
        assertEquals(357f, ChatCap(aStable).capDp, 4f)
    }

    @Test
    fun `360x640 small screen gives 232dp`() {
        // 小屏档：640 − 24 − 56 − 96 = 464 → 232
        val aStable = ChatCapSpec.estimateAStableViewportHeightDp(640f, 24f, 56f, 96f)
        assertEquals(464f, aStable, 0.01f)
        assertEquals(232f, ChatCap(aStable).capDp, 0.01f)
    }

    @Test
    fun `cap takes absolute side at 400dp when AStable is large`() {
        // 平板/横屏：AStable ≥ 800 → min(400, ≥400) = 400（取绝对值侧）
        assertEquals(400f, ChatCap(800f).capDp, 0.01f)
        assertEquals(400f, ChatCap(1200f).capDp, 0.01f)
        assertEquals(ChatCapSpec.MAX_CAP_DP, ChatCap(50_000f).capDp, 0.01f)
    }

    @Test
    fun `landscape shrinks cap proportional to AStable`() {
        // CHAT-50：横屏 AStable 变小 → cap ≈118–140dp（这里 236/2 = 118）
        val aStable = ChatCapSpec.estimateAStableViewportHeightDp(412f, 24f, 56f, 96f)
        assertEquals(236f, aStable, 0.01f)
        val cap = ChatCap(aStable).capDp
        assertEquals(118f, cap, 0.01f)
        assertTrue(cap in 118f..140f)
    }

    @Test
    fun `keyboard visibility never changes cap`() {
        // §1.6：键盘开合不改变 cap（用 IME 收起的 AStable 计一次，组合期缓存）
        val cap = ChatCap(600f)
        assertEquals(300f, cap.capDp, 0.01f)
        assertSame(cap, cap.withImeVisibility(imeVisible = true))
        assertSame(cap, cap.withImeVisibility(imeVisible = false))
        assertEquals(300f, cap.withImeVisibility(true).capDp, 0.01f)
    }

    @Test
    fun `degenerate AStable clamps to zero instead of negative`() {
        assertEquals(0f, ChatCap(0f).capDp, 0.01f)
        assertEquals(0f, ChatCap(-120f).capDp, 0.01f)
        assertEquals(
            0f,
            ChatCapSpec.estimateAStableViewportHeightDp(300f, 24f, 56f, 400f),
            0.01f,
        )
    }

    @Test
    fun `screen change recomputes cap non animated`() {
        val portrait = ChatCap(600f)
        val landscape = portrait.withScreenChange(
            screenHeightDp = 412f,
            systemInsetDp = 24f,
            topBarDp = 56f,
            composerHeightDp = 96f,
        )
        assertFalse(portrait == landscape)
        assertEquals(118f, landscape.capDp, 0.01f)
    }

    // ---------------------------------------------------------------- 24dp mask 几何

    @Test
    fun `no fade when content fits viewport`() {
        // §3.6 字段 10：内容不足一屏 → 无 mask、无滚动条
        val fits = ProcessCapFade.geometry(contentHeightDp = 200f, viewportHeightDp = 280f, scrollOffsetDp = 0f)
        assertFalse(fits.hasFade)
        assertEquals(FadeEdges.NONE, fits.edges)
        assertTrue(fits.stops.isEmpty())
        assertEquals(1f, fits.alphaAt(0f), 0.001f)

        // 到边判定阈值 1dp：只多 1dp 仍视为到边（不算可滚）
        val oneDpOver = ProcessCapFade.geometry(contentHeightDp = 281f, viewportHeightDp = 280f, scrollOffsetDp = 0f)
        assertEquals(FadeEdges.NONE, oneDpOver.edges)
    }

    @Test
    fun `bottom only fade while at top`() {
        val geometry = ProcessCapFade.geometry(
            contentHeightDp = 1_000f,
            viewportHeightDp = 280f,
            scrollOffsetDp = 0f,
        )
        assertEquals(FadeEdges.BOTTOM, geometry.edges)
        assertEquals(0f, geometry.topFadeDp, 0.01f)
        assertEquals(24f, geometry.bottomFadeDp, 0.01f)
        assertEquals(
            listOf(FadeStop(256f, 1f), FadeStop(280f, 0f)),
            geometry.stops,
        )
    }

    @Test
    fun `top only fade when at bottom`() {
        val geometry = ProcessCapFade.geometry(
            contentHeightDp = 1_000f,
            viewportHeightDp = 280f,
            scrollOffsetDp = 720f, // 到底
        )
        assertEquals(FadeEdges.TOP, geometry.edges)
        assertEquals(24f, geometry.topFadeDp, 0.01f)
        assertEquals(0f, geometry.bottomFadeDp, 0.01f)
        assertEquals(listOf(FadeStop(0f, 0f), FadeStop(24f, 1f)), geometry.stops)
    }

    @Test
    fun `both ends produce four stops`() {
        // CHAT-08：两端可滚 → 规格"合成四段"（透明→不透明→不透明→透明）
        val geometry = ProcessCapFade.geometry(
            contentHeightDp = 1_000f,
            viewportHeightDp = 280f,
            scrollOffsetDp = 500f,
        )
        assertEquals(FadeEdges.BOTH, geometry.edges)
        assertEquals(4, geometry.stops.size)
        assertEquals(
            listOf(FadeStop(0f, 0f), FadeStop(24f, 1f), FadeStop(256f, 1f), FadeStop(280f, 0f)),
            geometry.stops,
        )
        assertEquals(24f, geometry.topFadeDp, 0.01f)
        assertEquals(24f, geometry.bottomFadeDp, 0.01f)
        assertEquals(232f, geometry.opaqueHeightDp, 0.01f)

        // alpha 曲线：两端透明、中间不透明，24dp 线性渐变
        assertEquals(0f, geometry.alphaAt(0f), 0.001f)
        assertEquals(0.5f, geometry.alphaAt(12f), 0.001f)
        assertEquals(1f, geometry.alphaAt(140f), 0.001f)
        assertEquals(0.5f, geometry.alphaAt(268f), 0.001f)
        assertEquals(0f, geometry.alphaAt(280f), 0.001f)
    }

    @Test
    fun `tiny viewport splits fade bands in half`() {
        // 规格未给视口 <48dp 时两段渐变的边界；实现按对半切（我方取值，P4 校准）避免负中间段
        val geometry = ProcessCapFade.geometry(
            contentHeightDp = 100f,
            viewportHeightDp = 40f,
            scrollOffsetDp = 30f,
        )
        assertEquals(FadeEdges.BOTH, geometry.edges)
        assertEquals(20f, geometry.topFadeDp, 0.01f)
        assertEquals(20f, geometry.bottomFadeDp, 0.01f)
        assertEquals(0f, geometry.opaqueHeightDp, 0.01f)
        assertEquals(ProcessCapFade.FADE_DP, geometry.fadeDp, 0.001f)
    }
}
