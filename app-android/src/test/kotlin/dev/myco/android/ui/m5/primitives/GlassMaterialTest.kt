package dev.myco.android.ui.m5.primitives

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玻璃材质 token / API 判定 / 结构边界的纯逻辑测试（D-73 / D-74 / G-21）。
 *
 * 这些判据是"玻璃到底该不该出现"的唯一真源：渲染层只是读结果，不各自重算。
 */
class GlassMaterialTest {

    @Test
    fun `D73 token 数值被固化`() {
        val m = GlassMaterial.Overlay
        assertEquals(24.dp, m.blur)
        // Color alpha 量化到 8bit：0.04 → 10/255 = 0.0392（视觉上就是 D-73 的 4%）
        assertEquals(GlassMaterial.DEFAULT_TINT_ALPHA, m.tint.alpha, 0.002f)
        assertEquals(10, (m.tint.alpha * 255f).toInt())
        assertEquals(3.dp, m.refract)
        assertEquals(6.dp, m.band)
        assertEquals(1f, m.dispersionPx)
        assertEquals(0.35f, m.rim)
        assertEquals(0.25f, m.innerShadow)
        assertEquals(26.dp, m.corner)
    }

    @Test
    fun `modal 档位在 D73 的 32-40dp 范围内`() {
        val blur = GlassMaterial.Modal.blur.value
        assertTrue("modal blur=$blur 应在 32..40", blur in 32f..40f)
    }

    @Test
    fun `无折射对照档只改折射与色散`() {
        val a = GlassMaterial.noRefraction()
        assertEquals(0.dp, a.refract)
        assertEquals(0f, a.dispersionPx)
        assertEquals(GlassMaterial.Overlay.blur, a.blur)
        assertEquals(GlassMaterial.Overlay.tint, a.tint)
        assertEquals(GlassMaterial.Overlay.rim, a.rim)
        assertEquals(GlassMaterial.Overlay.innerShadow, a.innerShadow)
    }

    @Test
    fun `API 33 以下不做玻璃（D74 走 A 不透明）`() {
        assertFalse(isGlassSupported(sdkInt = 26, hardwareAccelerated = true))
        assertFalse(isGlassSupported(sdkInt = 31, hardwareAccelerated = true))
        assertFalse(isGlassSupported(sdkInt = 32, hardwareAccelerated = true))
        assertEquals(
            GlassAvailability.API_TOO_LOW,
            glassAvailability(sdkInt = 32, hardwareAccelerated = true),
        )
    }

    @Test
    fun `软件渲染不做玻璃（RenderEffect 被忽略）`() {
        assertEquals(
            GlassAvailability.SOFTWARE_RENDERING,
            glassAvailability(sdkInt = 35, hardwareAccelerated = false),
        )
        assertFalse(isGlassSupported(sdkInt = 35, hardwareAccelerated = false))
    }

    @Test
    fun `API 33 且硬件加速才是 FULL`() {
        assertTrue(isGlassSupported(sdkInt = 33, hardwareAccelerated = true))
        assertEquals(
            GlassAvailability.FULL,
            glassAvailability(sdkInt = 35, hardwareAccelerated = true),
        )
    }

    @Test
    fun `着色器构建失败降级 A 而不是裸模糊`() {
        assertEquals(
            GlassAvailability.SHADER_FAILED,
            glassAvailability(sdkInt = 35, hardwareAccelerated = true, shaderFailed = true),
        )
    }

    @Test
    fun `独立 window 结构上不允许捕获玻璃（G21）`() {
        // 现有 M5Sheet/M5Modal/M5Lightbox 都是 Dialog（独立 window）
        assertTrue(GlassStructuralLimits.usesIndependentWindow(usesDialog = true))
        assertFalse(GlassStructuralLimits.canCaptureBackdrop(usesDialog = true))
        assertTrue(GlassStructuralLimits.canCaptureBackdrop(usesDialog = false))
        assertTrue(GlassStructuralLimits.INDEPENDENT_WINDOW_REASON.contains("结构不允许"))
    }

    @Test
    fun `四角半径按浮层形态取零（无自由边不折射）`() {
        val sheet = GlassCorners.sheet(26.dp)
        assertEquals(26.dp, sheet.topStart)
        assertEquals(26.dp, sheet.topEnd)
        assertEquals(0.dp, sheet.bottomStart)
        assertEquals(0.dp, sheet.bottomEnd)

        val drawer = GlassCorners.drawer(26.dp)
        assertEquals(0.dp, drawer.topStart)
        assertEquals(26.dp, drawer.topEnd)
        assertEquals(0.dp, drawer.bottomStart)
        assertEquals(26.dp, drawer.bottomEnd)
    }

    @Test
    fun `形状指数单一真源：四角相等才允许超椭圆`() {
        // 当前产品 clip = RoundedCornerShape → shapeExponent 必须 2（shader uShapeN 同源）
        assertEquals(2f, GlassCorners.uniform(26.dp).effectiveShapeExponent)
        // 设计系统目标：四角相等 + exponent 4 → 超椭圆（SuperellipseShape），shader 也传 4
        val squircle = GlassCorners.uniform(26.dp).copy(shapeExponent = 4f)
        assertEquals(4f, squircle.effectiveShapeExponent)
        // 四角不相等时 SuperellipseShape 表达不了 → 强制退回圆角矩形（避免 clip 与 SDF 不同曲线）
        val mixed = GlassCorners(topStart = 26.dp, topEnd = 12.dp).copy(shapeExponent = 4f)
        assertEquals(2f, mixed.effectiveShapeExponent)
    }
}
