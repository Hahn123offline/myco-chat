// 验收 ID：CHAT-28 CHAT-29 CHAT-30 CHAT-31 CHAT-32 CHAT-33 CHAT-48 CHAT-51 CHAT-62
//
// 消息操作条 + 版本切换器（`docs/spec/panels/chat.md` §3.16 `chat.msg.actions` /
// §3.17 `chat.msg.version-switcher`）。
//
// 硬约束：
//  * **常驻 + 只轮尾最后一条**（D-19=A / CHAT-28）：渲染条件由 model 层 `ActionsBarBlock` 决定，
//    本组件不做显隐切换、不做 hover/浮现（触摸端不可达，反证见 §3.16 字段 13）；
//  * 行高 40dp、每键 **40×40** 布局盒 = 命中盒、图标 20dp（G-16 密集图标档 / CHAT-29）；
//  * 生成中：复制可用，重新生成 / 版本切换 **禁用但可达**（`disabled()` + 原因 + 末尾「生成中…」，
//    不用原生 disabled 抹掉节点；点击零命令，CHAT-32）；
//  * 复制 = **真写系统剪贴板** + 1000ms 换勾 + 可调名「已复制」+ 读屏播报；失败有失败态（CHAT-30/31）；
//  * 版本：M=1 不渲染；端点禁用；计数可读「第 N 版，共 M 版」（CHAT-33）；
//  * 复用 base 原子 `IconAction`（40×40 / 图标 20 / disabled 语义 / busy / done 对勾），不重写按键。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.chat.model.ActionsBarBlock
import dev.myco.android.ui.m5.primitives.IconAction
import dev.myco.android.ui.m5.primitives.IconActionState
import kotlinx.coroutines.delay

/**
 * 操作条契约（§3.16 / §3.17，**纯数据 + 纯函数 / JVM 单测覆盖**：键序、尺寸、禁用规则、可读名）。
 *
 * 单测口径（CHAT-29/32/33）：`KEY_ORDER` 固定、`KEY_SIZE_DP=40`、`enabledWhileStreaming` 只有复制为 true、
 * 版本可读名是「第 N 版，共 M 版」。
 */
object MessageActionsContract {
    /** §3.16 字段 2：单行 40dp。 */
    const val BAR_HEIGHT_DP: Float = 40f

    /** §3.16 字段 2 / G-16：每键 40×40 布局盒（命中盒 = 布局盒）。 */
    const val KEY_SIZE_DP: Float = 40f

    /** §3.16 字段 2：图标 20dp。 */
    const val ICON_SIZE_DP: Float = 20f

    /** §3.16 字段 2：键间命中盒净距 8（视觉间距靠每键内边距，布局盒相邻不相交）。 */
    const val KEY_GAP_DP: Float = 8f

    /** §3.16 字段 5：复制成功换勾 1000ms。 */
    const val COPY_FEEDBACK_MS: Long = 1000L

    /** §3.16 字段 5 / 状态矩阵 9：生成中在操作条末尾给可见原因。 */
    const val GENERATING_HINT: String = "生成中…"

    /** §3.16 字段 2：键序固定（反馈槽预留但不渲染）。 */
    enum class Key { COPY, REGENERATE, VERSION }

    val KEY_ORDER: List<Key> = listOf(Key.COPY, Key.REGENERATE, Key.VERSION)

    /** §3.16 字段 5 / CHAT-32：生成中只有复制可用，其余 `aria-disabled` 且点击零命令。 */
    fun enabledWhileStreaming(key: Key): Boolean = key == Key.COPY

    /** §3.17 字段 2：计数 `‹N/M›` 的文本（等宽数字）。 */
    fun versionLabel(index: Int, total: Int): String = "$index/$total"

    /** §3.17 字段 11：计数可达名「第 N 版，共 M 版」（demo 无此名 = 缺陷，必须补）。 */
    fun versionDescription(index: Int, total: Int): String = "第 $index 版，共 $total 版"

    /** §3.16 字段 5：生成中可见原因（null = 不渲染）。 */
    fun visibleReason(streaming: Boolean): String? = if (streaming) GENERATING_HINT else null

    /** §3.16 字段 11：每键可调名（禁用时也保住可达名，CHAT-32）。 */
    fun keyDescription(key: Key): String = when (key) {
        Key.COPY -> "复制"
        Key.REGENERATE -> "重新生成"
        Key.VERSION -> "版本切换"
    }
}

/**
 * 消息操作条（§3.16）。
 *
 * @param block model 层的 `ActionsBarBlock`（已保证"只轮尾 + 有助手内容"）
 * @param copyText 复制目标的**原文**（消息全文，不是 UI 文本子集）
 * @param version 版本信息；`null` 或 `total<=1` → 不渲染切换器（CHAT-33）
 */
