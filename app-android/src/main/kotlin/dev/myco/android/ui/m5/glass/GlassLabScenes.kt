package dev.myco.android.ui.m5.glass

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* ------------------------------------------------------------------------------------------------
 * 场景分发：每个场景 = 一次可截图的对照实验
 * --------------------------------------------------------------------------------------------- */

@Composable
fun GlassLabScenes(
    args: GlassLabArgs,
    scrollPx: Float,
    meter: FrameMeter,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (args.scene) {
            "raw" -> RawScene(scrollPx)
            "blur" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 148.dp,
                stripHeight = 132.dp,
                specs = listOf(
                    demoSpec(
                        label = "1 · 单层真实模糊 ON",
                        sub = "capture→drawLayer→BlurEffect(${fmt(args.blurDp)}dp)→超椭圆裁剪",
                        api = "API31+",
                        blurDp = args.blurDp,
                    ),
                    demoSpec(
                        label = "1 · 对照 OFF（只有 tint，没有模糊）",
                        sub = "同一块背景像素，无 backdrop 模糊 / 无 RenderEffect",
                        api = "API26+",
                        blurDp = 0f,
                        tintAlpha = 0.16f,
                        rim = 0.18f,
                    ),
                ),
            )

            "radius" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 76.dp,
                stripHeight = 96.dp,
                stripCaption = "↑ 原始背景（参考）：下面 6 块玻璃采样同一块像素，只改模糊半径",
                specs = listOf(4f, 8f, 16f, 24f, 32f, 48f).map { r ->
                    demoSpec(
                        label = "半径 ${fmt(r)}dp",
                        sub = if (r <= 8f) "小半径：噪点/文字轮廓仍在" else if (r <= 16f) "中半径：只剩色块" else "大半径：纯色雾",
                        api = "API31+",
                        blurDp = r,
                    )
                },
            )

            "sat" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 92.dp,
                stripHeight = 104.dp,
                stripCaption = "↑ 原始背景：下面同样 24dp 模糊，只改饱和度/亮度补偿",
                specs = listOf(1.0f, 1.2f, 1.5f, 1.8f).map { s ->
                    demoSpec(
                        label = "saturation ${fmt(s, 1)} · bright ${if (s >= 1.5f) "+0.06" else "0"}",
                        sub = if (s == 1f) "不补偿：模糊后整体发灰" else "ColorMatrix 补偿（挂在 blur 之后）",
                        api = "API31+",
                        blurDp = 24f,
                        sat = s,
                        bright = if (s >= 1.5f) 0.06f else 0f,
                    )
                },
            )

            "rim" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 104.dp,
                stripHeight = 104.dp,
                stripCaption = "↑ 原始背景：下面 blur 24dp，只改边缘高光 / 内阴影",
                specs = listOf(
                    demoSpec("无高光 · 无内阴影", "只有模糊+tint，像贴纸", "无", rim = 0f, innerShadow = 0f),
                    demoSpec("高光 0.35 · 内阴影 0.10", "1.2dp 线性渐变白边 + 内沿明暗", "无", rim = 0.35f, innerShadow = 0.10f),
                    demoSpec("高光 1.0 · 内阴影 0.22", "过强：边缘开始发白/发灰", "无", rim = 1f, innerShadow = 0.22f),
                ),
            )

            "agsl" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 96.dp,
                stripHeight = 104.dp,
                stripCaption = "↑ 原始背景：下面 blur 24dp + AGSL 伪折射（只作用于自身已渲染内容）",
                specs = listOf(
                    demoSpec("无折射（对照）", "只 blur", "API31+", blurDp = 24f),
                    demoSpec("AGSL 折射 6dp", "边缘位移小：像薄玻璃", "API33+", blurDp = 24f, refractDp = 6f),
                    demoSpec("AGSL 折射 16dp", "边缘明显放大/位移：像厚玻璃", "API33+", blurDp = 24f, refractDp = 16f),
                    demoSpec(
                        "反例：折射作用到整块（含文字）",
                        "文字一起被拉弯 → 只能对 backdrop 层用",
                        "API33+",
                        blurDp = 24f,
                        refractDp = 12f,
                        refractWholePanel = true,
                    ),
                ),
            )

            "noise" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 104.dp,
                stripHeight = 104.dp,
                stripCaption = "↑ 原始背景：下面 blur 24dp + 程序生成磨砂噪声",
                specs = listOf(0f, 0.012f, 0.028f).map { n ->
                    demoSpec(
                        label = "噪声 ${fmt(n * 100f, 1)}%",
                        sub = if (n == 0f) "纯 blur：塑料感/过于干净" else "128×128 黑白颗粒 tile，双向压 alpha",
                        api = "无（CPU 位图）",
                        blurDp = 24f,
                        noise = n,
                    )
                },
            )

            "legacy" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 148.dp,
                stripHeight = 128.dp,
                specs = listOf(
                    demoSpec(
                        label = "真 backdrop 模糊（API31+）",
                        sub = "采样背景 + BlurEffect + tint + 噪声 + 高光",
                        api = "API31+",
                        blurDp = 24f,
                    ),
                    demoSpec(
                        label = "降级假玻璃（API<31 实际路径）",
                        sub = "不采样背景：半透明 + 渐变 + 噪声 + 高光；保证不崩、不空",
                        api = "API26+",
                        legacy = true,
                        noise = 0.012f,
                    ),
                ),
            )

            "stack" -> StackScene(scrollPx = scrollPx, layers = 2, blurDp = args.blurDp)
            "stack3" -> StackScene(scrollPx = scrollPx, layers = 3, blurDp = args.blurDp)
            "debug" -> DebugScene(scrollPx = scrollPx)
            "window" -> WindowScene(scrollPx = scrollPx, radiusDp = args.blurDp, autoDialog = args.autoDialog)
            "perf" -> PerfScene(scrollPx = scrollPx, meter = meter, blurDp = args.blurDp)
            else -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 148.dp,
                stripHeight = 132.dp,
                specs = listOf(
                    demoSpec("单层真实模糊", "24dp backdrop blur", "API31+", blurDp = 24f),
                    demoSpec("对照 OFF", "只有 tint", "API26+", blurDp = 0f, tintAlpha = 0.16f),
                ),
            )
        }
    }
}

