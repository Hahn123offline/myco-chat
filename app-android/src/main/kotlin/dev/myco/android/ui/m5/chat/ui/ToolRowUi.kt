// 验收 ID：CHAT-20 CHAT-21 CHAT-22 CHAT-23 CHAT-24 CHAT-25 CHAT-26 CHAT-27 CHAT-48
//
// 工具行 + 工具详情「井」（`docs/spec/panels/chat.md` §3.8 `chat.tool.row` / §3.9 `chat.tool.details`）。
//
// 硬约束：
//  * 行 = 32dp + Δ 布局盒、**整行可点**、横向 Bleed 8（G-16 过程行档 / CHAT-20）；
//    摘要 = **展示名 + 对象 basename**、单行 ellipsis、≤100 字符，**不显示完整命令/参数**
//    （工作区路径/URL 泄到首屏是反证点，CHAT-21）；
//  * 行高由 `heightIn(min = 32dp)` 定高：摘要变化不重排兄弟（G-02）；
//  * 井 = header 44dp + 内容体 `maxHeight 280dp` 内滚 + 井底条 44dp「查看全部 ›」
//    （`base.primitives.content-fold` 的 well 变体；CHAT-23/25）；
//  * 井内是**唯一纵向滚动层**；到边按 G-04 全量吸收（复用 [CappedInnerScroll] 的仲裁器，CHAT-24）；
//  * 全文页 = **独立 route**（`chat.tool.detail`，栈 push）：本文件只给「查看全部」回调与
//    `TODO(lead)`（`TimelineEntry` 无 `toolCallId`，route 参数等 core 补）。
//  * 未知工具走 JsonBlock 文本（无「切换原始 JSON」入口，CHAT-27；P2 §4-10）。
//
// 井在**流式组体内**的形态冲突（规格裁决点，`TODO(lead)`）：
//  §3.9 要求井内滚，而 §3.6/CHAT-17 规定流式期该轮只允许组体那一层内滚。本实现的取舍：
//  流式组体内展开的工具井**不建内滚**（内容随组体那唯一一层滚，井底条仍可见）；定稿/独立展开时
//  井走 280dp 封顶内滚。这样 CHAT-17（一层内滚）永不被破坏，代价是流式期井高不受 280 限制。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.chat.model.ToolRowState
import dev.myco.android.ui.m5.primitives.IconAction
import dev.myco.android.ui.m5.primitives.IconActionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 工具行摘要（§3.8 字段 10 / CHAT-21，**纯函数 / JVM 单测覆盖**）。
 *
 * 规则：
 *  * 展示名 = core 给的展示名；缺失 → fallback 原始工具名（`web_search` / `read_url` / `ocr_image` / 未知）；
 *  * 对象 = 参数里的 `url` / `query` / `path` / `file` 等，只取 **basename**；
 *  * `展示名 · basename`，空摘要省略分隔点；**去换行**、**≤100 字符**；**不含完整命令/参数**。
 */
object ToolRowText {
    /** §3.8 字段 10：摘要上限 100 字符（`tx/ToolStepHeader.kt:22-93` E0）。 */
    const val MAX_SUMMARY_CHARS = 100

    /** 对象参数键（按优先级；只取一个，绝不拼整个参数串）。 */
    private val OBJECT_KEYS = listOf("url", "query", "path", "file", "filename", "name", "pattern", "title")

    fun displayName(rawName: String): String = when (rawName) {
        "web_search" -> "搜索网页"
        "read_url" -> "读取网页"
        "ocr_image" -> "识别图片"
        "time_now" -> "读取时间"
        else -> rawName
    }

