// 验收 ID：CMP-01 CMP-02 CMP-04 CMP-07 CMP-09 CMP-10 CMP-11 CMP-12 CMP-13 CMP-14 CMP-15 CMP-18
//          CMP-19 CMP-20 CMP-21 CMP-31 CMP-34 CMP-44 CMP-52 CMP-58 CMP-60 CMP-61 CMP-62 CMP-75
//          CMP-79 CMP-80 CMP-82 CMP-83（纯逻辑部分）
//
// composer 面板的**纯状态机 + 纯几何**（无 Compose / 无 Android 依赖，JVM 单测直接覆盖）。
//  * 单键语义（D-14）：`hasContent ? (running ? 入本地队列 : 发送) : (running ? 停止 : 禁用)`；
//    **没有第二颗停止键**；"有内容" = 草稿非空白 || 附件轨非空（composer.md §3.4 字段 5）；
//  * 生成中发送 → **客户端本地队列**（Lead 裁定 P3-CMP-01/02：不发请求、不做 steer），
//    `AssistantDone` 后只自动出队 1 条；停止/失败 → 队列保留并暂停；进程杀 → 队列丢失（只存内存）；
//  * 高度真源 = §1.4 解析式：`H = (q>0 ? Q+4 : 0) + R + C(n) + 8`，`n = clamp(lineCount, 1, nCap)`；
//    发布值不得用实测高回写（§1.5 第 1 条）；
//  * 主键语义表（§3.4 字段 11）逐项落到 [ComposerPrimaryUi]/[ComposerPrimaryIntent]，可单测（CMP-10/14）；
//  * 提交防重入（BUG-004）= [SubmitGuard]：提交瞬间置位，重复点击/长按/程序化触发全部归一；
//  * 命中盒 = 布局盒（G-11/G-16）：主键 48、`+` 40、胶囊 44×≤112、队列动作 3×40、文件卡重试 196×64 /
//    移除 40×40 —— 全部给纯几何函数 + 相交判定（CMP-13/19/31/34/61）。
//
// 附件项状态机（处理中/就绪/失败、intake、门禁）在 `ui/m5/attachment/AttachmentState.kt`，本文件只消费门禁。
package dev.myco.android.ui.m5.composer

import dev.myco.android.ui.m5.attachment.AttachmentGate
import dev.myco.android.ui.m5.attachment.AttachmentGateBlock
import kotlin.math.floor
import kotlin.math.min

// ---------------------------------------------------------------------------
// 运行态与主键
// ---------------------------------------------------------------------------

/** 会话/连接运行态（真源在 chat/session 层；composer 只消费）。 */
data class ComposerEnv(
    val running: Boolean = false,
    val submitting: Boolean = false,
    val locked: Boolean = false,
    val connected: Boolean = true,
    val initializing: Boolean = false,
)

/** composer 的视觉态（写入 `ComposerMetrics.state`，§1.5）。 */
enum class ComposerVisualState {
    IDLE,
    RUNNING,
    SUBMITTING,
    PROCESSING,
    LOCKED,
    OFFLINE,
}

/** 主键图形（发送箭头 / 停止方块 / 提交中 spinner）。 */
enum class PrimaryIcon {
    SEND,
    STOP,
    BUSY,
}

/**
 * 主键视觉 + 语义（§3.4 字段 11 的表，实现照抄）。
 *
 * @param interactive 是否消费点击（`false` = 禁用外观且点击无效）
 * @param dimmed 38% 不透明（禁用/提交中/被附件失败拦发都算）
 * @param liveRegion 提交中只播报一次（`liveRegion=Polite`）
 */
data class ComposerPrimaryUi(
    val icon: PrimaryIcon,
    val interactive: Boolean,
    val dimmed: Boolean,
    val contentDescription: String,
    val stateDescription: String? = null,
    val liveRegion: Boolean = false,
)

/** 主键点击后的意图（单键状态机的唯一出口）。 */
sealed interface ComposerPrimaryIntent {
    /** 生成中·空草稿 → 停止。 */
    data object Stop : ComposerPrimaryIntent

    /** 空闲/生成中有内容 → 发送（生成中走 [Enqueue]）。 */
    data object Send : ComposerPrimaryIntent

    /** 生成中·有内容 → 入本地队列（不发请求、不做 steer）。 */
    data object Enqueue : ComposerPrimaryIntent

    /** 可点但被拦（附件失败）：不发送，弹 toast 点名「移除 / 重试」。 */
    data class Blocked(val toast: String) : ComposerPrimaryIntent

    /** 完全无动作（空草稿 / 提交中 / 未连接 / 锁定 / 附件处理中）。 */
    data object Ignored : ComposerPrimaryIntent
}

/**
 * 提交防重入（BUG-004 修法：**提交瞬间置位，不等第一个 delta**）。
 * 纯类，便于单测"连点 10 次只进入 1 次"（CMP-15）。
 */
class SubmitGuard {
    /** 一次在飞 attempt 是否已占位。 */
    var inFlight: Boolean = false
        private set

