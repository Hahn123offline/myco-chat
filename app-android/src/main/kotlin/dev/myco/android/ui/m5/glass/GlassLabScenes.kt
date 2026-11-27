package dev.myco.android.ui.m5.glass

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/* ------------------------------------------------------------------------------------------------
 * 场景框架：一块背景（场景帧）+ 若干玻璃面板
 * --------------------------------------------------------------------------------------------- */

/**
 * 场景容器：铺满一块"场景帧"，并提供 [BackdropPainter]。
 *
 * [BackdropPainter] 的约定：`painter(modifier, rootCoord)` 在该 modifier 的左上角显示
 * **root 坐标 rootCoord 处的背景内容** —— 玻璃面板靠它拿到"自己背后那块像素"。
 */
@Composable
fun GlassSceneFrame(
    scrollPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(origin: Offset, backdrop: BackdropPainter) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val backdrop: BackdropPainter = { m, o, s -> GlassBackdrop(scrollPx = scrollPx, shift = o - origin, renderScale = s, modifier = m) }
    CaptureHost(
        modifier = modifier,
        background = { m ->
            val withPos = m.onGloballyPositioned { origin = it.positionInRoot() }
            backdrop(withPos, origin, 1f)
        },
    ) {
        content(origin, backdrop)
    }
}

