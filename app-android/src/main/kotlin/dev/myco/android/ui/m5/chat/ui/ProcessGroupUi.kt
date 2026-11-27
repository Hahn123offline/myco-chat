// 验收 ID：CHAT-06 CHAT-07 CHAT-08 CHAT-09 CHAT-10 CHAT-11 CHAT-12 CHAT-13 CHAT-14 CHAT-16
//          CHAT-17 CHAT-48 CHAT-50 CHAT-58
//
// 过程段组（`docs/spec/panels/chat.md` §3.5 `chat.group.header` / §3.6 `chat.group.body` / G-03 形态 D / G-04）。
//
// 形态 D（D-11=C）在本文件的落点：
//  * **流式期 = 形态 B**：组体是 `heightIn(max = cap)` 的**单层内滚**（cap = min(400dp, 0.5×AStable)），
//    内容增长时贴底跟尾（距组底 ≤1dp），**拖动即暂停跟尾**（CHAT-12/13），
//    可滚侧 24dp 四段 mask 渐隐（CHAT-08），到边**全量吸收残余速度**（G-04 / CHAT-09/10）；
//  * **定稿后 = 形态 A**：本文件不再提供任何滚动容器；成员拍平成列表流 block、组头走 `stickyHeader`
//    （见 TurnBlockUi.kt）。同一时刻**决不允许两层纵向内滚**（CHAT-17）。
//
// 组头（§3.5）：
//  * 布局盒 32dp + Δ、整行可点（命中 = 列宽 + 横向 Bleed 8，G-16 过程行档）；
//  * live 标题按"正在进行什么"合成、closed 标题只列类别**不含数量**（CHAT-16，纯函数 [GroupHeaderText]）；
//  * 复用 base 原子 `DisclosureHeader`（32dp 定高 / 整行开关 / chevron 常显 / stateDescription / G-08 读屏）。
//    已知偏离：原子把 chevron 放行尾（spec §3.5 要 leading 24 槽）、标题字号走 bodyMedium（spec 要正文 16/24）
//    —— 属 base.primitives 缺变体，登记为 `TODO(lead)`，本文件**不重写原子**。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.myco.android.ui.TimelineEntry
import dev.myco.android.ui.m5.chat.model.GroupBodyStreamingBlock
import dev.myco.android.ui.m5.chat.model.GroupHeaderBlock
import dev.myco.android.ui.m5.chat.model.NestedScrollArbiter
import dev.myco.android.ui.m5.chat.model.NestedScrollGesture
import dev.myco.android.ui.m5.chat.model.ProcessEntryKind
import dev.myco.android.ui.m5.chat.model.ProcessCapFade
import dev.myco.android.ui.m5.chat.model.ToolRowState
import dev.myco.android.ui.m5.chat.model.TurnPhase
import dev.myco.android.ui.m5.chat.model.TurnStatus
import dev.myco.android.ui.m5.primitives.DisclosureHeader

/** 组内成员间距（§1.5：6dp，`C/ChatGroupSeat.module.css:127-134` E0）。 */
internal val GroupMemberSpacing = 6.dp

/**
 * 过程组标题文案（§3.5 字段 10，**纯函数 / JVM 单测覆盖**）。
 *
 * - live：按"正在做什么"给动态文案；无活动 → 「正在分析请求」；仅 preparing → 「准备…」；
 * - closed：类别合成文案，**只列类别、不显示计数**（CHAT-16）；>3 类 → 「前 3 类 等」；
 * - 空组不该出现（调用方不渲染），兜底给「思考过程」。
 */
object GroupHeaderText {
    const val LIVE_IDLE = "正在分析请求"
    const val LIVE_PREPARING = "准备…"
    const val LIVE_TOOL = "正在使用工具…"
    const val LIVE_REASONING = "正在思考…"
    const val SETTLED_FALLBACK = "思考过程"