    /** 尝试占位；已占位返回 false（重复点击、长按、程序化触发全部归一）。 */
    fun tryEnter(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    /** 结算（成功/失败/超时）后复位，允许下一次**新快照**提交。 */
    fun reset() {
        inFlight = false
    }
}

/**
 * 单键语义机（纯函数）。
 *
 * 口径（用户拍板 D-14 + composer.md §3.4 字段 5）：
 *  - `hasContent = draft.isNotBlank() || rail.isNotEmpty()`；
 *  - 生成中发送 → 本地队列；**不存在第二颗停止键**；
 *  - 附件处理中 → 禁发（attachment.md ATT-17）；附件失败 → 拦发 + toast 指引（ATT-18 / P-ATT-07）。
 */
object ComposerMachine {

    /** 点击主键 → 意图。 */
    fun primaryIntent(
        env: ComposerEnv,
        hasContent: Boolean,
        gate: AttachmentGate = AttachmentGate.OPEN,
    ): ComposerPrimaryIntent {
        if (env.submitting) return ComposerPrimaryIntent.Ignored
        if (env.locked) return ComposerPrimaryIntent.Ignored
        if (!env.connected || env.initializing) return ComposerPrimaryIntent.Ignored

        if (!hasContent) {
            // 单键：空草稿时唯一语义是"停止"（生成中）；不因附件处理中而丢掉停止能力
            return if (env.running) ComposerPrimaryIntent.Stop else ComposerPrimaryIntent.Ignored
        }

        when (gate.block) {
            AttachmentGateBlock.FAILED -> return ComposerPrimaryIntent.Blocked(
                toast = gate.reason ?: "有附件失败，请移除或重试",
            )
            AttachmentGateBlock.PROCESSING -> return ComposerPrimaryIntent.Ignored
            null -> Unit
        }
        return if (env.running) ComposerPrimaryIntent.Enqueue else ComposerPrimaryIntent.Send
    }

    /** 主键视觉 + 语义（§3.4 字段 11 表）。 */
    fun primaryUi(
        env: ComposerEnv,
        hasContent: Boolean,
        gate: AttachmentGate = AttachmentGate.OPEN,
    ): ComposerPrimaryUi {
        if (env.submitting) {
            return ComposerPrimaryUi(
                icon = PrimaryIcon.BUSY,
                interactive = false,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = "正在提交",
                liveRegion = true,
            )
        }
        if (env.locked) {
            return ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = false,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = "当前会话只读",
            )
        }
        if (!env.connected) {
            return ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = false,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = "未连接，暂不能发送",
            )
        }
        if (env.initializing) {
            return ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = false,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = "连接后才能发送",
            )
        }
        if (env.running && !hasContent) {
            return ComposerPrimaryUi(
                icon = PrimaryIcon.STOP,
                interactive = true,
                dimmed = false,
                contentDescription = "停止生成",
                stateDescription = "正在生成",
            )
        }
        if (!hasContent) {
            return ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = false,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = "草稿为空，无法发送",
            )
        }
        when (gate.block) {
            // 失败项拦发：视觉禁用但保留点击，点了给 toast 指引（ATT-18 的"可见原因"）
            AttachmentGateBlock.FAILED -> return ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = true,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = gate.reason,
            )
            // 处理中：真禁用（§3.4 字段 5 ⑥；播报文案照抄字段 11 表）
            AttachmentGateBlock.PROCESSING -> return ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = false,
                dimmed = true,
                contentDescription = "发送",
                stateDescription = "附件处理中，暂不能发送",
            )
            null -> Unit
        }
        return if (env.running) {
            ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = true,
                dimmed = false,
                contentDescription = "发送",
                stateDescription = "正在生成，发送将排队，本轮结束后自动发出",
            )
        } else {
            ComposerPrimaryUi(
                icon = PrimaryIcon.SEND,
                interactive = true,
                dimmed = false,
                contentDescription = "发送",
                stateDescription = null,
            )
        }
    }

    /** 工具行 `+` 是否可用（提交中/处理中/只读/未连接 → 禁用，§3.7 字段 5/10）。 */
    fun attachTriggerEnabled(env: ComposerEnv, gate: AttachmentGate): Boolean =
        !env.submitting && env.connected && !env.initializing && !env.locked &&
            gate.block != AttachmentGateBlock.PROCESSING

    /** `+` 禁用原因（读屏播报；无禁用返回 null）。 */
    fun attachTriggerDisabledReason(env: ComposerEnv, gate: AttachmentGate): String? = when {
        env.submitting -> "正在提交，暂不能添加附件"
        env.locked -> "当前会话只读"
        !env.connected -> "未连接，暂不能添加附件"
        env.initializing -> "连接后才能添加附件"
        gate.block == AttachmentGateBlock.PROCESSING -> "附件处理中"
        else -> null
    }

    /** chips 视觉态（`ComposerMetrics.state`；优先级与主键语义一致）。 */
    fun visualState(env: ComposerEnv, gate: AttachmentGate): ComposerVisualState = when {
        env.submitting -> ComposerVisualState.SUBMITTING
        env.locked -> ComposerVisualState.LOCKED
        !env.connected || env.initializing -> ComposerVisualState.OFFLINE
        env.running -> ComposerVisualState.RUNNING
        gate.block == AttachmentGateBlock.PROCESSING -> ComposerVisualState.PROCESSING
        else -> ComposerVisualState.IDLE
    }
}

// ---------------------------------------------------------------------------
// 客户端本地队列（D-26 / P3-CMP-01 / P3-CMP-02）
// ---------------------------------------------------------------------------

/**
 * 一条待发消息（客户端本地状态，**不进 core、不发请求、不落盘**）。
 *
 * @param attachmentNames 队列行预览用（无正文时显示附件名）
 */
