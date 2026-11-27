// M5 玻璃材质 token + API 判定（G-19 路线 B / G-21 真效果 / D-73…D-75）。
//
// 来源（实现依据，不在此重复推导）：
//  * `docs/spec/decision-pack.md` §6.20 D-73：blur 24dp（浮层）/modal 32–40dp、tint 4% 白、
//    refract 3dp、band 6dp、dispersion 1px、rim 35%、innerShadow 25%；
//  * `docs/spec/decision-pack.md` D-74：**只有 API 33+ 启用玻璃**（需要 RuntimeShader 才有"玻璃标识"）。
//    API 31–32 的"裸 blur + tint"实测像磨砂面板、无边缘弯折 → 按 G-21 **不算玻璃**，直接走 A 不透明
//    neumorphism；API < 31 同理。**禁止**把玻璃降级成"半透明 + 无折射"的普通透明（G-19）。
//  * `docs/reports/glass-lab-report.md` §12：physics 场景实测，refract=0 vs 3dp 的 diff 为
//    "边缘-only、内区 0 变化"（0.68% 像素变化、内缩 60px 内 0/206,596）。
//
// 本文件只放**数值与能力判定**，不画东西（绘制在 `GlassSurface.kt`）。
package dev.myco.android.ui.m5.primitives

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M5 玻璃材质 token（数值 = D-73 定稿，**改这里就是改全 app 的玻璃**）。
 *
 * 全部参数都可以回答"它是从什么数据/公式算出来的"（G-21 落地要求）：
 *  - [blur]：背景捕获层的 `RenderEffect.createBlurEffect` 半径（真模糊，不是 tint 错觉）；
 *  - [refract] / [band]：`uv += sdfNormal(p) * smoothstep(band, 0, distToEdge) * refract` 的像素位移，
 *    `distToEdge` 来自圆角矩形 SDF，**只在边缘带内非零** → 内部像素逐位不变；
 *  - [dispersion]：R/B 采样点沿法线反向偏移 ±dispersion（px），G 不偏；
 *  - [rim]：`pow(max(dot(N, L), 0), 4) * thin(distToEdge)`，L = 固定光向（全局 LightRig 左上主光）；
 *  - [innerShadow]：`smoothstep(14px, 0, distToEdge) * 背光侧权重`；
 *  - [tint]：唯一的产品参数（均匀白 alpha），不做任何渐变。
 *
 * ⚠️ 与 D-73 的偏差说明：D-73 写 "dispersion 1px"，这里保留**物理像素**语义（采样层是设备像素），
 * 不是 dp —— 1dp 在 420dpi 上是 2.6px，会明显比实测档"花"。若产品要按 dp 走，改 [dispersionPx] 的单位即可。
 */
@Immutable
data class GlassMaterial(
    /** 背景模糊半径（dp）。浮层 24dp；modal 32–40dp（D-73）。 */
    val blur: Dp = 24.dp,
    /** 均匀白 tint；D-73 = 4% 白。 */
    val tint: Color = Color.White.copy(alpha = DEFAULT_TINT_ALPHA),
    /** 边缘像素位移强度（dp）；0 = 不做折射（用于 diff 对照）。 */
    val refract: Dp = 3.dp,
    /** 折射生效的边缘带宽（dp）；6dp 内从 1 衰减到 0。 */
    val band: Dp = 6.dp,
    /** 色散：R/B 相对 G 的采样偏移（**px**）；D-73 = 1px。 */
    val dispersionPx: Float = 1f,
    /** 沿边镜面高光强度（0–1）；D-73 = 35%。 */
    val rim: Float = 0.35f,
    /** 内阴影强度（0–1）；D-73 = 25%。 */
    val innerShadow: Float = 0.25f,
    /** 默认圆角（形状参数，与折射无关；sheet 26dp）。 */
    val corner: Dp = 26.dp,
) {
    companion object {
        /** D-73 定稿：4% 白 tint。 */
        const val DEFAULT_TINT_ALPHA: Float = 0.04f

        /** 浮层（抽屉 / sheet / 菜单 / 灯箱）默认档。 */
        val Overlay: GlassMaterial = GlassMaterial()

        /** 强隔离的居中 modal：D-73 的 32–40dp 档，取中值 36dp。 */
        val Modal: GlassMaterial = GlassMaterial(blur = 36.dp)

        /** 无折射的对照档：只用于验证"refract 只在边缘改变像素"（G-21 判据），**不是产品档位**。 */
        fun noRefraction(base: GlassMaterial = Overlay): GlassMaterial =
            base.copy(refract = 0.dp, dispersionPx = 0f)
    }
}

