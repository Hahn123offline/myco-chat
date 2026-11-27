// 验收 ID：BP-28 BP-29 BP-30 BP-31 BP-32 BP-73 BP-77 BP-79
//
// base.primitives.bottom-sheet（§3.11）：轻选择 / 详情 / 附件入口（D-03 B 档）。
//  * 独立 window（`Dialog`）+ 焦点陷阱（E1，P4 验）+ **回焦自己写**；
//  * 关闭集合：点遮罩（G-18）、返回键、**下滑关（仅内滚在顶时）**、关闭钮、按钮；
//  * **下滑只在 `scrollAtTop()` 为 true 时触发**（G-13）：内滚不在顶时下滑归内滚、到边按 G-04 吸收；
//    阈值：竖向位移 > 96dp 或末速度 > 600dp/s；松手把末速度交给 spring（G-14）；
//  * 高 ≤ 0.8 × 窗口高；正文内滚、按钮区钉底；**内容高度变化不做动画**（G-10）；
//  * 底部只垫一次 `union(navBars, IME, cutout)`（`LocalOverlayBottomInset`，E-02=A 字段 7 / BP-30）；
//  * 宽屏最大 640 居中；把手 36×4 视觉 + 96×44 命中，`contentDescription="拖动关闭"`。
package dev.myco.android.ui.m5.primitives

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.myco.android.ui.m5.shell.ScrimToken
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 底部升起面板。
 *
 * @param scrollAtTop 内滚是否在顶（**下滑关的唯一前提**，G-13）；内滚不在顶时本组件不接管下滑
 * @param returnFocus 关闭后回焦的触发元素（`+`/模型胶囊等）
 */
@Composable
fun M5Sheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    returnFocus: FocusRequester? = null,
    scrollAtTop: () -> Boolean = { true },
    showHandle: Boolean = true,
    showCloseButton: Boolean = true,
    buttons: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val dismiss: () -> Unit = {
        onDismissRequest()
        if (returnFocus != null) runCatching { returnFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false, // G-18 自己判
            dismissOnBackPress = true,
        ),
    ) {
        SheetWindow(
            title = title,
            onDismiss = dismiss,
            scrollAtTop = scrollAtTop,
            showHandle = showHandle,
            showCloseButton = showCloseButton,
            modifier = modifier,
            buttons = buttons,
            content = content,
        )
    }
}

@Composable
private fun SheetWindow(
    title: String,
    onDismiss: () -> Unit,
    scrollAtTop: () -> Boolean,
    showHandle: Boolean,
    showCloseButton: Boolean,
    modifier: Modifier,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // 跟手位移（px，向下为正）：拖拽直接写 state，松手才用 spring 收敛
    var offsetY by remember { mutableFloatStateOf(0f) }
    val bottomInset = LocalOverlayBottomInset.current
    val reduced = reducedMotion()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val dismissVelocityPx = with(density) { 600.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 遮罩：alpha 0.32（G-18 点关）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimToken.ALPHA))
                .scrimTapToDismiss(onDismiss),
        )
        val sheetMaxWidth = minOf(640.dp, maxWidth)
        val sheetMaxHeight = maxHeight * 0.8f
        Column(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .overlaySurfaceHitTest()
                .graphicsLayer { translationY = offsetY }
                .background(colors.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .then(
                    Modifier.sheetDragToDismiss(
                        scrollAtTop = scrollAtTop,
                        thresholdPx = dismissThresholdPx,
                        velocityThresholdPx = dismissVelocityPx,
                        getOffset = { offsetY },
                        setOffset = { offsetY = it },
                        onDismiss = onDismiss,
                        scope = scope,
                        reducedMotion = reduced,
                    ),
                )
                // 底部只垫一次：union(navBars, IME, cutout) 真源（BP-30）
                .padding(bottom = bottomInset)
                .semantics { paneTitle = title },
        ) {
            if (showHandle) {
                M5SheetHandle()
            }
            // 标题行（高 56：标题 16/24 + 关闭钮 44）
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            // 正文：内滚（到边按 G-04 吸收，不把速度传给遮罩/下层）
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                content()
            }
            // 按钮区：钉底、48 高、不随内容滚
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
            }
        }
    }
}

/** 把手：视觉 36×4，命中 96×44；`contentDescription="拖动关闭"`（可在读屏里听出可拖）。 */
@Composable
private fun M5SheetHandle() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 96.dp, height = 44.dp)
                .semantics { contentDescription = "拖动关闭" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(999.dp)),
            )
        }
    }
}

/**
 * 下滑关手势（**仅在 `scrollAtTop()` 为 true 时接管**，G-13）：
 * 方向锁（竖向 > 横向 且超 slop）→ 若内滚不在顶则放弃（归内滚）→ 跟手 → 松手按 96dp / 600dp·s⁻¹ 判定。
 * 末速度交给 spring（G-14）；reduced-motion → 瞬时。
 */
private fun Modifier.sheetDragToDismiss(
    scrollAtTop: () -> Boolean,
    thresholdPx: Float,
    velocityThresholdPx: Float,
    getOffset: () -> Float,
    setOffset: (Float) -> Unit,
    onDismiss: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    reducedMotion: Boolean,
): Modifier = pointerInput(scrollAtTop, thresholdPx, velocityThresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed) return@awaitEachGesture
        if (!scrollAtTop()) return@awaitEachGesture // 内滚不在顶：下滑归内滚（G-13）
        val startY = down.position.y
        val startX = down.position.x
        var locked = false
        var dragging = false
        var lastDy = 0f
        var lastTime = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val dx = change.position.x - startX
            val dy = change.position.y - startY
            if (!locked && abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx) * 1.5f) locked = true
            if (locked) {
                // 内滚可能在本手势中途不再在顶（内容被拖动）：立即让回给内滚
                if (!scrollAtTop()) break
                if (change.isConsumed) break
                dragging = true
                lastDy = dy.coerceAtLeast(0f)
                lastTime = change.uptimeMillis
                setOffset(lastDy)
                if (change.pressed) change.consume()
            }
            if (!change.pressed) break
        }
        if (dragging) {
            val seconds = ((lastTime - down.uptimeMillis) / 1000f).coerceAtLeast(0.001f)
            val velocity = lastDy / seconds
            val commit = lastDy >= thresholdPx || velocity >= velocityThresholdPx
            if (commit) {
                onDismiss()
            } else if (reducedMotion) {
                setOffset(0f)
            } else {
                // 松手把末速度交给 spring（向上的初速度 → 回到 0）
                scope.launch {
                    val anim = Animatable(getOffset())
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = M5Spring.settle,
                        initialVelocity = -velocity,
                    ) { setOffset(value) }
                }
            }
        }
    }
}