data class QueuedMessage(
    val id: Long,
    val text: String,
    val attachmentIds: List<String> = emptyList(),
    val attachmentNames: List<String> = emptyList(),
    val createdAtMs: Long = 0L,
) {
    /** 单行预览：正文优先，否则附件名（队列行预览区不可点，§3.18 字段 3）。 */
    val preview: String
        get() = when {
            text.isNotBlank() -> text.trim()
            attachmentNames.isNotEmpty() -> "附件：" + attachmentNames.joinToString("、")
            else -> "（空消息）"
        }
}

/** 队列状态。`paused` = 停止/失败后暂停（保留但不自动发）。 */
data class ComposerQueue(
    val items: List<QueuedMessage> = emptyList(),
    val paused: Boolean = false,
    /** 队列动作在飞（编辑/撤回/删除/自动发送串行化，§3.18 字段 5 ④）。 */
    val busy: Boolean = false,
    /** 下一个 id（本地自增；进程杀重建 = 从 1 开始，与草稿同边界）。 */
    val nextId: Long = 1L,
) {
    val count: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    /** 条高 `8 + min(q,3)×48`；q=0 时整条不渲染（composer.md §3.17 字段 2）。 */
    val heightDp: Float get() = if (isEmpty) 0f else ComposerMetricsCalculator.queueHeightDp(count)

    /** q>3 时条内滚（cap = 8+3×48 = 152）。 */
    val scrollable: Boolean get() = count > ComposerLayout.QUEUE_MAX_VISIBLE_ROWS

    /** busy 时三动作全部禁用（防连点 + 防与自动出队竞争）。 */
    val actionsEnabled: Boolean get() = !busy

    /** 自动出队前检查连接（断线则挂起，不丢队列）。 */
    val canAutoSend: Boolean get() = items.isNotEmpty() && !busy && !paused

    /** 暂停提示文案（条保留、内容灰显，§3.17 字段 10）。 */
    val pausedHint: String? get() = if (paused && items.isNotEmpty()) "本轮结束后继续" else null
}

/** 自动出队结果：`message != null` 表示本轮正常结束 → 出队 1 条走同一提交路径。 */
data class QueueDequeueResult(
    val queue: ComposerQueue,
    val message: QueuedMessage? = null,
)

/** 撤回结果：该条出队 + 文本回填草稿（草稿非空时以换行追加，不覆盖）。 */
data class QueueRecallResult(
    val queue: ComposerQueue,
    val draft: String,
    val recalled: QueuedMessage? = null,
)

/** 删除结果（供 Toast 6000ms 撤销恢复顺序，CMP-83）。 */
data class QueueDeleteResult(
    val queue: ComposerQueue,
    val deleted: QueuedMessage? = null,
    val index: Int = -1,
)

/** 编辑结果（空文本不允许保存）。 */
data class QueueEditResult(
    val queue: ComposerQueue,
    val changed: Boolean = false,
)

/**
 * 队列纯状态机（全部本地内存，进程杀即丢，CMP-81）。
 *
 * 出队规则（Lead 裁定）：`AssistantDone` → 只出队 **1 条**；停止/失败 → 保留并暂停，
 * 不自动发；下一次本轮正常结束再继续。
 */
object ComposerQueueMachine {

    /** 入队（生成中按发送）。 */
    fun enqueue(
        queue: ComposerQueue,
        text: String,
        attachmentIds: List<String> = emptyList(),
        attachmentNames: List<String> = emptyList(),
        nowMs: Long = 0L,
    ): ComposerQueue {
        val message = QueuedMessage(
            id = queue.nextId,
            text = text,
            attachmentIds = attachmentIds,
            attachmentNames = attachmentNames,
            createdAtMs = nowMs,
        )
        return queue.copy(items = queue.items + message, nextId = queue.nextId + 1L)
    }

    /**
     * 本轮正常结束（`AssistantDone`）→ 恢复（解除暂停）并出队**第 1 条**。
     * 返回的 message 由接线方走**与手动发送同一条提交路径**（防重入/快照同样生效）。
     */
    fun onAssistantDone(queue: ComposerQueue): QueueDequeueResult {
        if (queue.items.isEmpty()) {
            return QueueDequeueResult(queue.copy(paused = false))
        }
        val first = queue.items.first()
        return QueueDequeueResult(
            queue = queue.copy(
                items = queue.items.drop(1),
                paused = false,
                busy = true, // 自动发送在飞：行内动作先禁用（串行化）
            ),
            message = first,
        )
    }

    /** 自动发送结算（成功或失败由接线方判）：解除 busy。 */
    fun onAutoSendSettled(queue: ComposerQueue): ComposerQueue = queue.copy(busy = false)

    /** 自动发送失败 → 该条放回队首 + 暂停（不丢消息、不连发）。 */
    fun onAutoSendFailed(queue: ComposerQueue, message: QueuedMessage): ComposerQueue =
        queue.copy(items = listOf(message) + queue.items, paused = true, busy = false)

    /** 停止 / 本轮失败 → 队列保留并暂停（不清空、不自动发，CMP-80）。 */
    fun pause(queue: ComposerQueue): ComposerQueue = queue.copy(paused = true)

