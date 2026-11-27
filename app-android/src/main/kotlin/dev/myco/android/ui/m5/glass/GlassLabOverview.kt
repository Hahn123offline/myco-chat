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
 * 交互式实验页（默认场景 overview）：每张卡一个实验，开关/滑杆实时生效。
 * 截图对照在 scenes 里；这里是给人上手玩的版本。
 */
@Composable
fun GlassLabOverview(scrollPx: Float, device: GlassLabDevice, meter: FrameMeter) {
    var blurOn by remember { mutableStateOf(true) }
    var radius by remember { mutableStateOf(24f) }
    var layers by remember { mutableStateOf(2f) }
    var satOn by remember { mutableStateOf(true) }
    var sat by remember { mutableStateOf(1.35f) }
    var bright by remember { mutableStateOf(0.04f) }
    var rimOn by remember { mutableStateOf(true) }
    var rim by remember { mutableStateOf(0.30f) }
    var noise by remember { mutableStateOf(0.012f) }
    var agslOn by remember { mutableStateOf(false) }
    var refractDp by remember { mutableStateOf(10f) }
    var legacy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<android.app.Dialog?>(null) }
    val context = LocalContext.current
    DisposableEffect(Unit) { onDispose { dialog?.dismiss() } }

    val perfRunning = meter.running
    val transition = rememberInfiniteTransition(label = "ov")
    val animRadius by transition.animateFloat(
        initialValue = if (perfRunning) 6f else 24f,
        targetValue = if (perfRunning) 40f else 24f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "r",
    )

    GlassSceneFrame(scrollPx) { origin, backdrop ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1420))
                    .padding(8.dp),
            ) {
                Text(
                    "交互实验页：每张卡的开关/滑杆实时生效。截图对照请用 glass_scene=<name> 单独出图。" +
                        "本机结论：record+drawLayer 拿不到背景，面板用「重绘背景」实现真模糊。" +
                        "设备：API ${device.api} · ${device.glRenderer}",
                    color = Color(0xFFB7F7FF),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                )
            }
            Spacer(Modifier.height(6.dp))

            // 1 单层真实模糊
            ExpCard(
                title = "1 · 单层真实模糊（backdrop，重绘路径）",
                meta = "API 31+ · RenderEffect.createBlurEffect · 依赖硬件加速 · 真机风险：低",
                spec = demoSpec(
                    label = if (blurOn) "真实模糊 ${fmt(radius)}dp" else "关闭模糊（tint only）",
                    sub = "重绘背景 → BlurEffect → 超椭圆裁剪；tint 6%",
                    api = "API31+",
                    blurDp = if (blurOn) radius else 0f,
                    sat = if (satOn) sat else 1f,
                    bright = if (satOn) bright else 0f,
                    rim = if (rimOn) rim else 0f,
                    noise = noise,
                    legacy = legacy,
                ),
                backdrop = backdrop,
            ) {
                SwitchRow("模糊开关", blurOn) { blurOn = it }
                SliderRow("半径 ${fmt(radius)}dp", radius, 0f..48f) { radius = it }
                SwitchRow("饱和度 ${fmt(sat, 1)} / 亮度 +${fmt(bright, 2)}", satOn) { satOn = it }
                SliderRow("saturation", sat, 1f..1.8f) { sat = it }
                SliderRow("brightness", bright, 0f..0.15f) { bright = it }
                SwitchRow("边缘高光 ${fmt(rim * 100f)}%", rimOn) { rimOn = it }
                SliderRow("高光强度", rim, 0f..1f) { rim = it }
                SliderRow("磨砂噪声 ${fmt(noise * 100f, 1)}%", noise, 0f..0.03f) { noise = it }
            }

            // 2 多层
            ExpCard(
                title = "2 · 多层遮挡（1~3 层）",
                meta = "API 31+ · 每层重绘背景 · 真机风险：中（层数越多越脏越贵）",
                spec = demoSpec(
                    label = "${layers.toInt()} 层玻璃叠放",
                    sub = "「后层采样已合成结果」的对照见 glass_scene=stack",
                    api = "API31+",
                    blurDp = 24f,
                ),
                backdrop = backdrop,
            ) {
                SliderRow("层数 ${layers.toInt()}", layers, 1f..3f) { layers = it }
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    for (i in 0 until layers.toInt()) {
                        GlassPanel(
                            spec = demoSpec("", "", "", blurDp = 24f, tintAlpha = 0.06f + 0.02f * i),
                            backdrop = backdrop,
                            modifier = Modifier
                                .offset(x = (14 * i).dp, y = (18 * i).dp)
                                .fillMaxWidth(0.86f)
                                .height(100.dp),
                        )
                    }
                }
            }

            // 3 半径梯度
            ExpCard(
                title = "3 · 半径梯度 8 / 16 / 32dp",
                meta = "API 31+ · 同背景三档并排 · 真机风险：低（半径越大越贵）",
                spec = demoSpec("", "", "", blurDp = 0f),
                backdrop = backdrop,
            ) {
                Row(Modifier.fillMaxWidth().height(104.dp)) {
                    listOf(8f, 16f, 32f).forEach { r ->
                        GlassPanel(
                            spec = demoSpec("${fmt(r)}dp", "", "API31+", blurDp = r, rim = 0.26f),
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(104.dp).padding(2.dp),
                        )
                    }
                }
            }

            // 4 边缘高光
            ExpCard(
                title = "4 · 边缘高光 / 内阴影",
                meta = "无 API 要求（纯 Canvas 绘制）· 真机风险：极低",
                spec = demoSpec(
                    label = if (rimOn) "specular rim ${fmt(rim * 45f)}% 白" else "无边缘高光",
                    sub = "1.2dp 线性渐变描边 + 内沿明暗",
                    api = "无",
                    blurDp = 24f,
                    rim = if (rimOn) rim else 0f,
                ),
                backdrop = backdrop,
            ) {
                SwitchRow("高光开关", rimOn) { rimOn = it }
                SliderRow("高光强度", rim, 0f..1f) { rim = it }
            }

            // 5 AGSL
            ExpCard(
                title = "5 · 折射（AGSL RuntimeShader）",
                meta = "API 33+ · 只能采样自身已渲染内容 · 真机风险：中高（厂商 GLES 实现差异）",
                spec = demoSpec(
                    label = if (agslOn) "AGSL 折射 ${fmt(refractDp)}dp" else "无折射",
                    sub = "状态：" + GlassLabDiag.agslStatus,
                    api = "API33+",
                    blurDp = 24f,
                    refractDp = if (agslOn) refractDp else 0f,
                ),
                backdrop = backdrop,
            ) {
                SwitchRow("折射开关", agslOn) { agslOn = it }
                SliderRow("边缘位移 ${fmt(refractDp)}dp", refractDp, 0f..16f) { refractDp = it }
            }

            // 6 窗口级模糊
            ExpCard(
                title = "6 · 窗口级模糊（Dialog + FLAG_BLUR_BEHIND）",
                meta = "API 31+ · 需系统跨窗口模糊开关 · 独立窗口 · 真机风险：高（很多 ROM 不支持）",
                spec = demoSpec(
                    label = "窗口级模糊 / 无模糊对照（两个按钮）",
                    sub = "结果：" + GlassLabDiag.windowBlurRadiusResult + " · crossWindowBlur=" + GlassLabDiag.crossWindowBlurResult,
                    api = "API31+",
                    blurDp = 24f,
                ),
                backdrop = backdrop,
            ) {
                Row {
                    TextButton(onClick = { dialog = showBlurDialog(context, 24f, true) }) {
                        Text("有模糊", color = Color(0xFF9FE8FF), fontSize = 11.sp)
                    }
                    TextButton(onClick = { dialog = showBlurDialog(context, 24f, false) }) {
                        Text("无模糊对照", color = Color(0xFFFFC777), fontSize = 11.sp)
                    }
                }
            }

            // 7 降级
            ExpCard(
                title = "7 · 降级路径（API<31 假玻璃）",
                meta = "API 26+ · 半透明+渐变+噪声+高光 · 不依赖 RenderEffect · 真机风险：极低",
                spec = demoSpec(
                    label = if (legacy) "降级假玻璃（不采样背景）" else "真 backdrop 模糊",
                    sub = if (legacy) "API<31 实际路径：不崩，但看不见背景" else "对照：能采样背景",
                    api = if (legacy) "API26+" else "API31+",
                    blurDp = 24f,
                    legacy = legacy,
                    noise = 0.012f,
                ),
                backdrop = backdrop,
            ) {
                SwitchRow("强制降级路径", legacy) { legacy = it }
            }

            // 8 性能
            ExpCard(
                title = "8 · 帧时间（host GPU 模拟器实测）",
                meta = "Choreographer 帧间隔 · 只代表模拟器 · 真机未知",
                spec = demoSpec(
                    label = if (perfRunning) "动画模糊 ${fmt(animRadius)}dp（测帧中）" else "静止",
                    sub = "heavy 测试：glass_scene=perf + glass_autoscroll=1 + glass_layers=1|2|3",
                    api = "API31+",
                    blurDp = animRadius,
                ),
                backdrop = backdrop,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (meter.running) meter.stop() else meter.start()
                        Log.i(GLASSLAB_TAG, "PERF toggled running=${meter.running}")
                    }) {
                        Text(if (perfRunning) "停止测量" else "开始测量", color = Color(0xFF9FE8FF), fontSize = 11.sp)
                    }
                    Text(meter.snapshotNow(), color = Color(0xFFD8F8FF), fontSize = 9.sp)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ExpCard(
    title: String,
    meta: String,
    spec: GlassSpec,
    backdrop: BackdropPainter,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        GlassPanel(
            spec = spec,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xF00B0B12))
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
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.width(44.dp))
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
