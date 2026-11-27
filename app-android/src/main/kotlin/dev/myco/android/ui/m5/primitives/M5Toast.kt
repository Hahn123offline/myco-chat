// 验收 ID：BP-36 BP-37 BP-38 BP-39 BP-77
//
// base.primitives.toast（§3.13）：底部一次性轻提示（可带 1 个动作）。
//  * **同一时刻最多 1 条**（新的替换旧的）；
//  * 宽 `min(窗宽−32, 480)`、圆角 16、底 `inverseSurface`、高 ≥48、内边距 16；单行 14/20（2 行截断）；
//  * **横幅不拦指针**（G-01 / DSH `pointer-events:none`）：只有动作按钮可点（命中 ≥44 高）；
//  * 自动过期：无动作 **3000ms** / 有动作 **6000ms**；动作点击后立即消失；返回键**不消费**（toast 非模态）；
//  * `liveRegion = Assertive`：出现即播报全文一次；
//  * 进 `translationY 12dp + alpha` spring 0.86/420；**不做高度动画**；reduced-motion → 瞬时。
//    （退出动画需要"保持组合到动画结束"，由宿主按 G-10 决定；本组件按移除即消失处理 —— TODO(lead)）
//
// 宿主位置归 `base.layout` 的 overlay 槽（z=20、低于面板栈 40）；本组件只按自己锚点垫底部 inset。
package dev.myco.android.ui.m5.primitives

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 一条 toast 的数据（`id` 用于替换/计时去重）。 */
@Immutable
data class M5ToastData(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = if (actionLabel != null) 6000L else 3000L,
)

/** toast 宿主状态：同一时刻只有一条（新的替换旧的）。 */
@Stable
class M5ToastState {
    var current: M5ToastData? by mutableStateOf(null)
        private set

    private var nextId = 1L

    /** 显示一条 toast；替换当前那条（旧的立即消失）。 */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ): M5ToastData {
        val data = M5ToastData(
            id = nextId++,
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
        )
        current = data
        return data
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberM5ToastState(): M5ToastState = remember { M5ToastState() }

/**
 * toast 宿主：放在 overlay 槽（z=20，穿透）里渲染。
 * 空区域点击穿透到下层（横幅本身没有 pointerInput/clickable）。
 */
@Composable
fun M5ToastHost(
    state: M5ToastState,
    modifier: Modifier = Modifier,
) {
    val data = state.current ?: return
    val bottomInset = LocalOverlayBottomInset.current
    val reduced = reducedMotion()
    val colors = MaterialTheme.colorScheme
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    val offsetDp = remember { Animatable(if (reduced) 0f else 12f) }

    // 出现动画：translationY 12dp→0 + alpha 0→1（spring 0.86/420，不做高度动画）
    LaunchedEffect(data.id) {
        if (reduced) {
            alpha.snapTo(1f)
            offsetDp.snapTo(0f)
        } else {
            alpha.snapTo(0f)
            offsetDp.snapTo(12f)
            coroutineScope {
                launch { alpha.animateTo(1f, M5Spring.settle) }
                launch { offsetDp.animateTo(0f, M5Spring.settle) }
            }
        }
    }
    // 自动过期（无动作 3000 / 有动作 6000）
    LaunchedEffect(data.id) {
        delay(data.durationMs)
        if (state.current?.id == data.id) state.dismiss()
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomInset + 16.dp),
        ) {
            val maxBannerWidth = minOf(480.dp, (maxWidth - 32.dp).coerceAtLeast(0.dp))
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = maxBannerWidth)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = offsetDp.value * density
                    }
                    .background(colors.inverseSurface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 48.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Assertive
                        contentDescription = data.message
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.message,
                    modifier = Modifier.weight(1f),
                    color = colors.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.actionLabel != null) {
                    // 动作命中 ≥44 高（路径 D，BP-37）；横幅其它区域不拦指针
                    Text(
                        text = data.actionLabel,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .padding(start = 16.dp)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = data.actionLabel,
                                onClick = {
                                    data.onAction?.invoke()
                                    state.dismiss()
                                },
                            ),
                        color = colors.inversePrimary,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
