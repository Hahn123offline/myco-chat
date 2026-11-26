package dev.myco.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.model.ChatMessageUi
import dev.myco.android.ui.model.ChatProcessUi
import dev.myco.android.ui.model.ToolStepUi
import dev.myco.android.ui.model.TurnMetaUi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 解析后的聊天内容。 */
data class ChatContentState(
    val messages: List<ChatMessageUi> = emptyList(),
    val processes: Map<Long, ChatProcessUi> = emptyMap(),
    val metas: Map<Long, TurnMetaUi> = emptyMap(),
    val reasoning: Map<Long, String> = emptyMap(),
)

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content
private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.content?.toLongOrNull()

/** 事件日志 → 渲染模型（只做展示；不碰网络）。 */
fun parseChatContent(arr: JsonArray?): ChatContentState {
    val messages = mutableListOf<ChatMessageUi>()
    val process = linkedMapOf<Long, MutableList<ToolStepUi>>()
    val reasoning = linkedMapOf<Long, String>()
    val metas = linkedMapOf<Long, TurnMetaUi>()
    arr.orEmpty().forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        val type = o.str("type") ?: return@forEach
        val data = o["data"] as? JsonObject
        when (type) {
            "user/message" -> {
                val text = data?.str("text").orEmpty()
                val names = (data?.get("attachments") as? JsonArray)?.mapNotNull {
                    (it as? JsonObject)?.str("name")
                }.orEmpty()
                messages += ChatMessageUi(
                    role = "user",
                    text = text + if (names.isEmpty()) "" else "\n" + names.joinToString("\n") { "[附件] $it" },
                    messageId = data?.str("messageId"),
                    turn = data?.long("turn"),
                )
            }
            "assistant/message" -> {
                val reasoningText = data?.str("reasoning")
                if (!reasoningText.isNullOrBlank()) {
                    data?.long("turn")?.let { turn ->
                        reasoning[turn] = (reasoning[turn]?.plus("\n") ?: "") + reasoningText
                    }
                }
                messages += ChatMessageUi(
                    role = "assistant",
                    text = data?.str("text").orEmpty(),
                    messageId = data?.str("messageId"),
                    nodeId = data?.str("nodeId"),
                    version = data?.str("version")?.toIntOrNull() ?: 1,
                    turn = data?.long("turn"),
                    finishReason = data?.str("finishReason"),
                    reasoning = data?.str("reasoning"),
                )
            }
            "tool/call" -> {
                val turn = data?.long("turn") ?: return@forEach
                process.getOrPut(turn) { mutableListOf() } +=
                    ToolStepUi(name = data.str("name") ?: "tool", ok = null, text = "")
            }
            "tool/result" -> {
                val turn = data?.long("turn") ?: return@forEach
                val list = process.getOrPut(turn) { mutableListOf() }
                val name = data.str("name") ?: "tool"
                val ok = data.str("ok")?.toBoolean() ?: data.str("ok") == "true"
                val text = data.str("text") ?: ""
                val idx = list.indexOfLast { it.name == name && it.ok == null }
                if (idx >= 0) list[idx] = list[idx].copy(ok = ok, text = text)
                else list += ToolStepUi(name, ok, text)
            }
            "turn/end" -> {
                val turn = data?.long("turn") ?: return@forEach
                metas[turn] = TurnMetaUi(
                    status = data.str("status") ?: "ok",
                    steps = data.str("steps")?.toIntOrNull() ?: 0,
                    durationMs = data.long("durationMs"),
                    costUsd = data.str("costUsd")?.toDoubleOrNull(),
                )
            }
            "assistant.done" -> Unit
        }
    }
    val superseded = mutableSetOf<String>()
    arr.orEmpty().forEach { el ->
        val o = el as? JsonObject ?: return@forEach
        if (o.str("type") == "user/message") {
            (o["data"] as? JsonObject)?.str("revises")?.let { superseded += it }
        }
    }
    val withMarked = messages.map { m ->
        if (m.role == "user" && m.messageId != null && m.messageId in superseded) {
            m.copy(text = m.text + "\n（已被后续编辑消息替代）", revised = true)
        } else m
    }
    return ChatContentState(
        messages = withMarked,
        processes = process.mapValues { (_, steps) -> ChatProcessUi(steps) },
        metas = metas,
        reasoning = reasoning,
    )
}

