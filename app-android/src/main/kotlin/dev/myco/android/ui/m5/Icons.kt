package dev.myco.android.ui.m5

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M5 矢量图标：24dp 网格、1.6dp 线性、圆头圆角，直接 Canvas 自绘（不引图标库）。
 * 用 M5Icon(icon, tint) 绘制；不再用文字或 emoji 代图标。
 */
class M5IconSpec(val name: String, val draw: DrawScope.(Color) -> Unit)

@Composable
fun M5Icon(
    icon: M5IconSpec,
    modifier: Modifier = Modifier,
    tint: Color,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        withTransform({ scale(u, u, pivot = Offset.Zero) }) {
            icon.draw(this, tint)
        }
    }
}

private fun DrawScope.line(c: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.6f, cap = StrokeCap.Round)

private fun DrawScope.ring(c: Color, cx: Float, cy: Float, r: Float, width: Float = 1.6f) =
    drawCircle(c, r, Offset(cx, cy), style = Stroke(width))

private fun DrawScope.path(c: Color, block: Path.() -> Unit) =
    drawPath(
        Path().apply(block),
        c,
        style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

object M5Icons {
    val menu = M5IconSpec("菜单") { c ->
        line(c, 5f, 7.5f, 19f, 7.5f)
        line(c, 5f, 12f, 15.5f, 12f)
        line(c, 5f, 16.5f, 19f, 16.5f)
    }

    val back = M5IconSpec("返回") { c ->
        path(c) { moveTo(15.2f, 5.5f); lineTo(8.7f, 12f); lineTo(15.2f, 18.5f) }
    }

    val close = M5IconSpec("关闭") { c ->
        line(c, 6.5f, 6.5f, 17.5f, 17.5f)
        line(c, 17.5f, 6.5f, 6.5f, 17.5f)
    }

    val more = M5IconSpec("更多") { c ->
        drawCircle(c, 1.9f, Offset(5.5f, 12f))
        drawCircle(c, 1.9f, Offset(12f, 12f))
        drawCircle(c, 1.9f, Offset(18.5f, 12f))
    }

    val search = M5IconSpec("搜索") { c ->
        ring(c, 10.5f, 10.5f, 6.2f)
        line(c, 15.2f, 15.2f, 20f, 20f)
    }

    val plus = M5IconSpec("新增") { c ->
        line(c, 12f, 5f, 12f, 19f)
        line(c, 5f, 12f, 19f, 12f)
    }

    val send = M5IconSpec("发送") { c ->
        path(c) {
            moveTo(4.5f, 11.5f); lineTo(20f, 4.5f); lineTo(14f, 20f)
            lineTo(11.2f, 13.2f); close()
        }
    }

    val stop = M5IconSpec("停止") { c ->
        drawRoundRect(
            c, Offset(8.5f, 8.5f), Size(7f, 7f),
            cornerRadius = CornerRadius(1.4f), style = Stroke(1.6f),
        )
    }

    val mic = M5IconSpec("语音") { c ->
        drawRoundRect(
            c, Offset(9.6f, 4f), Size(4.8f, 9.6f),
            cornerRadius = CornerRadius(2.4f), style = Stroke(1.6f),
        )
        drawArc(
            c, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(6.6f, 6.4f), size = Size(10.8f, 10.8f),
            style = Stroke(1.6f),
        )
        line(c, 12f, 17.2f, 12f, 20.4f)
    }

    val check = M5IconSpec("完成") { c ->
        path(c) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    }

    val chevronDown = M5IconSpec("展开") { c ->
        path(c) { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    }

    val chevronRight = M5IconSpec("进入") { c ->
        path(c) { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
    }

    val clock = M5IconSpec("思考") { c ->
        ring(c, 12f, 12f, 8.4f)
        path(c) { moveTo(12f, 7.2f); lineTo(12f, 12.2f); lineTo(15.2f, 14.2f) }
    }

    val globe = M5IconSpec("网页") { c ->
        ring(c, 12f, 12f, 8.4f)
        line(c, 3.6f, 12f, 20.4f, 12f)
        path(c) {
            moveTo(12f, 3.6f)
            quadraticBezierTo(16.6f, 12f, 12f, 20.4f)
            quadraticBezierTo(7.4f, 12f, 12f, 3.6f)
        }
    }

    val spark = M5IconSpec("记忆") { c ->
        path(c) {
            moveTo(12f, 4.5f)
            cubicTo(12.6f, 8.8f, 15.2f, 11.4f, 19.5f, 12f)
            cubicTo(15.2f, 12.6f, 12.6f, 15.2f, 12f, 19.5f)
            cubicTo(11.4f, 15.2f, 8.8f, 12.6f, 4.5f, 12f)
            cubicTo(8.8f, 11.4f, 11.4f, 8.8f, 12f, 4.5f)
            close()
        }
    }

    val settings = M5IconSpec("设置") { c ->
        line(c, 4f, 7f, 20f, 7f)
        line(c, 4f, 12f, 20f, 12f)
        line(c, 4f, 17f, 20f, 17f)
        ring(c, 9f, 7f, 2.2f)
        ring(c, 15.5f, 12f, 2.2f)
        ring(c, 8f, 17f, 2.2f)
    }

    val trash = M5IconSpec("删除") { c ->
        line(c, 5f, 6.5f, 19f, 6.5f)
        path(c) {
            moveTo(9.5f, 6.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.5f)
        }
        path(c) {
            moveTo(7f, 6.5f); lineTo(8f, 19.5f); lineTo(16f, 19.5f); lineTo(17f, 6.5f)
        }
        line(c, 10.5f, 10.5f, 10.5f, 15.5f)
        line(c, 13.5f, 10.5f, 13.5f, 15.5f)
    }

    val archive = M5IconSpec("归档") { c ->
        drawRoundRect(
            c, Offset(4.5f, 5.2f), Size(15f, 3.6f),
            cornerRadius = CornerRadius(1.4f), style = Stroke(1.6f),
        )
        path(c) {
            moveTo(6.5f, 8.8f); lineTo(6.5f, 19.2f); lineTo(17.5f, 19.2f); lineTo(17.5f, 8.8f)
        }
        line(c, 10.2f, 14f, 13.8f, 14f)
    }

    val copy = M5IconSpec("复制") { c ->
        drawRoundRect(
            c, Offset(9f, 9f), Size(9.5f, 9.5f),
            cornerRadius = CornerRadius(2f), style = Stroke(1.6f),
        )
        path(c) { moveTo(6f, 15f); lineTo(6f, 5.5f); lineTo(15f, 5.5f) }
    }
}
