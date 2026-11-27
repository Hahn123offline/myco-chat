// 背景捕获层（G-19 路线 B 的"源"）：把背景内容录进一个 GraphicsLayer，供同树的玻璃取像素。
//
// 关键实现（`docs/spec/ref/backdrop-blur-deep.md` §1.5，RCA 修正后的唯一正确写法）：
//   `layer.record(IntSize(w, h)) { this@drawWithContent.drawContent() }`
// **必须**用 `DrawScope.record` **扩展函数**（两个参数）。它会把外层 DrawScope 的 canvas 临时
// 重定向到层的 recording canvas，所以 `drawContent()` 真的把子内容画进了层。
// 反例（本仓库踩过）：`GraphicsLayer.record(density, layoutDirection, size, block)` 成员函数
// 只把参数记到层上，不重定向外层 canvas → `drawContent()` 画到窗口、层里为空（玻璃画出来是空的）。
//
// 使用形态（"一个 Box 包背景 + 同树浮层取层"）：
// ```
// GlassBackdrop(Modifier.fillMaxSize()) {
//     ChatContent()            // 背景内容：录进层 + 上屏（原样）
// } overlay = {
//     M5GlassSheet(...) { ... } // 浮层：同一 Compose 树 → 能取到上面的层
// }
// ```
// ⚠️ 独立 window（Dialog/Popup）拿不到这个层（`GlassStructuralLimits` 已写明）。
package dev.myco.android.ui.m5.primitives

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 背景捕获句柄：一个 `GraphicsLayer`（API 29+ 就是公开 `RenderNode`）+ 它在 root 坐标系的原点。
 *
 * 一个 [GlassBackdrop] 只录**一份**源层；同树里的 N 块玻璃各自只做"裁自己背后那块 + blur"，
 * 不必各自重绘背景（capture 相对 repaint 的结构优势，D-67）。
 */
@Stable
class GlassBackdropState internal constructor(internal val layer: GraphicsLayer) {
    /** 源层左上角在 root 坐标系的位置（随布局更新；玻璃用它做坐标对齐）。 */
    var originInRoot: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** 源层在 root 坐标系里的尺寸（用于判断玻璃哪几条边与源层（屏幕）齐平）。 */
    var sizeInRoot: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    /** 源层至少成功录过一帧（首帧之前玻璃不画，避免画一格空）。 */
    var hasContent: Boolean = false
        internal set
}

/** 创建一个可跨玻璃复用的背景捕获句柄。 */
@Composable
fun rememberGlassBackdrop(): GlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdropState(layer) }
}

/**
 * 当前 Compose 树里最近的背景捕获层。`GlassSurface` 从这里取像素；
 * 为 null（没包 `GlassBackdrop`，或在独立 window 里）时玻璃**不伪造**，走 A 不透明。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

/**
 * 背景捕获宿主。
 *
 * @param background 背景内容：**每帧**录进源层，并原样上屏（用户看到的还是它）。
 * @param overlay 同树浮层内容：能通过 [LocalGlassBackdrop] 取到源层。
 * @param enabled false = 不录制（不支持的设备上省掉一次背景重绘），背景照常上屏。
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    state: GlassBackdropState = rememberGlassBackdrop(),
    enabled: Boolean = rememberGlassSupported(),
    background: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    state.originInRoot = it.positionInRoot()
                    state.sizeInRoot = IntSize(it.size.width, it.size.height)
                }
                .drawWithContent {
                    val w = size.width.roundToInt()
                    val h = size.height.roundToInt()
                    if (!enabled || w <= 0 || h <= 0 || state.layer.isReleased) {
                        // 不支持捕获 / 尺寸未就绪：只上屏，不录层（G-19 该走 A）
                        this@drawWithContent.drawContent()
                        return@drawWithContent
                    }
                    // ✅ DrawScope.record 扩展（canvas 重定向）—— 见文件头 RCA
                    state.layer.record(IntSize(w, h)) {
                        this@drawWithContent.drawContent()
                    }
                    state.hasContent = true
                    drawLayer(state.layer)
                },
            content = background,
        )
        CompositionLocalProvider(LocalGlassBackdrop provides state) {
            overlay()
        }
    }
}
