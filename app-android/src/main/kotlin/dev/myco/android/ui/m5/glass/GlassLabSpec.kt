package dev.myco.android.ui.m5.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Glass Lab —— 液态玻璃 / 真实模糊实验的规格模型。
 *
 * 这里只描述"一块玻璃长什么样"，不描述它放在哪；渲染见 GlassPanel.kt。
 * 每个字段都能在实验页里被开关/滑杆实时改，方便做前后对照截图。
 */
data class GlassSpec(
    /** 卡片主标题（画在玻璃上，保持锐利） */
    val label: String = "",
    /** 副标题：用来写"API 要求 / 风险" */
    val sub: String = "",
    /** 右上角 API 徽标，如 "API31+" */
    val api: String = "",

    /** 真实 backdrop 模糊半径（dp）；0 = 不模糊 */
    val blurDp: Float = 24f,
    /** 模糊后的饱和度补偿（ColorMatrix setSaturation） */
    val sat: Float = 1f,
    /** 模糊后的亮度补偿（ColorMatrix postTranslate，-0.2..0.2） */
    val bright: Float = 0f,
    /** 磨砂噪声强度（0..0.03） */
    val noise: Float = 0f,
    /** 边缘高光强度（0..1） */
    val rim: Float = 0.35f,
    /** 内阴影强度（0..0.3），0 = 关闭 */
    val innerShadow: Float = 0.10f,
    /** 玻璃 tint（叠在模糊图上，不参与模糊） */
    val tintAlpha: Float = 0.10f,
    val tintColor: Color = Color.White,

    /** 超椭圆参数 */
    val cornerDp: Float = 26f,
    val exponent: Float = 4f,

    /** 降级路径：不采样背景，只用半透明 + 渐变 + 噪声的"假玻璃" */
    val legacy: Boolean = false,

    /** AGSL 伪折射：边缘最大位移（dp）；0 = 不做 */
    val refractDp: Float = 0f,
    /** 折射作用区宽度（占半径比例，0.05..0.6） */
    val refractEdge: Float = 0.30f,
    /** true = 折射作用到整块（含文字），用于展示"错用"的样子 */
    val refractWholePanel: Boolean = false,
    /** true = 用「液体玻璃」AGSL：SDF 边缘法线位移 + 色散 + 镜面高光 + 内阴影 */
    val liquidShader: Boolean = false,
    /** 色散强度（像素，R/B 相对 G 的偏移） */
    val dispersion: Float = 1.2f,
    /** 重绘分辨率倍率：1=全分辨率，0.5=半分辨率（低分辨率渲染再放大，省填充率） */
    val renderScale: Float = 1f,
    /** true = 强制走"重绘背景"而不是捕获层（用于 capture vs repaint 同参数对照） */
    val forceRepaint: Boolean = false,
)

/**
 * 运行期诊断：AGSL 是否可用、跨窗口模糊是否开启、各实验的备注。
 * 页面顶部说明条和报告都靠这里的事实，不允许"猜"。
 */
object GlassLabDiag {
    var agslStatus: String by mutableStateOf("未尝试")
    var agslError: String? by mutableStateOf(null)
    var crossWindowBlurTried: Boolean by mutableStateOf(false)
    var crossWindowBlurResult: String by mutableStateOf("未尝试")
    var windowBlurRadiusResult: String by mutableStateOf("未尝试")

    fun reset() {
        agslStatus = "未尝试"
        agslError = null
        crossWindowBlurTried = false
        crossWindowBlurResult = "未尝试"
        windowBlurRadiusResult = "未尝试"
    }
}
