package dev.myco.bridge.http

import dev.myco.core.bridge.Bridge
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.CommandResult
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import dev.myco.core.llm.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/** http-sse 的客户端：同一套 Bridge 语义，供浏览器/远程壳使用。 */
class HttpBridgeClient(
    private val baseUrl: String,
    private val token: String = "",
) : Bridge {

    override suspend fun send(cmd: Command): CommandResult {
        val (name, payload) = when (cmd) {
            is Command.SessionEnsureDraft -> "session.ensureDraft" to buildJsonObject { put("app", cmd.app); put("preset", cmd.preset) }
            is Command.SessionGenerateTitle -> "session.generateTitle" to buildJsonObject { put("sessionId", cmd.sessionId) }
            is Command.SessionSetActiveLeaf -> "session.setActiveLeaf" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("leafId", cmd.leafId)
            }
            is Command.SessionCreate -> "session.create" to buildJsonObject {
                put("app", cmd.app); put("preset", cmd.preset); put("title", cmd.title)
            }
            is Command.SessionSetModel -> "session.setModel" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("providerId", cmd.providerId)
                put("modelId", cmd.modelId); put("reasoningLevel", cmd.reasoningLevel)
            }
            is Command.SessionRename -> "session.rename" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("title", cmd.title)
            }
            is Command.SessionDelete -> "session.delete" to buildJsonObject { put("sessionId", cmd.sessionId) }
            is Command.SessionRestore -> "session.restore" to buildJsonObject { put("sessionId", cmd.sessionId) }
            is Command.SessionPin -> "session.pin" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("pinned", cmd.pinned)
            }
            is Command.SessionArchive -> "session.archive" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("archived", cmd.archived)
            }
            is Command.SessionDeletePermanent -> "session.deletePermanent" to buildJsonObject { put("sessionId", cmd.sessionId) }
            is Command.ChatSend -> "chat.send" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("text", cmd.text)
                cmd.requestId?.let { put("requestId", it) }
                cmd.thinking?.let { put("thinking", it) }
                cmd.webSearch?.let { put("webSearch", it) }
                if (cmd.attachments.isNotEmpty()) {
                    put("attachments", dev.myco.core.spec.MycoJson.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(dev.myco.core.spec.AttachmentRef.serializer()),
                        cmd.attachments,
                    ))
                }
            }
            is Command.TranscribeAudio -> "audio.transcribe" to buildJsonObject {
                put("name", cmd.name); put("mime", cmd.mime); put("base64", cmd.base64)
            }
            is Command.AttachmentSave -> "attachment.save" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("name", cmd.name)
                put("mime", cmd.mime); put("base64", cmd.base64)
            }
            is Command.ProviderSave -> "provider.save" to buildJsonObject {
                put("profile", dev.myco.core.spec.MycoJson.encodeToJsonElement(
                    dev.myco.core.spec.ProviderProfile.serializer(), cmd.profile
                ))
                put("expectedRevision", cmd.expectedRevision)
            }
            is Command.ProviderDelete -> "provider.delete" to buildJsonObject {
                put("id", cmd.id); put("expectedRevision", cmd.expectedRevision)
            }
            is Command.CredentialSet -> "credential.set" to buildJsonObject {
                put("ref", cmd.ref); put("value", cmd.value)
            }
            is Command.ProviderDiscover -> "provider.discover" to buildJsonObject {
                put("profile", dev.myco.core.spec.MycoJson.encodeToJsonElement(
                    dev.myco.core.spec.ProviderProfile.serializer(), cmd.profile
                ))
            }
            is Command.ChatStop -> "chat.stop" to buildJsonObject {
                put("sessionId", cmd.sessionId); cmd.turnId?.let { put("turnId", it) }
            }
            is Command.ChatRegenerate -> "chat.regenerate" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("messageId", cmd.messageId)
                cmd.requestId?.let { put("requestId", it) }
            }
            is Command.ChatEditResend -> "chat.editResend" to buildJsonObject {
                put("sessionId", cmd.sessionId); put("messageId", cmd.messageId); put("text", cmd.text)
                cmd.requestId?.let { put("requestId", it) }
            }
            is Command.ProviderReplace -> "provider.replace" to buildJsonObject {
                put("providers", dev.myco.core.spec.MycoJson.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(dev.myco.core.spec.ProviderProfile.serializer()),
                    cmd.profiles,
                ))
                put("expectedRevision", cmd.expectedRevision)
            }
        }
        val body = post("/command/$name", payload)
        return if (body["ok"]?.jsonPrimitive?.content == "true") {
            CommandResult.Ok(body["data"] ?: buildJsonObject { })
        } else {
            val err = body["error"]?.jsonObject
            CommandResult.Err(
                code = err?.get("code")?.jsonPrimitive?.content ?: "HTTP_ERROR",
                message = err?.get("message")?.jsonPrimitive?.content ?: "unknown",
                retriable = err?.get("retriable")?.jsonPrimitive?.content == "true",
            )
        }
    }

    override suspend fun query(q: Query): QueryResult {
        val (name, payload) = when (q) {
            is Query.SessionModel -> "/session.model" to buildJsonObject { put("sessionId", q.sessionId) }
            is Query.SessionList -> "/session.list" to buildJsonObject {
                q.app?.let { put("app", it) }
                put("includeArchived", q.includeArchived)
                put("includeDeleted", q.includeDeleted)
            }
            is Query.SessionSearch -> "/session.search" to buildJsonObject {
                put("query", q.query); put("limit", q.limit); put("includeArchived", q.includeArchived)
            }
            is Query.EventReplay -> "/event.replay" to buildJsonObject {
                put("sessionId", q.sessionId); put("fromSeq", q.fromSeq); put("limit", q.limit)
            }
            is Query.SessionEvents -> "/session.events" to buildJsonObject {
                put("sessionId", q.sessionId); put("fromSeq", q.fromSeq)
            }
            Query.Providers -> "/providers" to buildJsonObject { }
            Query.ProviderExport -> "/provider.export" to buildJsonObject { }
            Query.DoctorRun -> "/doctor" to buildJsonObject { }
        }
        val body = post("/query$name", payload)
        return if (body["ok"]?.jsonPrimitive?.content == "true") {
            QueryResult.Ok(body["data"] ?: buildJsonObject { })
        } else {
            val err = body["error"]?.jsonObject
            QueryResult.Err(
                code = err?.get("code")?.jsonPrimitive?.content ?: "HTTP_ERROR",
                message = err?.get("message")?.jsonPrimitive?.content ?: "unknown",
            )
        }
    }

    override val events: Flow<BridgeEvent> = channelFlow {
        // SSE 用裸 Socket：HttpURLConnection 的 chunked 流在另一线程 disconnect 会死锁（实测）。
        // 取消时直接 socket.close()（线程安全），读取线程会抛 SocketException 退出。
        val uri = URI.create("$baseUrl/events")
        val host = uri.host
        val port = if (uri.port > 0) uri.port else 80
        val socket = java.net.Socket()
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = launch(Dispatchers.IO) {
            try {
                socket.connect(java.net.InetSocketAddress(host, port), 10_000)
                val request = buildString {
                    append("GET /events HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Accept: text/event-stream\r\n")
                    if (token.isNotEmpty()) append("Authorization: Bearer $token\r\n")
                    append("Connection: close\r\n\r\n")
                }
                socket.getOutputStream().write(request.toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()

                val input = java.io.BufferedInputStream(socket.getInputStream())
                fun readLineBytes(): String? {
                    val out = java.io.ByteArrayOutputStream()
                    while (true) {
                        val b = input.read()
                        if (b < 0) return if (out.size() == 0) null else out.toString(StandardCharsets.UTF_8)
                        if (b == '\n'.code) return out.toString(StandardCharsets.UTF_8)
                        if (b != '\r'.code) out.write(b)
                    }
                }
                fun readExactly(n: Int): ByteArray {
                    val bytes = ByteArray(n)
                    var off = 0
                    while (off < n) {
                        val r = input.read(bytes, off, n - off)
                        if (r < 0) throw java.io.EOFException("chunk truncated")
                        off += r
                    }
                    return bytes
                }
                val status = readLineBytes() ?: return@launch
                if (!status.contains("200")) return@launch
                while (true) {
                    val h = readLineBytes() ?: break
                    if (h.isEmpty()) break
                }
                val parser = SseParser()
                val pendingLine = StringBuilder()
                suspend fun feedText(text: String) {
                    pendingLine.append(text)
                    while (true) {
                        val idx = pendingLine.indexOf("\n")
                        if (idx < 0) break
                        val line = pendingLine.substring(0, idx).trimEnd('\r')
                        pendingLine.delete(0, idx + 1)
                        val frame = parser.feed(line) ?: continue
                        parseEvent(frame.data)?.let { send(it) }
                    }
                }
                // chunked 的 size 是**字节数**，必须按字节读再解码 UTF-8（按字符读会串位）
                while (!closed.get()) {
                    val sizeLine = readLineBytes() ?: break
                    val chunkSize = sizeLine.trim().toIntOrNull(16) ?: continue
                    if (chunkSize == 0) break
                    val bytes = readExactly(chunkSize)
                    readLineBytes() // 末尾 CRLF
                    feedText(bytes.toString(StandardCharsets.UTF_8))
                }
                parser.finish()?.let { frame -> parseEvent(frame.data)?.let { send(it) } }
            } catch (_: Exception) {
            } finally {
                runCatching { socket.close() }
            }
        }
        awaitClose {
            closed.set(true)
            runCatching { socket.close() }
            job.cancel()
        }
    }

    private fun post(path: String, payload: JsonObject): JsonObject {
        val conn = (URI.create(baseUrl + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            if (token.isNotEmpty()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }
                ?: "{}"
            return Json.parseToJsonElement(text).jsonObject
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun parseEvent(data: String): BridgeEvent? {
        val o = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        val seq = o["seq"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
        val sid = o["sessionId"]?.jsonPrimitive?.content ?: ""
        return when (o["type"]?.jsonPrimitive?.content) {
            "assistant.delta" -> BridgeEvent.AssistantDelta(
                sid,
                o["turnId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                o["text"]?.jsonPrimitive?.content ?: "",
                messageId = o["messageId"]?.jsonPrimitive?.content,
                seq = seq,
            )
            "reasoning.delta" -> BridgeEvent.ReasoningDelta(
                sid,
                o["turnId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                o["text"]?.jsonPrimitive?.content ?: "",
                seq,
            )
            "assistant.done" -> BridgeEvent.AssistantDone(
                sid,
                o["turnId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                o["text"]?.jsonPrimitive?.content ?: "",
                stopped = o["stopped"]?.jsonPrimitive?.content == "true",
                messageId = o["messageId"]?.jsonPrimitive?.content,
                finishReason = o["finishReason"]?.jsonPrimitive?.content,
                seq = seq,
            )
            "failed" -> BridgeEvent.Failed(
                o["code"]?.jsonPrimitive?.content ?: "ERROR",
                o["message"]?.jsonPrimitive?.content ?: "",
                o["retriable"]?.jsonPrimitive?.content == "true",
                sessionId = sid.ifBlank { null },
                seq = seq,
            )
            "session.updated" -> BridgeEvent.SessionUpdated(sid, seq)
            "tool.started" -> BridgeEvent.ToolStarted(
                sid,
                o["turnId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                o["name"]?.jsonPrimitive?.content ?: "",
                seq,
            )
            "tool.finished" -> BridgeEvent.ToolFinished(
                sid,
                o["turnId"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                o["name"]?.jsonPrimitive?.content ?: "",
                ok = o["ok"]?.jsonPrimitive?.content == "true",
                text = o["text"]?.jsonPrimitive?.content ?: "",
                seq = seq,
            )
            else -> null
        }
    }
}



