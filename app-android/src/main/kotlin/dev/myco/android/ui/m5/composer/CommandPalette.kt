// 验收 ID：CMP-48 CMP-49 CMP-50 CMP-51 CMP-52 CMP-53 CMP-54 CMP-55 CMP-56 CMP-57 CMP-77 CMP-78
//
// composer.candidates.menu / composer.candidates.row：`/` **本地静态命令**候选面板。
//
// Lead 裁定（P3-CMP-03，不再讨论）：
//  * 数据源 = **5 条纯 UI 动作命令**（`/new` `/model` `/settings` `/attach` `/clear`），客户端常量，
//    不依赖 core、不发任何请求（CMP-77）；
//  * **`@` 提及首版不做**：本文件没有任何 `@` 触发分支（CMP-78），行形态保留以便将来只换数据源；
//  * 组件照 combobox 实现：**前缀过滤 / 触摸上下滑选 / 无匹配整体关闭 / 焦点与 IME 留在草稿区**；
//    **无键盘支线**（D-09 触摸-only）：不绑 ↑↓ Enter Esc Tab（CMP-53）。
//
// 几何（§3.15 字段 2）：宽 = 卡宽；贴卡上沿外 6；行高 52；高 `min(5×52, A − cardH − 24)` 且 ≥1 行后内滚。
// 形态 C（overlay 浮层）**不占位、不改 H**（G-01 / CMP-52）：宿主把它画在座位 overlay 槽。
package dev.myco.android.ui.m5.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 5 条本地静态命令的 id（执行动作由接线方/本组件完成，**不进 core**）。 */
enum class ComposerCommandId {
    NEW,
    MODEL,
    SETTINGS,
    ATTACH,
    CLEAR,
}

/**
 * 一条本地命令。
 *
 * @param slashName 含 `/` 的命令名（用于前缀匹配）
 * @param label 行主名（去 `/`，如 `new`）
 * @param description 行右侧描述（12/16 secondary）
 * @param accessibilityLabel 读屏播报（"命令 <名>，<说明>"，§3.16 字段 11）
 */
data class ComposerCommand(
    val id: ComposerCommandId,
    val slashName: String,
    val label: String,
    val description: String,
) {
    val accessibilityLabel: String get() = "命令 $label，$description"
}

/** 首版命令清单：**恰好 5 条**，全部为 UI 动作、不发请求（CMP-77）。 */
object ComposerCommands {
    val ALL: List<ComposerCommand> = listOf(
        ComposerCommand(ComposerCommandId.NEW, "/new", "new", "新建会话"),
        ComposerCommand(ComposerCommandId.MODEL, "/model", "model", "打开模型与推理档位"),
        ComposerCommand(ComposerCommandId.SETTINGS, "/settings", "settings", "打开设置"),
        ComposerCommand(ComposerCommandId.ATTACH, "/attach", "attach", "打开附件面板"),
        ComposerCommand(ComposerCommandId.CLEAR, "/clear", "clear", "清空输入"),
    )

    fun byId(id: ComposerCommandId): ComposerCommand = ALL.first { it.id == id }
}

/**
 * 光标前正在输入的 `/` token。
 *
 * @param start token 起点（`/` 的位置）
 * @param end token 终点（光标后连续非空白字符的末端；选中命令时删除 `[start, end)`）
 * @param query `/` 之后到光标之前的文本（前缀过滤用）
 */
data class SlashToken(
    val start: Int,
    val end: Int,
    val query: String,
) {
    /** 删除时用的原 token 文本（手动关闭记忆的 key）。 */
    val raw: String get() = "/$query"
}

/** 候选面板可见性判定结果。 */
data class PaletteVisibility(
    val visible: Boolean,
    val token: SlashToken? = null,
    val commands: List<ComposerCommand> = emptyList(),
) {
    companion object {
        val HIDDEN = PaletteVisibility(false)
    }
}

/** 选中命令后的草稿变化（命令本身不往草稿里写文本，§3.16 字段 5）。 */
data class CommandSelection(
    val draft: String,
    val cursor: Int,
)

/** 手动关闭记忆：同一 token 不再自动弹，直到 token 被删除并重新输入。 */
data class CommandPaletteMemory(
    val dismissedToken: String? = null,
) {
    fun dismissFor(token: SlashToken): CommandPaletteMemory =
        copy(dismissedToken = token.raw)

    fun clear(): CommandPaletteMemory = copy(dismissedToken = null)
}

