// 验收 ID：CMP-13 CMP-19 CMP-20 CMP-21 CMP-25 CMP-26 CMP-31 CMP-34 CMP-55 CMP-61 CMP-72
//
// composer 命中盒/尺寸几何的 JVM 单测（G-11 唯一硬约束：相邻命中盒不得相交；命中盒 = 布局盒）。
//  * 主键 48×48、`+` 40×40、胶囊 44×≤112 且三键不相交（CMP-13/19/20/21）；
//  * 文件卡重试 196×64 与移除 40×40 不相交（CMP-34）；
//  * 队列三动作 3×40 与预览区不相交（CMP-61）；
//  * 附件轨项 64 + 间距 10 不相交（CMP-31）；卡宽 752 上限（CMP-72）。
package dev.myco.android.ui.m5.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class ComposerGeometryTest {

    private fun assertDisjoint(vararg boxes: HitBox) {
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                assertFalse(
                    "命中盒相交：$i=${boxes[i]} × $j=${boxes[j]}",
                    boxes[i].intersects(boxes[j]),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 工具行（CMP-13/19/20/21）

    @Test
    fun `tools row keys have the spec sizes`() {
        val plan = ComposerGeometry.capsulePlan(
            availableWidthDp = 304f,
            nameWidthDp = 40f,
            reasoningWidthDp = 20f,
            hasReasoning = true,
        )
        val layout = ComposerGeometry.toolsRowLayout(0f, 0f, 304f, plan)
        assertEquals(48f, layout.primary.widthDp, 0.001f)
        assertEquals(48f, layout.primary.heightDp, 0.001f) // G-16 主操作 48
        assertEquals(40f, layout.plus.widthDp, 0.001f)
        assertEquals(40f, layout.plus.heightDp, 0.001f) // G-16 密集图标 40
        assertEquals(44f, layout.capsule.heightDp, 0.001f) // 胶囊高 44
        assertTrue(layout.capsule.widthDp <= ComposerLayout.CAPSULE_MAX_WIDTH_DP + 0.001f)
        assertDisjoint(layout.plus, layout.capsule, layout.primary)
    }

    @Test
    fun `primary stays 48 and right aligned at narrow widths`() {
        // CMP-20/21：状态与窄屏都不改变主键尺寸/位置（键位永不移动）
        val plan = ComposerGeometry.capsulePlan(264f, nameWidthDp = 100f, reasoningWidthDp = 0f, hasReasoning = false)
        val layout = ComposerGeometry.toolsRowLayout(0f, 0f, 264f, plan)
        assertEquals(48f, layout.primary.widthDp, 0.001f)
        assertEquals(216f, layout.primary.left, 0.001f) // 264 − 48
        assertEquals(0f, layout.plus.left, 0.001f) // `+` 贴左，永不移动
        assertDisjoint(layout.plus, layout.capsule, layout.primary)
    }

    @Test
    fun `capsule degrades name then icon when narrow`() {
        // 预算内放不下"名 + 档位" → 只名；名牌都放不下 → 只图标 40（E-07=A）
        val both = ComposerGeometry.capsulePlan(304f, nameWidthDp = 40f, reasoningWidthDp = 20f, hasReasoning = true)
        assertEquals(ModelCapsuleForm.NAME_AND_REASONING, both.form)
        assertTrue(both.showReasoning)

        val nameOnly = ComposerGeometry.capsulePlan(304f, nameWidthDp = 60f, reasoningWidthDp = 20f, hasReasoning = true)
        assertEquals(ModelCapsuleForm.NAME_ONLY, nameOnly.form)
        assertFalse(nameOnly.showReasoning)
        assertTrue(nameOnly.widthDp <= 112f + 0.001f)

        val iconOnly = ComposerGeometry.capsulePlan(304f, nameWidthDp = 200f, reasoningWidthDp = 20f, hasReasoning = true)
        assertEquals(ModelCapsuleForm.ICON_ONLY, iconOnly.form)
        assertEquals(40f, iconOnly.widthDp, 0.001f)
        assertFalse(iconOnly.showName)
    }

    @Test
    fun `capsule never exceeds 112 and tools row fits with no wrap`() {
        // 360dp 屏 → 卡内 304；320dp 屏 → 卡内 264（§3.6 字段 10）
        listOf(304f, 264f).forEach { available ->
            val plan = ComposerGeometry.capsulePlan(available, 60f, 30f, hasReasoning = true)
            assertTrue(plan.widthDp <= ComposerLayout.CAPSULE_MAX_WIDTH_DP + 0.001f)
            assertTrue(ComposerGeometry.toolsRowFits(available, plan.widthDp))
            val layout = ComposerGeometry.toolsRowLayout(0f, 0f, available, plan)
            assertDisjoint(layout.plus, layout.capsule, layout.primary)
        }
    }

    @Test
    fun `card width is capped at 752`() {
        // CMP-72：宽屏 ≤840 以上卡宽仍 ≤752（与 chat 内容列同式）
        fun cardWidth(columnWidth: Float): Float =
            min(columnWidth - ComposerLayout.CARD_HORIZONTAL_MARGIN_DP * 2, ComposerLayout.CARD_MAX_WIDTH_DP)
        assertEquals(752f, cardWidth(1200f), 0.001f)
        assertEquals(752f, cardWidth(840f), 0.001f)
        assertEquals(320f, cardWidth(360f), 0.001f) // 360 屏 → 卡宽 320
    }

    // ---------------------------------------------------------------- 附件轨（CMP-25/26/31）

    @Test
    fun `image rail items do not intersect and keep 10dp gap`() {
        val a = ComposerGeometry.railItemBox(index = 0, isFileCard = false, railTopDp = 0f)
        val b = ComposerGeometry.railItemBox(index = 1, isFileCard = false, railTopDp = 0f)
        val c = ComposerGeometry.railItemBox(index = 2, isFileCard = false, railTopDp = 0f)
        assertEquals(64f, a.widthDp, 0.001f)
        assertEquals(64f, a.heightDp, 0.001f)
        assertEquals(10f, b.left - a.right, 0.001f) // gap 10
        assertDisjoint(a, b, c)
        // 上下 4dp：项顶 = (72−64)/2 = 4
        assertEquals(4f, a.top, 0.001f)
    }

    @Test
    fun `file card retry and remove hit boxes do not intersect`() {
        // CMP-34：重试区 196×64 与移除 40×40 不相交（240 = 196 + 44）
        val layout = ComposerGeometry.fileCardLayout(cardLeftDp = 8f, cardTopDp = 4f)
        assertEquals(240f, layout.card.widthDp, 0.001f)
        assertEquals(64f, layout.card.heightDp, 0.001f)
        assertEquals(196f, layout.retry.widthDp, 0.001f)
        assertEquals(64f, layout.retry.heightDp, 0.001f)
        assertEquals(40f, layout.remove.widthDp, 0.001f)
        assertEquals(40f, layout.remove.heightDp, 0.001f)
        assertFalse(layout.retry.intersects(layout.remove))
        // 重试区左缘贴卡左；移除区贴卡右（距右 4）
        assertEquals(layout.card.left, layout.retry.left, 0.001f)
        assertEquals(layout.card.right - 4f, layout.remove.right, 0.001f)
    }

    @Test
    fun `file card and its hit boxes stay inside the card`() {
        val layout = ComposerGeometry.fileCardLayout(cardLeftDp = 0f, cardTopDp = 0f)
        assertTrue(layout.retry.left >= layout.card.left)
        assertTrue(layout.retry.right <= layout.card.right)
        assertTrue(layout.remove.left >= layout.card.left)
        assertTrue(layout.remove.right <= layout.card.right)
        assertTrue(layout.remove.top >= layout.card.top)
        assertTrue(layout.remove.bottom <= layout.card.bottom)
    }

    // ---------------------------------------------------------------- 队列行（CMP-61）

    @Test
    fun `queue row actions are three 40dp keys that do not intersect preview`() {
        val actions = ComposerGeometry.queueRowActionBoxes(rowRightDp = 304f, rowTopDp = 0f)
        assertEquals(3, actions.size)
        actions.forEach {
            assertEquals(40f, it.widthDp, 0.001f)
            assertEquals(40f, it.heightDp, 0.001f)
        }
        assertDisjoint(actions[0], actions[1], actions[2])
        // 簇贴右端，净距 0（布局盒相邻不重叠）
        assertEquals(304f, actions[2].right, 0.001f)
        assertEquals(304f - 120f, actions[0].left, 0.001f)

        val preview = ComposerGeometry.queueRowPreviewBox(rowLeftDp = 0f, rowTopDp = 0f, rowWidthDp = 304f)
        actions.forEach { assertFalse("预览区不得与动作键相交", preview.intersects(it)) }
    }

    @Test
    fun `queue row actions are vertically centered in the 48dp row`() {
        val actions = ComposerGeometry.queueRowActionBoxes(rowRightDp = 100f, rowTopDp = 10f)
        assertEquals(14f, actions[0].top, 0.001f) // 10 + (48−40)/2
        assertEquals(54f, actions[0].bottom, 0.001f)
    }

    // ---------------------------------------------------------------- HitBox 判定

    @Test
    fun `touching boxes are not intersecting - open interval`() {
        val a = HitBox(0f, 0f, 10f, 10f)
        val b = HitBox(10f, 0f, 20f, 10f)
        assertFalse(a.intersects(b))
        assertTrue(a.intersects(HitBox(9f, 0f, 11f, 10f)))
    }

    @Test
    fun `seat height equals queue block plus card plus breath`() {
        // CMP-02/CMP-09：发布值 H = 队列块 + 卡 + 呼吸 8
        val card = ComposerMetricsCalculator.cardHeightDp(
            lineCount = 3,
            lineHeightDp = 24f,
            hasRail = true,
            hasHint = false,
        )
        val seat = ComposerMetricsCalculator.seatHeightDp(
            lineCount = 3,
            queueCount = 2,
            hasRail = true,
            hasHint = false,
            lineHeightDp = 24f,
        )
        assertEquals(card + ComposerMetricsCalculator.queueBlockHeightDp(2) + 8f, seat, 0.001f)
    }
}
