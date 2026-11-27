package dev.myco.android.ui.m5.glass

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 交互式实验页：每张卡 = 一个实验，卡片上直接可开/关、拖滑杆，实时看对比。
 * 截图用的"干净对照"在 scenes 里；这里是给人在设备上玩的版本。
 */
@Composable
fun GlassLabOverview(scrollPx: Float, device: GlassLabDevice, meter: FrameMeter) {
    val bg = rememberLayerCapture()

    var blurOn by remember { mutableStateOf(true) }
    var radius by remember { mutableStateOf(24f) }
    var layers by remember { mutableStateOf(2f) }
    var satOn by remember { mutableStateOf(true) }
    var sat by remember { mutableStateOf(1.4f) }
    var bright by remember { mutableStateOf(0.04f) }
    var rimOn by remember { mutableStateOf(true) }
    var rim by remember { mutableStateOf(0.35f) }
    var innerShadowOn by remember { mutableStateOf(true) }
    var agslOn by remember { mutableStateOf(false) }
    var refractDp by remember { mutableStateOf(10f) }
    var noise by remember { mutableStateOf(0.012f) }
    var legacy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<android.app.Dialog?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { dialog?.dismiss() }
    }

    // 性能卡：打开时动画半径，逼出持续重绘
    val perfRunning = meter.running
    val transition = rememberInfiniteTransition(label = "overview-perf")
    val animRadius by transition.animateFloat(
        initialValue = if (perfRunning) 6f else 24f,
        targetValue = if (perfRunning) 48f else 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "r",
    )

    Box(Modifier.fillMaxSize()) {
        RecordInto(bg, Modifier.fillMaxSize()) {
            GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                "交互式实验页：每张卡的开/关、滑杆都实时生效。顶部说明条给出当前 API 与渲染器。",
                color = Color(0xFFB7F7FF),
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(8.dp),
            )
            Spacer(Modifier.height(6.dp))

            // 1. 单层真实模糊
            ExpCard(
                title = "1 · 单层真实模糊（backdrop）",
                meta = "API 31+ · RenderEffect.createBlurEffect · 依赖硬件加速（软件 GPU 也能渲染） · 真机风险：低",
                bg = bg,
                spec = demoSpec(
                    label = if (blurOn) "真实模糊 ${fmt(radius)}dp" else "关闭模糊（仅 tint）",
                    sub = "rememberGraphicsLayer 捕获背景 → drawLayer + BlurEffect → 超椭圆裁剪",
                    api = "API31+",
                    blurDp = if (blurOn) radius else 0f,
                    sat = if (satOn) sat else 1f,
                    bright = if (satOn) bright else 0f,
                    rim = if (rimOn) rim else 0f,
                    innerShadow = if (innerShadowOn) 0.10f else 0f,
                    noise = noise,
                    legacy = legacy,
                ),
            ) {
                SwitchRow("模糊开关", blurOn) { blurOn = it }
                SliderRow("半径 ${fmt(radius)}dp", radius, 0f..48f) { radius = it }
                SwitchRow("饱和度/亮度补偿 ${fmt(sat, 1)} / +${fmt(bright, 2)}", satOn) { satOn = it }
                SliderRow("saturation", sat, 1f..1.8f) { sat = it }
                SliderRow("brightness", bright, 0f..0.15f) { bright = it }
                SwitchRow("边缘高光 ${fmt(rim * 100f)}%", rimOn) { rimOn = it }
                SliderRow("高光强度", rim, 0f..1f) { rim = it }
                SwitchRow("内阴影", innerShadowOn) { innerShadowOn = it }
                SliderRow("磨砂噪声 ${fmt(noise * 100f, 1)}%", noise, 0f..0.03f) { noise = it }
            }

            // 2. 双层 / 三层遮挡
            ExpCard(
                title = "2 · 多层遮挡（1~3 层）",
                meta = "API 31+ · 每层各自采样 · 依赖硬件加速 · 真机风险：中（层数越多越脏、越贵）",
                bg = bg,
                spec = demoSpec(
                    label = if (layers.toInt() >= 2) "${layers.toInt()} 层玻璃叠在一起" else "单层",
                    sub = "本卡是「每层都采样原始背景」的叠法；「后层采样合成结果」的对照见 scenes",
                    api = "API31+",
                    blurDp = 24f,
                ),
            ) {
                SliderRow("层数 ${layers.toInt()}", layers, 1f..3f) { layers = it }
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    for (i in 0 until layers.toInt()) {
                        GlassPanel(
                            spec = demoSpec("", "", "", blurDp = 24f, tintAlpha = 0.10f + 0.03f * i),
                            capture = bg,
                            modifier = Modifier
                                .offset(x = (14 * i).dp, y = (18 * i).dp)
                                .fillMaxWidth(0.86f)
                                .height(96.dp),
                        )
                    }
                }
            }

            // 3. 半径梯度
            ExpCard(
                title = "3 · 模糊半径梯度 4 / 16 / 48dp",
                meta = "API 31+ · 同一背景三档并排 · 真机风险：低（半径越大越贵）",
                bg = bg,
                spec = demoSpec("", "", "", blurDp = 0f),
            ) {
                Row(Modifier.fillMaxWidth().height(100.dp)) {
                    listOf(4f, 16f, 48f).forEach { r ->
                        GlassPanel(
                            spec = demoSpec("${fmt(r)}dp", "", "API31+", blurDp = r, rim = 0.30f),
                            capture = bg,
                            modifier = Modifier.weight(1f).height(100.dp).padding(2.dp),
                        )
                    }
                }
            }

            // 4. 边缘高光（卡片 1 里也有，这里给个近距离特写尺寸）
            ExpCard(
                title = "4 · 边缘高光 / 内阴影特写",
                meta = "无 API 要求（纯 Canvas 绘制）· 不依赖 GPU 特效 · 真机风险：极低",
                bg = bg,
                spec = demoSpec(
                    label = if (rimOn) "specular rim ${fmt(rim * 45f)}% 白" else "无边缘高光",
                    sub = "1.2dp 线性渐变描边 + 内沿明暗；玻璃感主要来自这里",
                    api = "无",
                    blurDp = 24f,
                    rim = if (rimOn) rim else 0f,
                    innerShadow = if (innerShadowOn) 0.10f else 0f,
                ),
            ) {
                SwitchRow("高光开关", rimOn) { rimOn = it }
                SliderRow("高光强度", rim, 0f..1f) { rim = it }
                SwitchRow("内阴影", innerShadowOn) { innerShadowOn = it }
            }

            // 5. AGSL 折射
            ExpCard(
                title = "5 · 折射 / 扭曲（AGSL RuntimeShader）",
                meta = "API 33+ · 只能采样「自身已渲染内容」· 依赖 GPU shader · 真机风险：中高（各厂商 GLES 实现差异）",
                bg = bg,
                spec = demoSpec(
                    label = if (agslOn) "AGSL 折射 ${fmt(refractDp)}dp" else "无折射",
                    sub = "状态：" + GlassLabDiag.agslStatus + (GlassLabDiag.agslError?.let { " · $it" } ?: ""),
                    api = "API33+",
                    blurDp = 24f,
                    refractDp = if (agslOn) refractDp else 0f,
                ),
            ) {
                SwitchRow("折射开关", agslOn) { agslOn = it }
                SliderRow("边缘位移 ${fmt(refractDp)}dp", refractDp, 0f..16f) { refractDp = it }
            }

            // 6. 窗口级模糊
            ExpCard(
                title = "6 · 窗口级模糊（Dialog + FLAG_BLUR_BEHIND）",
                meta = "API 31+ · 需要系统跨窗口模糊开关 · 需要独立窗口 · 真机风险：高（很多 ROM 直接不支持）",
                bg = bg,
                spec = demoSpec(
                    label = "点下面的按钮打开窗口级模糊 Dialog",
                    sub = "结果：" + GlassLabDiag.windowBlurRadiusResult + " · crossWindowBlur=" + GlassLabDiag.crossWindowBlurResult,
                    api = "API31+",
                    blurDp = 24f,
                ),
            ) {
                TextButton(onClick = {
                    dialog = showBlurDialog(context, 24f)
                }) {
                    Text("打开窗口级模糊 Dialog", color = Color(0xFF9FE8FF), fontSize = 11.sp)
                }
            }

            // 7. 降级路径
            ExpCard(
                title = "7 · 降级路径（API<31 假玻璃）",
                meta = "API 26+ · 不采样背景：半透明+渐变+噪声+高光 · 不依赖 RenderEffect · 真机风险：极低",
                bg = bg,
                spec = demoSpec(
                    label = if (legacy) "降级假玻璃（不采样背景）" else "真 backdrop 模糊",
                    sub = if (legacy) "API<31 设备上就是这样：不会崩，但看不到背景内容" else "对照：能采样背景",
                    api = if (legacy) "API26+" else "API31+",
                    blurDp = 24f,
                    legacy = legacy,
                    noise = 0.012f,
                ),
            ) {
                SwitchRow("强制降级路径", legacy) { legacy = it }
            }

            // 8. 性能
            ExpCard(
                title = "8 · 帧时间（软件 GPU 实测）",
                meta = "Choreographer 帧间隔 · 只代表「能渲染」 · 真机性能未知",
                bg = bg,
                spec = demoSpec(
                    label = if (perfRunning) "动画模糊 ${fmt(animRadius)}dp（测帧中）" else "静止",
                    sub = "heavy 测试请用 glass_scene=perf + glass_autoscroll=1",
                    api = "API31+",
                    blurDp = animRadius,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (meter.stopOrStart()) Log.i(GLASSLAB_TAG, "PERF toggled")
                    }) {
                        Text(if (perfRunning) "停止测量" else "开始测量", color = Color(0xFF9FE8FF), fontSize = 11.sp)
                    }
                    Text(
                        meter.snapshotNow(),
                        color = Color(0xFFD8F8FF),
                        fontSize = 9.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun FrameMeter.stopOrStart(): Boolean {
    return if (running) {
        stop()
        false
    } else {
        start()
        true
    }
}

@Composable
private fun ExpCard(
    title: String,
    meta: String,
    bg: LayerCapture,
    spec: GlassSpec,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        GlassPanel(
            spec = spec,
            capture = bg,
            modifier = Modifier.fillMaxWidth().height(116.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE60B0B12))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(meta, color = Color(0xFF9FA8C0), fontSize = 9.sp, lineHeight = 12.sp)
            Spacer(Modifier.height(4.dp))
            controls()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFFDCE3F5), fontSize = 10.sp)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFDCE3F5), fontSize = 9.5.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
