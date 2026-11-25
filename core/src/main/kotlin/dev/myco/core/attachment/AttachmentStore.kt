package dev.myco.core.attachment

import dev.myco.core.MycoHome
import dev.myco.core.io.TextFiles
import dev.myco.core.spec.AttachmentRef
import dev.myco.core.session.Ulid
import java.nio.file.Files
import java.nio.file.Path

class AttachmentTooLargeException(message: String) : RuntimeException(message)
class AttachmentNotFoundException(message: String) : RuntimeException(message)
class AttachmentOutsideRootException(message: String) : RuntimeException(message)

/**
 * 附件存储：只落在应用私有 `<home>/attachments/<sessionId>/`。
 * 不写绝对路径进事件；读时按相对位置解析并做越界校验（防 ../ 穿越）。
 *
 * 隐私默认：文件只在本地，只有用户显式发送时才把内容放进模型请求；不自动上传。
 */
class AttachmentStore(private val home: MycoHome) {
    private val root: Path get() = home.attachments

    fun save(sessionId: String, name: String, mime: String, bytes: ByteArray): AttachmentRef {
        val max = if (mime.startsWith("image/")) MAX_IMAGE_BYTES else MAX_FILE_BYTES
        if (bytes.size > max) throw AttachmentTooLargeException("附件过大：${bytes.size} > $max 字节")
        val id = Ulid.newId()
        val safeName = sanitize(name)
        val dir = root.resolve(sessionId)
        Files.createDirectories(dir)
        val target = dir.resolve("${id}__$safeName")
        Files.write(target, bytes)
        val kind = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("text/") || mime in TEXT_MIMES || safeName.substringAfterLast('.', "").lowercase() in TEXT_EXTS -> "text"
            else -> "file"
        }
        return AttachmentRef(id = id, name = name, mime = mime, size = bytes.size.toLong(), kind = kind)
    }

    /** 读文件本体；ref 必须属于该会话（防跨会话读取）。 */
    fun read(sessionId: String, ref: AttachmentRef): ByteArray {
        val dir = root.resolve(sessionId).normalize()
        val file = dir.resolve("${ref.id}__${sanitize(ref.name)}").normalize()
        if (!file.startsWith(dir)) throw AttachmentOutsideRootException("附件路径越界")
        if (!Files.isRegularFile(file)) throw AttachmentNotFoundException("附件不存在：${ref.id}")
        return Files.readAllBytes(file)
    }

    fun textContent(sessionId: String, ref: AttachmentRef, limit: Int = 12_000): String {
        val bytes = read(sessionId, ref)
        val text = String(bytes, Charsets.UTF_8)
        return if (text.length <= limit) text else text.take(limit) + "\n…（已截断）"
    }

    fun exists(sessionId: String, ref: AttachmentRef): Boolean =
        runCatching { read(sessionId, ref); true }.getOrDefault(false)

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val clean = base.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(80)
        return clean.ifBlank { "attachment" }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
        private val TEXT_MIMES = setOf(
            "application/json", "application/xml", "application/x-yaml", "application/javascript",
        )
        private val TEXT_EXTS = setOf("txt", "md", "markdown", "json", "csv", "yaml", "yml", "xml", "log", "kt", "py", "js", "ts")
    }
}
