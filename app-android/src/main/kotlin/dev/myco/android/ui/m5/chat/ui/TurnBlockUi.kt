// 验收 ID：CHAT-06 CHAT-11 CHAT-17 CHAT-19 CHAT-28 CHAT-30 CHAT-34 CHAT-41 CHAT-42 CHAT-43
//          CHAT-47 CHAT-54 CHAT-55 CHAT-59 CHAT-67 CHAT-68
//
// 注：正文 Markdown 的内部渲染（CHAT-60/61/64/65 的代码块/表格/链接白名单/半截块）与 F1 列表内行内代码
// 由 `ui/ChatContent.kt` 现有 `MarkdownText` 承担（本文件只复用，不重写），故不在此认领那些 ID。
//
// chat 各 block 类型的渲染分发（`docs/spec/panels/chat.md` §1.4 轮内顺序表 13 行 / §1.5 间距）。
//
// 一个 `TurnBlock` = 一个（或一组）lazy item；**key 就是 model 层给的稳定 key**（CHAT-57：
// 流式追加 token 时已渲染块 key 不变、不重建）。分发规则见 [turnBlockItems]。
//
// 硬约束：
//  * 唯一外层纵向滚动 = `ChatListM5` 的 LazyColumn；本文件**不引入第二个纵向懒列表**
//    （CHAT-58）；只有流式过程组体（ProcessGroupUi）与工具井有单层内滚；
//  * 定稿展开的过程组头 / 思考行头走 `stickyHeader`（G-06 / CHAT-11）；
//  * 正文复用 `ui/ChatContent.kt` 的 `MarkdownText`（不复制第二份 Markdown 渲染器）；
//  * 流式 Markdown 节流：≥100ms 且按上次渲染耗时自适应（[MarkdownStreamThrottle]，CHAT-59），
//    禁止每帧重排整条消息（CHAT-42）。
//
// 已知缺口（登记 `TODO(lead)`，不猜）：
//  1. `ui/ChatContent.kt` 的 `SourcesSection` 是 `private`（同一文件里还有 `private fun CodeBlock`），
//     本面板**无法复用**；来源行 + 来源 sheet 由本文件按 §3.20/§3.21 重画（仅"行 + 卡片"，
//     没有复制旧实现内容）。若 Lead 愿意把 `SourcesSection` 提成 public，可直接换回。
//  2. 流式光标（§3.11 字段 6）放在正文下方独立一行（2×18dp），不是"插在最后 glyph 之后"，
//     属已知偏离（Compose 内联光标需要 TextLayoutResult 自绘，未取证）。
//  3. 用户气泡上方的**附件条槽位**需要 `attachment` 面板的渲染器，本文件只留空位
//     （`ChatMessageUi` 里也没有结构化附件字段）。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.MarkdownText
import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.chat.model.ActionsBarBlock
import dev.myco.android.ui.m5.chat.model.GroupHeaderBlock
import dev.myco.android.ui.m5.chat.model.GroupMemberBlock
import dev.myco.android.ui.m5.chat.model.MaxTokensBlock
import dev.myco.android.ui.m5.chat.model.ProcessControlBlock
import dev.myco.android.ui.m5.chat.model.ProcessEntryKind
import dev.myco.android.ui.m5.chat.model.ReplyInlineReasoningBlock
import dev.myco.android.ui.m5.chat.model.ReplyMarkdownBlock
import dev.myco.android.ui.m5.chat.model.SourcesRowBlock
import dev.myco.android.ui.m5.chat.model.StreamStatusBlock
import dev.myco.android.ui.m5.chat.model.ToolRowState
import dev.myco.android.ui.m5.chat.model.TurnBlock
import dev.myco.android.ui.m5.chat.model.TurnErrorBlock
import dev.myco.android.ui.m5.chat.model.TurnPhase
import dev.myco.android.ui.m5.chat.model.TurnStatus
import dev.myco.android.ui.m5.chat.model.UserMessageBlock
import dev.myco.android.ui.m5.chat.model.LoadHistoryBlock
import dev.myco.android.ui.m5.chat.model.GroupBodyStreamingBlock
import dev.myco.android.ui.m5.primitives.M5Sheet
import dev.myco.android.ui.m5.primitives.reducedMotion
import kotlinx.coroutines.delay

