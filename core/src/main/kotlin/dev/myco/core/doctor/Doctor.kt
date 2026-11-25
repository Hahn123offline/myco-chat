package dev.myco.core.doctor

import dev.myco.core.MycoHome
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ProviderStore
import dev.myco.core.session.SessionStore
import dev.myco.core.spec.AuthProfile
import dev.myco.core.tools.ToolRegistry
import java.nio.file.Files

enum class CheckStatus { OK, WARN, ERROR }

data class Check(val name: String, val status: CheckStatus, val detail: String)

/**
 * 自检：每个失败都必须有 reason（前代"静默失败"的教训）。
 * `myco doctor` 与 GUI 的 doctor.run 同源。
 */
class Doctor(
    private val home: MycoHome,
    private val sessions: SessionStore,
    private val providers: ProviderStore,
    private val credentials: CredentialResolver,
    private val tools: ToolRegistry,
) {
    fun run(): List<Check> {
        val checks = mutableListOf<Check>()

        checks += runCatching {
            Files.createDirectories(home.store)
            val probe = home.store.resolve(".write-probe")
            dev.myco.core.io.TextFiles.write(probe, "ok")
            Files.deleteIfExists(probe)
            Check("store", CheckStatus.OK, "可写：${home.root}")
        }.getOrElse { Check("store", CheckStatus.ERROR, "不可写：${it.message}") }

        checks += runCatching {
            val list = sessions.list()
            Check("sessions", CheckStatus.OK, "${list.size} 个（formatVersion 1）")
        }.getOrElse { Check("sessions", CheckStatus.ERROR, "读取失败：${it.message}") }

        checks += runCatching {
            val list = providers.load().providers
            val enabled = list.count { it.enabled }
            if (list.isEmpty()) Check("providers", CheckStatus.WARN, "0 个已配置（本地/DeepSeek 模板可一键添加）")
            else Check("providers", CheckStatus.OK, "${list.size} 个（启用 $enabled）")
        }.getOrElse { Check("providers", CheckStatus.ERROR, "配置非法：${it.message}") }

        checks += runCatching {
            val list = providers.load().providers.filter { it.enabled }
            val missing = list.mapNotNull { p ->
                when (val a = p.auth) {
                    is AuthProfile.ApiKey -> if (credentials.describe(a.ref).configured) null else "${p.id}(${a.ref})"
                    AuthProfile.None -> null
                }
            }
            if (missing.isEmpty()) Check("credentials", CheckStatus.OK, "所有引用可解析（不回读值）")
            else Check("credentials", CheckStatus.WARN, "缺凭据：${missing.joinToString()}")
        }.getOrElse { Check("credentials", CheckStatus.ERROR, "解析失败：${it.message}") }

        checks += runCatching {
            val snap = providers.load()
            Check("settings", CheckStatus.OK, "providers.json revision=${snap.revision}")
        }.getOrElse { Check("settings", CheckStatus.ERROR, "读取失败：${it.message}") }

        checks += runCatching {
            val enabled = providers.load().providers.filter { it.enabled }
            val missingChat = enabled.filter { it.model("chat") == null }
            when {
                enabled.isEmpty() -> Check("models", CheckStatus.WARN, "没有启用的 provider")
                missingChat.isNotEmpty() -> Check(
                    "models", CheckStatus.ERROR,
                    "provider 没有可解析的 chat 角色：${missingChat.joinToString { it.id }}"
                )
                else -> Check("models", CheckStatus.OK, "${enabled.size} 个 provider 的 chat 角色可解析")
            }
        }.getOrElse { Check("models", CheckStatus.ERROR, "读取失败：${it.message}") }

        checks += runCatching {
            val n = tools.list().size
            val chars = tools.schemaChars()
            val status = if (chars <= BUDGET_CHARS) CheckStatus.OK else CheckStatus.ERROR
            Check("tools", status, "$n 个 / $chars 字符（上限 $BUDGET_CHARS）")
        }.getOrElse { Check("tools", CheckStatus.ERROR, "注册表异常：${it.message}") }

        checks += Check("bridge", CheckStatus.OK, "in-process 已就绪；http-sse 需运行 `myco serve`（桌面/远程）")
        return checks
    }

    companion object {
        const val BUDGET_CHARS = 24_000
    }
}

