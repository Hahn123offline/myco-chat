package dev.myco.android.ui.m5

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.core.spec.AttachmentRef

/** 空会话状态：标题 + 一句人格提示 + 两张建议卡片（点一下填进输入框）。 */
@Composable
fun M5EmptyState(onSuggestion: (String) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("开始一段对话", style = M5Type.headline, color = p.text)
            Text(
                "她记得你们聊过的事。",
                style = M5Type.caption,
                color = p.textTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
            SuggestionCard("今天有什么想聊的？", onSuggestion)
            Spacer(Modifier.height(10.dp))
            SuggestionCard("帮我理一下最近的思路", onSuggestion)
        }
    }
}

@Composable
private fun SuggestionCard(text: String, onSuggestion: (String) -> Unit) {
    val p = LocalM5.current.palette
    NeoSurface(
        modifier = Modifier.fillMaxWidth(),
        radius = M5Radius.card,
        material = NeoMaterial.Raised,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = { onSuggestion(text) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            M5Icon(M5Icons.spark, Modifier.size(15.dp), p.moss)
            Spacer(Modifier.width(10.dp))
            Text(text, style = M5Type.body, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 待发送附件：可横向滚的 M5 胶囊（名称 + 删除），替代旧 AssistChip。 */
@Composable
fun M5AttachmentRow(
    attachments: List<AttachmentRef>,
    onRemove: (AttachmentRef) -> Unit,
    onClear: () -> Unit,
) {
    if (attachments.isEmpty()) return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { ref ->
                    NeoSurface(
                        modifier = Modifier.height(32.dp),
                        radius = 16.dp,
                        material = NeoMaterial.Raised,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            M5Icon(M5Icons.copy, Modifier.size(12.dp), p.textTertiary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                ref.name,
                                style = M5Type.caption,
                                color = p.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            M5Icon(
                                M5Icons.close,
                                Modifier.size(11.dp).clickable { onRemove(ref) },
                                p.textTertiary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "清空",
                style = M5Type.caption,
                color = p.textTertiary,
                modifier = Modifier.clickable { onClear() },
            )
        }
    }
}

/** 错误条：M5 表面的轻提示，不抢正文。 */
@Composable
fun M5ErrorBar(text: String, onRetry: (() -> Unit)? = null) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(6.dp).clip(SuperellipseShape(3.dp))
                        .background(p.danger),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    style = M5Type.caption,
                    color = p.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRetry != null) {
                Text(
                    "重试",
                    style = M5Type.caption,
                    color = p.textSecondary,
                    modifier = Modifier.clickable { onRetry() }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
