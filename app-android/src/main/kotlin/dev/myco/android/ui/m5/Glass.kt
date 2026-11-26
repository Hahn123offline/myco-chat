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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M5 材质原语：液态玻璃（L0 静态层）。
 *
 * 三件事一起做，禁止只画一条亮边：
 * 1. 边缘菲涅尔 —— 沿轮廓的多段衰减（8dp 内缘柔光 + 1.2dp 渐变边），按光向调强度；
 * 2. 镜面高光 —— 左上角一片径向高光（低 alpha，不被单色渐变替代）；
 * 3. 透射 tint —— 垂直渐变让背景色透过来，暗色叠一点暖光。
 *
 * 真背景模糊（L1）和 AGSL 折射（L2）在 M5.0 spike 里单独验证；此处的 L0 是一等视觉，不是占位。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = M5Radius.card,
    exponent: Float = 4f,
    tint: Color? = null,
    intensity: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(M5Space.m),
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalM5.current.palette
    val shape = remember(radius, exponent) { SuperellipseShape(radius, exponent) }
    val topTint = tint ?: p.glassTint
    val bottomTint = tint?.copy(alpha = (tint.alpha * 0.55f).coerceIn(0f, 1f)) ?: p.glassTintBottom
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topTint, bottomTint)), shape)
            .glassLighting(shape, radius, p, intensity)
            .padding(contentPadding),
        content = { content() },
    )
}

private fun Modifier.glassLighting(
    shape: androidx.compose.ui.graphics.Shape,
    radius: Dp,
    p: M5Palette,
    intensity: Float,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)
        if (outline !is Outline.Generic) {
            onDrawBehind {}
        } else {
            val path = outline.path
            val androidPath = path.asAndroidPath()
            val shadowOffset = radius.toPx() * 0.28f
            val shadowBlur = (shadowOffset * 2.2f).coerceAtLeast(1f)

            // 边缘菲涅尔：左上亮 → 右下暗（深色模式用暖光，浅色用白）
            val rimBase = if (p.dark) p.rim else p.glassRim
            val rimStrong = (if (p.dark) 0.55f else 0.75f) * intensity
            val rimWeak = (if (p.dark) 0.10f else 0.18f) * intensity
            val rimBrush = Brush.linearGradient(
                colors = listOf(
                    rimBase.copy(alpha = rimStrong),
                    rimBase.copy(alpha = rimStrong * 0.55f),
                    rimBase.copy(alpha = rimWeak),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
            val softRim = Brush.verticalGradient(
                colors = listOf(rimBase.copy(alpha = (if (p.dark) 0.12f else 0.22f) * intensity), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.55f,
            )
            val specCenter = Offset(size.width * 0.24f, size.height * 0.12f)
            val specRadius = size.minDimension * 0.85f
            val specular = Brush.radialGradient(
                colors = listOf(
                    p.glassSpecular.copy(alpha = (if (p.dark) 0.16f else 0.55f) * intensity),
                    p.glassSpecular.copy(alpha = 0f),
                ),
                center = specCenter,
                radius = specRadius,
            )

            onDrawBehind {
                // 外投影：玻璃要浮起来，但比结构面轻
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    val save = nc.save()
                    nc.translate(shadowOffset * 0.35f, shadowOffset)
                    nc.drawPath(
                        androidPath,
                        blurPaint(p.shadowDark.copy(alpha = if (p.dark) 0.5f else 0.28f), shadowBlur),
                    )
                    nc.restoreToCount(save)
                }
                // 内缘柔光（大范围、低 alpha，模拟折射后的边缘亮度）
                drawPath(
                    path = path,
                    brush = softRim,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
                )
                // 1.2dp 渐变边（不是等宽灰线）
                drawPath(
                    path = path,
                    brush = rimBrush,
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
                )
                // 镜面高光块
                drawCircle(
                    brush = specular,
                    radius = specRadius,
                    center = specCenter,
                )
            }
        }
    }