    /** 用户手动发送成功/失败后恢复（不清队列；手动发送优先、不插队，§3.17 字段 5 ⑤）。 */
    fun setBusy(queue: ComposerQueue, busy: Boolean): ComposerQueue = queue.copy(busy = busy)

    /** 编辑：更新该条文本，顺序不变（CMP-64/66）；空文本拒绝。 */
    fun edit(queue: ComposerQueue, id: Long, newText: String): QueueEditResult {
        if (newText.isBlank()) return QueueEditResult(queue, changed = false)
        val index = queue.items.indexOfFirst { it.id == id }
        if (index < 0) return QueueEditResult(queue, changed = false)
        val items = queue.items.toMutableList()
        items[index] = items[index].copy(text = newText)
        return QueueEditResult(queue.copy(items = items), changed = true)
    }

    /**
     * 撤回（= 取消这条待发）→ 出队 + **文字回填草稿**（草稿非空时换行追加、光标落末尾）。
     * 不销毁文字（CMP-82）。
     */
    fun recall(queue: ComposerQueue, id: Long, draft: String): QueueRecallResult {
        val index = queue.items.indexOfFirst { it.id == id }
        if (index < 0) return QueueRecallResult(queue, draft, recalled = null)
        val message = queue.items[index]
        val remaining = queue.items.toMutableList().apply { removeAt(index) }
        val merged = appendToDraft(draft, message.text)
        return QueueRecallResult(
            queue = queue.copy(items = remaining),
            draft = merged,
            recalled = message,
        )
    }

    /** 删除（破坏性）：出队并返回 index 供 6000ms 撤销按原顺序插回（CMP-83）。 */
    fun delete(queue: ComposerQueue, id: Long): QueueDeleteResult {
        val index = queue.items.indexOfFirst { it.id == id }
        if (index < 0) return QueueDeleteResult(queue)
        val message = queue.items[index]
        val remaining = queue.items.toMutableList().apply { removeAt(index) }
        return QueueDeleteResult(queue.copy(items = remaining), deleted = message, index = index)
    }

    /** 撤销删除：按原 index 插回，顺序不变（6000ms 窗口由 toast 计时）。 */
    fun undoDelete(queue: ComposerQueue, message: QueuedMessage, index: Int): ComposerQueue {
        if (queue.items.any { it.id == message.id }) return queue
        val target = index.coerceIn(0, queue.items.size)
        val items = queue.items.toMutableList().apply { add(target, message) }
        return queue.copy(items = items)
    }

    /** 撤回/追加草稿：草稿非空时以换行分隔，不覆盖已打的字。 */
    fun appendToDraft(draft: String, text: String): String {
        if (text.isBlank()) return draft
        if (draft.isBlank()) return text
        return draft.trimEnd('\n') + "\n" + text
    }
}

// ---------------------------------------------------------------------------
// 粘贴 / 长文转附件（§3.13 / §3.14）
// ---------------------------------------------------------------------------

/** 一次纯文本插入（粘贴检测结果；[start] 为草稿内插入起点）。 */
data class PasteInsertion(
    val start: Int,
    val inserted: String,
) {
    val chars: Int get() = inserted.length

    /** 行数 = 换行数 + 1（与 §3.14 阈值口径一致）。 */
    val lines: Int get() = inserted.count { it == '\n' } + 1
}

/** 已触发的长文提示（文案 + 可回滚的插入区）。 */
data class LongTextHint(
    val text: String,
    val start: Int,
    val chars: Int,
    val lines: Int,
)

/** 粘贴分流结果（§3.13 字段 5 的唯一判据）。 */
sealed interface PasteRoute {
    data object None : PasteRoute

    /** 只有文件/图片 URI → 全部交 attachment intake。 */
    data class FilesOnly(val fileCount: Int) : PasteRoute

    /** 文件 + 文本 → 文件入轨、文本照常插入光标处（不吞文本，DSH 反证）。 */
    data class FilesAndText(val fileCount: Int, val text: String) : PasteRoute

    /** 只有文本 → 正常插入（可能触发长文提示）。 */
    data class TextOnly(val text: String) : PasteRoute
}

/**
 * 粘贴与长文提示的纯逻辑。
 *
 * 阈值 = 我方取值（P4 校准，composer.md §3.14 字段 5）：单次粘贴 `chars ≥ 6000` 或 `lines ≥ 120`
 * 出现提示条；**不自动转**（用户选）。6000 字符 ≈ 40+ 屏，完全无法校对；太墟 20 万字符依据是测量卡顿。
 */
object PasteLogic {

    /** 长文提示字符阈值（我方取值，CMP-44）。 */
    const val HINT_MIN_CHARS: Int = 6000

    /** 长文提示行数阈值（我方取值，CMP-44）。 */
    const val HINT_MIN_LINES: Int = 120

    fun shouldOfferAttachment(chars: Int, lines: Int): Boolean =
        chars >= HINT_MIN_CHARS || lines >= HINT_MIN_LINES

    fun shouldOfferAttachment(insertion: PasteInsertion): Boolean =
        shouldOfferAttachment(insertion.chars, insertion.lines)

    /**
     * 从一次文本变化里识别"纯插入"（粘贴/输入）。
     * 非纯插入（删除、替换、等长编辑）返回 null —— 长文提示只对一次粘贴判定。
     */
    fun detectInsertion(old: String, new: String): PasteInsertion? {
        if (new.length <= old.length) return null
        val limit = min(old.length, new.length)
        var prefix = 0
        while (prefix < limit && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < limit - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
        ) {
            suffix++
        }
        val end = new.length - suffix
        if (end <= prefix) return null
        val inserted = new.substring(prefix, end)
        if (inserted.isEmpty()) return null
        return PasteInsertion(prefix, inserted)
    }

