package dev.myco.android.ui.m5

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.primitives.M5Button
import dev.myco.android.ui.m5.primitives.M5ButtonVariant
import dev.myco.android.ui.m5.shell.BackDispatcher
import dev.myco.android.ui.m5.shell.CoverMode
import dev.myco.android.ui.m5.shell.Edge
import dev.myco.android.ui.m5.shell.LocalPanelInsets
import dev.myco.android.ui.m5.shell.PanelBackWiring
import dev.myco.android.ui.m5.shell.PanelHost
import dev.myco.android.ui.m5.shell.PanelInsetsProvider
import dev.myco.android.ui.m5.shell.PanelKind
import dev.myco.android.ui.m5.shell.PanelRoute
import dev.myco.android.ui.m5.shell.PanelStackState
import dev.myco.android.ui.m5.shell.SwipeOpenSource
import dev.myco.android.ui.m5.shell.SwipeSpec
import dev.myco.android.ui.m5.shell.WidthSpec
import dev.myco.android.ui.m5.shell.rememberPanelHostState
import dev.myco.android.ui.m5.shell.rememberPanelInsets

/**
 * B0 面板壳验证页（开发用，adb extra `m5_shell` 进入）。
 *
 * 覆盖验收：BL-04/05/06/07（push/pop/回根）、BL-10/12/13/14（拖拽开合几何）、
 * BL-16/20（抽屉遮罩 / 全屏无遮罩）、BL-22/23/25（返回链）、BL-40/41（insets 只垫一次）。
 *
 * 注意：`openDrawer` 的 lambda 会在 composition 期间被调用，**必须无副作用**（不能改 state）。
 */
@Composable
fun M5ShellDemo() {
    val insets = rememberPanelInsets()
    val root = remember {
        PanelRoute(routeId = "root-chat", panelId = "chat", kind = PanelKind.CHAT)
    }
    val drawerRoute = remember {
        PanelRoute(
            routeId = "sessions",
            panelId = "sessions",
            from = "root-chat",
            edge = Edge.LEFT,
            widthSpec = WidthSpec.MinMax(),
            coverMode = CoverMode.OVER_DIM,
            kind = PanelKind.SESSIONS,
            swipe = SwipeSpec(edge = Edge.LEFT, openSource = SwipeOpenSource.CONTENT_DRAG),
        )
    }
    val settingsRoute = remember {
        PanelRoute(
            routeId = "settings",
            panelId = "settings",
            from = "root-chat",
            edge = Edge.RIGHT,
            widthSpec = WidthSpec.Full,
            coverMode = CoverMode.OVER_OPAQUE,
            kind = PanelKind.SETTINGS,
        )
    }
    val state = rememberPanelHostState(initial = PanelStackState(routes = listOf(root)))
    val dispatcher = remember { BackDispatcher() }

    PanelInsetsProvider {
        Box(Modifier.fillMaxSize()) {
            PanelHost(
                state = state,
                openDrawer = { if (state.stack.isAtRoot) drawerRoute else null },
                panelTitle = { it.panelId },
            ) { route ->
                when (route.panelId) {
                    "chat" -> DemoChatRoot(
                        insetsText = insets.toString(),
                        stackDepth = state.stack.depth,
                        onOpenDrawer = { state.push(drawerRoute) },
                        onOpenSettings = { state.push(settingsRoute) },
                    )
                    "sessions" -> DemoDrawer(onClose = { state.pop() })
                    "settings" -> DemoSettings(onClose = { state.pop() })
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未知面板 ${route.panelId}")
                    }
                }
            }
            PanelBackWiring(state = state, dispatcher = dispatcher, imeVisible = false)
        }
    }
}

@Composable
private fun DemoChatRoot(
    insetsText: String,
    stackDepth: Int,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val insets = LocalPanelInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F0))
            .padding(horizontal = 20.dp)
            .padding(top = insets.top, bottom = insets.bottom),
    ) {
        Text("B0 面板壳验证 · 根层 chat", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "stack.depth = $stackDepth\ninsets = $insetsText",
            fontSize = 10.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            M5Button(text = "打开会话抽屉", onClick = onOpenDrawer)
            M5Button(text = "打开设置", onClick = onOpenSettings, variant = M5ButtonVariant.SECONDARY)
        }
        Text(
            "验证：内容区右滑 >96dp 开抽屉；左缘 20dp 内滑动交给系统；抽屉打开时按返回应立即关闭抽屉。",
            fontSize = 11.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items((1..40).toList()) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(if (i % 2 == 0) Color(0xFFFFFFFF) else Color(0xFFEFEFEA)),
                ) {
                    Text(
                        "列表项 $i —— 验证开合抽屉后滚动位置不变",
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoDrawer(onClose: () -> Unit) {
    val insets = LocalPanelInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .padding(16.dp)
            .padding(top = insets.top, bottom = insets.bottom),
    ) {
        Text("会话抽屉（PARTIAL + 0.32 遮罩）", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("宽度 = min(320dp, 0.86×屏宽, 屏宽−56dp)", fontSize = 10.sp, color = Color(0xFF888888))
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items((1..24).toList()) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onClose() },
                ) {
                    Text("会话 $i", fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterStart))
                }
            }
        }
        M5Button(text = "关闭抽屉", onClick = onClose, variant = M5ButtonVariant.SECONDARY)
    }
}

@Composable
private fun DemoSettings(onClose: () -> Unit) {
    val insets = LocalPanelInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAF7))
            .padding(20.dp)
            .padding(top = insets.top, bottom = insets.bottom),
    ) {
        Text("设置（FULL 全屏覆盖、无遮罩）", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "宽屏时内容区限宽 720dp 居中（D-29）。返回键应 pop 回 chat。",
            fontSize = 11.sp,
            color = Color(0xFF777777),
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        M5Button(text = "返回", onClick = onClose)
    }
}
