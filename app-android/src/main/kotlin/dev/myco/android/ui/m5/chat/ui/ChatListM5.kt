// 验收 ID：CHAT-06 CHAT-07 CHAT-11 CHAT-17 CHAT-28 CHAT-36 CHAT-37 CHAT-38 CHAT-39 CHAT-40
//          CHAT-48 CHAT-49 CHAT-50 CHAT-51 CHAT-53 CHAT-54 CHAT-57 CHAT-58 CHAT-59 CHAT-66
//
// chat 面板 UI 层入口（`docs/spec/panels/chat.md` §1.3 网格 / §1.4 轮内顺序 / §3.1 滚动跟随）。
//
// 结构（与 §1.4 的"扁平 block 级 LazyColumn，不是每轮一个 item"一一对应）：
//   ChatListM5 = 唯一纵向 LazyColumn + 内容列居中 + 回底钮 overlay + 空态槽位
//   每个 block 由 [turnBlockItems]（TurnBlockUi.kt）按类型分发；
//   滚动跟随由 [rememberChatScrollFollow]（ScrollFollowUi.kt）接 `ScrollFollowPolicy`。
//
// 硬约束落实：
//  * 唯一外层纵向滚动 = 本 LazyColumn（E-19=A）；其内只允许"流式过程组体 / 工具井 / 代码体"
//    三种单层内滚，**不得再嵌套纵向懒列表**（CHAT-58 / CHAT-17）；
//  * 内容列宽 = min(列宽 − 40dp, 752dp) 居中，左右内边距 20dp（§1.3，E0）；
//  * 底部留白 = composer 实时高 + 24dp（§1.3 / E-02，本面板不自算 composer 高）；
//  * 顶部留白 = 12dp 由首 block 的 `spacingBeforeDp` 给出（ChatBlockSpacing.FIRST_TOP_DP），
//    不用 contentPadding.top 再叠一次（避免 24dp 双倍）。
//  * 空态不内置（chat.empty.suggestion 属另一实现批次）：通过 [emptyContent] 槽位注入，
//    接线方传 `M5EmptyState` 时**必须**先去掉「她记得你们聊过的事」类承诺文案（CHAT-52）。
//
// 不本文件负责（归邻面板/批次）：composer 高度真源、顶栏、IME insets（M2/composer，E-02）、
// 全文页 route（工具详情「查看全部」只给回调）、长按菜单/编辑对话框浮层（只给回调）。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.SourceRef
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.model.ChatMessageUi
import dev.myco.android.ui.m5.chat.model.ChatCap
import dev.myco.android.ui.m5.chat.model.ChatTimelineInput
import dev.myco.android.ui.m5.chat.model.ReplyMarkdownBlock
import dev.myco.android.ui.m5.chat.model.TurnBlock
import dev.myco.android.ui.m5.chat.model.TurnExpansionState
import dev.myco.android.ui.m5.chat.model.TurnGrouping
import kotlin.math.min

/**
 * 消息版本信息（§3.17 `chat.msg.version-switcher`）。
 *
 * @param index 当前版（**1 基**，与「第 N 版，共 M 版」一致）
 * @param total 总版数；`total <= 1` → 不渲染切换器（CHAT-33）
 */
data class MessageVersion(val index: Int, val total: Int) {
    init {
        require(index >= 1) { "版本 index 是 1 基" }
        require(total >= 1) { "版本 total 至少 1" }
    }

    /** §3.17 字段 1：M=1 → 不渲染。 */
    val visible: Boolean get() = total > 1

    /** 端点禁用（40% 不透明），生成中也同样禁用（§3.17 字段 5/6）。 */
    val hasPrevious: Boolean get() = index > 1
    val hasNext: Boolean get() = index < total
}

/**
 * 列表 → 业务层的回调集合（全部可缺省；本 UI 层不发任何 Command，命令由上层决定）。
 *
 * 接线约定（Lead 在 `ChatScreen` 里接）：传进来的回调只做"语义"（复制文本 / 重新生成哪条消息 /
 * 重试哪轮 / 切哪个版本），**幂等与命令条数由上层保证**（CHAT-45 / CHAT-32 的单测在上层）。
 */
