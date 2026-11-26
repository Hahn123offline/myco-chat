package dev.myco.core.session

import dev.myco.core.MycoHome
import dev.myco.core.io.TextFiles
import dev.myco.core.spec.MycoJson
import dev.myco.core.spec.SessionMeta
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * 会话的可变元数据（标题覆盖/软删/置顶）。
 * 事件日志保持 append-only 不变；这里只放"展示层"状态，避免为改名动 formatVersion。
 */
@Serializable
data class SessionSidecar(
    val schemaVersion: Int = 1,
    val title: String? = null,
    val deletedAt: Long? = null,
    val pinned: Boolean = false,
    val archivedAt: Long? = null,
)

/** 列表/搜索用的会话摘要：把 sidecar 与最近消息信息合成，组件不碰文件系统。 */
data class SessionSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val app: String,
    val preset: String,
    val formatVersion: Int,
    val lastMessageAt: Long,
    val preview: String?,
    val messageCount: Int,
    val pinned: Boolean,
    val archivedAt: Long?,
    val deletedAt: Long?,
)

data class SessionSearchHit(
    val session: SessionMeta,
    val snippet: String,
    val seq: Long? = null,
    val role: String? = null,
)

class UnknownSessionFormatException(message: String) : RuntimeException(message)
class CorruptSessionException(message: String) : RuntimeException(message)

