// 验收 ID：BL-40 BL-41 BL-46 BL-30 BL-37（insets 与壳几何在横屏/宽屏下不变）
//
// base.layout.shell 的 insets 真源（E-02=A / §1.3.2）。三条硬规则：
//  ① **外框吃满全屏**：shell 根 / route 外框（背景/遮罩/滑动面）一律不垫 inset —— 否则滑动面被缩进、
//     遮罩盖不住系统栏区（§1.3.2-②）；
//  ② **只有内容根按边垫**：top = statusBars ∪ displayCutout(top)；start/end = displayCutout 对应侧；
//  ③ **底部只垫一次 union(navigationBars, IME, cutout)**，禁止 `navigationBarsPadding()` + `imePadding()`
//     叠加（双垫 = 输入区浮空，BL-41）。本文件把 union 结果做成**单值真源**下发给 composer/chat。
//
// 只做"算与发布"，不做视觉；具体 padding 由内容根用 [contentRootInsets] 消费一次。
package dev.myco.android.ui.m5.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.m5.primitives.LocalOverlayBottomInset
import dev.myco.android.ui.m5.primitives.LocalOverlayTopInset
import kotlin.math.abs

// ---------------------------------------------------------------------------
// 纯几何（可 JVM 单测）——"只垫一次"的判据就在这里
// ---------------------------------------------------------------------------

/** insets 的并集运算（同一条边 = 取最大值，这就是 union 的几何含义）。 */
object PanelInsetMath {
    /** 顶部：statusBars ∪ displayCutout(top)。 */
    fun unionTop(statusBarsDp: Float, cutoutTopDp: Float): Float = maxOf(statusBarsDp, cutoutTopDp)

    /**
     * 底部：**union(navigationBars, IME, displayCutout.bottom) 一次**。
     * 三条都在同一条边上 → 取最大值（不是相加！相加就是 BL-41 的"双垫"缺陷）。
     */
    fun unionBottom(navBarsDp: Float, imeDp: Float, cutoutBottomDp: Float): Float =
        maxOf(navBarsDp, imeDp, cutoutBottomDp)

    /** 起始边：displayCutout.start（横屏挖孔机必查，BL-40）。 */
    fun unionStart(cutoutStartDp: Float): Float = maxOf(0f, cutoutStartDp)

    /** 结束边：displayCutout.end。 */
    fun unionEnd(cutoutEndDp: Float): Float = maxOf(0f, cutoutEndDp)

    /**
     * 列表底部留白 = 输入区实测高（E-02=A 字段 7：composer 不自己读 `WindowInsets.ime`）。
     * 输入区已经把 union 底垫吃掉了，列表只需预留它的高度，不重复加 navBars。
     */
    fun listBottomPadding(inputAreaHeightDp: Float): Float = maxOf(0f, inputAreaHeightDp)
}

/** 解析后的 insets（dp）。除 `bottom` 外都是单边值，便于各内容根只垫自己那几条边。 */
@Immutable
data class PanelInsets(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    /** 原始分量（诊断/测试用；`bottom` 已是一次 union 的结果）。 */
    val statusBarsTop: Dp = 0.dp,
    val cutoutTop: Dp = 0.dp,
    val cutoutBottom: Dp = 0.dp,
    val cutoutStart: Dp = 0.dp,
    val cutoutEnd: Dp = 0.dp,
    val navBarsBottom: Dp = 0.dp,
    val imeBottom: Dp = 0.dp,
) {
    companion object {
        val Zero = PanelInsets()
    }
}

/** 读一次窗口 insets 并做三条边的 union（**不**记忆：insets 变化必须触发重算）。 */
@Composable
fun rememberPanelInsets(): PanelInsets {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    fun topOf(insets: WindowInsets): Dp = with(density) { insets.getTop(this).toDp() }
    fun bottomOf(insets: WindowInsets): Dp = with(density) { insets.getBottom(this).toDp() }
    fun leftOf(insets: WindowInsets): Dp = with(density) { insets.getLeft(this, layoutDirection).toDp() }
    fun rightOf(insets: WindowInsets): Dp = with(density) { insets.getRight(this, layoutDirection).toDp() }

    val statusTop = topOf(WindowInsets.statusBars)
    val cutoutTop = topOf(WindowInsets.displayCutout)
    val cutoutBottom = bottomOf(WindowInsets.displayCutout)
    val cutoutStart = leftOf(WindowInsets.displayCutout)
    val cutoutEnd = rightOf(WindowInsets.displayCutout)
    val navBottom = bottomOf(WindowInsets.navigationBars)
    val imeBottom = bottomOf(WindowInsets.ime)

    return PanelInsets(
        top = PanelInsetMath.unionTop(statusTop.value, cutoutTop.value).dp,
        bottom = PanelInsetMath.unionBottom(navBottom.value, imeBottom.value, cutoutBottom.value).dp,
        start = PanelInsetMath.unionStart(cutoutStart.value).dp,
        end = PanelInsetMath.unionEnd(cutoutEnd.value).dp,
        statusBarsTop = statusTop,
        cutoutTop = cutoutTop,
        cutoutBottom = cutoutBottom,
        cutoutStart = cutoutStart,
        cutoutEnd = cutoutEnd,
        navBarsBottom = navBottom,
        imeBottom = imeBottom,
    )
}

