package dev.myco.core.spec

import kotlinx.serialization.Serializable

/**
 * 附件引用（只存相对 id 与元数据，绝不存绝对路径——同步/迁移友好）。
 * 文件本体在 `<myco-home>/attachments/<sessionId>/<id>__<name>`。
 */
@Serializable
data class AttachmentRef(
    val id: String,
    /** 原始文件名（仅展示；落盘时会做安全处理） */
    val name: String,
    /** MIME，例如 image/png、text/plain */
    val mime: String,
    val size: Long,
    /** image / text / file —— 决定怎么进模型上下文 */
    val kind: String,
)
