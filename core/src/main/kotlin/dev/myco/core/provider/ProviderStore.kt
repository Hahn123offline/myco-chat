package dev.myco.core.provider

import dev.myco.core.MycoHome
import dev.myco.core.spec.MycoJson
import dev.myco.core.spec.ProviderFile
import dev.myco.core.spec.ProviderProfile
import java.nio.file.Files

class ProviderConfigException(message: String) : RuntimeException(message)

/**
 * provider 档案的读写（S0 用 JSON；I1 再上 YAML + revision 乐观锁）。
 * 校验原则：宁可起不来，也不要半残运行（未知/缺失字段直接报错）。
 */
class ProviderStore(private val home: MycoHome) {
    fun load(): List<ProviderProfile> {
        if (!Files.exists(home.providersFile)) return emptyList()
        val text = Files.readString(home.providersFile)
        val file = try {
            MycoJson.decodeFromString(ProviderFile.serializer(), text)
        } catch (e: Exception) {
            throw ProviderConfigException("providers.json 解析失败：${e.message}")
        }
        file.providers.forEach { validate(it) }
        return file.providers
    }

    fun save(providers: List<ProviderProfile>) {
        providers.forEach { validate(it) }
        home.ensureLayout()
        val text = MycoJson.encodeToString(ProviderFile.serializer(), ProviderFile(providers))
        val tmp = home.providersFile.resolveSibling("providers.json.tmp")
        Files.writeString(tmp, text)
        Files.move(tmp, home.providersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    fun firstEnabled(): ProviderProfile? = load().firstOrNull { it.enabled }

    private fun validate(p: ProviderProfile) {
        if (!ID_RE.matches(p.id)) throw ProviderConfigException("非法 provider id: ${p.id}")
        if (p.kind !in KINDS) throw ProviderConfigException("未知 kind: ${p.kind}")
        if (p.models.isEmpty()) throw ProviderConfigException("provider ${p.id} 没有 models")
        p.models.forEach {
            if (it.contextWindow <= 0) throw ProviderConfigException("model ${it.id} contextWindow <= 0")
            if (it.maxTokens <= 0) throw ProviderConfigException("model ${it.id} maxTokens <= 0")
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
