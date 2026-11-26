package dev.myco.bridge.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.myco.core.bridge.Bridge
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.CommandResult
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * http-sse 传输（A5 的第二种 Bridge 实现）：浏览器 / 远程客户端 / 调试用。
 * 手机端默认仍是 in-process；这里只做桌面/远程。
 *
 * 端点（bridgeVersion 1）：
 *   GET  /health
 *   POST /command/session.create | /command/chat.send     body=payload JSON
 *   POST /query/session.list | /query/session.events | /query.providers | /query.doctor
 *   GET  /events?sessionId=   SSE 事件流
 * 鉴权：Authorization: Bearer <token>（/health 除外）
 */
class HttpBridgeServer(
    private val bridge: Bridge,
    private val token: String,
    port: Int = 0,
    host: String = "127.0.0.1",
) : AutoCloseable {
    private val executor: java.util.concurrent.ExecutorService =
        Executors.newCachedThreadPool { r -> Thread(r, "myco-bridge-http").apply { isDaemon = true } }

    private val server: HttpServer = HttpServer.create(InetSocketAddress(host, port), 0).apply {
        executor = this@HttpBridgeServer.executor
        createContext("/health") { ex -> withAuth(ex) { json(ex, 200, """{"ok":true,"bridgeVersion":1}""") } }
        createContext("/command") { ex -> withAuth(ex) { handleCommand(ex) } }
        createContext("/query") { ex -> withAuth(ex) { handleQuery(ex) } }
        createContext("/events") { ex -> withAuth(ex) { handleEvents(ex) } }
    }

    val port: Int get() = server.address.port
    val baseUrl: String get() = "http://${server.address.hostString}:$port"

    fun start(): HttpBridgeServer = apply { server.start() }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun withAuth(ex: HttpExchange, block: () -> Unit) {
        if (token.isNotEmpty()) {
            val header = ex.requestHeaders.getFirst("Authorization")
            if (header != "Bearer $token") {
                json(ex, 401, """{"ok":false,"error":{"code":"UNAUTHORIZED","message":"bad token","retriable":false}}""")
                return
            }
        }
        block()
    }

    private fun handleCommand(ex: HttpExchange) {
        if (ex.requestMethod != "POST") return json(ex, 405, err("METHOD", "POST only"))
        val name = ex.requestURI.path.removePrefix("/command/")
        val payload = readJson(ex) as? JsonObject ?: JsonObject(emptyMap())
        val cmd: Command = when (name) {
            "session.create" -> Command.SessionCreate(
                app = payload["app"]?.jsonPrimitive?.content ?: "chat",
                preset = payload["preset"]?.jsonPrimitive?.content ?: "chat-default",
                title = payload["title"]?.jsonPrimitive?.content ?: "新会话",
            )
            "chat.send" -> {
                val attachments = payload["attachments"]?.let { arr ->
                    runCatching {
                        dev.myco.core.spec.MycoJson.decodeFromJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(dev.myco.core.spec.AttachmentRef.serializer()), arr
                        )
                    }.getOrElse { return json(ex, 400, err("PAYLOAD", "bad attachments: ${it.message}")) }
                } ?: emptyList()
                Command.ChatSend(
                    sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                    text = payload["text"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing text")),
                    attachments = attachments,
                )
            }
            "attachment.save" -> Command.AttachmentSave(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                name = payload["name"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing name")),
                mime = payload["mime"]?.jsonPrimitive?.content ?: "application/octet-stream",
                base64 = payload["base64"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing base64")),
            )
            "provider.save" -> {
                val profileJson = payload["profile"] ?: return json(ex, 400, err("PAYLOAD", "missing profile"))
                val profile = runCatching {
                    dev.myco.core.spec.MycoJson.decodeFromJsonElement(
                        dev.myco.core.spec.ProviderProfile.serializer(), profileJson
                    )
                }.getOrElse { return json(ex, 400, err("PAYLOAD", "bad profile: ${it.message}")) }
                Command.ProviderSave(
                    profile = profile,
                    expectedRevision = payload["expectedRevision"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }
            "provider.delete" -> Command.ProviderDelete(
                id = payload["id"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing id")),
                expectedRevision = payload["expectedRevision"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
            "credential.set" -> Command.CredentialSet(
                ref = payload["ref"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing ref")),
                value = payload["value"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing value")),
            )
            "provider.discover" -> {
                val profileJson = payload["profile"] ?: return json(ex, 400, err("PAYLOAD", "missing profile"))
                val profile = runCatching {
                    dev.myco.core.spec.MycoJson.decodeFromJsonElement(
                        dev.myco.core.spec.ProviderProfile.serializer(), profileJson
                    )
                }.getOrElse { return json(ex, 400, err("PAYLOAD", "bad profile: ${it.message}")) }
                Command.ProviderDiscover(profile)
            }
            "chat.stop" -> Command.ChatStop(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
            )
            "session.rename" -> Command.SessionRename(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                title = payload["title"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing title")),
            )
            "session.delete" -> Command.SessionDelete(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
            )
            "session.restore" -> Command.SessionRestore(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
            )
            "session.pin" -> Command.SessionPin(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                pinned = payload["pinned"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
            )
            "session.archive" -> Command.SessionArchive(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                archived = payload["archived"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
            )
            "session.deletePermanent" -> Command.SessionDeletePermanent(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
            )
            "provider.replace" -> {
                val arr = payload["providers"] as? kotlinx.serialization.json.JsonArray
                    ?: return json(ex, 400, err("PAYLOAD", "missing providers array"))
                val list = runCatching {
                    dev.myco.core.spec.MycoJson.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(dev.myco.core.spec.ProviderProfile.serializer()), arr
                    )
                }.getOrElse { return json(ex, 400, err("PAYLOAD", "bad providers: ${it.message}")) }
                Command.ProviderReplace(
                    profiles = list,
                    expectedRevision = payload["expectedRevision"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }
            else -> return json(ex, 404, err("UNKNOWN_COMMAND", name))
        }
        val result = runBlocking { bridge.send(cmd) }
        json(ex, 200, commandResultJson(result).toString())
    }

    private fun handleQuery(ex: HttpExchange) {
        if (ex.requestMethod != "POST") return json(ex, 405, err("METHOD", "POST only"))
        val name = ex.requestURI.path.removePrefix("/query")
        val payload = readJson(ex) as? JsonObject ?: JsonObject(emptyMap())
        val q: Query = when (name) {
            "/session.list" -> Query.SessionList(
                app = payload["app"]?.jsonPrimitive?.content,
                includeArchived = payload["includeArchived"]?.jsonPrimitive?.content == "true",
                includeDeleted = payload["includeDeleted"]?.jsonPrimitive?.content == "true",
            )
            "/session.events" -> Query.SessionEvents(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                fromSeq = payload["fromSeq"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
            "/providers" -> Query.Providers
            "/session.search" -> Query.SessionSearch(
                query = payload["query"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing query")),
                limit = payload["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50,
                includeArchived = payload["includeArchived"]?.jsonPrimitive?.content == "true",
            )
            "/event.replay" -> Query.EventReplay(
                sessionId = payload["sessionId"]?.jsonPrimitive?.content ?: return json(ex, 400, err("PAYLOAD", "missing sessionId")),
                fromSeq = payload["fromSeq"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                limit = payload["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 500,
            )
            "/provider.export" -> Query.ProviderExport
            "/doctor" -> Query.DoctorRun
            else -> return json(ex, 404, err("UNKNOWN_QUERY", name))
        }
        json(ex, 200, queryResultJson(runBlocking { bridge.query(q) }).toString())
    }

    private fun handleEvents(ex: HttpExchange) {
        if (ex.requestMethod != "GET") return json(ex, 405, err("METHOD", "GET only"))
        val params = ex.requestURI.query?.split("&")
            ?.mapNotNull { it.split("=").takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }
            .orEmpty()
        val sessionId = params.firstOrNull { it.first == "sessionId" }?.second
        val fromSeq = params.firstOrNull { it.first == "fromSeq" }?.second?.toLongOrNull() ?: 0L
        ex.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
        ex.responseHeaders.add("Cache-Control", "no-cache")
        ex.sendResponseHeaders(200, 0)
        val out = ex.responseBody

        var lastSeq = fromSeq

        try {
            // 先补发断线期间的运行期事件（内存环形缓冲），再订阅新事件
            if (sessionId != null) {
                val replay = runBlocking { bridge.query(Query.EventReplay(sessionId, fromSeq)) }
                val arr = (replay as? QueryResult.Ok)?.data as? kotlinx.serialization.json.JsonArray
                arr?.forEach { el ->
                    out.write("event: bridge\ndata: $el\n\n".toByteArray(StandardCharsets.UTF_8))
                    val seq = (el as? kotlinx.serialization.json.JsonObject)?.get("seq")
                        ?.jsonPrimitive?.content?.toLongOrNull() ?: lastSeq
                    if (seq > lastSeq) lastSeq = seq
                }
                out.flush()
            }
            runBlocking {
                bridge.events.collect { e ->
                    if (sessionId != null && e.sessionId != null && e.sessionId != sessionId) return@collect
                    if (sessionId != null && e.seq <= lastSeq) return@collect
                    if (e.seq > lastSeq) lastSeq = e.seq


                    out.write("event: bridge\ndata: ${eventJson(e)}\n\n".toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
            }
        } catch (_: Exception) {
            // 客户端断开 / 服务停止是正常结束
        } finally {
            runCatching { out.close() }
        }
    }

    private fun eventSessionId(e: BridgeEvent): String? = when (e) {
        is BridgeEvent.AssistantDelta -> e.sessionId
        is BridgeEvent.ReasoningDelta -> e.sessionId
        is BridgeEvent.AssistantDone -> e.sessionId
        is BridgeEvent.SessionUpdated -> e.sessionId
        is BridgeEvent.ToolStarted -> e.sessionId
        is BridgeEvent.ToolFinished -> e.sessionId
        is BridgeEvent.Failed -> e.sessionId
    }

    private fun eventJson(e: BridgeEvent): String = buildJsonObject {
        put("seq", e.seq)
        put("sessionId", e.sessionId ?: "")
        when (e) {
            is BridgeEvent.AssistantDelta -> {
                put("type", "assistant.delta"); put("turnId", e.turnId); put("text", e.text)
                e.messageId?.let { put("messageId", it) }
            }
            is BridgeEvent.ReasoningDelta -> {
                put("type", "reasoning.delta"); put("turnId", e.turnId); put("text", e.text)
            }
            is BridgeEvent.AssistantDone -> {
                put("type", "assistant.done"); put("turnId", e.turnId)
                put("text", e.text); put("stopped", e.stopped)
                e.messageId?.let { put("messageId", it) }
                e.finishReason?.let { put("finishReason", it) }
                e.usage?.let { u ->
                    put("usage", dev.myco.core.spec.MycoJson.encodeToJsonElement(dev.myco.core.spec.Usage.serializer(), u))
                }
            }
            is BridgeEvent.Failed -> {
                put("type", "failed"); put("code", e.code); put("message", e.message); put("retriable", e.retriable)
            }
            is BridgeEvent.SessionUpdated -> put("type", "session.updated")
            is BridgeEvent.ToolStarted -> {
                put("type", "tool.started"); put("turnId", e.turnId); put("name", e.name)
            }
            is BridgeEvent.ToolFinished -> {
                put("type", "tool.finished"); put("turnId", e.turnId)
                put("name", e.name); put("ok", e.ok); put("text", e.text)
            }
        }
    }.toString()

    private fun commandResultJson(r: CommandResult): JsonElement = when (r) {
        is CommandResult.Ok -> buildJsonObject { put("ok", true); put("data", r.data) }
        is CommandResult.Err -> buildJsonObject {
            put("ok", false)
            put("error", buildJsonObject { put("code", r.code); put("message", r.message); put("retriable", r.retriable) })
        }
    }

    private fun queryResultJson(r: QueryResult): JsonElement = when (r) {
        is QueryResult.Ok -> buildJsonObject { put("ok", true); put("data", r.data) }
        is QueryResult.Err -> buildJsonObject {
            put("ok", false)
            put("error", buildJsonObject { put("code", r.code); put("message", r.message) })
        }
    }

    private fun readJson(ex: HttpExchange): JsonElement? {
        val text = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        return runCatching { Json.parseToJsonElement(text) }.getOrNull()
    }

    private fun json(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun err(code: String, msg: String) =
        """{"ok":false,"error":{"code":"$code","message":"$msg","retriable":false}}"""
}




