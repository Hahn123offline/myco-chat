// M5 玻璃面（G-19 路线 B / G-21 真效果）：真背景模糊 + 真像素位移折射（Snell 棱镜偏折）+ 色散 + 菲涅尔高光/内阴影 + tint。
//
// 版本：**v1（M5Glass，2026-11-27）**。依据 `docs/spec/ref/liquid-glass-shaders.md` §4 与
// `scratch/glass-shaders/m5glass-v1.agsl`，修掉了 v0（`AGSL_PHYSICS_GLASS`）的 3 个结构性缺陷：
//  1. 位移方向：由"沿外向法线向外采样"改为**向内采样**（`offset = R.xy/(-R.z)·thickness`，Snell + 棱镜偏折）。
//     层被隐式裁到 bounds，层外没有像素 → 向外采样只会被 clamp 拉边；向内永远不越界（不需要 LayerOutsets）。
//  2. 法线：差分步长 `e=0.75px` → `e = clamp(band*0.3, 1, 16)px`，并加 `axisFade = smoothstep(0.15, 0.8, |∇d|)`
//     把中线（medial axis）上的法线翻转淡出，消除宽扁玻璃中线的硬缝；
//     色散由"独立 dispPx"改为**与位移成正比**（`ca = offset·uDispersion`，R 少偏 / B 多偏 / G 基准）。
//  3. 效果链：两个 `graphicsLayer{renderEffect}`（blur 层 + shader 层）合并为**一条 chain**：
//     `createChainEffect(shaderEffect, blurEffect)`（javadoc 语义 `outer(inner(source))` = shader(blur(背景))）；
//     SDF 与 clip 同曲线（`uShapeN`：2 = RoundedCornerShape / n = SuperellipseShape），且由**同一个 GlassCorners
//     对象**派生 clip 与 uniform（单一真源，杜绝"边缘位移带与圆角错位"）。
//
// 数据流（每帧）：
//  1. `GlassBackdrop` 把背景录进源层并上屏；
//  2. 本组件把"自己背后那块 + 1.5×blur 的 outsets"录进 ① 效果层（含遮罩色，遮罩属于 backdrop）；
//  3. 效果层 renderEffect = chain(shader, blur)：先真模糊、再 AGSL 折射；
//  4. 把效果层平移 -outset 画回（outsets 给模糊核周围像素，避免边缘透明带），叠 tint；子内容最后画（保持锐利）。
//
// 判据（G-21 / D-72 / liquid-glass-shaders §6）：`refract=0` vs `refract=3dp` 的 diff 必须**边缘-only、内部逐位不变**；
// `uRefract=0,uRim=0,uShadow=0` 必须与"不加 shader 的纯 blur"逐位一致。实测见 `docs/reports/p4-glass-material-report.md`。
//
// ⚠️ 结构边界：
//  * 只能取**同一 Compose 树、同一 window** 里已经画过的背景。Dialog/Popup 独立 window 拿不到
//    （`GlassStructuralLimits`）→ **不允许**用半透明伪造，直接走 A 不透明（[fallbackColor]）。
//  * AGSL 采样不到"窗口背景"；`content` 只能是本层已渲染的内容（先捕获再挂效果）。
//  * 已知缺口：位置来自 `onGloballyPositioned`（与 Haze 1.1.1 同策略），父层只改 graphicsLayer 时用
//    [positionOffset] 补偿；否则动画中捕获区域会滞后（停稳后准确）。
package dev.myco.android.ui.m5.primitives

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * AGSL M5Glass v1（公式来源见文件头；G-21 合规：每个像素都能回答"从什么数据/公式算出来"）。
 *
 * 与 `scratch/glass-shaders/m5glass-v1.agsl` 的**唯一差异**（本仓库实测需要，已写进报告）：
 *  - 增加 `uCenter` / `uHalfSize`：产品实现给模糊核留了 outsets，效果层比玻璃体大 2×outset，
 *    玻璃矩形不居中；候选假设"玻璃体 = 整个层"，这里把"层尺寸(size) / 玻璃中心(center) / 玻璃半尺寸(halfSize)"拆开。
 *  - 高光增加一项**方向性 Phong 镜面** `pow(max(dot(n2, L),0), 3)·bandMask`（物理公式，老 physics 场景同项）：
 *    候选的 `rimLine` 是几何边亮线（刻意不随光向），只靠内阴影体现光向；验收「光向旋转 90° → 边缘高光沿边移动」
 *    需要高光本身有方向性 → 加回 Phong 项（这是从 N·L 算出的物理量，不是贴图/描边近似）。
 */
