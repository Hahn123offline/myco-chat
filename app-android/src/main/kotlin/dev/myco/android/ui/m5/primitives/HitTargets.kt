// 验收 ID：BP-01 BP-03 BP-13 BP-18 BP-37 BP-67 BP-73 BP-79
//
// base.primitives.hit-target：**唯一扩边落点**（G-11 + G-16）。
//
// 硬约束（违反即不合格）：
//  ① **命中盒 = 布局盒**：扩边只能落在布局尺寸上（`heightIn` / `sizeIn` / `padding` / 包 Box），
//     **禁止**用 `Modifier.offset` / `graphicsLayer` / 绘制层冒充扩边（它们不改变命中盒）；
//  ② 判定三式（G-11）：`h + 2e ≥ F`、`2e ≤ g`、与相邻命中盒之间留 B；G-16 已取消固定 8dp 隔离带
//     （无 E0），**唯一硬约束变成"相邻命中盒不得相交"**；
//  ③ 装不下按 G-11 降级顺序退：① 加大 g → ② 关键区可点 → ③ 抬视觉高；禁止硬扩边导致重叠。
//
// 本文件上半部分是**纯几何**（JVM 单测覆盖判定式，见 PanelStackTest.kt 的 HitTargetsTest），
// 下半部分是布局修饰符（不引入任何 offset/graphicsLayer 扩边写法）。
package dev.myco.android.ui.m5.primitives

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 命中区落地路径（§1.4 四条路径；每条都必须改变**布局**尺寸，路径 B 除外——它是自绘命中层）。 */
enum class HitPath {
    /** A 整行承载：整行 `heightIn(min=44)` + clickable。 */
    A,

    /** B 自绘命中层补 hitTest（**不改变布局**，P4 原型验证，Q-BP-06 ④）。 */
    B,

    /** C 横向 Bleed：父盒加水平 padding，命中盒随父盒加宽。 */
    C,

    /** D 抬布局盒：`heightIn`/`sizeIn` 把布局盒抬到目标值。 */
    D,

    /** A+C：过程行 = 整行 + 横向 Bleed 8。 */
    A_C,

    /** D+C：密集图标 = 40×40 布局盒 + 横向 Bleed。 */
    D_C,
}

/**
 * 命中区类别与 F 值（G-16 定稿）。
 * `PROCESS_ROW` 的 F 是 32（不是 44）：G-16 明确"32 + 整行可点 + 横向 Bleed 8"。
 */
enum class HitCategory(
    /** F：最小命中高（dp）。 */
    val requiredMinDp: Float,
    /** 该类别规定的落地路径。 */
    val path: HitPath,
) {
    /** 输入框 / 主操作（发送、主按钮）。 */
    INPUT(48f, HitPath.D),

    /** 主操作按钮。 */
    PRIMARY_ACTION(48f, HitPath.D),

    /** 行 / 整块可点（会话行、菜单行、设置行、开关行）。 */
    ROW(44f, HitPath.A),

    /** 过程行（思考行、工具行）：32 + 整行 + 横向 Bleed 8。 */
    PROCESS_ROW(32f, HitPath.A_C),

    /** 密集图标（操作条每键）。 */
    DENSE_ICON(40f, HitPath.D_C),

    /** 行内片段 / 次级（chip、fold-toggle、Toast 动作）。 */
    INLINE_SEGMENT(36f, HitPath.D),

    /** 正文内链接（路径 B，自绘命中层）。 */
    TEXT_LINK(44f, HitPath.B),
}

/** 降级档位（G-11 顺序，序号越大越靠后）。 */
enum class HitDegradation {
    /** 达标，无需降级。 */
    NONE,

    /** ① 先加大相邻间距 g。 */
    INCREASE_GAP,

    /** ② 关键区可点（只让标题+箭头响应），扩展区不可点。 */
    KEY_AREA_ONLY,

    /** ③ 最后才抬视觉高。 */
    RAISE_VISUAL,
}