/**
 * 流式 Markdown 节流契约（§3.11 字段 9 / CHAT-59，**纯函数 / JVM 单测覆盖**）。
 *
 * 规格：`间隔 = clamp(上次渲染耗时 × 2, 50ms, 2000ms)`；
 * 本实现另加任务硬约束下限 **≥100ms**（"流式 Markdown 节流（≥100ms 或按块）"），
 * 所以实际取 `max(100ms, clamp(耗时×2, 50, 2000))` —— 仍落在规格区间 [50,2000] 内。
 */
object MarkdownStreamThrottle {
    /** 规格下限（E1 recon）。 */
    const val FLOOR_MS: Long = 50L

    /** 规格上限（demo E0）。 */
    const val CEIL_MS: Long = 2000L

    /** P4 硬约束：至少 100ms 一次（"≥100ms 或按块"）。 */
    const val MIN_INTERVAL_MS: Long = 100L

    /** 规格公式：`clamp(耗时×2, 50, 2000)`。 */
    fun intervalMs(lastRenderMs: Long): Long =
        (lastRenderMs.coerceAtLeast(0L) * 2L).coerceIn(FLOOR_MS, CEIL_MS)

    /** 实际节流间隔 = `max(100, 规格公式)`。 */
    fun throttleMs(lastRenderMs: Long): Long = maxOf(MIN_INTERVAL_MS, intervalMs(lastRenderMs))

    /** 距上次发布 ≥ 节流间隔才允许更新文本。 */
    fun shouldPublish(sinceLastPublishMs: Long, lastRenderMs: Long): Boolean =
        sinceLastPublishMs >= throttleMs(lastRenderMs)
}

/**
 * 流式文本节流（CHAT-42 / CHAT-59）：只有 `streaming` 时才节流；定稿立即用全文。
 *
 * 说明：Compose 没有"上一次渲染耗时"的直接读数，用"发布后到下一帧"的耗时近似；
 * 纯公式由 [MarkdownStreamThrottle] 单测锁定，此处只负责节拍。`TODO(lead)`：若 P4 真机测得
 * 该近似偏差大，改成 `TextLayoutResult` 回读（需要 MarkdownText 暴露 onTextLayout）。
 */
@Composable
internal fun rememberThrottledMarkdown(text: String, streaming: Boolean): String {
    if (!streaming) return text
    val latest by rememberUpdatedState(text)
    var published by remember { mutableStateOf(text) }
    LaunchedEffect(streaming) {
        while (true) {
            val next = latest
            if (next != published) {
                val startedAt = System.nanoTime()
                published = next
                withFrameNanos { }
                val costMs = (System.nanoTime() - startedAt) / 1_000_000L
                delay(MarkdownStreamThrottle.throttleMs(costMs))
            } else {
                delay(MarkdownStreamThrottle.MIN_INTERVAL_MS)
            }
        }
    }
    return published
}

