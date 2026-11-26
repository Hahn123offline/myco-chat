package dev.myco.android.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * 动效 token（来自 docs/recon/frontend-design-tech.md）：
 * 同一屏可见的动效类别 ≤3；禁止弹跳、禁止逐字流入动画（流式只闪光标）。
 */
object MycoMotion {
    val press = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    val enter = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val gesture = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    val exit = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val quick = tween<Float>(durationMillis = 120)
    const val PRESS_SCALE = 0.98f
}

/**
 * 统一按压反馈：轻微缩放（iOS/DeepSeek 类似），与 Material ripple 叠加使用。
 * 用法：`Modifier.pressable(interactionSource)`，并把它与 clickable 共用同一个 interactionSource。
 */
@Composable
fun Modifier.pressable(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MycoMotion.PRESS_SCALE else 1f,
        animationSpec = MycoMotion.press,
        label = "press-scale",
    )
    return this.scale(scale)
}
