// 验收 ID：BP-16 BP-17 BP-18 BP-13 BP-14 BP-15 BP-73 BP-76
//
// base.primitives.icon-action-bar（§3.6）+ icon-action（§3.5）：
//  * **常驻**（D-19）：不做 hover 显隐、不做"点气泡唤出"；出现/消失**零布局位移**、无动画；
//  * 每键 **40×40**（G-16 密集图标档；布局盒 = 命中盒，路径 D），图标 20，净距 0（靠每键 10dp 内边距形成视觉间距）；
//  * 横向 Bleed 8（路径 C）：首键图标左缘与正文行首对齐；
//  * 键间布局盒相邻**不重叠**（中心距 40），与正文/行级可点区不相交；
//  * disabled 单键 38% + `disabled()` + 不触发；busy 用 spinner 替换图标；复制成功对勾 1000ms；
//  * 每键 `contentDescription` **必填**（BP-19：缺一个即缺陷）；
//  * "只轮尾最后一条"由调用方（chat）决定 —— 本原子只保证常驻形态（D-19）。
package dev.myco.android.ui.m5.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 单个图标动作的展示状态（受控，无内部状态）。 */
data class IconActionState(
    /** 动作 id（回调参数）。 */
    val id: String,
    /** 读屏必填的动作名（BP-19）。 */
    val contentDescription: String,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    /** 复制成功后的 1000ms 对勾态（由调用方计时置位/复位，原子不自己计时）。 */
    val done: Boolean = false,
    /** 能力缺失 → 不渲染（不是禁用）。 */
    val available: Boolean = true,
    /** 图标绘制（20dp 区域，由原子给 tint）。 */
    val icon: @Composable (tint: Color) -> Unit,
)

/**
 * 常驻图标动作条（§3.6）。
 *
 * @param actions 空列表 = 不渲染（无正文/非轮尾）
 * @param onAction 点击回调（幂等由调用方保证；busy 期间原子不回调）
 */
@Composable
fun IconActionBar(
    actions: List<IconActionState>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    bleed: Dp = 8.dp,
) {
    val visible = actions.filter { it.available }
    if (visible.isEmpty()) return // 无正文/非轮尾：不渲染（零布局）
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            // top 4（dsh-Go 操作条 Padding(top:4)）+ 横向 Bleed 8（路径 C）
            .padding(start = bleed, end = bleed, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        visible.forEach { action ->
            IconAction(action = action, tint = tint, onAction = onAction)
        }
    }
}

/** 单个图标动作键：40×40 布局盒 = 命中盒（路径 D），图标 20 居中（净距 0 由内边距给出）。 */
@Composable
fun IconAction(
    action: IconActionState,
    tint: Color,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactive = action.enabled && !action.busy
    Box(
        modifier = modifier
            .hitTargetDenseIcon(40.dp)
            .alpha(if (action.enabled) 1f else 0.38f)
            .clickable(
                enabled = interactive,
                role = Role.Button,
                onClickLabel = action.contentDescription,
                onClick = { onAction(action.id) },
            )
            // 每键 10dp 内边距 → 图标 20 + 20 = 40 布局盒（G-16"A 密集图标"视觉间距由内边距给出）
            .padding(10.dp)
            .semantics {
                role = Role.Button
                contentDescription = action.contentDescription
                when {
                    action.busy -> stateDescription = "处理中"
                    !action.enabled -> {
                        disabled()
                        stateDescription = "不可用"
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            action.busy -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = tint,
                strokeWidth = 2.dp,
            )
            action.done -> CheckGlyph(tint = tint, size = 20.dp)
            else -> action.icon(tint)
        }
    }
}

/** 复制成功的对勾（DSH 是瞬时替换、1000ms 后复原；不做 AnimatedSwitcher —— G-10）。 */
@Composable
fun CheckGlyph(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.76f)
            lineTo(w * 0.84f, h * 0.26f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
