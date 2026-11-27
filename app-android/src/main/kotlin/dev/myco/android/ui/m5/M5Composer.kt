package dev.myco.android.ui.m5

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * M5 输入区：Glass 大圆角浮岛。
 * 文本区 ≥44dp，工具行按钮统一 32dp，发送键主态用 accent.moss。
 */
@Composable
fun M5Composer(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    thinking: Boolean = true,
    webSearch: Boolean = false,
    reasoningLabel: String? = null,
    onAttach: (() -> Unit)? = null,
    onToggleThinking: (() -> Unit)? = null,
    onOpenModel: (() -> Unit)? = null,
    onToggleWebSearch: (() -> Unit)? = null,
    streaming: Boolean = false,
    onStop: (() -> Unit)? = null,
) {
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        M5ComposerInner(
            modifier = modifier,
            value = value,
            onValueChange = onValueChange,
            onSend = onSend,
            thinking = thinking,
            webSearch = webSearch,
            reasoningLabel = reasoningLabel,
            onAttach = onAttach,
            onToggleThinking = onToggleThinking,
            onOpenModel = onOpenModel,
            onToggleWebSearch = onToggleWebSearch,
            streaming = streaming,
            onStop = onStop,
        )
    }
}

@Composable
private fun M5ComposerInner(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    thinking: Boolean = true,
    webSearch: Boolean = false,
    reasoningLabel: String? = null,
    onAttach: (() -> Unit)? = null,
    onToggleThinking: (() -> Unit)? = null,
    onOpenModel: (() -> Unit)? = null,
    onToggleWebSearch: (() -> Unit)? = null,
    streaming: Boolean = false,
    onStop: (() -> Unit)? = null,
) {
    val p = LocalM5.current.palette
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        radius = 34.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = M5Type.body.copy(color = p.text),
                    cursorBrush = SolidColor(p.moss),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text("说点什么…", style = M5Type.body, color = p.textTertiary)
                            }
                            inner()
                        }
                    },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeoSurface(
                    modifier = Modifier.size(32.dp),
                    radius = 16.dp,
                    material = NeoMaterial.Raised,
                    onClick = onAttach ?: {},
                ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(M5Icons.plus, Modifier.size(16.dp), p.textSecondary) } }
                NeoSurface(
                    modifier = Modifier.height(32.dp),
                    radius = 16.dp,
                    material = NeoMaterial.Raised,
                    fill = if (thinking) p.mossTint else p.surface,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                    onClick = onOpenModel ?: (onToggleThinking ?: {}),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        M5Icon(M5Icons.clock, Modifier.size(13.dp), if (thinking) p.moss else p.textSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("深度思考", style = M5Type.caption, color = if (thinking) p.text else p.textSecondary)
                        if (!reasoningLabel.isNullOrBlank()) {
                            Text(" · $reasoningLabel", style = M5Type.caption, color = p.textTertiary)
                        }
                        Spacer(Modifier.width(2.dp))
                        M5Icon(M5Icons.chevronDown, Modifier.size(10.dp), p.textTertiary)
                    }
                }
                NeoSurface(
                    modifier = Modifier.height(32.dp),
                    radius = 16.dp,
                    material = NeoMaterial.Raised,
                    fill = if (webSearch) p.mossTint else p.surface,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                    onClick = onToggleWebSearch ?: {},
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        M5Icon(M5Icons.globe, Modifier.size(13.dp), if (webSearch) p.moss else p.textSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("智能搜索", style = M5Type.caption, color = if (webSearch) p.text else p.textSecondary)
                    }
                }
                Spacer(Modifier.weight(1f))
                NeoSurface(
                    modifier = Modifier.size(32.dp),
                    onClick = if (streaming) (onStop ?: onSend) else onSend,
                    radius = 16.dp,
                    fill = p.moss,
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        M5Icon(
                            if (streaming) M5Icons.stop else M5Icons.send,
                            Modifier.size(16.dp),
                            Color(0xFF1B1B16),
                        )
                    }
                }
            }
        }
    }
}
