package dev.myco.android

import android.content.Context
import dev.myco.core.MycoHome
import dev.myco.core.bridge.InProcessBridge
import dev.myco.core.llm.ChatClient
import dev.myco.core.llm.ChatClients
import dev.myco.core.llm.FakeChatClient
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ProviderStore
import dev.myco.core.session.SessionStore
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.tools.ToolRegistry
import dev.myco.core.tools.timeNowTool
import java.io.File

/**
 * S0-b：Android 侧用 in-process Bridge（不开本地服务器）。
 * 模型先用 FakeChatClient（离线可复现）；接真 provider 在 I1，读同一份 ProviderStore。
 */
object BridgeHolder {
    lateinit var bridge: InProcessBridge
        private set
    lateinit var home: MycoHome
        private set

    fun init(context: Context) {
        if (::bridge.isInitialized) return
        rebuild(context)
    }

    /** provider 增删后重建：让 clientFactory 立刻按新配置生效（内核无跨轮状态，重建安全）。 */
    fun rebuild(context: Context) {
        home = MycoHome(File(context.filesDir, "myco").toPath()).also { it.ensureLayout() }
        val sessions = SessionStore(home)
        val providers = ProviderStore(home)
        val credentials = CredentialResolver(home)
        val tools = ToolRegistry().apply { register(timeNowTool()) }
        runCatching { providers.firstEnabled() }.getOrNull()?.let { p ->
            dev.myco.core.capability.registerDeepSeekCapabilityTools(tools, p, credentials)
        }
        // 没有 provider 时用假模型保证可离线跑；有 provider 就按方言接真服务（失败会明确报错，不静默造假）
        val hasProvider = runCatching { providers.load().providers.isNotEmpty() }.getOrDefault(false)
        val factory: (ProviderProfile) -> ChatClient =
            if (hasProvider) { p -> ChatClients.createOrFailed(p, credentials) } else { { FakeChatClient() } }
        bridge = InProcessBridge(home, sessions, providers, credentials, tools, dev.myco.core.attachment.AttachmentStore(home), factory)
    }
}

