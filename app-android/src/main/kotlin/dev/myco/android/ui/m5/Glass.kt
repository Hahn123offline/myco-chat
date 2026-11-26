package dev.myco.android.ui.m5

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M5 材质原语：液态玻璃（L0 静态层）。
 *
 * 目前是"tint + 边缘菲涅尔 + 镜面高光 + 投影"的静态近似——用户已确认：
 * 真正的观感要等折射（L2 AGSL / 真 backdrop）上来才成立，这里先保证可达性与层级正确。
 *
 * 点击同样有超椭圆扩散波纹 + 按压反馈（边缘变亮、投影收缩）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = M5Radius.card,
    exponent: Float = 4f,
    tint: Color? = null,
    intensity: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(M5Space.m),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalM5.current.palette
    val budget = LocalNeoShadowBudget.current
    val shape = remember(radius, exponent) { SuperellipseShape(radius, exponent) }
    val topTint = tint ?: p.glassTint
    val bottomTint = tint?.copy(alpha = (tint.alpha * 0.55f).coerceIn(0f, 1f)) ?: p.glassTintBottom
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressProgress(pressed && onClick != null && enabled)
    val ripple = remember { Animatable(0f) }
    var pressPos by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(pressed) {
        if (pressed) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(durationMillis = 420, easing = LinearOutSlowInEasing))
        }
    }
    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                pressPos = down.position
                            }
                        }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topTint, bottomTint)), shape)
            .glassLighting(shape, radius, p, intensity, budget, press)
            .superellipseRipple(shape, press, ripple.value, pressPos, Color.White)
            .padding(contentPadding),
        content = { content() },
    )
}

private fun Modifier.glassLighting(
    shape: androidx.compose.ui.graphics.Shape,
    radius: Dp,
    p: M5Palette,
    intensity: Float,
    budget: Dp?,
    press: Float,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)
    if (outline !is Outline.Generic) {
        onDrawBehind {}
    } else {
        val path = outline.path
        val androidPath = path.asAndroidPath()
        val fullShadowOffset = radius.toPx() * 0.28f
        val budgetPx = budget?.toPx()
        val budgetScale = if (budgetPx != null) {
            val reach = fullShadowOffset * 3.2f
            if (reach > budgetPx) (budgetPx / reach).coerceAtLeast(0.18f) else 1f
        } else 1f
        val shadowOffset = fullShadowOffset * budgetScale * (1f - 0.6f * press)
        val shadowBlur = (fullShadowOffset * 2.2f * budgetScale).coerceAtLeast(1f)
        val shadowAlpha = (if (p.dark) 0.5f else 0.28f) * (1f - 0.35f * press)

        // 边缘菲涅尔：左上亮 → 右下暗（深色用暖光，浅色用白）
        val rimBase = if (p.dark) p.rim else p.glassRim
        val lift = 1f + 0.45f * press
        val rimStrong = (if (p.dark) 0.55f else 0.75f) * intensity * lift
        val rimWeak = (if (p.dark) 0.10f else 0.18f) * intensity * lift
        val rimBrush = Brush.linearGradient(
            colors = listOf(
                rimBase.copy(alpha = rimStrong.coerceAtMost(1f)),
                rimBase.copy(alpha = (rimStrong * 0.55f).coerceAtMost(1f)),
                rimBase.copy(alpha = rimWeak),
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        )
        val softRim = Brush.verticalGradient(
            colors = listOf(
                rimBase.copy(alpha = ((if (p.dark) 0.12f else 0.22f) * intensity * lift).coerceAtMost(1f)),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.55f,
        )
        val specCenter = Offset(size.width * 0.24f, size.height * 0.12f)
        val specRadius = size.minDimension * 0.85f
        val specular = Brush.radialGradient(
            colors = listOf(
                p.glassSpecular.copy(alpha = ((if (p.dark) 0.16f else 0.55f) * intensity * lift).coerceAtMost(1f)),
                p.glassSpecular.copy(alpha = 0f),
            ),
            center = specCenter,
            radius = specRadius,
        )

        onDrawBehind {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val save = nc.save()
                nc.translate(shadowOffset * 0.35f, shadowOffset)
                nc.drawPath(androidPath, blurPaint(p.shadowDark.copy(alpha = shadowAlpha), shadowBlur))
                nc.restoreToCount(save)
            }
            drawPath(
                path = path,
                brush = softRim,
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = path,
                brush = rimBrush,
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                brush = specular,
                radius = specRadius,
                center = specCenter,
            )
        }
    }
}