private const val AGSL_M5GLASS_V1 = """
uniform shader content;      // 已模糊的背景（本层内容），坐标 = 层内像素
uniform float2 uSize;        // 效果层像素尺寸 (W,H)。coord ∈ [0,uSize)
uniform float2 uCenter;      // 玻璃矩形中心在层内的坐标（outsets 后不居中，故显式给）
uniform float2 uHalfSize;    // 玻璃矩形半尺寸
uniform float4 uRadii;       // 四角半径 px：(tl, tr, br, bl) —— 与 GlassCorners.toFloatArray 同序
uniform float  uShapeN;      // 2 = 圆形角（匹配 RoundedCornerShape）；n>2 = 超椭圆角指数（匹配 SuperellipseShape）
uniform float  uBand;        // 倒角带宽度 px：折射/高光只在此带内非零（0 = 关）
uniform float  uSlope;       // 倒角最大坡度 tan(θ)（0..2；1.2 ≈ 50°）
uniform float  uThickness;   // 玻璃等效厚度 px：屏幕位移 = 厚度·tan(θi-θr)
uniform float  uRefract;     // 折射总强度 0..1（0 = 直通，供 A/B 像素 diff）
uniform float  uMaxDisp;     // 单像素采样位移上限 px（防边缘奇点/花屏）
uniform float  uDispersion;  // 色散系数：R/B 采样偏移 = 位移 · 系数
uniform float  uIOR;         // 折射率 空气→玻璃（1.0..2.0，窗口玻璃 1.5）
uniform float  uEdgeGuard;   // 最外缘倒角缓冲 px：位移在此带内衰减到 0
uniform float2 uLightDir;    // 归一化光照方向（屏幕坐标，y 向下；左上光 ≈ (-0.707,-0.707)）
uniform float  uRim;         // 边缘镜面强度 0..1（菲涅尔 + 细亮边 + 方向性 Phong）
uniform float  uShadow;      // 内阴影强度 0..1（背光侧边沿压暗）
uniform float  uSat;         // 饱和度补偿（1 = 不变；G-21 A/B 验收要求保持 1）

// 按象限选角半径：p 以玻璃中心为原点，y 向下 ⇒ 上半 y<=0。
float cornerRadiusFor(float2 p, float4 radii) {
  if (p.y <= 0.0) {
    return p.x <= 0.0 ? radii.x : radii.y;      // 左上 / 右上
  }
  return p.x <= 0.0 ? radii.w : radii.z;        // 左下 / 右下
}

// sdGlass：四角可不同半径的距离场（负=内部）；n=2 精确圆角矩形，n>2 超椭圆角一阶距离。
float sdGlass(float2 p, float2 hs, float4 radii, float n) {
  float r = max(cornerRadiusFor(p, radii), 0.5);   // 0 半径会退化，给 0.5px 下限
  float2 q = abs(p) - hs + float2(r, r);           // 相对"角圆心矩形"的坐标
  if (q.x <= 0.0 && q.y <= 0.0) {
    return max(q.x, q.y) - r;                      // 核心区：到最近直边
  }
  if (q.y <= 0.0) {
    return q.x - r;                                // 左右直边带
  }
  if (q.x <= 0.0) {
    return q.y - r;                                // 上下直边带
  }
  if (n <= 2.01) {
    return length(q) - r;                          // 角区：圆形角（精确）
  }
  // 角区：超椭圆角 |X|^n + |Y|^n = r^n 的一阶距离
  float a = q.x / r;
  float b = q.y / r;
  float f = pow(a, n) + pow(b, n) - 1.0;
  float g = n * sqrt(pow(a, 2.0 * n - 2.0) + pow(b, 2.0 * n - 2.0)) / r;
  float dCorner = f / max(g, 0.00001);
  return max(dCorner, min(q.x, q.y) - r);
}

// 宽差分梯度：返回 (gx, gy, |∇d|)。|∇d|≈1 普通边带，→0 落在中线（法线翻转处，需淡出）。
float3 sdfGradWide(float2 p, float2 hs, float4 radii, float n, float e) {
  float gx = sdGlass(p + float2(e, 0.0), hs, radii, n) - sdGlass(p - float2(e, 0.0), hs, radii, n);
  float gy = sdGlass(p + float2(0.0, e), hs, radii, n) - sdGlass(p - float2(0.0, e), hs, radii, n);
  float2 g = float2(gx, gy) / (2.0 * e);
  return float3(g.x, g.y, length(g));
}

half4 main(float2 coord) {
  // ---- 0) 纯模糊直通：A/B 验证基准（refract/高光/阴影全关时必须逐位等于 blur 结果）
  if (uRefract <= 0.0 && uRim <= 0.0 && uShadow <= 0.0) {
    return content.eval(clamp(coord, float2(0.0, 0.0), uSize - float2(1.0, 1.0)));
  }

  float2 p = coord - uCenter;

  // ---- 1) 形状距离场 + 表面法线 ----
  float d = sdGlass(p, uHalfSize, uRadii, uShapeN);   // <0 内部
  float distEdge = max(-d, 0.0);                      // 到边的距离 px

  float e = clamp(uBand * 0.3, 1.0, 16.0);            // 宽差分步长：band 的 30%，clamp 1..16px
  float3 g3 = sdfGradWide(p, uHalfSize, uRadii, uShapeN, e);
  float gl = max(length(g3.xy), 0.00001);
  float2 n2 = g3.xy / gl;                             // 外向法线（指向最近边）
  float axisFade = smoothstep(0.15, 0.8, g3.z);       // 中线 fade：|∇d| 小的脊上折射→0

  // ---- 2) 倒角剖面：边缘最陡，向内衰减到 0 ----
  float t = clamp(distEdge / max(uBand, 1.0), 0.0, 1.0);
  float ramp = pow(1.0 - t, 1.65) * axisFade;
  float guard = smoothstep(0.0, max(uEdgeGuard, 0.001), distEdge);
  float bandMask = 1.0 - smoothstep(0.0, max(uBand, 1.0), distEdge);

  // 3D 表面法线：把 2D 法线按坡度倾斜，nz 由单位长度约束解出
  float2 nxy = n2 * (uSlope * ramp * guard);
  float nz = sqrt(max(1.0 - dot(nxy, nxy), 0.001));
  float3 N = float3(nxy.x, nxy.y, nz);

  // ---- 3) Snell 折射 + 厚度位移（向内采样）----
  float3 I = float3(0.0, 0.0, -1.0);
  float eta = 1.0 / max(uIOR, 1.0);
  float ci = dot(N, I);
  float k = 1.0 - eta * eta * (1.0 - ci * ci);
  float3 R = eta * I - (eta * ci + sqrt(max(k, 0.0))) * N;
  float2 offset = (R.xy / max(-R.z, 0.05)) * (uThickness * uRefract);

  // 位移上限（软上限：只在极端处压回，不改变带内梯度形状）
  float mag = length(offset);
  offset *= min(1.0, uMaxDisp / max(mag, 0.00001));

  float2 lo = float2(0.0, 0.0);
  float2 hi = uSize - float2(1.0, 1.0);
  float2 base = clamp(coord + offset, lo, hi);

  // ---- 4) 正常色散：n_R < n_B ⇒ 红光偏折小于蓝光 ----
  //   R 采样点 = base - ca（位移更小），B = base + ca（位移更大），G 不偏（避免整体偏色）
  float2 ca = offset * uDispersion;
  half4 sR = content.eval(clamp(base - ca, lo, hi));
  half4 sG = content.eval(base);
  half4 sB = content.eval(clamp(base + ca, lo, hi));
  float3 col = float3(sR.r, sG.g, sB.b);

  // ---- 5) 饱和度补偿 ----
  float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
  col = mix(float3(luma, luma, luma), col, uSat);

  // ---- 6) 光照：全部由几何 + 公式推出 ----
  float2 L = normalize(uLightDir);
  // Schlick 菲涅尔（玻璃 F0 ≈ 0.04）：只在倒角带内调制，平中央不参与
  float fres = 0.04 + 0.96 * pow(1.0 - nz, 5.0);
  // 细亮边：最外 ~uEdgeGuard 一圈的几何边（几何轮廓，与光向无关）
  float rimLine = 1.0 - smoothstep(0.0, max(uEdgeGuard * 1.2, 1.0), distEdge);
  // 方向性 Phong 镜面：随光向沿边移动（证明高光来自法线 N·L，不是贴图/等宽描边）
  float specDir = pow(max(dot(n2, L), 0.0), 3.0) * bandMask;
  float rim = ((fres * bandMask) * 0.35 + rimLine * 0.4) * uRim + specDir * uRim * 0.5;
  // 内阴影：背光侧更强，只作用于倒角带（几何 + 光向推出）
  float back = clamp(0.5 - 0.5 * dot(n2, L), 0.0, 1.0);
  float shade = (1.0 - smoothstep(0.0, max(uBand * 1.5, 0.001), distEdge)) * (0.35 + 0.65 * back) * uShadow;

  col = col * (1.0 - shade) + float3(rim, rim, rim);
  col = clamp(col, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));
  return half4(col.r, col.g, col.b, 1.0);
}
"""

