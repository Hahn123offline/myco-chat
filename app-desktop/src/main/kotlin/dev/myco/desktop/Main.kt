package dev.myco.desktop

import dev.myco.core.MycoHome
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.InProcessBridge
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import dev.myco.core.doctor.Doctor
import dev.myco.core.llm.ChatClient
import dev.myco.core.llm.FakeChatClient
import dev.myco.core.llm.OpenAiCompatibleClient
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ProviderStore
import dev.myco.core.session.SessionStore
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.tools.ToolRegistry
import dev.myco.core.tools.timeNowTool
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * S0 桌面/CLI 壳：验证 core 与 bridge 能跑通一轮（含流式）。
 * GUI 之后换成 Compose，这里只保证 headless 可用。
 */
fun main(args: Array<String>) = runBlocking {
    val home = MycoHome.resolveDefault()
    home.ensureLayout()
    val sessions = SessionStore(home)
    val providers = ProviderStore(home)
    val credentials = CredentialResolver(home)
    val tools = ToolRegistry().apply { register(timeNowTool()) }

    fun clientFor(provider: ProviderProfile): ChatClient =
        if (System.getenv("MYCO_FAKE") == "1" || provider.id == "fake") FakeChatClient()
        else OpenAiCompatibleClient(provider, credentials)

    val bridge = InProcessBridge(home, sessions, providers, credentials, tools, ::clientFor)

    when (args.firstOrNull()) {
        null, "help", "--help" -> printHelp()
        "doctor" -> doctor(home, sessions, providers, credentials, tools)
        "sessions" -> sessions.list().forEach {
            println("${it.id}  ${it.app}/${it.preset}  ${it.title}  ${java.time.Instant.ofEpochMilli(it.createdAt)}")
        }
        "providers" -> {
            val list = providers.load()
            if (list.isEmpty()) println("（未配置 provider；可用 `init-local` 写入本地模板）")
            list.forEach { p ->
                val status = when (val a = p.auth) {
                    is AuthProfile.ApiKey -> credentials.describe(a.ref).let { if (it.configured) "key:ok" else "key:missing(${a.ref})" }
                    AuthProfile.None -> "no-key"
                }
                println("${p.id}  ${p.kind}  ${p.baseUrl ?: ""}  [$status]  models=${p.models.joinToString { it.id }}")
            }
        }
        "new" -> {
            val title = args.drop(1).joinToString(" ").ifBlank { "新会话" }
            val meta = sessions.create("chat", "chat-default", title)
            println("created ${meta.id}  $title")
        }
        "chat" -> {
            val sessionId = args.getOrNull(1) ?: return@runBlocking usage("chat <sessionId> <text...>")
            val text = args.drop(2).joinToString(" ").ifBlank { return@runBlocking usage("chat <sessionId> <text...>") }
            chat(bridge, sessionId, text)
        }
        "demo" -> {
            val text = args.drop(1).joinToString(" ").ifBlank { "你好" }
            val meta = sessions.list().firstOrNull() ?: sessions.create("chat", "chat-default", "S0 demo")
            println("session=${meta.id}")
            chat(bridge, meta.id, text)
        }
        "init-local" -> {
            val baseUrl = args.getOrNull(1) ?: "http://127.0.0.1:8787/v1"
            providers.save(
                listOf(
                    ProviderProfile(
                        id = "local",
                        kind = "openai-completions",
                        displayName = "本地 OpenAI 兼容（fake-sse）",
                        baseUrl = baseUrl,
                        auth = AuthProfile.None,
                        models = listOf(ModelProfile(id = "fake-chat", contextWindow = 32768, maxTokens = 4096)),
                        roles = mapOf("chat" to "fake-chat", "fast" to "fake-chat"),
                    )
                )
            )
            println("wrote ${home.providersFile}")
            println("start fake server:  gradle :tools:fake-sse:run")
            println("then:               MYCO_FAKE=0 gradle :app-desktop:run --args=\"demo 你好\"")
        }
        else -> usage("unknown command: ${args.firstOrNull()}")
    }
}

private suspend fun chat(bridge: InProcessBridge, sessionId: String, text: String) {
    println("--- stream ---")
    val printer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch(
        start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED
    ) {
        bridge.events.collect { e ->
            when (e) {
                is BridgeEvent.AssistantDelta -> { print(e.text); System.out.flush() }
                is BridgeEvent.AssistantDone -> println("\n--- done (turn ${e.turnId}) ---")
                is BridgeEvent.Failed -> println("\n[error] ${e.code}: ${e.message} (retriable=${e.retriable})")
                is BridgeEvent.SessionUpdated -> Unit
            }
        }
    }
    val result = bridge.send(Command.ChatSend(sessionId, text))
    kotlinx.coroutines.delay(100)
    printer.cancel()
    if (result is dev.myco.core.bridge.CommandResult.Err) println("[failed] ${result.code}: ${result.message}")
}

private fun doctor(
    home: MycoHome,
    sessions: SessionStore,
    providers: ProviderStore,
    credentials: CredentialResolver,
    tools: ToolRegistry,
) {
    println("myco ${'$'}home ${home.root}")
    Doctor(home, sessions, providers, credentials, tools).run().forEach { c ->
        val prefix = when (c.status) { dev.myco.core.doctor.CheckStatus.OK -> "[ok]  "; dev.myco.core.doctor.CheckStatus.WARN -> "[warn]"; dev.myco.core.doctor.CheckStatus.ERROR -> "[err] " }
        println("$prefix ${c.name.padEnd(12)} ${c.detail}")
    }
}

private fun printHelp() {
    println(
        """
        myco S0 CLI
          doctor                 自检
          sessions               列出会话
          new [title]            新建会话
          providers              列出 provider（不打印密钥）
          chat <id> <text...>    发一条消息（流式）
          demo [text...]         用现有/新建会话跑一次 demo
          init-local [baseUrl]   写入本地 OpenAI 兼容 provider 模板（默认 fake-sse :8787）
        """.trimIndent()
    )
}

private fun usage(msg: String) {
    println("usage: $msg")
}
