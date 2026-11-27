// 验收 ID：ATT-06[candidate] ATT-12 ATT-13 ATT-14 ATT-15 ATT-16 ATT-17 ATT-18 ATT-19 ATT-20 ATT-21
//
// attachment 面板的**纯状态机**（无 Compose / 无 Android 依赖，JVM 单测直接覆盖）：
//  * intake 校验顺序 = 类型 → 单文件 → 总量（**数量不校验**，D-24 / P-ATT-04：首版不设客户端数量上限，
//    超限由后端错误回执呈现）；任一失败即**整批拒绝**（一个都不入队）；
//  * 限制值**全部来自后端投影**（[AttachmentLimits]），本文件**不写死任何上限常量**（ATT-13）；
//  * 附件项三态 = 处理中 / 就绪 / 失败（**不用 uploading/百分比**：本地落盘没有真实 total）；
//  * 处理通道**串行**：同时只有 1 项 active（[AttachmentDraft.activeProcessingId]），其余排队；
//  * 门禁：处理中禁发（ATT-17）；**失败项也禁发**（P-ATT-07 裁定，ATT-18，不允许静默带失败附件发出）；
//  * 处理中移除 = discard 标记，回执到达后由通道删文件（ATT-21，不产生孤儿）；
//  * 处理超时 30s → failed（retryable），我方取值（attachment.md §3.8 字段 10）。
//
// 呈现（缩略图 / 文件卡 / 红边 / 重试入口）归 composer（composer.attach.rail）；
// 本文件只输出**项级语义**与门禁判据（attachment.md 0.1 边界表 / Q-CMP-09 分割线）。
package dev.myco.android.ui.m5.attachment

import java.util.Locale

/** 附件种类：图片走图片口径（缩略图/灯箱），其余走文件卡口径。 */
enum class AttachmentItemKind {
    IMAGE,
    FILE,
}

/** 附件项状态（**只有三态**，见 attachment.md §3.8 字段 5）。 */
enum class AttachmentItemState {
    /** 处理中（本地落盘 / 校验在途）。 */
    PROCESSING,

    /** 就绪，可发送。 */
    READY,

    /** 失败；[AttachmentDraftItem.retryable] 决定是否给重试入口。 */
    FAILED,
}

/**
 * 来源通道产出的候选文件（intake 之前）。
 *
 * @param localId 本地稳定 id（UI key / 状态机主键；不要求等于后端附件 id）
 * @param uri 本地 content:// 或 file://（仅 UI 预览用；状态机不碰 IO）
 * @param temporary 相机临时文件：宿主在处理完成后负责删除（取消/失败即删）
 */
data class IncomingAttachment(
    val localId: String,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    val kind: AttachmentItemKind,
    val uri: String? = null,
    val temporary: Boolean = false,
)

/**
 * intake 限制（**真源 = 后端配置投影**；缺省/0/负数 = 不限，不误拦）。
 *
 * @param maxImageBytes 图片单文件上限（投影 `maxImageBytes`）
 * @param maxMessageImageBytes 单条消息图片总量上限（投影 `maxMessageImageBytes`）
 * @param maxFileBytes 通用文件单文件上限（投影；后端已知 8MB）
 * @param maxTotalBytes 整批总量上限（投影；缺省不限）
 * @param mediaTypes 允许的图片 MIME 集合（投影；空 = 不限）
 */
data class AttachmentLimits(
    val maxImageBytes: Long = 0L,
    val maxMessageImageBytes: Long = 0L,
    val maxFileBytes: Long = 0L,
    val maxTotalBytes: Long = 0L,
    val mediaTypes: Set<String> = emptySet(),
) {
    /** 单文件上限（按口径）。 */
    fun singleFileLimitBytes(kind: AttachmentItemKind): Long =
        if (kind == AttachmentItemKind.IMAGE) maxImageBytes else maxFileBytes

    companion object {
        /** 全部不限（投影未下发时的安全缺省：只显示后端错误，不前置拦截）。 */
        val UNLIMITED = AttachmentLimits()
    }
}