/** 着色器构建诊断（报告/调试用；不参与视觉）。 */
object GlassDiagnostics {
    /** 最近一次构建结果：`FULL` / `SHADER_FAILED` / `API_TOO_LOW`。 */
    @Volatile
    var status: String = "未构建"

    /** 最近一次构建异常（null = 成功）。 */
    @Volatile
    var lastError: String? = null
}

/**
 * 玻璃面。
 *
 * 必须是 [GlassBackdrop] 的**后代**（同一 Compose 树），否则取不到背景层 → 走 A 不透明。
 *
 * @param fallbackColor A 路线兜底色（**不透明**；G-19：做不到真玻璃时不许半透明）。默认用主题 surface。
 * @param material 材质 token（D-73）；`GlassMaterial.noRefraction()` 可做 diff 对照（不是产品档位）。
 * @param corners 四角半径 + 形状指数：**同一对象**同时派生 clip 形状与 shader 的 SDF（G-21 单一真源）。
 * @param positionOffset 调用方用 `graphicsLayer` 位移本组件（转场/跟手）时，把同一个位移传进来
 *   （`onGloballyPositioned` 只在布局变化时派发；静态位置传 `Offset.Zero`）。
 * @param backdropScrim 玻璃**下方那一层**的遮罩色（如 overlay scrim `#000 α0.32`）。`backdrop-filter`
 *   采样包含面板之下已画过的所有层；遮罩在玻璃下面就必须算进背景，否则浅色主题下玻璃会比周围亮一截。
 * @param lightDir 屏幕坐标的归一化光向（y 向下）；默认取全局 LightRig 左上主光。验证用可旋转 90°。
 * @param debugPureBlur **仅验收用**：true = 只挂 blur 不挂 shader，用于证明 chain 直通等价（产品不传）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    material: GlassMaterial = GlassMaterial.Overlay,
    corners: GlassCorners = GlassCorners.uniform(material.corner),
    fallbackColor: Color = MaterialTheme.colorScheme.surface,
    positionOffset: Offset = Offset.Zero,
    backdropScrim: Color = Color.Transparent,
    lightDir: Offset = DEFAULT_LIGHT_DIR,
    debugPureBlur: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    // 效果层：一个 GraphicsLayer 承载"捕获背景的裁剪 + chain(shader, blur)"（v1 合并为单层）
    val effectLayer = rememberGraphicsLayer()

    val blurPx = with(density) { material.blur.toPx() }
    val wantedOutsetsPx = (blurPx * 1.5f).roundToInt().coerceAtLeast(1)
    // 逐边外扩：贴屏幕边的一侧不外扩（否则层里出现透明圈，模糊后露出背后真实背景）
    val outsets = remember(
        positionInRoot,
        positionOffset,
        sizePx,
        backdrop?.originInRoot,
        backdrop?.sizeInRoot,
        wantedOutsetsPx,
    ) {
        if (backdrop == null) {
            GlassOutsets()
        } else {
            GlassOutsets.compute(
                glassPositionInRoot = positionInRoot + positionOffset,
                glassSizePx = sizePx,
                sourceOriginInRoot = backdrop.originInRoot,
                sourceSizeInRoot = backdrop.sizeInRoot,
                wanted = wantedOutsetsPx,
            )
        }
    }

    val chain = remember(
        sizePx,
        material,
        corners,
        density,
        lightDir,
        debugPureBlur,
        blurPx,
        outsets,
    ) {
        buildGlassChain(
            material = material,
            corners = corners,
            density = density,
            glassWidthPx = sizePx.width,
            glassHeightPx = sizePx.height,
            outsets = outsets,
            blurPx = blurPx,
            lightDir = lightDir,
            pureBlurOnly = debugPureBlur,
        )
    }
    GlassDiagnostics.status = chain.status.name
    GlassDiagnostics.lastError = chain.error

    val glassEffect = chain.effect
    val shape = remember(corners) { corners.toShape() }
    val canDrawGlass = glassEffect != null && backdrop != null

    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                positionInRoot = coordinates.positionInRoot()
                val newSize = coordinates.size
                if (newSize != sizePx) sizePx = IntSize(newSize.width, newSize.height)
            }
            .clip(shape)
            .drawWithContent {
                val drawn = canDrawGlass && backdrop != null && drawGlassBackdrop(
                    backdrop = backdrop,
                    glassPositionInRoot = positionInRoot + positionOffset,
                    effectLayer = effectLayer,
                    effect = checkNotNull(glassEffect),
                    outsets = outsets,
                    tint = material.tint,
                    backdropScrim = backdropScrim,
                )
                if (!drawn) {
                    // G-19/G-21：做不到真玻璃（API<33 / 软件渲染 / 着色器失败 / 无捕获层）
                    // → A 不透明 neumorphism 的底，**不是**半透明。
                    drawRect(fallbackColor)
                }
                this@drawWithContent.drawContent()
            },
        content = content,
    )
}

/**
 * 把"玻璃背后那块背景"从源层裁出来 → chain(shader, blur) → 上屏。返回 false 表示本帧画不了（走 A 兜底）。
 *
 * 效果层比玻璃体四周各大 `outsetsPx`：模糊核在边界外没有像素可采，不留 outsets 会出现边缘透明/拉边带
 * （坑：本栈没有 1.12 的 LayerOutsets，只能手工做）。shader 用 `uCenter/uHalfSize` 把玻璃矩形定位在层内。
 */
