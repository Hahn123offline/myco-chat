package dev.myco.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 可复用的"预测式退出"进度源（0..1）。
 *
 * 三个入口写同一个 progress，页面只按 progress 做动画：
 *  - 系统返回手势（PredictiveBackHandler 的 progress）；
 *  - 页面向右拖拽（[swipeRightToExit]）；
 *  - 点击返回按钮/常规退出（[exit] 从当前进度补到 1 再切换页面，保证和手势同一套运动）。
 *
 * M5 可以在同一个 progress 上叠加更多动效（缩放、模糊、阴影、共享元素等）。
 */
@Stable
class PredictiveNavState {
    var progress by mutableFloatStateOf(0f)
        private set

    /** 退出/滑回动画进行中：此时忽略拖拽写入，避免动画与拖拽互相拉扯。 */
    var animating by androidx.compose.runtime.mutableStateOf(false)
        private set

    /** 手势进度（0..1），由 PredictiveBackHandler 或拖拽调用。 */
    fun onGestureProgress(value: Float) {
        if (animating) return
        progress = value.coerceIn(0f, 1f)
    }

    fun reset() {
        progress = 0f
    }

    /** 系统返回手势是否正在驱动本状态（用于避免页面拖拽和系统手势同时写入导致抖动）。 */
    var systemDriven by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun beginSystemGesture() {
        systemDriven = true
    }

    fun endSystemGesture() {
        systemDriven = false
    }

    /** 入场：先把进度放到 1（页面停在屏幕右侧），随后 [animateEnter] 滑入。 */
    fun prepareEnter() {
        progress = 1f
    }

    suspend fun animateEnter(durationMs: Int = 220) {
        animating = true
        try {
            if (progress > 0f) {
                val anim = Animatable(progress)
                anim.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) { progress = value }
            }
        } finally {
            reset()
            animating = false
        }
    }

    /** 常规退出（点返回键/按钮）：先补齐到 1，再执行页面切换。 */
    suspend fun exit(durationMs: Int = 180, onCommit: suspend () -> Unit) {
        animating = true
        try {
            if (progress < 1f) {
                val anim = Animatable(progress)
                anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) { progress = value }
            }
            onCommit()
        } finally {
            reset()
            animating = false
        }
    }

    /** 拖拽未过阈值：滑回原位。 */
    suspend fun cancel(durationMs: Int = 160) {
        animating = true
        try {
            if (progress > 0f) {
                val anim = Animatable(progress)
                anim.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) { progress = value }
            }
        } finally {
            reset()
            animating = false
        }
    }
}

@Composable
fun rememberPredictiveNavState(): PredictiveNavState = remember { PredictiveNavState() }

/**
 * 页面"向右拖拽退出"：横向位移明显占优后才接管（消费事件，避免误触竖向滚动）；
 * 跟随手指写 [state].progress；松手超过 30% 或速度超过 1000px/s 则完成退出，否则滑回。
 *
 * [onCommit] 只负责页面切换（真正的"补齐到 1 再切"由 [PredictiveNavState.exit] 负责）。
 */
fun Modifier.swipeRightToExit(
    state: PredictiveNavState,
    enabled: Boolean,
    onCommit: () -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val scope = rememberCoroutineScope()
    val commit = rememberUpdatedState(onCommit)
    val touchSlop = with(LocalDensity.current) { 20.dp.toPx() }
    this.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 左缘交给系统返回手势：页面拖拽不接管，避免两套进度互相打架（抖动）
            if (down.position.x <= touchSlop) return@awaitEachGesture
            if (state.systemDriven) return@awaitEachGesture
            val startX = down.position.x
            val startY = down.position.y
            val startTime = down.uptimeMillis
            var lastX = startX
            var lastTime = startTime
            var horizontal = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - startX
                val dy = change.position.y - startY
                if (!horizontal && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    horizontal = true
                }
                if (horizontal) {
                    // 系统返回手势接管、或退出/滑回动画进行中：本手势停止写入，避免两套进度互相拉扯
                    if (state.systemDriven || state.animating) {
                        horizontal = false
                        break
                    }
                    lastX = change.position.x
                    lastTime = change.uptimeMillis
                    val next = dx / size.width.toFloat()
                    // 死区：手指轻微抖动不推页面，避免反复微调造成“抽搐”
                    if (abs(next - state.progress) > 0.0015f) state.onGestureProgress(next)
                    if (change.pressed) change.consume()
                }
                // 抬手事件也要先取到最终位移（快速甩动可能只有 down + up 两个事件）
                if (!change.pressed) break
            }
            if (horizontal && !state.systemDriven && !state.animating) {
                val seconds = ((lastTime - startTime) / 1000f).coerceAtLeast(0.001f)
                val velocity = (lastX - startX) / seconds
                if (state.progress > 0.22f || velocity > 600f) {
                    scope.launch { state.exit { commit.value() } }
                } else {
                    scope.launch { state.cancel() }
                }
            }
        }
    }
}
