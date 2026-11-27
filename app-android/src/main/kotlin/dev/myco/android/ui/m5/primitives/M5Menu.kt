// 验收 ID：BP-32 BP-33 BP-34 BP-35 BP-79
//
// base.primitives.menu（§3.12）：菜单 = **手机形态的底部 sheet 单层列表**（P2 §2、C-BP-03）。
//  * **复用 bottom-sheet 容器**（把手 / 标题 / insets / 下滑关语义），所以 BP-32 的"源码断言无 popup 路径"成立：
//    本文件不实现锚定 popup、不实现子菜单（P2 §4-2）；
//  * 行高 44（路径 A：整行可点，布局盒 = 命中盒）、水平内边距 20、图标 20 + gap 12 + 文字 14/20；
//  * 危险项 error 色；选中项右侧 20 对勾 + `stateDescription="已选中"`；
//  * disabled 40% + 不可点；超长文案单行 ellipsis；
//  * 选中后默认关、焦点回锚点按钮；空菜单**不弹**（调用方/本组件都判）。
package dev.myco.android.ui.m5.primitives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 菜单项（受控；危险项与普通项必须视觉可辨，§3.12 字段 6）。 */
data class M5MenuItem(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val selected: Boolean = false,
    /** 项内容描述（读屏必填；默认用 label）。 */
    val contentDescription: String = label,
    val icon: (@Composable (tint: Color) -> Unit)? = null,
)

/**
 * 手机形态菜单（底部 sheet 单层列表）。
 *
 * @param dismissOnSelect 选中后是否关（默认关；调用方可自行决定）
 */
@Composable
fun M5Menu(
    visible: Boolean,
    items: List<M5MenuItem>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    returnFocus: FocusRequester? = null,
    dismissOnSelect: Boolean = true,
    scrollAtTop: () -> Boolean = { true },
) {
    if (!visible) return
    // 空菜单不弹（§3.12 字段 10：`items.isEmpty()` 时不打开，调用方负责；这里双保险）
    if (items.isEmpty()) return
    M5Sheet(
        visible = true,
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        returnFocus = returnFocus,
        scrollAtTop = scrollAtTop,
    ) {
        items.forEach { item ->
            M5MenuRow(
                item = item,
                onSelect = {
                    onSelect(item.id)
                    if (dismissOnSelect) onDismissRequest()
                },
            )
            Spacer(Modifier.size(4.dp))
        }
    }
}

/** 菜单行：整行 44 可点（路径 A），命中盒 = 布局盒。 */
@Composable
private fun M5MenuRow(item: M5MenuItem, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        item.danger -> colors.error
        item.selected -> colors.primary
        else -> colors.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.enabled,
                role = Role.Button,
                onClickLabel = item.label,
                onClick = onSelect,
            )
            .heightIn(min = 44.dp)
            .padding(horizontal = 20.dp)
            .alpha(if (item.enabled) 1f else 0.4f)
            .semantics {
                role = Role.Button
                contentDescription = item.contentDescription
                if (item.selected) stateDescription = "已选中"
                if (!item.enabled) {
                    disabled()
                    stateDescription = "不可用"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) {
            item.icon(tint)
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text = item.label,
            modifier = Modifier.weight(1f),
            color = tint,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.selected) {
            CheckGlyph(tint = colors.primary, size = 20.dp)
        }
    }
}
