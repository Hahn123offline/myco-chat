package dev.myco.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 视觉令牌 v0 的 Compose 落地：
 * 组件禁止硬编码 hex；所有颜色/间距/圆角从 MycoTheme / MycoSpacing / MycoRadius 取。
 * 来源：docs/design/50-visual-tokens.md（用户色卡语义化）。
 */

object MycoSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object MycoRadius {
    val control = 12.dp
    val card = 16.dp
    val sheet = 24.dp
}

/** 主题之外的扩展色：发光、玻璃、气泡。浅色模式不用发光。 */
data class MycoExtendedColors(
    val glow: Color,
    val glowSoft: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val bubbleUser: Color,
    val bubbleAssistant: Color,
    val bubbleError: Color,
    val bubbleStopped: Color,
)

private val LightExtended = MycoExtendedColors(
    glow = Color.Transparent,
    glowSoft = Color.Transparent,
    glassTint = Color(0xF2FFFFFF),
    glassBorder = Color(0xFFC9D2B8),
    bubbleUser = Color(0xFFE3EBD2),
    bubbleAssistant = Color(0xFFF1F5E7),
    bubbleError = Color(0xFFFFDAD6),
    bubbleStopped = Color(0xFFF1F5E7),
)

private val DarkExtended = MycoExtendedColors(
    glow = Color(0xFFE09A42),
    glowSoft = Color(0x33E09A42),
    glassTint = Color(0x26FFFFFF),
    glassBorder = Color(0x40E09A42),
    bubbleUser = Color(0xFF2E3A24),
    bubbleAssistant = Color(0xFF26221E),
    bubbleError = Color(0xFF5A1A16),
    bubbleStopped = Color(0xFF26221E),
)

val LocalMycoColors = staticCompositionLocalOf { LightExtended }

private val LightColors = lightColorScheme(
    // 对比度修正：浅色背景上 B0C591 作正文/文字按钮太浅（实测发灰）。
    // 主色文字/描边换成同色相深绿，主色浅绿保留在容器/选中态里（palette 气质不变）。
    primary = Color(0xFF5F7040),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB0C591),
    onPrimaryContainer = Color(0xFF1B2415),
    secondary = Color(0xFF6F8A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EBD2),
    onSecondaryContainer = Color(0xFF2A3220),
    tertiary = Color(0xFFC98B86),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEBE9),
    onTertiaryContainer = Color(0xFF3A1E1A),
    background = Color(0xFFFAFDF3),
    onBackground = Color(0xFF1B1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B16),
    surfaceVariant = Color(0xFFF1F5E7),
    onSurfaceVariant = Color(0xFF4A5044),
    outline = Color(0xFF9AA88A),
    outlineVariant = Color(0xFFE3EBD2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC983),
    onPrimary = Color(0xFF1B2415),
    primaryContainer = Color(0xFF2E3A24),
    onPrimaryContainer = Color(0xFFDCE8C8),
    secondary = Color(0xFFEBEDB7),
    onSecondary = Color(0xFF232313),
    secondaryContainer = Color(0xFF3A3A22),
    onSecondaryContainer = Color(0xFFF0F2CC),
    tertiary = Color(0xFFF3BFA5),
    onTertiary = Color(0xFF3A2118),
    tertiaryContainer = Color(0xFF4A2E22),
    onTertiaryContainer = Color(0xFFFFE0CE),
    background = Color(0xFF161412),
    onBackground = Color(0xFFF3F0E8),
    surface = Color(0xFF1E1B18),
    onSurface = Color(0xFFF3F0E8),
    surfaceVariant = Color(0xFF26221E),
    onSurfaceVariant = Color(0xFFC9C2B6),
    outline = Color(0xFF4A4640),
    outlineVariant = Color(0xFF3A3530),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A1A16),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 中文正文行高按 1.6 左右（frontend-design-tech 建议）；includeFontPadding=false 让视觉更紧凑。 */
private val MycoTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 26.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 22.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 20.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ),
)

private val MycoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(MycoRadius.control),
    medium = RoundedCornerShape(MycoRadius.card),
    large = RoundedCornerShape(MycoRadius.sheet),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun MycoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extended = if (dark) DarkExtended else LightExtended
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MycoTypography,
        shapes = MycoShapes,
    ) {
        CompositionLocalProvider(LocalMycoColors provides extended, content = content)
    }
}
