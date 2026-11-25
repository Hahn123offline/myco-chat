package dev.myco.core.session

import dev.myco.core.MycoHome
import dev.myco.core.spec.MycoJson
import dev.myco.core.spec.SessionMeta
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class UnknownSessionFormatException(message: String) : RuntimeException(message)
class CorruptSessionException(message: String) : RuntimeException(message)

/**
 * 会话 = append-only 事件日志（每会话一个 JSONL 文件）。
 *
 * 不变量：
 *  - seq 单调、由 store 分配；重放按 seq 排序，不按时间排序。
 *  - 首事件必须是 session/start(f=1)，否则拒绝打开（不自动迁移）。
 *  - 只允许最后一行不完整（崩溃尾部）：读侧丢弃并 warn；中间损坏必须报错。
 */
class SessionStore(private val home: MycoHome) {
    private val seqCache = mutableMapOf<String, Long>()

    init {
        home.ensureLayout()
    }

    fun create(app: String, preset: String, title: String = "新会话"): SessionMeta {
        val meta = SessionMeta(
            id = Ulid.newId(),
            title = title,
            createdAt = System.currentTimeMillis(),
            app = app,
            preset = preset,
            formatVersion = 1,
        )
        val dir = sessionDir(meta.id)
        Files.createDirectories(dir)
        val start = SessionEvent.SessionStart(seq = 0, at = meta.createdAt, data = meta)
        appendLine(dir, start)
        seqCache[meta.id] = 0
        return meta
    }

    fun append(sessionId: String, event: SessionEvent): SessionEvent {
        val dir = sessionDir(sessionId)
        if (!Files.exists(dir)) throw IllegalArgumentException("session not found: $sessionId")
        ensureLoaded(sessionId)
        val next = (seqCache[sessionId] ?: -1L) + 1
        val stamped = event.withSeq(next).withAt(System.currentTimeMillis())
        appendLine(dir, stamped)
        seqCache[sessionId] = next
        return stamped
    }

    fun read(sessionId: String): List<SessionEvent> = readFrom(sessionId, 0)

    fun readFrom(sessionId: String, fromSeq: Long): List<SessionEvent> {
        val dir = sessionDir(sessionId)
        val file = dir.resolve(FILE_NAME)
        if (!Files.exists(file)) throw IllegalArgumentException("session not found: $sessionId")
        val raw = Files.readAllLines(file, StandardCharsets.UTF_8)
        val events = ArrayList<SessionEvent>(raw.size)
        var lastBad: Int = -1
        raw.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            try {
                events += MycoJson.decodeFromString(SessionEvent.serializer(), line)
            } catch (e: Exception) {
                // 只有"最后一行"允许不完整（崩溃尾部）
                if (index == raw.lastIndex) lastBad = index else throw CorruptSessionException(
                    "corrupt event at line ${index + 1} of $sessionId: ${e.message}"
                )
            }
        }
        if (lastBad >= 0) {
            System.err.println("[warn] dropped incomplete tail line of $sessionId (line ${lastBad + 1})")
        }
        if (events.isEmpty()) throw CorruptSessionException("empty session: $sessionId")
        val first = events.first()
        if (first !is SessionEvent.SessionStart) throw CorruptSessionException("first event not session/start: $sessionId")
        if (first.data.formatVersion != 1) {
            throw UnknownSessionFormatException(
                "unsupported session formatVersion=${first.data.formatVersion} (only 1), session=$sessionId"
            )
        }
        seqCache[sessionId] = events.maxOf { it.seq }
        return events.filter { it.seq >= fromSeq }
    }

    fun list(): List<SessionMeta> {
        if (!Files.exists(home.sessions)) return emptyList()
        return Files.list(home.sessions).use { stream ->
            stream.filter { Files.isDirectory(it) && Files.exists(it.resolve(FILE_NAME)) }
                .map { dir ->
                    val first = Files.readAllLines(dir.resolve(FILE_NAME), StandardCharsets.UTF_8).firstOrNull()
                        ?: throw CorruptSessionException("empty log: $dir")
                    val event = MycoJson.decodeFromString(SessionEvent.serializer(), first)
                    (event as? SessionEvent.SessionStart)?.data
                        ?: throw CorruptSessionException("no session/start: $dir")
                }
                .toList()
                .sortedByDescending { it.createdAt }
        }
    }

    private fun ensureLoaded(sessionId: String) {
        if (seqCache.containsKey(sessionId)) return
        read(sessionId) // 填充缓存并校验
    }

    private fun appendLine(dir: Path, event: SessionEvent) {
        val line = MycoJson.encodeToString(SessionEvent.serializer(), event) + "\n"
        Files.writeString(
            dir.resolve(FILE_NAME), line, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        )
    }

    private fun sessionDir(sessionId: String): Path = home.sessions.resolve(sessionId)

    companion object {
        const val FILE_NAME = "events.v1.jsonl"
    }
}