/** 列表级：block → lazy item 分发（§1.4 13 行顺序）。 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.turnBlockItems(
    blocks: List<TurnBlock>,
    runtime: ChatBlockRuntime,
) {
    blocks.forEach { block ->
        when (block) {
            is LoadHistoryBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                LoadHistoryRow()
            }

            is UserMessageBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                UserMessageUi(block, runtime)
            }

            is ProcessControlBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                ProcessControlRowUi(block, runtime)
            }

            // §3.5 字段 7：定稿**展开**态组头 = stickyHeader（G-06 / CHAT-11）；
            // 流式（live）组高 ≤ cap、不需要 sticky（§3.5 字段 7 反向）。
            is GroupHeaderBlock -> if (!block.live && block.expanded) {
                chatStickyBlock(block.key, block.spacingBeforeDp, runtime.stickyBackground) {
                    Box(Modifier.fillMaxWidth().processRowBleed()) {
                        ProcessGroupHeaderUi(block = block, background = runtime.stickyBackground) {
                            runtime.expansion.toggleGroup(block.key, block.turnId)
                            if (!block.expanded) runtime.requestScrollToBlock(block.key)
                        }
                    }
                }
            } else {
                chatBlockItem(block.key, block.spacingBeforeDp) {
                    ProcessGroupHeaderBlockUi(block, runtime)
                }
            }

            is GroupBodyStreamingBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                ProcessGroupBodyUi(block, runtime)
            }

            is GroupMemberBlock -> groupMemberItems(block, runtime)

            is ReplyInlineReasoningBlock -> reasoningBlockItems(
                blockKey = block.key,
                text = block.text,
                running = block.running,
                expanded = block.expanded,
                spacingBeforeDp = block.spacingBeforeDp,
                onToggle = { runtime.expansion.toggleInlineReasoning(block.key) },
                runtime = runtime,
            )

            is ReplyMarkdownBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                ReplyMarkdownUi(block)
            }

            is MaxTokensBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                MaxTokensRowUi()
            }

            is TurnErrorBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                TurnErrorRowUi(block) { runtime.callbacks.onRetryTurn(block.turnId, block.key) }
            }

            // §3.20 / CHAT-54：来源行 N>0 才出现（分组层已保证）；点击开 §3.21 来源 sheet。
            is SourcesRowBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                SourcesRowUi(block.sources) { runtime.openSources(block.sources) }
            }

            is ActionsBarBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                MessageActionsBarUi(
                    block = block,
                    copyText = block.message?.text ?: runtime.replyTextOf(block.turnId),
                    version = runtime.versions(block.key),
                    onCopied = runtime.callbacks.onCopied,
                    onRegenerate = {
                        block.message?.let(runtime.callbacks.onRegenerate)
                    },
                    onVersionSwitch = { target -> runtime.callbacks.onVersionSwitch(block.key, target) },
                )
            }

            is StreamStatusBlock -> chatBlockItem(block.key, block.spacingBeforeDp) {
                StreamStatusRowUi()
            }
        }
    }
}

/** 定稿拍平的组成员（§1.4 第 5 行）：思考行展开要 sticky 头，工具行是行 + 井。 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.groupMemberItems(
    block: GroupMemberBlock,
    runtime: ChatBlockRuntime,
) {
    val entry = block.member
    val settled = !entry.running
    if (entry.kind == ProcessEntryKind.REASONING && settled) {
        reasoningBlockItems(
            blockKey = block.key,
            text = entry.text,
            running = entry.running,
            expanded = runtime.expansion.isMemberExpanded(block.key),
            spacingBeforeDp = block.spacingBeforeDp,
            onToggle = { runtime.expansion.toggleMember(block.key) },
            runtime = runtime,
        )
        return
    }
    chatBlockItem(block.key, block.spacingBeforeDp) {
        ProcessMemberUi(
            entry = entry,
            blockKey = block.key,
            runtime = runtime,
            inCappedGroup = false,
            toolState = TurnStatus(turnId = block.turnId, phase = TurnPhase.CLOSED_OK).toolRowState(entry),
        )
    }
}

/**
 * 思考行两态分发（§3.7 字段 7 / 形态 D）：
 *  * 定稿 + 展开 → **头部 stickyHeader + 展开体独立 item**（形态 A，CHAT-02 / CHAT-11 / CHAT-17）；
 *  * 其余（折叠、流式、组内）→ 单个 inline item（流式在组内由组体承担唯一内滚）。
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.reasoningBlockItems(
    blockKey: String,
    text: String,
    running: Boolean,
    expanded: Boolean,
    spacingBeforeDp: Int,
    onToggle: () -> Unit,
    runtime: ChatBlockRuntime,
) {
    if (text.isBlank()) return
    val toggle: () -> Unit = {
        onToggle()
        // CHAT-19：只在"展开"这条路径上把展开体顶部带入视口。
        if (!expanded) runtime.requestScrollToBlock(blockKey)
    }
    if (!running && expanded) {
        stickyHeader(key = blockKey) {
            ChatStickyContent(background = runtime.stickyBackground, topPadding = spacingBeforeDp.dp) {
                // 过程行横向 Bleed 8：命中盒 = 列宽 + 16dp（G-16），视觉仍贴正文缘。
                Box(Modifier.fillMaxWidth().processRowBleed()) {
                    ReasoningHeaderUi(
                        text = text,
                        running = running,
                        expanded = expanded,
                        onToggle = toggle,
                        background = runtime.stickyBackground,
                    )
                }
            }
        }
        item(key = "$blockKey/body") {
            ChatContentColumn {
                // 展开体左缩进 22dp（§3.7 字段 2：图标 16 + gap 6）；原子内部缩进 32 只用于 inline 分支。
                Box(Modifier.padding(start = 22.dp)) { ReasoningBodyUi(text) }
            }
        }
        return
    }
    chatBlockItem(blockKey, spacingBeforeDp) {
        Box(Modifier.fillMaxWidth().processRowBleed()) {
            ReasoningRowUi(
                text = text,
                running = running,
                expanded = expanded,
                onToggle = toggle,
                inCappedGroup = true,
                capDp = runtime.capDp,
                contentVersion = text,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 通用 item 包装（内容列居中 / sticky 底色遮挡）
// ---------------------------------------------------------------------------

private fun LazyListScope.chatBlockItem(
    key: Any,
    spacingBeforeDp: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        ChatContentColumn(topPadding = spacingBeforeDp.dp, content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.chatStickyBlock(
    key: Any,
    spacingBeforeDp: Int,
    background: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    stickyHeader(key = key) {
        ChatStickyContent(background = background, topPadding = spacingBeforeDp.dp, content = content)
    }
}

// ---------------------------------------------------------------------------
// 各类 block 的具体视觉
// ---------------------------------------------------------------------------

/** §1.4 第 13 行 / CHAT-53：首屏回放中的顶部「载入历史…」（不显示骨架屏）。 */
@Composable
private fun LoadHistoryRow() {
    Text(
        text = "载入历史…",
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** §3.13 用户气泡：右对齐、宽 ≤ min(0.86×屏宽, 560dp)、padding 10×16、圆角 26、长按开菜单（CHAT-67/34）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessageUi(block: UserMessageBlock, runtime: ChatBlockRuntime) {
    val colors = MaterialTheme.colorScheme
    val message = block.message
    val version = runtime.versions(block.key)?.takeIf { it.visible }
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            // 附件条槽位（归 attachment 面板；本文件不渲染，见文件头缺口 3）。
            Box(
                Modifier
                    .widthIn(max = runtime.userBubbleMaxWidthDp.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colors.primaryContainer)
                    // §3.13 字段 4：只绑长按（不绑单击 → 不产生"点了没反应"的假点击语义/波纹）。
                    .pointerInput(message) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                runtime.callbacks.onUserLongPress(message)
                            },
                        )
                    }
                    .semantics(mergeDescendants = true) {
                        // §3.13 字段 11 / CHAT-34：读屏必须能"复制（真写剪贴板）/ 编辑并重发"。
                        customActions = listOf(
                            CustomAccessibilityAction("复制") {
                                val ok = runCatching {
                                    clipboard.setText(AnnotatedString(message.text))
                                }.isSuccess
                                if (ok) runtime.callbacks.onCopied(message.text)
                                true
                            },
                            CustomAccessibilityAction("编辑并重发") {
                                runtime.callbacks.onUserEditResend(message)
                                true
                            },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column {
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onPrimaryContainer,
                        )
                    }
                    if (message.revised) {
                        // §3.13 字段 5：被编辑重发的旧消息保留并标注（不删除）。
                        Text(
                            text = "（已被后续编辑消息替换）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            // §3.17 用户挂载位：气泡下方右对齐一行（版本数 >1 才出现）。
            if (version != null) {
                Spacer(Modifier.height(4.dp))
                VersionSwitcherUi(
                    version = version,
                    enabled = true,
                    onSwitch = { target -> runtime.callbacks.onVersionSwitch(block.key, target) },
                )
            }
        }
    }
}

/** §3.4 整轮过程折叠控制条：33dp + Δ、整行可点（含 Bleed 8）、底部 0.5dp 分隔线。 */
@Composable
private fun ProcessControlRowUi(block: ProcessControlBlock, runtime: ChatBlockRuntime) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .processRowBleed()
                .heightIn(min = ChatListMetrics.PROCESS_CONTROL_BASE_DP.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (block.expanded) "收起过程" else "展开过程",
                    onClick = { block.turnId?.let(runtime.expansion::toggleProcessControl) },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            M5Icon(
                icon = M5Icons.chevronDown,
                modifier = Modifier.rotate(if (block.expanded) 180f else 0f),
                tint = colors.onSurfaceVariant,
                size = 14.dp,
            )
        }
        // §3.4 字段 2：底部 0.5dp 分隔线。
        HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
    }
}