    /** 从 args JSON 里取"对象"原值（不保证 URL/路径）；取不到给空串。 */
    fun objectRaw(args: String): String {
        if (args.isBlank() || args == "{}") return ""
        return runCatching {
            val obj = Json.parseToJsonElement(args).jsonObject
            OBJECT_KEYS.firstNotNullOfOrNull { key ->
                (obj[key] as? JsonPrimitive)
                    ?.takeIf { it !is JsonNull }
                    ?.content
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull().orEmpty()
    }

    /** basename：URL 取域名；路径取最后一段；其余原值（去 query/fragment/尾部斜杠）。 */
    fun basename(value: String): String {
        val cleaned = value.substringBefore('?').substringBefore('#').trim().trimEnd('/')
        if (cleaned.isBlank()) return ""
        if (cleaned.contains("://")) return cleaned.substringAfter("://").substringBefore('/').ifBlank { cleaned }
        return cleaned.substringAfterLast('/').ifBlank { cleaned }
    }

    /** §3.8 字段 10：`展示名 · 对象 basename`；空摘要省略分隔点；去换行 + 截断 100。 */
    fun summary(entry: TimelineEntry): String {
        val name = displayName(entry.name).replace('\n', ' ').trim()
        val obj = basename(objectRaw(entry.args)).replace('\n', ' ').trim()
        val joined = if (obj.isBlank()) name else "$name · $obj"
        return joined.take(MAX_SUMMARY_CHARS)
    }

    /** 工具类型图标选择（视觉；无颜色含义）。 */
    fun iconKey(rawName: String): String = when (rawName) {
        "web_search" -> "search"
        "read_url" -> "globe"
        "ocr_image" -> "spark"
        "time_now" -> "clock"
        else -> "more"
    }
}

/**
 * 井内预览截断（§3.9 字段 2/10 / CHAT-25，**纯函数 / JVM 单测覆盖**）。
 *
 * 截断值：`web_search` 结果 ≤8 条、`read_url` 正文 ≤8 行、`ocr_image` 文本 ≤8 行、未知工具 ≤8 行。
 * 超长单行按字截断（[MAX_LINE_CHARS]）。**不静默截断**：返回隐藏行数供井底条播报。
 */
object ToolWellPreview {
    const val MAX_RESULTS = 8
    const val MAX_LINES = 8
    const val MAX_LINE_CHARS = 400

    data class Preview(
        val lines: List<String>,
        /** 被截掉的行数/条数（0 = 未截断）。 */
        val hidden: Int,
    ) {
        val hasMore: Boolean get() = hidden > 0
    }

    fun preview(entry: TimelineEntry): Preview = when {
        entry.sources.isNotEmpty() -> fromSources(entry.sources)
        entry.name == "web_search" -> fromSources(entry.sources)
        else -> fromText(entry.text)
    }

    /** web_search：每条来源一行（标题 — 域名），≤8 条。 */
    fun fromSources(sources: List<SourceRef>): Preview {
        val lines = sources.take(MAX_RESULTS).map { source ->
            val site = source.site ?: ToolRowText.basename(source.url).ifBlank { source.url }
            val title = source.title.replace('\n', ' ').trim().ifBlank { site }
            "$title — $site".take(MAX_LINE_CHARS)
        }
        return Preview(lines = lines, hidden = (sources.size - MAX_RESULTS).coerceAtLeast(0))
    }

    /** read_url / ocr_image / 未知工具：文本 ≤8 行，单行 ≤400 字；JSON 先美化（文本渲染，不建 json-tree）。 */
    fun fromText(raw: String): Preview {
        if (raw.isBlank()) return Preview(emptyList(), 0)
        val text = if (raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[")) {
            prettyJson(raw)
        } else {
            raw
        }
        val all = text.lines()
        val lines = all.take(MAX_LINES).map { it.take(MAX_LINE_CHARS) }
        return Preview(lines = lines, hidden = (all.size - MAX_LINES).coerceAtLeast(0))
    }

    /** 未知工具的 JSON 文本化（`Json { prettyPrint = true }`；不做树视图，P2 §4-10 / CHAT-27）。 */
    fun prettyJson(raw: String): String = runCatching {
        Json { prettyPrint = true }.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            Json.parseToJsonElement(raw),
        )
    }.getOrDefault(raw)
}

/**
 * 全文页截断契约（§3.9 字段 10 / CHAT-25，**纯函数 / JVM 单测覆盖**）。
 *
 * 头 **60** 行 + 尾 **140** 行、总 **16000** 字上限，中间显示「省略 N 行」（**不静默截断**）。
 * 全文页本身是独立 route（`chat.tool.detail`），`TODO(lead)`：等 core 补 `toolCallId` 后接栈。
 */
object ToolFullText {
    const val HEAD_LINES = 60
    const val TAIL_LINES = 140
    const val MAX_CHARS = 16000

    data class Truncated(
        val text: String,
        /** 中间省略的行数（0 = 未截断）。 */
        val hiddenLines: Int,
        val truncated: Boolean,
    )

    fun truncate(raw: String): Truncated {
        val lines = raw.lines()
        val needsLineTruncation = lines.size > HEAD_LINES + TAIL_LINES
        if (!needsLineTruncation) {
            if (raw.length <= MAX_CHARS) return Truncated(raw, hiddenLines = 0, truncated = false)
            // 只有字上限超了（例如超长单行）：按字符保住头尾，中间给显式截断标记。
            val headChars = MAX_CHARS * 2 / 3
            val marker = "\n…内容超长已截断…\n"
            val tailChars = (MAX_CHARS - marker.length - headChars).coerceAtLeast(0)
            val text = raw.take(headChars) + marker + raw.takeLast(tailChars)
            return Truncated(text.take(MAX_CHARS), hiddenLines = 0, truncated = true)
        }
        val hidden = (lines.size - HEAD_LINES - TAIL_LINES).coerceAtLeast(0)
        val head = lines.take(HEAD_LINES).joinToString("\n")
        val tail = lines.takeLast(TAIL_LINES).joinToString("\n")
        val marker = "\n…省略 $hidden 行…\n"
        var text = head + marker + tail
        if (text.length > MAX_CHARS) {
            // 行数已经很长的文件还要受 16000 字总上限约束：头保 2/3、尾保 1/3。
            val budget = (MAX_CHARS - marker.length).coerceAtLeast(0)
            val tailBudget = budget / 3
            val headBudget = budget - tailBudget
            text = head.take(headBudget) + marker + tail.takeLast(tailBudget)
        }
        return Truncated(text.take(MAX_CHARS), hidden, true)
    }
}

/** 工具行（§3.8）：折叠 32dp+Δ 单行，展开 = 下方 inline 井（§3.9）。 */
@Composable
internal fun ToolRowUi(
    entry: TimelineEntry,
    state: ToolRowState,
    expanded: Boolean,
    onToggle: () -> Unit,
    inCappedGroup: Boolean,
    onOpenDetail: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interactive = state != ToolRowState.PREPARING
    Column(Modifier.fillMaxWidth()) {
        // 整行可点：命中盒 = 列宽 + 2×8dp，布局盒 32dp+Δ（G-16 过程行档 / CHAT-20）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .processRowBleed()
                .heightIn(min = ChatListMetrics.PROCESS_ROW_BASE_DP.dp)
                .clickable(
                    enabled = interactive,
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起工具详情" else "展开工具详情",
                    onClick = onToggle,
                )
                .semantics {
                    role = Role.Button
                    stateDescription = toolStateDescription(state, expanded)
                    if (!interactive) {
                        // §3.8 字段 6：preparing 不可展开 —— 读屏也要听出原因（不用原生 disabled 隐藏）。
                        disabled()
                        contentDescription = "准备中，暂不可展开"
                    }
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                ToolTypeIcon(entry.name, state)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = ToolRowText.summary(entry),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = toolSummaryColor(state, colors.onSurfaceVariant, colors.error),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            ToolStatusTrailing(state)
        }
        if (expanded) {
            ToolWellUi(
                entry = entry,
                state = state,
                inCappedGroup = inCappedGroup,
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

/** 行尾状态（§3.8 字段 6 / CHAT-22）：running 转圈、failed「失败」、stopped 「已停止」、preparing「准备中」、succeeded 无。 */
@Composable
private fun ToolStatusTrailing(state: ToolRowState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        ToolRowState.RUNNING -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = colors.onSurfaceVariant,
            strokeWidth = 2.dp,
        )
        ToolRowState.FAILED -> Text("失败", style = MaterialTheme.typography.labelSmall, color = colors.error)
        ToolRowState.STOPPED -> Text("已停止", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
        ToolRowState.PREPARING -> Text("准备中", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        ToolRowState.SUCCEEDED -> Unit
    }
}

private fun toolSummaryColor(state: ToolRowState, normal: androidx.compose.ui.graphics.Color, error: androidx.compose.ui.graphics.Color) =
    if (state == ToolRowState.FAILED) error else normal

private fun toolStateDescription(state: ToolRowState, expanded: Boolean): String {
    val name = when (state) {
        ToolRowState.PREPARING -> "准备中"
        ToolRowState.RUNNING -> "运行中"
        ToolRowState.FAILED -> "失败"
        ToolRowState.STOPPED -> "已停止"
        ToolRowState.SUCCEEDED -> "完成"
    }
    return "$name，${if (expanded) "已展开" else "已折叠"}"
}

/** 工具类型图标（20dp，槽 24dp）。 */
@Composable
private fun ToolTypeIcon(name: String, state: ToolRowState) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val icon = when (ToolRowText.iconKey(name)) {
        "search" -> M5Icons.search
        "globe" -> M5Icons.globe
        "spark" -> M5Icons.spark
        "clock" -> M5Icons.clock
        else -> M5Icons.settings
    }
    M5Icon(icon = icon, tint = tint, size = 20.dp)
}

/**
 * 工具详情「井」（§3.9 字段 2/7/8）。
 *
 * header 44 + 内容体（封顶 280 / 流式组体内不封顶）+ 井底条 44「查看全部 ›」。
 * 井内**唯一纵向滚动**、到边 G-04 吸收（[CappedInnerScroll] 的仲裁器）。
 */
@Composable
private fun ToolWellUi(
    entry: TimelineEntry,
    state: ToolRowState,
    inCappedGroup: Boolean,
    onOpenDetail: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val preview = ToolWellPreview.preview(entry)
    val lineCount = preview.lines.size + preview.hidden
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        // header 44dp：工具名 + 复制键 40×40（G-16 密集图标档）。
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ToolRowText.displayName(entry.name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (state == ToolRowState.FAILED) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            WellCopyButton(text = wellCopyText(entry))
        }
        // 内容体：padding 16/12；纵向封顶 280（流式组体内不建内滚，见文件头裁决）。
        if (inCappedGroup) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                WellLines(preview, state)
            }
        } else {
            CappedInnerScroll(
                capDp = 280.dp,
                contentVersion = entry,
                showFade = false,
                followTail = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    WellLines(preview, state)
                }
            }
        }
        // 井底条 44dp：整条可点 → 全文页 route（CHAT-25；route 归上层，TODO(lead)）。
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "查看全部", onClick = onOpenDetail)
                .semantics {
                    role = Role.Button
                    contentDescription = "查看全部，共 $lineCount 行"
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "查看全部",
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier.weight(1f),
            )
            M5Icon(icon = M5Icons.chevronRight, tint = colors.primary, size = 16.dp)
        }
    }
}

