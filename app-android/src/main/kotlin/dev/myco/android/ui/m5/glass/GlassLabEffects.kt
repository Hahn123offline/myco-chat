package dev.myco.android.ui.m5.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * 所有"真模糊 / 补偿 / 折射 / 噪声"的底层构造。
 *
 * 关键设计：模糊必须只作用于 **采样背景的那一帧内容**（采样层），
 * 而不是用它自己的 alpha 去近似 —— 这是"真 backdrop"和"假玻璃"的分界线。
 */

/**
 * 模糊 + 饱和度/亮度补偿 合成为一个 RenderEffect 链：
 *   blur(radius) → colorMatrix(saturation, brightness)
 * 用 RenderEffect.createColorFilterEffect 把色彩矩阵挂在 blur 之后，
 * 这样才能真正补回"模糊导致的发灰"，而不是在模糊前把颜色拉爆。
 */
fun buildBlurPlusColorEffect(blurPx: Float, sat: Float, bright: Float): ComposeRenderEffect? {
    if (Build.VERSION.SDK_INT < 31) return null
    val needBlur = blurPx > 0.1f
    val needColor = sat != 1f || bright != 0f
    if (!needBlur && !needColor) return null
    return try {
        var effect: RenderEffect? = null
        if (needBlur) {
            effect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        }
        if (needColor) {
            val m = ColorMatrix()
            m.setSaturation(sat)
            if (bright != 0f) {
                // ColorMatrix 没有 postTranslate：亮度补偿用一条加法（translate）矩阵 postConcat 上去
                val d = bright * 255f
                m.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1f, 0f, 0f, 0f, d,
                            0f, 1f, 0f, 0f, d,
                            0f, 0f, 1f, 0f, d,
                            0f, 0f, 0f, 1f, 0f,
                        )
                    )
                )
            }
            val cf = ColorMatrixColorFilter(m)
            effect = if (effect == null) {
                RenderEffect.createColorFilterEffect(cf)
            } else {
                RenderEffect.createColorFilterEffect(cf, effect)
            }
        }
        effect?.asComposeRenderEffect()
    } catch (t: Throwable) {
        null
    }
}

/**
 * AGSL 伪折射（API 33+）。
 *
 * 说明（会写进报告）：RuntimeShader 的输入着色器绑定的是**本节点自身已经渲染出的内容**，
 * 它拿不到窗口背景 / 兄弟节点的像素。所以"玻璃折射背景"必须先用 in-app 捕获把背景画进玻璃，
 * 再用这个 shader 去位移"玻璃自己的内容"，才能看出折射背景的错觉。
 */
internal const val AGSL_REFRACT = """
uniform shader content;
uniform float2 size;
uniform float2 refract;
uniform float edge;
uniform float n;
half4 main(float2 coord) {
    float2 c = size * 0.5;
    float2 p = (coord - c) / max(c, float2(1.0, 1.0));
    float ax = abs(p.x);
    float ay = abs(p.y);
    float d = pow(ax, n) + pow(ay, n);
    float r = pow(max(d, 0.0001), 1.0 / n);
    float k = smoothstep(1.0 - edge, 1.0, r);
    float2 dir = p / max(length(p), 0.0001);
    float2 off = -dir * k * refract;
    return content.eval(clamp(coord + off, float2(0.0, 0.0), size));
}
"""

/** AGSL 构建结果：effect 可能为 null（不可用/编译失败），status 给页面与报告留证。 */
data class RefractBuild(
    val effect: ComposeRenderEffect?,
    val status: String,
    val error: String? = null,
)

fun buildRefractEffect(
    width: Float,
    height: Float,
    refractPx: Float,
    edge: Float,
    exponent: Float,
): RefractBuild {
    if (Build.VERSION.SDK_INT < 33) {
        return RefractBuild(null, "不可用：API ${Build.VERSION.SDK_INT} < 33（AGSL 需要 API 33）")
    }
    if (width <= 1f || height <= 1f) return RefractBuild(null, "尺寸未就绪")
    return try {
        val shader = RuntimeShader(AGSL_REFRACT)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("refract", refractPx, refractPx)
        shader.setFloatUniform("edge", edge)
        shader.setFloatUniform("n", exponent)
        val e = RenderEffect.createRuntimeShaderEffect(shader, "content")
        RefractBuild(e.asComposeRenderEffect(), "可用（RuntimeShader 已生效）")
    } catch (t: Throwable) {
        RefractBuild(null, "失败", t.message ?: t::class.simpleName)
    }
}

/**
 * 程序生成 1~2% 磨砂噪声：每个像素随机取纯黑/纯白，绘制时整体压到低 alpha。
 * 这样噪声是"双向颗粒"（提亮/压暗各一半），不会像纯灰噪声那样把画面整体拉灰。
 */
internal object GlassLabNoise {
    val tile: Bitmap by lazy {
        val n = 128
        val px = IntArray(n * n)
        val rnd = java.util.Random(0x5EED)
        for (i in px.indices) {
            val alpha = 0xFF shl 24
            px[i] = if (rnd.nextBoolean()) {
                alpha or 0x00FFFFFF // 白
            } else {
                alpha or 0x00000000 // 黑
            }
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    val paint: Paint by lazy {
        Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }
}