    /** 「转为附件」：从草稿中删除该次插入的文本（越界时安全降级为不删）。 */
    fun removeInsertion(draft: String, hint: LongTextHint): String {
        val end = hint.start + hint.text.length
        if (hint.start < 0 || end > draft.length) return draft
        if (!draft.regionMatches(hint.start, hint.text, 0, hint.text.length)) return draft
        return draft.removeRange(hint.start, end)
    }

    /** 剪贴板分流（文件 URI 的读取由平台/宿主做，这里只判分流）。 */
    fun route(hasFiles: Boolean, text: String?): PasteRoute {
        val body = text ?: ""
        return when {
            hasFiles && body.isNotBlank() -> PasteRoute.FilesAndText(fileCount = 1, text = body)
            hasFiles -> PasteRoute.FilesOnly(fileCount = 1)
            body.isNotBlank() -> PasteRoute.TextOnly(body)
            else -> PasteRoute.None
        }
    }
}

// ---------------------------------------------------------------------------
// 高度真源（§1.4 / §1.5）+ 命中盒几何（G-11 / G-16）
// ---------------------------------------------------------------------------

/**
 * 发布给 chat 的输入区指标（§1.5）。**seatHeight 由解析式直接算出，不是测量回写**。
 *
 * @param seatHeightDp H（不含系统 insets）
 * @param cardTopDp 卡上沿相对座位顶（= 队列条高 + 4 间隔）
 * @param lineCount n（已 clamp 到 [1, lineCap]）
 * @param lineCap n_max（键盘上方可用区降档后的结果）
 */
data class ComposerMetrics(
    val seatHeightDp: Float = 0f,
    val cardTopDp: Float = 0f,
    val cardHeightDp: Float = 0f,
    val draftHeightDp: Float = 0f,
    val lineCount: Int = 1,
    val lineCap: Int = ComposerLayout.MAX_LINES,
    val queueHeightDp: Float = 0f,
    val railHeightDp: Float = 0f,
    val hintHeightDp: Float = 0f,
    val visualState: ComposerVisualState = ComposerVisualState.IDLE,
) {
    val hasRail: Boolean get() = railHeightDp > 0f
    val hasHint: Boolean get() = hintHeightDp > 0f
    val hasQueue: Boolean get() = queueHeightDp > 0f

    /** 有队列条时与卡之间额外 4dp 间隔（§1.3）。 */
    val queueGapDp: Float get() = if (hasQueue) ComposerLayout.QUEUE_GAP_DP else 0f
}

/** 尺寸常量（全部 dp，来源 composer.md §1.3；本文件是唯一真源）。 */
object ComposerLayout {
    const val CARD_MAX_WIDTH_DP: Float = 752f
    const val CARD_HORIZONTAL_MARGIN_DP: Float = 20f
    const val CARD_INNER_PADDING_DP: Float = 8f
    const val DRAFT_MIN_HEIGHT_DP: Float = 48f
    const val DRAFT_HORIZONTAL_PADDING_DP: Float = 6f
    const val TOOLS_ROW_DP: Float = 48f
    const val PRIMARY_SIZE_DP: Float = 48f
    const val PLUS_SIZE_DP: Float = 40f
    const val CAPSULE_HEIGHT_DP: Float = 44f
    const val CAPSULE_MAX_WIDTH_DP: Float = 112f
    const val CAPSULE_ICON_ONLY_WIDTH_DP: Float = 40f
    const val CAPSULE_MIN_GAP_TO_PRIMARY_DP: Float = 8f
    const val RAIL_HEIGHT_DP: Float = 72f
    const val RAIL_ITEM_HEIGHT_DP: Float = 64f
    const val RAIL_IMAGE_SIZE_DP: Float = 64f
    const val RAIL_FILE_CARD_WIDTH_DP: Float = 240f
    const val RAIL_ITEM_GAP_DP: Float = 10f
    const val RAIL_HORIZONTAL_PADDING_DP: Float = 8f
    const val QUEUE_ROW_DP: Float = 48f
    const val QUEUE_ACTION_SIZE_DP: Float = 40f
    const val QUEUE_PADDING_DP: Float = 8f
    const val QUEUE_MAX_VISIBLE_ROWS: Int = 3
    const val QUEUE_GAP_DP: Float = 4f
    const val HINT_HEIGHT_DP: Float = 56f
    const val SEAT_BOTTOM_BREATH_DP: Float = 8f
    const val MAX_LINES: Int = 6
    const val LINE_HEIGHT_BASE_SP: Float = 24f
    const val CONTENT_FONT_BASE_SP: Float = 16f
    const val PALETTE_ANCHOR_GAP_DP: Float = 6f
    const val PALETTE_ROW_DP: Float = 52f
    const val PALETTE_MAX_ROWS: Int = 5
}