private fun DrawScope.drawGlassBackdrop(
    backdrop: GlassBackdropState,
    glassPositionInRoot: Offset,
    effectLayer: GraphicsLayer,
    effect: ComposeRenderEffect,
    outsets: GlassOutsets,
    tint: Color,
    backdropScrim: Color,
): Boolean {
    val source = backdrop.layer
    if (source.isReleased || !backdrop.hasContent) return false
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    if (w <= 1 || h <= 1) return false

    // 源层坐标 → 本玻璃局部坐标的平移量（玻璃位置每帧可能变）
    val dx = backdrop.originInRoot.x - glassPositionInRoot.x
    val dy = backdrop.originInRoot.y - glassPositionInRoot.y

    // ① 裁"玻璃背后那块 + 逐边 outsets"进效果层；遮罩画进同一层（遮罩在玻璃下面 = 背景的一部分）
    effectLayer.record(IntSize(outsets.layerWidth(w), outsets.layerHeight(h))) {
        translate(outsets.left + dx, outsets.top + dy) {
            drawLayer(source)
        }
        if (backdropScrim.alpha > 0f) {
            drawRect(backdropScrim)
        }
    }
    effectLayer.renderEffect = effect

    // ② 效果层画回（裁掉 outsets），再叠 tint
    translate(-outsets.left.toFloat(), -outsets.top.toFloat()) {
        drawLayer(effectLayer)
    }
    drawRect(tint)
    return true
}

