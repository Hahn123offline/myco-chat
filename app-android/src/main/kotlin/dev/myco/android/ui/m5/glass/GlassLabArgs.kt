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
    val scrollPx: Float = 420f,
    val blurDp: Float = 24f,
    val sat: Float = 1f,
    val noise: Float = 0f,
    val rim: Float = 0.35f,
    val layers: Int = 2,
    val autoScroll: Boolean = false,
    val autoDialog: Boolean = false,
    val fixedRadius: Boolean = false,
    val renderScale: Float = 1f,
    val throttleFrames: Int = 1,
    val targetPreset: String = "target",
    val showUi: Boolean = true,
    val area: String = "both",
    val capture: Boolean = false,
    val liquid: Boolean = false,
    val refract: Float = 3f,
    val band: Float = 6f,
    val disp: Float = 1f,
    val tintAlpha: Float = 0.04f,
    val noshader: Boolean = false,
) {
    companion object {
        fun from(intent: Intent?): GlassLabArgs {
            if (intent == null) return GlassLabArgs()
            return GlassLabArgs(
                scene = intent.getStringExtra("glass_scene") ?: "overview",
                scrollPx = intent.getFloatExtra("glass_scroll", 420f),
                blurDp = intent.getFloatExtra("glass_blur", 24f),
                sat = intent.getFloatExtra("glass_sat", 1f),
                noise = intent.getFloatExtra("glass_noise", 0f),
                rim = intent.getFloatExtra("glass_rim", 0.35f),
                layers = intent.getIntExtra("glass_layers", 2),
                autoScroll = intent.getBooleanExtra("glass_autoscroll", false),
                autoDialog = intent.getBooleanExtra("glass_dialog", false),
                fixedRadius = intent.getBooleanExtra("glass_fixed", false),
                renderScale = intent.getFloatExtra("glass_render_scale", 1f),
                throttleFrames = intent.getIntExtra("glass_throttle", 1),
                targetPreset = intent.getStringExtra("glass_target") ?: "target",
                showUi = intent.getBooleanExtra("glass_ui", true),
                area = intent.getStringExtra("glass_area") ?: "both",
                capture = intent.getBooleanExtra("glass_capture", false),
                liquid = intent.getBooleanExtra("glass_liquid", false),
                refract = intent.getFloatExtra("glass_refract", 3f),
                band = intent.getFloatExtra("glass_band", 6f),
                disp = intent.getFloatExtra("glass_disp", 1f),
                tintAlpha = intent.getFloatExtra("glass_tint", 0.04f),
                noshader = intent.getBooleanExtra("glass_noshader", false),
            )
        }
    }
}
