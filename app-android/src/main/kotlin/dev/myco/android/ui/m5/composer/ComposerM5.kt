// 验收 ID：CMP-01 CMP-02 CMP-03 CMP-04 CMP-05 CMP-06 CMP-07 CMP-08 CMP-09 CMP-10 CMP-11 CMP-12
//          CMP-13 CMP-14 CMP-15 CMP-16 CMP-17 CMP-18 CMP-19 CMP-20 CMP-21 CMP-22 CMP-23 CMP-24
//          CMP-27 CMP-48 CMP-49 CMP-51 CMP-52 CMP-53 CMP-69 CMP-70 CMP-71 CMP-72 CMP-73 CMP-74
//          CMP-75 CMP-76 CMP-77 CMP-84（视觉/语义部分；状态机与几何断言在 ComposerStateTest）
//
// composer 面板 UI 入口：`composer.input.seat / card / draft-surface / primary / tools.row`（composer.md §3.1–§3.6）。
//
// 结构（全部 in-flow，座位是 chat 内容区底部的固定尾，不是 overlay）：
//   ComposerM5 = 座位（发布 ComposerMetrics）+ 队列条（上方独立条）+ 输入卡 + 座位底部呼吸 8
//   输入卡 = [附件轨 72][长文提示 56][草稿区 D(n)][工具行 48]（卡内水平 padding 8）
//   `/` 候选面板 = 座位 overlay 槽（贴卡上沿外 6，**不占位、不改 H**）
//
// 硬约束落实：
//  * 单键（D-14）：空草稿=停止、有内容=发送/入队；**没有第二颗停止键**（遍历工具行只有 + / 胶囊 / 主键）；
//  * `+` = 附件入口（D-15）；思考开关**不在 composer**（D-12）；命令只走 `/`；`@` 首版不做（D-09/P3-CMP-03）；
//  * 高度发布 = 解析式（§1.4 公式 + TextMeasurer 行数），**不用 onSizeChanged 回写**；
//    三类来源（n / q / 有无轨或提示条）同帧生效，其它视觉变化不改 H（§1.5）；
//  * 命中盒 = 布局盒（G-11）：主键 48、`+` 40、胶囊 44×≤112、轨道项 64、队列动作 40；
//  * 弹层/浮层不占位（G-01）；命令面板不抢焦点（CMP-48）；不做高度动画（G-10）。
//
// 接线约定（Lead 在 ChatScreen.kt 里接）：
//  1) `val controller = rememberComposerController()`；
//  2) 主键发送 → `onSend(snapshot)`；成功后调 `controller.onSendSucceeded()`，失败保持草稿（`onSendFailed()` 只复位守卫）；
//     ⚠️ `onSend` 回调里必须**同步**置 `submitting=true`（[SubmitGuard] 在点击瞬间占位）。结算后置回 false
//     或调 `controller.onSendSucceeded()/onSendFailed()` 复位；否则守卫会一直吞后续点击（BUG-004 防重入的另一面）。
//  3) `AssistantDone` → `controller.onAssistantTurnDone()?.let { controller.sendSnapshot(it) }` 交给自己的发送函数；
//     发送成功 `controller.onAutoSendSettled()`，失败 `controller.onAutoSendFailed(it)`（放回队首 + 暂停）；
//     停止/失败轮调 `controller.pauseQueue()`（队列保留、不自动发）；
//  4) `+` → `onOpenAttachmentPanel()` 打开 `AttachmentSheetM5`；面板选完 → 宿主读元数据后
//     `controller.addAttachments(listOf(IncomingAttachment(...)), limits)`，再把 `controller.nextAttachmentToProcess()`
//     交给 attachment 通道，回执后 `controller.markAttachmentReady/Failed(...)`；
//  5) 主键在飞置 `submitting=true`；`onToast` 接 `M5ToastState.show`。
//
// TODO(lead)：
//  * `ComposerMetrics` → chat 的 `contentPadding.bottom` / 回底钮 `bottom` 同帧消费由 ChatScreen 接线（本组件只发布；
//    发布走 SideEffect，P3-CMP-07 的"0 帧差"需真机核，若不够必须升容器内自定义 Layout）；
//  * 命令面板"点面板外（列表/顶栏）关闭"需要容器 overlay 槽的 outside-tap 接线；本组件用 Popup 处理卡内空白、
//    选中、无匹配、删除 token 四条路径；
//  * 面板关闭回焦：composer CMP-23 要草稿区、attachment ATT-28 要 `+` —— 两个 FocusRequester 都暴露
//    （`draftFocusRequester` / `attachTriggerFocusRequester`），由接线方裁决传哪个给 AttachmentSheetM5.returnFocus；
//  * 图片"处理中"项的移除入口：§3.9 禁预览 vs §3.11 允许移除 → 现实现"处理中不可操作、失败/就绪进灯箱移除"；
//  * M5Lightbox 无底部动作槽（base.primitives §3.14 字段 2 要求）→ 移除动作暂渲染在 page 槽底部，待 primitives 补槽；
//  * 单键"有内容"口径：按 composer.md §3.4 字段 5 = 草稿非空白 || 轨非空；用户口述 `input.isBlank() ? stop() : send()`
//    未区分附件，二者仅在"有附件无文字"时不同，需确认；
//  * 颜色/字号/圆角 token 归 base.theme（B3 后切），当前读 MaterialTheme。
package dev.myco.android.ui.m5.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.attachment.AttachmentAddResult
import dev.myco.android.ui.m5.attachment.AttachmentDraft
import dev.myco.android.ui.m5.attachment.AttachmentDraftItem
import dev.myco.android.ui.m5.attachment.AttachmentGate
import dev.myco.android.ui.m5.attachment.AttachmentItemState
import dev.myco.android.ui.m5.attachment.AttachmentLimits
import dev.myco.android.ui.m5.attachment.AttachmentMachine
import dev.myco.android.ui.m5.attachment.AttachmentRemoval
import dev.myco.android.ui.m5.attachment.AttachmentTransition
import dev.myco.android.ui.m5.attachment.IncomingAttachment
import dev.myco.android.ui.m5.attachment.IntakeResult
import dev.myco.android.ui.m5.primitives.M5Button
import dev.myco.android.ui.m5.primitives.M5ButtonVariant
import dev.myco.android.ui.m5.primitives.M5ToastHost
import dev.myco.android.ui.m5.primitives.reducedMotion
import dev.myco.android.ui.m5.primitives.rememberM5ToastState
import dev.myco.android.ui.m5.attachment.AttachmentSheetM5
import dev.myco.android.ui.m5.attachment.AttachmentSheetCallbacks
import dev.myco.android.ui.m5.attachment.rememberAttachmentSheetState

