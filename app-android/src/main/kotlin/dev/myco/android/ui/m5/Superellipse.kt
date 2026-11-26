package dev.myco.android.ui.m5

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow

/**
 * M5 形状原语：Lamé 超椭圆（squircle）。
 *
 * 参数曲线：x = a·|cos t|^(2/n)·sign(cos t)，y = b·|sin t|^(2/n)·sign(sin t)。
 * 圆角矩形版本只把 4 个角画成 90° 超椭圆弧，中间保持直边（不是整圈采样再裁）。
 *
 * 约定：
 * - n = 4 是经典 squircle（iOS 圆角约 n≈5）；n 越大越方，n=2 退化为正圆角。
 * - 每角自适应采样 6..24 点，大半径下也平滑；Path 按尺寸缓存，重组不重算。
 * - clip / background / border / shadow 全走同一个 Outline，不存在"裁剪轮廓和视觉轮廓不一致"。
 */
class SuperellipseShape(
    private val radius: Dp,
    private val exponent: Float = 4f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        }
        val r = with(density) { radius.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        return Outline.Generic(SuperellipsePathCache.get(size.width, size.height, r, exponent))
    }

    override fun equals(other: Any?): Boolean =
        other is SuperellipseShape && other.radius == radius && other.exponent == exponent

    override fun hashCode(): Int = 31 * radius.hashCode() + exponent.hashCode()

    override fun toString(): String = "SuperellipseShape(radius=$radius, n=$exponent)"
}

/** 胶囊：半径=高/2 的超椭圆，比正圆更"肉"一点，作为按钮/标签的统一形。 */
@Composable
fun rememberSquircleCapsule(height: Dp, exponent: Float = 4f): Shape =
    remember(height, exponent) { SuperellipseShape(height / 2, exponent) }

@Composable
fun rememberSuperellipse(radius: Dp, exponent: Float = 4f): Shape =
    remember(radius, exponent) { SuperellipseShape(radius, exponent) }

internal object SuperellipsePathCache {
    private data class Key(val w: Int, val h: Int, val r: Int, val n: Int)

    private val cache = object : LinkedHashMap<Key, Path>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Path>): Boolean = size > 64
    }

    fun get(width: Float, height: Float, radius: Float, exponent: Float): Path {
        val key = Key(
            w = width.toBits(),
            h = height.toBits(),
            r = radius.toBits(),
            n = exponent.toBits(),
        )
        synchronized(cache) {
            cache[key]?.let { return it }
            val path = build(width, height, radius, exponent)
            cache[key] = path
            return path
        }
    }

    private fun build(w: Float, h: Float, r: Float, n: Float): Path {
        val path = Path()
        if (r <= 0.01f) {
            path.moveTo(0f, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            return path
        }
        val steps = (r / 3f).toInt().coerceIn(6, 24)
        val e = 2f / n
        fun pow(v: Float): Float = if (v <= 0f) 0f else v.powCompat(e)

        // 起点：上边左端
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        // 右上角：(w-r,0) → (w,r)
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI.toFloat() / 2f)
            path.lineTo(w - r + r * pow(kotlin.math.sin(t)), r - r * pow(kotlin.math.cos(t)))
        }
        path.lineTo(w, h - r)
        // 右下角：(w,h-r) → (w-r,h)
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI.toFloat() / 2f)
            path.lineTo(w - r + r * pow(kotlin.math.cos(t)), h - r + r * pow(kotlin.math.sin(t)))
        }
        path.lineTo(r, h)
        // 左下角：(r,h) → (0,h-r)
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI.toFloat() / 2f)
            path.lineTo(r - r * pow(kotlin.math.sin(t)), h - r + r * pow(kotlin.math.cos(t)))
        }
        path.lineTo(0f, r)
        // 左上角：(0,r) → (r,0)
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI.toFloat() / 2f)
            path.lineTo(r - r * pow(kotlin.math.cos(t)), r - r * pow(kotlin.math.sin(t)))
        }
        path.close()
        return path
    }
}

private fun Float.powCompat(e: Float): Float = if (this <= 0f) 0f else pow(e)
