package dev.myco.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 基础组件库（M1）：只保证逻辑、状态与令牌正确；装饰级视觉（插画/动效）推迟到 M6。
 * 所有颜色从 MycoTheme / LocalMycoColors 取，禁止硬编码 hex。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MycoTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            when {
                onMenu != null -> TextButton(onClick = onMenu) {
                    Text("☰", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
                onBack != null -> TextButton(onClick = onBack) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** 基础毛玻璃：半透明表面 + 细边框；深色模式下用发光色做边缘（不喧宾夺主）。
 *  [highlight] = 焦点态（如输入中），深色下边框更亮，浅色下阴影更明显。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(MycoRadius.card),
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ext = LocalMycoColors.current
    val dark = ext.glow.alpha > 0f
    val fill = if (dark) {
        Brush.linearGradient(listOf(ext.glassTint, ext.glassTint.copy(alpha = 0.55f)))
    } else {
        SolidColor(ext.glassTint)
    }
    val edge = if (dark) {
        val glowAlpha = if (highlight) 0.85f else 0.45f
        Brush.linearGradient(
            listOf(ext.glassBorder, ext.glow.copy(alpha = glowAlpha), ext.glassBorder)
        )
    } else {
        SolidColor(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else ext.glassBorder)
    }
    val shadowMod = if (!dark) {
        Modifier.shadow(
            elevation = if (highlight) 6.dp else 3.dp,
            shape = shape,
            clip = false,
            ambientColor = Color(0x1A5F7040),
            spotColor = Color(0x225F7040),
        )
    } else Modifier
    Box(
        modifier
            .then(shadowMod)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, edge, shape)
    ) { content() }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = MycoSpacing.l, bottom = MycoSpacing.xs),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SettingsRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = MycoSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 会话列表项：标题 + 时间（右侧）+ 片段。长按由调用方处理。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionListItem(
    title: String,
    time: String,
    snippet: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = MycoSpacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            if (snippet != null) {
                Text(
                    snippet,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MycoSpacing.s, top = 2.dp),
        )
    }
}

/** 搜索框（历史页）：保持和输入区一致的圆角与描边语气。 */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String = "搜索会话或消息") {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.l, vertical = MycoSpacing.s),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = RoundedCornerShape(MycoRadius.control),
    )
}

@Composable
fun EmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val ext = LocalMycoColors.current
    Column(
        Modifier.fillMaxWidth().padding(MycoSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MycoSpacing.s),
    ) {
        // 装饰级视觉（M6）：两圈同色系，不抢内容
        Canvas(Modifier.size(76.dp).padding(bottom = MycoSpacing.s)) {
            val r = size.minDimension / 2f
            drawCircle(color = scheme.primaryContainer.copy(alpha = 0.75f), radius = r * 0.92f)
            drawCircle(
                color = ext.bubbleUser.copy(alpha = 0.9f),
                radius = r * 0.55f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.42f),
            )
            drawCircle(
                color = scheme.primary.copy(alpha = 0.55f),
                radius = r * 0.92f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = MycoSpacing.s)) { Text(actionLabel) }
        }
    }
}

@Composable
fun InlineError(text: String, onRetry: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MycoRadius.control))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = MycoSpacing.m, vertical = MycoSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("重试", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

enum class BubbleRole { USER, ASSISTANT, ERROR, SYSTEM }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    role: BubbleRole,
    text: String,
    streaming: Boolean = false,
    stopped: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val ext = LocalMycoColors.current
    val scheme = MaterialTheme.colorScheme
    val system = role == BubbleRole.SYSTEM
    val fill = when (role) {
        BubbleRole.USER -> ext.bubbleUser
        BubbleRole.ASSISTANT -> ext.bubbleAssistant
        BubbleRole.ERROR -> ext.bubbleError
        BubbleRole.SYSTEM -> Color.Transparent
    }
    val contentColor = when (role) {
        BubbleRole.USER -> scheme.onPrimaryContainer
        BubbleRole.ASSISTANT -> scheme.onSurface
        BubbleRole.ERROR -> scheme.onErrorContainer
        BubbleRole.SYSTEM -> scheme.onSurfaceVariant
    }
    val shape = when (role) {
        BubbleRole.USER -> RoundedCornerShape(MycoRadius.card, MycoRadius.card, 4.dp, MycoRadius.card)
        BubbleRole.ASSISTANT -> RoundedCornerShape(MycoRadius.card, MycoRadius.card, MycoRadius.card, 4.dp)
        else -> RoundedCornerShape(MycoRadius.card)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (role == BubbleRole.USER) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (system) 0.9f else 0.86f)
                .clip(shape)
                .then(
                    if (!system) Modifier.shadow(
                        elevation = 1.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color(0x145F7040),
                        spotColor = Color(0x1A5F7040),
                    ) else Modifier
                )
                .background(fill, shape)
                .then(
                    if (onLongPress != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
                    else Modifier
                )
                .then(if (system) Modifier.padding(vertical = MycoSpacing.xs) else Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                .animateContentSize(),
        ) {
            Text(
                text + if (streaming) " ▍" else "",
                style = if (system) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                color = contentColor,
                textAlign = if (system) TextAlign.Center else TextAlign.Start,
            )
            if (stopped) {
                Text(
                    "已停止",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MycoSpacing.xs),
                )
            }
        }
    }
}

@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    enabled: Boolean = true,
    onAttach: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(MycoRadius.sheet), highlight = focused) {
        Row(
            Modifier.fillMaxWidth().padding(MycoSpacing.s),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s),
        ) {
            if (onAttach != null) {
                OutlinedButton(onClick = onAttach, shape = RoundedCornerShape(MycoRadius.control)) {
                    Text("＋", fontSize = 18.sp)
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…") },
                maxLines = 4,
                shape = RoundedCornerShape(MycoRadius.control),
                interactionSource = interaction,
            )
            if (streaming) {
                OutlinedButton(onClick = onStop) { Text("停止") }
            } else {
                Button(onClick = onSend, enabled = enabled && value.isNotBlank()) { Text("发送") }
            }
        }
    }
}

@Composable
fun Divider() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