/**
 * 输入区发布的高度指标（由 `composer.input.seat` 提供，chat 同帧消费）。
 * 纯数据在 [ComposerMetrics]；这里只是 CompositionLocal 包装。
 */
val LocalComposerMetrics = staticCompositionLocalOf { ComposerMetrics() }

/**
 * composer 的本地可变状态（draft / 队列 / 附件轨 / 命令面板 / 长文提示 / 提交防重入）。
 *
 * 纯逻辑（单键语义、队列出队、高度公式、几何、粘贴阈值）都在 [ComposerState.kt] 的纯函数里；
 * 本类只做 Compose 状态持有与派发，便于 Lead 在 ChatScreen 里一个 `remember` 拿到全部。
 *
 * **边界**：草稿与队列都只存内存（与 E-15 同边界：进程杀即丢，CMP-81）；不发任何请求。
 */
@Stable
class ComposerController(initialDraft: String = "") {

    // —— 草稿（TextFieldValue 保留选区与 IME 组字，CMP-05：键盘/断点切换不重挂载也不丢） ——
    var draftValue by mutableStateOf(TextFieldValue(initialDraft, androidx.compose.ui.text.TextRange(initialDraft.length)))
        private set

    val draft: String get() = draftValue.text
    val cursor: Int get() = draftValue.selection.end

    // —— 队列（客户端本地队列，D-26） ——
    var queue by mutableStateOf(ComposerQueue())
        private set

    // —— 草稿附件轨（状态真源 = attachment.AttachmentDraft） ——
    var attachments by mutableStateOf(AttachmentDraft())
        private set

    // —— 命令候选关闭记忆 / 长文提示 / 提交守卫 ——
    var paletteMemory by mutableStateOf(CommandPaletteMemory())
        private set
    var pasteHint by mutableStateOf<LongTextHint?>(null)
        private set

    val submitGuard = SubmitGuard()

    /** 当前是否"有内容"（单键判据：草稿非空白 **或** 附件轨非空）。 */
    val hasContent: Boolean get() = draft.isNotBlank() || !attachments.isEmpty

    val gate: AttachmentGate get() = attachments.gate

    private var clearedDraftSnapshot: String? = null

    // ------------------------------------------------------------------ 草稿

    /**
     * 草稿文本/选区变化（接线方直接把 `TextFieldValue` 回传）。
     * 同时做两件事：粘贴长文提示判定（§3.14）、命令候选关闭记忆维护（§3.15 字段 5 ④）。
     */
    fun onDraftValueChange(value: TextFieldValue) {
        val oldText = draftValue.text
        val oldCursor = draftValue.selection.end
        draftValue = value
        val insertion = PasteLogic.detectInsertion(oldText, value.text)
        if (insertion != null && PasteLogic.shouldOfferAttachment(insertion)) {
            pasteHint = LongTextHint(
                text = insertion.inserted,
                start = insertion.start,
                chars = insertion.chars,
                lines = insertion.lines,
            )
        }
        if (value.text != oldText || value.selection.end != oldCursor) {
            paletteMemory = CommandPaletteLogic.memoryAfterTextChange(
                paletteMemory,
                value.text,
                value.selection.end,
            )
        }
    }

    /** 程序化改草稿（撤回回填 / 命令移除 token / /clear 撤销）：光标给到末尾或指定位置。 */
    fun setDraftText(text: String, cursor: Int = text.length) {
        val safe = cursor.coerceIn(0, text.length)
        draftValue = TextFieldValue(text, androidx.compose.ui.text.TextRange(safe))
        paletteMemory = CommandPaletteLogic.memoryAfterTextChange(paletteMemory, text, safe)
        if (pasteHint != null && text.isBlank()) pasteHint = null
    }

    /** `/clear`：清空草稿并记住快照（6000ms Toast 可撤销）。 */
    fun clearDraftForUndo(): String? {
        val snapshot = draftValue.text
        clearedDraftSnapshot = snapshot
        setDraftText("")
        pasteHint = null
        return snapshot.ifBlank { null }
    }

    /** `/clear` 撤销：草稿仍为空才恢复（避免覆盖用户撤销后新打的字）。 */
    fun undoClear(): Boolean {
        val snapshot = clearedDraftSnapshot ?: return false
        if (draft.isNotBlank()) return false
        setDraftText(snapshot)
        clearedDraftSnapshot = null
        return true
    }

    /** 长文提示「保留」：关闭提示，草稿原样（CMP-46）。 */
    fun dismissPasteHint() {
        pasteHint = null
    }

    /** 长文提示「转为附件」：从草稿删掉本次粘贴文本，返回该文本（通道归 attachment，CMP-45）。 */
    fun takePasteHintForAttachment(): String? {
        val hint = pasteHint ?: return null
        val newDraft = PasteLogic.removeInsertion(draft, hint)
        if (newDraft == draft && draft.isNotEmpty()) {
            // 草稿在提示出现后被改动（插入区已不匹配）→ 放弃转换，避免"附件+原文"重复
            pasteHint = null
            return null
        }
        setDraftText(newDraft)
        pasteHint = null
        return hint.text
    }

