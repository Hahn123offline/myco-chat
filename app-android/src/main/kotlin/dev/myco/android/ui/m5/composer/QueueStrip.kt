// 验收 ID：CMP-58 CMP-59 CMP-60 CMP-61 CMP-62 CMP-64 CMP-65 CMP-66 CMP-67 CMP-68 CMP-79 CMP-80 CMP-82 CMP-83
//
// composer.queue.strip / composer.queue.row / composer.queue.edit-dialog（composer.md §3.17–§3.19）：
//  * **客户端本地队列的独立条**（在输入卡上方，参与布局流，E-05=C 无折叠头）；
//    高 = `8 + min(q,3)×48`，`q>3` 条内滚（cap 152），q=0 整条不渲染（CMP-58/60）；
//  * 行 48：预览（单行 13/20 ellipsis，**不可点**）+ 三动作 40×40（编辑 / 撤回 / 删除，净距 0）；
//    三动作与预览区**命中盒不相交**（CMP-61）；
//  * busy（提交/自动发送在飞）→ 三动作全禁用（串行化，防连点与自动出队竞争，CMP-62）；
//  * 编辑 = 居中 **Modal**（不是行内编辑器）；空文本保存禁用（CMP-64/65/66）；
//  * 撤回 = 出队 + 文字回填草稿（不销毁文字，CMP-82）；删除 = 出队 + 宿主 Toast 6000ms 可撤销（CMP-83）；
//  * 首版**不含 steer**：本文件不存在 steer 入口（CMP-63 的负向断言由源码遍历保证）。
package dev.myco.android.ui.m5.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.m5.M5IconSpec
import dev.myco.android.ui.m5.primitives.IconAction
import dev.myco.android.ui.m5.primitives.IconActionState
import dev.myco.android.ui.m5.primitives.M5Button
import dev.myco.android.ui.m5.primitives.M5ButtonVariant
import dev.myco.android.ui.m5.primitives.M5Modal
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.M5Icon

/** 编辑图标（自绘 24dp 网格铅笔，不引图标库；M5Icons 无编辑图标）。 */
private val EditPencilIcon = M5IconSpec("编辑待发消息") { color ->
    drawLine(
        color = color,
        start = Offset(6.5f, 17.5f),
        end = Offset(16.5f, 7.5f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(16f, 6f),
        end = Offset(18f, 8f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(6.5f, 17.5f),
        end = Offset(8.5f, 17.6f),
        strokeWidth = 1.6f,
        cap = StrokeCap.Round,
    )
}

/**
 * 客户端队列条（`q ≥ 1` 才渲染；q=0 返回 0 节点）。
 *
 * @param actionsEnabled false = busy（提交/自动发送在飞）→ 三动作全禁用
 * @param onEdit 保存编辑，返回 false 表示写回失败（防御分支：留在编辑态 + 错误行，CMP-67）
 * @param onRecall 撤回（宿主把文字回填草稿）
 * @param onDelete 删除（宿主负责 Toast 6000ms 撤销）
 */
@Composable
fun QueueStrip(
    queue: ComposerQueue,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = !queue.busy,
    onEdit: (id: Long, newText: String) -> Boolean = { _, _ -> true },
    onRecall: (id: Long) -> Unit = {},
    onDelete: (id: Long) -> Unit = {},
    onCancelEdit: () -> Unit = {},
) {
    // CMP-60：队列清空 → 整条不渲染（H 同帧回算）
    if (queue.isEmpty) return

    val colors = MaterialTheme.colorScheme
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingText by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    val editReturnFocus = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(queue.heightDp.dp)
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .alpha(if (queue.paused) 0.6f else 1f)
            .semantics {
                contentDescription = "待发消息，共 ${queue.count} 条"
                liveRegion = LiveRegionMode.Polite
                if (queue.paused) stateDescription = "已暂停，本轮结束后继续"
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = (ComposerLayout.QUEUE_PADDING_DP / 2).dp),
        ) {
            itemsIndexed(queue.items, key = { _, item -> item.id }) { index, item ->
                QueueRow(
                    item = item,
                    index = index,
                    total = queue.count,
                    actionsEnabled = actionsEnabled,
                    editFocusRequester = if (editingId == item.id) editReturnFocus else null,
                    onEdit = {
                        editingId = item.id
                        editingText = item.text
                        saveError = null
                    },
                    onRecall = { onRecall(item.id) },
                    onDelete = { onDelete(item.id) },
                )
            }
        }
    }

    // 编辑 Dialog：居中 Modal（D-03；不做行内编辑器，避免键盘把队列条顶走）
    val editingItem = editingId?.let { id -> queue.items.firstOrNull { it.id == id } }
    if (editingId != null && editingItem != null) {
        QueueEditDialog(
            initialText = editingText,
            error = saveError,
            returnFocus = editReturnFocus,
            onDismiss = {
                editingId = null
                saveError = null
                onCancelEdit()
            },
            onSave = { text ->
                // 防御分支：本地写回正常不可达；返回 false → 留在编辑态 + 错误行（CMP-67）
                val saved = onEdit(editingItem.id, text)
                if (saved) {
                    editingId = null
                    saveError = null
                } else {
                    saveError = "保存失败：待发文本未更新"
                }
                saved
            },
        )
    }
}

