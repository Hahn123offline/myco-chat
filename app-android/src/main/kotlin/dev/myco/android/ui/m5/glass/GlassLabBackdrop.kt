package dev.myco.android.ui.m5.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * "有内容的后台"：彩色列表 / 图片感渐变 / 文字 / 棋盘 / 色相条。
 *
 * 用**单个 Canvas 程序化绘制**（不是嵌套 Compose 行），原因：
 *  1. 玻璃面板要"重绘一份背景"，Canvas 重绘成本低、可复用同一份几何；
 *  2. 行高/偏移是确定的，便于做"同一块像素多档对比"；
 *  3. 嵌套 Column 在受限高度里会被压缩/裁剪（本机踩过）。
 *
 * 参数：
 *  - [scrollPx]：内容纵向滚动量（内容坐标 → 屏幕坐标）
 *  - [shift]：把该背景画到"某个玻璃面板"里时，内容需要的额外平移；
 *    shift=(sx,sy) 表示"场景坐标 (sx,sy) 处的像素显示在本 Canvas 的左上角"。
 */
@Composable
fun GlassBackdrop(
    scrollPx: Float,
    shift: Offset = Offset.Zero,
    renderScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        val body: DrawScope.() -> Unit = {
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color(0xFF31135E), Color(0xFF0E1B2C), Color(0xFF3A0F2E)),
                start = Offset.Zero,
                end = Offset(1400f, 2600f),
            )
        )
        val top = -scrollPx - shift.y
        val left = -shift.x
        var y = top
        for (i in 0 until 48) {
            val h = rowHeightPx(i)
            drawRow(i, y, left, h, measurer)
            y += h + gapPx
            if (y > size.height + 200f) break
            if (y + h < -200f) continue
        }
        }
        if (renderScale != 1f) {
            scale(renderScale, renderScale, Offset.Zero) { body() }
        } else {
            body()
        }
    }
}

private val gapPx get() = 26f

private fun rowHeightPx(index: Int): Float = when (index % 6) {
    0 -> 150f * 2.625f
    1 -> 140f * 2.625f
    2 -> 120f * 2.625f
    3 -> 150f * 2.625f
    4 -> 170f * 2.625f
    else -> 130f * 2.625f
}

