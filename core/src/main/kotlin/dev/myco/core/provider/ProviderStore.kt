package dev.myco.core.provider

import dev.myco.core.MycoHome
import dev.myco.core.settings.SettingsStore
import dev.myco.core.spec.MycoJson
import dev.myco.core.spec.ProviderFile
import dev.myco.core.spec.ProviderProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ProviderConfigException(message: String) : RuntimeException(message)

data class ProvidersSnapshot(val revision: Long, val providers: List<ProviderProfile>)

/**
 * provider 档案的读写：revision 乐观锁（A6/A5）+ 严格校验。
 * 文件仍是 providers.json；新格式带 revision 信封（SettingsStore 负责）。
 */
class ProviderStore(private val home: MycoHome) {
    private val settings = SettingsStore(home.providersFile)

    fun load(): ProvidersSnapshot {
        val doc = settings.read()
        if (doc.data is JsonObject && (doc.data as JsonObject).isEmpty()) {
            return ProvidersSnapshot(doc.revision, emptyList())
        }
        val file = try {
            MycoJson.decodeFromString(ProviderFile.serializer(), doc.data.toString())
        } catch (e: Exception) {
            throw ProviderConfigException("providers.json 解析失败：${e.message}")
        }
        file.providers.forEach { validate(it) }
        return ProvidersSnapshot(doc.revision, file.providers)
    }

    /** 期望的 revision 必须与当前一致；返回新 revision。 */
    fun save(providers: List<ProviderProfile>, expectedRevision: Long): Long {
        providers.forEach { validate(it) }
        val payload = Json.parseToJsonElement(MycoJson.encodeToString(ProviderFile.serializer(), ProviderFile(providers)))
        return settings.write(payload, expectedRevision)
    }

    fun firstEnabled(): ProviderProfile? = load().providers.firstOrNull { it.enabled }

    private fun validate(p: ProviderProfile) {
        if (!ID_RE.matches(p.id)) throw ProviderConfigException("非法 provider id: ${p.id}")
        if (p.kind !in KINDS) throw ProviderConfigException("未知 kind: ${p.kind}")
        if (p.models.isEmpty()) throw ProviderConfigException("provider ${p.id} 没有 models")
        p.models.forEach {
            if (it.contextWindow <= 0L) throw ProviderConfigException("model ${it.id} contextWindow <= 0")
            if (it.maxTokens <= 0L) throw ProviderConfigException("model ${it.id} maxTokens <= 0")
        }
        when (val auth = p.auth) {
            is dev.myco.core.spec.AuthProfile.ApiKey ->
                if (!REF_RE.matches(auth.ref)) throw ProviderConfigException("非法 auth.ref: ${auth.ref}")
            dev.myco.core.spec.AuthProfile.None -> Unit
        }
    }

    companion object {
        private val ID_RE = Regex("^[a-z][a-z0-9-]{1,31}$")
        private val REF_RE = Regex("^[A-Z][A-Z0-9_]*$")
        private val KINDS = setOf("deepseek-messages", "openai-completions", "openai-responses")
    }
}
