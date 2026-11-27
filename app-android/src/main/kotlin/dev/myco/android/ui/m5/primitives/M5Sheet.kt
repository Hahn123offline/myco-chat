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
//
// 玻璃形态（P4 玻璃材质，G-19 路线 B / D-74 / D-75）：
//  * ⚠️ 本文件的 **M5Sheet 是 `Dialog`（独立 window）→ 结构上捕获不到主窗口背景**（G-21）。
//    因此 M5Sheet **保持 A 不透明 neumorphism**（`colors.surface`），不做"半透明 + blur"伪造。
//  * 玻璃形态用**同一 Compose 树内**的 [M5GlassSheet] / [M5GlassDrawer]（本文件末尾）：它们必须在
//    `GlassBackdrop { ... } overlay = { ... }` 的 overlay 槽里调用，才能取到背景捕获层；
//    API < 33 / 软件渲染 → 自动落回 A 不透明（D-74），不是半透明。
package dev.myco.android.ui.m5.primitives

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

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
internal fun Modifier.sheetDragToDismiss(
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

// ---------------------------------------------------------------------------
// 玻璃形态（同一 Compose 树内；API 33+ 真玻璃，否则 A 不透明）
//
// 与上面 M5Sheet/M5Modal/M5Lightbox 的区别（必须理解，否则玻璃画不出背景）：
//  * 上面的三个原语是 `Dialog`（独立 window）→ 捕获层拿不到主窗口内容 → 按 G-21 保持 A 不透明；
//  * 这里的两个原语必须被放在 `GlassBackdrop { 背景 } overlay = { ... }` 的 overlay 槽里，
//    与背景共享同一个 Compose 树/同一个 window → `GlassSurface` 才能从 `LocalGlassBackdrop` 取到源层。
//  * 关闭集合与 M5Sheet 对齐：点遮罩（G-18）、返回键、下滑（仅内滚在顶时）、关闭钮；出现用 spring（G-14）。
// ---------------------------------------------------------------------------

/**
 * 玻璃底部 sheet（in-window 版 M5Sheet）。
 *
 * 用法（Lead 接线）：
 * ```
 * GlassBackdrop(Modifier.fillMaxSize()) {
 *     ChatContent()
 * } overlay = {
 *     M5GlassSheet(visible = sheetVisible, onDismissRequest = { sheetVisible = false }, title = "…") { … }
 * }
 * ```
 *
 * API < 33 / 软件渲染 / 着色器失败 → `GlassSurface` 自动画 `fallbackColor`（不透明 surface，A 路线）；
 * **不会**退化成"半透明 + blur"（G-19/D-74）。
 */
@Composable
fun M5GlassSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    material: GlassMaterial = GlassMaterial.Overlay,
    lightDir: Offset = DEFAULT_LIGHT_DIR,
    debugPureBlur: Boolean = false,
    debugNoScrim: Boolean = false,
    returnFocus: FocusRequester? = null,
    scrollAtTop: () -> Boolean = { true },
    showHandle: Boolean = true,
    showCloseButton: Boolean = true,
    buttons: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion()
    val bottomInset = LocalOverlayBottomInset.current
    val density = LocalDensity.current
    // 跟手位移（px，向下为正）：拖拽直接写 state，松手才用 spring 收敛
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val dismissVelocityPx = with(density) { 600.dp.toPx() }
    val dismiss: () -> Unit = {
        onDismissRequest()
        if (returnFocus != null) runCatching { returnFocus.requestFocus() }
    }
    BackHandler { dismiss() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMaxWidth = minOf(640.dp, maxWidth)
        val sheetMaxHeight = maxHeight * 0.8f
        val enterOffsetPx = with(density) { maxHeight.toPx() }
        // 出现：spring 从屏底滑入（G-14 / G-10 允许位移动画；不做高度动画）
        val enter = remember { Animatable(if (reduced) 0f else 1f) }
        LaunchedEffect(Unit) { if (!reduced) enter.animateTo(0f, M5Spring.settle) }
        val translateY = offsetY + enter.value * enterOffsetPx
        // 遮罩：alpha 0.32（G-18 点关；随入场淡入）
        val scrimAlpha = if (debugNoScrim) 0f else ScrimToken.ALPHA * (1f - enter.value)
        if (scrimAlpha > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .scrimTapToDismiss(dismiss),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                // 用 offset（placement）而不是 graphicsLayer：onGloballyPositioned 才会随位移刷新，
                // 玻璃的捕获区域才能跟手（graphicsLayer 只改绘制不改布局，会买到一帧的错位）。
                .offset { IntOffset(0, translateY.roundToInt()) }
                .overlaySurfaceHitTest()
                .then(
                    Modifier.sheetDragToDismiss(
                        scrollAtTop = scrollAtTop,
                        thresholdPx = dismissThresholdPx,
                        velocityThresholdPx = dismissVelocityPx,
                        getOffset = { offsetY },
                        setOffset = { offsetY = it },
                        onDismiss = dismiss,
                        scope = scope,
                        reducedMotion = reduced,
                    ),
                ),
        ) {
            GlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight),
                material = material,
                corners = GlassCorners.sheet(material.corner),
                fallbackColor = colors.surface,
                // 遮罩画在玻璃下面 → 它属于 backdrop（不传会显得比周围亮一截）
                backdropScrim = Color.Black.copy(alpha = scrimAlpha),
                lightDir = lightDir,
                debugPureBlur = debugPureBlur,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
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
                            M5OverlayCloseButton(onClick = dismiss, tint = colors.onSurfaceVariant)
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
    }
}

