package dev.myco.core.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * GUI ↔ 核心的唯一接口（见 specs/bridge.contract.md）。
 * S0 只有 in-process 实现；http-sse 在 I0 T5 加。
 */
sealed class Command {
    data class ChatSend(val sessionId: String, val text: String) : Command()
    data class SessionCreate(
        val app: String = "chat",
        val preset: String = "chat-default",
        val title: String = "新会话",
    ) : Command()
}

sealed class Query {
    data class SessionList(val app: String? = null) : Query()
    data class SessionEvents(val sessionId: String, val fromSeq: Long = 0) : Query()
    data object Providers : Query()
    data object DoctorRun : Query()
}

sealed class BridgeEvent {
    data class AssistantDelta(val sessionId: String, val turnId: Long, val text: String) : BridgeEvent()
    data class AssistantDone(val sessionId: String, val turnId: Long, val text: String) : BridgeEvent()
    data class Failed(val code: String, val message: String, val retriable: Boolean) : BridgeEvent()
    data class SessionUpdated(val sessionId: String) : BridgeEvent()
}

sealed class CommandResult {
    data class Ok(val data: JsonElement = JsonNull) : CommandResult()
    data class Err(val code: String, val message: String, val retriable: Boolean = false) : CommandResult()
}

sealed class QueryResult {
    data class Ok(val data: JsonElement) : QueryResult()
    data class Err(val code: String, val message: String) : QueryResult()
}

interface Bridge {
    suspend fun send(cmd: Command): CommandResult
    suspend fun query(q: Query): QueryResult
    val events: Flow<BridgeEvent>
}