    // ------------------------------------------------------------------ 命令候选

    fun dismissPalette() {
        val token = CommandPaletteLogic.detectSlashToken(draft, cursor) ?: return
        paletteMemory = paletteMemory.dismissFor(token)
    }

    /** 选中命令后清空"手动关闭"记忆（新 token 可再次自动弹）。 */
    fun clearPaletteMemory() {
        paletteMemory = CommandPaletteMemory()
    }

    /** `/` 候选面板当前可见性（无匹配/未被关闭才可见，CMP-50）。 */
    fun paletteVisibility(): PaletteVisibility =
        CommandPaletteLogic.visibility(draft, cursor, paletteMemory)

    // ------------------------------------------------------------------ 附件

    /** intake 后整批入队（失败整批拒绝，返回结果供 toast）。 */
    fun addAttachments(
        batch: List<IncomingAttachment>,
        limits: AttachmentLimits,
        nowMs: Long = System.currentTimeMillis(),
    ): AttachmentAddResult {
        val result = AttachmentMachine.addBatch(attachments, batch, limits, nowMs)
        attachments = result.draft
        return result
    }

    fun nextAttachmentToProcess(): AttachmentDraftItem? = attachments.nextToProcess

    fun markAttachmentReady(
        id: String,
        remoteId: String? = null,
        processedSizeBytes: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): AttachmentTransition {
        val transition = AttachmentMachine.markReady(attachments, id, nowMs, remoteId, processedSizeBytes)
        attachments = transition.draft
        return transition
    }

    fun markAttachmentFailed(
        id: String,
        reason: String,
        retryable: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): AttachmentTransition {
        val transition = AttachmentMachine.markFailed(attachments, id, reason, retryable, nowMs)
        attachments = transition.draft
        return transition
    }

    /** 移除（处理中 → discard；就绪/失败 → 立即删；输出驱动宿主删除语义）。 */
    fun removeAttachment(id: String, nowMs: Long = System.currentTimeMillis()): AttachmentRemoval {
        val removal = AttachmentMachine.remove(attachments, id, nowMs)
        attachments = removal.draft
        return removal
    }

    /** 重试（在飞连点幂等；不新增附件、顺序不变，CMP-38/39）。 */
    fun retryAttachment(id: String, nowMs: Long = System.currentTimeMillis()): AttachmentTransition {
        val transition = AttachmentMachine.beginRetry(attachments, id, nowMs)
        attachments = transition.draft
        return transition
    }

    /** 30s 超时 tick（宿主可用 LaunchedEffect 周期调用；返回是否有变化）。 */
    fun tickAttachmentTimeouts(nowMs: Long = System.currentTimeMillis()): Boolean {
        val transition = AttachmentMachine.tickTimeouts(attachments, nowMs)
        if (transition.changed) attachments = transition.draft
        return transition.changed
    }

    // ------------------------------------------------------------------ 队列

    /** 发送成功 → 清空草稿与轨（附件随消息提交，CMP-19）；失败**保留**（CMP-16）。 */
    fun onSendSucceeded() {
        clearDraftAndAttachments()
        submitGuard.reset()
    }

    /**
     * 只清草稿/轨、**不动提交守卫**（提交瞬间乐观清空用；守卫保持到结算，
     * 防止"清空后同一帧连点两次"绕过防重入）。
     */
    fun clearDraftAndAttachments() {
        setDraftText("")
        attachments = AttachmentDraft()
        pasteHint = null
    }

    /** 发送失败 → 只复位提交守卫，草稿/附件原样保留（BUG-004）。 */
    fun onSendFailed() {
        submitGuard.reset()
    }

    /** 生成中按发送 → 入本地队列（**不发请求**，CMP-79）；清空草稿与轨（内容已进队列）。 */
    fun enqueueCurrent(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!hasContent) return false
        if (queue.busy) return false // 队列动作/自动发送在飞 → 串行化
        val readyItems = attachments.items.filter { it.state == AttachmentItemState.READY }
        queue = ComposerQueueMachine.enqueue(
            queue = queue,
            text = draft.trim(),
            attachmentIds = attachments.readyRemoteIds(),
            attachmentNames = readyItems.map { it.name },
            nowMs = nowMs,
        )
        setDraftText("")
        attachments = AttachmentDraft()
        pasteHint = null
        submitGuard.reset()
        return true
    }

    /**
     * 本轮正常结束（`AssistantDone`）→ 解除暂停并出队**第 1 条**。
     * 返回值由接线方走与手动发送同一条提交路径；无队列返回 null（CMP-79 / CMP-80）。
     * 幂等/串行：自动发送在飞（busy）或队列空时不重复出队。
     */
    fun onAssistantTurnDone(): QueuedMessage? {
        if (queue.busy) return null
        val result = ComposerQueueMachine.onAssistantDone(queue)
        queue = result.queue
        return result.message
    }

    /** 自动发送结算（成功/失败后由接线方调）。 */
    fun onAutoSendSettled() {
        queue = ComposerQueueMachine.onAutoSendSettled(queue)
    }

    /** 自动发送失败 → 放回队首 + 暂停（不丢消息，CMP-80）。 */
    fun onAutoSendFailed(message: QueuedMessage) {
        queue = ComposerQueueMachine.onAutoSendFailed(queue, message)
    }

    /** 停止 / 本轮失败 → 队列保留并暂停（不清空、不自动发）。 */
    fun pauseQueue() {
        queue = ComposerQueueMachine.pause(queue)
    }

    fun recallQueue(id: Long): QueuedMessage? {
        if (queue.busy) return null
        val result = ComposerQueueMachine.recall(queue, id, draft)
        queue = result.queue
        if (result.recalled != null) setDraftText(result.draft)
        return result.recalled
    }

    fun deleteQueue(id: Long): QueueDeleteResult {
        if (queue.busy) return QueueDeleteResult(queue)
        val result = ComposerQueueMachine.delete(queue, id)
        queue = result.queue
        return result
    }

    fun undoDeleteQueue(message: QueuedMessage, index: Int) {
        queue = ComposerQueueMachine.undoDelete(queue, message, index)
    }

    fun editQueue(id: Long, newText: String): Boolean {
        if (queue.busy) return false
        val result = ComposerQueueMachine.edit(queue, id, newText)
        queue = result.queue
        return result.changed
    }

    /** 提交瞬间冻结的快照（§3.5 字段 5 ②）。 */
    fun sendSnapshot(fromQueue: Boolean = false, queueMessageId: Long? = null): ComposerSendSnapshot =
        ComposerSendSnapshot(
            text = draft.trim(),
            attachmentIds = attachments.readyRemoteIds(),
            fromQueue = fromQueue,
            queueMessageId = queueMessageId,
        )

    /** 自动出队后 → 发送快照（接线方直接把它交给自己的发送函数；与手动发送同一条提交路径）。 */
    fun sendSnapshot(message: QueuedMessage): ComposerSendSnapshot =
        ComposerSendSnapshot(
            text = message.text,
            attachmentIds = message.attachmentIds,
            fromQueue = true,
            queueMessageId = message.id,
        )
}

