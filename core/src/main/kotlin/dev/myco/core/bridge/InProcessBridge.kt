package dev.myco.core.bridge

import dev.myco.core.MycoHome
import dev.myco.core.doctor.Doctor
import dev.myco.core.llm.ChatChunk
import dev.myco.core.llm.ChatClient
import dev.myco.core.llm.ChatMessage
import dev.myco.core.llm.ChatRequest
import dev.myco.core.llm.ToolSpec
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ProviderStore
import dev.myco.core.session.SessionEvent
import dev.myco.core.session.SessionStore
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.spec.SessionMeta
import dev.myco.core.tools.ToolRegistry
import kotlinx.coroutines.async
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

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )
    private val activeTurns = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override suspend fun send(cmd: Command): CommandResult = when (cmd) {
        is Command.SessionCreate -> {
            val meta = sessions.create(cmd.app, cmd.preset, cmd.title)
            _events.tryEmit(BridgeEvent.SessionUpdated(meta.id))
            CommandResult.Ok(buildJsonObject { put("sessionId", meta.id) })
        }
        is Command.ChatSend -> chatSend(cmd)
        is Command.ChatStop -> chatStop(cmd)
        is Command.ProviderSave -> providerSave(cmd)
        is Command.ProviderDelete -> providerDelete(cmd)
        is Command.CredentialSet -> {
            runCatching { credentials.set(cmd.ref, cmd.value) }
                .fold(
                    onSuccess = { CommandResult.Ok() },
                    onFailure = { CommandResult.Err("INVALID_CREDENTIAL", it.message ?: "写入失败") },
                )
        }
        is Command.ProviderDiscover -> {
            runCatching { dev.myco.core.provider.ModelDiscovery.discover(cmd.profile, credentials) }
                .fold(
                    onSuccess = { models ->
                        CommandResult.Ok(buildJsonObject {
                            put("models", buildJsonArray {
                                models.forEach { m ->
                                    add(buildJsonObject {
                                        put("id", m.id)
                                        put("displayName", m.displayName ?: "")
                                        m.contextWindow?.let { put("contextWindow", it) }
                                        m.maxOutputTokens?.let { put("maxTokens", it) }
                                    })
                                }
                            })
                        })
                    },
                    onFailure = { e ->
                        val code = if (e.message?.contains("缺少凭据") == true || e.message?.contains("api-key") == true)
                            "CREDENTIAL_MISSING" else "DISCOVERY_FAILED"
                        CommandResult.Err(code, e.message ?: "获取模型失败")
                    },
                )
        }
        is Command.ProviderReplace -> {
            runCatching { providers.save(cmd.profiles, cmd.expectedRevision) }
                .fold(
                    onSuccess = { rev -> CommandResult.Ok(buildJsonObject { put("revision", rev) }) },
                    onFailure = { e ->
                        val code = if (e is dev.myco.core.settings.SettingsConflictException) "SETTINGS_CONFLICT" else "PROVIDER_CONFIG"
                        CommandResult.Err(code, e.message ?: "导入失败")
                    },
                )
        }
    }

    private fun providerSave(cmd: Command.ProviderSave): CommandResult {
        val current = runCatching { providers.load() }
            .getOrElse { return CommandResult.Err("PROVIDER_CONFIG", it.message ?: "读取失败") }
        val merged = current.providers.toMutableList()
        val idx = merged.indexOfFirst { it.id == cmd.profile.id }
        if (idx >= 0) merged[idx] = cmd.profile else merged += cmd.profile
        return try {
            val rev = providers.save(merged, cmd.expectedRevision)
            CommandResult.Ok(buildJsonObject { put("revision", rev) })
        } catch (e: dev.myco.core.settings.SettingsConflictException) {
            CommandResult.Err("SETTINGS_CONFLICT", e.message ?: "revision 冲突")
        } catch (e: Exception) {
            CommandResult.Err("PROVIDER_CONFIG", e.message ?: "保存失败")
        }
    }

    private fun providerDelete(cmd: Command.ProviderDelete): CommandResult {
        val current = runCatching { providers.load() }
            .getOrElse { return CommandResult.Err("PROVIDER_CONFIG", it.message ?: "读取失败") }
        val filtered = current.providers.filterNot { it.id == cmd.id }
        if (filtered.size == current.providers.size) {
            return CommandResult.Err("PROVIDER_NOT_FOUND", "provider 不存在：${cmd.id}")
        }
        return try {
            val rev = providers.save(filtered, cmd.expectedRevision)
            CommandResult.Ok(buildJsonObject { put("revision", rev) })
        } catch (e: dev.myco.core.settings.SettingsConflictException) {
            CommandResult.Err("SETTINGS_CONFLICT", e.message ?: "revision 冲突")
        } catch (e: Exception) {
            CommandResult.Err("PROVIDER_CONFIG", e.message ?: "保存失败")
        }
    }

    private suspend fun chatSend(cmd: Command.ChatSend): CommandResult = runChat(cmd)

    private fun chatStop(cmd: Command.ChatStop): CommandResult {
        val collector = activeTurns.remove(cmd.sessionId)
            ?: return CommandResult.Err("NOT_RUNNING", "该会话没有进行中的回合")
        collector.cancel()
        return CommandResult.Ok(buildJsonObject { put("stopped", true) })
    }

    private suspend fun runChat(cmd: Command.ChatSend): CommandResult {
        val provider = providers.firstEnabled()
            ?: return CommandResult.Err("PROVIDER_MISSING", "还没有配置 provider，请先在设置里添加")
        val model = provider.model("chat")
            ?: return CommandResult.Err("MODEL_MISSING", "provider ${provider.id} 没有 chat 角色模型")

        // 工具 schema 进每轮请求前缀：超预算直接拒绝，不偷偷裁剪（模型看到的必须可复现）
        val toolSpecs = tools.list().map { ToolSpec(it.name, it.description, it.parameters) }
        val schemaChars = tools.schemaChars()
        if (schemaChars > TOOL_BUDGET_CHARS) {
            return CommandResult.Err("TOOL_BUDGET", "工具 schema 超预算：$schemaChars > $TOOL_BUDGET_CHARS 字符")
        }

        val history = runCatching { sessions.read(cmd.sessionId) }
            .getOrElse { return CommandResult.Err("SESSION_NOT_FOUND", "会话不存在：${cmd.sessionId}") }

        val turn = history.count { it is SessionEvent.TurnStart }.toLong() + 1
        sessions.append(cmd.sessionId, SessionEvent.TurnStart(data = SessionEvent.TurnStart.Data(turn)))
        sessions.append(
            cmd.sessionId,
            SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data(cmd.text, source = "human"))
        )

        val client = clientFactory(provider)
        val full = StringBuilder()
        var failed: ChatChunk.Failed? = null
        var steps = 0
        var totalCost = 0.0
        var finished = false
        // 当前这一步的文本（取消时只落这一段，避免和已落盘的中途消息重复）
        val currentStep = StringBuilder()
        var messages = buildMessages(history) + ChatMessage("user", cmd.text)

        // 把"流式 + 工具循环"单独放一个 job：停止 = 取消它；runChat 自己不被取消，能正常收尾
        val collector = scope.async {
            while (steps < MAX_TOOL_STEPS) {
                steps++
                currentStep.clear()
                val toolRequests = mutableListOf<ChatChunk.ToolRequest>()
                client.stream(
                    ChatRequest(
                        model = model.id,
                        messages = messages,
                        maxTokens = model.maxTokens,
                        tools = toolSpecs,
                    )
                ).collect { chunk ->
                    when (chunk) {
                        is ChatChunk.Delta -> {
                            currentStep.append(chunk.text)
                            full.append(chunk.text)
                            _events.tryEmit(BridgeEvent.AssistantDelta(cmd.sessionId, turn, chunk.text))
                        }
                        is ChatChunk.ToolRequest -> toolRequests += chunk
                        is ChatChunk.Done -> chunk.usage?.costUsd?.let { totalCost += it }
                        is ChatChunk.Failed -> failed = chunk
                    }
                }
                if (failed != null) return@async

                val stepText = currentStep.toString()
                if (toolRequests.isEmpty()) {
                    if (stepText.isNotEmpty()) {
                        sessions.append(
                            cmd.sessionId,
                            SessionEvent.AssistantMessage(
                                data = SessionEvent.AssistantMessage.Data(stepText, model.id, null)
                            )
                        )
                    }
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.TurnEnd(data = SessionEvent.TurnEnd.Data(turn, steps = steps, costUsd = totalCost))
                    )
                    _events.tryEmit(BridgeEvent.AssistantDone(cmd.sessionId, turn, full.toString()))
                    finished = true
                    return@async
                }

                // 这一轮是工具调用：先落 assistant 文本（若有），再逐个执行
                if (stepText.isNotEmpty()) {
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.AssistantMessage(
                            data = SessionEvent.AssistantMessage.Data(stepText, model.id, null)
                        )
                    )
                }
                val calls = mutableListOf<dev.myco.core.llm.ToolCall>()
                val resultMessages = mutableListOf<ChatMessage>()
                for (tr in toolRequests) {
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.ToolCall(data = SessionEvent.ToolCall.Data(tr.name, tr.arguments))
                    )
                    _events.tryEmit(BridgeEvent.ToolStarted(cmd.sessionId, turn, tr.name))
                    val result = tools.execute(tr.name, tr.arguments)
                    val ok = result.isSuccess
                    val out = result.getOrElse { it.message ?: "工具执行失败" }
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.ToolResult(
                            data = SessionEvent.ToolResult.Data(tr.name, ok, out, reason = if (ok) null else out)
                        )
                    )
                    _events.tryEmit(BridgeEvent.ToolFinished(cmd.sessionId, turn, tr.name, ok, out))
                    calls += dev.myco.core.llm.ToolCall(tr.id, tr.name, tr.arguments.toString())
                    resultMessages += ChatMessage("tool", out, toolCallId = tr.id, toolName = tr.name)
                }
                messages = messages + ChatMessage("assistant", stepText, toolCalls = calls) + resultMessages
            }
        }
        activeTurns[cmd.sessionId] = collector
        try {
            collector.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (!collector.isCancelled) throw e
            // 用户点"停止"：把当前步已收到的部分文本按"已停止"落盘，turn 正常收尾
            val partial = currentStep.toString()
            if (partial.isNotEmpty()) {
                sessions.append(
                    cmd.sessionId,
                    SessionEvent.AssistantMessage(
                        data = SessionEvent.AssistantMessage.Data(partial, model.id, null)
                    )
                )
            }
            sessions.append(
                cmd.sessionId,
                SessionEvent.TurnEnd(data = SessionEvent.TurnEnd.Data(turn, steps = 0, null))
            )
            _events.tryEmit(BridgeEvent.AssistantDone(cmd.sessionId, turn, full.toString(), stopped = true))
            return CommandResult.Ok(buildJsonObject {
                put("turnId", turn); put("text", full.toString()); put("stopped", true)
            })
        } finally {
            activeTurns.remove(cmd.sessionId)
        }

        failed?.let {
            _events.tryEmit(BridgeEvent.Failed("LLM_FAILED", it.message, it.retriable))
            return CommandResult.Err("LLM_FAILED", it.message, it.retriable)
        }
        if (!finished) {
            sessions.append(
                cmd.sessionId,
                SessionEvent.TurnEnd(data = SessionEvent.TurnEnd.Data(turn, steps = steps, costUsd = totalCost))
            )
            return CommandResult.Err("MAX_STEPS", "工具调用超过 $MAX_TOOL_STEPS 步，已停止")
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
            val snap = providers.load()
            QueryResult.Ok(buildJsonObject {
                put("revision", snap.revision)
                put("providers", buildJsonArray { snap.providers.forEach { add(providerJson(it)) } })
            })
        }
        Query.ProviderExport -> {
            val snap = providers.load()
            val listJson = dev.myco.core.spec.MycoJson.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(ProviderProfile.serializer()),
                snap.providers,
            )
            QueryResult.Ok(buildJsonObject {
                put("schemaVersion", 1)
                put("revision", snap.revision)
                put("providers", listJson)
            })
        }
        Query.DoctorRun -> QueryResult.Ok(buildJsonArray {
            doctor.run().forEach { c ->
                add(buildJsonObject { put("name", c.name); put("status", c.status.name); put("detail", c.detail) })
            }
        })
    }

    private fun providerJson(p: ProviderProfile): JsonElement = buildJsonObject {
        put("id", p.id)
        put("kind", p.kind)
        put("displayName", p.displayName ?: "")
        put("baseUrl", p.baseUrl ?: "")
        put("enabled", p.enabled)
        put("auth", when (val a = p.auth) {
            is dev.myco.core.spec.AuthProfile.ApiKey -> buildJsonObject {
                put("type", "api-key"); put("ref", a.ref)
            }
            dev.myco.core.spec.AuthProfile.None -> buildJsonObject { put("type", "none") }
        })
        put("models", buildJsonArray {
            p.models.forEach { m ->
                add(buildJsonObject {
                    put("id", m.id)
                    put("name", m.name ?: "")
                    put("contextWindow", m.contextWindow)
                    put("maxTokens", m.maxTokens)
                    put("input", buildJsonArray { m.input.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                })
            }
        })
        put("credential", when (val a = p.auth) {
            is dev.myco.core.spec.AuthProfile.ApiKey -> {
                val status = credentials.describe(a.ref)
                buildJsonObject {
                    put("configured", status.configured)
                    put("source", status.source ?: "")
                    put("writable", status.writable)
                }
            }
            dev.myco.core.spec.AuthProfile.None -> buildJsonObject { put("configured", true); put("source", "none") }
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
        // 历史回放需要 payload；至少把消息文本带出去（GUI 靠它渲染历史）
        when (e) {
            is SessionEvent.SessionStart -> put("data", buildJsonObject {
                put("id", e.data.id); put("title", e.data.title); put("app", e.data.app); put("formatVersion", e.data.formatVersion)
            })
            is SessionEvent.TurnStart -> put("data", buildJsonObject { put("turn", e.data.turn) })
            is SessionEvent.UserMessage -> put("data", buildJsonObject { put("text", e.data.text) })
            is SessionEvent.AssistantMessage -> put("data", buildJsonObject { put("text", e.data.text) })
            is SessionEvent.ToolCall -> put("data", buildJsonObject { put("name", e.data.name); put("args", e.data.args) })
            is SessionEvent.ToolResult -> put("data", buildJsonObject {
                put("name", e.data.name); put("ok", e.data.ok); put("text", e.data.text); put("reason", e.data.reason ?: "")
            })
            is SessionEvent.SystemInject -> put("data", buildJsonObject { put("text", e.data.text) })
            is SessionEvent.TurnEnd -> put("data", buildJsonObject { put("turn", e.data.turn); put("steps", e.data.steps) })
        }
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

/** 一轮里最多允许多少次"模型→工具→模型"往返；超了明确失败，不无限烧钱。 */
const val MAX_TOOL_STEPS = 4

/** 所有工具 schema 进每请求的固定前缀预算（字符）。 */
const val TOOL_BUDGET_CHARS = 24_000
