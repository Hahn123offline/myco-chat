package dev.myco.core.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import dev.myco.core.spec.ProviderProfile

/**
 * GUI ↔ 核心的唯一接口（见 specs/bridge.contract.md）。
 * 两种传输：in-process（手机/桌面原生）与 http-sse（浏览器/远程）。
 */
sealed class Command {
    data class ChatSend(val sessionId: String, val text: String) : Command()
    data class SessionCreate(
        val app: String = "chat",
        val preset: String = "chat-default",
        val title: String = "新会话",
    ) : Command()

    /** 新增/更新 provider；expectedRevision 不匹配返回 SETTINGS_CONFLICT。 */
    data class ProviderSave(val profile: ProviderProfile, val expectedRevision: Long) : Command()

    data class ProviderDelete(val id: String, val expectedRevision: Long) : Command()

    /** 只写不读；值不会出现在任何查询结果里。 */
    data class CredentialSet(val ref: String, val value: String) : Command()

    /** 显式"获取可用模型"：只在用户点按钮时调用，不会在保存 provider 时自动跑。 */
    data class ProviderDiscover(val profile: ProviderProfile) : Command()

    /** 取消该会话正在跑的一轮；部分文本会按"已停止"落盘，不假装完成。 */
    data class ChatStop(val sessionId: String) : Command()

    /** 导入：整份替换 provider 列表（GUI 侧文件选择后调用）；expectedRevision 冲突则拒绝。 */
    data class ProviderReplace(val profiles: List<ProviderProfile>, val expectedRevision: Long) : Command()

    /** 改名（只改展示标题，不改历史事件）。 */
    data class SessionRename(val sessionId: String, val title: String) : Command()

    /** 软删（日志保留，列表默认隐藏，可 restore）。 */
    data class SessionDelete(val sessionId: String) : Command()

    data class SessionRestore(val sessionId: String) : Command()
}

sealed class Query {
    data class SessionList(val app: String? = null) : Query()
    data class SessionEvents(val sessionId: String, val fromSeq: Long = 0) : Query()

    /** 搜索标题与会话内文本（大小写不敏感）。 */
    data class SessionSearch(val query: String, val limit: Int = 50) : Query()
    data object Providers : Query()
    data object DoctorRun : Query()

    /** 导出：完整 provider 档案（含 spec 字段），GUI 负责写文件；与 CLI 的 provider-export 同格式。 */
    data object ProviderExport : Query()
}

sealed class BridgeEvent {
    data class AssistantDelta(val sessionId: String, val turnId: Long, val text: String) : BridgeEvent()
    data class AssistantDone(
        val sessionId: String,
        val turnId: Long,
        val text: String,
        val stopped: Boolean = false,
    ) : BridgeEvent()
    data class Failed(val code: String, val message: String, val retriable: Boolean) : BridgeEvent()
    data class SessionUpdated(val sessionId: String) : BridgeEvent()

    /** 正在调用工具（GUI 可显示"正在使用 xxx"）。 */
    data class ToolStarted(val sessionId: String, val turnId: Long, val name: String) : BridgeEvent()

    /** 工具返回（ok=false 时 text 是原因）。 */
    data class ToolFinished(
        val sessionId: String,
        val turnId: Long,
        val name: String,
        val ok: Boolean,
        val text: String,
    ) : BridgeEvent()
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