/** 记住一个 composer 控制器（会话内单实例；切会话可换 key）。 */
@Composable
fun rememberComposerController(initialDraft: String = ""): ComposerController =
    remember { ComposerController(initialDraft) }

/** 宿主 Toast 回调：`onToast(message, actionLabel?, onAction?)`。 */
typealias ComposerToastCallback = (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit

/**
 * 输入区入口（座位 + 队列条 + 输入卡 + `/` 候选浮层）。
 *
 * @param controller 本地状态（draft/queue/rail/命令/长文提示）
 * @param running 生成中（单键语义切换 + 停止）
 * @param submitting 提交中（主键 busy、`+` 禁用、草稿只读不重挂载）
 * @param locked 只读会话（首版不可达，形态保留）
 * @param connected 连接态（未连接 → 禁用发送，草稿保留）
 * @param initializing 会话初始化（主键禁用、hint 提示）
 * @param modelLabel 模型胶囊主名（内容归 model 面板；本组件只管座位与 112 上限）
 * @param reasoningLabel 档位文案（降级时先丢它，E-07=A）
 * @param attachTriggerFocusRequester `+` 的返回焦点（接线方同时传给 AttachmentSheetM5.returnFocus）
 * @param draftFocusRequester 草稿区 FocusRequester（CMP-23：面板关闭后回焦草稿）；不传则内部自建
 * @param availableHeightDp A = 键盘打开时聊天列表可视高（§1.4 降档公式的唯一外部输入）
 * @param contentFontSizeSp 内容字号（E-13 字号轴 10–22；LH = 24 + (字号−16)）
 * @param onMetricsChanged 发布值回调（chat 同帧消费；本组件只发布，不写 chat）
 */
@Composable
fun ComposerM5(
    controller: ComposerController,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    submitting: Boolean = false,
    locked: Boolean = false,
    connected: Boolean = true,
    initializing: Boolean = false,
    modelLabel: String? = null,
    reasoningLabel: String? = null,
    onOpenModel: () -> Unit = {},
    onOpenAttachmentPanel: () -> Unit = {},
    attachTriggerFocusRequester: FocusRequester? = null,
    draftFocusRequester: FocusRequester? = null,
    onSend: (ComposerSendSnapshot) -> Unit = {},
    onStop: () -> Unit = {},
    onRemoveAttachment: (AttachmentRemoval) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    onPreviewAttachment: (String) -> Unit = {},
    onConvertLongTextToAttachment: (String) -> Unit = {},
    onCommand: (ComposerCommand) -> Unit = {},
    onToast: ComposerToastCallback = { _, _, _ -> },
    availableHeightDp: Dp = LocalConfiguration.current.screenHeightDp.dp,
    contentFontSizeSp: Float = 16f,
    onMetricsChanged: (ComposerMetrics) -> Unit = {},
) {
    val env = ComposerEnv(
        running = running,
        submitting = submitting,
        locked = locked,
        connected = connected,
        initializing = initializing,
    )
    val gate = controller.gate
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val internalDraftFocus = remember { FocusRequester() }
    val focusDraft = draftFocusRequester ?: internalDraftFocus

    val lineHeightDp = with(density) {
        ComposerMetricsCalculator.lineHeightSp(contentFontSizeSp).sp.toDp()
    }
    val typography = MaterialTheme.typography
    val draftStyle = remember(typography, contentFontSizeSp) {
        typography.bodyLarge.copy(
            fontSize = contentFontSizeSp.sp,
            lineHeight = ComposerMetricsCalculator.lineHeightSp(contentFontSizeSp).sp,
        )
    }

    // 提交中结束 → 复位防重入守卫（BUG-004：失败/成功后都允许下一次新快照）
    LaunchedEffect(submitting) {
        if (!submitting) controller.submitGuard.reset()
    }
    // 附件 30s 超时（§3.8 字段 10）
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            controller.tickAttachmentTimeouts()
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = minOf(
            this.maxWidth - ComposerLayout.CARD_HORIZONTAL_MARGIN_DP.dp * 2,
            ComposerLayout.CARD_MAX_WIDTH_DP.dp,
        ).coerceAtLeast(0.dp)
        val toolsInnerWidth = (cardWidth - ComposerLayout.CARD_INNER_PADDING_DP.dp * 2).coerceAtLeast(0.dp)
        val draftInnerWidthPx = with(density) {
            (cardWidth - ComposerLayout.CARD_INNER_PADDING_DP.dp * 2 -
                ComposerLayout.DRAFT_HORIZONTAL_PADDING_DP.dp * 2).toPx().toInt().coerceAtLeast(1)
        }
        val hasRail = !controller.attachments.isEmpty
        val hasHint = controller.pasteHint != null
        // §3.17 字段 10：只读/锁定会话 → 队列条整条不渲染（也不计入 H）
        val queueCount = if (env.locked) 0 else controller.queue.count
        val lineCap = ComposerMetricsCalculator.lineCap(
            availableHeightDp = availableHeightDp.value,
            hasRail = hasRail,
            queueCount = queueCount,
            lineHeightDp = lineHeightDp.value,
        )
        // 行数 = TextMeasurer 的解析式测量（不用 onSizeChanged 回写；§1.5 第 1 条）
        val measuredLineCount = remember(measurer, controller.draft, draftInnerWidthPx, draftStyle) {
            if (controller.draft.isEmpty()) {
                1
            } else {
                runCatching {
                    measurer.measure(
                        text = AnnotatedString(controller.draft),
                        style = draftStyle,
                        constraints = Constraints(maxWidth = draftInnerWidthPx),
                    ).lineCount
                }.getOrDefault(1)
            }
        }
        val metrics = ComposerMetricsCalculator.metrics(
            lineCount = measuredLineCount,
            queueCount = queueCount,
            hasRail = hasRail,
            hasHint = hasHint,
            lineCap = lineCap,
            lineHeightDp = lineHeightDp.value,
            visualState = ComposerMachine.visualState(env, gate),
        )
        SideEffect { onMetricsChanged(metrics) }

        val palette = controller.paletteVisibility()
        val paletteHeight = ComposerMetricsCalculator.paletteHeightDp(
            itemCount = palette.commands.size,
            availableHeightDp = availableHeightDp.value,
            cardHeightDp = metrics.cardHeightDp,
        )

        CompositionLocalProvider(LocalComposerMetrics provides metrics) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.seatHeightDp.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (queueCount > 0) {
                        CenteredColumn {
                            QueueStrip(
                                queue = controller.queue,
                                actionsEnabled = !env.submitting && controller.queue.actionsEnabled,
                                onEdit = { id, text ->
                                    controller.dismissPalette()
                                    val saved = controller.editQueue(id, text)
                                    if (!saved) onToast("内容不能为空", null, null)
                                    saved
                                },
                                onRecall = { id ->
                                    controller.dismissPalette()
                                    val recalled = controller.recallQueue(id)
                                    if (recalled != null) {
                                        onToast("已回填到输入框", null, null)
                                    }
                                },
                                onDelete = { id ->
                                    controller.dismissPalette()
                                    val result = controller.deleteQueue(id)
                                    if (result.deleted != null) {
                                        onToast("已删除 1 条待发", "撤销") {
                                            controller.undoDeleteQueue(result.deleted, result.index)
                                        }
                                    }
                                },
                            )
                        }
                        // 队列条与卡之间 4dp（§1.3）
                        Spacer(Modifier.height(ComposerLayout.QUEUE_GAP_DP.dp))
                    }

                    // ---------------- 输入卡 ----------------
                    CenteredColumn {
                        ComposerCard(
                            controller = controller,
                            env = env,
                            gate = gate,
                            metrics = metrics,
                            cardWidth = cardWidth,
                            toolsInnerWidth = toolsInnerWidth,
                            draftStyle = draftStyle,
                            lineHeightDp = lineHeightDp,
                            draftFocus = focusDraft,
                            paletteVisible = palette.visible,
                            modelLabel = modelLabel,
                            reasoningLabel = reasoningLabel,
                            attachTriggerFocusRequester = attachTriggerFocusRequester,
                            onOpenModel = onOpenModel,
                            onOpenAttachmentPanel = onOpenAttachmentPanel,
                            onSend = onSend,
                            onStop = onStop,
                            onRemoveAttachment = onRemoveAttachment,
                            onRetryAttachment = onRetryAttachment,
                            onPreviewAttachment = onPreviewAttachment,
                            onConvertLongTextToAttachment = onConvertLongTextToAttachment,
                            onToast = onToast,
                        )
                    }

                    // 座位底部呼吸带 8dp（容器在座位下沿之外垫 insets）
                    Spacer(Modifier.height(ComposerLayout.SEAT_BOTTOM_BREATH_DP.dp))
                }

                // ---------------- `/` 候选面板（overlay 槽，不占位、不改 H） ----------------
                // 用 Popup 而不是座位内偏移：面板贴在卡上沿外 6dp 时会超出座位（in-flow）边界，
                // Compose 的命中测试被父节点边界裁剪 → 座位外画的浮层点不到。Popup 是独立 window，
                // 不占位（G-01）、不抢焦点（focusable=false → IME/光标留在草稿区，CMP-48）。
                val paletteToken = palette.token
                if (palette.visible && paletteToken != null) {
                    val cardTopPx = with(density) { metrics.cardTopDp.dp.roundToPx() }
                    val gapPx = with(density) { ComposerLayout.PALETTE_ANCHOR_GAP_DP.dp.roundToPx() }
                    val positionProvider = remember(cardTopPx, gapPx) {
                        ComposerPalettePopupPosition(cardTopPx = cardTopPx, gapPx = gapPx)
                    }
                    Popup(
                        popupPositionProvider = positionProvider,
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        ),
                    ) {
                        CommandPalette(
                            commands = palette.commands,
                            width = cardWidth,
                            height = paletteHeight.dp,
                            onSelect = { command ->
                                val selection = CommandPaletteLogic.select(controller.draft, paletteToken, command)
                                controller.setDraftText(selection.draft, selection.cursor)
                                controller.clearPaletteMemory()
                                when (command.id) {
                                    ComposerCommandId.ATTACH -> onOpenAttachmentPanel()
                                    ComposerCommandId.CLEAR -> {
                                        val cleared = controller.clearDraftForUndo()
                                        if (cleared != null) {
                                            onToast("已清空输入", "撤销") { controller.undoClear() }
                                        }
                                    }
                                    else -> onCommand(command)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * `/` 候选面板的 Popup 定位：水平与卡同心，底边贴卡上沿外 6dp。
 *
 * Popup 的锚点是座位（本组件根节点）——用座位左上角 + 解析式 cardTop 反推，不依赖测量时机。
 */
private class ComposerPalettePopupPosition(
    private val cardTopPx: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val y = anchorBounds.top + cardTopPx - popupContentSize.height - gapPx
        return IntOffset(x, y)
    }
}

/** 居中列：座位内所有块水平居中且宽 ≤ 752（与 chat 内容列同一条竖向对齐线）。 */@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ComposerLayout.CARD_HORIZONTAL_MARGIN_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.widthIn(max = ComposerLayout.CARD_MAX_WIDTH_DP.dp).fillMaxWidth()) {
            Column { content() }
        }
    }
}

/**
 * 输入卡：`[附件轨][长文提示][草稿区][工具行]`，卡内水平 padding 8、垂直 0。
 * 点卡内空白 = 回焦草稿（G-11：卡不成为语义按钮，只用 pointerInput 做指针便利）。
 */
@Composable
private fun ComposerCard(
    controller: ComposerController,
    env: ComposerEnv,
    gate: AttachmentGate,
    metrics: ComposerMetrics,
    cardWidth: Dp,
    toolsInnerWidth: Dp,
    draftStyle: TextStyle,
    lineHeightDp: Dp,
    draftFocus: FocusRequester,
    paletteVisible: Boolean,
    modelLabel: String?,
    reasoningLabel: String?,
    attachTriggerFocusRequester: FocusRequester?,
    onOpenModel: () -> Unit,
    onOpenAttachmentPanel: () -> Unit,
    onSend: (ComposerSendSnapshot) -> Unit,
    onStop: () -> Unit,
    onRemoveAttachment: (AttachmentRemoval) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onPreviewAttachment: (String) -> Unit,
    onConvertLongTextToAttachment: (String) -> Unit,
    onToast: ComposerToastCallback,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val primaryUi = ComposerMachine.primaryUi(env, controller.hasContent, gate)
    val plusEnabled = ComposerMachine.attachTriggerEnabled(env, gate)
    val plusDisabledReason = ComposerMachine.attachTriggerDisabledReason(env, gate)
    // 点任何工具/轨道原子都算"点面板外"（命令候选关闭并记住 token，§3.15 字段 5 ④）
    val closePalette: () -> Unit = {
        if (paletteVisible) controller.dismissPalette()
    }
    val placeholder = when {
        env.locked -> "当前会话只读"
        !env.connected -> "未连接，暂不能发送"
        env.initializing -> "连接后才能发送"
        else -> "输入消息…"
    }

    Column(
        modifier = Modifier
            .widthIn(max = ComposerLayout.CARD_MAX_WIDTH_DP.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .then(
                if (env.running) {
                    Modifier.border(1.dp, colors.primary.copy(alpha = 0.5f), shape)
                } else {
                    Modifier
                },
            )
            .pointerInput(paletteVisible) {
                detectTapGestures {
                    if (paletteVisible) {
                        controller.dismissPalette()
                    } else if (!env.locked) {
                        runCatching { draftFocus.requestFocus() }
                    }
                }
            },
    ) {
        // 附件轨（空轨不渲染，卡高同帧 -72）
        if (!controller.attachments.isEmpty) {
            Box(Modifier.fillMaxWidth().height(ComposerLayout.RAIL_HEIGHT_DP.dp)) {
                AttachmentRail(
                    items = controller.attachments.items,
                    actionsEnabled = !env.submitting && !env.locked,
                    onRemove = { id ->
                        closePalette()
                        val removal = controller.removeAttachment(id)
                        if (removal.changed) onRemoveAttachment(removal)
                    },
                    onRetry = { id ->
                        closePalette()
                        val transition = controller.retryAttachment(id)
                        if (transition.changed) onRetryAttachment(id)
                    },
                    onPreview = { id ->
                        closePalette()
                        onPreviewAttachment(id)
                    },
                )
            }
        }

        // 长文提示条（参与 H；出现即 +56）
        val pasteHint = controller.pasteHint
        if (pasteHint != null) {
            LongTextHintRow(
                hint = pasteHint,
                onConvert = {
                    closePalette()
                    val text = controller.takePasteHintForAttachment()
                    if (text != null) {
                        onConvertLongTextToAttachment(text)
                        onToast("已转为附件", null, null)
                    }
                },
                onKeep = {
                    closePalette()
                    controller.dismissPasteHint()
                },
            )
        }

        // 草稿区（镜像撑高：按行数算高，n=cap 后内滚）
        Box(Modifier.fillMaxWidth().height(metrics.draftHeightDp.dp)) {
            BasicTextField(
                value = controller.draftValue,
                onValueChange = { controller.onDraftValueChange(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        // 文字距卡缘 = 卡内 8 + 草稿区 6 = 14（§3.3 字段 2）；与行数测量的
                        // 可用宽（cardWidth − 28）严格一致，否则 n 会高估、卡比实际文字高
                        horizontal = (ComposerLayout.CARD_INNER_PADDING_DP +
                            ComposerLayout.DRAFT_HORIZONTAL_PADDING_DP).dp,
                        vertical = ComposerMetricsCalculator
                            .draftVerticalPaddingDp(lineHeightDp.value).dp,
                    )
                    .focusRequester(draftFocus)
                    .semantics {
                        // §3.3 字段 11：contentDescription = 占位文案（随状态换）；只读态给原因
                        contentDescription = placeholder
                        when {
                            env.locked -> stateDescription = "当前会话只读"
                            !env.connected -> stateDescription = "未连接，暂不能发送"
                            env.initializing -> stateDescription = "连接后才能发送"
                        }
                    },
                textStyle = draftStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                // 提交中：草稿只读但**不重挂载**（保 IME 组字/选区，§3.5 字段 4）；只读态可滚动/选字
                readOnly = env.locked || env.submitting,
                decorationBox = { inner ->
                    Box {
                        if (controller.draft.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = draftStyle,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        // 工具行：`[+40][胶囊 ≤112][flex][主键 48]`，单行 nowrap、键位固定
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ComposerLayout.TOOLS_ROW_DP.dp)
                .padding(horizontal = ComposerLayout.CARD_INNER_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // `+` = 附件入口（D-15）；40×40 = 命中盒
            Box(
                modifier = Modifier
                    .size(ComposerLayout.PLUS_SIZE_DP.dp)
                    .clip(CircleShape)
                    .then(
                        if (attachTriggerFocusRequester != null) {
                            Modifier.focusRequester(attachTriggerFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .alpha(if (plusEnabled) 1f else 0.38f)
                    .clickable(
                        enabled = plusEnabled,
                        role = Role.Button,
                        onClickLabel = "添加附件",
                        onClick = {
                            closePalette()
                            onOpenAttachmentPanel()
                        },
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = "添加附件"
                        if (!plusEnabled) {
                            disabled()
                            stateDescription = plusDisabledReason ?: "不可用"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                M5Icon(M5Icons.plus, Modifier.size(22.dp), colors.onSurfaceVariant)
            }

            ModelCapsule(
                availableWidthDp = toolsInnerWidth.value,
                modelLabel = modelLabel,
                reasoningLabel = reasoningLabel,
                onClick = {
                    closePalette()
                    onOpenModel()
                },
            )

            Spacer(Modifier.weight(1f))

            // 主键：单键（空=停止 / 有内容=发送或入队），48×48 = 命中盒，永不位移
            PrimaryKey(
                ui = primaryUi,
                onClick = {
                    closePalette()
                    when (val intent = ComposerMachine.primaryIntent(env, controller.hasContent, gate)) {
                        ComposerPrimaryIntent.Stop -> onStop()
                        ComposerPrimaryIntent.Send -> {
                            if (controller.submitGuard.tryEnter()) {
                                onSend(controller.sendSnapshot())
                            }
                        }
                        ComposerPrimaryIntent.Enqueue -> {
                            if (controller.enqueueCurrent()) {
                                onToast("已排队，共 ${controller.queue.count} 条", null, null)
                            }
                        }
                        is ComposerPrimaryIntent.Blocked -> onToast(intent.toast, null, null)
                        ComposerPrimaryIntent.Ignored -> Unit
                    }
                },
            )
        }
    }
}

/** 模型胶囊：高 44、max-width 112；降级序列（丢档位 → 丢名 → 只图标 40）。始终可点。 */
@Composable
private fun ModelCapsule(
    availableWidthDp: Float,
    modelLabel: String?,
    reasoningLabel: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    // 字号/行高固定 13/18；remember 防止每帧重组都重新测量文字
    val labelStyle = remember(typography) {
        typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp)
    }
    val name = modelLabel?.takeIf { it.isNotBlank() } ?: "模型"
    val reasoning = reasoningLabel?.takeIf { it.isNotBlank() }
    val nameWidthDp = remember(measurer, name, labelStyle) {
        with(density) {
            measurer.measure(AnnotatedString(name), style = labelStyle).size.width.toDp().value
        }
    }
    val reasoningWidthDp = remember(measurer, reasoning, labelStyle) {
        if (reasoning == null) {
            0f
        } else {
            with(density) {
                measurer.measure(AnnotatedString(" · $reasoning"), style = labelStyle).size.width.toDp().value
            }
        }
    }
    val plan = ComposerGeometry.capsulePlan(
        availableWidthDp = availableWidthDp,
        nameWidthDp = nameWidthDp,
        reasoningWidthDp = reasoningWidthDp,
        hasReasoning = reasoning != null,
    )
    Row(
        modifier = Modifier
            .height(ComposerLayout.CAPSULE_HEIGHT_DP.dp)
            .width(plan.widthDp.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = "打开模型与推理档位", onClick = onClick)
            .padding(horizontal = 10.dp)
            .semantics {
                role = Role.Button
                contentDescription = buildString {
                    append("模型 $name")
                    if (reasoning != null) append("，档位 $reasoning")
                    append("，打开模型与推理档位")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        M5Icon(M5Icons.spark, Modifier.size(14.dp), colors.primary)
        if (plan.showName) {
            Text(
                text = name,
                style = labelStyle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plan.showReasoning && reasoning != null) {
            Text(
                text = "· $reasoning",
                style = labelStyle,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plan.form != ModelCapsuleForm.ICON_ONLY) {
            M5Icon(M5Icons.chevronDown, Modifier.size(10.dp), colors.onSurfaceVariant)
        }
    }
}

/** 主键（48×48 = 布局盒 = 命中盒；图标直切，不做 alpha 离屏层/子组合）。 */
@Composable
private fun PrimaryKey(
    ui: ComposerPrimaryUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // 禁用/拦发：用**色值 alpha**表达 38%，不用 Modifier.alpha 离屏层 —— 模拟器实测
    // "键盘弹出后工具行重排" 时离屏层不重绘，主键语义节点在、画面却空白（B2 实测 bug）。
    val dim = if (ui.dimmed) 0.38f else 1f
    val background = when (ui.icon) {
        PrimaryIcon.SEND -> if (ui.interactive) colors.primary.copy(alpha = dim) else colors.surfaceVariant.copy(alpha = dim)
        PrimaryIcon.STOP -> colors.errorContainer.copy(alpha = dim)
        PrimaryIcon.BUSY -> colors.surfaceVariant.copy(alpha = dim)
    }
    val tint = when (ui.icon) {
        PrimaryIcon.SEND -> if (ui.interactive) colors.onPrimary else colors.onSurfaceVariant
        PrimaryIcon.STOP -> colors.error
        PrimaryIcon.BUSY -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(ComposerLayout.PRIMARY_SIZE_DP.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(
                enabled = ui.interactive,
                role = Role.Button,
                onClickLabel = ui.contentDescription,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = ui.contentDescription
                ui.stateDescription?.let { stateDescription = it }
                if (!ui.interactive) disabled()
                if (ui.liveRegion) liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        when (ui.icon) {
            PrimaryIcon.SEND -> M5Icon(M5Icons.send, Modifier.size(24.dp), tint)
            PrimaryIcon.STOP -> M5Icon(M5Icons.stop, Modifier.size(24.dp), tint)
            PrimaryIcon.BUSY -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = tint,
                strokeWidth = 2.dp,
            )
        }
    }
}

/** 长文提示条（56dp：文字 13/20 + 两个 48 高按钮；出现即改卡高，参与 H）。 */
@Composable
private fun LongTextHintRow(
    hint: LongTextHint,
    onConvert: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComposerLayout.HINT_HEIGHT_DP.dp)
            .padding(horizontal = ComposerLayout.CARD_INNER_PADDING_DP.dp)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "粘贴内容较长（${hint.chars} 字符），可转为附件"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        Text(
            text = "内容较长，转为附件？",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        M5Button(
            text = "保留",
            variant = M5ButtonVariant.SECONDARY,
            minWidth = 64.dp,
            onClick = onKeep,
        )
        M5Button(
            text = "转为附件",
            variant = M5ButtonVariant.PRIMARY,
            minWidth = 64.dp,
            onClick = onConvert,
        )
    }
}

// ---------------------------------------------------------------------------
// 开发自验入口（`adb shell am start ... --ez m5_composer_demo true`）
// ---------------------------------------------------------------------------

/**
 * B2 composer 渲染验证页（开发用，不串产品流程）。
 *
 * 验证：座位 in-flow、卡高解析式（H 显示在顶栏）、单键语义、队列条、附件轨、长文提示、`+` sheet。
 * 真实图片/文件来自系统 picker；录音/相机等不可用的模拟器上走失败 toast（sheet 自己报错）。
 */
@Composable
fun ComposerDemoM5() {
    val controller = rememberComposerController()
    val toastState = rememberM5ToastState()
    val sheetState = rememberAttachmentSheetState()
    var running by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lastMetrics by remember { mutableStateOf(ComposerMetrics()) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Text(
            text = "B2 composer demo · H=${"%.0f".format(lastMetrics.seatHeightDp)}dp " +
                "n=${lastMetrics.lineCount}/${lastMetrics.lineCap}",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M5Button(text = if (running) "停止生成" else "开始生成", onClick = { running = !running })
            M5Button(text = "排队×2", onClick = {
                controller.setDraftText("补充一句：先别改配置")
                controller.enqueueCurrent()
                controller.setDraftText("再补充一条")
                controller.enqueueCurrent()
                controller.setDraftText("")
            })
            M5Button(text = "断开连接", variant = if (connected) M5ButtonVariant.SECONDARY else M5ButtonVariant.PRIMARY,
                onClick = { connected = !connected })
            M5Button(text = "只读", variant = if (locked) M5ButtonVariant.PRIMARY else M5ButtonVariant.SECONDARY,
                onClick = { locked = !locked })
        }
        Spacer(Modifier.weight(1f))
        ComposerM5(
            controller = controller,
            running = running,
            submitting = submitting,
            locked = locked,
            connected = connected,
            modelLabel = "DeepSeek V3",
            reasoningLabel = "high",
            onOpenModel = { toastState.show("打开模型面板（demo）") },
            onOpenAttachmentPanel = { sheetState.visible = true },
            onSend = { snapshot ->
                toastState.show("发送：${snapshot.text.ifBlank { "附件 ${snapshot.attachmentIds.size} 个" }}")
                controller.onSendSucceeded()
            },
            onStop = {
                running = false
                controller.pauseQueue()
                toastState.show("已停止")
            },
            onRemoveAttachment = { removal ->
                toastState.show("移除附件 ${removal.removed?.name ?: "(已移除)"}")
            },
            onRetryAttachment = { id -> toastState.show("重试附件 $id（demo 无通道）") },
            onPreviewAttachment = { id -> toastState.show("预览附件 $id") },
            onConvertLongTextToAttachment = { text ->
                toastState.show("转为附件：${text.length} 字符（demo 无通道）")
            },
            onCommand = { command -> toastState.show("执行命令 ${command.slashName}（demo）") },
            onToast = { message, actionLabel, action ->
                toastState.show(message, actionLabel, action)
            },
            onMetricsChanged = { lastMetrics = it },
        )
    }
    M5ToastHost(state = toastState)
    AttachmentSheetM5(
        state = sheetState,
        callbacks = AttachmentSheetCallbacks(
            onSourcesPicked = { sources ->
                toastState.show("已选 ${sources.size} 个附件（demo 不落盘）")
            },
            onError = { message -> toastState.show(message) },
            onCameraTempFile = { file -> file.delete() },
        ),
    )
}
