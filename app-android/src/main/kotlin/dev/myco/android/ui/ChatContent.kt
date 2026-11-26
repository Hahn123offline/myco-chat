package dev.myco.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 解析后的聊天内容。 */
data class SourceRef(
    val title: String,
    val url: String,
    val site: String? = null,
    val snippet: String? = null,
    val date: String? = null,
)

data class TimelineEntry(
    val kind: String, // reasoning | tool | text
    val text: String = "",
    val name: String = "",
    val args: String = "",
    val ok: Boolean? = null,
    val running: Boolean = false,
    val sources: List<SourceRef> = emptyList(),
)

/** 从 web_search 的工具结果里提取来源（title/url/site）。 */
fun parseSources(raw: String): List<SourceRef> = runCatching {
    val root = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
    val arr = root["results"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    arr.mapNotNull { el ->
        val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val url = o["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val title = o["title"]?.jsonPrimitive?.content ?: url
        SourceRef(
            title = title,
            url = url,
            site = o["site"]?.jsonPrimitive?.content,
            snippet = o["snippet"]?.jsonPrimitive?.content ?: o["summary"]?.jsonPrimitive?.content,
            date = o["date"]?.jsonPrimitive?.content?.takeIf { it != "null" }?.take(10)?.replace("-", "/"),
        )
    }
}.getOrDefault(emptyList())

data class ChatContentState(
    val messages: List<ChatMessageUi> = emptyList(),
    val timelines: Map<Long, List<TimelineEntry>> = emptyMap(),
    val turnSources: Map<Long, List<SourceRef>> = emptyMap(),
    val processes: Map<Long, ChatProcessUi> = emptyMap(),
    val metas: Map<Long, TurnMetaUi> = emptyMap(),
    val reasoning: Map<Long, String> = emptyMap(),
)

/** 宽容取值：遇到对象/数组/null 一律当没有，绝不因为一条脏事件把整个会话渲染打断。 */
private fun JsonElement?.asContentOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.str(key: String): String? = this[key].asContentOrNull()
private fun JsonObject.long(key: String): Long? = this[key].asContentOrNull()?.toLongOrNull()

/** 事件日志 → 渲染模型（只做展示；不碰网络）。 */
fun parseChatContent(arr: JsonArray?): ChatContentState {
    val messages = mutableListOf<ChatMessageUi>()
    val process = linkedMapOf<Long, MutableList<ToolStepUi>>()
    val timelines = linkedMapOf<Long, MutableList<TimelineEntry>>()
    val turnSources = linkedMapOf<Long, MutableList<SourceRef>>()
    fun timeline(turn: Long) = timelines.getOrPut(turn) { mutableListOf() }
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
                    parentId = data?.str("parentId"),
                )
            }
            "assistant/message" -> {
                val reasoningText = data?.str("reasoning")
                if (!reasoningText.isNullOrBlank()) {
                    data?.long("turn")?.let { turn ->
                        reasoning[turn] = (reasoning[turn]?.plus("\n") ?: "") + reasoningText
                        timeline(turn) += TimelineEntry(kind = "reasoning", text = reasoningText)
                    }
                }
                val nodeForTimeline = data?.str("nodeId")
                val assistantText = data?.str("text").orEmpty()
                if (nodeForTimeline == null && assistantText.isNotBlank()) {
                    data?.long("turn")?.let { turn -> timeline(turn) += TimelineEntry(kind = "text", text = assistantText) }
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
                    parentId = data?.str("parentId"),
                )
            }
            "tool/call" -> {
                val turn = data?.long("turn") ?: return@forEach
                val toolName = data.str("name") ?: "tool"
                val toolArgs = data["args"]?.toString().orEmpty()
                process.getOrPut(turn) { mutableListOf() } +=
                    ToolStepUi(name = toolName, ok = null, text = "", args = toolArgs)
                timeline(turn) += TimelineEntry(kind = "tool", name = toolName, args = toolArgs, running = true)
            }
            "tool/result" -> {
                val turn = data?.long("turn") ?: return@forEach
                val list = process.getOrPut(turn) { mutableListOf() }
                val name = data.str("name") ?: "tool"
                val ok = data?.str("ok")?.let { it.equals("true", ignoreCase = true) } ?: false
                val text = data.str("text") ?: ""
                val sources = if (name == "web_search") parseSources(text) else emptyList()
                if (sources.isNotEmpty()) {
                    val agg = turnSources.getOrPut(turn) { mutableListOf() }
                    sources.forEach { s -> if (agg.none { it.url == s.url }) agg += s }
                }
                val idx = list.indexOfLast { it.name == name && it.ok == null }
                if (idx >= 0) list[idx] = list[idx].copy(ok = ok, text = text)
                else list += ToolStepUi(name, ok, text)
                val entries = timelines.getOrPut(turn) { mutableListOf() }
                val tIdx = entries.indexOfLast { it.kind == "tool" && it.name == name && it.running }
                if (tIdx >= 0) entries[tIdx] = entries[tIdx].copy(ok = ok, text = text, running = false, sources = sources)
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
        timelines = timelines.mapValues { (_, v) -> v.toList() },
        turnSources = turnSources.mapValues { (_, v) -> v.toList() },
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
                line.matches(Regex("^\\\\s*([-*_])(\\\\s*\\\\1){2,}\\\\s*$")) -> {
                    flushParagraph()
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
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
                    val url = text.substring(mid + 2, end).trim()
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append(label)
                        }
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

/** 工具循环里"中间步"的助手消息：不挂在会话树上（nodeId=null），内容已进过程时间线，不能当正文再渲染一次。 */
internal fun ChatMessageUi.isToolStepPlaceholder(): Boolean =
    role == "assistant" && nodeId == null &&
        (finishReason == "tool_calls" || finishReason == "tool_use")

/** 聊天区最终可见的消息 + 是否来自会话树分支（树分支内每个节点只会出现一次，无需再按版本去重）。 */
internal data class VisibleMessages(
    val messages: List<ChatMessageUi>,
    val fromTree: Boolean,
)

/**
 * 决定聊天区到底渲染哪些消息。
 *
 * 有会话树：只能渲染 activeLeaf 回溯出来的那一条分支。activeLeaf 失效（悬空/不属于本次快照）时
 * 退回"最新叶子"所在分支——绝不能退化成渲染全部消息，否则同一 turn 的中间步 + 正式回答会让
 * LazyColumn 出现重复 key（"Key ph-1 was already used"）直接闪退。
 * 无会话树（早期扁平会话）：渲染整列表，但剔除工具中间步。
 */
internal fun resolveVisibleMessages(all: List<ChatMessageUi>, activeLeaf: String?): VisibleMessages {
    val usable = all.filterNot { it.isToolStepPlaceholder() }
    if (usable.isEmpty()) return VisibleMessages(emptyList(), fromTree = false)
    val byId = LinkedHashMap<String, ChatMessageUi>()
    usable.forEach { m -> m.messageId?.let { byId[it] = m } }
    if (!usable.any { it.parentId != null }) {
        // 还没有树结构的旧会话：原样展示（messageId 可能为空，别拿它去重）
        return VisibleMessages(usable, fromTree = false)
    }
    fun chain(leaf: String?): List<ChatMessageUi> {
        if (leaf == null) return emptyList()
        val out = ArrayDeque<ChatMessageUi>()
        val seen = mutableSetOf<String>()
        var cursor: String? = leaf
        while (cursor != null && seen.add(cursor)) {
            val m = byId[cursor] ?: break
            out.addFirst(m)
            cursor = m.parentId
        }
        return out.toList()
    }
    val leaf = activeLeaf?.takeIf { byId.containsKey(it) } ?: derivedLeaf(byId.values)
    val path = chain(leaf)
    if (path.isNotEmpty()) return VisibleMessages(path, fromTree = true)
    // 连树也回溯不出来（数据异常）：退化为剔除中间步后的整列表，至少不崩
    return VisibleMessages(usable, fromTree = false)
}

/** 最新的叶子：没有任何消息把它当 parentId。列表按事件顺序，所以取最后一个。 */
private fun derivedLeaf(all: Collection<ChatMessageUi>): String? {
    val parents = all.mapNotNull { it.parentId }.toSet()
    return all.lastOrNull { it.messageId != null && it.messageId !in parents }?.messageId
        ?: all.lastOrNull { it.messageId != null }?.messageId
}

/** 聊天内容渲染：助手全宽无气泡、Markdown、版本切换、过程折叠、footer、操作条。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessages(
    state: ChatContentState,
    listState: LazyListState,
    streamingText: String?,
    streamingReasoning: String? = null,
    onCopy: (String) -> Unit,
    onRegenerate: (ChatMessageUi) -> Unit,
    onUserLongPress: (ChatMessageUi) -> Unit = {},
    initialVersions: Map<String, Int> = emptyMap(),
    onVersionSelected: (nodeId: String, version: Int) -> Unit = { _, _ -> },
    initialActiveLeaf: String? = null,
    onActiveLeafChanged: (leafId: String?) -> Unit = {},
    liveEntries: List<TimelineEntry> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var activeLeaf by remember { mutableStateOf(initialActiveLeaf) }
    androidx.compose.runtime.LaunchedEffect(initialActiveLeaf) {
        // 发送/重生成后 bridge 的 activeLeaf 会变化，必须同步，否则新回答不在路径上会"消失"
        if (initialActiveLeaf != null && initialActiveLeaf != activeLeaf) activeLeaf = initialActiveLeaf
    }
    val allMessages = state.messages
    fun deepestLeaf(startId: String): String {
        var cursor = startId
        val seen = mutableSetOf<String>()
        while (seen.add(cursor)) {
            val child = allMessages.lastOrNull { it.parentId == cursor && it.messageId != null } ?: return cursor
            cursor = child.messageId ?: return cursor
        }
        return cursor
    }
    val resolved = remember(state.messages, activeLeaf) {
        resolveVisibleMessages(state.messages, activeLeaf)
    }
    val visible = resolved.messages
    val activeVersions = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Int>().apply { putAll(initialVersions) }
    }
    androidx.compose.runtime.LaunchedEffect(activeLeaf, resolved) {
        resolved.messages.forEach { m ->
            if (m.role == "assistant" && m.nodeId != null) activeVersions[m.nodeId] = m.version
        }
    }
    val expandedProcess = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    val expandedTools = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val maxGroupHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.5f).coerceAtMost(400.dp)
    val nodes = state.messages.filter { it.role == "assistant" && it.nodeId != null }
        .groupBy { it.nodeId!! }
        .mapValues { (_, list) -> list.sortedBy { it.version } }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = MycoSpacing.l, vertical = MycoSpacing.m,
        ),
        verticalArrangement = Arrangement.spacedBy(MycoSpacing.s),
    ) {
        val renderedProcessTurns = mutableSetOf<Long>()
        visible.forEachIndexed { index, msg ->
            if (msg.role == "user") {
                item(key = "u-$index-${msg.messageId ?: ""}") {
                    ChatBubble(BubbleRole.USER, msg.text, onLongPress = { onUserLongPress(msg) })
                }
                return@forEachIndexed
            }
            // 工具中间步（无 nodeId）已在 resolveVisibleMessages 里剔除；这里再兜一层
            if (msg.isToolStepPlaceholder()) return@forEachIndexed
            val versions = msg.nodeId?.let { nodes[it] }.orEmpty()
            // 树分支：activeLeaf 已经选定版本，路径上每个节点只会出现一次
            // 旧扁平数据：按"当前选中/最大版本"去重，避免同一 nodeId 重复渲染
            val active = if (resolved.fromTree) msg.version
                else activeVersions[msg.nodeId] ?: versions.maxOfOrNull { it.version } ?: msg.version
            if (!resolved.fromTree && msg.nodeId != null && msg.version != active) return@forEachIndexed
            val shown = msg
            val turn = msg.turn
            val entries = turn?.let { state.timelines[it] }.orEmpty()
            // 同一 turn 的过程组只渲染一次，key 带 index 保证唯一（历史数据异常也不闪退）
            if (entries.isNotEmpty() && turn != null && renderedProcessTurns.add(turn)) {
                item(key = "ph-$index-$turn") {
                    TimelineHeaderRow(
                        entries = entries,
                        expanded = expandedProcess[turn] ?: false,
                        onToggle = { expandedProcess[turn] = expandedProcess[turn] != true },
                    )
                }
                item(key = "pb-$index-$turn") {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expandedProcess[turn] == true,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                    ) {
                        TimelineBody(
                            entries = entries,
                            keyPrefix = "pb-$turn",
                            expandedTools = expandedTools,
                            maxHeight = maxGroupHeight,
                            autoScroll = false,
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
                            val nodeId = msg.nodeId
                            if (nodeId != null) {
                                activeVersions[nodeId] = v
                                onVersionSelected(nodeId, v)
                                val target = nodes[nodeId]?.firstOrNull { it.version == v }
                                val leaf = target?.messageId?.let { deepestLeaf(it) }
                                activeLeaf = leaf
                                onActiveLeafChanged(leaf)
                            }
                        },
                    )
                    val meta = turn?.let { state.metas[it] }
                    if (meta != null) Footer(meta)
                    val sources = turn?.let { state.turnSources[it] }.orEmpty()
                    if (sources.isNotEmpty()) SourcesSection(sources)
                }
            }
        }
        if (liveEntries.isNotEmpty() || streamingText != null) {
            if (liveEntries.isNotEmpty()) {
                item(key = "live-ph") { TimelineHeaderRow(entries = liveEntries, expanded = true, onToggle = {}, live = true) }
                item(key = "live-pb") {
                    TimelineBody(
                        entries = liveEntries,
                        keyPrefix = "live",
                        expandedTools = expandedTools,
                        maxHeight = maxGroupHeight,
                        autoScroll = true,
                    )
                }
            }
            if (streamingText != null) {
                item(key = "streaming") {
                    Column(Modifier.fillMaxWidth()) { MarkdownText(streamingText + " ▍") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineBody(
    entries: List<TimelineEntry>,
    keyPrefix: String,
    expandedTools: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    maxHeight: androidx.compose.ui.unit.Dp,
    autoScroll: Boolean,
) {
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(entries.size) {
        if (autoScroll && entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }
    Box(
        Modifier.fillMaxWidth().heightIn(max = maxHeight)
            .pointerInput(entries.size, maxHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var handling: Boolean? = null
                    var lastY = down.position.y
                    val samples = mutableListOf<Pair<Long, Float>>()
                    samples += down.uptimeMillis to lastY
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        if (dy != 0f) {
                            if (handling == null) {
                                // 手指上滑=内容向下滚(forward)；下滑=backward
                                handling = if (dy < 0f) listState.canScrollForward else listState.canScrollBackward
                            }
                            if (handling == true) {
                                // 组体内部：自己分发，自己 fling，不交给外层
                                listState.dispatchRawDelta(-dy)
                                change.consume()
                                samples += change.uptimeMillis to lastY
                                if (samples.size > 8) samples.removeAt(0)
                            }
                            // handling == false：手势起点已在边界，不消费，完全交给外层原生滚动（流畅）
                        }
                    }
                    if (handling == true && samples.size >= 2) {
                        val first = samples.first()
                        val last = samples.last()
                        val dtMs = (last.first - first.first).coerceAtLeast(1L)
                        val velocityPxPerSec = (last.second - first.second) / (dtMs / 1000f)
                        scope.launch {
                            var speed = -velocityPxPerSec
                            var lastFrame = androidx.compose.runtime.withFrameNanos { it }
                            while (kotlin.math.abs(speed) > 80f) {
                                val frame = androidx.compose.runtime.withFrameNanos { it }
                                val dt = ((frame - lastFrame) / 1_000_000_000.0).coerceIn(0.008, 0.05)
                                lastFrame = frame
                                val moved = listState.dispatchRawDelta((speed * dt).toFloat())
                                if (kotlin.math.abs(moved) < 0.5f) break
                                speed = (speed * kotlin.math.exp(-4.5 * dt)).toFloat()
                            }
                        }
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { entryIndex, entry ->
                val key = "$keyPrefix-$entryIndex"
                if (entry.kind == "text") {
                    item(key = "rh-$key") { TimelineEntryContent(entry) }
                } else {
                    stickyHeader(key = "rh-$key") {
                        TimelineEntryHeader(
                            entry = entry,
                            expanded = expandedTools[key] == true,
                            onToggle = { expandedTools[key] = expandedTools[key] != true },
                        )
                    }
                    if (expandedTools[key] == true) {
                        item(key = "rc-$key") { TimelineEntryContent(entry) }
                    }
                }
            }
        }
    }
}

private class GroupBodyScrollIsolation(
    private val list: LazyListState,
) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    private var tracking = false
    private var startedAtEdge = false

    fun endGesture() {
        tracking = false
        startedAtEdge = false
    }

    override fun onPreScroll(
        available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ): androidx.compose.ui.geometry.Offset {
        if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && !tracking) {
            tracking = true
            startedAtEdge = when {
                available.y > 0f -> !list.canScrollBackward
                available.y < 0f -> !list.canScrollForward
                else -> false
            }
        }
        return androidx.compose.ui.geometry.Offset.Zero
    }

    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset,
        available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ): androidx.compose.ui.geometry.Offset {
        if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
            return androidx.compose.ui.geometry.Offset.Zero
        }
        // 手势开始时组体已在该方向边界：本次新手势允许滚外层
        if (startedAtEdge) return androidx.compose.ui.geometry.Offset.Zero
        // 同一次手势从内部滚到边界：撞墙，不把剩余位移交给外层
        return available
    }

    override suspend fun onPostFling(
        consumed: androidx.compose.ui.unit.Velocity,
        available: androidx.compose.ui.unit.Velocity,
    ): androidx.compose.ui.unit.Velocity {
        val allowParent = startedAtEdge
        endGesture()
        // 从内部滚出来的惯性一律不交给外层；新手势起于边界时才允许外层滚动
        return if (allowParent) androidx.compose.ui.unit.Velocity.Zero else available
    }
}

@Composable
private fun TimelineEntryHeader(entry: TimelineEntry, expanded: Boolean, onToggle: () -> Unit) {
    when (entry.kind) {
        "reasoning" -> {
            val preview = entry.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: ""
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                    .padding(horizontal = MycoSpacing.s, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (expanded) "▾ " else "▸ ") + "思考" + if (preview.isNotBlank()) " · $preview" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        "tool" -> {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                    .padding(horizontal = MycoSpacing.s, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (expanded) "▾ " else "▸ ") + entry.name + " · " + when {
                        entry.running -> "进行中…"
                        entry.ok == false -> "失败"
                        else -> "完成"
                    } + if (entry.sources.isNotEmpty()) " · 已阅读 ${entry.sources.size} 个网页" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        else -> TimelineEntryContent(entry)
    }
}

@Composable
private fun TimelineEntryContent(entry: TimelineEntry) {
    when (entry.kind) {
        "reasoning" -> {
            if (entry.text.isNotBlank()) {
                Text(
                    entry.text.take(6000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = MycoSpacing.m, bottom = 4.dp),
                )
            }
        }
        "tool" -> {
            val uriHandler = LocalUriHandler.current
            Column(Modifier.fillMaxWidth().padding(start = MycoSpacing.m, top = 2.dp, bottom = 4.dp)) {
                if (entry.args.isNotBlank() && entry.args != "{}") {
                    Text(
                        "参数：${entry.args.take(400)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (entry.name) {
                    "web_search" -> {
                        if (entry.sources.isNotEmpty()) {
                            Text(
                                "已阅读 ${entry.sources.size} 个网页",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            entry.sources.take(3).forEach { s ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(s.url) } }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SiteIcon(site = s.site, url = s.url, size = 16.dp)
                                    Text(
                                        "  " + s.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (entry.sources.size > 3) {
                                Text(
                                    "其余 ${entry.sources.size - 3} 个来源见回答下方",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (entry.text.isNotBlank()) {
                            Text(entry.text.take(1200), style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    "time_now" -> {
                        val display = runCatching {
                            val o = kotlinx.serialization.json.Json.parseToJsonElement(entry.text).jsonObject
                            o["local"]?.jsonPrimitive?.content ?: entry.text
                        }.getOrDefault(entry.text)
                        Text("当前时间：${display.take(200)}", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        if (entry.text.isNotBlank()) {
                            Text(
                                entry.text.take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        "text" -> {
            if (entry.text.isNotBlank()) {
                Text(
                    entry.text.take(1200),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(start = MycoSpacing.s, top = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineHeaderRow(
    entries: List<TimelineEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    live: Boolean = false,
) {
    val toolCount = entries.count { it.kind == "tool" }
    val runningTool = entries.any { it.kind == "tool" && it.running }
    val runningReasoning = entries.any { it.kind == "reasoning" && it.running }
    val categories = entries.filter { it.kind == "tool" }.map {
        when (it.name) {
            "web_search" -> "搜索网页"
            "read_url" -> "读取网页"
            "time_now" -> "读取时间"
            else -> "调用 ${it.name}"
        }
    }.distinct()
    val header = when {
        live && runningTool -> "正在使用工具…"
        live && runningReasoning -> "正在思考…"
        live -> "正在处理…"
        categories.isNotEmpty() -> "已" + categories.take(3).joinToString("、") +
            if (categories.size > 3) " 等" else ""
        else -> "思考过程"
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .clickable(onClick = onToggle)
            .padding(horizontal = MycoSpacing.s, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.bodySmall)
        Text(header, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimelineEntryRow(entry: TimelineEntry, expanded: Boolean, onToggle: () -> Unit) {
    when (entry.kind) {
        "reasoning" -> {
            if (entry.text.isNotBlank()) {
                val preview = entry.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: ""
                Column(Modifier.fillMaxWidth().padding(start = MycoSpacing.s)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (expanded) "▾ " else "▸ ") + "思考" + if (preview.isNotBlank()) " · $preview" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (expanded) {
                        Text(
                            entry.text.take(6000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = MycoSpacing.m, bottom = 4.dp),
                        )
                    }
                }
            }
        }
        "tool" -> {
            Column(Modifier.fillMaxWidth().padding(start = MycoSpacing.s)) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (expanded) "▾ " else "▸ ") + entry.name + " · " + when {
                            entry.running -> "进行中…"
                            entry.ok == false -> "失败"
                            else -> "完成"
                        } + if (entry.sources.isNotEmpty()) " · 已阅读 ${entry.sources.size} 个网页" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (expanded) {
                    if (entry.args.isNotBlank() && entry.args != "{}") {
                        Text(
                            "参数：${entry.args.take(300)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = MycoSpacing.m),
                        )
                    }
                    if (entry.sources.isEmpty() && entry.text.isNotBlank()) {
                        Text(
                            entry.text.take(1200),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = MycoSpacing.m),
                        )
                    }
                }
            }
        }
        "text" -> {
            if (entry.text.isNotBlank()) {
                Text(
                    entry.text.take(1200),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = MycoSpacing.s, top = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSection(sources: List<SourceRef>) {
    val uriHandler = LocalUriHandler.current
    var showSheet by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { showSheet = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("来源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sources.take(6).forEach { s -> SiteIcon(site = s.site, url = s.url, size = 18.dp) }
        if (sources.size > 6) {
            Text("+${sources.size - 6}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Text("${sources.size} 个网页 ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    if (showSheet) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MycoSpacing.l, vertical = MycoSpacing.s)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("来源 · ${sources.size} 个网页", style = MaterialTheme.typography.titleMedium)
                sources.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .clickable { runCatching { uriHandler.openUri(s.url) } }
                            .padding(horizontal = MycoSpacing.s, vertical = 8.dp),
                    ) {
                        SiteIcon(site = s.site, url = s.url, size = 22.dp)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                "${i + 1}. " + (s.site ?: SiteIcons.hostOf(s.url) ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!s.snippet.isNullOrBlank()) {
                                Text(
                                    s.snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!s.date.isNullOrBlank()) {
                                Text(s.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun TimelineFold(
    entries: List<TimelineEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    live: Boolean = false,
) {
    val toolCount = entries.count { it.kind == "tool" }
    val expandedTools = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    val runningTool = entries.any { it.kind == "tool" && it.running }
    val runningReasoning = entries.any { it.kind == "reasoning" && it.running }
    val categories = entries.filter { it.kind == "tool" }.map {
        when (it.name) {
            "web_search" -> "搜索网页"
            "read_url" -> "读取网页"
            "time_now" -> "读取时间"
            else -> "调用 ${it.name}"
        }
    }.distinct()
    val header = when {
        live && runningTool -> "正在使用工具…"
        live && runningReasoning -> "正在思考…"
        live -> "正在处理…"
        categories.isNotEmpty() -> "已" + categories.take(3).joinToString("、") +
            if (categories.size > 3) " 等" else ""
        else -> "思考过程"
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MycoRadius.control))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = MycoSpacing.m, vertical = MycoSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggle, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                header,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            entries.forEachIndexed { entryIndex, entry ->
                when (entry.kind) {
                    "reasoning" -> {
                        if (entry.text.isNotBlank()) {
                            Text(
                                "✳ 思考 · ${entry.text.take(2000)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                    "tool" -> {
                        val open = expandedTools[entryIndex] == true
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { expandedTools[entryIndex] = !open }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (open) "▾ " else "▸ ") + entry.name + " · " + when {
                                    entry.running -> "进行中…"
                                    entry.ok == false -> "失败"
                                    else -> "完成"
                                } + if (entry.sources.isNotEmpty()) " · 已阅读 ${entry.sources.size} 个网页" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (!open) {
                            // 收起态只显示一行摘要，详情点击展开
                        } else {
                        if (entry.args.isNotBlank() && entry.args != "{}") {
                            Text(
                                "参数：${entry.args.take(300)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = MycoSpacing.m),
                            )
                        }
                        if (entry.sources.isNotEmpty()) {
                            val uriHandler = LocalUriHandler.current
                            Column(Modifier.padding(start = MycoSpacing.m, top = 2.dp)) {
                                entry.sources.take(6).forEachIndexed { i, s ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                            .clickable { runCatching { uriHandler.openUri(s.url) } }
                                            .padding(vertical = 3.dp),
                                    ) {
                                        Text("${i + 1}. ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text(s.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                s.site ?: s.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (entry.text.isNotBlank()) {
                            Text(
                                entry.text.take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = MycoSpacing.m),
                            )
                        }
                        }
                    }
                    "text" -> {
                        if (entry.text.isNotBlank()) {
                            Text(
                                entry.text.take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessFold(
    turn: Long,
    process: ChatProcessUi?,
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
                if (process != null && process.steps.isNotEmpty()) "过程 · ${process.steps.size} 步" else "思考过程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (turn >= 0) {
                Text("turn $turn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            if (!reasoning.isNullOrBlank()) {
                Text(
                    "思考：${reasoning.take(1200)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MycoSpacing.xs),
                )
            }
            process?.steps?.forEach { step ->
                Text(
                    "· ${step.name} ${if (step.ok == false) "失败" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (step.args.isNotBlank()) {
                    Text(
                        "参数：${step.args.take(300)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = MycoSpacing.m),
                    )
                }
                if (step.text.isNotBlank()) {
                    Text(
                        "结果：${step.text.take(1200)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = MycoSpacing.m, top = 2.dp),
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





























































