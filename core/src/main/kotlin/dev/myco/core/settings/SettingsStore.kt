package dev.myco.core.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

class SettingsConflictException(message: String) : RuntimeException(message)

data class SettingsDoc(val revision: Long, val data: JsonElement)

/**
 * 带 revision 乐观锁的设置文件（A6 同步的前置：所有可同步对象都要有版本）。
 *
 * 文件格式（新）：`{"schemaVersion":1,"revision":N,"data":{...}}`
 * 兼容旧格式：文件本身就是 data（revision=0），首次写入时升级为带信封格式。
 * 写入是 tmp + 原子 move；同进程内用锁串行化。
 */
class SettingsStore(private val file: Path) {
    private val lock = Any()

    fun read(): SettingsDoc = synchronized(lock) {
        if (!Files.exists(file)) return SettingsDoc(0, JsonObject(emptyMap()))
        val root = try {
            Json.parseToJsonElement(Files.readString(file))
        } catch (e: Exception) {
            throw SettingsConflictException("设置文件损坏：$file（${e.message}）")
        }
        val obj = root as? JsonObject ?: return SettingsDoc(0, root)
        val isEnvelope = obj.containsKey("schemaVersion") && obj.containsKey("revision") && obj.containsKey("data")
        if (isEnvelope) {
            SettingsDoc(
                revision = obj["revision"]?.jsonPrimitive?.longOrNull ?: 0,
                data = obj["data"] ?: JsonObject(emptyMap()),
            )
        } else {
            SettingsDoc(0, root)
        }
    }

    /** 返回新 revision；expectedRevision 不匹配则抛冲突，不写盘。 */
    fun write(data: JsonElement, expectedRevision: Long): Long = synchronized(lock) {
        val current = read()
        if (current.revision != expectedRevision) {
            throw SettingsConflictException(
                "revision 冲突：期望 $expectedRevision，实际 ${current.revision}（可能被另一个客户端改过）"
            )
        }
        val next = current.revision + 1
        val envelope = buildJsonObject {
            put("schemaVersion", 1)
            put("revision", next)
            put("data", data)
        }
        Files.createDirectories(file.toAbsolutePath().parent)
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, envelope.toString())
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        next
    }

    fun exists(): Boolean = Files.exists(file)
}
