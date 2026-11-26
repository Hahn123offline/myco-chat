package dev.myco.android.ui.m5

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * M5.3 聊天页样板：玻璃顶栏 + 消息节奏 + 新版输入区。
 * 数据为静态样张；思考过程用「可直接点击的文字」呈现（不包色块/按钮），
 * 且位置在最终回答之前。
 */
@Composable
fun M5ChatSample(dark: Boolean, onToggleDark: () -> Unit, onClose: () -> Unit) {
    M5Theme(dark) {
        val p = LocalM5.current.palette
        var input by remember { mutableStateOf("") }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bg))),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                SampleTopBar(dark = dark, onToggleDark = onToggleDark, onClose = onClose)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        UserBubble("今天有点累，但还想把 M5 推起来。")
                    }
                    item {
                        ThinkingSection(
                            expandedInitial = true,
                            summary = "执行了命令，已读取图片，已写入文件等",
                        )
                    }
                    item {
                        AssistantBlock(
                            text = "先别急着把整个界面推倒。M5 只需要先定下两样东西：一套可复用的视觉原语，" +
                                "和一个能承载它们的壳。前者是形状、材质、光照和动效，后者是面板之间的层叠关系。\n\n" +
                                "这两样定稳之后，聊天、抽屉、设置只是它们的第一批应用。",
                            meta = "18.4s · 5 步",
                        )
                    }
                    item {
                        BranchBar()
                    }
                    item {
                        UserBubble("那先把聊天页做出来看看。")
                    }
                    item {
                        ThinkingSection(
                            expandedInitial = false,
                            summary = "思考 · 已读取 2 个网页",
                        )
                    }
                    item {
                        StreamingBlock()
                    }
                }
                M5Composer(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    value = input,
                    onValueChange = { input = it },
                    onSend = { input = "" },
                )
            }
        }
    }
}

@Composable
private fun SampleTopBar(dark: Boolean, onToggleDark: () -> Unit, onClose: () -> Unit) {
    val p = LocalM5.current.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoSurface(
            modifier = Modifier.size(36.dp),
            radius = 18.dp,
            material = NeoMaterial.Raised,
            onClick = onClose,
        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(M5Icons.menu, Modifier.size(17.dp), p.text) } }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "把 M5 推起来",
                style = M5Type.title,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("DeepSeek · 刚刚", style = M5Type.caption, color = p.textTertiary)
        }
        NeoSurface(
            modifier = Modifier.height(32.dp),
            radius = 16.dp,
            material = NeoMaterial.Raised,
            contentPadding = PaddingValues(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
            onClick = onToggleDark,
        ) { Text(if (dark) "深色" else "浅色", style = M5Type.caption, color = p.textSecondary) }
        Spacer(Modifier.width(8.dp))
        NeoSurface(
            modifier = Modifier.size(36.dp),
            radius = 18.dp,
            material = NeoMaterial.Raised,
            onClick = {},
        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(M5Icons.more, Modifier.size(17.dp), p.text) } }
    }
}

@Composable
private fun UserBubble(text: String) {
    val p = LocalM5.current.palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        NeoSurface(
            modifier = Modifier.widthIn(max = 300.dp),
            radius = 26.dp,
            material = NeoMaterial.Float,
            fill = p.blush,
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(text, style = M5Type.body, color = Color(0xFF2E1A17))
        }
    }
}

@Composable
private fun AssistantBlock(text: String, meta: String) {
    val p = LocalM5.current.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = M5Type.body, color = p.text)
        Text(meta, style = M5Type.caption, color = p.textTertiary)
    }
}

/**
 * 思考过程：可直接点击文字（无底色/无按钮）。展开后在下方列出过程行，
 * 位置在最终回答之前——思考不属于回答的附属按钮。
 */
@Composable
private fun ThinkingSection(expandedInitial: Boolean, summary: String) {
    val p = LocalM5.current.palette
    var expanded by remember { mutableStateOf(expandedInitial) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SuperellipseShape(M5Radius.control))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            M5Icon(
                if (expanded) M5Icons.chevronDown else M5Icons.chevronRight,
                Modifier.size(13.dp),
                p.textTertiary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                summary,
                style = M5Type.caption,
                color = p.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (pressed) 0.55f else 1f),
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ThinkingLine(M5Icons.globe, "上下文注入", "time-context")
                ThinkingLine(M5Icons.spark, "思考", "User approved → proceed to chat page sample.")
                ThinkingLine(M5Icons.copy, "写入", "app-android/src/main/kotlin/.../ChatSample.kt")
                ThinkingLine(M5Icons.chevronRight, "运行命令", "Add branch icons and wire chat sample entry")
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ThinkingLine(icon: M5IconSpec, title: String, detail: String) {
    val p = LocalM5.current.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        M5Icon(icon, Modifier.size(13.dp), p.textTertiary)
        Spacer(Modifier.width(7.dp))
        Text(title, style = M5Type.caption, color = p.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text(
            detail,
            style = M5Type.caption,
            color = p.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BranchBar() {
    val p = LocalM5.current.palette
    Row(
        Modifier.fillMaxWidth().padding(end = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        M5Icon(M5Icons.branchLeft, Modifier.size(16.dp), p.textTertiary)
        Spacer(Modifier.width(6.dp))
        Text("1 / 2", style = M5Type.caption, color = p.textTertiary)
        Spacer(Modifier.width(6.dp))
        M5Icon(M5Icons.branchRight, Modifier.size(16.dp), p.textTertiary)
    }
}

@Composable
private fun StreamingBlock() {
    val p = LocalM5.current.palette
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val gapPx = with(LocalDensity.current) { 3.dp.toPx() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "好，我先把顶栏、消息节奏和输入区拼成一屏——",
                style = M5Type.body,
                color = p.text,
                onTextLayout = { layout = it },
            )
            val l = layout
            if (l != null && l.lineCount > 0) {
                val line = l.lineCount - 1
                val cursorX = l.getLineRight(line) + gapPx
                val cursorTop = l.getLineTop(line) + gapPx
                Box(
                    Modifier
                        .offset { IntOffset(cursorX.roundToInt(), cursorTop.roundToInt()) }
                        .size(width = 3.dp, height = 18.dp)
                        .clip(SuperellipseShape(1.5.dp))
                        .background(p.moss),
                )
            }
        }
    }
}
