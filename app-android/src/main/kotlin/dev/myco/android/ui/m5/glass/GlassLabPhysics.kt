package dev.myco.android.ui.m5.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * G-21 合规版「干净玻璃测试体」。
 *
 * 只做能从几何/物理数据算出来的东西：
 *  1. 真背景模糊（捕获层 + BlurEffect）
 *  2. 真像素位移折射：`uv += sdfNormal * f(distanceToEdge)`，f 只在边缘带内非零
 *  3. 色散：R/G/B 采样偏移不同
 *  4. 镜面高光：`max(dot(N, L), 0)^k`（N = SDF 梯度/法线，L = 固定光照方向）
 *  5. 内阴影：由 `distanceToEdge` 推出的边缘暗化
 *
 * **不做**：径向渐变"镜头"、月牙、外圈光晕、任何不是从公式算出来的高光/光斑。
 */
internal const val AGSL_PHYSICS_GLASS = """
uniform shader content;
uniform float2 size;
uniform float2 halfSize;
uniform float corner;
uniform float refractPx;
uniform float bandPx;
uniform float dispPx;
uniform float rimStrength;
uniform float shadowStrength;
uniform float2 lightDir;

float sdRoundRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float2 sdfNormal(float2 p) {
    float e = 0.75;
    float dx = sdRoundRect(p + float2(e, 0.0), halfSize, corner) -
               sdRoundRect(p - float2(e, 0.0), halfSize, corner);
    float dy = sdRoundRect(p + float2(0.0, e), halfSize, corner) -
               sdRoundRect(p - float2(0.0, e), halfSize, corner);
    float2 g = float2(dx, dy);
    return g / max(length(g), 0.0001);
}

half4 main(float2 coord) {
    float2 p = coord - size * 0.5;
    float d = sdRoundRect(p, halfSize, corner);   // <0 内部
    float distToEdge = max(-d, 0.0);
    float2 n = sdfNormal(p);

    // f 只在边缘带内非零：边缘 1 → 带内边界 0
    float f = smoothstep(bandPx, 0.0, distToEdge);

    // 位移：沿法线方向把采样点往外推（边缘"凸起"的错觉来自采样被弯折）
    float2 base = coord + n * (f * refractPx);

    // 色散：R/B 相对 G 反向偏移，且同样只在边缘带内
    float2 dither = n * (f * dispPx);
    half4 sr = content.eval(clamp(base + dither, float2(0.0, 0.0), size));
    half4 sg = content.eval(clamp(base, float2(0.0, 0.0), size));
    half4 sb = content.eval(clamp(base - dither, float2(0.0, 0.0), size));

    // 镜面高光：法线 · 光照方向（固定 L），只出现在很薄的一圈
    float ndl = max(dot(n, normalize(lightDir)), 0.0);
    float thin = smoothstep(2.2, 0.0, distToEdge);
    float spec = pow(ndl, 4.0) * thin * rimStrength;

    // 内阴影：边缘附近的遮挡暗化，背光侧更强
    float back = clamp(0.5 - 0.5 * dot(n, normalize(lightDir)), 0.0, 1.0);
    float shadow = smoothstep(14.0, 0.0, distToEdge) * (0.35 + 0.65 * back) * shadowStrength;

    float3 col = float3(sr.r, sg.g, sb.b);
    col += float3(1.0) * spec;
    col *= (1.0 - shadow);
    return half4(col.r, col.g, col.b, 1.0);
}
"""

data class PhysicsGlassEffect(
    val effect: ComposeRenderEffect?,
    val status: String,
    val error: String? = null,
)

fun buildPhysicsGlassEffect(
    widthPx: Float,
    heightPx: Float,
    cornerPx: Float,
    refractPx: Float,
    bandPx: Float,
    dispPx: Float,
    rimStrength: Float,
    shadowStrength: Float,
    lightDirX: Float,
    lightDirY: Float,
): PhysicsGlassEffect {
    if (Build.VERSION.SDK_INT < 33) {
        return PhysicsGlassEffect(null, "不可用：API ${Build.VERSION.SDK_INT} < 33（AGSL 需要 API 33）")
    }
    if (widthPx <= 1f || heightPx <= 1f) return PhysicsGlassEffect(null, "尺寸未就绪")
    return try {
        val sh = RuntimeShader(AGSL_PHYSICS_GLASS)
        sh.setFloatUniform("size", widthPx, heightPx)
        sh.setFloatUniform("halfSize", widthPx / 2f, heightPx / 2f)
        sh.setFloatUniform("corner", cornerPx)
        sh.setFloatUniform("refractPx", refractPx)
        sh.setFloatUniform("bandPx", bandPx)
        sh.setFloatUniform("dispPx", dispPx)
        sh.setFloatUniform("rimStrength", rimStrength)
        sh.setFloatUniform("shadowStrength", shadowStrength)
        sh.setFloatUniform("lightDir", lightDirX, lightDirY)
        val e = RenderEffect.createRuntimeShaderEffect(sh, "content")
        PhysicsGlassEffect(e.asComposeRenderEffect(), "可用（物理玻璃 shader 生效）")
    } catch (t: Throwable) {
        PhysicsGlassEffect(null, "失败", t.message ?: t::class.simpleName)
    }
}