/**
 * 候选面板纯逻辑（可单测）。
 *
 * 出现条件（唯一判据，§3.15 字段 1）：光标前 token 是 `/`（行首或空白之后、query 内无空白）
 * **且**本地命令表里有前缀匹配。`@` 不触发。
 */
object CommandPaletteLogic {

    /**
     * 检测光标前的 `/` token；不满足返回 null。
     * **`@` 在这里没有任何分支**（首版不做提及，CMP-78）。
     */
    fun detectSlashToken(text: String, cursor: Int): SlashToken? {
        val safeCursor = cursor.coerceIn(0, text.length)
        if (safeCursor <= 0) return null
        var start = safeCursor
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        if (start >= safeCursor) return null
        if (text[start] != '/') return null
        // token 内（光标前）不能再出现 '/'；查询串无空白（由上面的扫描保证）
        val query = text.substring(start + 1, safeCursor)
        if (query.contains('/')) return null
        // token 终点向光标后扩展，避免"选中后残留半截命令"
        var end = safeCursor
        while (end < text.length && !text[end].isWhitespace()) end++
        return SlashToken(start = start, end = end, query = query)
    }

    /** 前缀过滤（大小写不敏感；空 query = 全 5 条）。 */
    fun filter(query: String): List<ComposerCommand> {
        if (query.isEmpty()) return ComposerCommands.ALL
        val prefix = "/${query.lowercase()}"
        return ComposerCommands.ALL.filter { it.slashName.lowercase().startsWith(prefix) }
    }

    /** 可见性：token 存在 + 有前缀匹配 + 未被手动关闭（无匹配 → 整体关闭，不留空壳 CMP-50）。 */
    fun visibility(
        text: String,
        cursor: Int,
        memory: CommandPaletteMemory = CommandPaletteMemory(),
    ): PaletteVisibility {
        val token = detectSlashToken(text, cursor) ?: return PaletteVisibility.HIDDEN
        if (memory.dismissedToken == token.raw) return PaletteVisibility.HIDDEN
        val commands = filter(token.query)
        if (commands.isEmpty()) return PaletteVisibility.HIDDEN
        return PaletteVisibility(visible = true, token = token, commands = commands)
    }

    /**
     * 选中命令：从草稿移除 `/query` token（**不写命令名进草稿**），光标落 token 起点。
     * 之后由调用方执行命令并关闭面板；**不自动发送**（选中 ≠ 提交，CMP-56）。
     */
    fun select(text: String, token: SlashToken, command: ComposerCommand): CommandSelection {
        val start = token.start.coerceIn(0, text.length)
        val end = token.end.coerceIn(start, text.length)
        val cleaned = text.removeRange(start, end)
        return CommandSelection(draft = cleaned, cursor = start.coerceAtMost(cleaned.length))
    }

    /** token 被删除/改写后清除"手动关闭"记忆（同 token 再输入会重新弹）。 */
    fun memoryAfterTextChange(memory: CommandPaletteMemory, text: String, cursor: Int): CommandPaletteMemory {
        val token = detectSlashToken(text, cursor) ?: return memory.clear()
        return if (token.raw == memory.dismissedToken) memory else memory.clear()
    }
}

/**
 * `/` 候选面板（overlay 浮层；**不占位**）。
 *
 * @param commands 过滤后的候选（空 = 不渲染，调用方也不应召唤）
 * @param onSelect 选中（调用方执行命令 → 关闭面板 → 焦点仍留在草稿区）
 * @param width 面板宽 = 卡宽（§3.15 字段 2）
 */
@Composable
fun CommandPalette(
    commands: List<ComposerCommand>,
    onSelect: (ComposerCommand) -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    if (commands.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .width(width)
            .heightIn(min = ComposerLayout.PALETTE_ROW_DP.dp)
            .height(height)
            .shadow(12.dp, shape)
            .background(colors.surfaceContainerHigh, shape)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = "命令候选，共 ${commands.size} 项" },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(commands, key = { _, item -> item.id.name }) { _, command ->
                CommandRow(command = command, onSelect = onSelect)
            }
        }
    }
}

/** 候选行：52dp 整行可点（布局盒 = 命中盒），无行内次级命中区、无选中高亮（D-09）。 */
@Composable
private fun CommandRow(
    command: ComposerCommand,
    onSelect: (ComposerCommand) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComposerLayout.PALETTE_ROW_DP.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = command.accessibilityLabel,
                onClick = { onSelect(command) },
            )
            .padding(horizontal = 12.dp)
            .semantics {
                role = Role.Button
                contentDescription = command.accessibilityLabel
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = command.label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = command.description,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
