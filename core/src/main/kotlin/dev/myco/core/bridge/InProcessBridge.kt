package dev.myco.core.bridge

import dev.myco.core.MycoHome
import dev.myco.core.doctor.Doctor
import dev.myco.core.llm.ChatChunk
import dev.myco.core.llm.ChatClient
import dev.myco.core.llm.ChatMessage
import dev.myco.core.llm.ChatRequest
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ProviderStore
import dev.myco.core.session.SessionEvent
import dev.myco.core.session.SessionStore
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.SessionMeta
import dev.myco.core.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * in-process bridge：GUI 与核心同进程直连，不开端口（手机端默认）。
 * S0 的 chat.send 是"跑完一轮才返回"，GUI 靠 events 流拿到增量。
 */
class InProcessBridge(
    private val home: MycoHome,
    private val sessions: SessionStore,
    private val providers: ProviderStore,
    private val credentials: CredentialResolver,
    private val tools: ToolRegistry,
    private val clientFactory: (ProviderProfile) -> ChatClient,
) : Bridge {

    private val _events = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 256)
    override val events: Flow<BridgeEvent> = _events.asSharedFlow()

    private val doctor = Doctor(home, sessions, providers, credentials, tools)

    override suspend fun send(cmd: Command): CommandResult = when (cmd) {
        is Command.SessionCreate -> {
            val meta = sessions.create(cmd.app, cmd.preset, cmd.title)
            _events.tryEmit(BridgeEvent.SessionUpdated(meta.id))
            CommandResult.Ok(buildJsonObject { put("sessionId", meta.id) })
        }
        is Command.ChatSend -> chatSend(cmd)
    }

    private suspend fun chatSend(cmd: Command.ChatSend): CommandResult {
        val provider = providers.firstEnabled()
            ?: return CommandResult.Err("PROVIDER_MISSING", "还没有配置 provider，请先在设置里添加")
        val model = provider.model("chat")
            ?: return CommandResult.Err("MODEL_MISSING", "provider ${provider.id} 没有 chat 角色模型")

        val history = runCatching { sessions.read(cmd.sessionId) }
            .getOrElse { return CommandResult.Err("SESSION_NOT_FOUND", "会话不存在：${cmd.sessionId}") }

        val turn = history.count { it is SessionEvent.TurnStart }.toLong() + 1
        sessions.append(cmd.sessionId, SessionEvent.TurnStart(data = SessionEvent.TurnStart.Data(turn)))
        sessions.append(
            cmd.sessionId,
            SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data(cmd.text, source = "human"))
        )

        val messages = buildMessages(history) + ChatMessage("user", cmd.text)
        val client = clientFactory(provider)
        val full = StringBuilder()
        var failed: ChatChunk.Failed? = null

        client.stream(ChatRequest(model = model.id, messages = messages, maxTokens = model.maxTokens)).collect { chunk ->
            when (chunk) {
                is ChatChunk.Delta -> {
                    full.append(chunk.text)
                    _events.tryEmit(BridgeEvent.AssistantDelta(cmd.sessionId, turn, chunk.text))
                }
                is ChatChunk.Done -> {
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.AssistantMessage(
                            data = SessionEvent.AssistantMessage.Data(chunk.text, chunk.model, chunk.usage)
                        )
                    )
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.TurnEnd(data = SessionEvent.TurnEnd.Data(turn, steps = 1, chunk.usage?.costUsd))
                    )
                    _events.tryEmit(BridgeEvent.AssistantDone(cmd.sessionId, turn, chunk.text))
                }
                is ChatChunk.Failed -> failed = chunk
            }
        }

        failed?.let {
            _events.tryEmit(BridgeEvent.Failed("LLM_FAILED", it.message, it.retriable))
            return CommandResult.Err("LLM_FAILED", it.message, it.retriable)
        }
        return CommandResult.Ok(buildJsonObject { put("turnId", turn); put("text", full.toString()) })
    }

    private fun buildMessages(history: List<SessionEvent>): List<ChatMessage> = history.mapNotNull { e ->
        when (e) {
            is SessionEvent.UserMessage -> ChatMessage("user", e.data.text)
            is SessionEvent.AssistantMessage -> ChatMessage("assistant", e.data.text)
            else -> null
        }
    }

    override suspend fun query(q: Query): QueryResult = when (q) {
        is Query.SessionList -> {
            val list = sessions.list().filter { q.app == null || it.app == q.app }
            QueryResult.Ok(buildJsonArray { list.forEach { add(sessionJson(it)) } })
        }
        is Query.SessionEvents -> {
            val events = runCatching { sessions.readFrom(q.sessionId, q.fromSeq) }
                .getOrElse { return QueryResult.Err("SESSION_NOT_FOUND", it.message ?: "读取失败") }
            QueryResult.Ok(buildJsonArray { events.forEach { add(eventJson(it)) } })
        }
        Query.Providers -> {
            val list = providers.load().providers
            QueryResult.Ok(buildJsonArray {
                list.forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("kind", p.kind)
                        put("enabled", p.enabled)
                        put("models", buildJsonArray { p.models.forEach { m -> add(kotlinx.serialization.json.JsonPrimitive(m.id)) } })
                    })
                }
            })
        }
        Query.DoctorRun -> QueryResult.Ok(buildJsonArray {
            doctor.run().forEach { c ->
                add(buildJsonObject { put("name", c.name); put("status", c.status.name); put("detail", c.detail) })
            }
        })
    }

    private fun sessionJson(m: SessionMeta): JsonElement = buildJsonObject {
        put("id", m.id); put("title", m.title); put("createdAt", m.createdAt)
        put("app", m.app); put("preset", m.preset); put("formatVersion", m.formatVersion)
    }

    private fun eventJson(e: SessionEvent): JsonElement = buildJsonObject {
        put("type", typeName(e))
        put("seq", e.seq)
        put("at", e.at)
    }

    private fun typeName(e: SessionEvent): String = when (e) {
        is SessionEvent.SessionStart -> "session/start"
        is SessionEvent.TurnStart -> "turn/start"
        is SessionEvent.UserMessage -> "user/message"
        is SessionEvent.AssistantMessage -> "assistant/message"
        is SessionEvent.ToolCall -> "tool/call"
        is SessionEvent.ToolResult -> "tool/result"
        is SessionEvent.SystemInject -> "system/inject"
        is SessionEvent.TurnEnd -> "turn/end"
    }
}
