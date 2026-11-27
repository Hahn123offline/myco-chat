package dev.myco.android.ui.m5

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.ChatContentState
import dev.myco.android.ui.MarkdownText
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.model.ChatMessageUi
import dev.myco.android.ui.model.TurnMetaUi
import dev.myco.android.ui.resolveVisibleMessages
import dev.myco.android.ui.userVersionGroups

/** 真实聊天页的 M5 顶栏：玻璃浮条（菜单 + 标题/状态）。 */
@Composable
fun M5ChatTopBarReal(title: String, subtitle: String, onMenu: (() -> Unit)?) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeoSurface(
                modifier = Modifier.size(36.dp),
                radius = 18.dp,
                material = NeoMaterial.Raised,
                onClick = onMenu ?: {},
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    M5Icon(M5Icons.menu, Modifier.size(17.dp), p.text)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = M5Type.title, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = M5Type.caption, color = p.textTertiary)
            }
        }
    }
}

/**
 * 真实数据的 M5 聊天列表（与 ChatMessages 同签名，可整块切换）。
 *
 * 已接：会话树可见路径、用户分支切换、思考过程组（文字点击）、Markdown 正文、meta、流式。
 * **暂未接（设计缺口，不静默）**：
 * 1. 单条消息操作（复制/重新生成）与用户长按菜单的 M5 视觉还没做；
 * 2. 过程组没有内层滚动隔离/分组折叠，长 reasoning 会整段展开；
 * 3. Markdown 代码块/表格仍走 M1 颜色；
 * 4. 分支切换没有锚点修正（沿用旧实现）。
 */