/* ------------------------------------------------------------------------------------------------
 * 场景分发
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
            "raw" -> GlassSceneFrame(scrollPx) { _, _ ->
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

            // ① 对照三连：原始背景（顶部参考条）/ 只有 tint / 重绘背景+真模糊
            "compare" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 150.dp,
                stripHeight = 150.dp,
                stripCaption = "① 原始背景（无玻璃，参考）——下面两块玻璃都采样这一条的同一块像素",
                specs = listOf(
                    demoSpec(
                        label = "② 只有 tint（无模糊）",
                        sub = "同一块背景 + 10% 白色 tint + 1.2dp 边缘高光",
                        api = "API26+",
                        blurDp = 0f,
                        tintAlpha = 0.10f,
                        rim = 0.28f,
                    ),
                    demoSpec(
                        label = "③ 重绘背景 + RenderEffect.blur(24dp)",
                        sub = "背景内容重绘 → BlurEffect → 超椭圆裁剪（本机可用的真模糊路径）",
                        api = "API31+",
                        blurDp = args.blurDp,
                    ),
                ),
            )

            // ② 可读性梯度 8/16/24/32
            "radius" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 96.dp,
                stripHeight = 150.dp,
                stripCaption = "② 原始背景：14sp/11sp 文字 + 棋盘（下面 4 块玻璃采样同一块像素，只改半径）",
                specs = listOf(8f, 16f, 24f, 32f).map { r ->
                    demoSpec(
                        label = "半径 ${fmt(r)}dp",
                        sub = when {
                            r <= 8f -> "小：文字轮廓仍在，但已发虚"
                            r <= 16f -> "中：14sp 可读，11sp 开始糊"
                            r <= 24f -> "大：正文勉强，小字不可读"
                            else -> "很大：只剩色块/明暗"
                        },
                        api = "API31+",
                        blurDp = r,
                    )
                },
            )

            // 饱和度/亮度补偿
            "sat" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 104.dp,
                stripHeight = 120.dp,
                stripCaption = "原始背景：下面同样 24dp 模糊，只改 ColorMatrix 饱和度/亮度补偿",
                specs = listOf(
                    demoSpec("saturation 1.0（不补偿）", "模糊后整体发灰", "API31+", blurDp = 24f),
                    demoSpec("saturation 1.2", "轻度补色", "API31+", blurDp = 24f, sat = 1.2f),
                    demoSpec("saturation 1.5 + bright +0.04", "接近原始饱和感", "API31+", blurDp = 24f, sat = 1.5f, bright = 0.04f),
                    demoSpec("saturation 1.8 + bright +0.06", "过冲：高饱和区变色", "API31+", blurDp = 24f, sat = 1.8f, bright = 0.06f),
                ),
            )

            // 边缘高光 / 内阴影
            "rim" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 116.dp,
                stripHeight = 120.dp,
                stripCaption = "原始背景：下面 blur 24dp，只改边缘高光 / 内阴影",
                specs = listOf(
                    demoSpec("无高光 · 无内阴影", "只有模糊 + tint，像贴纸", "无", blurDp = 24f, rim = 0f, innerShadow = 0f),
                    demoSpec("高光 35% · 内阴影 10%", "1.2dp 白边 + 内沿明暗 → 玻璃厚度感", "无", blurDp = 24f, rim = 0.35f, innerShadow = 0.10f),
                    demoSpec("高光 100% · 内阴影 22%", "过强：边缘发白、像塑料", "无", blurDp = 24f, rim = 1f, innerShadow = 0.22f),
                ),
            )

            // AGSL 折射
            "agsl" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 100.dp,
                stripHeight = 120.dp,
                stripCaption = "原始背景：下面 blur 24dp + AGSL RuntimeShader 伪折射（只能吃自己已渲染的内容）",
                specs = listOf(
                    demoSpec("无折射（对照）", "只 blur", "API31+", blurDp = 24f),
                    demoSpec("折射 6dp", "边缘轻微位移 → 薄玻璃", "API33+", blurDp = 24f, refractDp = 6f),
                    demoSpec("折射 16dp", "边缘明显放大/位移 → 厚玻璃", "API33+", blurDp = 24f, refractDp = 16f),
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

            // 磨砂噪声
            "noise" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 116.dp,
                stripHeight = 120.dp,
                stripCaption = "原始背景：下面 blur 24dp + 程序生成磨砂噪声",
                specs = listOf(0f, 0.012f, 0.028f).map { n ->
                    demoSpec(
                        label = "噪声 ${fmt(n * 100f, 1)}%",
                        sub = if (n == 0f) "纯 blur：过于干净/塑料感" else "128×128 黑白颗粒，双向压 alpha",
                        api = "无（CPU 位图）",
                        blurDp = 24f,
                        noise = n,
                    )
                },
            )

            // ⑥ 降级外观：真模糊 vs API<31 假玻璃
            "legacy" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 150.dp,
                stripHeight = 140.dp,
                stripCaption = "原始背景：下面左/上为真模糊，右/下为 API<31 降级假玻璃",
                specs = listOf(
                    demoSpec(
                        label = "真 backdrop 模糊（API31+）",
                        sub = "重绘背景 + BlurEffect(24dp) + tint + 噪声 + 高光",
                        api = "API31+",
                        blurDp = 24f,
                    ),
                    demoSpec(
                        label = "降级假玻璃（API<31 实际路径）",
                        sub = "不采样背景：半透明 + 渐变 + 噪声 + 高光；不会崩、但看不见背景内容",
                        api = "API26+",
                        legacy = true,
                        noise = 0.012f,
                    ),
                ),
            )

            "subtle" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 118.dp,
                stripHeight = 150.dp,
                stripCaption = "浮层位置对照：下面三块玻璃都采样这条「文字 + 细线」背景（DSH 浮层效果取向）",
                specs = listOf(
                    demoSpec("a · blur 32dp + tint 4%", "低 tint：最接近 DSH「看不清但加分」", "API31+", blurDp = 32f, tintAlpha = 0.04f, rim = 0.22f),
                    demoSpec("b · blur 40dp + tint 8% + sat 1.5", "更「磨砂」一点，细节柔化更明显", "API31+", blurDp = 40f, tintAlpha = 0.08f, sat = 1.5f, rim = 0.26f),
                    demoSpec("c · blur 48dp + tint 2%", "最「清晰透明」：强烈糊化但几乎不着色", "API31+", blurDp = 48f, tintAlpha = 0.02f, rim = 0.20f),
                ),
            )

            "clarity" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 150.dp,
                stripHeight = 140.dp,
                stripCaption = "同一位置两种气质：左/上「清晰透明」，右/下「磨砂面板」",
                specs = listOf(
                    demoSpec("清晰透明：blur 40dp + tint 2%", "细节被柔化但结构仍可辨认，几乎无色偏", "API31+", blurDp = 40f, tintAlpha = 0.02f, rim = 0.22f),
                    demoSpec("磨砂面板：blur 20dp + tint 16%", "可见度高、更「面板」，但背景细节被压掉", "API31+", blurDp = 20f, tintAlpha = 0.16f, rim = 0.34f, noise = 0.012f),
                ),
            )

            "capture" -> CaptureScene(scrollPx)

            "physics" -> PhysicsScene(args, scrollPx)
            "target" -> PhysicsScene(args, scrollPx)   // G-21：target 旧装饰版已删除，别名指向干净测试体

            "refraction" -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 128.dp,
                stripHeight = 132.dp,
                stripCaption = "边缘折射：同一块背景，比较「API33+ 液体 shader」/「API31-32 只有高光+内阴影」/「纯 blur」",
                specs = listOf(
                    demoSpec(
                        label = "API 33+ · 液体折射（AGSL）",
                        sub = "SDF 法线位移 3dp + 色散 1.2px + 边缘高光 + 内阴影",
                        api = "API33+",
                        blurDp = 24f,
                        refractDp = 3f,
                        liquidShader = true,
                        dispersion = 1.2f,
                        rim = 0.55f,
                        innerShadow = 0.16f,
                    ),
                    demoSpec(
                        label = "API 31–32 · 无 shader",
                        sub = "同一背景：只有 1.2dp 边缘高光 + 内阴影（折射缺失）",
                        api = "API31-32",
                        blurDp = 24f,
                        rim = 0.55f,
                        innerShadow = 0.16f,
                    ),
                    demoSpec(
                        label = "对照 · 纯 blur（无高光/折射）",
                        sub = "看高光/折射贡献了多少「玻璃感」",
                        api = "API31+",
                        blurDp = 24f,
                        rim = 0f,
                        innerShadow = 0f,
                    ),
                ),
            )

            "dynamic" -> DynamicScene(args, scrollPx, meter)
            "winblur-dyn" -> WindowBlurDynamicScene(scrollPx)

            "stack" -> StackScene(scrollPx, layers = 2, blurDp = args.blurDp)
            "stack3" -> StackScene(scrollPx, layers = 3, blurDp = args.blurDp)
            "scroll" -> ScrollScene(scrollPx)
            "window" -> WindowScene(scrollPx, args.blurDp, args.autoDialog)
            "perf" -> PerfScene(scrollPx, meter, args.blurDp, args.layers, args.fixedRadius, args.liquid)
            else -> CompareScene(
                scrollPx = scrollPx,
                panelHeight = 150.dp,
                stripHeight = 150.dp,
                specs = listOf(
                    demoSpec("重绘背景 + blur ${fmt(args.blurDp)}dp", "真 backdrop 模糊", "API31+", blurDp = args.blurDp),
                    demoSpec("对照：只有 tint", "无模糊", "API26+", blurDp = 0f, tintAlpha = 0.10f),
                ),
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * 通用对照场景：顶部一条"原始背景参考条"，下面每块玻璃都采样那条里的同一块像素
 * --------------------------------------------------------------------------------------------- */