/** §1.4 第 7 行 / §3.11 正文：复用 `MarkdownText` + 流式节流 + 流式光标 + 「已停止」尾标。 */
@Composable
private fun ReplyMarkdownUi(block: ReplyMarkdownBlock) {
    val colors = MaterialTheme.colorScheme
    val text = rememberThrottledMarkdown(block.text, block.streaming)
    Column(Modifier.fillMaxWidth()) {
        MarkdownText(text)
        if (block.streaming) {
            // §3.11 字段 6：流式末尾光标（2dp 竖线）。已知偏离：以下是独立一行而非插在 glyph 之后，
            // 不会引起上方段落重排（CHAT-42 的重排判据仍满足）。
            StreamingCursor()
        }
        if (block.interrupted) {
            // §3.18 / CHAT-43：正文末行尾追加「已停止」。
            InterruptedSuffixUi()
        }
    }
}

/** 流式光标：1s 周期闪烁；reduced-motion → 静态（§3.11 字段 9）。 */
@Composable
private fun StreamingCursor() {
    val colors = MaterialTheme.colorScheme
    val reduced = reducedMotion()
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "stream-cursor")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "stream-cursor-alpha",
        )
        animated
    }
    Box(
        Modifier
            .padding(top = 4.dp)
            .size(width = 2.dp, height = 18.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(colors.primary.copy(alpha = alpha)),
    )
}