/** 队列行：预览（不可点）+ 编辑/撤回/删除三动作。 */
@Composable
private fun QueueRow(
    item: QueuedMessage,
    index: Int,
    total: Int,
    actionsEnabled: Boolean,
    editFocusRequester: FocusRequester?,
    onEdit: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComposerLayout.QUEUE_ROW_DP.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 预览区**不可点**（避免与三动作争命中，§3.18 字段 3）
        Text(
            text = item.preview,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .semantics {
                    contentDescription = "待发第 ${index + 1} 条，共 $total 条：${item.preview}"
                },
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconAction(
            action = IconActionState(
                id = "edit",
                contentDescription = "编辑待发消息",
                enabled = actionsEnabled,
                icon = { tint -> M5Icon(EditPencilIcon, Modifier.size(20.dp), tint) },
            ),
            tint = colors.onSurfaceVariant,
            onAction = { onEdit() },
            modifier = if (editFocusRequester != null) {
                Modifier.focusRequester(editFocusRequester)
            } else {
                Modifier
            },
        )
        IconAction(
            action = IconActionState(
                id = "recall",
                contentDescription = "取消并回填到输入框",
                enabled = actionsEnabled,
                icon = { tint -> M5Icon(M5Icons.back, Modifier.size(20.dp), tint) },
            ),
            tint = colors.onSurfaceVariant,
            onAction = { onRecall() },
        )
        IconAction(
            action = IconActionState(
                id = "delete",
                contentDescription = "删除待发消息",
                enabled = actionsEnabled,
                icon = { tint -> M5Icon(M5Icons.trash, Modifier.size(20.dp), tint) },
            ),
            tint = colors.error,
            onAction = { onDelete() },
        )
    }
}

/**
 * 编辑待发消息 Dialog（composer.queue.edit-dialog）。
 *
 * @param returnFocus 关闭后回焦触发行的编辑键（自写；平台只保证焦点陷阱）
 * @param onSave 返回 false = 写回失败 → 留在编辑态 + 错误行（CMP-67 防御分支）
 */
@Composable
fun QueueEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
    returnFocus: FocusRequester? = null,
    error: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    var value by remember(initialText) {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = initialText,
                selection = androidx.compose.ui.text.TextRange(initialText.length),
            ),
        )
    }
    val inputFocus = remember { FocusRequester() }
    val canSave = value.text.isNotBlank()

    LaunchedEffect(Unit) {
        runCatching { inputFocus.requestFocus() }
    }

    M5Modal(
        visible = true,
        onDismissRequest = onDismiss,
        title = "编辑待发消息",
        returnFocus = returnFocus,
        buttons = {
            M5Button(
                text = "取消",
                variant = M5ButtonVariant.SECONDARY,
                onClick = onDismiss,
            )
            M5Button(
                text = "保存",
                variant = M5ButtonVariant.PRIMARY,
                // 空文本保存禁用（CMP-66）；重复点保存只提交一次（onSave 幂等由上层保证）
                enabled = canSave,
                disabledReason = "内容为空",
                onClick = {
                    if (canSave) onSave(value.text)
                },
            )
        },
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 192.dp)
                .focusRequester(inputFocus)
                .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .semantics {
                    contentDescription = "待发消息内容"
                },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "输入待发消息…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

/** 供单测/文档引用的绘制辅助（保持与 M5 图标同一 24dp 网格口径）。 */
@Suppress("unused")
private fun DrawScope.drawStroke(color: Color, from: Offset, to: Offset) {
    drawLine(color, from, to, strokeWidth = 1.6f, cap = StrokeCap.Round, pathEffect = null)
}

@Suppress("unused")
private fun DrawScope.drawRingStroke(color: Color, radius: Float) {
    drawCircle(color, radius, style = Stroke(width = 1.6f))
}