/** 助手全宽 Markdown（基础集：标题/列表/引用/代码块/粗斜体/行内代码；表格先按等宽行）。 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lines = text.lines()
        var i = 0
        val paragraph = StringBuilder()
        @Composable fun flushParagraph() {
            val p = paragraph.toString().trim()
            paragraph.clear()
            if (p.isNotEmpty()) InlineMarkdown(p)
        }
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            when {
                line.startsWith("```") -> {
                    flushParagraph()
                    val lang = line.removePrefix("```").trim()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                        code.append(lines[i]).append('\n'); i++
                    }
                    CodeBlock(code.toString().trimEnd(), lang)
                }
                line.isBlank() -> flushParagraph()
                line.startsWith("#") -> {
                    flushParagraph()
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(3)
                    val content = line.drop(level).trim()
                    Text(
                        inlineAnnotated(content),
                        style = when (level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                    )
                }
                line.startsWith("> ") -> {
                    flushParagraph()
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)).width(3.dp))
                        InlineMarkdown(line.removePrefix("> ").trim())
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushParagraph()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge)
                        InlineMarkdown(line.drop(2))
                    }
                }
                Regex("^\\d+[.)]\\s+").containsMatchIn(line) -> {
                    flushParagraph()
                    val num = line.takeWhile { it.isDigit() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("$num.  ", style = MaterialTheme.typography.bodyLarge)
                        InlineMarkdown(line.dropWhile { it.isDigit() }.drop(1).trimStart())
                    }
                }
                line.contains('|') -> {
                    flushParagraph()
                    val tableRows = mutableListOf<List<String>>()
                    var j = i
                    while (j < lines.size && lines[j].contains('|')) {
                        val l = lines[j].trim()
                        val isSeparator = l.replace(Regex("[|\\-: ]"), "").isEmpty()
                        if (!isSeparator && l.isNotBlank()) {
                            tableRows += l.trim('|').split('|').map { it.trim() }
                        }
                        j++
                    }
                    if (tableRows.isNotEmpty()) TableBlock(tableRows)
                    i = j - 1
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
                }
            }
            i++
        }
        flushParagraph()
    }
}

@Composable
private fun TableBlock(rows: List<List<String>>) {
    val header = rows.first()
    val body = rows.drop(1)
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MycoRadius.control))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth()) {
            header.forEach { cell ->
                Text(
                    inlineAnnotated(cell),
                    modifier = Modifier.width(120.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        androidx.compose.material3.HorizontalDivider()
        body.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Text(
                        inlineAnnotated(cell),
                        modifier = Modifier.width(120.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdown(text: String) {
    SelectionContainer { Text(inlineAnnotated(text), style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun inlineAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    fun nextMarker(start: Int, marker: String): Int = text.indexOf(marker, start)
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = nextMarker(i + 2, "**")
                if (end < 0) { append(text.substring(i)); i = text.length }
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end < 0) { append(text.substring(i)); i = text.length }
                else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceVariant)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            text.startsWith("*", i) || text.startsWith("_", i) -> {
                val marker = text[i].toString()
                val end = text.indexOf(marker, i + 1)
                if (end < 0) { append(text[i].toString()); i++ }
                else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            text.startsWith("[", i) && text.indexOf("](", i) > 0 -> {
                val mid = text.indexOf("](", i)
                val end = text.indexOf(')', mid)
                if (end < 0) { append(text[i].toString()); i++ }
                else {
                    val label = text.substring(i + 1, mid)
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                    i = end + 1
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, lang: String) {
    val clipboard = LocalClipboardManager.current
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MycoRadius.control))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(MycoSpacing.m),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (lang.isNotBlank()) {
                    Text(lang, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                } else Box(Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("复制") }
            }
            SelectionContainer {
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** 聊天内容渲染：助手全宽无气泡、Markdown、版本切换、过程折叠、footer、操作条。 */
@Composable
fun ChatMessages(
    state: ChatContentState,
    listState: LazyListState,
    streamingText: String?,
    onCopy: (String) -> Unit,
    onRegenerate: (ChatMessageUi) -> Unit,
    onUserLongPress: (ChatMessageUi) -> Unit = {},
    initialVersions: Map<String, Int> = emptyMap(),
    onVersionSelected: (nodeId: String, version: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val activeVersions = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Int>().apply { putAll(initialVersions) }
    }
    val expandedProcess = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    val nodes = state.messages.filter { it.role == "assistant" && it.nodeId != null }
        .groupBy { it.nodeId!! }
        .mapValues { (_, list) -> list.sortedBy { it.version } }
    var lastProcessTurn: Long? = null

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = MycoSpacing.l, vertical = MycoSpacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(MycoSpacing.s),
    ) {
        state.messages.forEachIndexed { index, msg ->
            if (msg.role == "user") {
                item(key = "u-$index-${msg.messageId ?: ""}") {
                    ChatBubble(BubbleRole.USER, msg.text, onLongPress = { onUserLongPress(msg) })
                }
                return@forEachIndexed
            }
            // 中间工具步骤文本（无 nodeId 且 finishReason=tool_calls）折叠进过程，不单独渲染
            if (msg.nodeId == null && msg.finishReason == "tool_calls") return@forEachIndexed
            val versions = msg.nodeId?.let { nodes[it] }.orEmpty()
            val active = activeVersions[msg.nodeId] ?: versions.maxOfOrNull { it.version } ?: msg.version
            // 版本链只渲染当前选中版本一次，避免同一 messageId 重复 key 或重复渲染
            if (msg.nodeId != null && msg.version != active) return@forEachIndexed
            val shown = msg
            val turn = msg.turn
            if (turn != null && turn != lastProcessTurn) {
                lastProcessTurn = turn
                val process = state.processes[turn]
                if (process != null && process.steps.isNotEmpty()) {
                    item(key = "p-$turn") {
                        ProcessFold(
                            turn = turn,
                            process = process,
                            reasoning = state.reasoning[turn],
                            expanded = expandedProcess[turn] == true,
                            onToggle = { expandedProcess[turn] = expandedProcess[turn] != true },
                        )
                    }
                }
            }
            item(key = "a-$index-${shown.messageId ?: ""}") {
                Column(Modifier.fillMaxWidth()) {
                    MarkdownText(shown.text)
                    MessageActions(
                        message = shown,
                        versions = versions,
                        activeVersion = active,
                        onCopy = { onCopy(shown.text) },
                        onRegenerate = { onRegenerate(shown) },
                        onSwitch = { v ->
                            if (msg.nodeId != null) {
                                activeVersions[msg.nodeId!!] = v
                                onVersionSelected(msg.nodeId!!, v)
                            }
                        },
                    )
                    val meta = turn?.let { state.metas[it] }
                    if (meta != null) Footer(meta)
                }
            }
        }
        if (streamingText != null) {
            item(key = "streaming") {
                Column(Modifier.fillMaxWidth()) {
                    MarkdownText(streamingText + " ▍")
                }
            }
        }
    }
}