data class ChatListCallbacks(
    /** 复制原文（已真写剪贴板；本回调只用于上层提示/统计，可空实现）。 */
    val onCopied: (text: String) -> Unit = {},
    /** §3.16：重新生成（新版本，不幂等；生成中上层应已禁用到不了这里）。 */
    val onRegenerate: (ChatMessageUi) -> Unit = {},
    /** §3.19：以同一 requestId 重发（语义由上层提供，UI 只传当前错误 block 的轮号）。 */
    val onRetryTurn: (turnId: Long?, blockKey: String) -> Unit = { _, _ -> },
    /** §3.13/§3.14：长按用户气泡（开菜单归上层浮层）。 */
    val onUserLongPress: (ChatMessageUi) -> Unit = {},
    /** §3.15：TalkBack 自定义动作「编辑并重发」。 */
    val onUserEditResend: (ChatMessageUi) -> Unit = {},
    /** §3.17：版本切换（`blockKey` = 该 block 稳定 key；`targetIndex` 1 基）。 */
    val onVersionSwitch: (blockKey: String, targetIndex: Int) -> Unit = { _, _ -> },
    /** §3.9：工具井「查看全部」→ 全文页 route（route push 归上层，`TODO(lead)`）。 */
    val onOpenToolDetail: (entry: TimelineEntry) -> Unit = {},
)

/**
 * 展开状态（G-07：本地态、绑轮次版本）。
 *
 * - 过程区/组头/内联 reasoning 的开关走 model 层 [TurnExpansionState]（分组要用）；
 * - 过程**成员**（思考行正文、工具井）的开关不在分组里，按 block key 单独记；
 * - 轮次版本前进（重建）→ 调用方传 `expansionResetKey` 变化触发 [reset]；
 *   同一轮内 key 含 `turnId`，天然不会串到别的轮。
 */
@Stable
class ChatListExpansion {
    /** 分组可见性输入（model 层）。 */
    var groupState by mutableStateOf(TurnExpansionState())
        private set

    private val memberExpanded = mutableStateMapOf<String, Boolean>()

    /** 整轮过程区开关（§3.4 字段 5：写 store = 这里的本地态）。 */
    fun toggleProcessControl(turnId: Long) {
        val set = groupState.expandedProcessTurns
        groupState = groupState.copy(
            expandedProcessTurns = if (turnId in set) set - turnId else set + turnId,
        )
    }

    /** §3.5 组头开合（按 header key）；展开时把该轮记入"用户手动展开"，settle 后保持展开（CHAT-18）。 */
    fun toggleGroup(headerKey: String, turnId: Long? = null) {
        val set = groupState.collapsedGroupKeys
        val collapsing = headerKey !in set
        groupState = groupState.copy(
            collapsedGroupKeys = if (collapsing) set + headerKey else set - headerKey,
            expandedProcessTurns = if (!collapsing && turnId != null) {
                groupState.expandedProcessTurns + turnId
            } else {
                groupState.expandedProcessTurns
            },
        )
    }

    /** §3.7 内联 reasoning 开合（按 block key）。 */
    fun toggleInlineReasoning(blockKey: String) {
        val set = groupState.expandedInlineReasoningKeys
        groupState = groupState.copy(
            expandedInlineReasoningKeys = if (blockKey in set) set - blockKey else set + blockKey,
        )
    }

    /** 过程成员（思考行正文 / 工具井）开合。 */
    fun toggleMember(blockKey: String) {
        memberExpanded[blockKey] = memberExpanded[blockKey] != true
    }

    fun isMemberExpanded(blockKey: String): Boolean = memberExpanded[blockKey] == true

    /** G-07：轮次版本前进/会话重建 → 全部回折叠。 */
    fun reset() {
        groupState = TurnExpansionState()
        memberExpanded.clear()
    }
}

/**
 * §1.3 / §1.6 / §3.2 的纯几何契约（**JVM 单测覆盖**，不依赖 Android）。
 *
 * 规格来源逐条标注；改动任何数字都必须先改 `chat.md`。
 */
object ChatListMetrics {
    /** §1.3 列表左右内边距（含在内容列宽公式里）。 */
    const val HORIZONTAL_PADDING_DP: Float = 20f