/** 一个命中盒的判定计划（纯数据）。 */
@Immutable
data class HitTargetPlan(
    val category: HitCategory,
    /** 视觉高（dp）。 */
    val visualHeightDp: Float,
    /** 布局高（dp）= 真正参与测量的高度（≈ 命中盒高）。 */
    val layoutHeightDp: Float,
    /** 上下各扩的边 e（dp）：`h + 2e = F` 时的解。 */
    val verticalBleedDp: Float,
    /** 横向 Bleed（路径 C，dp）。 */
    val horizontalBleedDp: Float,
    val neighborGapDp: Float,
    /** 三式是否全部满足。 */
    val satisfied: Boolean,
    val degradation: HitDegradation,
) {
    /** 布局盒最终高度（命中盒高）。 */
    val hitHeightDp: Float get() = layoutHeightDp
}

/** 命中的矩形（px 或 dp 都行，只要同一单位；纯几何工具）。 */
@Immutable
data class HitRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * G-11 / G-16 的判定式工具（纯函数，**JVM 单测覆盖**）。
 */
object HitTargets {

    /**
     * G-11 建议的隔离带 B（8dp）。G-16 把"≥8dp 隔离带"降级为"无 E0 的惯例"，
     * 唯一硬约束改为"相邻命中盒不得相交"；保留常量只用于文档/诊断，**不作为失败判据**。
     */
    const val SUGGESTED_ISOLATION_DP: Float = 8f

    /**
     * 三式判定（G-11）：
     *  ① `h + 2e ≥ F`
     *  ② `2e ≤ g`（不得吃进相邻节点间距；g ≤ 0 表示"无相邻可点节点"，按不相交处理 → 视为满足）
     *  ③ `g − 2e ≥ B`：G-16 取消固定 B，这里 B=0，等价于 ②。
     */
    fun satisfiesFormula(
        visualHeightDp: Float,
        verticalBleedDp: Float,
        requiredMinDp: Float,
        neighborGapDp: Float,
    ): Boolean {
        val heightOk = visualHeightDp + 2f * verticalBleedDp >= requiredMinDp - 0.001f
        val gapOk = neighborGapDp <= 0f || 2f * verticalBleedDp <= neighborGapDp + 0.001f
        return heightOk && gapOk
    }

    /**
     * 给"视觉尺寸 + 类别 + 相邻间距"算出达标计划。
     *
     * @param visualHeightDp 视觉高（例如过程行 32、图标键 40、正文链接行高 22）
     * @param neighborGapDp 与相邻**可点**节点的间距；没有相邻可点节点传 0
     * @param horizontalBleedDp 横向 Bleed（路径 C 需要，例如过程行 8）
     */
    fun plan(
        category: HitCategory,
        visualHeightDp: Float,
        neighborGapDp: Float = 0f,
        horizontalBleedDp: Float = 0f,
    ): HitTargetPlan {
        val required = category.requiredMinDp
        val layoutHeight = maxOf(visualHeightDp, required)
        val bleed = ((layoutHeight - visualHeightDp) / 2f).coerceAtLeast(0f)
        val gapOk = neighborGapDp <= 0f || 2f * bleed <= neighborGapDp + 0.001f
        val heightOk = layoutHeight + 0.001f >= required
        val satisfied = heightOk && gapOk
        val degradation = when {
            satisfied -> HitDegradation.NONE
            // 间距塞不下 e：先加 g，其次关键区可点，最后才抬视觉高
            neighborGapDp > 0f -> HitDegradation.INCREASE_GAP
            else -> HitDegradation.KEY_AREA_ONLY
        }
        return HitTargetPlan(
            category = category,
            visualHeightDp = visualHeightDp,
            layoutHeightDp = layoutHeight,
            verticalBleedDp = bleed,
            horizontalBleedDp = horizontalBleedDp,
            neighborGapDp = neighborGapDp,
            satisfied = satisfied,
            degradation = degradation,
        )
    }

    /** 达标所需的相邻间距（降级 ① 的目标值）：`g ≥ 2e`。 */
    fun requiredGapDp(category: HitCategory, visualHeightDp: Float): Float {
        val layoutHeight = maxOf(visualHeightDp, category.requiredMinDp)
        return (layoutHeight - visualHeightDp).coerceAtLeast(0f)
    }

    /** 两个命中盒是否相交（开区间判定：贴边不算相交）。 */
    fun intersects(a: HitRect, b: HitRect): Boolean =
        a.left < b.right - 0.001f && b.left < a.right - 0.001f &&
            a.top < b.bottom - 0.001f && b.top < a.bottom - 0.001f

