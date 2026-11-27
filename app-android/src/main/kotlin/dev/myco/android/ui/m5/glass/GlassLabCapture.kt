package dev.myco.android.ui.m5.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 背景捕获（**RCA 修复后的正确写法**）。
 *
 * 关键：必须用 `DrawScope.record(graphicsLayer, size) { ... }`（DrawScope 成员扩展，
 * compose-ui 1.7.5 `DrawScope.kt`）——它会临时把外层 DrawScope 的 DrawContext.canvas 换成层的 canvas
 * 再执行 block，因此 `this@drawWithContent.drawContent()` 会把子内容真正画进**层里**。
 *
 * 之前失败的写法：直接调 `GraphicsLayer.record(density, layoutDirection, size, block)` 成员函数，
 * 不会重定向外层 canvas → 内容画到窗口、层为空。
 */
@Composable
fun RecordInto(
    capture: LayerCapture,
    modifier: Modifier = Modifier,
    /** 每 N 帧重新录制一次（N>1 时层里保留上一帧 → 用于"隔帧更新/陈旧模糊"测试） */
    recordEvery: Int = 1,
    content: @Composable BoxScope.() -> Unit,
) {
    val frame = remember { IntArray(1) }
    Box(
        modifier
            .onGloballyPositioned { capture.originInRoot = it.positionInRoot() }
            .drawWithContent {
                frame[0]++
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                if (w > 0 && h > 0) {
                    val doRecord = recordEvery <= 1 || frame[0] % recordEvery == 0
                    if (doRecord) {
                        // ✅ 扩展函数（RCA 修复）：会把本 DrawScope 的 canvas 重定向到层里
                        capture.layer.record(IntSize(w, h)) {
                            this@drawWithContent.drawContent()
                        }
                        capture.hasContent = true
                    }
                    drawLayer(capture.layer)
                } else {
                    this@drawWithContent.drawContent()
                }
            },
        content = content,
    )
}

/** 背景捕获句柄：一个 Compose GraphicsLayer（RenderNode）+ 它在 root 坐标系里的原点。 */
class LayerCapture {
    lateinit var layer: GraphicsLayer
    var originInRoot: Offset by mutableStateOf(Offset.Zero)
    var hasContent: Boolean = false
}

@Composable
fun rememberLayerCapture(): LayerCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LayerCapture().also { it.layer = layer } }
}

/** 当前场景是否用"捕获背景"作为玻璃的像素来源（`glass_capture=true`）。 */
val LocalGlassCaptureEnabled = compositionLocalOf { false }

/** 当前场景的捕获层（非空时玻璃面板优先用它，而不是重绘背景）。 */
val LocalGlassCapture = compositionLocalOf<LayerCapture?> { null }

/**
 * 场景背景宿主：根据 [LocalGlassCaptureEnabled] 决定是"只画背景"还是"画 + 录进捕获层"。
 * 捕获模式下，背景只画一次、所有玻璃共享同一份层（静态层只会被录一次）。
 */
@Composable
fun CaptureHost(
    modifier: Modifier = Modifier,
    recordEvery: Int = 1,
    background: @Composable BoxScope.(Modifier) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val captureEnabled = LocalGlassCaptureEnabled.current
    val capture = rememberLayerCapture()
    Box(modifier.fillMaxSize()) {
        val scene: @Composable BoxScope.() -> Unit = {
            if (captureEnabled) {
                RecordInto(capture, Modifier.fillMaxSize(), recordEvery) {
                    background(Modifier.fillMaxSize())
                }
            } else {
                background(Modifier.fillMaxSize())
            }
            content()
        }
        if (captureEnabled) {
            CompositionLocalProvider(LocalGlassCapture provides capture) { scene() }
        } else {
            scene()
        }
    }
}

/**
 * `glass_scene=capture`：RCA 验证 + capture/repaint A/B 对照。
 * 判据：玻璃里应出现「模糊后的真实背景」，且随背景滚动更新。
 */
@Composable
fun CaptureScene(scrollPx: Float) {
    CompositionLocalProvider(LocalGlassCaptureEnabled provides true) {
        GlassSceneFrame(scrollPx) { _, backdrop ->
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    "RCA 验证：源层用 DrawScope.record{} 录一次，玻璃用 drawLayer + BlurEffect。" +
                        "玻璃内应出现「模糊后的背景」并随滚动更新。",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC000000))
                        .padding(6.dp),
                )
                Spacer(Modifier.height(8.dp))
                GlassPanel(
                    spec = demoSpec("capture · blur 24dp", "源层只录一次，N 块玻璃共享", "API31+", blurDp = 24f),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                Spacer(Modifier.height(8.dp))
                GlassPanel(
                    spec = demoSpec("capture · blur 48dp", "大半径对照", "API31+", blurDp = 48f),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                Spacer(Modifier.height(8.dp))
                GlassPanel(
                    spec = demoSpec("repaint · blur 24dp", "同参数的重绘路径（对照）", "API31+", blurDp = 24f)
                        .copy(forceRepaint = true),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
            }
        }
    }
}