    /** §3.5 字段 10：>3 类时只留前 3 类 + 「等」。 */
    const val MAX_CATEGORIES = 3

    /** 工具类别展示名（myco 工具集 + 未知工具兜底；不显示计数、不显示参数）。 */
    fun categoryOf(toolName: String): String = when (toolName) {
        "web_search" -> "搜索网页"
        "read_url" -> "读取网页"
        "ocr_image" -> "识别图片"
        "time_now" -> "读取时间"
        else -> "调用 $toolName"
    }

    /** closed：类别合成，不带计数。 */
    fun closedTitle(members: List<TimelineEntry>): String {
        val categories = members
            .asSequence()
            .filter { it.kind == ProcessEntryKind.TOOL }
            .map { categoryOf(it.name) }
            .distinct()
            .toList()
        if (categories.isEmpty()) {
            // 只有 reasoning / 中间步正文的组：给一个中性类别名（不出现数字、不出现工具名）。
            return SETTLED_FALLBACK
        }
        val head = categories.take(MAX_CATEGORIES).joinToString("、")
        return "已" + head + if (categories.size > MAX_CATEGORIES) " 等" else ""
    }

    /** live：动态文案（标题恒为单行，字数变化不换行 = G-02 定高）。 */
    fun liveTitle(members: List<TimelineEntry>): String {
        if (members.any { it.kind == ProcessEntryKind.TOOL && it.running }) return LIVE_TOOL
        if (members.any { it.kind == ProcessEntryKind.REASONING && it.running }) return LIVE_REASONING
        if (members.any { it.kind == ProcessEntryKind.TOOL && it.ok == null && !it.running }) return LIVE_PREPARING
        return LIVE_IDLE
    }

    fun title(members: List<TimelineEntry>, live: Boolean): String =
        if (live) liveTitle(members) else closedTitle(members)
}

/**
 * 组头（§3.5）。
 *
 * 命中区 = 整行 × (32dp + Δ) + 横向 Bleed 8（G-16 过程行档）：由 [processRowBleed] 的布局盒 +
 * 原子自己的 `bleed` 内边距共同给出，**不用 offset/graphicsLayer 假装扩边**。
 */
@Composable
internal fun ProcessGroupHeaderUi(
    block: GroupHeaderBlock,
    background: Color,
    onToggle: () -> Unit,
) {
    val title = rememberDwellTitle(GroupHeaderText.title(block.members, block.live))
    DisclosureHeader(
        title = title,
        expanded = block.expanded,
        onToggle = onToggle,
        // G-08：live 时读屏可听出"正在处理"（视觉隐藏节点，出现即播报一次）。
        running = block.live,
        runningLabel = if (block.live) "正在处理" else "",
        headerBackground = background,
    )
}

/**
 * live 组头标题最短驻留（§3.5 字段 10 / CHAT-15）：两次更新间隔 <150ms 时屏幕只显示后一个标题，
 * 且它至少在位 150ms（连续工具不再每帧闪变）。只保留最新待显示标题。
 */
object GroupHeaderDwell {
    /** 规格：150ms 最短驻留。 */
    const val DWELL_MS: Long = 150L
}

/** 组头标题驻留（150ms）：只保留最新待显示标题，减少闪变（CHAT-15）。 */
@Composable
internal fun rememberDwellTitle(title: String): String {
    var applied by remember { mutableStateOf(title) }
    LaunchedEffect(title) {
        if (title == applied) return@LaunchedEffect
        kotlinx.coroutines.delay(GroupHeaderDwell.DWELL_MS)
        applied = title
    }
    return applied
}

/**
 * 流式组体（§3.6 字段 2/6/8）：cap 内滚 + 24dp 四段 mask + 到边吸收 + 跟尾。
 *
 * 这是**该轮唯一允许的纵向内滚容器**（CHAT-17）：成员（含已展开的思考行）在里面
 * **不许再套第二层滚动**，见 [ProcessMemberUi] 的 `inCappedGroup = true`。
 */