    /** §1.3 内容列宽上限 = 752dp（`demo/M/MessageList.module.css:32-37` E0）。 */
    const val CONTENT_MAX_WIDTH_DP: Float = 752f

    /** §1.3 列表底部留白基础值 24dp（composer 高另加）。 */
    const val LIST_BOTTOM_GAP_DP: Float = 24f

    /** §1.3 列表顶部留白（首块 12dp，P4 校准）。 */
    const val LIST_TOP_GAP_DP: Float = 12f

    /** §1.3 过程行布局盒基准 32dp（思考行 / 工具行 / 组头）；§3.4 控制条基准 33dp。 */
    const val PROCESS_ROW_BASE_DP: Float = 32f
    const val PROCESS_CONTROL_BASE_DP: Float = 33f

    /** 过程行"文字行 + 上下各 4dp"的固定内边距（D-02）。 */
    const val PROCESS_ROW_VERTICAL_PADDING_DP: Float = 8f

    /** G-16 过程行档：横向 Bleed 8dp（命中盒 = 列宽 + 2×8dp，整行可点）。 */
    const val PROCESS_ROW_BLEED_DP: Float = 8f

    /** §1.3 操作条行高 40dp；每键 40×40；图标 20。 */
    const val ACTIONS_BAR_HEIGHT_DP: Float = 40f
    const val ACTION_KEY_SIZE_DP: Float = 40f
    const val ACTION_ICON_SIZE_DP: Float = 20f

    /** §3.20 来源行行高 44dp。 */
    const val SOURCES_ROW_HEIGHT_DP: Float = 44f

    /** §3.2 回底钮 44×44；end 16dp；bottom = composer 高 + 16dp。 */
    const val TO_BOTTOM_SIZE_DP: Float = 44f
    const val TO_BOTTOM_END_DP: Float = 16f
    const val TO_BOTTOM_BOTTOM_GAP_DP: Float = 16f

    /** §3.3 用户气泡宽上限：min(0.86×屏宽, 560dp)。 */
    const val USER_BUBBLE_SCREEN_RATIO: Float = 0.86f
    const val USER_BUBBLE_MAX_WIDTH_DP: Float = 560f

    /** §1.3 内容列宽 = min(列宽 − 40dp, 752dp)。 */
    fun contentColumnWidthDp(columnWidthDp: Float): Float {
        if (columnWidthDp <= 0f) return 0f
        return (columnWidthDp - 2f * HORIZONTAL_PADDING_DP)
            .coerceAtMost(CONTENT_MAX_WIDTH_DP)
            .coerceAtLeast(0f)
    }

    /**
     * §1.3 Δ（font-delta）：过程行行高 = `max(基准, 文字实测行高 + 上下内边距)`。
     * 构建期算一次（G-02 定高：摘要变化不重排兄弟）。
     */
    fun processRowHeightDp(textLineHeightDp: Float): Float =
        maxOf(PROCESS_ROW_BASE_DP, textLineHeightDp + PROCESS_ROW_VERTICAL_PADDING_DP)

    /** §3.4：控制条布局盒 = `max(33dp, 文字行高 + 上下内边距)`。 */
    fun processControlHeightDp(textLineHeightDp: Float): Float =
        maxOf(PROCESS_CONTROL_BASE_DP, textLineHeightDp + PROCESS_ROW_VERTICAL_PADDING_DP)

    /** §1.3 列表底部留白 = composer 实时高 + 24dp（E-02：本面板只消费）。 */
    fun listBottomPaddingDp(composerHeightDp: Float): Float =
        composerHeightDp.coerceAtLeast(0f) + LIST_BOTTOM_GAP_DP

    /** §3.2 回底钮 bottom 偏移 = composer 实时高 + 16dp（不用魔数）。 */
    fun toBottomBottomOffsetDp(composerHeightDp: Float): Float =
        composerHeightDp.coerceAtLeast(0f) + TO_BOTTOM_BOTTOM_GAP_DP

    /** §3.13 用户气泡宽度上限 = min(0.86×屏宽, 560dp)。 */
    fun userBubbleMaxWidthDp(screenWidthDp: Float): Float =
        min(screenWidthDp.coerceAtLeast(0f) * USER_BUBBLE_SCREEN_RATIO, USER_BUBBLE_MAX_WIDTH_DP)

