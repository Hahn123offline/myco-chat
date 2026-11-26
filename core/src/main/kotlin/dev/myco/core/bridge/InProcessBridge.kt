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
import kotlinx.coroutines.sync.withLock
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
    private val attachments: dev.myco.core.attachment.AttachmentStore,
    private val clientFactory: (ProviderProfile) -> ChatClient,
) : Bridge {

    private val _events = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 1024)
    override val events: Flow<BridgeEvent> = _events.asSharedFlow()

    // 运行期事件：每会话单调 seq + 环形缓冲（断线/切会话可重放），不落盘（持久历史走 SessionEvents）
    private val eventSeq = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    private val eventBuffers = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<BridgeEvent>>()
    private val completedRequests = java.util.concurrent.ConcurrentHashMap<String, CommandResult>()

    private suspend fun emit(sessionId: String?, assign: (Long) -> BridgeEvent) {
        val key = sessionId ?: ""
        val seq = eventSeq.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicLong(0) }.incrementAndGet()
        val event = assign(seq)
        val buffer = eventBuffers.computeIfAbsent(key) { ArrayDeque() }
        synchronized(buffer) {
            buffer.addLast(event)
            while (buffer.size > EVENT_BUFFER) buffer.removeFirst()
        }
        _events.emit(event)
    }

    private val doctor = Doctor(home, sessions, providers, credentials, tools)

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )
    private val activeTurns = java.util.concurrent.ConcurrentHashMap<String, ActiveTurn>()

    override suspend fun send(cmd: Command): CommandResult = when (cmd) {
        is Command.SessionCreate -> {
            val meta = sessions.create(cmd.app, cmd.preset, cmd.title)
            emit(meta.id) { seq -> BridgeEvent.SessionUpdated(meta.id, seq) }
            CommandResult.Ok(buildJsonObject { put("sessionId", meta.id) })
        }
        is Command.AttachmentSave -> {
            runCatching {
                val bytes = java.util.Base64.getDecoder().decode(cmd.base64)
                attachments.save(cmd.sessionId, cmd.name, cmd.mime, bytes)
            }.fold(
                onSuccess = { ref ->
                    CommandResult.Ok(buildJsonObject {
                        put("id", ref.id); put("name", ref.name); put("mime", ref.mime)
                        put("size", ref.size); put("kind", ref.kind)
                    })
                },
                onFailure = { CommandResult.Err("ATTACHMENT_SAVE", it.message ?: "保存附件失败") },
            )
        }
        is Command.ChatSend -> chatSend(cmd)
        is Command.TranscribeAudio -> {
            val provider = providers.firstEnabled()
                ?: return CommandResult.Err("PROVIDER_MISSING", "还没有配置 provider")
            val client = dev.myco.core.capability.DeepSeekToolsClient.fromProvider(provider, credentials)
                ?: return CommandResult.Err("ASR_UNAVAILABLE", "当前 provider 不支持 DeepSeek ASR")
            runCatching {
                val bytes = java.util.Base64.getDecoder().decode(cmd.base64)
                client.asr(bytes, cmd.name)
            }.fold(
                onSuccess = { r ->
                    CommandResult.Ok(buildJsonObject {
                        put("text", r.text)
                        r.durationMs?.let { put("durationMs", it) }
                    })
                },
                onFailure = { CommandResult.Err("ASR_FAILED", it.message ?: "语音转写失败") },
            )
        }
        is Command.ChatRegenerate -> {
            val key = cmd.requestId?.let { "${cmd.sessionId}:regen:$it" }
            if (key != null) completedRequests[key]?.let { return it }
            val result = regenerate(cmd)
            if (key != null) {
                if (completedRequests.size > 200) completedRequests.clear()
                completedRequests[key] = result
            }
            result
        }
        is Command.ChatEditResend -> {
            val key = cmd.requestId?.let { "${cmd.sessionId}:edit:$it" }
            if (key != null) completedRequests[key]?.let { return it }
            val result = editResend(cmd)
            if (key != null) {
                if (completedRequests.size > 200) completedRequests.clear()
                completedRequests[key] = result
            }
            result
        }
        is Command.ChatStop -> chatStop(cmd)
        is Command.SessionSetModel -> runCatching {
            sessions.setModelSelection(cmd.sessionId, cmd.providerId, cmd.modelId, cmd.reasoningLevel)
        }.fold(
            onSuccess = {
                emit(cmd.sessionId) { seq -> BridgeEvent.SessionUpdated(cmd.sessionId, seq) }
                CommandResult.Ok(buildJsonObject {
                    put("providerId", cmd.providerId); put("modelId", cmd.modelId)
                    put("reasoningLevel", cmd.reasoningLevel)
                })
            },
            onFailure = { CommandResult.Err("SESSION_MODEL", it.message ?: "保存模型选择失败") },
        )
        is Command.SessionRename -> runCatching { sessions.rename(cmd.sessionId, cmd.title) }            .fold(
                onSuccess = { meta ->
                    emit(meta.id) { seq -> BridgeEvent.SessionUpdated(meta.id, seq) }
                    CommandResult.Ok(buildJsonObject { put("sessionId", meta.id); put("title", meta.title) })
                },
                onFailure = { CommandResult.Err("SESSION_RENAME", it.message ?: "改名失败") },
            )
        is Command.SessionDelete -> runCatching { sessions.delete(cmd.sessionId) }
            .fold(
                onSuccess = {
                    emit(cmd.sessionId) { seq -> BridgeEvent.SessionUpdated(cmd.sessionId, seq) }
                    CommandResult.Ok(buildJsonObject { put("deleted", true) })
                },
                onFailure = { CommandResult.Err("SESSION_DELETE", it.message ?: "删除失败") },
            )
        is Command.SessionRestore -> runCatching { sessions.restore(cmd.sessionId) }
            .fold(
                onSuccess = {
                    emit(cmd.sessionId) { seq -> BridgeEvent.SessionUpdated(cmd.sessionId, seq) }
                    CommandResult.Ok(buildJsonObject { put("restored", true) })
                },
                onFailure = { CommandResult.Err("SESSION_RESTORE", it.message ?: "恢复失败") },
            )
        is Command.SessionPin -> runCatching { sessions.setPinned(cmd.sessionId, cmd.pinned) }
            .fold(
                onSuccess = {
                    emit(cmd.sessionId) { seq -> BridgeEvent.SessionUpdated(cmd.sessionId, seq) }
                    CommandResult.Ok(buildJsonObject { put("pinned", cmd.pinned) })
                },
                onFailure = { CommandResult.Err("SESSION_PIN", it.message ?: "置顶失败") },
            )
        is Command.SessionArchive -> runCatching { sessions.archive(cmd.sessionId, cmd.archived) }
            .fold(
                onSuccess = {
                    emit(cmd.sessionId) { seq -> BridgeEvent.SessionUpdated(cmd.sessionId, seq) }
                    CommandResult.Ok(buildJsonObject { put("archived", cmd.archived) })
                },
                onFailure = { CommandResult.Err("SESSION_ARCHIVE", it.message ?: "归档失败") },
            )
        is Command.SessionDeletePermanent -> runCatching { sessions.deletePermanently(cmd.sessionId) }
            .fold(
                onSuccess = {
                    emit(cmd.sessionId) { seq -> BridgeEvent.SessionUpdated(cmd.sessionId, seq) }
                    CommandResult.Ok(buildJsonObject { put("deleted", true); put("permanent", true) })
                },
                onFailure = { CommandResult.Err("SESSION_DELETE", it.message ?: "永久删除失败") },
            )
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

    private suspend fun chatSend(cmd: Command.ChatSend): CommandResult {
        val key = cmd.requestId?.let { "${cmd.sessionId}:$it" }
        if (key != null) completedRequests[key]?.let { return it }
        val result = runChat(cmd)
        if (key != null) {
            if (completedRequests.size > 200) completedRequests.clear()
            completedRequests[key] = result
        }
        return result
    }

    private suspend fun chatStop(cmd: Command.ChatStop): CommandResult {
        val active = activeTurns.remove(cmd.sessionId)
            ?: return CommandResult.Err("NOT_RUNNING", "该会话没有进行中的回合")
        if (cmd.turnId != null && cmd.turnId != active.turnId) {
            activeTurns[cmd.sessionId] = active // 不误停别的轮次
            return CommandResult.Err("TURN_MISMATCH", "当前进行中的是 turn ${active.turnId}，不是 ${cmd.turnId}")
        }
        active.job.cancel()
        return CommandResult.Ok(buildJsonObject { put("stopped", true); put("turnId", active.turnId) })
    }

    private data class RegenCtx(val nodeId: String, val version: Int, val cutSeq: Long)
    private data class EditCtx(val revisesMessageId: String, val cutSeq: Long)
    private data class ActiveTurn(val job: kotlinx.coroutines.Job, val turnId: Long)
    private val sessionLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
        val mutex = sessionLocks.computeIfAbsent(sessionId) { kotlinx.coroutines.sync.Mutex() }
        return mutex.withLock { block() }
    }

    private suspend fun runChat(cmd: Command.ChatSend): CommandResult =
        withSessionLock(cmd.sessionId) { runChatInternal(cmd, regen = null) }

    /** 同节点重新生成：上下文截到原 user 消息之前，新 assistant 消息 version+1，不重复写 user 消息。 */
    private suspend fun regenerate(cmd: Command.ChatRegenerate): CommandResult = withSessionLock(cmd.sessionId) {
        val history = runCatching { sessions.read(cmd.sessionId) }
            .getOrElse { return@withSessionLock CommandResult.Err("SESSION_NOT_FOUND", "会话不存在：${cmd.sessionId}") }
        val target = history.filterIsInstance<SessionEvent.AssistantMessage>()
            .firstOrNull { it.data.messageId == cmd.messageId }
            ?: return@withSessionLock CommandResult.Err("MESSAGE_NOT_FOUND", "找不到要重新生成的消息：${cmd.messageId}")
        val nodeId = target.data.nodeId
            ?: return@withSessionLock CommandResult.Err("MESSAGE_NOT_VERSIONED", "该消息没有版本链节点，无法同节点重生成")
        val turn = target.data.turn
            ?: return@withSessionLock CommandResult.Err("MESSAGE_NOT_VERSIONED", "该消息缺少 turn 信息")
        val user = history.filterIsInstance<SessionEvent.UserMessage>()
            .lastOrNull { it.data.turn == turn && it.seq < target.seq }
            ?: return@withSessionLock CommandResult.Err("MESSAGE_NOT_FOUND", "找不到对应的用户消息")
        val version = (history.filterIsInstance<SessionEvent.AssistantMessage>()
            .filter { it.data.nodeId == nodeId }.maxOfOrNull { it.data.version } ?: target.data.version) + 1
        val requestId = cmd.requestId?.let { "regen:$it" }
        runChatInternal(
            Command.ChatSend(
                sessionId = cmd.sessionId,
                text = user.data.text,
                attachments = user.data.attachments,
                requestId = requestId,
            ),
            RegenCtx(nodeId, version, user.seq),
        )
    }

    private suspend fun editResend(cmd: Command.ChatEditResend): CommandResult = withSessionLock(cmd.sessionId) {
        val history = runCatching { sessions.read(cmd.sessionId) }
            .getOrElse { return@withSessionLock CommandResult.Err("SESSION_NOT_FOUND", "会话不存在：${cmd.sessionId}") }
        val target = history.filterIsInstance<SessionEvent.UserMessage>()
            .firstOrNull { it.data.messageId == cmd.messageId }
            ?: return@withSessionLock CommandResult.Err("MESSAGE_NOT_FOUND", "找不到要编辑的消息：${cmd.messageId}")
        if (cmd.text.isBlank()) return@withSessionLock CommandResult.Err("EMPTY_TEXT", "消息不能为空")
        runChatInternal(
            Command.ChatSend(
                sessionId = cmd.sessionId,
                text = cmd.text,
                attachments = target.data.attachments,
                requestId = cmd.requestId?.let { "edit:$it" },
            ),
            regen = null,
            edit = EditCtx(revisesMessageId = cmd.messageId, cutSeq = target.seq),
        )
    }

    private suspend fun runChatInternal(cmd: Command.ChatSend, regen: RegenCtx?, edit: EditCtx? = null): CommandResult {
        // 会话级模型选择：请求显式 > 会话保存 > 第一个可用 provider 的 chat 角色
        val stored = runCatching { sessions.modelSelection(cmd.sessionId) }.getOrNull()
        val providerId = cmd.providerId ?: stored?.providerId
        val provider = if (providerId != null) {
            providers.load().providers.firstOrNull { it.id == providerId && it.enabled }
                ?: return CommandResult.Err("PROVIDER_MISSING", "provider 不存在或已停用：$providerId")
        } else {
            providers.firstEnabled()
                ?: return CommandResult.Err("PROVIDER_MISSING", "还没有配置 provider，请先在设置里添加")
        }
        val modelId = cmd.modelId ?: stored?.modelId
        val model = if (modelId != null) {
            provider.models.firstOrNull { it.id == modelId }
                ?: return CommandResult.Err("MODEL_MISSING", "provider ${provider.id} 没有模型：$modelId")
        } else {
            provider.model("chat")
                ?: return CommandResult.Err("MODEL_MISSING", "provider ${provider.id} 没有 chat 角色模型")
        }
        // 推理等级 → 模型自己声明的 API 参数值（efforts[level]，null 表示该档关闭思考）
        val explicitLevel = cmd.reasoningLevel
        val storedLevel = stored?.reasoningLevel?.takeIf { level ->
            model.reasoning?.efforts?.containsKey(level) == true
        }
        val level = explicitLevel ?: storedLevel ?: model.reasoning?.default
        val apiValue = when {
            level == null -> null
            model.reasoning?.efforts?.containsKey(level) != true ->
                return CommandResult.Err("REASONING_UNSUPPORTED", "模型 ${model.id} 不支持推理等级：$level")
            else -> model.reasoning.efforts[level]
        }
        val thinking = when {
            level == null -> cmd.thinking
            apiValue == null -> false
            else -> true
        }
        val reasoningEffort = apiValue

        // 工具 schema 进每轮请求前缀：超预算直接拒绝，不偷偷裁剪（模型看到的必须可复现）
        val allToolSpecs = tools.list().map { ToolSpec(it.name, it.description, it.parameters) }
        val toolSpecs = if (cmd.webSearch == false) {
            allToolSpecs.filterNot { it.name == "web_search" || it.name == "read_url" }
        } else allToolSpecs
        val schemaChars = tools.schemaChars()
        if (schemaChars > TOOL_BUDGET_CHARS) {
            return CommandResult.Err("TOOL_BUDGET", "工具 schema 超预算：$schemaChars > $TOOL_BUDGET_CHARS 字符")
        }

        val history = runCatching { sessions.read(cmd.sessionId) }
            .getOrElse { return CommandResult.Err("SESSION_NOT_FOUND", "会话不存在：${cmd.sessionId}") }
        val contextHistory = when {
            regen != null -> history.filter { it.seq < regen.cutSeq }
            edit != null -> history.filter { it.seq < edit.cutSeq }
            else -> history
        }

        // 附件在本轮开始前一次性读入（失败则整轮拒绝，不写半截 turn）
        val prepared = runCatching {
            val inline = StringBuilder(cmd.text)
            val images = mutableListOf<dev.myco.core.llm.ChatImage>()
            for (ref in cmd.attachments) {
                when (ref.kind) {
                    "image" -> {
                        val b64 = java.util.Base64.getEncoder().encodeToString(attachments.read(cmd.sessionId, ref))
                        images += dev.myco.core.llm.ChatImage(ref.mime, b64)
                    }
                    "text" -> {
                        inline.append("\n\n[附件：${ref.name}]\n")
                            .append(attachments.textContent(cmd.sessionId, ref))
                    }
                    else -> inline.append("\n\n[附件：${ref.name}（${ref.mime}，${ref.size} 字节）未解析，仅元数据]")
                }
            }
            if (inline.isBlank() && images.isNotEmpty()) inline.append("（见图片）")
            inline.toString() to images
        }.getOrElse { return CommandResult.Err("ATTACHMENT_NOT_FOUND", it.message ?: "附件不可读") }

        val turn = history.count { it is SessionEvent.TurnStart }.toLong() + 1
        val turnStartedAt = System.currentTimeMillis()
        val turnNodeId = regen?.nodeId ?: dev.myco.core.session.Ulid.newId()
        val turnVersion = regen?.version ?: 1
        val finalMessageId = dev.myco.core.session.Ulid.newId()
        val userMessageId = dev.myco.core.session.Ulid.newId()
        sessions.append(cmd.sessionId, SessionEvent.TurnStart(data = SessionEvent.TurnStart.Data(turn)))
        if (regen == null) {
            sessions.append(
                cmd.sessionId,
                SessionEvent.UserMessage(
                    data = SessionEvent.UserMessage.Data(
                        cmd.text, source = "human", attachments = cmd.attachments,
                        messageId = userMessageId, turn = turn,
                        revises = edit?.revisesMessageId,
                    )
                )
            )
        }

        val client = clientFactory(provider)
        val full = StringBuilder()
        var failed: ChatChunk.Failed? = null
        var steps = 0
        var totalCost = 0.0
        var finished = false
        var lastUsage: dev.myco.core.spec.Usage? = null
        var lastFinishReason: String? = null
        var lastReasoning: String? = null
        // 当前这一步的文本/思考（取消时只落这一段，避免和已落盘的中途消息重复）
        val currentStep = StringBuilder()
        val currentReasoning = StringBuilder()
        var messages = buildMessages(contextHistory) + ChatMessage("user", prepared.first, images = prepared.second)

        // 把"流式 + 工具循环"单独放一个 job：停止 = 取消它；runChat 自己不被取消，能正常收尾
        val collector = scope.async {
            while (steps < MAX_TOOL_STEPS) {
                steps++
                currentStep.clear()
                currentReasoning.clear()
                val toolRequests = mutableListOf<ChatChunk.ToolRequest>()
                client.stream(
                    ChatRequest(
                        model = model.id,
                        messages = messages,
                        maxTokens = model.maxTokens,
                        thinking = thinking,
                        reasoningEffort = reasoningEffort,
                        tools = toolSpecs,
                    )
                ).collect { chunk ->
                    when (chunk) {
                        is ChatChunk.Delta -> {
                            currentStep.append(chunk.text)
                            full.append(chunk.text)
                            emit(cmd.sessionId) { seq ->
                                BridgeEvent.AssistantDelta(cmd.sessionId, turn, chunk.text, finalMessageId, seq)
                            }
                        }
                        is ChatChunk.ReasoningDelta -> {
                            currentReasoning.append(chunk.text)
                            emit(cmd.sessionId) { seq ->
                                BridgeEvent.ReasoningDelta(cmd.sessionId, turn, chunk.text, seq)
                            }
                        }
                        is ChatChunk.ToolRequest -> toolRequests += chunk
                        is ChatChunk.Done -> {
                            chunk.usage?.costUsd?.let { totalCost += it }
                            lastUsage = chunk.usage
                            lastFinishReason = chunk.finishReason
                        }
                        is ChatChunk.Failed -> failed = chunk
                    }
                }
                if (failed != null) return@async
                lastReasoning = currentReasoning.toString().ifEmpty { lastReasoning }

                val stepText = currentStep.toString()
                if (toolRequests.isEmpty()) {
                    if (stepText.isNotEmpty() || !lastReasoning.isNullOrEmpty()) {
                        sessions.append(
                            cmd.sessionId,
                            SessionEvent.AssistantMessage(
                                data = SessionEvent.AssistantMessage.Data(
                                    text = stepText,
                                    model = model.id,
                                    usage = lastUsage,
                                    turn = turn,
                                    messageId = finalMessageId,
                                    nodeId = turnNodeId,
                                    version = turnVersion,
                                    finishReason = lastFinishReason ?: "stop",
                                    reasoning = lastReasoning?.takeIf { it.isNotBlank() },
                                )
                            )
                        )
                    }
                    val status = when (lastFinishReason) {
                        "length" -> "truncated"
                        "content_filter" -> "error"
                        else -> "ok"
                    }
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.TurnEnd(
                            data = SessionEvent.TurnEnd.Data(
                                turn = turn, steps = steps, costUsd = totalCost, status = status,
                                durationMs = System.currentTimeMillis() - turnStartedAt, messageId = finalMessageId,
                            )
                        )
                    )
                    emit(cmd.sessionId) { seq ->
                        BridgeEvent.AssistantDone(
                            cmd.sessionId, turn, full.toString(), stopped = false,
                            messageId = finalMessageId, finishReason = lastFinishReason, usage = lastUsage, seq = seq,
                        )
                    }
                    finished = true
                    return@async
                }

                // 这一轮是工具调用：先落 assistant 文本（若有），再逐个执行
                if (stepText.isNotEmpty() || !currentReasoning.isEmpty()) {
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.AssistantMessage(
                            data = SessionEvent.AssistantMessage.Data(
                                text = stepText,
                                model = model.id,
                                usage = lastUsage,
                                turn = turn,
                                messageId = dev.myco.core.session.Ulid.newId(),
                                version = 1,
                                finishReason = lastFinishReason ?: "tool_calls",
                                reasoning = currentReasoning.toString().takeIf { it.isNotBlank() },
                            )
                        )
                    )
                }
                val calls = mutableListOf<dev.myco.core.llm.ToolCall>()
                val resultMessages = mutableListOf<ChatMessage>()
                for (tr in toolRequests) {
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.ToolCall(
                            data = SessionEvent.ToolCall.Data(tr.name, tr.arguments, turn = turn)
                        )
                    )
                    emit(cmd.sessionId) { seq -> BridgeEvent.ToolStarted(cmd.sessionId, turn, tr.name, seq) }
                    val result = tools.execute(tr.name, tr.arguments)
                    val ok = result.isSuccess
                    val out = result.getOrElse { it.message ?: "工具执行失败" }
                    sessions.append(
                        cmd.sessionId,
                        SessionEvent.ToolResult(
                            data = SessionEvent.ToolResult.Data(
                                tr.name, ok, out, reason = if (ok) null else out, turn = turn,
                            )
                        )
                    )
                    emit(cmd.sessionId) { seq -> BridgeEvent.ToolFinished(cmd.sessionId, turn, tr.name, ok, out, seq) }
                    calls += dev.myco.core.llm.ToolCall(tr.id, tr.name, tr.arguments.toString())
                    resultMessages += ChatMessage("tool", out, toolCallId = tr.id, toolName = tr.name)
                }
                messages = messages + ChatMessage("assistant", stepText, toolCalls = calls) + resultMessages
            }
        }
        activeTurns[cmd.sessionId] = ActiveTurn(collector, turn)
        try {
            collector.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (!collector.isCancelled) throw e
            // 用户点"停止"：把当前步已收到的部分文本按"已停止"落盘，turn 正常收尾
            val partial = currentStep.toString()
            if (partial.isNotEmpty() || !currentReasoning.isEmpty()) {
                sessions.append(
                    cmd.sessionId,
                    SessionEvent.AssistantMessage(
                        data = SessionEvent.AssistantMessage.Data(
                            text = partial,
                            model = model.id,
                            turn = turn,
                            messageId = finalMessageId,
                            nodeId = turnNodeId,
                            version = 1,
                            finishReason = "stopped",
                            reasoning = currentReasoning.toString().takeIf { it.isNotBlank() },
                        )
                    )
                )
            }
            sessions.append(
                cmd.sessionId,
                SessionEvent.TurnEnd(
                    data = SessionEvent.TurnEnd.Data(
                        turn = turn, steps = 0, costUsd = totalCost, status = "stopped",
                        durationMs = System.currentTimeMillis() - turnStartedAt, messageId = finalMessageId,
                    )
                )
            )
            emit(cmd.sessionId) { seq ->
                BridgeEvent.AssistantDone(
                    cmd.sessionId, turn, full.toString(), stopped = true,
                    messageId = finalMessageId, finishReason = "stopped", usage = lastUsage, seq = seq,
                )
            }
            return CommandResult.Ok(buildJsonObject {
                put("turnId", turn); put("text", full.toString()); put("stopped", true)
                put("messageId", finalMessageId)
            })
        } finally {
            activeTurns.remove(cmd.sessionId)
        }

        failed?.let {
            sessions.append(
                cmd.sessionId,
                SessionEvent.TurnEnd(
                    data = SessionEvent.TurnEnd.Data(
                        turn = turn, steps = steps, costUsd = totalCost, status = "error",
                        durationMs = System.currentTimeMillis() - turnStartedAt, messageId = finalMessageId,
                    )
                )
            )
            emit(cmd.sessionId) { seq -> BridgeEvent.Failed("LLM_FAILED", it.message, it.retriable, cmd.sessionId, seq) }
            return CommandResult.Err("LLM_FAILED", it.message, it.retriable)
        }
        if (!finished) {
            sessions.append(
                cmd.sessionId,
                SessionEvent.TurnEnd(
                    data = SessionEvent.TurnEnd.Data(
                        turn = turn, steps = steps, costUsd = totalCost, status = "error",
                        durationMs = System.currentTimeMillis() - turnStartedAt, messageId = finalMessageId,
                    )
                )
            )
            return CommandResult.Err("MAX_STEPS", "工具调用超过 $MAX_TOOL_STEPS 步，已停止")
        }
        return CommandResult.Ok(buildJsonObject {
            put("turnId", turn); put("text", full.toString()); put("messageId", finalMessageId)
            put("version", turnVersion); put("nodeId", turnNodeId)
        })
    }

    /** 历史 → 模型上下文：同名 nodeId 只保留最新 version；被编辑替代的旧 user 消息不进上下文。 */
    private fun buildMessages(history: List<SessionEvent>): List<ChatMessage> {
        val latestVersion = history.filterIsInstance<SessionEvent.AssistantMessage>()
            .mapNotNull { e -> e.data.nodeId?.let { it to e.data.version } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, versions) -> versions.max() }
        val revisedUserIds = history.filterIsInstance<SessionEvent.UserMessage>()
            .mapNotNull { it.data.revises }.toSet()
        return history.mapNotNull { e ->
            when (e) {
                is SessionEvent.UserMessage -> {
                    val id = e.data.messageId
                    if (id != null && id in revisedUserIds) null else ChatMessage("user", e.data.text)
                }
                is SessionEvent.AssistantMessage -> {
                    val node = e.data.nodeId
                    if (node != null && e.data.version < (latestVersion[node] ?: 0)) null
                    else ChatMessage("assistant", e.data.text)
                }
                else -> null
            }
        }
    }

    override suspend fun query(q: Query): QueryResult = when (q) {
        is Query.SessionList -> {
            val list = runCatching {
                sessions.summaries(includeArchived = q.includeArchived, includeDeleted = q.includeDeleted, app = q.app)
            }.getOrElse { return QueryResult.Err("SESSION_LIST", it.message ?: "读取失败") }
            QueryResult.Ok(buildJsonArray { list.forEach { add(summaryJson(it)) } })
        }
        is Query.SessionEvents -> {
            val events = runCatching { sessions.readFrom(q.sessionId, q.fromSeq) }
                .getOrElse { return QueryResult.Err("SESSION_NOT_FOUND", it.message ?: "读取失败") }
            QueryResult.Ok(buildJsonArray { events.forEach { add(eventJson(it)) } })
        }
        is Query.SessionModel -> {
            val sel = runCatching { sessions.modelSelection(q.sessionId) }.getOrNull()
            QueryResult.Ok(buildJsonObject {
                put("providerId", sel?.providerId); put("modelId", sel?.modelId)
                put("reasoningLevel", sel?.reasoningLevel)
                val p = sel?.providerId?.let { id -> providers.load().providers.firstOrNull { it.id == id } }
                if (p != null) {
                    put("providerName", p.displayName ?: p.id)
                    put("providerAvailable", p.enabled)
                    val m = sel?.modelId?.let { mid -> p.models.firstOrNull { it.id == mid } }
                    put("modelName", m?.name ?: m?.id ?: sel?.modelId)
                    put("modelAvailable", m != null)
                }
            })
        }
        is Query.EventReplay -> {
            val buffer = eventBuffers[q.sessionId]
            if (buffer == null) {
                QueryResult.Ok(buildJsonArray { })
            } else {
                val events = synchronized(buffer) {
                    buffer.filter { it.seq > q.fromSeq }.takeLast(q.limit.coerceIn(1, EVENT_BUFFER))
                }
                QueryResult.Ok(buildJsonArray { events.forEach { add(bridgeEventJson(it)) } })
            }
        }
        is Query.SessionSearch -> {
            val hits = runCatching { sessions.search(q.query, q.limit, includeArchived = q.includeArchived) }
                .getOrElse { return QueryResult.Err("SEARCH_FAILED", it.message ?: "搜索失败") }
            QueryResult.Ok(buildJsonArray {
                hits.forEach { h ->
                    add(buildJsonObject {
                        put("id", h.session.id)
                        put("title", h.session.title)
                        put("createdAt", h.session.createdAt)
                        put("app", h.session.app)
                        put("preset", h.session.preset)
                        put("formatVersion", h.session.formatVersion)
                        put("snippet", h.snippet)
                        h.seq?.let { put("seq", it) }
                        h.role?.let { put("role", it) }
                    })
                }
            })
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

    private fun summaryJson(m: dev.myco.core.session.SessionSummary): JsonElement = buildJsonObject {
        put("id", m.id); put("title", m.title); put("createdAt", m.createdAt)
        put("app", m.app); put("preset", m.preset); put("formatVersion", m.formatVersion)
        put("lastMessageAt", m.lastMessageAt)
        m.preview?.let { put("preview", it) }
        put("messageCount", m.messageCount)
        put("pinned", m.pinned)
        m.archivedAt?.let { put("archivedAt", it) }
        m.deletedAt?.let { put("deletedAt", it) }
        put("archived", m.archivedAt != null)
        put("deleted", m.deletedAt != null)
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
            is SessionEvent.UserMessage -> put("data", buildJsonObject {
                put("text", e.data.text)
                e.data.messageId?.let { put("messageId", it) }
                e.data.turn?.let { put("turn", it) }
                if (e.data.attachments.isNotEmpty()) {
                    put("attachments", dev.myco.core.spec.MycoJson.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(dev.myco.core.spec.AttachmentRef.serializer()),
                        e.data.attachments,
                    ))
                }
            })
            is SessionEvent.AssistantMessage -> put("data", buildJsonObject {
                put("text", e.data.text)
                e.data.model?.let { put("model", it) }
                e.data.turn?.let { put("turn", it) }
                e.data.messageId?.let { put("messageId", it) }
                e.data.nodeId?.let { put("nodeId", it) }
                put("version", e.data.version)
                e.data.finishReason?.let { put("finishReason", it) }
                e.data.reasoning?.let { put("reasoning", it) }
                e.data.usage?.let { u ->
                    put("usage", dev.myco.core.spec.MycoJson.encodeToJsonElement(dev.myco.core.spec.Usage.serializer(), u))
                }
            })
            is SessionEvent.ToolCall -> put("data", buildJsonObject {
                put("name", e.data.name); put("args", e.data.args)
                e.data.turn?.let { put("turn", it) }
            })
            is SessionEvent.ToolResult -> put("data", buildJsonObject {
                put("name", e.data.name); put("ok", e.data.ok); put("text", e.data.text); put("reason", e.data.reason ?: "")
                e.data.turn?.let { put("turn", it) }
            })
            is SessionEvent.SystemInject -> put("data", buildJsonObject { put("text", e.data.text) })
            is SessionEvent.TurnEnd -> put("data", buildJsonObject {
                put("turn", e.data.turn); put("steps", e.data.steps); put("status", e.data.status)
                e.data.costUsd?.let { put("costUsd", it) }
                e.data.durationMs?.let { put("durationMs", it) }
                e.data.messageId?.let { put("messageId", it) }
            })
        }
    }

    private fun bridgeEventJson(e: BridgeEvent): JsonElement = buildJsonObject {
        put("seq", e.seq)
        put("sessionId", e.sessionId ?: "")
        when (e) {
            is BridgeEvent.AssistantDelta -> {
                put("type", "assistant.delta")
                put("turnId", e.turnId); put("text", e.text)
                e.messageId?.let { put("messageId", it) }
            }
            is BridgeEvent.ReasoningDelta -> {
                put("type", "reasoning.delta")
                put("turnId", e.turnId); put("text", e.text)
            }
            is BridgeEvent.AssistantDone -> {
                put("type", "assistant.done")
                put("turnId", e.turnId); put("text", e.text); put("stopped", e.stopped)
                e.messageId?.let { put("messageId", it) }
                e.finishReason?.let { put("finishReason", it) }
                e.usage?.let { u ->
                    put("usage", dev.myco.core.spec.MycoJson.encodeToJsonElement(dev.myco.core.spec.Usage.serializer(), u))
                }
            }
            is BridgeEvent.Failed -> {
                put("type", "failed")
                put("code", e.code); put("message", e.message); put("retriable", e.retriable)
            }
            is BridgeEvent.SessionUpdated -> put("type", "session.updated")
            is BridgeEvent.ToolStarted -> {
                put("type", "tool.started"); put("turnId", e.turnId); put("name", e.name)
            }
            is BridgeEvent.ToolFinished -> {
                put("type", "tool.finished"); put("turnId", e.turnId); put("name", e.name)
                put("ok", e.ok); put("text", e.text)
            }
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

/** 每会话保留的运行期事件条数（断线重放大致窗口）。 */
const val EVENT_BUFFER = 500