    /** 找第一对相交的命中盒（BP-01/BP-79 的几何单测用）。 */
    fun firstIntersection(rects: List<HitRect>): Pair<Int, Int>? {
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                if (intersects(rects[i], rects[j])) return i to j
            }
        }
        return null
    }

    /** BP-01 断言：同屏任意两个可点原子的命中矩形不得相交。 */
    fun assertNoIntersection(rects: List<HitRect>) {
        val pair = firstIntersection(rects) ?: return
        error("命中盒相交：index ${pair.first} 与 ${pair.second}（${rects[pair.first]} × ${rects[pair.second]}）")
    }

    /**
     * 并排小目标（≥2 个相邻图标键）的布局间距：G-16 说"间距由内边距给出"，
     * 本函数把"净距 0 + 每键内边距"折算成**布局盒中心距**，便于几何单测。
     */
    fun denseIconCenterDistanceDp(keyWidthDp: Float, horizontalPaddingPerKeyDp: Float): Float =
        keyWidthDp + 2f * horizontalPaddingPerKeyDp
}

// ---------------------------------------------------------------------------
// 布局修饰符（**真的改变布局尺寸**，不碰 offset/graphicsLayer）
// ---------------------------------------------------------------------------

/**
 * 路径 A/D：把**布局盒**抬到最小高度（命中盒即布局盒）。
 * 视觉内容不跟着放大（内容自己居中）；代价是行高真的变高，使用前必须在 spec 字段 2 声明。
 */
fun Modifier.hitTargetMinHeight(min: Dp): Modifier = this.heightIn(min = min)

/** 路径 D：同时抬宽与高（图标键 / 关闭钮 / 主按钮 48）。 */
fun Modifier.hitTargetMinSize(minWidth: Dp, minHeight: Dp): Modifier = this.sizeIn(minWidth = minWidth, minHeight = minHeight)

/** 便捷：方形命中盒 48/44/40/36（G-16 档位）。 */
fun Modifier.hitTargetSquare(size: Dp): Modifier = this.sizeIn(minWidth = size, minHeight = size)

/**
 * 路径 C：横向 Bleed —— 父盒加水平 padding，命中盒随父盒加宽。
 * ⚠️ 视觉内容若要贴回正文缘，只能用 `Modifier.offset(x = -bleed)` 移动**视觉**；
 * 命中盒由本 padding 决定（offset 不改变命中盒，所以不违反 G-11）。
 */
fun Modifier.hitTargetBleed(horizontal: Dp = 8.dp): Modifier = this.padding(horizontal = horizontal)

/** 路径 A + C（过程行）：整行 32 高（F=32）+ 横向 Bleed 8。 */
fun Modifier.hitTargetProcessRow(bleed: Dp = 8.dp): Modifier =
    this.heightIn(min = 32.dp).padding(horizontal = bleed)

/** 路径 A（行级可点）：整行 44 高。 */
fun Modifier.hitTargetRow(min: Dp = 44.dp): Modifier = this.heightIn(min = min)

/** 路径 D（密集图标）：40×40 布局盒 + 横向 Bleed 由父 Row 的 padding 给。 */
fun Modifier.hitTargetDenseIcon(size: Dp = 40.dp): Modifier = this.sizeIn(minWidth = size, minHeight = size)

/** 按类别自动选合适的布局修饰符（调用点不需要记忆 F 值）。 */
fun Modifier.hitTargetFor(
    category: HitCategory,
    horizontalBleed: Dp = 8.dp,
): Modifier = when (category) {
    HitCategory.INPUT, HitCategory.PRIMARY_ACTION -> this.hitTargetMinHeight(48.dp)
    HitCategory.ROW -> this.hitTargetRow(44.dp)
    HitCategory.PROCESS_ROW -> this.heightIn(min = 32.dp).hitTargetBleed(horizontalBleed)
    HitCategory.DENSE_ICON -> this.hitTargetDenseIcon(40.dp)
    HitCategory.INLINE_SEGMENT -> this.hitTargetMinHeight(36.dp)
    HitCategory.TEXT_LINK -> this.hitTargetMinHeight(44.dp)
}
