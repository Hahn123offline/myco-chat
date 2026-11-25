package dev.myco.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 工具定义：schema 进入模型请求前缀，所以字段都要有预算（summary ≤160、description ≤900）。
 * S0 只有注册表与一个演示工具；执行管线（pre/execute/post）在 I0 T2 补。
 */
class ToolDef(
    val name: String,
    val summary: String,
    val description: String,
    val parameters: JsonObject = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) },
    /** 计费说明（进 UI/日志，不进模型 schema；null = 免费/本地） */
    val costNote: String? = null,
    val execute: suspend (JsonObject) -> String,
)

class ToolRegistry {
    private val tools = LinkedHashMap<String, ToolDef>()

    fun register(def: ToolDef): ToolDef {
        require(NAME_RE.matches(def.name)) { "非法工具名: ${def.name}" }
        require(def.summary.length <= 160) { "summary 超 160 字符: ${def.name}" }
        require(def.description.length <= 900) { "description 超 900 字符: ${def.name}" }
        require(!tools.containsKey(def.name)) { "工具重名: ${def.name}" }
        tools[def.name] = def
        return def
    }

    fun list(): List<ToolDef> = tools.values.toList()
    fun names(): List<String> = tools.keys.toList()

    suspend fun execute(name: String, args: JsonObject): Result<String> {
        val def = tools[name] ?: return Result.failure(IllegalArgumentException("unknown tool: $name"))
        return runCatching { def.execute(args) }
    }

    /** 预算：所有工具 schema 的 JSON 字符数（进每请求固定前缀）。 */
    fun schemaChars(): Int = tools.values.sumOf { def ->
        val schema = buildJsonObject {
            put("name", def.name)
            put("description", def.description)
            put("parameters", def.parameters)
        }
        schema.toString().length
    }

    companion object {
        private val NAME_RE = Regex("^[a-z][a-z0-9_]*$")
    }
}

fun timeNowTool(zone: ZoneId = ZoneId.systemDefault()): ToolDef = ToolDef(
    name = "time_now",
    summary = "读取当前时间",
    description = "返回当前本地时间（ISO-8601）与 epoch 毫秒。S0 的演示工具，用于验证注册/预算/执行管线。",
    parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    },
) {
    val now = Instant.now()
    val local = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zone).format(now)
    "{\"local\":\"$local\",\"epochMs\":${now.toEpochMilli()}}"
}