/**
 * 会话 = append-only 事件日志（每会话一个 JSONL 文件）。
 *
 * 不变量：
 *  - seq 单调、由 store 分配；重放按 seq 排序，不按时间排序。
 *  - 首事件必须是 session/start(f=1)，否则拒绝打开（不自动迁移）。
 *  - 只允许最后一行不完整（崩溃尾部）：读侧丢弃并 warn；中间损坏必须报错。
 *
 * 可变元数据（标题/软删）放在同目录 `meta.v1.json`，不改事件日志。
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
        val raw = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)
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

    fun list(includeDeleted: Boolean = false): List<SessionMeta> {
        if (!Files.exists(home.sessions)) return emptyList()
        val dirs = Files.list(home.sessions).use { stream ->
            stream.filter { Files.isDirectory(it) && Files.exists(it.resolve(FILE_NAME)) }.toList()
        }
        return dirs.mapNotNull { dir ->
            val sidecar = readSidecar(dir)
            if (!includeDeleted && sidecar?.deletedAt != null) return@mapNotNull null
            val first = Files.readAllLines(dir.resolve(FILE_NAME), java.nio.charset.StandardCharsets.UTF_8)
                .firstOrNull() ?: throw CorruptSessionException("empty log: $dir")
            val event = MycoJson.decodeFromString(SessionEvent.serializer(), first)
            val meta = (event as? SessionEvent.SessionStart)?.data
                ?: throw CorruptSessionException("no session/start: $dir")
            // 标题覆盖：日志里的是创建时标题，sidecar 有则优先
            sidecar?.title?.let { meta.copy(title = it) } ?: meta
        }.sortedByDescending { it.createdAt }
    }

    /**
     * 抽屉/列表用摘要：最近活动时间、预览、消息数、置顶/归档/删除状态。
     * 默认排除归档与回收站；includeArchived/includeDeleted 用于归档页/回收站。
     */
    fun summaries(
        includeArchived: Boolean = false,
        includeDeleted: Boolean = false,
        app: String? = null,
    ): List<SessionSummary> {
        if (!Files.exists(home.sessions)) return emptyList()
        val dirs = Files.list(home.sessions).use { stream ->
            stream.filter { Files.isDirectory(it) && Files.exists(it.resolve(FILE_NAME)) }.toList()
        }
        return dirs.mapNotNull { dir ->
            val sidecar = readSidecar(dir)
            if (!includeDeleted && sidecar?.deletedAt != null) return@mapNotNull null
            if (!includeArchived && sidecar?.archivedAt != null) return@mapNotNull null
            val lines = Files.readAllLines(dir.resolve(FILE_NAME), java.nio.charset.StandardCharsets.UTF_8)
            val first = lines.firstOrNull() ?: throw CorruptSessionException("empty log: $dir")
            val event = MycoJson.decodeFromString(SessionEvent.serializer(), first)
            val meta = (event as? SessionEvent.SessionStart)?.data
                ?: throw CorruptSessionException("no session/start: $dir")
            if (app != null && meta.app != app) return@mapNotNull null
            val decoded = lines.mapNotNull { line ->
                if (line.isBlank()) null else runCatching { MycoJson.decodeFromString(SessionEvent.serializer(), line) }.getOrNull()
            }
            val lastAt = decoded.maxOfOrNull { it.at } ?: meta.createdAt
            val lastText = decoded.asReversed().firstNotNullOfOrNull { e ->
                when (e) {
                    is SessionEvent.UserMessage -> e.data.text
                    is SessionEvent.AssistantMessage -> e.data.text
                    else -> null
                }?.takeIf { it.isNotBlank() }
            }
            val msgCount = decoded.count { it is SessionEvent.UserMessage || it is SessionEvent.AssistantMessage }
            SessionSummary(
                id = meta.id,
                title = sidecar?.title ?: meta.title,
                createdAt = meta.createdAt,
                app = meta.app,
                preset = meta.preset,
                formatVersion = meta.formatVersion,
                lastMessageAt = lastAt,
                preview = lastText?.replace('\n', ' ')?.take(80),
                messageCount = msgCount,
                pinned = sidecar?.pinned ?: false,
                archivedAt = sidecar?.archivedAt,
                deletedAt = sidecar?.deletedAt,
            )
        }.sortedWith(compareByDescending<SessionSummary> { it.pinned }.thenByDescending { it.lastMessageAt })
    }

    fun setPinned(sessionId: String, pinned: Boolean) {
        val dir = requireSessionDir(sessionId)
        val current = readSidecar(dir) ?: SessionSidecar()
        writeSidecar(dir, current.copy(pinned = pinned))
    }

    fun archive(sessionId: String, archived: Boolean) {
        val dir = requireSessionDir(sessionId)
        val current = readSidecar(dir) ?: SessionSidecar()
        writeSidecar(dir, current.copy(archivedAt = if (archived) System.currentTimeMillis() else null))
    }

    /** 永久删除：删会话日志、sidecar 与该会话附件目录；不可恢复。 */
    fun deletePermanently(sessionId: String) {
        val dir = requireSessionDir(sessionId)
        dir.toFile().deleteRecursively()
        runCatching { home.attachments.resolve(sessionId).toFile().deleteRecursively() }
        seqCache.remove(sessionId)
    }

    private fun requireSessionDir(sessionId: String): Path {
        val dir = sessionDir(sessionId)
        if (!Files.exists(dir.resolve(FILE_NAME))) throw IllegalArgumentException("session not found: $sessionId")
        return dir
    }

    /** 标题可变（只改展示，不改历史事件）。空标题拒绝。 */
    fun rename(sessionId: String, title: String): SessionMeta {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "标题不能为空" }
        require(clean.length <= 80) { "标题最长 80 字符" }
        val dir = sessionDir(sessionId)
        if (!Files.exists(dir.resolve(FILE_NAME))) throw IllegalArgumentException("session not found: $sessionId")
        val current = readSidecar(dir) ?: SessionSidecar()
        writeSidecar(dir, current.copy(title = clean))
        return list(includeDeleted = true).firstOrNull { it.id == sessionId }
            ?: throw IllegalStateException("session list missing after rename: $sessionId")
    }

    /** 软删：保留日志与元数据，列表默认隐藏；可 restore。 */
    fun delete(sessionId: String) {
        val dir = sessionDir(sessionId)
        if (!Files.exists(dir.resolve(FILE_NAME))) throw IllegalArgumentException("session not found: $sessionId")
        val current = readSidecar(dir) ?: SessionSidecar()
        writeSidecar(dir, current.copy(deletedAt = System.currentTimeMillis()))
    }

    fun restore(sessionId: String) {
        val dir = sessionDir(sessionId)
        if (!Files.exists(dir.resolve(FILE_NAME))) throw IllegalArgumentException("session not found: $sessionId")
        val current = readSidecar(dir) ?: return
        writeSidecar(dir, current.copy(deletedAt = null))
    }

    /** 搜索：标题与会话内 user/assistant 文本；返回命中消息 seq/role 便于"跳到那条"。 */
    fun search(query: String, limit: Int = 50, includeArchived: Boolean = false): List<SessionSearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<SessionSearchHit>()
        for (summary in summaries(includeArchived = includeArchived)) {
            if (hits.size >= limit) break
            val meta = SessionMeta(
                id = summary.id, title = summary.title, createdAt = summary.createdAt,
                app = summary.app, preset = summary.preset, formatVersion = summary.formatVersion,
            )
            val titleHit = summary.title.contains(q, ignoreCase = true)
            val events = try {
                read(summary.id)
            } catch (e: Exception) {
                continue
            }
            var snippet: String? = null
            var hitSeq: Long? = null
            var hitRole: String? = null
            for (e in events) {
                val (text, role) = when (e) {
                    is SessionEvent.UserMessage -> e.data.text to "user"
                    is SessionEvent.AssistantMessage -> e.data.text to "assistant"
                    else -> continue
                }
                if (text.contains(q, ignoreCase = true)) {
                    val idx = text.indexOf(q, ignoreCase = true).coerceAtLeast(0)
                    val start = (idx - 30).coerceAtLeast(0)
                    snippet = text.substring(start, (idx + q.length + 60).coerceAtMost(text.length))
                    hitSeq = e.seq
                    hitRole = role
                    break
                }
            }
            if (titleHit || snippet != null) {
                hits += SessionSearchHit(meta, snippet ?: meta.title, hitSeq, hitRole ?: if (titleHit) "title" else null)
            }
        }
        return hits
    }

    private fun readSidecar(dir: Path): SessionSidecar? {
        val f = dir.resolve(SIDECAR_NAME)
        if (!Files.exists(f)) return null
        return runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(SessionSidecar.serializer(), TextFiles.read(f))
        }.getOrNull()
    }

    private fun writeSidecar(dir: Path, sidecar: SessionSidecar) {
        TextFiles.write(dir.resolve(SIDECAR_NAME), MycoJson.encodeToString(SessionSidecar.serializer(), sidecar))
    }

    private fun ensureLoaded(sessionId: String) {
        if (seqCache.containsKey(sessionId)) return
        read(sessionId) // 填充缓存并校验
    }

    private fun appendLine(dir: Path, event: SessionEvent) {
        val line = MycoJson.encodeToString(SessionEvent.serializer(), event) + "\n"
        dev.myco.core.io.TextFiles.append(dir.resolve(FILE_NAME), line)
    }

    private fun sessionDir(sessionId: String): Path = home.sessions.resolve(sessionId)

    companion object {
        const val FILE_NAME = "events.v1.jsonl"
        const val SIDECAR_NAME = "meta.v1.json"
    }
}