/**
 * 圆角四角（topStart/topEnd/bottomEnd/bottomStart，LTR）+ 角曲线指数。
 *
 * 形状与着色器的 SDF 必须用**同一组半径 / 同一条曲线**：形状负责裁剪（Compose `clip`），SDF 负责法线/边缘带。
 * 两者不一致时边缘折射会出现在"其实已经被裁掉"的角上（视觉上是脏边/固定亮环，G-21 违规）。
 * `shapeExponent = 2` → `RoundedCornerShape`；`> 2` 且四角相等 → `SuperellipseShape(radius, exponent)`。
 * 本类同时是 clip 与 shader uniform 的**唯一真源**（调用方不单独传这两个值）。
 */
@Immutable
data class GlassCorners(
    val topStart: Dp = 0.dp,
    val topEnd: Dp = 0.dp,
    val bottomEnd: Dp = 0.dp,
    val bottomStart: Dp = 0.dp,
    /** 角曲线指数：2 = 圆角矩形；4 = 设计系统 SuperellipseShape(exponent=4)。 */
    val shapeExponent: Float = 2f,
) {
    /** 实际生效的角指数：只有四角半径相等时才允许超椭圆（SuperellipseShape 只支持统一半径）。 */
    val effectiveShapeExponent: Float
        get() = if (shapeExponent > 2.01f && topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart) {
            shapeExponent
        } else {
            2f
        }

    fun toShape(): androidx.compose.ui.graphics.Shape =
        if (effectiveShapeExponent > 2.01f) {
            // 四角相等（上面已校验）→ 设计系统的超椭圆角，与 shader 的 uShapeN 同一条曲线
            dev.myco.android.ui.m5.SuperellipseShape(topStart, effectiveShapeExponent)
        } else {
            androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = topStart,
                topEnd = topEnd,
                bottomEnd = bottomEnd,
                bottomStart = bottomStart,
            )
        }

    /** 玻璃片在屏幕左/右/上/下边缘"被裁断"时，对应角给 0（真实玻璃没有自由边就没有折射）。 */
    fun toFloatArray(density: androidx.compose.ui.unit.Density): FloatArray = with(density) {
        floatArrayOf(
            topStart.toPx(),
            topEnd.toPx(),
            bottomEnd.toPx(),
            bottomStart.toPx(),
        )
    }

    companion object {
        fun uniform(radius: Dp): GlassCorners =
            GlassCorners(radius, radius, radius, radius)

        /** 底部 sheet：只有上面两个角圆（底部与屏幕底边齐平）。 */
        fun sheet(radius: Dp): GlassCorners =
            GlassCorners(topStart = radius, topEnd = radius)

        /** 左侧抽屉：只有右侧两个角圆（左边缘在屏幕外）。 */
        fun drawer(radius: Dp): GlassCorners =
            GlassCorners(topEnd = radius, bottomEnd = radius)
    }
}

/** 玻璃最低 API：`RuntimeShader`（AGSL）是"玻璃标识"（边缘折射/色散/几何高光/内阴影）的前提，API 33。 */
const val GLASS_MIN_SDK: Int = 33

/**
 * 能力判定结果（渲染层按它选"真玻璃"还是"A 不透明"；**没有第三档**）。
 */
enum class GlassAvailability {
    /** 真玻璃：API 33+ + 硬件加速 + 着色器构建成功。 */
    FULL,

    /** API < 33：没有 RuntimeShader → 结构不允许做玻璃（D-74）→ A 不透明。 */
    API_TOO_LOW,