@Composable
internal fun MessageActionsBarUi(
    block: ActionsBarBlock,
    copyText: String,
    version: MessageVersion?,
    onCopied: (String) -> Unit,
    onRegenerate: () -> Unit,
    onVersionSwitch: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val streaming = block.streaming
    var copied by remember(block.key) { mutableStateOf(false) }
    var copyFailed by remember(block.key) { mutableStateOf(false) }

    // CHAT-31：1000ms 换勾后回位。
    LaunchedEffect(copied) {
        if (copied) {
            delay(MessageActionsContract.COPY_FEEDBACK_MS)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MessageActionsContract.BAR_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 1) 复制：常驻可用（生成中也可用，CHAT-32）。
        IconAction(
            action = IconActionState(
                id = "copy",
                contentDescription = when {
                    copyFailed -> "复制失败，点按重试"
                    copied -> "已复制"
                    else -> MessageActionsContract.keyDescription(MessageActionsContract.Key.COPY)
                },
                done = copied,
                icon = { tint -> M5Icon(icon = M5Icons.copy, tint = tint, size = MessageActionsContract.ICON_SIZE_DP.dp) },
            ),
            tint = colors.onSurfaceVariant,
            onAction = {
                val ok = runCatching {
                    // CHAT-30：真写系统剪贴板（旧稿只 Toast 不写 = BUG-001）。
                    clipboard.setText(AnnotatedString(copyText))
                }.isSuccess
                copied = ok
                copyFailed = !ok
                if (ok) onCopied(copyText)
            },
        )
        // 2) 重新生成：生成中 aria-disabled（可聚焦、可听出原因）。
        IconAction(
            action = IconActionState(
                id = "regenerate",
                contentDescription = MessageActionsContract.keyDescription(MessageActionsContract.Key.REGENERATE),
                // §3.16 字段 5：生成中禁用；block.regenerateEnabled == !streaming（与 KEY_ORDER 契约一致）。
                enabled = block.regenerateEnabled,
                icon = { tint -> M5Icon(icon = M5Icons.refresh, tint = tint, size = MessageActionsContract.ICON_SIZE_DP.dp) },
            ),
            tint = colors.onSurfaceVariant,
            onAction = { if (block.regenerateEnabled) onRegenerate() },
        )
        // 3) 版本切换器：M=1 不渲染。
        if (version != null && version.visible) {
            VersionSwitcherUi(
                version = version,
                enabled = block.versionSwitchEnabled,
                onSwitch = onVersionSwitch,
            )
        }
        // 4) 生成中可见原因（12sp secondary；视觉可见 + 读屏可听）。
        MessageActionsContract.visibleReason(streaming)?.let { reason ->
            Text(
                text = reason,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        // 5) 复制成功的读屏播报（视觉隐藏，出现即播报一次）。
        A11yAnnouncement(if (copied) "已复制" else null)
        A11yAnnouncement(if (copyFailed) "复制失败" else null)
    }
}

/**
 * 版本切换器 `‹N/M›`（§3.17）。
 *
 * 视觉高 28 / 布局盒 40×40 每箭头；计数 15/18 等宽、不可点；端点 40% + `disabled()`（CHAT-33）。
 * 用户消息挂载位复用同一组件（气泡下方右对齐行）。
 */
@Composable
internal fun VersionSwitcherUi(
    version: MessageVersion,
    enabled: Boolean,
    onSwitch: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconAction(
            action = IconActionState(
                id = "version-prev",
                contentDescription = "上一版",
                enabled = enabled && version.hasPrevious,
                icon = { tint -> M5Icon(icon = M5Icons.branchLeft, tint = tint, size = MessageActionsContract.ICON_SIZE_DP.dp) },
            ),
            tint = colors.onSurfaceVariant,
            onAction = { if (enabled && version.hasPrevious) onSwitch(version.index - 1) },
        )
        Text(
            text = MessageActionsContract.versionLabel(version.index, version.total),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 15.sp,
                lineHeight = 18.sp,
                // CHAT-33：计数等宽，避免 N/M 变化时宽度抖动。
                fontFeatureSettings = "tnum",
            ),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.semantics {
                contentDescription = MessageActionsContract.versionDescription(version.index, version.total)
            },
        )
        IconAction(
            action = IconActionState(
                id = "version-next",
                contentDescription = "下一版",
                enabled = enabled && version.hasNext,
                icon = { tint -> M5Icon(icon = M5Icons.branchRight, tint = tint, size = MessageActionsContract.ICON_SIZE_DP.dp) },
            ),
            tint = colors.onSurfaceVariant,
            onAction = { if (enabled && version.hasNext) onSwitch(version.index + 1) },
        )
    }
}

/**
 * 读屏一次性播报（G-08 同族）：1dp 视觉隐藏节点 + `liveRegion=Polite`，文本变化才播报。
 * `text == null` 时不渲染（零布局）。
 */
@Composable
internal fun A11yAnnouncement(text: String?) {
    if (text.isNullOrBlank()) return
    Box(
        Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}
