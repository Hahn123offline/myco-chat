// 验收 ID：BL-16 BL-17 BL-18 BL-19 BL-20 BL-21 BL-42 BL-47 BL-50
//
// base.layout.z-ladder + `route.scrim` 的层叠常量与分层渲染件。
// 规则（§1.6）：
//  * 栈内 route **不写 z 数字**：按列表顺序渲染（后入者在上）；栈深增长不新增常量；
//  * 跨家族只有 0 / 10 / 20 / 40 / 1000 / 1100，保留槽 11 / 15 / 60 / 70；
//  * 遮罩不占独立 z 家族：属于栈顶 route 内部，画在"下层 → 遮罩 → 该层内容"之间，全 app 最多 1 个；
//  * 抬升只允许发生在浮层家族内，导航栈禁止抬升。
//
// 颜色归 base.theme —— 本文件只定 alpha=0.32 与几何。TODO(lead)：`PanelScrim` 的底色改用 base.theme 的 scrim token。
package dev.myco.android.ui.m5.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import dev.myco.android.ui.m5.primitives.scrimTapToDismiss

/**
 * z 阶梯常量（§1.6 唯一取值；只读）。
 * 栈内 route 不取这里的数字 —— 用 `NAV_STACK` 作为整条导航栈的家族号即可。
 */
object PanelZ {
    /** 0：根 route（chat）。 */
    const val ROOT_ROUTE: Float = 0f

    /** 10：宽屏常驻列（侧栏 / 右栏）；参与布局流、不进栈。 */
    const val DOCKED_COLUMN: Float = 10f

    /** 20：框架浮层槽 `shell.overlay`（穿透；Toast / FAB / 菜单锚点）。 */
    const val OVERLAY_SLOT: Float = 20f

    /** 40：导航栈 route 家族（栈内仍按顺序，不递增）。 */
    const val NAV_STACK: Float = 40f

    /** 1000：modal / lightbox / bottom sheet（三者互斥，同家族内按打开序）。 */
    const val FLOATING: Float = 1000f

    /** 1100：portal menu（菜单高于 modal）。 */
    const val PORTAL_MENU: Float = 1100f

    /** 保留槽（新增家族只能用这几个号，避免以后全局改号）。 */
    const val RESERVED_11: Float = 11f
    const val RESERVED_15: Float = 15f
    const val RESERVED_60: Float = 60f
    const val RESERVED_70: Float = 70f

    /**
     * 浮层家族在外部 window 里的抬升顺序（同一家族内按打开序）。
     * Toast 不在 1000 家族：它在 overlay 槽（20），低于面板栈（40）——BL-50。
     */
    val floatingFamilies: List<Float> = listOf(FLOATING, PORTAL_MENU)
}

/** 遮罩 token：全 app **唯一值** 0.32（D-05；`RuntimeAdaptiveNavigationScaffold.kt:169-172` E0）。 */
object ScrimToken {
    const val ALPHA: Float = 0.32f

    /** alpha = 0.32 × progress，线性、无独立 tween（§1.7）。 */
    fun alphaFor(progress: Float): Float = (ALPHA * progress.coerceIn(0f, 1f)).coerceIn(0f, ALPHA)
}

/** 层叠家族的画法（PanelHost 依此决定"谁在上面"）。 */
enum class PanelZFamily(val z: Float) {
    ROOT(PanelZ.ROOT_ROUTE),
    DOCKED_COLUMN(PanelZ.DOCKED_COLUMN),
    OVERLAY_SLOT(PanelZ.OVERLAY_SLOT),
    NAV_STACK(PanelZ.NAV_STACK),
    FLOATING(PanelZ.FLOATING),
    PORTAL_MENU(PanelZ.PORTAL_MENU),
}

/** 给一个 z 家族的使用位（同家族内不要自造新数值）。 */
fun Modifier.panelZ(family: PanelZFamily): Modifier = this.zIndex(family.z)

/**
 * 遮罩层（`base.layout.route.scrim`）。
 *
 * - 全屏 `inset:0`（含系统栏/挖孔区），不随抽屉宽度缩（§1.7 几何）；
 * - `alpha` 由 progress 线性驱动（BL-19）；不存在独立 tween；
 * - 关闭判据 = **按下与松手都在遮罩上**（G-18 / BL-16 / BL-17）：本函数用自绘手势，
 *   不用 `clickable`（`clickable` 会在滑出边界时静默取消，但不会在"框内起手拖到遮罩松手"时
 *   按 G-18 明确判定；自绘才能记录 down 位置）；
 * - `dismissEnabled=false`（拖拽中 progress<1）时吞指针但**不**响应点关（§1.7 可点时机）；
 * - 遮罩自身可被读屏当作"关闭"按钮（`role=Button` + `onClickLabel="关闭"`），不抢焦点、不做 liveRegion。
 *
 * BL-20：全屏不透明面板**不创建**本节点（由 PanelHost 只对抽屉型调用）。
 */
@Composable
fun PanelScrim(
    alpha: Float,
    dismissEnabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0f) return
    // TODO(lead)：底色应为 base.theme 的 scrim token；这里先用纯黑，alpha 已符合 0.32 契约。
    val color = Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))
    Box(
        modifier
            .fillMaxSize()
            .background(color)
            .then(
                if (dismissEnabled) {
                    Modifier
                        .scrimTapToDismiss(onDismiss)
                        .semantics {
                            role = Role.Button
                            onClick(label = "关闭") {
                                onDismiss()
                                true
                            }
                        }
                } else {
                    // 拖拽中：吞掉所有指针，避免"边拖边点到下层"（BL-18）
                    Modifier.blockPointerInput()
                }
            ),
    )
}

/**
 * G-18 严判据点关手势的**唯一实现**在 `base.primitives`（`M5Modal.kt` 的 `scrimTapToDismiss`），
 * 单测/审查只看一份：框内起手拖到遮罩松手不关、拖动超 slop 不关、按下+松手都在遮罩才关。
 * （原本在本文件也有一份副本，已删掉以免两份判定漂移。）
 */

/**
 * 吞掉全部指针（被覆盖层隔离，BL-42）：在 **Initial pass** 消费，
 * 保证下层子树在 Main pass 之前就收不到事件；不改变任何布局尺寸。
 */
fun Modifier.blockPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { change -> change.consume() }
        }
    }
}

/**
 * 框架浮层槽（`slots.overlay`, z=20，穿透，BL-50）：
 * 槽本身不消费指针；空的槽区域点击穿到下层。子元素自己接命中。
 */
@Composable
fun PanelOverlaySlot(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().panelZ(PanelZFamily.OVERLAY_SLOT)) {
        content()
    }
}

/** 浮层家族层（modal / lightbox / bottom sheet，z=1000；三者互斥，见 `M5Modal`/`M5Sheet`/`M5Lightbox`）。 */
@Composable
fun PanelFloatingLayer(
    modifier: Modifier = Modifier,
    family: PanelZFamily = PanelZFamily.FLOATING,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().panelZ(family)) {
        content()
    }
}

/** 常驻列槽（z=10，参与布局流；不进栈、无遮罩、无开合手势）。 */
@Composable
fun PanelDockedColumnSlot(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.panelZ(PanelZFamily.DOCKED_COLUMN)) {
        content()
    }
}

