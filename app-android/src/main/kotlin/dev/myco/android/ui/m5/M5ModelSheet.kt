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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 供 M5 模型面板使用的扁平行（不回传 UI 私有类型）。 */
data class M5ModelOption(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val modelName: String,
    val reasoningDefault: String?,
    val levels: List<String>,
    val selected: Boolean,
)

/**
 * DSH 风格的「模型 + 思考强度」合一面板：设置里配好，聊天页一个胶囊即可调整。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M5ModelSheet(
    models: List<M5ModelOption>,
    currentReasoning: String?,
    thinking: Boolean,
    error: String?,
    onToggleThinking: () -> Unit,
    onPickModel: (M5ModelOption) -> Unit,
    onPickReasoning: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = if (dark) M5DarkPalette else M5LightPalette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = base.surface,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
    ) {
        M5Theme(dark) {
            val p = LocalM5.current.palette
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 14.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(SuperellipseShape(2.dp))
                        .background(p.textTertiary.copy(alpha = 0.35f)),
                )
                // 「深度思考」合并在同一个入口里：一个面板管模型 + 思考强度 + 开关
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("深度思考", style = M5Type.body, color = p.text)
                        Text("随回答一起输出思考过程", style = M5Type.caption, color = p.textTertiary)
                    }
                    M5Switch(checked = thinking) { onToggleThinking() }
                }
                Spacer(Modifier.height(6.dp))
                Text("模型", style = M5Type.caption, color = p.textTertiary)
                Spacer(Modifier.height(4.dp))
                models.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(SuperellipseShape(16.dp))
                            .clickable { onPickModel(m) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.modelName, style = M5Type.body, color = p.text)
                            Text(
                                "${m.providerName} · ${m.modelId}",
                                style = M5Type.caption,
                                color = p.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (m.selected) {
                            M5Icon(M5Icons.check, Modifier.size(16.dp), p.moss)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("思考强度", style = M5Type.caption, color = p.textTertiary)
                Spacer(Modifier.height(8.dp))
                val levels = (models.firstOrNull { it.selected } ?: models.firstOrNull())?.levels.orEmpty()
                if (levels.isEmpty()) {
                    Text("当前模型不支持思考强度", style = M5Type.caption, color = p.textTertiary)
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReasoningPill("默认", currentReasoning == null) { onPickReasoning(null) }
                        levels.forEach { level ->
                            ReasoningPill(level, currentReasoning == level) { onPickReasoning(level) }
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, style = M5Type.caption, color = p.danger)
                }
            }
        }
    }
}

@Composable
private fun ReasoningPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalM5.current.palette
    NeoSurface(
        radius = 15.dp,
        material = NeoMaterial.Raised,
        fill = if (selected) p.moss else p.surface,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        onClick = onClick,
    ) {
        Text(
            label,
            style = M5Type.caption,
            color = if (selected) Color(0xFF1B1B16) else p.textSecondary,
        )
    }
}

@Composable
private fun M5Switch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalM5.current.palette
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(SuperellipseShape(13.dp))
            .background(if (checked) p.moss else p.sunken)
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(SuperellipseShape(10.dp))
                .background(if (checked) Color(0xFF1B1B16) else p.surfaceHigh),
        )
    }
}