/** 草稿轨里的一项（状态机持有的数据）。 */
data class AttachmentDraftItem(
    val id: String,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    val kind: AttachmentItemKind,
    val uri: String? = null,
    val temporary: Boolean = false,
    val state: AttachmentItemState = AttachmentItemState.PROCESSING,
    val failureReason: String? = null,
    val retryable: Boolean = false,
    /** 仅当通道有**真实** total 时才有值（本地落盘通常没有 → null，只显示处理中）。 */
    val progress: Float? = null,
    /** 本项进入处理开始时间（串行通道的 active 项才有值；超时判定用）。 */
    val processingStartedAtMs: Long? = null,
    /** 后端回执 id（发送时优先用它）；未回执 = null。 */
    val remoteId: String? = null,
)

/** intake 结果：要么整批接受，要么整批拒绝（含点名第几项）。 */
sealed interface IntakeResult {
    /** 整批通过，附上生成的草稿项（全部为 `PROCESSING`）。 */
    data class Accepted(val items: List<AttachmentDraftItem>) : IntakeResult

    /**
     * 整批拒绝。
     *
     * @param reason 可直接进 toast 的文案（值来自投影，不写死数字）
     * @param failedIndex 批次内第几项（0 基；总量/整批级失败为 -1）
     */
    data class Rejected(
        val reason: String,
        val failedIndex: Int = -1,
        val failedName: String? = null,
    ) : IntakeResult
}

/** 门禁拦发原因分类（composer 主键据此决定"禁用"还是"可点但弹提示"）。 */
enum class AttachmentGateBlock {
    /** 有项处理中 → 主键禁用（ATT-17）。 */
    PROCESSING,

    /** 有项失败 → 阻止发送 + toast 指明「移除 / 重试」（ATT-18 / P-ATT-07）。 */
    FAILED,
}

/** 发送门禁输出（attachment.md §3.8 字段 5）。 */
data class AttachmentGate(
    val canSend: Boolean,
    val processingCount: Int = 0,
    val failedCount: Int = 0,
    val reason: String? = null,
    val block: AttachmentGateBlock? = null,
) {
    companion object {
        val OPEN = AttachmentGate(canSend = true)
    }
}

/** 移除动作的输出：区分"立即删文件"与"处理中 discard（回执后删）"。 */
data class AttachmentRemoval(
    val draft: AttachmentDraft,
    val removed: AttachmentDraftItem?,
    /** 就绪/失败项移除 → 立即调删除命令（ATT-20）。 */
    val needsDeleteNow: Boolean,
    /** 处理中项移除 → discard：项立即消失、回执到达后删文件（ATT-21）。 */
    val discardOnAck: Boolean,
) {
    val changed: Boolean get() = removed != null
}

/**
 * 草稿附件轨状态。
 *
 * @param activeProcessingId 串行通道当前处理项（一次最多 1 项，ATT-15）
 */
data class AttachmentDraft(
    val items: List<AttachmentDraftItem> = emptyList(),
    val activeProcessingId: String? = null,
) {
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    val processingCount: Int get() = items.count { it.state == AttachmentItemState.PROCESSING }
    val failedCount: Int get() = items.count { it.state == AttachmentItemState.FAILED }
    val readyCount: Int get() = items.count { it.state == AttachmentItemState.READY }

    /** 串行通道下一次该处理哪一项（空 = 无活可干）。 */
    val nextToProcess: AttachmentDraftItem?
        get() = activeProcessingId?.let { id -> items.firstOrNull { it.id == id } }

    /** 发送门禁（失败优先于处理中：失败项需要用户动作，先报它）。 */
    val gate: AttachmentGate
        get() {
            if (failedCount > 0) {
                return AttachmentGate(
                    canSend = false,
                    processingCount = processingCount,
                    failedCount = failedCount,
                    reason = "有 $failedCount 个附件失败，请移除或重试",
                    block = AttachmentGateBlock.FAILED,
                )
            }
            if (processingCount > 0) {
                return AttachmentGate(
                    canSend = false,
                    processingCount = processingCount,
                    failedCount = 0,
                    reason = "还有 $processingCount 个附件在处理中",
                    block = AttachmentGateBlock.PROCESSING,
                )
            }
            return AttachmentGate.OPEN
        }

    /** 可发送的附件 id（优先后端回执 id；就绪项，顺序 = 入队顺序，ATT-19）。 */
    fun readyRemoteIds(): List<String> =
        items.filter { it.state == AttachmentItemState.READY }
            .map { it.remoteId ?: it.id }
}

