// 验收 ID：BP-40 BP-41 BP-42 BP-43 BP-44 BP-77 BP-79
//
// base.primitives.image-lightbox（§3.14）：全屏图片查看。
//  * 全屏黑底（不随主题反转）；关闭钮 44×44 右上，距 `statusBar+8`、右 16；舞台边距 16；
//  * 关闭集合：点遮罩（G-18，**不是**按下即关）、返回键、关闭钮；**下滑不关**（下滑被缩放/平移占用）；
//  * 双击 1×↔2×（居中到点按处，spring 0.86/420）；**缩放态禁翻页**（`userScrollEnabled=false`）；
//  * 无入场动画（DSH E0 未见声明）；加载失败占位由调用方给（本组件只给插槽）；
//  * 关闭后**回焦缩略图**（自写）；`paneTitle="图片查看"`，图库 `contentDescription="第 N / 共 M 张"`。
//
// 已知缺口（写进回复「待 Lead 裁决」）：双指缩放需要与 pager 做多指手势仲裁，P4 先做双击 1×/2×（BP-40 主项），
// 双指缩放留 TODO(lead)。缩放态禁翻页已按 BP-41 实现（`userScrollEnabled = !zoomed`）。
package dev.myco.android.ui.m5.primitives

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * 图片查看器。
 *
 * @param pageCount 同组图片数（≥1；图库横滑）
 * @param page 单页内容插槽（图片加载/失败占位由调用方决定）
 * @param returnFocus 关闭后回焦的缩略图
 */
@Composable
fun M5Lightbox(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    pageCount: Int,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    returnFocus: FocusRequester? = null,
    page: @Composable (index: Int) -> Unit,
) {
    if (!visible || pageCount <= 0) return
    val dismiss: () -> Unit = {
        onDismissRequest()
        if (returnFocus != null) runCatching { returnFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
    ) {
        LightboxWindow(
            pageCount = pageCount,
            initialPage = initialPage.coerceIn(0, pageCount - 1),
            onDismiss = dismiss,
            modifier = modifier,
            page = page,
        )
    }
}

@Composable
private fun LightboxWindow(
    pageCount: Int,
    initialPage: Int,
    onDismiss: () -> Unit,
    modifier: Modifier,
    page: @Composable (index: Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    // 双击缩放：1× ↔ 2×，居中到点按处（t = (p − center) × (1 − s)）
    val scale = androidx.compose.runtime.remember { Animatable(1f) }
    val translateX = androidx.compose.runtime.remember { Animatable(0f) }
    val translateY = androidx.compose.runtime.remember { Animatable(0f) }
    val zoomed = scale.value > 1.01f
    val topInset = LocalOverlayTopInset.current
    val bottomInset = LocalOverlayBottomInset.current
    val counterLabel = "第 ${pagerState.currentPage + 1} / 共 $pageCount 张"

    // 翻页时退出缩放态（缩放态本来禁翻页，这里是双保险）
    LaunchedEffect(pagerState.currentPage) {
        if (scale.value > 1.01f) {
            scale.snapTo(1f)
            translateX.snapTo(0f)
            translateY.snapTo(0f)
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // 遮罩层：G-18 严判据点关（图片本身不绑定点击关）
        Box(
            Modifier
                .fillMaxSize()
                .scrimTapToDismiss(onDismiss)
                .semantics { paneTitle = "图片查看" },
        )
        // 图片舞台：边距 16；缩放态禁翻页
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = translateX.value
                    translationY = translateY.value
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { position ->
                            val target = if (scale.value > 1.01f) 1f else 2f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val targetX = (position.x - center.x) * (1f - target)
                            val targetY = (position.y - center.y) * (1f - target)
                            scope.launch { scale.animateTo(target, M5Spring.settle) }
                            scope.launch { translateX.animateTo(targetX, M5Spring.settle) }
                            scope.launch { translateY.animateTo(targetY, M5Spring.settle) }
                        },
                    )
                },
            userScrollEnabled = !zoomed,
        ) { index ->
            page(index)
        }
        // 关闭钮：44×44，距 statusBar + 8、右 16
        M5OverlayCloseButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topInset + 8.dp, end = 16.dp),
            contentDescription = "关闭",
            tint = Color.White,
        )
        // 图库计数：底部安全区之上 16
        Text(
            text = counterLabel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp)
                .semantics { contentDescription = counterLabel },
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