// ---------------------------------------------------------------------------
// 下发真源
// ---------------------------------------------------------------------------

/** 全部 insets（容器读一次，向下提供）。 */
val LocalPanelInsets = staticCompositionLocalOf { PanelInsets.Zero }

/**
 * 底部单次 union 真源（`union(navBars, IME, cutout)`）。
 * composer 只消费它，**不要**自己再读 `WindowInsets.ime` 或叠加 `imePadding()`（BL-41）。
 */
val LocalPanelBottomInset = staticCompositionLocalOf { 0.dp }

/** 输入区实测高（E-02=A 字段 7）：聊天列表底部留白 = 它，不是估算值。 */
val LocalInputAreaHeight = staticCompositionLocalOf { 0.dp }

/**
 * 输入区高度的发布者（真源）。由 shell 创建一份，composer 用
 * `Modifier.publishInputAreaHeight(registry)` 上报，chat 读 [LocalInputAreaHeight]。
 */
@Stable
class PanelInputAreaRegistry {
    var height: Dp by mutableStateOf(0.dp)
        private set

    fun update(value: Dp) {
        if (abs((value - height).value) > 0.5f) height = value
    }
}

/** shell 创建一次即可（不要每帧 remember 新的）。 */
@Composable
fun rememberInputAreaRegistry(): PanelInputAreaRegistry = remember { PanelInputAreaRegistry() }

/** 输入区在布局后把自己的**实测高**写回真源（不参与计算，只上报）。 */
@Composable
fun Modifier.publishInputAreaHeight(registry: PanelInputAreaRegistry): Modifier {
    val density = LocalDensity.current
    return this.onSizeChanged { size ->
        registry.update(with(density) { size.height.toDp() })
    }
}

/**
 * insets 提供者：**始终包在 shell 最外层**（`SystemUiController`/edge-to-edge 由 Lead 的 MainActivity 负责）。
 * 根节点自身不垫任何 inset（§1.3.2-①）。
 */
@Composable
fun PanelInsetsProvider(
    registry: PanelInputAreaRegistry? = null,
    content: @Composable () -> Unit,
) {
    val insets = rememberPanelInsets()
    CompositionLocalProvider(
        LocalPanelInsets provides insets,
        LocalPanelBottomInset provides insets.bottom,
        // registry.height 是 Compose state：输入区改高 → 读它的消费方自动重组，无需额外同步
        LocalInputAreaHeight provides (registry?.height ?: 0.dp),
        // 浮层（primitives）只按自己锚点垫一次 bottom / top（E-02=A 字段 7）
        LocalOverlayBottomInset provides insets.bottom,
        LocalOverlayTopInset provides insets.top,
        content = content,
    )
}

/**
 * 内容根的 insets 修饰：**唯一**按边垫的落点（§1.3.2）。
 * 默认 top + bottom + start/end(cutout) 全垫；`fullScreenPanel` 这类"内容仍要垫、背景不垫"的层直接用它。
 *
 * ⚠️ 不要在调用方再叠加 `imePadding()` / `navigationBarsPadding()` / `statusBarsPadding()`。
 */
@Composable
fun Modifier.contentRootInsets(
    includeTop: Boolean = true,
    includeBottom: Boolean = true,
    includeHorizontal: Boolean = true,
): Modifier {
    val insets = LocalPanelInsets.current
    val density = LocalDensity.current
    return remember(insets, density, includeTop, includeBottom, includeHorizontal) {
        val windowInsets = with(density) {
            WindowInsets(
                left = if (includeHorizontal) insets.start.roundToPx() else 0,
                top = if (includeTop) insets.top.roundToPx() else 0,
                right = if (includeHorizontal) insets.end.roundToPx() else 0,
                bottom = if (includeBottom) insets.bottom.roundToPx() else 0,
            )
        }
        this.windowInsetsPadding(windowInsets)
    }
}

/**
 * shell 根容器：**吃满全屏、不垫 inset**（滑动面/遮罩/背景必须贴到屏幕边）。
 * `enableEdgeToEdge` 与系统栏透明/图标明暗归 Lead 的 MainActivity / base.theme。
 */
@Composable
fun PanelShellRoot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
    }
}

/** 便捷：底部真源（供 `M5Toast` 等浮层按自己锚点垫，它们不归内容根垫）。 */
@Composable
fun panelBottomInset(): Dp = LocalPanelBottomInset.current

/** 便捷：顶部内容 inset。 */
@Composable
fun panelTopInset(): Dp = LocalPanelInsets.current.top