/** 干净玻璃测试体：圆角矩形 + 捕获背景 + 物理 shader。无图标/文字/光晕。 */
@Composable
fun PhysicsGlassBody(
    backdrop: BackdropPainter,
    modifier: Modifier = Modifier,
    blurDp: Float = 24f,
    tintAlpha: Float = 0.04f,
    cornerDp: Float = 26f,
    refractDp: Float = 3f,
    bandDp: Float = 6f,
    dispPx: Float = 1f,
    rimStrength: Float = 0.55f,
    shadowStrength: Float = 0.25f,
) {
    val density = LocalDensity.current
    val blurPx = with(density) { blurDp.dp.toPx() }
    val cornerPx = with(density) { cornerDp.dp.toPx() }
    val refractPx = with(density) { refractDp.dp.toPx() }
    val bandPx = with(density) { bandDp.dp.toPx() }
    val shape = remember(cornerDp) { RoundedCornerShape(cornerDp) }
    val capture = LocalGlassCapture.current
    var posInRoot by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier) {
        val pxSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val physics = remember(
            pxSize,
            cornerPx,
            refractPx,
            bandPx,
            dispPx,
            rimStrength,
            shadowStrength,
        ) {
            buildPhysicsGlassEffect(
                widthPx = pxSize.width.toFloat(),
                heightPx = pxSize.height.toFloat(),
                cornerPx = cornerPx,
                refractPx = refractPx,
                bandPx = bandPx,
                dispPx = dispPx,
                rimStrength = rimStrength,
                shadowStrength = shadowStrength,
                lightDirX = -0.75f,
                lightDirY = -0.66f,
            )
        }
        GlassLabDiag.agslStatus = physics.status
        GlassLabDiag.agslError = physics.error

        val blurEffect: ComposeRenderEffect? = remember(blurPx) {
            buildBlurPlusColorEffect(blurPx, 1f, 0f)
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { posInRoot = it.positionInRoot() }
                .clip(shape)
        ) {
            // 1) 背景：捕获优先，没有则重绘
            var textureMod = Modifier.fillMaxSize()
            if (physics.effect != null) {
                textureMod = textureMod.graphicsLayer { renderEffect = physics.effect }
            }
            if (blurEffect != null) {
                textureMod = textureMod.graphicsLayer { renderEffect = blurEffect }
            }
            if (capture != null) {
                val t = capture.originInRoot - posInRoot
                Box(
                    textureMod.drawBehind {
                        translate(t.x, t.y) { drawLayer(capture.layer) }
                    }
                )
            } else {
                Box(textureMod) {
                    backdrop(Modifier.fillMaxSize(), posInRoot, 1f)
                }
            }
            // 2) tint（唯一允许的"非物理"参数：产品可调）
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Color.White.copy(alpha = tintAlpha))
                    }
            )
        }
    }
}

/**
 * `glass_scene=physics`：G-21 合规的干净玻璃测试体（无装饰）。
 * 参数通过 intent extras / 页内滑杆调：blur / tint / refract / band / disp / rim。
 */
@Composable
fun PhysicsScene(args: GlassLabArgs, scrollPx: Float) {
    var blurDp by remember { mutableStateOf(args.blurDp) }
    var tintAlpha by remember { mutableStateOf(args.tintAlpha) }
    var refractDp by remember { mutableStateOf(args.refract) }
    var bandDp by remember { mutableStateOf(args.band) }
    var dispPx by remember { mutableStateOf(args.disp) }
    var rim by remember { mutableStateOf(args.rim) }
    val cornerDp = 26f

    CaptureHost(background = { m -> GlassBackdrop(scrollPx, Offset.Zero, 1f, m) }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(
                    "G-21 干净玻璃测试体（无图标/无光晕/无渐变装饰）\n" +
                        "blur=${fmt(blurDp)}dp tint=${fmt(tintAlpha * 100f, 0)}% " +
                        "band=${fmt(bandDp, 1)}dp disp=${fmt(dispPx, 1)}px rim=${fmt(rim * 100f, 0)}%",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(7.dp),
                )
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    PhysicsGlassBody(
                        backdrop = { m, _, _ -> GlassBackdrop(scrollPx, Offset.Zero, 1f, m) },
                        modifier = Modifier.fillMaxWidth(0.86f).height(150.dp),
                        blurDp = blurDp,
                        tintAlpha = tintAlpha,
                        cornerDp = cornerDp,
                        refractDp = refractDp,
                        bandDp = bandDp,
                        dispPx = dispPx,
                        rimStrength = rim,
                        shadowStrength = 0.25f,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "判据：refract=0 → 只有模糊；refract>0 → **只有边缘带内**像素位移（内部不动）。" +
                        "镜头的『局部放大』未做——见报告『结构不允许』。",
                    color = Color(0xFFDCE9F5),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (args.showUi) Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE60B0B12))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    PhysSlider("refract ${fmt(refractDp, 1)}dp", refractDp, 0f..8f) { refractDp = it }
                    PhysSlider("band ${fmt(bandDp, 1)}dp", bandDp, 1f..24f) { bandDp = it }
                    PhysSlider("dispersion ${fmt(dispPx, 1)}px", dispPx, 0f..3f) { dispPx = it }
                    PhysSlider("blur ${fmt(blurDp)}dp", blurDp, 0f..48f) { blurDp = it }
                    PhysSlider("tint ${fmt(tintAlpha * 100f, 0)}%", tintAlpha, 0f..0.20f) { tintAlpha = it }
                    PhysSlider("highlight ${fmt(rim * 100f, 0)}%", rim, 0f..1f) { rim = it }
                }
            }
        }
    }
}

@Composable
private fun PhysSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFDCE3F5), fontSize = 9.sp, modifier = Modifier.width(140.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