@Composable
internal fun ProcessGroupBodyUi(
    block: GroupBodyStreamingBlock,
    runtime: ChatBlockRuntime,
) {
    CappedInnerScroll(
        capDp = runtime.capDp,
        // 内容变化 → 贴底跟尾（距组底 ≤1dp 才跟；拖动过就暂停，CHAT-12/13）。
        contentVersion = block.members,
    ) {
        block.members.forEachIndexed { index, entry ->
            if (index > 0) Spacer(Modifier.height(GroupMemberSpacing))
            ProcessMemberUi(
                entry = entry,
                blockKey = "${block.key}/member:$index",
                runtime = runtime,
                inCappedGroup = true,
                // 流式期一律按 live 轮判工具状态（running / preparing / failed，§3.8 字段 6 / CHAT-22）。
                toolState = TurnStatus(turnId = block.turnId, phase = TurnPhase.STREAMING)
                    .toolRowState(entry),
            )
        }
    }
}

/**
 * 组内成员行（思考行 / 工具行 / 工具循环中间步正文）。
 *
 * `inCappedGroup = true` 表示调用方已经提供了那**唯一一层**内滚（流式组体或独立渲染的封顶思考行），
 * 此时思考行展开体**不再自建滚动**（§3.7 字段 7 / CHAT-17）。
 * 定稿拍平的成员不走这里（要 sticky 头，见 TurnBlockUi.kt 的 `reasoningBlockItems`）。
 */
@Composable
internal fun ProcessMemberUi(
    entry: TimelineEntry,
    blockKey: String,
    runtime: ChatBlockRuntime,
    inCappedGroup: Boolean,
    toolState: ToolRowState,
) {
    val expanded = runtime.expansion.isMemberExpanded(blockKey)
    when (entry.kind) {
        ProcessEntryKind.REASONING -> ReasoningRowUi(
            text = entry.text,
            running = entry.running,
            expanded = expanded,
            onToggle = {
                runtime.expansion.toggleMember(blockKey)
                // §3.7 字段 5 / CHAT-19：展开后把展开体顶部带入视口（已可见则不动）。
                if (!expanded) runtime.requestScrollToBlock(blockKey)
            },
            inCappedGroup = inCappedGroup,
            capDp = runtime.capDp,
            contentVersion = entry,
        )
        ProcessEntryKind.TOOL -> ToolRowUi(
            entry = entry,
            state = toolState,
            expanded = expanded,
            onToggle = {
                // §3.8 字段 4：preparing 不可展开（点击零行为，CHAT-22）。
                if (toolState != ToolRowState.PREPARING) {
                    runtime.expansion.toggleMember(blockKey)
                    if (!expanded) runtime.requestScrollToBlock(blockKey)
                }
            },
            inCappedGroup = inCappedGroup,
            onOpenDetail = { runtime.callbacks.onOpenToolDetail(entry) },
        )
        else -> StepTextRowUi(text = entry.text, inCappedGroup = inCappedGroup)
    }
}