/**
 * 高度公式（§1.4），全部纯函数：
 * ```
 * LH   = 24 + Δ                 // Δ = 内容字号 − 16sp（sp→dp 由 UI 用 density 换算）
 * D(n) = 48 + (n − 1) × LH
 * C(n) = D(n) + 48
 * Q(q) = 8 + min(q,3) × 48
 * H    = (q>0 ? Q+4 : 0) + R + C(n) + 8
 * nCap = clamp( floor((0.4A − 96 − R − (q>0 ? Q+4 : 0)) / LH) + 1, 1, 6 )
 * ```
 */
object ComposerMetricsCalculator {

    /** 行高（sp）随内容字号：LH = 24 + (字号 − 16)。 */
    fun lineHeightSp(contentFontSizeSp: Float): Float =
        ComposerLayout.LINE_HEIGHT_BASE_SP + (contentFontSizeSp - ComposerLayout.CONTENT_FONT_BASE_SP)

    fun queueHeightDp(q: Int): Float =
        if (q <= 0) 0f
        else ComposerLayout.QUEUE_PADDING_DP + min(q, ComposerLayout.QUEUE_MAX_VISIBLE_ROWS) * ComposerLayout.QUEUE_ROW_DP

    /** 草稿区高 `D(n) = 48 + (n−1)·LH`（n 已 clamp）。 */
    fun draftHeightDp(lineCount: Int, lineHeightDp: Float): Float {
        val n = lineCount.coerceIn(1, ComposerLayout.MAX_LINES)
        return ComposerLayout.DRAFT_MIN_HEIGHT_DP + (n - 1) * lineHeightDp
    }

    /** 草稿区上下内边距：`(48 − LH) / 2`（大字号 LH 变大 → 归零，不裁切）。 */
    fun draftVerticalPaddingDp(lineHeightDp: Float): Float =
        ((ComposerLayout.DRAFT_MIN_HEIGHT_DP - lineHeightDp) / 2f).coerceAtLeast(0f)

    /** 卡高 `C(n) + R + 提示条`。 */
    fun cardHeightDp(
        lineCount: Int,
        lineHeightDp: Float,
        hasRail: Boolean,
        hasHint: Boolean,
    ): Float {
        val rail = if (hasRail) ComposerLayout.RAIL_HEIGHT_DP else 0f
        val hint = if (hasHint) ComposerLayout.HINT_HEIGHT_DP else 0f
        return rail + hint + draftHeightDp(lineCount, lineHeightDp) + ComposerLayout.TOOLS_ROW_DP
    }

    /** 队列条总占位（含与卡之间的 4dp 间隔）。 */
    fun queueBlockHeightDp(q: Int): Float =
        if (q <= 0) 0f else queueHeightDp(q) + ComposerLayout.QUEUE_GAP_DP

    /** 座位高 H（不含系统 insets）。 */
    fun seatHeightDp(
        lineCount: Int,
        queueCount: Int,
        hasRail: Boolean,
        hasHint: Boolean,
        lineHeightDp: Float,
        breathingDp: Float = ComposerLayout.SEAT_BOTTOM_BREATH_DP,
    ): Float = queueBlockHeightDp(queueCount) +
        cardHeightDp(lineCount, lineHeightDp, hasRail, hasHint) +
        breathingDp

    /**
     * 键盘避让降档：`n_max = clamp(floor((0.4A − 96 − R − 队列块) / LH) + 1, 1, 6)`。
     *
     * @param availableHeightDp A = 键盘打开时聊天列表可视高（由容器下发；§1.4）
     */
    fun lineCap(
        availableHeightDp: Float,
        hasRail: Boolean,
        queueCount: Int,
        lineHeightDp: Float,
    ): Int {
        if (lineHeightDp <= 0f) return ComposerLayout.MAX_LINES
        val rail = if (hasRail) ComposerLayout.RAIL_HEIGHT_DP else 0f
        val budget = 0.4f * availableHeightDp - 96f - rail - queueBlockHeightDp(queueCount)
        val raw = floor(budget / lineHeightDp).toInt() + 1
        return raw.coerceIn(1, ComposerLayout.MAX_LINES)
    }

    /** 组装发布值（输入全部是"解析式"量，没有任何测量回写）。 */
    fun metrics(
        lineCount: Int,
        queueCount: Int,
        hasRail: Boolean,
        hasHint: Boolean,
        lineCap: Int,
        lineHeightDp: Float,
        visualState: ComposerVisualState,
    ): ComposerMetrics {
        val cap = lineCap.coerceIn(1, ComposerLayout.MAX_LINES)
        val n = lineCount.coerceIn(1, cap)
        val queueH = queueHeightDp(queueCount)
        return ComposerMetrics(
            seatHeightDp = seatHeightDp(n, queueCount, hasRail, hasHint, lineHeightDp),
            cardTopDp = queueBlockHeightDp(queueCount),
            cardHeightDp = cardHeightDp(n, lineHeightDp, hasRail, hasHint),
            draftHeightDp = draftHeightDp(n, lineHeightDp),
            lineCount = n,
            lineCap = cap,
            queueHeightDp = queueH,
            railHeightDp = if (hasRail) ComposerLayout.RAIL_HEIGHT_DP else 0f,
            hintHeightDp = if (hasHint) ComposerLayout.HINT_HEIGHT_DP else 0f,
            visualState = visualState,
        )
    }