/** 状态转移结果（`changed=false` 表示重复回执被幂等吞掉）。 */
data class AttachmentTransition(
    val draft: AttachmentDraft,
    val changed: Boolean,
    /** 回执到达时该项已被移除（处理中 discard）→ 通道应立即删这个文件（ATT-21）。 */
    val orphanToDelete: String? = null,
)

/** 加入批次的输出：草稿 + intake 结果（UI 据此决定 toast）。 */
data class AttachmentAddResult(
    val draft: AttachmentDraft,
    val intake: IntakeResult,
)

/**
 * 附件项状态机（纯函数）。
 *
 * 转移（attachment.md §3.8 字段 5）：
 *  选中 → PROCESSING；回执成功 → READY；回执失败 → FAILED；FAILED + 重试 → PROCESSING；
 *  任意态 + 移除 → 从 items 消失（处理中另给 discard 标记）。
 *  状态只能由回执驱动（不存在无回执的 READY）。
 */
object AttachmentMachine {

    /** 我方取值（attachment.md §3.8 字段 10）：无回执 30s 转 failed。 */
    const val PROCESS_TIMEOUT_MS: Long = 30_000L

    /** 处理超时文案。 */
    const val TIMEOUT_REASON: String = "处理超时，可重试"

    // ------------------------------------------------------------------ intake + 入队

    /**
     * intake 校验后整批入队（串行通道：`activeProcessingId` 只会指向第一项）。
     *
     * @param nowMs 当前时钟（毫秒；传 0 表示测试/未提供 → 不开始超时计时）
     */
    fun addBatch(
        draft: AttachmentDraft,
        batch: List<IncomingAttachment>,
        limits: AttachmentLimits,
        nowMs: Long = 0L,
    ): AttachmentAddResult {
        when (val intake = AttachmentIntake.validate(batch, limits)) {
            is IntakeResult.Rejected -> return AttachmentAddResult(draft, intake)
            is IntakeResult.Accepted -> {
                if (intake.items.isEmpty()) return AttachmentAddResult(draft, intake)
                val active = draft.activeProcessingId
                val newItems = intake.items.mapIndexed { index, item ->
                    if (active == null && index == 0) {
                        item.copy(processingStartedAtMs = nowMs.takeIf { it > 0L })
                    } else {
                        item
                    }
                }
                val nextActive = active ?: newItems.first().id
                return AttachmentAddResult(
                    draft = draft.copy(items = draft.items + newItems, activeProcessingId = nextActive),
                    intake = intake,
                )
            }
        }
    }

    /** 回执成功：PROCESSING → READY，并把串行队列里的下一项提升为 active。 */
    fun markReady(
        draft: AttachmentDraft,
        id: String,
        nowMs: Long = 0L,
        remoteId: String? = null,
        processedSizeBytes: Long? = null,
    ): AttachmentTransition {
        val index = draft.items.indexOfFirst { it.id == id }
        if (index < 0) return AttachmentTransition(draft, changed = false, orphanToDelete = id)
        val item = draft.items[index]
        if (item.state != AttachmentItemState.PROCESSING) {
            return AttachmentTransition(draft, changed = false) // 重复回执幂等
        }
        val updated = draft.items.toMutableList()
        updated[index] = item.copy(
            state = AttachmentItemState.READY,
            failureReason = null,
            retryable = false,
            progress = null,
            processingStartedAtMs = null,
            remoteId = remoteId ?: item.remoteId,
            sizeBytes = processedSizeBytes ?: item.sizeBytes,
        )
        return AttachmentTransition(
            draft = promoteNext(draft.copy(items = updated), nowMs),
            changed = true,
        )
    }

