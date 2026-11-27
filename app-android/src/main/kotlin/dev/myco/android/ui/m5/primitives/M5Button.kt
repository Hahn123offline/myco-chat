// 验收 ID：BP-09 BP-10 BP-71 BP-73 BP-76
//
// base.primitives.button（§3.3）：主 / 次 / 危险 / 工具栏四变体。
//  * 高 48（G-16 输入/主操作档）、最小宽 64、水平内边距 20、圆角 16；
//  * 命中盒 = 布局盒（路径 D，`heightIn` 真的抬布局尺寸，不用 offset 扩边）；
//  * 单行 ellipsis（大字号允许换行 → 高增）；loading = 转圈替换文字并禁用；
//  * disabled 40% 不透明 + `disabled()` 语义 + `stateDescription` 原因（**不用**原生 disabled 隐藏节点）；
//  * 无 hover / 无 focus 视觉（D-09 触摸-only）；无尺寸动画（G-10）。
//
// 颜色/字号 token 归 base.theme：这里读 MaterialTheme（Lead 的 base.theme 落地后由它统一提供）。TODO(lead)
package dev.myco.android.ui.m5.primitives

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 按钮变体（§3.3 字段 6）。危险动作必须与次要动作明确不同色，防不可逆误点。 */
enum class M5ButtonVariant {
    /** 主操作：primary 底 + onPrimary 文字。 */
    PRIMARY,

    /** 次要操作：outline 边框。 */
    SECONDARY,

    /** 危险操作（删除/不可逆）：error 底 + onError 文字。 */
    DANGER,

    /** 工具栏文字键：无底色、无边框。 */
    TOOLBAR,
}

/**
 * 文字按钮。
 *
 * @param enabled 业务可用性（不传 loading 时会显示 40% + `disabled()` 语义）
 * @param loading 提交中：转圈替换文字、禁用、`stateDescription="处理中"`
 * @param disabledReason 禁用原因（读屏可听出，§3.3 字段 11）
 */
@Composable
fun M5Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: M5ButtonVariant = M5ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    minWidth: Dp = 64.dp,
    disabledReason: String = "不可用",
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactive = enabled && !loading
    val background = when (variant) {
        M5ButtonVariant.PRIMARY -> colors.primary
        M5ButtonVariant.SECONDARY -> Color.Transparent
        M5ButtonVariant.DANGER -> colors.error
        M5ButtonVariant.TOOLBAR -> Color.Transparent
    }
    val foreground = when (variant) {
        M5ButtonVariant.PRIMARY -> colors.onPrimary
        M5ButtonVariant.SECONDARY -> colors.primary
        M5ButtonVariant.DANGER -> colors.onError
        M5ButtonVariant.TOOLBAR -> colors.primary
    }
    val shape = RoundedCornerShape(16.dp)
    val border = if (variant == M5ButtonVariant.SECONDARY) BorderStroke(1.dp, colors.outline) else null

    Box(
        modifier = modifier
            // disabled：40% 不透明（§3.3 字段 6）
            .alpha(if (interactive) 1f else 0.4f)
            // 路径 D：命中盒 = 布局盒（48 高 / ≥64 宽），不用 offset 扩边
            .hitTargetMinSize(minWidth = minWidth, minHeight = 48.dp)
            .clip(shape)
            .background(background, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                enabled = interactive,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                if (loading) {
                    stateDescription = "处理中"
                } else if (!enabled) {
                    // 不用原生 disabled 隐藏节点：保留节点 + 可听出原因（P2 §2.2 末行）
                    disabled()
                    stateDescription = disabledReason
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = foreground,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.invoke()
                Text(
                    text = text,
                    color = foreground,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 底部表单保存按钮（`form-save` 的按钮部分）：48 高、转圈不改高度、disabled 三态原因。
 * 完整原子（钉底条 + 分隔线）归 `settings` 面板装配，这里只提供按钮形态。
 */
@Composable
fun M5FormSaveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dirty: Boolean = true,
    invalid: Boolean = false,
    saving: Boolean = false,
    onSavingChange: (Boolean) -> Unit = {},
    variant: M5ButtonVariant = M5ButtonVariant.PRIMARY,
) {
    val enabled = dirty && !invalid && !saving
    val reason = when {
        saving -> "保存中"
        invalid -> "有错误"
        !dirty -> "未修改"
        else -> "不可用"
    }
    M5Button(
        text = text,
        onClick = {
            if (!enabled) return@M5Button
            onSavingChange(true)
            onClick()
        },
        modifier = modifier.widthIn(min = 64.dp),
        variant = variant,
        enabled = enabled,
        loading = saving,
        disabledReason = reason,
    )
}
