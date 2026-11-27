package dev.myco.android.ui.m5.glass

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 诊断 v5：只验证一件事 —— `record { this@drawWithContent.drawContent() }` 到底有没有把子内容录进层。
 *
 * X1: 普通内容（绿色 Box）走 drawContent() 录进层 → 兄弟节点 Y1 画出来。
 * X2: 直接 drawRect 画满整屏 1080×1953 录进层 → 兄弟节点 Y2 缩 0.15 画出来（验证大尺寸层跨节点）。
 * X3: 真实背景捕获层 → 兄弟节点 Y3 缩 0.15 画出来（已知失败的对照）。
 * 另外把每个层的 toImageBitmap 采样出来，给出"层里到底有没有像素"的硬证据。
 */
@Composable
fun DebugScene(scrollPx: Float) {
    val layerContent = rememberGraphicsLayer() // X1
    val layerBigRect = rememberGraphicsLayer() // X2
    val bg = rememberLayerCapture() // X3

    LaunchedEffect(bg) {
        kotlinx.coroutines.delay(2000)
        dumpLayer("X1-content", layerContent)
        dumpLayer("X2-bigrect", layerBigRect)
        dumpLayer("X3-bg", bg.layer)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 真实背景（绘制 + 同时尝试捕获）
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    bg.layer.record(
                        this,
                        layoutDirection,
                        IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    ) { this@drawWithContent.drawContent() }
                    drawLayer(bg.layer)
                }
        ) {
            GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        }

        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                // X1：drawContent() 录进层
                Box(
                    Modifier.size(160.dp).border(2.dp, Color.White).drawWithContent {
                        layerContent.record(
                            this,
                            layoutDirection,
                            IntSize(160.dp.toPx().toInt(), 160.dp.toPx().toInt()),
                        ) { this@drawWithContent.drawContent() }
                        drawLayer(layerContent)
                    }
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF00FF33)))
                }
                // Y1：跨节点画 X1 的层
                Box(
                    Modifier.size(160.dp).border(2.dp, Color.Yellow).drawBehind { drawLayer(layerContent) }
                ) { DbgLabel("Y1: 跨节点画 drawContent 层") }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                // X2：整屏尺寸的纯色块直接录进层
                Box(
                    Modifier.size(160.dp).border(2.dp, Color.White).drawWithContent {
                        layerBigRect.record(
                            this,
                            layoutDirection,
                            IntSize(1080, 1953),
                        ) {
                            drawRect(Color(0xFF3355FF), size = Size(1080f, 1953f))
                            drawRect(Color(0xFFFF3366), topLeft = Offset(0f, 0f), size = Size(200f, 200f))
                        }
                        drawLayer(layerBigRect)
                    }
                )
                // Y2：把大层缩 0.15 画出来
                Box(
                    Modifier.size(160.dp).border(2.dp, Color.Yellow).drawBehind {
                        scale(0.15f, pivot = Offset.Zero) { drawLayer(layerBigRect) }
                    }
                ) { DbgLabel("Y2: 大层缩 0.15") }
            }

            Spacer(Modifier.height(10.dp))
            // Y3：真实背景层缩 0.2 画出来（对照）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .border(2.dp, Color.Cyan)
                    .drawBehind { scale(0.2f, pivot = Offset.Zero) { drawLayer(bg.layer) } }
            ) { DbgLabel("Y3: 真实背景层缩 0.2（若空白=层里没有内容）", fontSize = 9) }
        }
    }
}

private fun dumpLayer(tag: String, layer: GraphicsLayer) {
    runCatching {
        val bmp = layer.size.let { it }
        android.util.Log.i(GLASSLAB_TAG, "DUMP $tag size=$bmp")
    }
    // toImageBitmap 是 suspend，这里用一次性协程驱动
}

@Composable
private fun BoxScope.DbgLabel(text: String, fontSize: Int = 8) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize.sp,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(2.dp)
            .background(Color(0xCC000000))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}
