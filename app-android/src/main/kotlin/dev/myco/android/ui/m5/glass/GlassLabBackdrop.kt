package dev.myco.android.ui.m5.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * "有内容的后台"：滚动的彩色列表 / 图片感渐变 / 文字 / 棋盘 / 色相条。
 *
 * 背景必须同时包含：
 *  - 高饱和色块（判断模糊后是否发灰）
 *  - 小字号文字（判断可读性掉到什么程度）
 *  - 硬边棋盘（判断边缘/折射是否真的在位移像素）
 *  - 色相渐变条（判断饱和度补偿有没有过冲）
 */
@Composable
fun GlassBackdrop(
    scrollPx: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowGap = with(density) { 10.dp.toPx() }
    Column(
        modifier
            .clipToBounds()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF31135E), Color(0xFF0E1B2C), Color(0xFF3A0F2E)),
                    start = Offset.Zero,
                    end = Offset(1400f, 2600f),
                )
            )
            .offset { IntOffset(0, -scrollPx.roundToInt()) },
    ) {
        for (i in 0 until 48) {
            BackdropRow(index = i)
            Spacer(Modifier.height(with(density) { rowGap.toDp() }))
        }
    }
}

@Composable
private fun BackdropRow(index: Int) {
    when (index % 6) {
        0 -> ColorBlockRow(index)
        1 -> TextRow(index)
        2 -> CheckerRow(index)
        3 -> RingRow(index)
        4 -> MeshRow(index)
        else -> HueRow(index)
    }
}

@Composable
private fun ColorBlockRow(index: Int) {
    val variants = listOf(
        listOf(Color(0xFFFF3D7F), Color(0xFFFFA63D), Color(0xFFFFE93D)),
        listOf(Color(0xFF3DDCFF), Color(0xFF3D7BFF), Color(0xFF9A3DFF)),
        listOf(Color(0xFF3DFFA8), Color(0xFFB7FF3D), Color(0xFF3DFFC8)),
    )
    val colors = variants[index % variants.size]
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        colors.forEachIndexed { i, c ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .weight(1f)
                    .height(150.dp)
                    .background(c),
            ) {
                Text(
                    text = "背景色块 ${index}-$i",
                    color = Color(0xFF111111),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun TextRow(index: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF101018)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "标题：文字可读性基准 $index（16sp SemiBold）",
                color = Color(0xFFF2F2F7),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "正文 14sp：模糊后这行是否还能读？如果读不清，说明半径过大或者需要更高的 tint/对比度。",
                color = Color(0xFFCFCFD8),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "小字 11sp：模糊会把字重打散，形成灰雾。",
                color = Color(0xFF9A9AA8),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "极小 9sp：0123456789 ABC abc",
                color = Color(0xFF7C7C8A),
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun CheckerRow(index: Int) {
    val accent = if (index % 2 == 0) Color(0xFFFFD23D) else Color(0xFF7DFF3D)
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFF1B1B22)),
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val cell = 10.dp.toPx()
            val cols = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if ((r + c) % 2 == 0) {
                        drawRect(
                            color = if (r % 4 < 2) accent.copy(alpha = 0.85f) else Color(0xFF2A2A36),
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
        Text(
            "硬边棋盘（2dp 级细节）",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RingRow(index: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF0B3D2E), Color(0xFF0B2E3D)))),
    ) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val r = 52.dp.toPx()
            for (i in 0 until 5) {
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFF3DFFE0) else Color(0xFFFF3D8A),
                    radius = r - i * 9.dp.toPx(),
                    center = Offset(size.width * 0.28f, size.height * 0.5f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx()),
                )
            }
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(size.width * 0.28f, size.height * 0.5f),
            )
            drawRect(
                color = Color(0xFFFFD23D),
                topLeft = Offset(size.width * 0.62f, size.height * 0.28f),
                size = Size(80.dp.toPx(), 80.dp.toPx()),
            )
        }
        Text(
            "圆环 / 方形（判断折射与硬边）",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MeshRow(index: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(Color(0xFF120A1E)),
    ) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFF6A3D), Color(0x00FF6A3D)),
                    center = Offset(size.width * 0.2f, size.height * 0.2f),
                    radius = size.width * 0.55f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3D9BFF), Color(0x003D9BFF)),
                    center = Offset(size.width * 0.8f, size.height * 0.75f),
                    radius = size.width * 0.6f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFC13DFF), Color(0x00C13DFF)),
                    center = Offset(size.width * 0.55f, size.height * 0.2f),
                    radius = size.width * 0.45f,
                )
            )
        }
        Text(
            "图片感渐变（判断发灰 / 丢饱和）",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun HueRow(index: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Color(0xFF0C0C12)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "色相条（判断饱和度补偿是否过冲）",
                color = Color(0xFFD8D8E2),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                                Color(0xFFFF0000),
                            )
                        )
                    ),
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Text("黑→白灰阶：", color = Color(0xFFB0B0BE), fontSize = 10.sp, modifier = Modifier.width(76.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Black, Color.White))),
                )
            }
        }
    }
}