@Composable
fun CompareScene(
    scrollPx: Float,
    specs: List<GlassSpec>,
    panelHeight: Dp = 96.dp,
    stripHeight: Dp = 120.dp,
    stripCaption: String = "",
) {
    GlassSceneFrame(scrollPx) { origin, backdrop ->
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            var stripInRoot by remember { mutableStateOf<Offset?>(null) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
                    .onGloballyPositioned { stripInRoot = it.positionInRoot() },
            ) {
                Text(
                    text = stripCaption.ifEmpty { "↑ 原始背景参考条（未加玻璃）" },
                    color = Color.White,
                    fontSize = 10.sp,
                    style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            specs.forEach { spec ->
                GlassPanel(
                    spec = spec,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(panelHeight),
                    sampleAtScene = stripInRoot,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * ③ 多层叠加：
 *   左列 = 每层都采样原始背景（叠而不浑浊）
 *   右列 = 后一层采样"已合成结果"（模糊会累积）
 * --------------------------------------------------------------------------------------------- */

private enum class StackMode { SAMPLES_BG, COMPOUND }

@Composable
fun StackScene(scrollPx: Float, layers: Int, blurDp: Float) {
    GlassSceneFrame(scrollPx) { origin, backdrop ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val colW = (maxWidth - 36.dp) / 2
            val panelH = if (layers >= 3) 128.dp else 168.dp
            val step = if (layers >= 3) 86.dp else 108.dp
            val top = 42.dp

            StackLabel("A 每层都采样原始背景（叠而不浑浊）", 12.dp, colW)
            StackLabel("B 后层采样已合成结果（模糊累积）", 24.dp + colW, colW)

            val specs = listOf(
                demoSpec("底层 B", "blur ${fmt(blurDp)}dp", "API31+", blurDp = blurDp),
                demoSpec("中层 A", "blur ${fmt(blurDp)}dp", "API31+", blurDp = blurDp),
                demoSpec("顶层 C", "blur ${fmt(blurDp)}dp", "API31+", blurDp = blurDp),
            )
            StackColumn(origin, backdrop, 12.dp, top, colW, StackMode.SAMPLES_BG, specs.take(layers), panelH, step)
            StackColumn(origin, backdrop, 24.dp + colW, top, colW, StackMode.COMPOUND, specs.take(layers), panelH, step)
        }
    }
}

@Composable
private fun StackLabel(text: String, x: Dp, width: Dp) {
    Text(
        text = text,
        color = Color(0xFFD9F6FF),
        fontSize = 9.sp,
        lineHeight = 11.sp,
        style = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(1f, 1f), 3f)),
        modifier = Modifier
            .offset(x = x, y = 6.dp)
            .width(width)
            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp),
    )
}

@Composable
private fun StackColumn(
    origin: Offset,
    backdrop: BackdropPainter,
    colX: Dp,
    colY: Dp,
    colW: Dp,
    mode: StackMode,
    specs: List<GlassSpec>,
    panelH: Dp,
    step: Dp,
) {
    val d = LocalDensity.current
    val colXpx = with(d) { colX.toPx() }
    val colYpx = with(d) { colY.toPx() }
    val stepPx = with(d) { step.toPx() }
    fun panelRoot(i: Int) = Offset(origin.x + colXpx, origin.y + colYpx + stepPx * i)

    Box(Modifier.offset(x = colX, y = colY).width(colW).height(panelH + step * (specs.size - 1))) {
        when {
            mode == StackMode.SAMPLES_BG -> specs.forEachIndexed { i, spec ->
                GlassPanel(
                    spec = spec,
                    backdrop = backdrop,
                    modifier = Modifier.offset(0.dp, step * i).fillMaxWidth().height(panelH),
                )
            }

            specs.size == 2 -> {
                GlassPanel(
                    spec = specs[0],
                    backdrop = backdrop,
                    modifier = Modifier.offset(0.dp, 0.dp).fillMaxWidth().height(panelH),
                )
                val composite: BackdropPainter = { m, o, s ->
                    CompositeBackdrop(m, o, backdrop, specs[0], panelRoot(0), colW, panelH)
                }
                GlassPanel(
                    spec = specs[1],
                    backdrop = composite,
                    modifier = Modifier.offset(0.dp, step).fillMaxWidth().height(panelH),
                )
            }

            else -> {
                GlassPanel(
                    spec = specs[0],
                    backdrop = backdrop,
                    modifier = Modifier.offset(0.dp, 0.dp).fillMaxWidth().height(panelH),
                )
                val composite1: BackdropPainter = { m, o, s ->
                    CompositeBackdrop(m, o, backdrop, specs[0], panelRoot(0), colW, panelH)
                }
                GlassPanel(
                    spec = specs[1],
                    backdrop = composite1,
                    modifier = Modifier.offset(0.dp, step).fillMaxWidth().height(panelH),
                )
                val composite2: BackdropPainter = { m, o, s ->
                    CompositeBackdrop(m, o, composite1, specs[1], panelRoot(1), colW, panelH)
                }
                GlassPanel(
                    spec = specs[2],
                    backdrop = composite2,
                    modifier = Modifier.offset(0.dp, step * 2).fillMaxWidth().height(panelH),
                )
            }
        }
    }
}

/**
 * "已合成结果"的重绘器：先把下层背景画一遍，再把(root 坐标 [lowerRoot]) 处的下层玻璃画一遍。
 * 这样上层玻璃采样到的就是"内容 + 下层玻璃"的合成结果（等价于真 backdrop 的累积）。
 */
@Composable
private fun CompositeBackdrop(
    modifier: Modifier,
    atRoot: Offset,
    lowerBackdrop: BackdropPainter,
    lowerSpec: GlassSpec,
    lowerRoot: Offset,
    lowerWidth: Dp,
    lowerHeight: Dp,
) {
    Box(modifier) {
        lowerBackdrop(Modifier.fillMaxSize(), atRoot, 1f)
        GlassPanel(
            spec = lowerSpec,
            backdrop = lowerBackdrop,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (lowerRoot.x - atRoot.x).roundToInt(),
                        (lowerRoot.y - atRoot.y).roundToInt(),
                    )
                }
                .width(lowerWidth)
                .height(lowerHeight),
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * ④ 滚动列表上方的玻璃：背景自动滚动，玻璃固定，看模糊是否"跟手更新"
 * --------------------------------------------------------------------------------------------- */

@Composable
fun ScrollScene(scrollPx: Float) {
    GlassSceneFrame(scrollPx) { origin, backdrop ->
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text(
                "④ 滚动列表 + 固定玻璃：背景每帧在滚，玻璃里的模糊应随之更新（冻结=陈旧模糊）",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(6.dp),
            )
            Spacer(Modifier.height(8.dp))
            // 固定位置的玻璃（不随背景滚动）：看它里面的内容是否跟着背景变
            GlassPanel(
                spec = demoSpec("固定玻璃 A（背景在滚）", "若内容不动 = 模糊冻结/陈旧", "API31+", blurDp = 24f),
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            Spacer(Modifier.height(10.dp))
            GlassPanel(
                spec = demoSpec("固定玻璃 B", "同一帧内两块玻璃采样各自位置", "API31+", blurDp = 32f),
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "对比方法：间隔 3 秒截两张图，玻璃内二维码/文字位置应发生位移。",
                color = Color(0xFFB7F7FF),
                fontSize = 9.sp,
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(6.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * ⑤ 性能：1/2/3 层玻璃 + 动画模糊半径
 * --------------------------------------------------------------------------------------------- */

@Composable
fun PerfScene(scrollPx: Float, meter: FrameMeter, blurDp: Float, layers: Int, fixedRadius: Boolean, liquid: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "perf")
    val animRadius by transition.animateFloat(
        initialValue = 6f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "r",
    )
    LaunchedEffect(Unit) {
        meter.start()
        Log.i(GLASSLAB_TAG, "PERF start layers=$layers")
    }
    DisposableEffect(Unit) {
        onDispose { meter.stop() }
    }
    GlassSceneFrame(scrollPx) { origin, backdrop ->
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Spacer(Modifier.height(2.dp))
            repeat(layers.coerceIn(1, 3)) { i ->
                GlassPanel(
                    spec = demoSpec(
                        label = "第 ${i + 1} 层：blur ${if (fixedRadius) fmt(blurDp) else "动画 " + fmt(animRadius)}dp" +
                            (if (liquid) " + 液体 shader" else ""),
                        sub = "共 $layers 层玻璃 + 背景每帧滚动",
                        api = if (liquid) "API33+" else "API31+",
                        blurDp = if (fixedRadius) blurDp else animRadius.coerceAtLeast(4f),
                        refractDp = if (liquid) 3f else 0f,
                        liquidShader = liquid,
                        rim = if (liquid) 0.55f else 0.30f,
                    ),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE6000000))
                    .padding(10.dp),
            ) {
                Text(
                    text = "帧时间（UI 线程 Choreographer，host GPU 模拟器）：\n" + meter.snapshotNow() +
                        "\n\n层数=$layers · 每块玻璃每帧重绘一遍背景",
                    color = Color(0xFFD8F8FF),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * 窗口级模糊（Dialog + FLAG_BLUR_BEHIND + setBackgroundBlurRadius）
 * --------------------------------------------------------------------------------------------- */

@Composable
fun WindowScene(scrollPx: Float, radiusDp: Float, autoDialog: Boolean) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var open by remember { mutableStateOf(autoDialog) }
    var blurEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(open, blurEnabled) {
        if (open && dialog == null) {
            dialog = showBlurDialog(context, radiusDp, blurEnabled)
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

    GlassSceneFrame(scrollPx) { origin, backdrop ->
        Box(Modifier.fillMaxSize()) {
            GlassPanel(
                spec = demoSpec(
                    label = "in-app 玻璃（对照）",
                    sub = "同屏内的 backdrop 模糊（重绘路径）；窗口级模糊若生效，Dialog 后面会整体糊掉",
                    api = "API31+",
                    blurDp = 24f,
                ),
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(150.dp),
            )
            Column(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(onClick = {
                    blurEnabled = true
                    open = true
                }) {
                    Text("打开窗口模糊 Dialog", color = Color.White, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    blurEnabled = false
                    open = true
                }) {
                    Text("打开无模糊对照 Dialog", color = Color(0xFFFFC777), fontSize = 11.sp)
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
}

/**
 * 纯 Android View 的 Dialog（不依赖 Compose Dialog 内部实现，才能可靠拿到 window）。
 * applyBlur=false 时是"无模糊对照"。
 */
internal fun showBlurDialog(context: Context, radiusDp: Float, applyBlur: Boolean): Dialog {
    val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
    val density = context.resources.displayMetrics.density
    val radiusPx = (radiusDp * density).toInt()

    val title = TextView(context).apply {
        text = if (applyBlur) "窗口级模糊（Window blur）" else "对照：无模糊 Dialog"
        setTextColor(AndroidColor.WHITE)
        textSize = 17f
        setPadding(0, 0, 0, 8)
    }
    val body = TextView(context).apply {
        text = if (applyBlur) {
            "FLAG_BLUR_BEHIND + setBackgroundBlurRadius($radiusDp dp)\n" +
                "API ${Build.VERSION.SDK_INT} · crossWindowBlur=${if (Build.VERSION.SDK_INT >= 31) runCatching {
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
                }.getOrDefault(false) else "API<31"}\n\n" +
                "若系统跨窗口模糊真的生效：Dialog 后面的整窗内容应该糊掉。\n" +
                "若没糊：本机跨窗口模糊不可用（或本外观对照见另一张图）。"
        } else {
            "同一个 Dialog，但**不加** FLAG_BLUR_BEHIND / blurRadius。\n" +
                "用来对照「窗口模糊到底有没有生效」。"
        }
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
                if (applyBlur) {
                    val attrs = w.attributes
                    attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    w.attributes = attrs
                    w.setBackgroundBlurRadius(radiusPx)
                }
                w.setDimAmount(0f)
                if (applyBlur) {
                    "已请求 blurRadius=${radiusPx}px（是否真生效看截图）"
                } else {
                    "对照：未请求模糊"
                }
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
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled.toString()
        } else {
            "API<31"
        }
    } catch (t: Throwable) {
        "查询异常"
    }
    Log.i(GLASSLAB_TAG, "WINDOW applyBlur=$applyBlur result=$result crossWindowBlur=${GlassLabDiag.crossWindowBlurResult}")
    dialog.show()
    return dialog
}

/* ------------------------------------------------------------------------------------------------
 * 规格 & 小工具
 * --------------------------------------------------------------------------------------------- */

internal fun demoSpec(
    label: String,
    sub: String,
    api: String,
    blurDp: Float = 24f,
    sat: Float = 1f,
    bright: Float = 0f,
    noise: Float = 0f,
    rim: Float = 0.30f,
    innerShadow: Float = 0.10f,
    tintAlpha: Float = 0.06f,
    legacy: Boolean = false,
    refractDp: Float = 0f,
    refractWholePanel: Boolean = false,
    liquidShader: Boolean = false,
    forceRepaint: Boolean = false,
    dispersion: Float = 1.2f,
    renderScale: Float = 1f,
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
    liquidShader = liquidShader,
    dispersion = dispersion,
    renderScale = renderScale,
    forceRepaint = forceRepaint,
)

internal fun fmt(v: Float, digits: Int = 0): String =
    if (digits <= 0) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.${digits}f", v)

/* ------------------------------------------------------------------------------------------------
 * 动态验证：玻璃下方滚动列表，比较「每帧 / 隔帧 / 降分辨率」三种更新策略
 *   - glass_throttle=N：玻璃里的背景每 N 帧才更新一次（N=1 每帧；N=2 隔帧）
 *   - glass_render_scale=1|0.5|0.25：玻璃内背景以低分辨率渲染再放大
 * 组合方式：adb ... --es glass_scene dynamic --ei glass_throttle 2 --ef glass_render_scale 0.5
 * --------------------------------------------------------------------------------------------- */

@Composable
fun DynamicScene(args: GlassLabArgs, scrollPx: Float, meter: FrameMeter) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(0) }
    var glassScroll by remember { mutableStateOf(scrollPx) }

    LaunchedEffect(scrollPx) {
        frame += 1
        if (args.throttleFrames <= 1 || frame % args.throttleFrames == 0) glassScroll = scrollPx
    }
    LaunchedEffect(Unit) { meter.start() }
    DisposableEffect(Unit) { onDispose { meter.stop() } }

    val scale = args.renderScale.coerceIn(0.2f, 1f)
    val strategy = (if (args.throttleFrames <= 1) "每帧更新" else "隔 ${args.throttleFrames} 帧更新") +
        " · 分辨率 " + (scale * 100f).roundToInt() + "%"

    CaptureHost(recordEvery = args.throttleFrames.coerceAtLeast(1), background = { m -> GlassBackdrop(scrollPx, Offset.Zero, 1f, m) }) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        // 玻璃里的背景：用可能被节流/降采样的 scroll 重绘
        val glassBackdrop: BackdropPainter = { m, o, s -> GlassBackdrop(glassScroll, o - origin, s, m) }

        when (args.area) {
            "top" -> GlassPanel(
                spec = demoSpec("顶栏 60dp（≈5% 屏）", strategy, "API31+", blurDp = 24f).copy(renderScale = scale),
                backdrop = glassBackdrop,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(60.dp),
            )
            "input" -> GlassPanel(
                spec = demoSpec("输入区 100dp（≈8% 屏）", strategy, "API31+", blurDp = 24f).copy(renderScale = scale),
                backdrop = glassBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(100.dp),
            )
            "full" -> GlassPanel(
                spec = demoSpec("整屏玻璃（≈100% 屏）", strategy, "API31+", blurDp = 24f).copy(renderScale = scale),
                backdrop = glassBackdrop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                GlassPanel(
                    spec = demoSpec("顶栏 60dp（≈5% 屏）", strategy, "API31+", blurDp = 24f).copy(renderScale = scale),
                    backdrop = glassBackdrop,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(60.dp),
                )
                GlassPanel(
                    spec = demoSpec("输入区 100dp（≈8% 屏）", strategy, "API31+", blurDp = 24f).copy(renderScale = scale),
                    backdrop = glassBackdrop,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(100.dp),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE6000000))
                .padding(10.dp),
        ) {
            Text(
                "动态策略：$strategy\n" + meter.snapshotNow() +
                    "\n玻璃面积占比：顶栏≈5% + 输入区≈8% ≈ 13% 屏幕面积" +
                    "\n（模拟器基线本身 >16.6ms，只作相对比较）",
                color = Color(0xFFD8F8FF),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
    }
}

/* ------------------------------------------------------------------------------------------------
 * 窗口级 blurBehind 动态测试：全屏透明 overlay window，背后列表在滚
 * --------------------------------------------------------------------------------------------- */

@Composable
fun WindowBlurDynamicScene(scrollPx: Float) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    LaunchedEffect(Unit) {
        runCatching { dialog = showDynamicBlurOverlay(context) }
            .onFailure { GlassLabDiag.windowBlurRadiusResult = "失败：" + it.message }
    }
    DisposableEffect(Unit) {
        onDispose {
            dialog?.dismiss()
            dialog = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(scrollPx, Offset.Zero, 1f, Modifier.fillMaxSize())
        Text(
            "窗口级 blurBehind 动态测试：背后列表在滚。\n" +
                "overlay 后面的内容若「在动 + 是糊的」= 实时跟随（免费方案）；\n" +
                "若锐利或冻结 = 该设备/ROM 不支持跨窗口模糊。",
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color(0xDD000000), RoundedCornerShape(6.dp))
                .padding(10.dp),
        )
    }
}

private fun showDynamicBlurOverlay(context: Context): Dialog {
    val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    val density = context.resources.displayMetrics.density
    val label = TextView(context).apply {
        text = "窗口级 blurBehind（全屏透明 overlay）\nAPI ${Build.VERSION.SDK_INT} · crossWindowBlur=" +
            (if (Build.VERSION.SDK_INT >= 31) runCatching {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
            }.getOrDefault(false) else "API<31") +
            "\n背后的列表在滚动：若这里是糊的且在变 = 实时跟随"
        setTextColor(AndroidColor.WHITE)
        textSize = 12f
        setBackgroundColor(AndroidColor.argb(0x66, 0, 0, 0))
        setPadding(24, 16, 24, 16)
    }
    val root = FrameLayout(context).apply {
        addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = (260 * density).toInt()
            },
        )
    }
    dialog.setContentView(root)
    val result = try {
        if (Build.VERSION.SDK_INT < 31) {
            "API<31：无窗口模糊"
        } else {
            val w = dialog.window
            if (w == null) {
                "window=null"
            } else {
                val attrs = w.attributes
                attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                w.attributes = attrs
                w.setBackgroundBlurRadius((40 * density).toInt())
                w.setDimAmount(0f)
                w.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                "overlay 已请求 blurBehind(40dp)"
            }
        }
    } catch (t: Throwable) {
        "异常：" + (t.message ?: t::class.simpleName)
    }
    GlassLabDiag.windowBlurRadiusResult = result
    Log.i(GLASSLAB_TAG, "WINDOW dynamic overlay: $result")
    dialog.show()
    return dialog
}