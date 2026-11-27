package dev.myco.android.ui.m5.glass

import android.content.Intent

/**
 * 截图/实验驱动参数：全部从 Activity 的 Intent 里读，**不需要改 MainActivity 之外任何现有文件**。
 *
 * 例：
 *   adb shell am start -n dev.myco.android/.MainActivity --ez m5_glass true \
 *       --es glass_scene radius --eI glass_scroll 900
 */
data class GlassLabArgs(
    val scene: String = "overview",
    val scrollPx: Float = 760f,
    val blurDp: Float = 24f,
    val sat: Float = 1f,
    val noise: Float = 0f,
    val rim: Float = 0.35f,
    val layers: Int = 2,
    val autoScroll: Boolean = false,
    val autoDialog: Boolean = false,
) {
    companion object {
        fun from(intent: Intent?): GlassLabArgs {
            if (intent == null) return GlassLabArgs()
            return GlassLabArgs(
                scene = intent.getStringExtra("glass_scene") ?: "overview",
                scrollPx = intent.getFloatExtra("glass_scroll", 760f),
                blurDp = intent.getFloatExtra("glass_blur", 24f),
                sat = intent.getFloatExtra("glass_sat", 1f),
                noise = intent.getFloatExtra("glass_noise", 0f),
                rim = intent.getFloatExtra("glass_rim", 0.35f),
                layers = intent.getIntExtra("glass_layers", 2),
                autoScroll = intent.getBooleanExtra("glass_autoscroll", false),
                autoDialog = intent.getBooleanExtra("glass_dialog", false),
            )
        }
    }
}
