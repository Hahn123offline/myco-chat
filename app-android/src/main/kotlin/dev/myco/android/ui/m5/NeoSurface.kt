package dev.myco.android.ui.m5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M5 材质原语：新拟物（Neumorphism）。
 *
 * Raised / Inset / Float 三种表面，全部使用 Superellipse 轮廓；
 * 阴影来自同一个 LightRig：亮侧左上、暗侧右下，暗色换色而不是反相。
 *
 * 性能约定（M5.0 实测后回填）：
 * - 阴影用 BlurMaskFilter 绘制，Path 按尺寸缓存；
 * - 一屏真正带模糊阴影的面 ≤8 个，列表行内部不要用。
 */
enum class NeoMaterial { Raised, Inset, Float }

@Composable
fun NeoSurface(
    modifier: Modifier = Modifier,
    radius: Dp = M5Radius.card,
    exponent: Float = 4f,
    material: NeoMaterial = NeoMaterial.Raised,
    fill: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalM5.current.palette
    val shape = remember(radius, exponent) { SuperellipseShape(radius, exponent) }
    val base = fill ?: p.surface
    val brush: Brush = when (material) {
        NeoMaterial.Raised -> Brush.verticalGradient(
            listOf(
                lighten(base, if (p.dark) 0.09f else 0.12f),
                darken(base, if (p.dark) 0.05f else 0.08f),
            ),
        )
        NeoMaterial.Float -> Brush.verticalGradient(
            listOf(lighten(base, 0.08f), darken(base, 0.10f)),
        )
        NeoMaterial.Inset -> Brush.verticalGradient(
            listOf(darken(p.sunken, 0.05f), lerpColor(p.sunken, base, 0.45f)),
        )
    }
    Box(
        modifier
            .then(
                if (material == NeoMaterial.Inset) {
                    Modifier
                } else {
                    Modifier.neoOuterShadow(shape, radius, p, flat = material == NeoMaterial.Float)
                }
            )
            .clip(shape)
            .background(brush, shape)
            .then(if (material == NeoMaterial.Inset) Modifier.neoInsetShadow(shape, radius, p) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 外阴影：亮影左上、暗影右下。Float 只保留单侧投影（更轻，不抢结构面）。
 * offset = r·0.30，blur = offset·2.05（与用户给的 22/45 公式同比例）。
 */
private fun Modifier.neoOuterShadow(
    shape: Shape,
    radius: Dp,
    p: M5Palette,
    flat: Boolean,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    if (outline !is Outline.Generic) return@drawBehind
    val path = outline.path.asAndroidPath()
    val offset = (radius.toPx() * 0.34f).coerceIn(3.dp.toPx(), 13.dp.toPx())
    val wideBlur = offset * 1.75f
    val tightBlur = (offset * 0.65f).coerceAtLeast(1f)
    val lightAlpha = if (p.dark) 0.55f else 0.95f
    val darkWide = if (p.dark) 0.95f else 0.55f
    val darkTight = if (p.dark) 0.60f else 0.42f
    if (!flat) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val save = nc.save()
            nc.translate(-offset * 0.9f, -offset * 0.9f)
            nc.drawPath(path, blurPaint(p.shadowLight.copy(alpha = lightAlpha), wideBlur))
            nc.restoreToCount(save)
        }
    }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val save = nc.save()
        nc.translate(offset * 0.9f, offset)
        nc.drawPath(path, blurPaint(p.shadowDark.copy(alpha = darkWide), wideBlur))
        nc.restoreToCount(save)
    }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val save = nc.save()
        nc.translate(offset * 0.55f, offset * 0.62f)
        nc.drawPath(path, blurPaint(p.shadowDark.copy(alpha = darkTight), tightBlur))
        nc.restoreToCount(save)
    }
}

/** 内阴影：把形状的"外部"平移进内部再模糊，暗侧左上、亮侧右下。 */
private fun Modifier.neoInsetShadow(shape: Shape, radius: Dp, p: M5Palette): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)
    if (outline !is Outline.Generic) {
        onDrawBehind {}
    } else {
        val path = outline.path.asAndroidPath()
        val inverse = android.graphics.Path().apply {
            addRect(
                -size.width, -size.height,
                size.width * 2f, size.height * 2f,
                android.graphics.Path.Direction.CW,
            )
            op(path, android.graphics.Path.Op.DIFFERENCE)
        }
        val offset = (radius.toPx() * 0.26f).coerceIn(2.dp.toPx(), 10.dp.toPx())
        val blur = (offset * 1.15f).coerceAtLeast(1f)
        val darkAlpha = if (p.dark) 0.95f else 0.60f
        val lightAlpha = if (p.dark) 0.32f else 0.95f
        onDrawBehind {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val save = nc.save()
                nc.clipPath(path)
                nc.translate(offset, offset)
                nc.drawPath(inverse, blurPaint(p.shadowDark.copy(alpha = darkAlpha), blur))
                nc.restoreToCount(save)
                val save2 = nc.save()
                nc.clipPath(path)
                nc.translate(-offset, -offset)
                nc.drawPath(inverse, blurPaint(p.shadowLight.copy(alpha = lightAlpha), blur))
                nc.restoreToCount(save2)
            }
        }
    }
}

internal fun blurPaint(color: Color, blur: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = android.graphics.Paint.Style.FILL
        if (blur > 0.5f) {
            maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

internal fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

internal fun lighten(c: Color, t: Float): Color = lerpColor(c, Color.White, t)

internal fun darken(c: Color, t: Float): Color = lerpColor(c, Color.Black, t)