internal fun demoSpec(
    label: String,
    sub: String,
    api: String,
    blurDp: Float = 24f,
    sat: Float = 1f,
    bright: Float = 0f,
    noise: Float = 0f,
    rim: Float = 0.35f,
    innerShadow: Float = 0.10f,
    tintAlpha: Float = 0.10f,
    legacy: Boolean = false,
    refractDp: Float = 0f,
    refractWholePanel: Boolean = false,
): GlassSpec = GlassSpec(
    label = label,
    sub = sub,
    api = api,
    blurDp = blurDp,
    sat = sat,
    bright = bright,
    noise = noise,
    rim = rim,
    innerShadow = innerShadow,
    tintAlpha = tintAlpha,
    legacy = legacy,
    refractDp = refractDp,
    refractWholePanel = refractWholePanel,
)

internal fun fmt(v: Float, digits: Int = 0): String =
    if (digits <= 0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.${digits}f", v)

/* ------------------------------------------------------------------------------------------------
 * 场景 0：纯背景（没有任何玻璃）—— 报告里的"参考帧"
 * --------------------------------------------------------------------------------------------- */

@Composable
private fun RawScene(scrollPx: Float) {
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        Text(
            "原始背景（无任何玻璃层）",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * 通用对照场景：顶部一条"原始背景参考条"，下面每块玻璃都采样那条里的**同一块像素**
 * --------------------------------------------------------------------------------------------- */

@Composable
fun CompareScene(
    scrollPx: Float,
    specs: List<GlassSpec>,
    panelHeight: Dp = 84.dp,
    stripHeight: Dp = 96.dp,
    stripCaption: String = "",
) {
    val bg = rememberLayerCapture()
    Box(Modifier.fillMaxSize()) {
        RecordInto(bg, Modifier.fillMaxSize()) {
            GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            var stripLocal by remember { mutableStateOf<Offset?>(null) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
                    .onGloballyPositioned { stripLocal = it.positionInRoot() - bg.originInRoot },
            ) {
                Text(
                    text = stripCaption.ifEmpty { "↑ 原始背景参考条（未加玻璃）" },
                    color = Color.White,
                    fontSize = 10.sp,
                    style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            specs.forEach { spec ->
                GlassPanel(
                    spec = spec,
                    capture = bg,
                    modifier = Modifier.fillMaxWidth().height(panelHeight),
                    sampleLocalTopLeft = stripLocal,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * 场景 2：分层遮挡 —— 左列"每层都采样原始背景"，右列"后层采样已合成结果"
 * --------------------------------------------------------------------------------------------- */

private enum class StackMode { SAMPLES_BG, COMPOUND }

@Composable
fun StackScene(scrollPx: Float, layers: Int, blurDp: Float) {
    val bg = rememberLayerCapture()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colW = (maxWidth - 36.dp) / 2
        val panelH = if (layers >= 3) 128.dp else 168.dp
        val step = if (layers >= 3) 86.dp else 108.dp
        val colTop = 40.dp

        RecordInto(bg, Modifier.fillMaxSize()) {
            GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        }

        ColumnLabel("A · 每层都只采样原始背景（叠而不浑浊）", 12.dp, colW)
        ColumnLabel("B · 后一层采样已合成结果（模糊会累积）", 24.dp + colW, colW)

        val specs = listOf(
            demoSpec("底层 B", "blur ${fmt(blurDp)}dp", "API31+", blurDp = blurDp),
            demoSpec("中层 A", "blur ${fmt(blurDp)}dp", "API31+", blurDp = blurDp),
            demoSpec("顶层 C", "blur ${fmt(blurDp)}dp", "API31+", blurDp = blurDp),
        )
        StackColumn(bg, 12.dp, colTop, colW, StackMode.SAMPLES_BG, specs.take(layers), panelH, step)
        StackColumn(bg, 24.dp + colW, colTop, colW, StackMode.COMPOUND, specs.take(layers), panelH, step)
    }
}

@Composable
private fun ColumnLabel(text: String, x: Dp, width: Dp) {
    Text(
        text = text,
        color = Color(0xFFD9F6FF),
        fontSize = 9.5.sp,
        lineHeight = 12.sp,
        style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
        modifier = Modifier
            .offset(x = x, y = 6.dp)
            .width(width)
            .background(Color(0x99000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun StackColumn(
    bg: LayerCapture,
    x: Dp,
    y: Dp,
    width: Dp,
    mode: StackMode,
    specs: List<GlassSpec>,
    panelH: Dp,
    step: Dp,
) {
    val c1 = rememberLayerCapture()
    val c2 = rememberLayerCapture()
    val totalH = panelH + step * (specs.size - 1)
    Box(Modifier.offset(x = x, y = y).width(width).height(totalH)) {
        when {
            specs.size == 1 -> GlassPanel(
                spec = specs[0],
                capture = bg,
                modifier = Modifier.offset(0.dp, 0.dp).fillMaxWidth().height(panelH),
            )

            mode == StackMode.SAMPLES_BG -> specs.forEachIndexed { i, spec ->
                GlassPanel(
                    spec = spec,
                    capture = bg,
                    modifier = Modifier.offset(0.dp, step * i).fillMaxWidth().height(panelH),
                )
            }

            specs.size == 2 -> {
                RecordInto(c1, Modifier.fillMaxSize()) {
                    DrawCaptureAt(bg, x, y)
                    GlassPanel(
                        spec = specs[0],
                        capture = bg,
                        modifier = Modifier.offset(0.dp, 0.dp).fillMaxWidth().height(panelH),
                    )
                }
                GlassPanel(
                    spec = specs[1],
                    capture = c1,
                    modifier = Modifier.offset(0.dp, step).fillMaxWidth().height(panelH),
                )
            }

            else -> {
                RecordInto(c1, Modifier.fillMaxSize()) {
                    DrawCaptureAt(bg, x, y)
                    GlassPanel(
                        spec = specs[0],
                        capture = bg,
                        modifier = Modifier.offset(0.dp, 0.dp).fillMaxWidth().height(panelH),
                    )
                }
                RecordInto(c2, Modifier.fillMaxSize()) {
                    DrawCaptureAt(c1, 0.dp, 0.dp)
                    GlassPanel(
                        spec = specs[1],
                        capture = c1,
                        modifier = Modifier.offset(0.dp, step).fillMaxWidth().height(panelH),
                    )
                }
                GlassPanel(
                    spec = specs[2],
                    capture = c2,
                    modifier = Modifier.offset(0.dp, step * 2).fillMaxWidth().height(panelH),
                )
            }
        }
    }
}

/**
 * 把 capture 层按 root 对齐画进当前节点：
 * 当前节点在 root 的 (offsetX, offsetY)，捕获层内容按 root 坐标记录，所以要反向平移。
 */
@Composable
fun DrawCaptureAt(capture: LayerCapture, offsetX: Dp, offsetY: Dp) {
    val d = LocalDensity.current
    val ox = with(d) { offsetX.toPx() }
    val oy = with(d) { offsetY.toPx() }
    Box(
        Modifier.fillMaxSize().drawBehind {
            translate(-ox, -oy) { drawLayer(capture.layer) }
        }
    )
}

/* ------------------------------------------------------------------------------------------------
 * 场景 8：窗口级模糊（Dialog + FLAG_BLUR_BEHIND + setBackgroundBlurRadius）
 * --------------------------------------------------------------------------------------------- */

@Composable
fun WindowScene(scrollPx: Float, radiusDp: Float, autoDialog: Boolean) {
    val bg = rememberLayerCapture()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var open by remember { mutableStateOf(autoDialog) }

    LaunchedEffect(open) {
        if (open && dialog == null) {
            Log.i(GLASSLAB_TAG, "WINDOW blur: crossWindowBlur=" + tryCrossWindowBlur(context))
            dialog = showBlurDialog(context, radiusDp)
        }
        if (!open && dialog != null) {
            dialog?.dismiss()
            dialog = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            dialog?.dismiss()
            dialog = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        RecordInto(bg, Modifier.fillMaxSize()) {
            GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        }
        // 一个 in-app 玻璃面板：用来对比"窗口级模糊"和"in-app 层叠"
        GlassPanel(
            spec = demoSpec(
                label = "in-app 玻璃（对照）",
                sub = "同一屏内的 backdrop 模糊；窗口级模糊应该是「整窗」效果，两者会叠加",
                api = "API31+",
                blurDp = 24f,
            ),
            capture = bg,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .height(150.dp),
        )
        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            TextButton(onClick = { open = true }) {
                Text(if (open) "窗口模糊 Dialog 已打开" else "打开窗口级模糊 Dialog", color = Color.White, fontSize = 11.sp)
            }
            Text(
                "结果：" + GlassLabDiag.windowBlurRadiusResult,
                color = Color(0xFFB7F7FF),
                fontSize = 9.sp,
                style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
            )
            Text(
                "crossWindowBlur=" + GlassLabDiag.crossWindowBlurResult,
                color = Color(0xFFB7F7FF),
                fontSize = 9.sp,
                style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
            )
        }
    }
}

private fun tryCrossWindowBlur(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return false
    return try {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
    } catch (t: Throwable) {
        false
    }
}

/**
 * 用纯 Android View 搭的 Dialog（不依赖 Compose Dialog 内部实现，才能可靠拿到 window）。
 * 先设置 FLAG_BLUR_BEHIND + setBackgroundBlurRadius，再 show。
 */
internal fun showBlurDialog(context: Context, radiusDp: Float): Dialog {
    val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
    val density = context.resources.displayMetrics.density
    val radiusPx = (radiusDp * density).toInt()

    val title = TextView(context).apply {
        text = "窗口级模糊（Window blur）"
        setTextColor(AndroidColor.WHITE)
        textSize = 17f
        setPadding(0, 0, 0, 8)
    }
    val body = TextView(context).apply {
        text = "FLAG_BLUR_BEHIND + setBackgroundBlurRadius($radiusDp dp)\n" +
            "API ${Build.VERSION.SDK_INT}\n\n" +
            "若系统跨窗口模糊开启：Dialog 后面的整窗内容应该被糊掉。\n" +
            "若没糊：这台设备的跨窗口模糊不可用（模拟器常见）。"
        setTextColor(AndroidColor.argb(235, 220, 240, 255))
        textSize = 12f
    }
    val status = TextView(context).apply {
        text = ""
        setTextColor(AndroidColor.argb(230, 120, 255, 220))
        textSize = 10f
        setPadding(0, 10, 0, 0)
    }
    val close = Button(context).apply {
        text = "关闭"
        setOnClickListener { dialog.dismiss() }
    }
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 44, 48, 32)
        background = GradientDrawable().apply {
            cornerRadius = 44f
            setColor(AndroidColor.argb(0x5A, 0x10, 0x14, 0x20))
            setStroke(2, AndroidColor.argb(0x66, 0xFF, 0xFF, 0xFF))
        }
        addView(title)
        addView(body)
        addView(status)
        addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = android.view.Gravity.END; topMargin = 18 },
        )
    }
    dialog.setContentView(root)

    val result = try {
        if (Build.VERSION.SDK_INT < 31) {
            "API<31：无窗口模糊 API"
        } else {
            val w = dialog.window
            if (w == null) {
                "window=null"
            } else {
                val attrs = w.attributes
                attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                w.attributes = attrs
                w.setBackgroundBlurRadius(radiusPx)
                w.setDimAmount(0f)
                "已请求 blurRadius=${radiusPx}px（是否生效看跨窗口模糊开关与截图）"
            }
        }
    } catch (t: Throwable) {
        "异常：" + (t.message ?: t::class.simpleName)
    }
    status.text = "结果：" + result
    GlassLabDiag.windowBlurRadiusResult = result
    GlassLabDiag.crossWindowBlurTried = true
    GlassLabDiag.crossWindowBlurResult = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
                .toString()
        } else {
            "API<31"
        }
    } catch (t: Throwable) {
        "查询异常"
    }
    Log.i(GLASSLAB_TAG, "WINDOW result=$result crossWindowBlur=${GlassLabDiag.crossWindowBlurResult}")
    dialog.show()
    GlassLabDiag.windowBlurRadiusResult = result + " · shown"
    return dialog
}

/* ------------------------------------------------------------------------------------------------
 * 场景：性能（帧时间）—— 动画模糊半径 + 自动滚动背景，持续出帧后统计
 * --------------------------------------------------------------------------------------------- */

@Composable
fun PerfScene(scrollPx: Float, meter: FrameMeter, blurDp: Float) {
    val bg = rememberLayerCapture()
    val transition = rememberInfiniteTransition(label = "perf")
    val animRadius by transition.animateFloat(
        initialValue = 6f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radius",
    )
    LaunchedEffect(Unit) {
        meter.start()
        Log.i(GLASSLAB_TAG, "PERF start: blur animation + auto scroll")
    }
    DisposableEffect(Unit) {
        onDispose { meter.stop() }
    }

    Box(Modifier.fillMaxSize()) {
        RecordInto(bg, Modifier.fillMaxSize()) {
            GlassBackdrop(scrollPx, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Spacer(Modifier.height(2.dp))
            repeat(3) { i ->
                GlassPanel(
                    spec = demoSpec(
                        label = "动画模糊半径 ${fmt(animRadius)}dp",
                        sub = "第 ${i + 1} 块：持续重绘，用于测帧时间",
                        api = "API31+",
                        blurDp = animRadius.coerceAtLeast(4f),
                    ),
                    capture = bg,
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6000000))
                    .padding(12.dp),
            ) {
                Text(
                    text = "帧时间（UI 线程 Choreographer，软件 GPU）：\n" + meter.snapshotNow() +
                        "\n\n注：swiftshader 只代表「能渲染」，不代表真机流畅。",
                    color = Color(0xFFD8F8FF),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}