/** 工具循环中间步正文（§1.4「合并/切段规则」里的 `text` 成员；规格未单列节点，按组内次级文本）。 */
@Composable
private fun StepTextRowUi(text: String, inCappedGroup: Boolean) {
    if (text.isBlank()) return
    androidx.compose.material3.Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * **单层封顶内滚容器**（§3.6 形态 B 的实现；也复用给独立渲染的流式思考行 §3.7 字段 7）。
 *
 * 组成（缺一不合格）：
 *  1. `heightIn(max = capDp)`：cap 由调用方按 `min(400dp, 0.5×AStable)` 算好（ChatCap）；
 *  2. 唯一 `verticalScroll`；
 *  3. 可滚侧 24dp mask 渐隐（[ProcessCapFade] 的 stops → `Brush.verticalGradient` + `BlendMode.DstIn` 离屏层）；
 *  4. G-04：到边残余**全量吸收**（[Modifier.absorbsInnerEdgeScroll] + 手势开始快照）；
 *  5. 跟尾：距组底 ≤1dp 时内容增长贴底；**按下即暂停跟尾**（CHAT-12），抬回底恢复（CHAT-13）。
 *
 * @param contentVersion 内容版本（成员 list / 文本）；变化时按跟尾状态决定是否贴底。
 */
@Composable
internal fun CappedInnerScroll(
    capDp: Dp,
    contentVersion: Any?,
    modifier: Modifier = Modifier,
    showFade: Boolean = true,
    /** 流式过程组/流式思考行要跟尾；工具井是静态内容，不跟尾（打开时停在顶部）。 */
    followTail: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current.density
    var viewportPx by remember { mutableIntStateOf(0) }
    var following by remember { mutableStateOf(followTail) }
    var gesture by remember { mutableStateOf(NestedScrollGesture()) }

    // 内容增长 / cap 变化（maxValue 在布局后才更新）→ 到达新底：跟尾则贴底，否则保持偏移（CHAT-12/13）。
    LaunchedEffect(scrollState.maxValue, contentVersion) {
        if (followTail && following && !gesture.active && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val contentHeightDp = (scrollState.maxValue + viewportPx) / density
    val geometry = ProcessCapFade.geometry(
        contentHeightDp = contentHeightDp,
        viewportHeightDp = viewportPx / density,
        scrollOffsetDp = scrollState.value / density,
    )
    val maskStops: List<Pair<Float, Float>> = if (!showFade || !geometry.hasFade || geometry.viewportHeightDp <= 0f) {
        emptyList()
    } else {
        geometry.stops.map { it.positionDp / geometry.viewportHeightDp to it.alpha }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = capDp)
            .clipToBounds()
            .onSizeChanged { viewportPx = it.height }
            // 手势开始快照（G-04 用"手势开始时能不能滚"，而不是到边那帧再判）。
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    gesture = NestedScrollArbiter.beginGesture(
                        canScrollForward = scrollState.canScrollForward,
                        canScrollBackward = scrollState.canScrollBackward,
                    )
                    following = false // 拖动即暂停跟尾（CHAT-12）
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        pressed = event.changes.any { it.pressed }
                    }
                    gesture = NestedScrollArbiter.endGesture()
                    // 抬回距组底 ≤1dp → 恢复贴底（CHAT-13）。
                    following = followTail && (scrollState.maxValue - scrollState.value) / density <= 1f
                }
            }
            .absorbsInnerEdgeScroll { gesture }
            .fadeMask(maskStops),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            content = content,
        )
    }
}

/** mask 渐隐：`BlendMode.DstIn` + 离屏层（否则 DstIn 会打穿到窗口背景）。 */
private fun Modifier.fadeMask(stops: List<Pair<Float, Float>>): Modifier {
    if (stops.isEmpty()) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val colorStops = stops
                .map { (position, alpha) ->
                    position.coerceIn(0f, 1f) to Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))
                }
                .toTypedArray()
            drawRect(
                brush = Brush.verticalGradient(*colorStops),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** 组头容器：供 TurnBlockUi 在非 sticky 时使用（sticky 由 `stickyHeader` 承担）。 */
@Composable
internal fun ProcessGroupHeaderBlockUi(
    block: GroupHeaderBlock,
    runtime: ChatBlockRuntime,
) {
    Box(Modifier.fillMaxWidth().processRowBleed()) {
        ProcessGroupHeaderUi(block = block, background = Color.Transparent) {
            runtime.expansion.toggleGroup(block.key, block.turnId)
            // §3.5 字段 5 落点：未关闭组 → 贴底跟尾；这里只负责"展开后展开体进入视口"（CHAT-19）。
            if (!block.expanded) runtime.requestScrollToBlock(block.key)
        }
    }
}