/** §3.10 首 token 空窗：34dp 三点脉冲；内容到达即整块卸载（互斥由分组层保证，CHAT-41）。 */
@Composable
private fun StreamStatusRowUi() {
    val colors = MaterialTheme.colorScheme
    val reduced = reducedMotion()
    val transition = rememberInfiniteTransition(label = "stream-status")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatScrollMetrics.STREAM_STATUS_HEIGHT_DP.dp)
            // G-08：读屏可听出"正在生成"（一次，不循环）。
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "正在生成"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { index ->
            val alpha = if (reduced) {
                0.6f
            } else {
                val animated by transition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 700),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 120),
                    ),
                    label = "stream-dot-$index",
                )
                animated
            }
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

/** §3.20 来源行：44dp 整行、`来源 N 个网页 ›`（CHAT-54）。 */
@Composable
private fun SourcesRowUi(sources: List<SourceRef>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ChatListMetrics.SOURCES_ROW_HEIGHT_DP.dp)
            .clickable(role = Role.Button, onClickLabel = "查看来源", onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "来源，共 ${sources.size} 个网页"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        M5Icon(icon = M5Icons.globe, tint = colors.onSurfaceVariant, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "来源 ${sources.size} 个网页",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        M5Icon(icon = M5Icons.chevronRight, tint = colors.onSurfaceVariant, size = 16.dp)
    }
}

// ---------------------------------------------------------------------------
// 来源 sheet（§3.21 `chat.source.panel`，E-17=A / G-03 形态 C）
// ---------------------------------------------------------------------------

/**
 * 来源面板（sheet）：header 60 的等价由 `M5Sheet` 的标题行给出；来源卡 padding 12 / 圆角 12 / 卡间 6，
 * 整卡可点 → 系统浏览器；标题 `role=dialog` 由 `M5Sheet` 的 `paneTitle` 提供（CHAT-55）。
 *
 * 打开时对来源做**快照**（`sources` 是点击那一刻的 list 值，后续轮次追加不影响本 sheet）。
 * `TODO(lead)`：§3.21 字段 2 的"sheet 封顶 min(400dp, 0.5×AStable)"需要 `M5Sheet` 暴露 maxHeight 参数
 * （现为固定 0.8×窗口高），属 base.primitives 缺能力。
 */
@Composable
internal fun SourceSheetUi(
    sources: List<SourceRef>?,
    onDismiss: () -> Unit,
) {
    if (sources.isNullOrEmpty()) return
    val uriHandler = LocalUriHandler.current
    M5Sheet(
        visible = true,
        onDismissRequest = onDismiss,
        title = "来源 ${sources.size} 个网页",
        showHandle = true,
        showCloseButton = true,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sources.forEach { source -> SourceCardUi(source) { runCatching { uriHandler.openUri(source.url) } } }
        }
    }
}

@Composable
private fun SourceCardUi(source: SourceRef, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = "打开网页", onClick = onClick)
            .semantics { contentDescription = "${source.title}，打开网页" }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.site ?: ToolRowText.basename(source.url).ifBlank { source.url },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!source.date.isNullOrBlank()) {
                Text(
                    text = source.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