@Composable
fun ChatMessagesM5(
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
    autoExpandTurn: Long? = null,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        var activeLeaf by remember { mutableStateOf(initialActiveLeaf) }
        LaunchedEffect(initialActiveLeaf) {
            if (initialActiveLeaf != null && initialActiveLeaf != activeLeaf) activeLeaf = initialActiveLeaf
        }
        val allMessages = state.messages
        val resolved = remember(state.messages, activeLeaf) {
            resolveVisibleMessages(state.messages, activeLeaf)
        }
        val userVersions = remember(state.messages) { userVersionGroups(state.messages) }
        val expandedProcess = remember { mutableStateMapOf<Long, Boolean>() }
        val expandedLines = remember { mutableStateMapOf<String, Boolean>() }
        LaunchedEffect(autoExpandTurn) {
            if (autoExpandTurn != null) expandedProcess[autoExpandTurn] = true
        }

        fun deepestLeaf(startId: String): String {
            var cursor = startId
            val seen = mutableSetOf<String>()
            while (seen.add(cursor)) {
                val child = allMessages.lastOrNull { it.parentId == cursor && it.messageId != null } ?: return cursor
                cursor = child.messageId ?: return cursor
            }
            return cursor
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val renderedTurns = mutableSetOf<Long>()
            resolved.messages.forEachIndexed { index, msg ->
                if (msg.role == "user") {
                    val siblings = msg.messageId?.let { userVersions[it] }.orEmpty()
                    item(key = "u-$index-${msg.messageId ?: ""}") {
                        Column(Modifier.fillMaxWidth()) {
                            UserBubbleM5(msg.text) { onUserLongPress(msg) }
                            if (siblings.size > 1) {
                                UserBranchBarM5(
                                    versions = siblings,
                                    active = msg,
                                    onSwitch = { target ->
                                        val leaf = target.messageId?.let { deepestLeaf(it) }
                                        if (leaf != null) {
                                            activeLeaf = leaf
                                            onActiveLeafChanged(leaf)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    return@forEachIndexed
                }
                val turn = msg.turn
                val entries = turn?.let { state.timelines[it] }.orEmpty()
                if (entries.isNotEmpty() && turn != null && renderedTurns.add(turn)) {
                    item(key = "th-$index-$turn") {
                        ThinkingHeaderM5(
                            entries = entries,
                            expanded = expandedProcess[turn] ?: false,
                            live = false,
                            onToggle = { expandedProcess[turn] = expandedProcess[turn] != true },
                        )
                    }
                    item(key = "tb-$index-$turn") {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = expandedProcess[turn] == true,
                            enter = androidx.compose.animation.expandVertically(
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                                clip = true,
                            ),
                            exit = androidx.compose.animation.shrinkVertically(
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
                                clip = true,
                            ),
                        ) {
                            val groupMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.45f)
                                .coerceAtMost(360f).dp
                            val groupScroll = rememberScrollState()
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = groupMax)
                                    .verticalScroll(groupScroll)
                                    .padding(start = 20.dp, top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                entries.forEachIndexed { i, e ->
                                    val key = "p-$turn-$i"
                                    ThinkingLineM5(e, expandedLines[key] == true) {
                                        expandedLines[key] = expandedLines[key] != true
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "a-$index-${msg.messageId ?: ""}") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MarkdownText(msg.text)
                        val meta = turn?.let { state.metas[it] }
                        if (meta != null) MetaLineM5(meta)
                        AssistantActionsM5(
                            onCopy = { onCopy(msg.text) },
                            onRegenerate = { onRegenerate(msg) },
                        )
                    }
                }
            }
            if (streamingText != null || liveEntries.isNotEmpty()) {
                item(key = "stream") {
                    StreamingTurnM5(liveEntries = liveEntries, text = streamingText)
                }
            }
            item(key = "tail") { Spacer(Modifier.size(2.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubbleM5(text: String, onLongPress: () -> Unit) {
    val p = LocalM5.current.palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        NeoSurface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
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
private fun UserBranchBarM5(
    versions: List<ChatMessageUi>,
    active: ChatMessageUi,
    onSwitch: (ChatMessageUi) -> Unit,
) {
    val p = LocalM5.current.palette
    val idx = versions.indexOfFirst { it.messageId == active.messageId }.coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().padding(end = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        M5Icon(
            M5Icons.branchLeft,
            Modifier.size(16.dp).clickable { if (idx > 0) onSwitch(versions[idx - 1]) },
            if (idx > 0) p.textSecondary else p.textTertiary,
        )
        Spacer(Modifier.width(6.dp))
        Text("${idx + 1} / ${versions.size}", style = M5Type.caption, color = p.textTertiary)
        Spacer(Modifier.width(6.dp))
        M5Icon(
            M5Icons.branchRight,
            Modifier.size(16.dp).clickable { if (idx < versions.size - 1) onSwitch(versions[idx + 1]) },
            if (idx < versions.size - 1) p.textSecondary else p.textTertiary,
        )
    }
}

@Composable
private fun ThinkingHeaderM5(
    entries: List<TimelineEntry>,
    expanded: Boolean,
    live: Boolean,
    onToggle: () -> Unit,
) {
    val p = LocalM5.current.palette
    val last = entries.lastOrNull()
    val summary = when {
        last == null -> if (live) "思考中…" else "过程"
        last.kind == "reasoning" ->
            last.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(48) ?: "思考"
        last.kind == "tool" ->
            (if (last.running) "运行中 · " else "") + last.name + (if (last.ok == false) "（失败）" else "")
        else -> last.text.lineSequence().firstOrNull()?.trim()?.take(48)?.ifBlank { "写入" } ?: "写入"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        M5Icon(if (expanded) M5Icons.chevronDown else M5Icons.chevronRight, Modifier.size(13.dp), p.textTertiary)
        Spacer(Modifier.width(7.dp))
        if (live) {
            PulseDot(p.moss)
            Spacer(Modifier.width(6.dp))
        }
        Text(summary, style = M5Type.caption, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PulseDot(color: Color) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "think-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "think-pulse-alpha",
    )
    Box(
        Modifier.size(5.dp).clip(SuperellipseShape(2.5.dp)).background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun ThinkingLineM5(entry: TimelineEntry, expanded: Boolean, onToggle: () -> Unit) {
    val p = LocalM5.current.palette
    val icon: M5IconSpec
    val title: String
    val detail: String
    when (entry.kind) {
        "reasoning" -> {
            icon = M5Icons.spark
            title = "思考"
            detail = entry.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
        "tool" -> {
            icon = M5Icons.chevronRight
            title = when {
                entry.running -> "运行中"
                entry.ok == false -> "失败"
                else -> "运行命令"
            }
            detail = entry.name + if (entry.args.isNotBlank() && entry.args != "{}") " · ${entry.args.take(80)}" else ""
        }
        else -> {
            icon = M5Icons.copy
            title = "写入"
            detail = entry.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Spacer(Modifier.width(6.dp))
            M5Icon(
                if (expanded) M5Icons.chevronDown else M5Icons.chevronRight,
                Modifier.size(11.dp),
                p.textTertiary,
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                clip = true,
            ),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
                clip = true,
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (entry.kind == "tool") {
                    val prettyArgs = prettyJson(entry.args)
                    if (prettyArgs.isNotBlank()) {
                        DetailBlock("参数") { DetailText(prettyArgs) }
                    }
                    if (entry.sources.isNotEmpty()) {
                        DetailBlock("来源") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                entry.sources.take(6).forEachIndexed { i, s ->
                                    Text(
                                        "${i + 1}. ${s.title ?: s.url}",
                                        style = M5Type.caption,
                                        color = p.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        s.site ?: s.url,
                                        style = M5Type.caption,
                                        color = p.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (entry.text.isNotBlank()) {
                        DetailBlock("结果") { DetailText(entry.text) }
                    }
                } else {
                    // 思考/写入内容不包块：直接是正文式文本（旧展示习惯）
                    val scroll = rememberScrollState()
                    Text(
                        entry.text.trim().ifBlank { "（无内容）" }.take(4000),
                        style = M5Type.caption,
                        color = p.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(scroll),
                    )
                }
            }
        }
    }
}

/** 工具详情用凹陷内部组块包起来，不再是一坨裸 JSON。 */
@Composable
private fun DetailBlock(label: String?, content: @Composable () -> Unit) {
    val p = LocalM5.current.palette
    NeoSurface(
        modifier = Modifier.fillMaxWidth(),
        radius = M5Radius.inset,
        material = NeoMaterial.Inset,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (label != null) {
                Text(label, style = M5Type.caption, color = p.textTertiary)
            }
            content()
        }
    }
}

@Composable
private fun DetailText(text: String) {
    val p = LocalM5.current.palette
    val scroll = rememberScrollState()
    Text(
        text.take(4000),
        style = M5Type.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        color = p.textSecondary,
        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(scroll),
    )
}

private fun prettyJson(raw: String): String {
    if (raw.isBlank() || raw == "{}" || raw == "null") return ""
    return runCatching {
        val el = Json.parseToJsonElement(raw)
        Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), el)
    }.getOrElse { raw }
}
@Composable
private fun MetaLineM5(meta: TurnMetaUi) {
    val p = LocalM5.current.palette
    val secs = meta.durationMs?.let { "%.1fs".format(it / 1000.0) }
    val status = when (meta.status) {
        "stopped" -> "已停止"
        "error" -> "出错"
        "truncated" -> "被截断"
        else -> null
    }
    Text(
        listOfNotNull(secs, "${meta.steps} 步", status).joinToString(" · "),
        style = M5Type.caption,
        color = p.textTertiary,
    )
}

@Composable
private fun StreamingTurnM5(liveEntries: List<TimelineEntry>, text: String?) {
    val p = LocalM5.current.palette
    var liveExpanded by remember { mutableStateOf(false) }
    val lineExpanded = remember { mutableStateMapOf<Int, Boolean>() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (liveEntries.isNotEmpty()) {
            ThinkingHeaderM5(
                entries = liveEntries,
                expanded = liveExpanded,
                live = true,
                onToggle = { liveExpanded = !liveExpanded },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = liveExpanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    clip = true,
                ),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
                    clip = true,
                ),
            ) {
                val groupMax = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.45f)
                    .coerceAtMost(360f).dp
                val groupScroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = groupMax)
                        .verticalScroll(groupScroll)
                        .padding(start = 20.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    liveEntries.forEachIndexed { i, e ->
                        ThinkingLineM5(e, lineExpanded[i] == true) {
                            lineExpanded[i] = lineExpanded[i] != true
                        }
                    }
                }
            }
        }
        if (text != null) {
            MarkdownText(text)
            Box(
                Modifier.padding(top = 4.dp).size(width = 3.dp, height = 18.dp)
                    .clip(SuperellipseShape(1.5.dp)).background(p.moss),
            )
        }
    }
}

@Composable
private fun AssistantActionsM5(onCopy: () -> Unit, onRegenerate: () -> Unit) {
    val p = LocalM5.current.palette
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        M5Icon(M5Icons.copy, Modifier.size(15.dp).clickable { onCopy() }, p.textTertiary)
        M5Icon(M5Icons.refresh, Modifier.size(15.dp).clickable { onRegenerate() }, p.textTertiary)
    }
}

@Composable
fun M5BackToLatest(onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            NeoSurface(
                radius = 16.dp,
                material = NeoMaterial.Raised,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = onClick,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    M5Icon(M5Icons.chevronDown, Modifier.size(13.dp), p.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text("回到最新", style = M5Type.caption, color = p.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun StreamingTextM5(text: String) {
    val p = LocalM5.current.palette
    Column(Modifier.fillMaxWidth()) {
        MarkdownText(text)
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(width = 3.dp, height = 18.dp)
                .clip(SuperellipseShape(1.5.dp))
                .background(p.moss),
        )
    }
}

/** 输入区上方的「模型 · 思考强度」胶囊：设置配好，这里一键调整。 */
@Composable
fun M5ModelBar(modelLabel: String, reasoningLabel: String?, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            NeoSurface(
                radius = 15.dp,
                material = NeoMaterial.Raised,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                onClick = onClick,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    M5Icon(M5Icons.spark, Modifier.size(12.dp), p.textTertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(modelLabel, style = M5Type.caption, color = p.textSecondary)
                    if (!reasoningLabel.isNullOrBlank()) {
                        Text(" · ", style = M5Type.caption, color = p.textTertiary)
                        Text(reasoningLabel, style = M5Type.caption, color = p.textTertiary)
                    }
                    Spacer(Modifier.width(4.dp))
                    M5Icon(M5Icons.chevronDown, Modifier.size(11.dp), p.textTertiary)
                }
            }
        }
    }
}