package dev.myco.android.ui.m5.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.rememberSuperellipse
import kotlin.math.roundToInt

/**
 * 背景捕获句柄：一个 GraphicsLayer（RenderNode）+ 它在 root 坐标系里的原点。
 *
 * 原点很重要：玻璃面板要把"捕获层"对齐到自己的位置上，必须知道捕获层是画在哪个坐标系的。
 * 所有面板都按 root 坐标做补偿，因此可以在任意嵌套布局里正确对齐（滚动列表也行）。
 */
class LayerCapture {
    lateinit var layer: GraphicsLayer
    var originInRoot: Offset by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberLayerCapture(): LayerCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LayerCapture().also { it.layer = layer } }
}

/**
 * 把 content 画进 capture 层，同时原样画出来 —— 这是 Compose 1.7 的"捕获背景"写法。
 * 同一帧内必须 record 一次再 drawLayer 一次。
 */
@Composable
fun RecordInto(
    capture: LayerCapture,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val logged = remember { booleanArrayOf(false) }
    Box(
        modifier
            .onGloballyPositioned { capture.originInRoot = it.positionInRoot() }
            .drawWithContent {
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                if (w > 0 && h > 0) {
                    capture.layer.record(this, layoutDirection, IntSize(w, h)) {
                        this@drawWithContent.drawContent()
                    }
                    if (!logged[0]) {
                        logged[0] = true
                        android.util.Log.i(
                            GLASSLAB_TAG,
                            "RECORD size=${w}x$h origin=${capture.originInRoot} layer=$capture",
                        )
                    }
                    drawLayer(capture.layer)
                } else {
                    this@drawWithContent.drawContent()
                }
            },
        content = content,
    )
}

/**
 * 一块"液态玻璃"面板。
 *
 * 渲染管线（从下到上）：
 *   1. 采样层：drawLayer(背景捕获) + 平移补偿 → 正好是"面板背后那块像素"
 *   2. graphicsLayer { renderEffect = blur → colorMatrix }  ← 真 backdrop 模糊
 *   3. （可选）graphicsLayer { renderEffect = AGSL }       ← 对"自己的内容"做位移（伪折射）
 *   4. 锐利叠加层：tint / 磨砂噪声 / 内阴影 / 边缘高光
 *   5. 文字与交互内容（保持锐利）
 *
 * @param sampleLocalTopLeft 非空时，强制采样捕获层里固定的那块区域（用于"同一背景内容做多档对比"）
 */
@Composable
fun GlassPanel(
    spec: GlassSpec,
    capture: LayerCapture,
    modifier: Modifier = Modifier,
    sampleLocalTopLeft: Offset? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
        val pxSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val shape = rememberSuperellipse(spec.cornerDp.dp, spec.exponent)

        var posInRoot by remember { mutableStateOf(Offset.Zero) }

        val blurPx = with(density) { spec.blurDp.dp.toPx() }
        val refractPx = with(density) { spec.refractDp.dp.toPx() }

        val blurEffect: RenderEffect? = remember(blurPx, spec.sat, spec.bright) {
            buildBlurPlusColorEffect(blurPx, spec.sat, spec.bright)
        }
        val refractBuild: RefractBuild = if (spec.refractDp > 0f) {
            remember(pxSize.width, pxSize.height, refractPx, spec.refractEdge, spec.exponent) {
                buildRefractEffect(
                    width = pxSize.width.toFloat(),
                    height = pxSize.height.toFloat(),
                    refractPx = refractPx,
                    edge = spec.refractEdge,
                    exponent = spec.exponent,
                )
            }
        } else {
            remember { RefractBuild(null, "未启用") }
        }
        LaunchedEffect(refractBuild) {
            if (spec.refractDp > 0f) {
                GlassLabDiag.agslStatus = refractBuild.status
                GlassLabDiag.agslError = refractBuild.error
            }
        }
        LaunchedEffect(blurEffect, sampleLocalTopLeft) {
            android.util.Log.i(
                GLASSLAB_TAG,
                "PANEL '${spec.label}' blurDp=${spec.blurDp} blurEffect=${blurEffect != null} " +
                    "refract=${refractBuild.effect != null} sampleTopLeft=$sampleLocalTopLeft",
            )
        }

        val outlinePath: Path? = remember(shape, pxSize, layoutDir, density) {
            val o = shape.createOutline(
                Size(pxSize.width.toFloat(), pxSize.height.toFloat()),
                layoutDir,
                density,
            )
            (o as? Outline.Generic)?.path
        }

        val core: @Composable BoxScope.() -> Unit = {
            if (!spec.legacy) {
                // 1+2+3：采样背景 → 模糊/色彩补偿 →（可选）AGSL 折射
                val sampleModifier = Modifier
                    .matchParentSize()
                    .then(
                        if (refractBuild.effect != null && !spec.refractWholePanel) {
                            Modifier.graphicsLayer { renderEffect = refractBuild.effect }
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (blurEffect != null) {
                            Modifier.graphicsLayer { renderEffect = blurEffect }
                        } else {
                            Modifier
                        }
                    )
                    .drawBehind {
                        val tx: Float
                        val ty: Float
                        if (sampleLocalTopLeft != null) {
                            tx = -sampleLocalTopLeft.x
                            ty = -sampleLocalTopLeft.y
                        } else {
                            tx = capture.originInRoot.x - posInRoot.x
                            ty = capture.originInRoot.y - posInRoot.y
                        }
                        translate(tx, ty) { drawLayer(capture.layer) }
                    }
                Box(sampleModifier)
            }
            // 4：锐利叠加层
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind { drawGlassOverlay(spec, outlinePath) }
            )
            // 5：文字
            PanelLabels(spec)
            content()
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { posInRoot = it.positionInRoot() }
                .clip(shape)
        ) {
            if (spec.refractDp > 0f && spec.refractWholePanel && refractBuild.effect != null) {
                // 反例：折射作用到整块（连文字一起扭曲）
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { renderEffect = refractBuild.effect }
                ) {
                    Box(Modifier.matchParentSize()) { core() }
                }
            } else {
                core()
            }
        }
    }
}

