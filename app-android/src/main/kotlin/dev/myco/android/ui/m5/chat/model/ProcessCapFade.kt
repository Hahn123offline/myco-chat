// CHAT-08
package dev.myco.android.ui.m5.chat.model

/**
 * 过程组封顶内滚的 **24dp mask 渐隐**几何（`docs/spec/panels/chat.md` §1.3「到边遮罩」/ §3.6 字段 6）。
 *
 * 规格：
 * - 上下各 **24dp** `mask` 线性渐变；判定阈值 **1dp**；
 * - 可滚侧才有渐隐，不可滚侧无；两端都可滚时两端同时渐变（规格原文"合成四段"= 4 个 stop：
 *   透明 → 不透明 → 不透明 → 透明）；
 * - 内容不足一屏 → 无 mask、无滚动条（§3.6 字段 10）。
 *
 * 纯几何，不依赖 Compose：输出 [CapFadeGeometry.stops] 交给 UI 换成 `Brush.verticalGradient` /
 * alpha 渐变（Compose 无 `mask-image` 等价物，实现层按 alpha 或 blended 层绘制）。
 */
enum class FadeEdges {
    /** 不可滚（内容不足一屏 / 两端到边）：无 mask。 */
    NONE,

    /** 只能向上滚（已离开顶部，未到底）：只渐隐顶边。 */
    TOP,

    /** 只能向下滚（在顶部，未到底）：只渐隐底边。 */
    BOTTOM,

    /** 两端都可滚：两端各 24dp 渐隐（4 个 stop）。 */
    BOTH,
}

/** mask 渐变的一个 stop：位置（dp，相对视口顶）+ 不透明度（0=完全透明=内容不可见）。 */
data class FadeStop(val positionDp: Float, val alpha: Float)

/**
 * 一次滚动位置下的遮罩几何。
 *
 * @param edges 哪侧需要渐隐。
 * @param fadeDp 规格值 24dp（两端的最大渐隐带）。
 * @param topFadeDp 实际顶边渐隐带（0 = 不渐隐）。
 * @param bottomFadeDp 实际底边渐隐带（0 = 不渐隐）。
 * @param viewportHeightDp 可视高（= cap 内滚容器高）。
 * @param contentHeightDp 内容全高。
 * @param scrollOffsetDp 当前内滚偏移（0 = 顶）。
 * @param stops mask 渐变 stop 序列（NONE 时为空）。
 */
data class CapFadeGeometry(
    val edges: FadeEdges,
    val fadeDp: Float,
    val topFadeDp: Float,
    val bottomFadeDp: Float,
    val viewportHeightDp: Float,
    val contentHeightDp: Float,
    val scrollOffsetDp: Float,
    val stops: List<FadeStop>,
) {
    val hasFade: Boolean get() = edges != FadeEdges.NONE

    /** 中间完全不透明段的高度（两端同时渐隐时 = 视口 − 两段）。 */
    val opaqueHeightDp: Float
        get() = (viewportHeightDp - topFadeDp - bottomFadeDp).coerceAtLeast(0f)

    /**
     * 视口内 y 处的不透明度（0=透明，1=完全可见）。用于自绘 mask / 单测校验：
     * 顶边 y=0 透明、y=topFadeDp 处完全可见；底边对称。
     */
    fun alphaAt(yDp: Float): Float {
        if (edges == FadeEdges.NONE || viewportHeightDp <= 0f) return 1f
        val y = yDp.coerceIn(0f, viewportHeightDp)
        val fromTop = if (topFadeDp > 0f && y < topFadeDp) y / topFadeDp else 1f
        val fromBottom =
            if (bottomFadeDp > 0f && y > viewportHeightDp - bottomFadeDp) {
                (viewportHeightDp - y) / bottomFadeDp
            } else {
                1f
            }
        return minOf(fromTop, fromBottom).coerceIn(0f, 1f)
    }
}

/** 过程组封顶遮罩计算器（§3.6 字段 6）。 */
object ProcessCapFade {
    /** 规格：上下各 24dp mask 线性渐变。 */
    const val FADE_DP: Float = 24f

    /** 规格：到边判定阈值 1dp（≤1dp 视为到边）。 */
    const val EDGE_THRESHOLD_DP: Float = 1f

    /**
     * 计算当前滚动位置的遮罩几何。
     *
     * @param contentHeightDp 组体内容全高（成员间距已计入）。
     * @param viewportHeightDp 封顶容器可视高（流式期 = [ChatCap.capDp]）。
     * @param scrollOffsetDp 内滚偏移，0 = 顶部。
     */
    fun geometry(
        contentHeightDp: Float,
        viewportHeightDp: Float,
        scrollOffsetDp: Float,
    ): CapFadeGeometry {
        val viewport = viewportHeightDp.coerceAtLeast(0f)
        if (viewport <= 0f) {
            return CapFadeGeometry(
                edges = FadeEdges.NONE,
                fadeDp = FADE_DP,
                topFadeDp = 0f,
                bottomFadeDp = 0f,
                viewportHeightDp = viewport,
                contentHeightDp = contentHeightDp,
                scrollOffsetDp = scrollOffsetDp,
                stops = emptyList(),
            )
        }
        // 内容不足一屏（或只多 ≤1dp）→ 无 mask、无滚动条（§3.6 字段 10）。
        val overflow = contentHeightDp - viewport
        val canScrollUp = scrollOffsetDp > EDGE_THRESHOLD_DP
        val canScrollDown = (contentHeightDp - viewport - scrollOffsetDp) > EDGE_THRESHOLD_DP
        val edges = when {
            overflow <= EDGE_THRESHOLD_DP -> FadeEdges.NONE
            canScrollUp && canScrollDown -> FadeEdges.BOTH
            canScrollUp -> FadeEdges.TOP
            canScrollDown -> FadeEdges.BOTTOM
            else -> FadeEdges.NONE
        }
        var top = if (edges == FadeEdges.TOP || edges == FadeEdges.BOTH) FADE_DP else 0f
        var bottom = if (edges == FadeEdges.BOTTOM || edges == FadeEdges.BOTH) FADE_DP else 0f
        // 规格未给"视口不足 48dp 时两段渐变如何取舍"的边界；按对半切避免负的中间段（我方取值，P4 校准）。
        if (top + bottom > viewport) {
            if (top > 0f && bottom > 0f) {
                top = viewport / 2f
                bottom = viewport / 2f
            } else {
                top = top.coerceAtMost(viewport)
                bottom = bottom.coerceAtMost(viewport)
            }
        }
        val stops: List<FadeStop> = when (edges) {
            FadeEdges.NONE -> emptyList()
            FadeEdges.TOP -> listOf(
                FadeStop(0f, 0f),
                FadeStop(top, 1f),
            )
            FadeEdges.BOTTOM -> listOf(
                FadeStop(viewport - bottom, 1f),
                FadeStop(viewport, 0f),
            )
            FadeEdges.BOTH -> listOf(
                FadeStop(0f, 0f),
                FadeStop(top, 1f),
                FadeStop(viewport - bottom, 1f),
                FadeStop(viewport, 0f),
            )
        }
        return CapFadeGeometry(
            edges = edges,
            fadeDp = FADE_DP,
            topFadeDp = top,
            bottomFadeDp = bottom,
            viewportHeightDp = viewport,
            contentHeightDp = contentHeightDp,
            scrollOffsetDp = scrollOffsetDp,
            stops = stops,
        )
    }
}