    /** 回执失败：PROCESSING → FAILED（`retryable` 分级由通道给）。 */
    fun markFailed(
        draft: AttachmentDraft,
        id: String,
        reason: String,
        retryable: Boolean,
        nowMs: Long = 0L,
    ): AttachmentTransition {
        val index = draft.items.indexOfFirst { it.id == id }
        if (index < 0) return AttachmentTransition(draft, changed = false, orphanToDelete = id)
        val item = draft.items[index]
        if (item.state != AttachmentItemState.PROCESSING) {
            return AttachmentTransition(draft, changed = false)
        }
        val updated = draft.items.toMutableList()
        updated[index] = item.copy(
            state = AttachmentItemState.FAILED,
            failureReason = reason,
            retryable = retryable,
            progress = null,
            processingStartedAtMs = null,
        )
        return AttachmentTransition(
            draft = promoteNext(draft.copy(items = updated), nowMs),
            changed = true,
        )
    }

    /**
     * 重试：仅 `FAILED && retryable` 可进入；重试在飞的连点被幂等吞掉（类比 BUG-004）。
     * 不新增附件、不改变轨内顺序（ATT-16 / CMP-38）。
     */
    fun beginRetry(draft: AttachmentDraft, id: String, nowMs: Long = 0L): AttachmentTransition {
        val index = draft.items.indexOfFirst { it.id == id }
        if (index < 0) return AttachmentTransition(draft, changed = false)
        val item = draft.items[index]
        if (item.state != AttachmentItemState.FAILED || !item.retryable) {
            return AttachmentTransition(draft, changed = false)
        }
        val updated = draft.items.toMutableList()
        updated[index] = item.copy(
            state = AttachmentItemState.PROCESSING,
            failureReason = null,
            progress = null,
            processingStartedAtMs = nowMs.takeIf { it > 0L },
        )
        val active = draft.activeProcessingId ?: id
        return AttachmentTransition(
            draft = draft.copy(items = updated, activeProcessingId = active),
            changed = true,
        )
    }

    /**
     * 移除：处理中 → discard（项立即消失、回执后删）；就绪/失败 → 立即删文件（ATT-20/21）。
     * 幂等：找不到项返回 changed=false。
     *
     * @param nowMs 当前时钟（提升串行队列下一项时从此刻起算超时）
     */
    fun remove(draft: AttachmentDraft, id: String, nowMs: Long = 0L): AttachmentRemoval {
        val index = draft.items.indexOfFirst { it.id == id }
        if (index < 0) {
            return AttachmentRemoval(draft, removed = null, needsDeleteNow = false, discardOnAck = false)
        }
        val item = draft.items[index]
        val remaining = draft.items.toMutableList().apply { removeAt(index) }
        val processing = item.state == AttachmentItemState.PROCESSING
        val nextDraft = promoteNext(
            draft.copy(
                items = remaining,
                activeProcessingId = if (draft.activeProcessingId == id) null else draft.activeProcessingId,
            ),
            nowMs = nowMs,
        )
        return AttachmentRemoval(
            draft = nextDraft,
            removed = item,
            needsDeleteNow = !processing,
            discardOnAck = processing,
        )
    }

    /**
     * 处理超时 tick：active 项开始超过 [PROCESS_TIMEOUT_MS] → FAILED（retryable），
     * 并提升下一项。返回 `changed=false` 表示没有超时。
     */
    fun tickTimeouts(draft: AttachmentDraft, nowMs: Long): AttachmentTransition {
        val active = draft.nextToProcess ?: return AttachmentTransition(draft, changed = false)
        val started = active.processingStartedAtMs ?: return AttachmentTransition(draft, changed = false)
        if (nowMs - started < PROCESS_TIMEOUT_MS) return AttachmentTransition(draft, changed = false)
        val updated = draft.items.map {
            if (it.id == active.id) {
                it.copy(
                    state = AttachmentItemState.FAILED,
                    failureReason = TIMEOUT_REASON,
                    retryable = true,
                    processingStartedAtMs = null,
                )
            } else {
                it
            }
        }
        return AttachmentTransition(
            draft = promoteNext(draft.copy(items = updated), nowMs),
            changed = true,
        )
    }