/**
 * 玻璃左侧抽屉（in-window）：也是 `GlassBackdrop` overlay 槽里的浮层，左边缘与屏幕齐平。
 *
 * 形状：**只有右侧两个角圆**（`GlassCorners.drawer`）；左侧与屏幕边缘齐平，不做假圆角。
 * 宽度默认 84%（与 M5 现有抽屉一致），内容由调用方给（会话列表等）。
 */
@Composable
fun M5GlassDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    material: GlassMaterial = GlassMaterial.Overlay,
    lightDir: Offset = DEFAULT_LIGHT_DIR,
    debugPureBlur: Boolean = false,
    debugNoScrim: Boolean = false,
    widthFraction: Float = 0.84f,
    drawerTitle: String = "会话",
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val colors = MaterialTheme.colorScheme
    val reduced = reducedMotion()
    val bottomInset = LocalOverlayBottomInset.current
    val topInset = LocalOverlayTopInset.current
    val density = LocalDensity.current
    BackHandler { onDismissRequest() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val drawerWidth = maxWidth * widthFraction.coerceIn(0.5f, 0.92f)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val enter = remember { Animatable(if (reduced) 0f else 1f) }
        LaunchedEffect(Unit) { if (!reduced) enter.animateTo(0f, M5Spring.settle) }
        val translateX = -drawerWidthPx * enter.value
        val scrimAlpha = if (debugNoScrim) 0f else ScrimToken.ALPHA * (1f - enter.value)
        if (scrimAlpha > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .scrimTapToDismiss(onDismissRequest),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(drawerWidth)
                .fillMaxHeight()
                // placement 位移（不是 graphicsLayer）：玻璃捕获区域随抽屉跟手刷新
                .offset { IntOffset(translateX.roundToInt(), 0) }
                .overlaySurfaceHitTest(),
        ) {
            GlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                material = material,
                corners = GlassCorners.drawer(material.corner),
                fallbackColor = colors.surface,
                backdropScrim = Color.Black.copy(alpha = scrimAlpha),
                lightDir = lightDir,
                debugPureBlur = debugPureBlur,
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(top = topInset, bottom = bottomInset)
                        .semantics { paneTitle = drawerTitle },
                ) {
                    content()
                }
            }
        }
    }
}

