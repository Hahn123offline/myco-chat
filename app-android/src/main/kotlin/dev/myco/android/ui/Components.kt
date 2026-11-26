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

/** 卡片表面：默认**完全不透明**的扁平表面 + 柔和渐变阴影（新拟物方向）；真正的毛玻璃作为显式选项留到后续。
 *  [highlight] = 焦点态（如输入中）。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(MycoRadius.card),
    highlight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ext = LocalMycoColors.current
    val scheme = MaterialTheme.colorScheme
    val dark = ext.glow.alpha > 0f
    // 不透明渐变：顶部 surface，底部混入同色系容器（仍 100% 不透明）
    val bottomTint = if (dark) {
        androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primaryContainer, 0.35f)
    } else {
        androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primaryContainer, 0.28f)
    }
    val fill = Brush.verticalGradient(listOf(scheme.surface, bottomTint))
    val edge = if (dark) {
        val glowAlpha = if (highlight) 0.8f else 0.35f
        Brush.linearGradient(listOf(ext.glassBorder, ext.glow.copy(alpha = glowAlpha), ext.glassBorder))
    } else {
        SolidColor(if (highlight) scheme.primary.copy(alpha = 0.5f) else scheme.outlineVariant)
    }
    val shadowMod = Modifier.shadow(
        elevation = if (highlight) 6.dp else 3.dp,
        shape = shape,
        clip = false,
        ambientColor = if (dark) ext.glow.copy(alpha = 0.18f) else Color(0x1A5F7040),
        spotColor = if (dark) ext.glow.copy(alpha = 0.22f) else Color(0x225F7040),
    )
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
    // 基座形态：助手消息全宽、无气泡；气泡只给用户（chat 变体未来可加回）。
    val bubble = role != BubbleRole.ASSISTANT && !system
    val fill = when (role) {
        BubbleRole.USER -> ext.bubbleUser
        BubbleRole.ASSISTANT -> Color.Transparent
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
        BubbleRole.USER -> RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        BubbleRole.ASSISTANT -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(MycoRadius.card)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (role == BubbleRole.USER) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (system) 0.9f else if (role == BubbleRole.USER) 0.86f else 1f)
                .clip(shape)
                .then(
                    if (bubble) Modifier.shadow(
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
                .then(
                    when {
                        system -> Modifier.padding(vertical = MycoSpacing.xs)
                        bubble -> Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        else -> Modifier.padding(vertical = 4.dp)
                    }
                )
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
    onVoice: (() -> Unit)? = null,
    thinking: Boolean = false,
    onToggleThinking: (() -> Unit)? = null,
    webSearch: Boolean = false,
    onToggleWebSearch: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(MycoRadius.sheet), highlight = focused) {
        Column(Modifier.fillMaxWidth().padding(MycoSpacing.s)) {
            Row(
                Modifier.fillMaxWidth(),
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
                when {
                    streaming -> OutlinedButton(onClick = onStop) { Text("停止") }
                    value.isNotBlank() || enabled -> Button(onClick = onSend, enabled = value.isNotBlank() || enabled) { Text("发送") }
                    onVoice != null -> OutlinedButton(onClick = onVoice, shape = RoundedCornerShape(MycoRadius.control)) { Text("🎤") }
                    else -> Button(onClick = onSend, enabled = false) { Text("发送") }
                }
            }
            if (onToggleThinking != null || onToggleWebSearch != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(MycoSpacing.s), modifier = Modifier.padding(top = MycoSpacing.s)) {
                    if (onToggleThinking != null) {
                        androidx.compose.material3.FilterChip(
                            selected = thinking,
                            onClick = onToggleThinking,
                            label = { Text("深度思考") },
                        )
                    }
                    if (onToggleWebSearch != null) {
                        androidx.compose.material3.FilterChip(
                            selected = webSearch,
                            onClick = onToggleWebSearch,
                            label = { Text("智能搜索") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Divider() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