    /**
     * 提升串行队列的下一项：只处理下一个 `PROCESSING` 项，从 nowMs 起计时。
     * 没有 active 时找第一个处理中项；已有 active 且仍处理中则保持不动。
     */
    internal fun promoteNext(draft: AttachmentDraft, nowMs: Long): AttachmentDraft {
        val current = draft.activeProcessingId?.let { id -> draft.items.firstOrNull { it.id == id } }
        if (current != null && current.state == AttachmentItemState.PROCESSING) return draft
        val next = draft.items.firstOrNull { it.state == AttachmentItemState.PROCESSING }
            ?: return draft.copy(activeProcessingId = null)
        val items = draft.items.map {
            if (it.id == next.id) {
                it.copy(processingStartedAtMs = it.processingStartedAtMs ?: nowMs.takeIf { t -> t > 0L })
            } else {
                it
            }
        }
        return draft.copy(items = items, activeProcessingId = next.id)
    }
}

/**
 * intake 校验器（attachment.md §3.7）。**校验顺序固定：类型 → 单文件 → 总量**；
 * 数量不校验（首版不设客户端数量上限）；任一失败即整批拒绝。
 */
object AttachmentIntake {

    fun validate(batch: List<IncomingAttachment>, limits: AttachmentLimits): IntakeResult {
        if (batch.isEmpty()) return IntakeResult.Accepted(emptyList())

        // ① 类型（仅图片口径有 mediaTypes 投影；通用文件不做扩展名白名单 C-M5-11）
        for ((index, item) in batch.withIndex()) {
            if (item.kind != AttachmentItemKind.IMAGE) continue
            if (limits.mediaTypes.isEmpty()) continue
            if (limits.mediaTypes.none { it.equals(item.mime, ignoreCase = true) }) {
                return IntakeResult.Rejected(
                    reason = "不支持的图片类型：${item.mime}",
                    failedIndex = index,
                    failedName = item.name,
                )
            }
        }

        // ② 单文件（图片用 maxImageBytes，通用文件用 maxFileBytes；0/负数 = 不限）
        for ((index, item) in batch.withIndex()) {
            val limit = limits.singleFileLimitBytes(item.kind)
            if (limit > 0L && item.sizeBytes > limit) {
                val label = if (item.kind == AttachmentItemKind.IMAGE) "单张上限" else "单文件上限"
                return IntakeResult.Rejected(
                    reason = "${item.name} 超过$label ${formatBytes(limit)}",
                    failedIndex = index,
                    failedName = item.name,
                )
            }
        }

        // ③ 总量（纯图片批优先用 maxMessageImageBytes，否则 maxTotalBytes；0/负数 = 不限）
        val allImages = batch.all { it.kind == AttachmentItemKind.IMAGE }
        val totalLimit = if (allImages && limits.maxMessageImageBytes > 0L) {
            limits.maxMessageImageBytes
        } else {
            limits.maxTotalBytes
        }
        if (totalLimit > 0L) {
            val total = batch.sumOf { it.sizeBytes.coerceAtLeast(0L) }
            if (total > totalLimit) {
                return IntakeResult.Rejected(
                    reason = "总大小超过 ${formatBytes(totalLimit)}",
                    failedIndex = -1,
                )
            }
        }

        // 数量不校验（D-24 / P-ATT-04）；入队 → 全部 PROCESSING
        return IntakeResult.Accepted(batch.map { it.toDraftItem() })
    }

    /** 候选入队项（全部处理中；串行 active 由 [AttachmentMachine.addBatch] 指定）。 */
    fun IncomingAttachment.toDraftItem(): AttachmentDraftItem = AttachmentDraftItem(
        id = localId,
        name = name,
        mime = mime,
        sizeBytes = sizeBytes.coerceAtLeast(0L),
        kind = kind,
        uri = uri,
        temporary = temporary,
        state = AttachmentItemState.PROCESSING,
    )

    /**
     * 字节数 → 人话（只用于**投影值**的展示，不产生任何上限常量）。
     * 8388608 → "8 MB"、1048576 → "1 MB"、1572864 → "1.5 MB"。
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> trimNumber(bytes / gb) + " GB"
            bytes >= mb -> trimNumber(bytes / mb) + " MB"
            bytes >= kb -> trimNumber(bytes / kb) + " KB"
            else -> "$bytes B"
        }
    }

    private fun trimNumber(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
