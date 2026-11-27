package dev.myco.android.ui.m5.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.rememberSuperellipse
import kotlin.math.roundToInt

/**
 * 「重绘式 backdrop」：把**同一份背景内容**按面板位置做偏移后再画一遍，然后给这一份挂 RenderEffect 模糊。
 *
 * 参数 (modifier, offset, scale)：
 *  - offset = 背景内容里**应当落在该 modifier 左上角**的场景坐标（root px）
 *  - scale  = 该份背景要以多少倍率绘制（1=全分辨率，0.5=半分辨率 → 供"降分辨率省填充率"实验）
 *
 * 这是本机上唯一能真正跑通的"真 backdrop"实现（record+drawLayer 拿不到内容，见报告）。
 */
typealias BackdropPainter = @Composable (Modifier, Offset, Float) -> Unit

/**
 * 一块"液态玻璃"面板。
 *
 * 渲染管线（从下到上）：
 *   1. 重绘背景：按 offset 偏移，正好是"面板背后那块像素"
 *   2. graphicsLayer { renderEffect = blur → colorMatrix }  ← 真 backdrop 模糊 + 饱和度/亮度补偿
 *   3. （可选）graphicsLayer { renderEffect = AGSL }       ← 液体折射/伪折射（只作用于自身内容）
 *   4. 锐利叠加层：tint / 磨砂噪声 / 内阴影 / 边缘高光
 *   5. 文字与交互内容（保持锐利）
 *
 * @param sampleAtScene 非空时，强制把该场景坐标（root px）的内容显示在面板左上角（用于"同一背景内容多档对比"）
 */
@Composable
fun GlassPanel(
    spec: GlassSpec,
    backdrop: BackdropPainter,
    modifier: Modifier = Modifier,
    sampleAtScene: Offset? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val layoutDir = LocalLayoutDirection.current
        val pxSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val shape = rememberSuperellipse(spec.cornerDp.dp, spec.exponent)
        val renderScale = spec.renderScale.coerceIn(0.2f, 1f)

        var posInRoot by remember { mutableStateOf(Offset.Zero) }

        val blurPx = with(density) { spec.blurDp.dp.toPx() }
        val refractPx = with(density) { spec.refractDp.dp.toPx() }
        val dispPx = with(density) { spec.dispersion.dp.toPx() }

        // 低分辨率渲染时，effect 节点尺寸 = 面板尺寸 × renderScale
        val effectW = (pxSize.width * renderScale).roundToInt().coerceAtLeast(1)
        val effectH = (pxSize.height * renderScale).roundToInt().coerceAtLeast(1)

        val blurEffect: RenderEffect? = remember(blurPx, spec.sat, spec.bright) {
            buildBlurPlusColorEffect(blurPx, spec.sat, spec.bright)
        }
        val refractBuild: RefractBuild = if (spec.refractDp > 0f) {
            remember(
                effectW,
                effectH,
                refractPx,
                dispPx,
                spec.refractEdge,
                spec.exponent,
                spec.liquidShader,
                spec.rim,
                spec.innerShadow,
            ) {
                if (spec.liquidShader) {
                    buildLiquidEffect(
                        width = effectW.toFloat(),
                        height = effectH.toFloat(),
                        refractPx = refractPx,
                        dispersionPx = dispPx,
                        edge = spec.refractEdge,
                        exponent = spec.exponent,
                        rim = spec.rim * 1.4f,
                        shadow = spec.innerShadow * 1.6f,
                    )
                } else {
                    buildRefractEffect(
                        width = effectW.toFloat(),
                        height = effectH.toFloat(),
                        refractPx = refractPx,
                        edge = spec.refractEdge,
                        exponent = spec.exponent,
                    )
                }
            }
        } else {
            remember { RefractBuild(null, "未启用") }
        }
        LaunchedEffect(refractBuild) {
            if (spec.refractDp > 0f) {
                GlassLabDiag.agslStatus = refractBuild.status
                GlassLabDiag.agslError = refractBuild.error
                android.util.Log.i(
                    GLASSLAB_TAG,
                    "AGSL ${spec.label}: status=${refractBuild.status} err=${refractBuild.error}",
                )
            }
        }

        val outlinePath: Path? = remember(shape, pxSize, layoutDir, density) {
            val o = shape.createOutline(
                Size(pxSize.width.toFloat(), pxSize.height.toFloat()),
                layoutDir,
                density,
            )
            (o as? Outline.Generic)?.path
        }

        // 要落在面板左上角的内容坐标（root px）；交给 BackdropPainter
        val backdropOffset = sampleAtScene ?: posInRoot
        val capture = LocalGlassCapture.current
        val useCapture = capture != null && !spec.forceRepaint && !spec.legacy

        // 1+2+3：重绘背景 → blur →（可选）AGSL
        val texture: @Composable BoxScope.() -> Unit = {
            var m = Modifier.fillMaxSize()
            if (refractBuild.effect != null && !spec.refractWholePanel) {
                m = m.graphicsLayer { renderEffect = refractBuild.effect }
            }
            if (blurEffect != null) {
                m = m.graphicsLayer { renderEffect = blurEffect }
            }
            if (useCapture) {
                // 捕获路径：把源层按 root 对齐画进来（只多一次 blur pass，不重绘背景）
                val t = capture!!.originInRoot - backdropOffset
                Box(
                    m.drawBehind {
                        translate(t.x, t.y) { drawLayer(capture.layer) }
                    }
                )
            } else {
                Box(m) {
                    backdrop(Modifier.fillMaxSize(), backdropOffset, renderScale)
                }
            }
        }

        val core: @Composable BoxScope.() -> Unit = {
            if (!spec.legacy) {
                if (renderScale < 0.999f) {
                    // 先以低分辨率渲染到小节点，再整体放大：填充率/模糊核成本按 renderScale² 下降
                    val innerW = with(density) { effectW.toDp() }
                    val innerH = with(density) { effectH.toDp() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1f / renderScale
                                scaleY = 1f / renderScale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        Box(Modifier.requiredSize(innerW, innerH)) { texture() }
                    }
                } else {
                    texture()
                }
            }
            // 4：锐利叠加层（tint / 噪声 / 内阴影 / 边缘高光）
            Box(
                Modifier
                    .fillMaxSize()
                    .drawOverlay { drawGlassOverlay(spec, outlinePath) }
            )
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
                        .fillMaxSize()
                        .graphicsLayer { renderEffect = refractBuild.effect }
                ) {
                    Box(Modifier.fillMaxSize()) { core() }
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
                .drawOverlay { drawRect(Color(0x99000000)) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/** 小工具：drawBehind 的别名（避免与内容绘制混淆）。 */
internal fun Modifier.drawOverlay(block: DrawScope.() -> Unit): Modifier = this.drawBehind(block)

/**
 * 叠加层绘制：tint / 磨砂噪声 / 内阴影 / 边缘高光。
 * 全部为锐利绘制（不进模糊），保证玻璃"表面"有实体感。
 */
private fun DrawScope.drawGlassOverlay(spec: GlassSpec, path: Path?) {
    if (spec.legacy) {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    spec.tintColor.copy(alpha = 0.30f),
                    spec.tintColor.copy(alpha = 0.12f),
                )
            )
        )
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

    if (spec.noise > 0f) {
        drawIntoCanvas { canvas ->
            val paint = GlassLabNoise.paint
            paint.alpha = (spec.noise * 255f).roundToInt().coerceIn(0, 255)
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }

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