    /** 内容列（含左右 20dp 内边距）的外层盒宽上限 = 752 + 2×20。 */
    fun contentBoxMaxWidthDp(): Float = CONTENT_MAX_WIDTH_DP + 2f * HORIZONTAL_PADDING_DP

    /**
     * G-16 / CHAT-20：过程行命中盒的左边界（相对列表）= `内容列内边距 − Bleed`。
     * 内容列 20dp + Bleed 8 → 命中盒从 x=12dp 起、到 W−12dp 止；视觉内容仍在 20dp 正文缘。
     */
    fun processRowHitInsetDp(contentPaddingDp: Float = HORIZONTAL_PADDING_DP): Float =
        (contentPaddingDp - PROCESS_ROW_BLEED_DP).coerceAtLeast(0f)
}

/** 内容列显式居中的外层盒宽（= 752 + 40dp）。 */
private val ContentBoxMaxWidth = ChatListMetrics.contentBoxMaxWidthDp().dp

/**
 * chat 列表唯一入口。
 *
 * @param state 时间线输入（`ChatTimelineInput`：已落盘消息 + 直播轮 + 来源 + 元数据 + 载入态）；
 *   由现有模型投影，见 `TurnGrouping` 的 KDoc。
 * @param listState 外层 `LazyListState`（由接线方持有，便于发送/返回时复用同一滚动位置）。
 * @param aStableViewportHeightDp **IME 收起时**的列表可视高（AStable，E-02）；
 *   仅用于 `cap = min(400dp, 0.5×AStable)`，键盘开合不重算（§1.6）。
 * @param bottomContentPadding composer 实时高度（E-02）；本组件再加 24dp 基础留白。
 * @param callbacks 业务回调（见 [ChatListCallbacks]）；本 UI 层不发 Command。
 * @param versions 按 block 稳定 key 取版本信息（`null`/total=1 → 不渲染切换器）。
 * @param expansionResetKey 变化时清空所有展开态（G-07：传轮次版本/sessionId）。
 * @param emptyContent 空态槽位（chat.empty.suggestion 由另一批次实现）。
 * @param backgroundColor 列表背景色（sticky 头要用它遮挡下方内容，G-06）。
 * @param scrollToBottomSignal 变化即"程序化回底并恢复跟随"（发送成功后贴底用；不取消跟随）。
 * @param onFollowingChanged 跟随状态变化回调（上层可用它决定是否显示自己的"在底部"提示）。
 */
