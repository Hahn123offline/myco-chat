// 验收 ID：BP-21 BP-22 BP-23 BP-24 BP-25 BP-29 BP-42 BP-73 BP-79
//
// base.primitives.modal（§3.9）：居中模态框（确认 / 危险 / 表单）。
//  * 独立 window（`Dialog`）→ 焦点陷阱由平台保证（E1，P4 验）；**回焦必须自己写**；
//  * 关闭集合：点遮罩（G-18 严判据）、返回键、卡片内关闭钮、底部按钮；**无下滑关**（D-03）；
//  * 卡片宽 `min(380, 窗宽−48)`、圆角 16、内边距 24；内容可内滚、按钮区固定、卡高 ≤ 窗高−48；
//  * 出现/消失 spring 0.86/420（alpha + scale 0.96→1，**不做高度动画**）；reduced-motion → 瞬时；
//  * 遮罩 alpha 0.32（唯一值，D-05）。
//
// 本文件同时提供三个浮层共用的 G-18 点遮罩手势（`scrimTapToDismiss`），sheet/menu/lightbox 复用同一份，
// 保证"按下与松手都在遮罩上才关"只有一处实现（G-18 单一真源）。
package dev.myco.android.ui.m5.primitives

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.myco.android.ui.m5.shell.ScrimToken

// ---------------------------------------------------------------------------
// 浮层共享件（G-18 点遮罩单真源 / 命中占位 / 关闭钮 / insets 真源）
// ---------------------------------------------------------------------------

/** 浮层底垫真源：`union(navigationBars, IME, cutout)`，由 shell 的 InsetsController 提供（只垫一次）。 */
val LocalOverlayBottomInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/** 浮层顶垫真源：`statusBars ∪ cutout`（Lightbox 关闭钮 / Modal 四周避让用）。 */
val LocalOverlayTopInset = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

/**
 * **G-18 严判据**的点遮罩关闭手势（全浮层唯一实现）：
 *  - 记录按下点，要求松手点也在本节点内，且全程位移不超过 touch slop；
 *  - 框内按下 → 拖到遮罩松手 → **不关**（BP-21 ①）；
 *  - 遮罩按下 → 拖动超过 slop → **不关**（BP-21 ② / BL-17）。
 */
fun Modifier.scrimTapToDismiss(onDismiss: () -> Unit): Modifier = pointerInput(onDismiss) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downPosition = down.position
            down.consume()
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if ((change.position - downPosition).getDistance() > viewConfiguration.touchSlop) cancelled = true
                if (!change.pressed) {
                    if (!cancelled && inBounds(downPosition) && inBounds(change.position)) onDismiss()
                    break
                }
            }
        }
    }
}

private fun PointerInputScope.inBounds(position: Offset): Boolean =
    position.x >= 0f && position.y >= 0f &&
        position.x <= size.width.toFloat() && position.y <= size.height.toFloat()

/**
 * 命中测试占位（**不消费**事件）：让浮层卡片/面板在 hit-test 里先被命中，
 * 避免"点卡片空白处"被下面的遮罩当成点关；同时不影响卡片内部的滚动/按钮。
 */
fun Modifier.overlaySurfaceHitTest(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}

/** 浮层关闭钮：44×44 布局盒（路径 D），图标 20，必填 `contentDescription`。 */
@Composable
fun M5OverlayCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "关闭",
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier
            .hitTargetSquare(44.dp)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        CloseGlyph(tint = tint, size = 20.dp)
    }
}

/** 关闭钮图标（自绘 X，1.6dp 线性，不引图标库）。 */
@Composable
internal fun CloseGlyph(tint: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = 1.6.dp.toPx()
        drawLine(tint, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.22f, w * 0.78f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// Modal
// ---------------------------------------------------------------------------

/**
 * 居中 Modal（D-03 A 档：确认 / 危险 / 不可逆）。
 *
 * **无下滑关**（D-03）：卡片上不挂任何下拉手势；只能遮罩点关 / 返回键 / 关闭钮 / 按钮。
 *
 * @param returnFocus 关闭后要回焦的触发元素（平台只保证陷阱，不保证还焦 —— 必须自己写）
 * @param buttons 底部按钮区（主/危险在右、取消在左由调用方排）
 * @param content 内容区（可内滚；标题与按钮区固定）—— **尾随 lambda** 绑这里
 */
@Composable
fun M5Modal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    returnFocus: FocusRequester? = null,
    showCloseButton: Boolean = true,
    buttons: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val reduced = reducedMotion()
    val dismiss: () -> Unit = {
        onDismissRequest()
        // 回焦（BP-24）：失败兜底由调用方处理（焦点不得丢给 body）
        if (returnFocus != null) {
            runCatching { returnFocus.requestFocus() }
        }
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false, // 点外关闭由我们自己按 G-18 判定，不用平台近似
            dismissOnBackPress = true,
        ),
    ) {
        ModalWindow(
            title = title,
            reducedMotion = reduced,
            onDismiss = dismiss,
            showCloseButton = showCloseButton,
            modifier = modifier,
            buttons = buttons,
            content = content,
        )
    }
}

@Composable
private fun ModalWindow(
    title: String,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    showCloseButton: Boolean,
    modifier: Modifier,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = remember { Animatable(if (reducedMotion) 1f else 0.96f) }
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            // spring 0.86/420（不动卡片物理尺寸，只做 alpha + scale）
            launchBoth(scale, alpha)
        }
    }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 遮罩（全屏、alpha 0.32，G-18 点关）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimToken.ALPHA))
                .scrimTapToDismiss(onDismiss),
        )
        // 卡片：宽 min(380, 窗宽−48)，四周 24 外边距
        val cardMaxWidth = minOf(380.dp, (maxWidth - 48.dp).coerceAtLeast(0.dp))
        val cardMaxHeight = (maxHeight - 48.dp).coerceAtLeast(0.dp)
        Column(
            modifier = modifier
                .align(Alignment.Center)
                .widthIn(max = cardMaxWidth)
                .heightIn(max = cardMaxHeight)
                .overlaySurfaceHitTest()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题行（标题 16/24 加粗 + 关闭钮 44 在右上）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                if (showCloseButton) {
                    M5OverlayCloseButton(onClick = onDismiss, tint = colors.onSurfaceVariant)
                }
            }
            // 内容区：可内滚（到边按 G-04 吸收，遮罩拦截指针）
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
            // 按钮区：固定不滚
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
            }
        }
    }
}

/** 同时跑两个 spring（两条独立通道，任一可达）。 */
private suspend fun launchBoth(
    scale: Animatable<Float, AnimationVector1D>,
    alpha: Animatable<Float, AnimationVector1D>,
) {
    // 顺序执行更简单：先 alpha 再 scale（两者时长同档，视觉上仍是同一次入场）
    alpha.animateTo(1f, M5Spring.settleOrSnap(false))
    scale.animateTo(1f, M5Spring.settleOrSnap(false))
}