/**
 * 效果层的四边外扩量（px）。模糊核在边界外没有像素可采 → 需要 outsets。
 *
 * **但不能扩到源层（屏幕）之外**：玻璃贴着屏幕边时，层里那圈会是透明 → 模糊后边缘半透明，
 * 被 A 路线"纯 blur"画出来会露出背后真实背景（锐利条纹），且 shader 采样到透明值。
 * 所以按"玻璃边到源层边的可用距离"逐边取 min(需求, 可用)：贴屏幕边的方向不外扩，
 * 模糊在对齐的层边界上以 CLAMP 收口（= 真 backdrop 在屏幕边界的正确行为）。
 */
private data class GlassOutsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    fun layerWidth(glassW: Int): Int = glassW + left + right

    fun layerHeight(glassH: Int): Int = glassH + top + bottom

    companion object {
        fun compute(
            glassPositionInRoot: Offset,
            glassSizePx: IntSize,
            sourceOriginInRoot: Offset,
            sourceSizeInRoot: IntSize,
            wanted: Int,
        ): GlassOutsets {
            if (glassSizePx.width <= 1 || glassSizePx.height <= 1 || sourceSizeInRoot.width <= 0) {
                return GlassOutsets()
            }
            val glassLeft = glassPositionInRoot.x
            val glassTop = glassPositionInRoot.y
            val glassRight = glassLeft + glassSizePx.width
            val glassBottom = glassTop + glassSizePx.height
            val srcLeft = sourceOriginInRoot.x
            val srcTop = sourceOriginInRoot.y
            val srcRight = srcLeft + sourceSizeInRoot.width
            val srcBottom = srcTop + sourceSizeInRoot.height
            fun clampOut(available: Float): Int =
                minOf(wanted.toFloat(), available.coerceAtLeast(0f)).roundToInt()
            return GlassOutsets(
                left = clampOut(glassLeft - srcLeft),
                top = clampOut(glassTop - srcTop),
                right = clampOut(srcRight - glassRight),
                bottom = clampOut(srcBottom - glassBottom),
            )
        }
    }
}