@Composable
private fun ProcessFold(
    turn: Long,
    process: ChatProcessUi,
    reasoning: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MycoRadius.control))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = MycoSpacing.m, vertical = MycoSpacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggle) {
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "过程 · ${process.steps.size} 步",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("turn $turn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            if (!reasoning.isNullOrBlank()) {
                Text(
                    "思考：${reasoning.take(500)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MycoSpacing.xs),
                )
            }
            process.steps.forEach { step ->
                Text(
                    "· ${step.name} ${if (step.ok == false) "失败" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (step.text.isNotBlank()) {
                    Text(
                        step.text.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = MycoSpacing.m),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActions(
    message: ChatMessageUi,
    versions: List<ChatMessageUi>,
    activeVersion: Int,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onSwitch: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCopy) { Text("复制") }
        if (message.messageId != null) TextButton(onClick = onRegenerate) { Text("重新生成") }
        if (versions.size > 1) {
            val idx = versions.indexOfFirst { it.version == activeVersion }.coerceAtLeast(0)
            TextButton(enabled = idx > 0, onClick = { onSwitch(versions[idx - 1].version) }) { Text("‹") }
            Text("${idx + 1}/${versions.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(enabled = idx < versions.size - 1, onClick = { onSwitch(versions[idx + 1].version) }) { Text("›") }
        }
    }
}

@Composable
private fun Footer(meta: TurnMetaUi) {
    val secs = meta.durationMs?.let { "%.1fs".format(it / 1000.0) }
    val cost = meta.costUsd?.takeIf { it > 0 }?.let { "¥${"%.4f".format(it)}" }
    val status = when (meta.status) {
        "stopped" -> "已停止"
        "error" -> "出错"
        "truncated" -> "被截断"
        else -> null
    }
    Text(
        listOfNotNull(secs, "${meta.steps} 步", cost, status).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}


