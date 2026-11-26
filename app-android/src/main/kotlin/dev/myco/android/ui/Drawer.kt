package dev.myco.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.model.SessionGroup
import dev.myco.android.ui.model.SessionItemUi

/**
 * 抽屉内容（与后端解耦：只接收 SessionItemUi + 回调）。
 * 手机=覆盖式抽屉；平板/宽屏可复用同一内容放在常驻侧栏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MycoDrawerContent(
    sessions: List<SessionItemUi>,
    selectedId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SessionItemUi) -> Unit,
    onSessionLongPress: (SessionItemUi) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(start = MycoSpacing.l, end = MycoSpacing.m, top = MycoSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("myco", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onNewChat) { Text("＋ 新对话") }
        }
        SearchField(value = query, onValueChange = onQueryChange)
        if (sessions.isEmpty()) {
            Text(
                if (query.isBlank()) "还没有会话" else "没有匹配",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MycoSpacing.l, vertical = MycoSpacing.m),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                var lastGroup: SessionGroup? = null
                sessions.forEach { item ->
                    if (item.group != lastGroup) {
                        lastGroup = item.group
                        item { GroupHeader(item.group.label) }
                    }
                    item(key = item.id) {
                        DrawerSessionRow(
                            item = item,
                            selected = item.id == selectedId,
                            onClick = { onSelect(item) },
                            onLongClick = { onSessionLongPress(item) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(MycoSpacing.s))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.m, vertical = MycoSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSettings) { Text("设置") }
            Spacer(Modifier.weight(1f))
            Text("本地优先 · 离线可用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = MycoSpacing.l, top = MycoSpacing.m, bottom = MycoSpacing.xs),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSessionRow(
    item: SessionItemUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MycoSpacing.s)
            .clip(RoundedCornerShape(MycoRadius.control))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .pressable(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = MycoSpacing.m, vertical = MycoSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (!item.snippet.isNullOrBlank()) {
                Text(
                    item.snippet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(item.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onLongClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
            Text("⋯", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NewChatButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.l)) {
        Text("开始新对话")
    }
}