    /** 软件渲染（Compose Preview / 关闭硬件加速）：`RenderEffect` 被忽略，模糊不会产生 → A 不透明。 */
    SOFTWARE_RENDERING,

    /** 着色器构建失败（该 GPU/驱动不支持 RuntimeShader 或半径异常）→ A 不透明。 */
    SHADER_FAILED,
}

/**
 * 纯函数判定（可 JVM 单测）。
 *
 * @param sdkInt 设备 API（默认取运行时值，便于单测传参）
 * @param hardwareAccelerated 是否硬件加速（Compose 里读 `LocalView.current.isHardwareAccelerated`）
 */
fun glassAvailability(
    sdkInt: Int = Build.VERSION.SDK_INT,
    hardwareAccelerated: Boolean = true,
    shaderFailed: Boolean = false,
): GlassAvailability = when {
    sdkInt < GLASS_MIN_SDK -> GlassAvailability.API_TOO_LOW
    !hardwareAccelerated -> GlassAvailability.SOFTWARE_RENDERING
    shaderFailed -> GlassAvailability.SHADER_FAILED
    else -> GlassAvailability.FULL
}

/**
 * 玻璃是否可用：**API ≥ 33 且硬件加速**（D-74）。
 *
 * 注意：`hardwareAccelerated` 默认 `true` 只方便纯逻辑调用（单测/报告）；UI 里请用
 * [rememberGlassSupported]，它会读真实的 `LocalView`。
 */
fun isGlassSupported(
    sdkInt: Int = Build.VERSION.SDK_INT,
    hardwareAccelerated: Boolean = true,
): Boolean = glassAvailability(sdkInt, hardwareAccelerated) == GlassAvailability.FULL

/** 组合内判定：读当前 view 的真实硬件加速状态（软件渲染 → 一律 A 路线）。 */
@Composable
fun rememberGlassSupported(): Boolean {
    val view = LocalView.current
    return remember(view) { isGlassSupported(Build.VERSION.SDK_INT, view.isHardwareAccelerated) }
}

/** 组合内判定（含"着色器构建失败"之后的二次降级）。 */
@Composable
fun rememberGlassAvailability(shaderFailed: Boolean = false): GlassAvailability {
    val view = LocalView.current
    return remember(view, shaderFailed) {
        glassAvailability(Build.VERSION.SDK_INT, view.isHardwareAccelerated, shaderFailed)
    }
}

/**
 * 结构边界（G-21「结构不允许」的代码真源）。
 *
 * 捕获路径（`GraphicsLayer.record { drawContent() }` + 兄弟节点 `drawLayer`）**只能拿到同一 Compose
 * 树、同一 window 里已经画过的背景**（`docs/spec/ref/backdrop-blur-deep.md` §2.1 / §3）。
 *
 * 现有三个浮层原语 **M5Sheet / M5Modal / M5Lightbox 都用 `Dialog` = 独立 window**：
 * 主窗口内容不在该 window 的 draw 树里 → **结构不允许捕获**，按 G-21 保持 A 不透明，
 * 不许用半透明 + blur 伪造。要玻璃形态时用同一树内的 `M5GlassSheet` / `M5GlassDrawer`
 * （见 `M5Sheet.kt`），它们由 Lead 在 ChatScreen/Shell 接线。
 */
object GlassStructuralLimits {
    /** 独立 window（Compose `Dialog` / `Popup`）里的浮层不能做捕获玻璃。 */
    const val INDEPENDENT_WINDOW_REASON: String =
        "结构不允许：Compose Dialog/Popup 是独立 window，同一 Compose 树的捕获层拿不到主窗口内容（G-21）"

    /** 该浮层是否用独立 window（true = 不能做捕获玻璃，必须保持 A 不透明）。 */
    fun usesIndependentWindow(usesDialog: Boolean): Boolean = usesDialog

    /** 该位置能否用捕获玻璃。 */
    fun canCaptureBackdrop(usesDialog: Boolean): Boolean = !usesDialog
}
