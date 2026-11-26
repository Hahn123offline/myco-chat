package dev.myco.android.ui.m5

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M5 视觉令牌（v1）。与 M1 的 MycoTheme 并存：M5 原语只读这里，M5.1 再替换旧 Theme。
 * 规则见 docs/design/90-m5-visual-system.md §2。
 */

@Immutable
data class M5Palette(
    val dark: Boolean,
    val bg: Color,
    val bgTop: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val sunken: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val moss: Color,
    val mossTint: Color,
    val blush: Color,
    val cream: Color,
    val rim: Color,
    val danger: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val glassTint: Color,
    val glassTintBottom: Color,
    val glassRim: Color,
    val glassSpecular: Color,
)

val M5LightPalette = M5Palette(
    dark = false,
    bg = Color(0xFFEEF2E3),
    bgTop = Color(0xFFF8FBF0),
    surface = Color(0xFFF8FBF2),
    surfaceHigh = Color(0xFFFFFFFF),
    sunken = Color(0xFFDDE5CF),
    text = Color(0xFF1B1B16),
    textSecondary = Color(0xFF59614F),
    textTertiary = Color(0xFF8B9380),
    moss = Color(0xFFB0C591),
    mossTint = Color(0xFFE3EBD2),
    blush = Color(0xFFEFB9B3),
    cream = Color(0xFFFFE3DE),
    rim = Color(0x00000000),
    danger = Color(0xFFB3261E),
    // 阴影从底色派生，不用中性灰（灰会显脏）
    shadowDark = Color(0xFFB7C1A3),
    shadowLight = Color(0xFFFFFFFF),
    glassTint = Color(0xB4FFFFFF),
    glassTintBottom = Color(0x8CFFFFFF),
    glassRim = Color(0xFFFFFFFF),
    glassSpecular = Color(0xFFFFFFFF),
)

val M5DarkPalette = M5Palette(
    dark = true,
    bg = Color(0xFF141210),
    bgTop = Color(0xFF1B1814),
    surface = Color(0xFF211D19),
    surfaceHigh = Color(0xFF2B251E),
    sunken = Color(0xFF0B0908),
    text = Color(0xFFF3F0E8),
    textSecondary = Color(0xFFBDB7A9),
    textTertiary = Color(0xFF8A8477),
    moss = Color(0xFFAEC983),
    mossTint = Color(0xFF2E3A24),
    blush = Color(0xFFF3BFA5),
    cream = Color(0xFFFFDBB2),
    rim = Color(0xFFE09A42),
    danger = Color(0xFFFFB4AB),
    shadowDark = Color(0xFF040303),
    shadowLight = Color(0xFF3B3A35),
    glassTint = Color(0x24FFFFFF),
    glassTintBottom = Color(0x12FFFFFF),
    glassRim = Color(0x40FFFFFF),
    glassSpecular = Color(0x1FFFFFFF),
)

/** 全屏统一光向：左上主光。所有表面读同一个 rig，禁止各组件自写渐变角度。 */
@Immutable
data class LightRig(
    val azimuthDeg: Float = 135f,
    val elevation: Float = 0.55f,
    val key: Float = 0.9f,
    val fill: Float = 0.3f,
    val rim: Float = 0.45f,
)

@Immutable
data class M5ThemeData(val palette: M5Palette, val rig: LightRig = LightRig())

val LocalM5 = staticCompositionLocalOf { M5ThemeData(M5LightPalette) }

@Composable
fun M5Theme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalM5 provides M5ThemeData(if (dark) M5DarkPalette else M5LightPalette),
        content = content,
    )
}

object M5Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** 同心圆角：子半径 = 父半径 − 间距（下限 4dp）。 */
object M5Radius {
    val panel = 32.dp
    val card = 20.dp
    val control = 12.dp
    val inset = 8.dp
    val chip = 6.dp

    fun child(parent: Dp, gap: Dp): Dp = (parent - gap).coerceAtLeast(4.dp)
}

private fun cjk(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

object M5Type {
    val caption = cjk(11, 16)
    val label = cjk(13, 18, FontWeight.Medium)
    val body = cjk(16, 26)
    val title = cjk(17, 24, FontWeight.SemiBold)
    val headline = cjk(20, 28, FontWeight.SemiBold)
    val display = cjk(28, 36, FontWeight.SemiBold)
}

/**
 * 动效：跟手 1:1，释放必须带速度。禁止用定长 tween 假装动效。
 */
object M5Motion {
    val settle: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = 420f,
        visibilityThreshold = 0.5f,
    )
    val glide: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessLow,
        visibilityThreshold = 0.5f,
    )
    val snap: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.92f,
        stiffness = 900f,
        visibilityThreshold = 0.5f,
    )
    val fade = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
}