    /** 命令候选面板高：`min(5×52, A − cardH − 24)` 且至少 1 行（§3.15 字段 2）。 */
    fun paletteHeightDp(itemCount: Int, availableHeightDp: Float, cardHeightDp: Float): Float {
        if (itemCount <= 0) return 0f
        val room = availableHeightDp - cardHeightDp - 24f
        val wanted = itemCount.coerceAtMost(ComposerLayout.PALETTE_MAX_ROWS) * ComposerLayout.PALETTE_ROW_DP
        return maxOf(ComposerLayout.PALETTE_ROW_DP, min(wanted, room))
    }
}

/** 简单的布局命中盒（dp；纯几何，不依赖 Compose）。 */
data class HitBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val widthDp: Float get() = right - left
    val heightDp: Float get() = bottom - top

    /** 开区间相交判定（贴边不算相交，与 G-11 一致）。 */
    fun intersects(other: HitBox): Boolean =
        left < other.right - 0.001f && other.left < right - 0.001f &&
            top < other.bottom - 0.001f && other.top < bottom - 0.001f
}

/** 模型胶囊降级形态（E-07=A：先丢档位 → 再丢名 → 只图标 40）。 */
enum class ModelCapsuleForm {
    NAME_AND_REASONING,
    NAME_ONLY,
    ICON_ONLY,
}

/** 胶囊降级结果（宽度含内边距；`showReasoning` 只在第一形态为 true）。 */
data class ModelCapsulePlan(
    val form: ModelCapsuleForm,
    val widthDp: Float,
    val showName: Boolean,
    val showReasoning: Boolean,
)

/** 工具行几何（§3.6）：`[+40][胶囊 ≤112][flex][主键 48]`。 */
data class ToolsRowLayout(
    val plus: HitBox,
    val capsule: HitBox,
    val primary: HitBox,
)

/** 文件卡内部两个命中区（§3.10/§3.11）。 */
data class FileCardLayout(
    val card: HitBox,
    val retry: HitBox,
    val remove: HitBox,
)

/**
 * 命中盒与降级几何（G-11/G-16）。全部纯函数，单测断言"相邻命中盒不相交"。
 */
object ComposerGeometry {

    /** 胶囊内部固定开销：左右内边距 20 + 图标 16 + 图标间距 4。 */
    const val CAPSULE_CHROME_DP: Float = 40f

    /** 胶囊与主键净距（不同功能组，防误触；§3.3 字段 2）。 */
    const val CAPSULE_PRIMARY_GAP_DP: Float = ComposerLayout.CAPSULE_MIN_GAP_TO_PRIMARY_DP

    /**
     * 胶囊降级：按文字自然宽与可用预算决定形态。
     *
     * @param availableWidthDp 工具行可用内宽（卡内宽）
     * @param nameWidthDp 模型名文字宽（调用方用 TextMeasurer 估算或按字数估）
     * @param reasoningWidthDp 档位文字宽
     */
    fun capsulePlan(
        availableWidthDp: Float,
        nameWidthDp: Float,
        reasoningWidthDp: Float,
        hasReasoning: Boolean,
    ): ModelCapsulePlan {
        val budget = (availableWidthDp - ComposerLayout.PLUS_SIZE_DP - ComposerLayout.PRIMARY_SIZE_DP -
            CAPSULE_PRIMARY_GAP_DP).coerceAtLeast(ComposerLayout.CAPSULE_ICON_ONLY_WIDTH_DP)
        val cap = min(ComposerLayout.CAPSULE_MAX_WIDTH_DP, budget)
        val reasoningExtra = if (hasReasoning) 4f + reasoningWidthDp else 0f
        val requiredBoth = CAPSULE_CHROME_DP + nameWidthDp + reasoningExtra
        if (requiredBoth <= cap) {
            return ModelCapsulePlan(
                form = ModelCapsuleForm.NAME_AND_REASONING,
                widthDp = requiredBoth,
                showName = true,
                showReasoning = hasReasoning,
            )
        }
        val requiredName = CAPSULE_CHROME_DP + nameWidthDp
        if (requiredName <= cap) {
            return ModelCapsulePlan(
                form = ModelCapsuleForm.NAME_ONLY,
                widthDp = requiredName,
                showName = true,
                showReasoning = false,
            )
        }
        // 只图标 40 宽（位置与高度不变，主键永不缩）
        return ModelCapsulePlan(
            form = ModelCapsuleForm.ICON_ONLY,
            widthDp = ComposerLayout.CAPSULE_ICON_ONLY_WIDTH_DP,
            showName = false,
            showReasoning = false,
        )
    }

    /** 工具行三键布局（行内坐标；主键贴右端，胶囊贴 `+` 右侧）。 */
    fun toolsRowLayout(
        rowLeftDp: Float,
        rowTopDp: Float,
        availableWidthDp: Float,
        capsulePlan: ModelCapsulePlan,
    ): ToolsRowLayout {
        val plusTop = rowTopDp + (ComposerLayout.TOOLS_ROW_DP - ComposerLayout.PLUS_SIZE_DP) / 2f
        val capsuleTop = rowTopDp + (ComposerLayout.TOOLS_ROW_DP - ComposerLayout.CAPSULE_HEIGHT_DP) / 2f
        val primaryLeft = rowLeftDp + availableWidthDp - ComposerLayout.PRIMARY_SIZE_DP
        val capsuleLeft = rowLeftDp + ComposerLayout.PLUS_SIZE_DP
        return ToolsRowLayout(
            plus = HitBox(
                left = rowLeftDp,
                top = plusTop,
                right = rowLeftDp + ComposerLayout.PLUS_SIZE_DP,
                bottom = plusTop + ComposerLayout.PLUS_SIZE_DP,
            ),
            capsule = HitBox(
                left = capsuleLeft,
                top = capsuleTop,
                right = capsuleLeft + capsulePlan.widthDp,
                bottom = capsuleTop + ComposerLayout.CAPSULE_HEIGHT_DP,
            ),
            primary = HitBox(
                left = primaryLeft,
                top = rowTopDp,
                right = primaryLeft + ComposerLayout.PRIMARY_SIZE_DP,
                bottom = rowTopDp + ComposerLayout.PRIMARY_SIZE_DP,
            ),
        )
    }