/** 一次构建的结果：effect 为 null 时调用方走 A 不透明（D-74），status/error 进诊断与报告。 */
private data class GlassChain(
    val effect: ComposeRenderEffect?,
    val status: GlassAvailability,
    val error: String? = null,
)

/**
 * 构建 v1 效果链（单一 chain，避免两层离屏 pass）：
 *   `result = shader(blur(capturedBackdrop))`
 * 参数换算（liquid-glass-shaders §4.4）：
 *  - `refract` token 是"最大采样位移 dp" → `uMaxDisp = refractPx`，`uThickness` 由 `refractPx / tanDeviation(slope, ior)` 反解；
 *  - `dispersion` token 是"绝对位移 px" → `uDispersion = dispersionPx / refractPx`（与位移成正比）；
 *  - `uSat` 固定 1（G-21 A/B 验收要求内区逐位不变；饱和度补偿会改内区像素 → 留待 Lead 裁决）。
 */
private fun buildGlassChain(
    material: GlassMaterial,
    corners: GlassCorners,
    density: Density,
    glassWidthPx: Int,
    glassHeightPx: Int,
    outsets: GlassOutsets,
    blurPx: Float,
    lightDir: Offset,
    pureBlurOnly: Boolean,
): GlassChain {
    val blurEffect: RenderEffect? = if (blurPx > 0.01f && Build.VERSION.SDK_INT >= 31) {
        runCatching {
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        }.getOrNull()
    } else {
        null
    }
    if (pureBlurOnly) {
        // 验收专用：不加 shader 的纯 blur（与 chain 直通档逐位对比）
        return GlassChain(blurEffect?.asComposeRenderEffect(), GlassAvailability.FULL)
    }
    if (Build.VERSION.SDK_INT < GLASS_MIN_SDK) {
        return GlassChain(null, GlassAvailability.API_TOO_LOW)
    }
    if (glassWidthPx <= 2 || glassHeightPx <= 2) {
        // 尺寸未就绪：返回 null 但不算失败（下一帧会重建）
        return GlassChain(null, GlassAvailability.FULL)
    }
    return try {
        val bandPx = with(density) { material.band.toPx() }
        val refractPx = with(density) { material.refract.toPx() }
        val edgeGuardPx = with(density) { EDGE_GUARD_DP.toPx() }
        val maxDispPx = refractPx.coerceAtLeast(0f)
        val dispersion = if (refractPx > 0.01f) {
            (material.dispersionPx / refractPx).coerceIn(0f, 1f)
        } else {
            0f
        }
        val slope = SLOPE
        val ior = IOR
        val thicknessPx = if (refractPx > 0.01f) refractPx / tanDeviation(slope, ior) else 0f

        val shader = RuntimeShader(AGSL_M5GLASS_V1)
        // 层尺寸（含 outsets）与玻璃矩形在该层内的位置 —— 三者必须与 drawGlassBackdrop 的录制一致
        shader.setFloatUniform("uSize", outsets.layerWidth(glassWidthPx).toFloat(), outsets.layerHeight(glassHeightPx).toFloat())
        shader.setFloatUniform("uCenter", outsets.left + glassWidthPx * 0.5f, outsets.top + glassHeightPx * 0.5f)
        shader.setFloatUniform("uHalfSize", glassWidthPx * 0.5f, glassHeightPx * 0.5f)
        // 形状：与 clip 同一组数值 + 同一模式（G-21：裁剪轮廓 = 折射轮廓）
        shader.setFloatUniform("uRadii", corners.toFloatArray(density))
        shader.setFloatUniform("uShapeN", corners.effectiveShapeExponent)
        shader.setFloatUniform("uBand", bandPx)
        shader.setFloatUniform("uSlope", slope)
        shader.setFloatUniform("uThickness", thicknessPx)
        shader.setFloatUniform("uRefract", if (refractPx > 0.01f) 1f else 0f)
        shader.setFloatUniform("uMaxDisp", maxDispPx.coerceAtLeast(0.01f))
        shader.setFloatUniform("uDispersion", dispersion)
        shader.setFloatUniform("uIOR", ior)
        shader.setFloatUniform("uEdgeGuard", edgeGuardPx)
        shader.setFloatUniform("uLightDir", lightDir.x, lightDir.y)
        shader.setFloatUniform("uRim", material.rim)
        shader.setFloatUniform("uShadow", material.innerShadow)
        shader.setFloatUniform("uSat", 1f)

        val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val composed: RenderEffect = if (blurEffect != null) {
            // javadoc：createChainEffect(outer, inner) = outer(inner(source)) = shader(blur(content))
            RenderEffect.createChainEffect(shaderEffect, blurEffect)
        } else {
            shaderEffect
        }
        GlassChain(composed.asComposeRenderEffect(), GlassAvailability.FULL)
    } catch (t: Throwable) {
        GlassChain(
            effect = null,
            status = GlassAvailability.SHADER_FAILED,
            error = "${t.javaClass.simpleName}: ${t.message}",
        )
    }
}