@Composable
private fun WellLines(preview: ToolWellPreview.Preview, state: ToolRowState) {
    val colors = MaterialTheme.colorScheme
    if (preview.lines.isEmpty()) {
        Text("无输出", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        preview.lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (state == ToolRowState.FAILED) FontFamily.Monospace else FontFamily.Default,
                color = if (state == ToolRowState.FAILED) colors.error else colors.onSurface,
            )
        }
        if (preview.hidden > 0) {
            // 不静默截断：明示还有多少（§3.9 字段 10 / CHAT-25）。
            Text(
                text = "…还有 ${preview.hidden} 行",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WellCopyButton(text: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var done by remember { mutableStateOf(false) }
    IconAction(
        action = IconActionState(
            id = "copy-tool-output",
            contentDescription = if (done) "已复制" else "复制工具输出",
            done = done,
            icon = { tint -> M5Icon(icon = M5Icons.copy, tint = tint, size = 20.dp) },
        ),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        onAction = {
            // 真写系统剪贴板（失败静默不取：给出对勾/播报由 done 表达；异常时抛给调用方日志）。
            runCatching { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }
                .onSuccess { done = true }
        },
    )
    // 1000ms 后回位（与消息操作条同一反馈节奏，CHAT-31/CHAT-62）。
    LaunchedEffect(done) {
        if (done) {
            kotlinx.coroutines.delay(1000L)
            done = false
        }
    }
}

private fun wellCopyText(entry: TimelineEntry): String =
    if (entry.text.isNotBlank()) entry.text else ToolWellPreview.fromSources(entry.sources).lines.joinToString("\n")