@Composable
fun ChatListM5(
    state: ChatTimelineInput,
    listState: LazyListState,
    aStableViewportHeightDp: Float,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    callbacks: ChatListCallbacks = ChatListCallbacks(),
    versions: (blockKey: String) -> MessageVersion? = { null },
    expansionResetKey: Any? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    scrollToBottomSignal: Any? = null,
    onFollowingChanged: (Boolean) -> Unit = {},
) {
    val expansion = remember { ChatListExpansion() }
    LaunchedEffect(expansionResetKey) { expansion.reset() }

    // 分组纯函数（同输入必得同输出 → key 稳定，CHAT-57）。
    val blocks: List<TurnBlock> = remember(state, expansion.groupState) {
        TurnGrouping.group(state, expansion.groupState)
    }

    if (blocks.isEmpty()) {
        Box(modifier.fillMaxSize()) { emptyContent?.invoke() }
        return
    }

    // §1.6：cap 组合期算一次；键盘开合不改变 cap（ChatCap.withImeVisibility 恒等）。
    val cap = remember(aStableViewportHeightDp) { ChatCap(aStableViewportHeightDp) }

    // §3.1 / §3.2：滚动跟随状态机（含回底钮迟滞、程序化滚动不打断跟随）。
    val follow = rememberChatScrollFollow(
        listState = listState,
        loadingHistory = state.loadingHistory,
    )
    // 内容增长（流式 token / 上方插入）→ 跟随时重定向到新底，非跟随时不拽回读者（CHAT-36/CHAT-40）。
    LaunchedEffect(blocks) { follow.onContentGrew() }

    // 发送成功后的"程序化回底"：直接到底 + 恢复跟随（§3.2 字段 5 / CHAT-39）。
    LaunchedEffect(scrollToBottomSignal) {
        if (scrollToBottomSignal != null) follow.scrollToBottom()
    }
    // 跟随状态外发（上层可用它决定自己的"回到底部"入口）。
    LaunchedEffect(follow.state.following) { onFollowingChanged(follow.state.following) }

    // 操作条「复制」要拿该轮正文全文：直播轮正文在 reply block 里（actions block 的 message 为 null）。
    val replyTextByTurn = remember(blocks) {
        blocks.filterIsInstance<ReplyMarkdownBlock>().associate { it.turnId to it.text }
    }
    val latestReplyText = rememberUpdatedState(replyTextByTurn)

    // §3.1 字段 8 / CHAT-40：非跟随状态下"上方插入 block"时按语义锚点补偿。
    // 用 SideEffect（组合后、布局前，同帧生效）而不是 LaunchedEffect（帧后，会先跳一帧）。
    val blockKeys = remember(blocks) { blocks.map { it.key } }
    val keyHolder = remember { BlockKeyHolder() }
    SideEffect {
        follow.updateBlockKeys(blockKeys)
        val previous = keyHolder.keys
        if (previous != null && previous != blockKeys && !follow.state.following) {
            follow.onProgrammaticScroll()
            val anchorKey = previous.getOrNull(listState.firstVisibleItemIndex)
            val targetIndex = if (anchorKey != null) blockKeys.indexOf(anchorKey) else -1
            if (targetIndex >= 0 && targetIndex != listState.firstVisibleItemIndex) {
                // 布局期同帧请求（非挂起）→ 读者正在看的那一行 y 位移 ≤2dp。
                listState.requestScrollToItem(targetIndex, listState.firstVisibleItemScrollOffset)
            }
        }
        keyHolder.keys = blockKeys
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // §3.13：用户气泡宽上限 = min(0.86×屏宽, 560dp)；这里拿的是壳给本列表的列宽（§1.1 宽屏）。
        val runtime = remember(cap, expansion, callbacks, versions, backgroundColor, maxWidth) {
            ChatBlockRuntime(
                capDp = cap.capDp.coerceAtLeast(0f).dp,
                expansion = expansion,
                callbacks = callbacks,
                versions = versions,
                stickyBackground = backgroundColor,
                userBubbleMaxWidthDp = ChatListMetrics.userBubbleMaxWidthDp(screenWidthDp = maxWidth.value),
                // 操作条的"复制"要拿该轮正文全文；直播轮正文在 ReplyMarkdownBlock 里（actions block 的
                // message 为 null），所以从这里按 turnId 回查（值随组合更新，用 updatedState 保最新）。
                replyTextOf = { turnId -> latestReplyText.value[turnId] ?: "" },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 读者手势第一帧即停跟随（CHAT-37），程序化滚动不进这里（§3.1 字段 4）。
                .chatReaderGestureObserver(
                    onStart = { follow.onReaderGestureStart() },
                    onEnd = { follow.onReaderGestureEnd() },
                ),
            contentPadding = PaddingValues(
                top = 0.dp,
                // §1.3：底部 = composer 实时高 + 24dp（E-02 由接线方给 composer 高）。
                bottom = ChatListMetrics.listBottomPaddingDp(bottomContentPadding.value).dp,
            ),
        ) {
            turnBlockItems(blocks = blocks, runtime = runtime)
        }
        BackToBottomButtonUi(
            visible = follow.state.toBottomButtonVisible,
            onClick = { follow.scrollToBottom() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = ChatListMetrics.TO_BOTTOM_END_DP.dp,
                    bottom = ChatListMetrics.toBottomBottomOffsetDp(bottomContentPadding.value).dp,
                ),
        )
        // §3.20/§3.21：来源行触发的来源 sheet（面板内浮层，G-03 形态 C；与菜单/对话框互斥由上层 dispatcher 保证）。
        SourceSheetUi(sources = runtime.sourcesSheet, onDismiss = { runtime.closeSources() })

        // §3.7 字段 5 / CHAT-19：展开后展开体顶部进入视口（仅当它没有完全可见时；已可见则列表不动）。
        val latestBlocks = rememberUpdatedState(blocks)
        LaunchedEffect(runtime, listState) {
            snapshotFlow { runtime.pendingScrollKey }.collect { key ->
                if (key == null) return@collect
                runtime.consumePendingScrollKey()
                val index = latestBlocks.value.indexOfFirst { it.key == key }
                if (index < 0) return@collect
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
                val notFullyVisible = visible == null ||
                    visible.offset + visible.size > info.viewportEndOffset ||
                    visible.offset < info.viewportStartOffset
                if (notFullyVisible) listState.scrollToItem(index)
            }
        }
    }
}

/** 非状态持有器：记住上一帧 block key 列表（写 state 会触发额外重组，这里只需普通字段）。 */
private class BlockKeyHolder {
    var keys: List<String>? = null
}

/** 一个 block 渲染期所需的装配上下文（避免每个 block 传 8 个参数）。 */
@Stable
internal class ChatBlockRuntime(
    /** §1.6 流式过程组 cap（已按 AStable 算好）。 */
    val capDp: Dp,
    val expansion: ChatListExpansion,
    val callbacks: ChatListCallbacks,
    val versions: (blockKey: String) -> MessageVersion?,
    /** sticky 头底色（G-06：必须不透明地盖住下方内容）。 */
    val stickyBackground: Color,
    /** §3.13 用户气泡宽上限 = min(0.86×屏宽, 560dp)。 */
    val userBubbleMaxWidthDp: Float,
    /** 按轮取正文全文（操作条复制用；直播轮 actions block 的 message 为 null）。 */
    val replyTextOf: (Long?) -> String,
) {
    /** §3.21 来源 sheet 的**快照**（null = 关闭）；打开时取点击那一刻的 list 值。 */
    var sourcesSheet: List<SourceRef>? by mutableStateOf(null)
        private set

    fun openSources(sources: List<SourceRef>) {
        sourcesSheet = sources
    }

    fun closeSources() {
        sourcesSheet = null
    }

    /**
     * §3.7 字段 5 / CHAT-19：展开后把展开体顶部带入视口。
     * 消费方（ChatListM5）在布局后按 key 找 index，只在"没完全可见"时滚动。
     */
    var pendingScrollKey: String? by mutableStateOf(null)
        private set

    fun requestScrollToBlock(blockKey: String) {
        pendingScrollKey = blockKey
    }

    /** 取出并清空待滚动目标（一次消费，避免反复滚动）。 */
    fun consumePendingScrollKey(): String? {
        val key = pendingScrollKey
        pendingScrollKey = null
        return key
    }
}

/**
 * 内容列：`min(列宽 − 40dp, 752dp)` 居中（§1.3 E0）。
 *
 * 实现 = 外层满宽 Box 居中 + `widthIn(max = 792dp)`（752 + 2×20）+ 左右 20dp 内边距。
 */
@Composable
internal fun ChatContentColumn(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = ContentBoxMaxWidth)
                .fillMaxWidth()
                .padding(
                    start = ChatListMetrics.HORIZONTAL_PADDING_DP.dp,
                    end = ChatListMetrics.HORIZONTAL_PADDING_DP.dp,
                    top = topPadding,
                ),
            content = content,
        )
    }
}

/** sticky 头容器：满宽不透明底色遮挡（G-06）+ 内容列对齐。 */
@Composable
internal fun ChatStickyContent(
    background: Color,
    topPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().background(background)) {
        ChatContentColumn(topPadding = topPadding, content = content)
    }
}

/**
 * 过程行的**横向 Bleed 8**（G-16 过程行档：命中盒 = 列宽 + 2×8dp，整行可点）。
 *
 * 命中盒 = 布局盒（G-11 硬约束）：这里**真的改变测量宽度**（`layout` 把测量宽 +16、再负向放置 8），
 * **不用** `Modifier.offset` / `graphicsLayer` 假装扩边；视觉内容靠行内 8dp 内边距回到正文缘。
 * 上层内容列 20dp 内边距 → 命中盒落在屏幕 x=12..W−12（CHAT-20 的量测口径）。
 */
internal fun Modifier.processRowBleed(bleed: Dp = ChatListMetrics.PROCESS_ROW_BLEED_DP.dp): Modifier = this.layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val bleedPx = bleed.roundToPx()
        val wide = constraints.maxWidth + 2 * bleedPx
        val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-bleedPx, 0) }
    }
}