    /** 工具行是否能单行放下（不换行、主键完整可见；CMP-19）。 */
    fun toolsRowFits(availableWidthDp: Float, capsuleWidthDp: Float): Boolean =
        ComposerLayout.PLUS_SIZE_DP + capsuleWidthDp + CAPSULE_PRIMARY_GAP_DP +
            ComposerLayout.PRIMARY_SIZE_DP <= availableWidthDp + 0.001f

    /** 附件轨第 index 项的命中盒（项 64 / 文件卡 240×64；间距 10；首尾 8）。 */
    fun railItemBox(
        index: Int,
        isFileCard: Boolean,
        railTopDp: Float,
    ): HitBox {
        val width = if (isFileCard) {
            ComposerLayout.RAIL_FILE_CARD_WIDTH_DP
        } else {
            ComposerLayout.RAIL_IMAGE_SIZE_DP
        }
        var left = ComposerLayout.RAIL_HORIZONTAL_PADDING_DP
        for (i in 0 until index) {
            // 前序项宽按同类型算（测试常用同类型轨）；真实布局由 LazyRow 负责
            left += width + ComposerLayout.RAIL_ITEM_GAP_DP
        }
        val itemTop = railTopDp + (ComposerLayout.RAIL_HEIGHT_DP - ComposerLayout.RAIL_ITEM_HEIGHT_DP) / 2f
        return HitBox(left, itemTop, left + width, itemTop + ComposerLayout.RAIL_ITEM_HEIGHT_DP)
    }

    /**
     * 文件卡内部：重试区 196×64（卡左），移除 40×40（卡右、垂直居中）。
     * `196 + 44 = 240`：两者净距 4，不相交（CMP-34）。
     */
    fun fileCardLayout(cardLeftDp: Float, cardTopDp: Float): FileCardLayout {
        val cardWidth = ComposerLayout.RAIL_FILE_CARD_WIDTH_DP
        val card = HitBox(
            cardLeftDp,
            cardTopDp,
            cardLeftDp + cardWidth,
            cardTopDp + ComposerLayout.RAIL_ITEM_HEIGHT_DP,
        )
        val retry = HitBox(
            cardLeftDp,
            cardTopDp,
            cardLeftDp + (cardWidth - 44f),
            cardTopDp + ComposerLayout.RAIL_ITEM_HEIGHT_DP,
        )
        val removeTop = cardTopDp + (ComposerLayout.RAIL_ITEM_HEIGHT_DP - 40f) / 2f
        val remove = HitBox(
            cardLeftDp + cardWidth - 44f,
            removeTop,
            cardLeftDp + cardWidth - 4f,
            removeTop + 40f,
        )
        return FileCardLayout(card = card, retry = retry, remove = remove)
    }

    /** 队列行三动作（编辑/撤回/删除）各 40×40，簇贴右端，与预览区不相交。 */
    fun queueRowActionBoxes(rowRightDp: Float, rowTopDp: Float): List<HitBox> {
        val actions = 3 * ComposerLayout.QUEUE_ACTION_SIZE_DP
        val start = rowRightDp - actions
        val top = rowTopDp + (ComposerLayout.QUEUE_ROW_DP - ComposerLayout.QUEUE_ACTION_SIZE_DP) / 2f
        return (0..2).map { i ->
            val left = start + i * ComposerLayout.QUEUE_ACTION_SIZE_DP
            HitBox(left, top, left + ComposerLayout.QUEUE_ACTION_SIZE_DP, top + ComposerLayout.QUEUE_ACTION_SIZE_DP)
        }
    }

    /** 队列行预览区（不可点，只用于几何断言）：行内左端到动作簇之前。 */
    fun queueRowPreviewBox(rowLeftDp: Float, rowTopDp: Float, rowWidthDp: Float): HitBox {
        val actionsLeft = rowLeftDp + rowWidthDp - 3 * ComposerLayout.QUEUE_ACTION_SIZE_DP
        return HitBox(
            rowLeftDp,
            rowTopDp,
            actionsLeft,
            rowTopDp + ComposerLayout.QUEUE_ROW_DP,
        )
    }
}

/** 一次发送的内容快照（提交瞬间冻结，§3.5 字段 5 ②）。 */
data class ComposerSendSnapshot(
    val text: String,
    val attachmentIds: List<String> = emptyList(),
    /** 是否来自队列自动出队（接线方据此走同一条提交路径）。 */
    val fromQueue: Boolean = false,
    /** 队列条目 id（自动发送时回传给队列状态机用于失败回滚）。 */
    val queueMessageId: Long? = null,
) {
    val isEmpty: Boolean get() = text.isBlank() && attachmentIds.isEmpty()
}
