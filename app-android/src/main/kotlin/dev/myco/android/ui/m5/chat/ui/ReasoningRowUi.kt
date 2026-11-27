// 验收 ID：CHAT-01 CHAT-02 CHAT-03 CHAT-04 CHAT-05 CHAT-17 CHAT-18 CHAT-19 CHAT-48
//          （CHAT-01…05 为思考行既有验收，沿用不改号）
//
// 思考行（`docs/spec/panels/chat.md` §3.7 `chat.msg.thinking-header` / G-02 / G-03 形态 D / G-05 / G-06 / G-08）。
//
// 形态 D 在本文件的落点（**同一时刻只允许一层纵向内滚**）：
//  * 定稿展开 = 形态 A：`disclosureStickyRow`（头部 `stickyHeader` 吸附 + 展开体随外层列表滚、
//    **不封顶、不加任何内层滚动**，G-03 形态 A / G-06 / CHAT-02 / CHAT-17）；
//  * 流式期且**独立渲染**（不在组体内）= 自身封顶内滚（cap = min(400dp, 0.5×AStable) / §1.6）；
//  * 流式期且**在组体内** = 展开体只有内容，**不再建第二层滚动**（组体就是那一层，§3.7 字段 7 / CHAT-17）。
//
// 复用基座原子 `DisclosureRow` / `disclosureStickyRow`（32dp 定高 / 整行即开关 / 横向 Bleed 8 /
// stateDescription / G-08 读屏 / G-06 sticky），**不重写披露行**。
//
// 已知偏离（登记 `TODO(lead)`，均属 base 原子缺能力，不在本面板重写）：
//  1. G-05「流式摘要右端 48dp 渐隐、不用省略号」：原子的 summary 仍是 ellipsis（base.primitives 自己
//     也留了 TODO：需要 TextLayoutResult + shader）；
//  2. 展开体左缩进原子固定 32dp（= 图标 24 + gap 8），spec §3.7 要 22dp（= 16 + 6）；
//  3. live 摘要 shimmer 未做（原子无 shimmer token）。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.MarkdownText
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.primitives.DisclosureHeader
import dev.myco.android.ui.m5.primitives.DisclosureRow

/** 思考行标题（金标准：固定「思考」）。 */
internal const val REASONING_TITLE = "思考"

/** 运行态读屏文案（G-08：视觉隐藏、出现即播报一次）。 */
internal const val REASONING_RUNNING_LABEL = "正在思考"

/**
 * 思考行摘要（§3.7 字段 10 / Q-02，**纯函数 / JVM 单测覆盖** CHAT-04）。
 *
 * - `running`（流式中）= **最后完成段落首个非空行**（最后一段可能还没写完，跳过它）；
 * - `settled`（定稿）= **首行**；
 * - 两者都删掉 `**`（Q-02）；空摘要 → 调用方隐藏点+摘要（本函数返回空串）。
 */
object ReasoningSummary {