private fun DrawScope.drawRow(index: Int, y: Float, left: Float, h: Float, measurer: TextMeasurer) {
    if (y > size.height || y + h < 0f) return
    when (index % 6) {
        0 -> {
            val variants = listOf(
                listOf(Color(0xFFFF3D7F), Color(0xFFFFA63D), Color(0xFFFFE93D)),
                listOf(Color(0xFF3DDCFF), Color(0xFF3D7BFF), Color(0xFF9A3DFF)),
                listOf(Color(0xFF3DFFA8), Color(0xFFB7FF3D), Color(0xFF3DFFC8)),
            )
            val colors = variants[index % 3]
            val w = size.width / 3f
            colors.forEachIndexed { i, c ->
                drawRect(
                    color = c,
                    topLeft = Offset(left + i * w, y),
                    size = Size(w, h),
                )
                drawTxt(
                    textMeasurer = measurer,
                    text = "背景色块 $index-$i",
                    topLeft = Offset(left + i * w + 26f, y + 26f),
                    style = TextStyle(color = Color(0xFF111111), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        1 -> {
            drawRect(Color(0xFF101018), topLeft = Offset(left, y), size = Size(size.width, h))
            val lines = listOf(
                Triple("标题：文字可读性基准 $index（16sp SemiBold）", 16f, 0xFFF2F2F7),
                Triple("正文 14sp：模糊后这行还能读吗？读不清说明半径过大或对比度不够。", 14f, 0xFFCFCFD8),
                Triple("小字 11sp：模糊会把字重打散，形成灰雾。", 11f, 0xFF9A9AA8),
                Triple("极小 9sp：0123456789 ABC abc", 9f, 0xFF7C7C8A),
            )
            var ty = y + 30f
            lines.forEach { (s, sp, argb) ->
                drawTxt(
                    textMeasurer = measurer,
                    text = s,
                    topLeft = Offset(left + 30f, ty),
                    style = TextStyle(color = Color(argb), fontSize = sp.sp),
                )
                ty += sp * 2.625f * 1.6f
            }
        }

        2 -> {
            drawRect(Color(0xFF1B1B22), topLeft = Offset(left, y), size = Size(size.width, h))
            val cell = 26f
            val accent = if (index % 2 == 0) Color(0xFFFFD23D) else Color(0xFF7DFF3D)
            val cols = (size.width / cell).toInt() + 2
            val rows = (h / cell).toInt() + 1
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if ((r + c) % 2 == 0) {
                        drawRect(
                            color = if (r % 4 < 2) accent.copy(alpha = 0.85f) else Color(0xFF2A2A36),
                            topLeft = Offset(left + c * cell, y + r * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
            drawTxt(
                textMeasurer = measurer,
                text = "硬边棋盘（2dp 级细节）",
                topLeft = Offset(left + 20f, y + 20f),
                style = TextStyle(color = Color.White, fontSize = 11.sp),
            )
        }

        3 -> {
            drawRect(
                Brush.horizontalGradient(listOf(Color(0xFF0B3D2E), Color(0xFF0B2E3D))),
                topLeft = Offset(left, y),
                size = Size(size.width, h),
            )
            val cx = left + size.width * 0.28f
            val cy = y + h * 0.5f
            repeat(5) { i ->
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFF3DFFE0) else Color(0xFFFF3D8A),
                    radius = 52f * 2.625f - i * 9f * 2.625f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 5f * 2.625f),
                )
            }
            drawCircle(color = Color.White, radius = 5f * 2.625f, center = Offset(cx, cy))
            drawRect(
                color = Color(0xFFFFD23D),
                topLeft = Offset(left + size.width * 0.62f, y + h * 0.28f),
                size = Size(80f * 2.625f, 80f * 2.625f),
            )
        }

        4 -> {
            drawRect(Color(0xFF120A1E), topLeft = Offset(left, y), size = Size(size.width, h))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6A3D), Color(0x00FF6A3D)),
                    center = Offset(left + size.width * 0.2f, y + h * 0.2f),
                    radius = size.width * 0.55f,
                ),
                topLeft = Offset(left, y),
                size = Size(size.width, h),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3D9BFF), Color(0x003D9BFF)),
                    center = Offset(left + size.width * 0.8f, y + h * 0.75f),
                    radius = size.width * 0.6f,
                ),
                topLeft = Offset(left, y),
                size = Size(size.width, h),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFC13DFF), Color(0x00C13DFF)),
                    center = Offset(left + size.width * 0.55f, y + h * 0.2f),
                    radius = size.width * 0.45f,
                ),
                topLeft = Offset(left, y),
                size = Size(size.width, h),
            )
            drawTxt(
                textMeasurer = measurer,
                text = "图片感渐变（判断发灰 / 丢饱和）",
                topLeft = Offset(left + 20f, y + 20f),
                style = TextStyle(color = Color.White, fontSize = 11.sp),
            )
        }

        else -> {
            drawRect(Color(0xFF0C0C12), topLeft = Offset(left, y), size = Size(size.width, h))
            drawTxt(
                textMeasurer = measurer,
                text = "色相条（判断饱和度补偿是否过冲）",
                topLeft = Offset(left + 26f, y + 16f),
                style = TextStyle(color = Color(0xFFD8D8E2), fontSize = 11.sp),
            )
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                        Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
                    )
                ),
                topLeft = Offset(left + 26f, y + 56f),
                size = Size(size.width - 52f, 160f),
            )
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.White)),
                topLeft = Offset(left + 200f, y + 240f),
                size = Size(size.width - 260f, 46f),
            )
            drawTxt(
                textMeasurer = measurer,
                text = "黑→白灰阶",
                topLeft = Offset(left + 26f, y + 240f),
                style = TextStyle(color = Color(0xFFB0B0BE), fontSize = 10.sp),
            )
        }
    }
}

/**
 * 安全版 drawText：Canvas 局部坐标可能落在画布外（背景是"世界坐标 + 偏移"），
 * 直接 drawText 会用 (画布尺寸 - topLeft) 推导约束，越界就抛 maxHeight(-N)。
 */
private fun DrawScope.drawTxt(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle,
) {
    if (topLeft.x >= size.width || topLeft.y >= size.height || topLeft.y < -120f) return
    val w = (size.width - topLeft.x).coerceAtLeast(1f)
    val h = (size.height - topLeft.y).coerceAtLeast(1f)
    drawText(
        textMeasurer = textMeasurer,
        text = text,
        topLeft = topLeft,
        style = style,
        size = Size(w, minOf(h, 600f)),
    )
}