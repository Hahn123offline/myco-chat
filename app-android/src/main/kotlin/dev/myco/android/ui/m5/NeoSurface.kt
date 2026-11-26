package dev.myco.android.ui.m5

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M5 材质原语：新拟物（Neumorphism）。
 *
 * 规则（2026-11-27 拍板）：
 * 1. 投影颜色取自面板自身颜色（粉气泡=粉影，绿按钮=橄榄影），中性表面用派生中性影；
 * 2. 按钮语义优先凸起（Raised），凹陷只给输入/槽位；
 * 3. 点击 = 凸起被按凹 + 超椭圆裁切的扩散波纹（不用 Material 矩形 ripple）；
 * 4. **同级不遮挡**：用 [NeoSiblings] 声明同级间距，阴影自动缩到 gap/2 以内，
 *    一个按钮的投影不会盖到旁边同级按钮上。
 */
enum class NeoMaterial { Raised, Inset, Float }

/** 同级阴影预算：由 [NeoSiblings] 提供，阴影最大外扩距离 = gap / 2。 */
val LocalNeoShadowBudget = staticCompositionLocalOf<Dp?> { null }

/** 声明一组同级元素（按钮/卡片）的间距；组内阴影不会超出各自半间距。 */
@Composable
fun NeoSiblings(gap: Dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNeoShadowBudget provides gap / 2f, content = content)
}

@Composable
fun NeoSurface(
    modifier: Modifier = Modifier,
    radius: Dp = M5Radius.card,
    exponent: Float = 4f,
    material: NeoMaterial = NeoMaterial.Raised,
    fill: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalM5.current.palette
    val budget = LocalNeoShadowBudget.current
    val shape = remember(radius, exponent) { SuperellipseShape(radius, exponent) }
    val base = fill ?: p.surface
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
            .then(
                if (material == NeoMaterial.Inset) {
                    Modifier
                } else {
                    Modifier.neoOuterShadow(shape, radius, p, base, budget, press, flat = material == NeoMaterial.Float)
                }
            )
            .clip(shape)
            .background(brush, shape)
            .then(
                when {
                    material == NeoMaterial.Inset -> Modifier.neoInsetShadow(shape, radius, p, 1f)
                    press > 0.01f -> Modifier.neoInsetShadow(shape, radius, p, press * 0.9f)
                    else -> Modifier
                }
            )
            .superellipseRipple(shape, press, ripple.value, pressPos, base)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
internal fun rememberPressProgress(pressed: Boolean): Float = animateFloatAsState(
    targetValue = if (pressed) 1f else 0f,
    animationSpec = if (pressed) {
        tween(durationMillis = 90, easing = FastOutLinearInEasing)
    } else {
        spring(dampingRatio = 0.78f, stiffness = 520f)
    },
    label = "m5-press",
).value

/** 超椭圆裁切的按压波纹；玻璃/新拟物共用。 */
internal fun Modifier.superellipseRipple(
    shape: Shape,
    press: Float,
    ripple: Float,
    pressPos: Offset,
    base: Color,
): Modifier = drawWithContent {
    drawContent()
    if (press <= 0.01f || ripple <= 0f) return@drawWithContent
    val outline = shape.createOutline(size, layoutDirection, this)
    if (outline !is Outline.Generic) return@drawWithContent
    val center = if (pressPos == Offset.Unspecified) this.center else pressPos
    val radius = size.maxDimension * 1.45f * ripple
    clipPath(outline.path) {
        drawCircle(
            color = lerpColor(Color.White, base, 0.12f).copy(alpha = 0.30f * press),
            radius = radius,
            center = center,
        )
    }
}

/**
 * 外阴影：亮影左上、暗影右下，颜色取自身；按下时 offset 收缩、alpha 降低，
 * 同时叠加内阴影（NeoSurface 里调用），形成"凸起被按凹下去"。
 * [budget] 非空时，阴影最大外扩被限制在半间距内（同级互不遮挡）。
 */
private fun Modifier.neoOuterShadow(
    shape: Shape,
    radius: Dp,
    p: M5Palette,
    base: Color,
    budget: Dp?,
    press: Float,
    flat: Boolean,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    if (outline !is Outline.Generic) return@drawBehind
    val path = outline.path.asAndroidPath()
    val fullOffset = (radius.toPx() * 0.34f).coerceIn(3.dp.toPx(), 13.dp.toPx())
    val budgetPx = budget?.toPx()
    val budgetScale = if (budgetPx != null) {
        val reach = fullOffset * 2.85f
        if (reach > budgetPx) (budgetPx / reach).coerceAtLeast(0.18f) else 1f
    } else 1f
    val offset = fullOffset * budgetScale * (1f - 0.72f * press)
    val wideBlur = (fullOffset * 1.75f * budgetScale).coerceAtLeast(1f)
    val tightBlur = (fullOffset * 0.65f * budgetScale).coerceAtLeast(1f)
    val fade = 1f - 0.42f * press
    val light = highlightTint(base, p)
    val dark = shadowTint(base, p)
    val lightAlpha = (if (p.dark) 0.22f else 0.95f) * fade
    val darkWide = (if (p.dark) 0.95f else 0.55f) * fade
    val darkTight = (if (p.dark) 0.60f else 0.42f) * fade
    if (!flat) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val save = nc.save()
            nc.translate(-offset * 0.9f, -offset * 0.9f)
            nc.drawPath(path, blurPaint(light.copy(alpha = lightAlpha), wideBlur))
            nc.restoreToCount(save)
        }
    }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val save = nc.save()
        nc.translate(offset * 0.9f, offset)
        nc.drawPath(path, blurPaint(dark.copy(alpha = darkWide), wideBlur))
        nc.restoreToCount(save)
    }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val save = nc.save()
        nc.translate(offset * 0.55f, offset * 0.62f)
        nc.drawPath(path, blurPaint(dark.copy(alpha = darkTight), tightBlur))
        nc.restoreToCount(save)
    }
}

/** 内阴影：把形状的"外部"平移进内部再模糊；strength 用于按压时渐进出现。 */
private fun Modifier.neoInsetShadow(
    shape: Shape,
    radius: Dp,
    p: M5Palette,
    strength: Float,
): Modifier = drawWithCache {
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
        val darkAlpha = (if (p.dark) 0.95f else 0.60f) * strength
        val lightAlpha = (if (p.dark) 0.32f else 0.95f) * strength
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

/** 颜色饱和度（0..1）：决定投影/高光要不要采用面板自身色。 */
private fun chromaAmount(c: Color): Float {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    return ((mx - mn) * 3.2f).coerceIn(0f, 1f)
}

/** 投影 = 中性影 + 面板自身色的深色版（按饱和度混合）。 */
internal fun shadowTint(base: Color, p: M5Palette): Color =
    lerpColor(p.shadowDark, darken(base, if (p.dark) 0.55f else 0.30f), chromaAmount(base))

/** 亮影同理，用面板自身色的浅色版。 */
internal fun highlightTint(base: Color, p: M5Palette): Color =
    lerpColor(p.shadowLight, lighten(base, 0.35f), chromaAmount(base) * if (p.dark) 0.30f else 0.80f)