    fun of(text: String, running: Boolean): String {
        if (text.isBlank()) return ""
        val source = if (running) lastCompletedParagraph(text) else text
        val firstLine = source
            .lineSequence()
            .map { stripInlineMarkers(it).trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return ""
        return firstLine
    }

    /** Q-02：摘要里不出现 `**`（以及行内代码反引号、标题井号）。 */
    fun stripInlineMarkers(line: String): String {
        var s = line.replace("**", "")
        s = s.replace("`", "")
        return s.replace(Regex("^#{1,6}\\s*"), "").trimStart()
    }

    /**
     * 最后一个**已完成**段落（流式中最后一段还在长）。
     * 判定：文本末尾没有换行 → 末段未完成，取它之前最后一个非空段。
     */
    internal fun lastCompletedParagraph(text: String): String {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        if (text.endsWith("\n") || text.endsWith("\r\n")) {
            return paragraphs.lastOrNull { it.isNotBlank() } ?: text
        }
        return paragraphs.dropLast(1).lastOrNull { it.isNotBlank() } ?: paragraphs.lastOrNull().orEmpty()
    }
}

/**
 * 思考行（非 sticky 版）：折叠 = 32dp 定高行；展开 = inline 展开体。
 *
 * 三种使用场景（由调用方选）：
 *  * 组内流式成员：`inCappedGroup = true` → 展开体不加内滚（组体承担）；
 *  * 独立流式渲染：`inCappedGroup = false && running = true` → 展开体自身封顶内滚（cap）；
 *  * 定稿但**不要求 sticky**（如组内成员的非 sticky 分支/兜底）：`running = false` → 不封顶、
 *    无内滚（形态 A）。需要 sticky 头定稿态请用 [reasoningStickyItems]。
 */
@Composable
internal fun ReasoningRowUi(
    text: String,
    running: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    inCappedGroup: Boolean,
    capDp: Dp,
    contentVersion: Any?,
) {
    // §3.7 字段 1：reasoning 文本 trim 后为空 → 整行不渲染（不产生空行推挤）。
    if (text.isBlank()) return
    val summary = ReasoningSummary.of(text, running)
    DisclosureRow(
        title = REASONING_TITLE,
        expanded = expanded,
        onToggle = onToggle,
        icon = { ReasoningIcon() },
        summary = summary.ifBlank { null },
        running = running,
        runningLabel = REASONING_RUNNING_LABEL,
    ) {
        when {
            // 形态 D 的 B 分支：流式 + 独立渲染 → 自身封顶内滚（唯一一层）。
            running && !inCappedGroup -> CappedInnerScroll(
                capDp = capDp,
                contentVersion = contentVersion,
            ) { 
                ReasoningBodyUi(text)
            }
            // 形态 A / 组内：不封顶、无内滚（由外层列表或组体承担那一层）。
            else -> ReasoningBodyUi(text)
        }
    }
}

/** 定稿展开的思考行：**头部走 `stickyHeader`**（G-06 / CHAT-02），展开体不封顶（CHAT-17）。 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.reasoningStickyItems(
    itemKey: String,
    text: String,
    running: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerBackground: Color,
    bodyKey: String = "$itemKey/body",
) {
    if (text.isBlank()) return
    stickyHeader(key = itemKey) {
        ChatStickyContent(background = headerBackground) {
            ReasoningHeaderUi(
                text = text,
                running = running,
                expanded = expanded,
                onToggle = onToggle,
                background = headerBackground,
            )
        }
    }
    if (expanded) {
        item(key = bodyKey) {
            ChatContentColumn { ReasoningBodyUi(text) }
        }
    }
}

/**
 * 思考行头部（披露行本体）：复用基座 `DisclosureHeader`
 * （32dp 定高 / 整行即开关 / 横向 Bleed 8 / `stateDescription` / G-08 运行态读屏）。
 */
@Composable
internal fun ReasoningHeaderUi(
    text: String,
    running: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    background: Color = Color.Transparent,
) {
    val summary = ReasoningSummary.of(text, running)
    DisclosureHeader(
        title = REASONING_TITLE,
        expanded = expanded,
        onToggle = onToggle,
        icon = { ReasoningIcon() },
        summary = summary.ifBlank { null },
        running = running,
        runningLabel = REASONING_RUNNING_LABEL,
        headerBackground = background,
    )
}

/** 展开体正文：复用 `ui/ChatContent.kt` 的 `MarkdownText`（**不复制一份**）。 */
@Composable
internal fun ReasoningBodyUi(text: String) {
    if (text.isBlank()) return
    MarkdownText(text.trim())
}

/** leading 图标：思考（时钟）。走 M5Icon 自绘矢量，不写死颜色（tint 由原子给）。 */
@Composable
private fun ReasoningIcon() {
    M5Icon(
        icon = M5Icons.clock,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        size = 16.dp,
    )
}