@Composable
private fun BoxScope.PanelLabels(spec: GlassSpec) {
    if (spec.label.isNotEmpty()) {
        Column(Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = spec.label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 4f)),
            )
            if (spec.sub.isNotEmpty()) {
                Text(
                    text = spec.sub,
                    color = Color(0xEEFFFFFF),
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
                )
            }
        }
    }
    if (spec.api.isNotEmpty()) {
        Text(
            text = spec.api,
            color = Color(0xFFB7F7FF),
            fontSize = 8.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .drawBehind { drawRect(Color(0x99000000)) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/**
 * 叠加层绘制：tint / 磨砂噪声 / 内阴影 / 边缘高光。
 * 全部为锐利绘制（不进模糊），保证玻璃"表面"有实体感。
 */
private fun DrawScope.drawGlassOverlay(spec: GlassSpec, path: Path?) {
    // tint：真实玻璃总有一点染色，模糊图直接透出来会显得"脏"
    if (spec.legacy) {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    spec.tintColor.copy(alpha = 0.30f),
                    spec.tintColor.copy(alpha = 0.12f),
                )
            )
        )
        // 假玻璃的"糊"：一条斜向柔光
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.00f),
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.00f),
                ),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            )
        )
    } else {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    spec.tintColor.copy(alpha = spec.tintAlpha),
                    spec.tintColor.copy(alpha = (spec.tintAlpha * 0.5f).coerceIn(0f, 1f)),
                )
            )
        )
    }

    // 磨砂噪声（程序生成 128×128 平铺）
    if (spec.noise > 0f) {
        drawIntoCanvas { canvas ->
            val paint = GlassLabNoise.paint
            paint.alpha = (spec.noise * 255f).roundToInt().coerceIn(0, 255)
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }

    // 内阴影：贴边压暗，让玻璃有厚度（不然像贴纸）
    if (spec.innerShadow > 0f && path != null) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = spec.innerShadow),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.62f,
            ),
            style = Stroke(width = 5.dp.toPx()),
        )
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = spec.innerShadow * 0.9f),
                ),
                startY = size.height * 0.45f,
                endY = size.height,
            ),
            style = Stroke(width = 3.dp.toPx()),
        )
    }

    // 边缘高光（specular rim）：左上亮 → 右下弱
    if (spec.rim > 0f && path != null) {
        val a = (0.45f * spec.rim).coerceIn(0f, 0.95f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = a),
                    Color.White.copy(alpha = a * 0.30f),
                    Color.White.copy(alpha = a * 0.70f),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.85f, size.height),
            ),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}