/** 棱镜偏折角的正切：`tan(θi − θr)`，`sinθr = sinθi / ior`（slope = tanθi，θi = atan(slope)）。 */
private fun tanDeviation(slope: Float, ior: Float): Float {
    val thetaI = kotlin.math.atan(slope)
    val sinThetaR = (kotlin.math.sin(thetaI) / ior).coerceIn(-0.999f, 0.999f)
    val thetaR = kotlin.math.asin(sinThetaR)
    return kotlin.math.tan(thetaI - thetaR).coerceAtLeast(0.0001f)
}

/** 产品侧物理常量（不接 token）：倒角最大坡度、折射率、最外缘保护带。 */
private const val SLOPE: Float = 1.2f
private const val IOR: Float = 1.5f
private val EDGE_GUARD_DP = 1.5.dp

/** 全局唯一光向 token（`LightRig(azimuth 135°, elevation 55°)` 的屏幕投影：左上前方）。 */
val DEFAULT_LIGHT_DIR: Offset = Offset(-0.70710678f, -0.70710678f)

/** 验证用：把光向绕屏幕法线旋转（D 验收：旋转 90° 后高光/内阴影应沿边移动）。 */
fun rotateLightDir(lightDir: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    return Offset(lightDir.x * cos - lightDir.y * sin, lightDir.x * sin + lightDir.y * cos)
}

/** 供宿主/调试查"当前玻璃是否真的生效"（含 API/硬件/着色器三关）。 */
@Composable
fun rememberGlassDiagnostics(): GlassAvailability = rememberGlassAvailability()
