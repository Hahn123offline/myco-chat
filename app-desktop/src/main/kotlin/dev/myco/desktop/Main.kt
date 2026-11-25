package dev.myco.desktop

import dev.myco.core.MycoHome
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.InProcessBridge
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import dev.myco.core.doctor.Doctor
import dev.myco.core.llm.ChatClient
import dev.myco.core.llm.ChatClients
import dev.myco.core.llm.FakeChatClient
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ModelDiscovery
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
import java.nio.file.Files
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject

/**
 * S0 桌面/CLI 壳：验证 core 与 bridge 能跑通一轮（含流式）。
 * GUI 之后换成 Compose，这里只保证 headless 可用。
 */
fun main(args: Array<String>): Unit = runBlocking {
    val home = MycoHome.resolveDefault()
    home.ensureLayout()
    val sessions = SessionStore(home)
    val providers = ProviderStore(home)
    val credentials = CredentialResolver(home)
    val tools = ToolRegistry().apply { register(timeNowTool()) }

    fun clientFor(provider: ProviderProfile): ChatClient =
        if (System.getenv("MYCO_FAKE") == "1" || provider.id == "fake") FakeChatClient()
        else ChatClients.createOrFailed(provider, credentials)

    val bridge = InProcessBridge(home, sessions, providers, credentials, tools, ::clientFor)

    when (args.firstOrNull()) {
        null, "help", "--help" -> printHelp()
        "doctor" -> doctor(home, sessions, providers, credentials, tools)
        "sessions" -> sessions.list().forEach {
            println("${it.id}  ${it.app}/${it.preset}  ${it.title}  ${java.time.Instant.ofEpochMilli(it.createdAt)}")
        }
        "providers" -> {
            val snap = providers.load()
            val list = snap.providers
            println("revision=${snap.revision}")
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
        "init-deepseek" -> {
            val baseUrl = args.getOrNull(1) ?: "https://api.deepseek.com/anthropic"
            val current = providers.load()
            val revision = providers.save(
                listOf(
                    ProviderProfile(
                        id = "deepseek",
                        kind = "deepseek-messages",
                        displayName = "DeepSeek 官方",
                        baseUrl = baseUrl,
                        auth = AuthProfile.ApiKey("DEEPSEEK_API_KEY"),
                        models = listOf(
                            ModelProfile(
                                id = "deepseek-flash",
                                name = "DeepSeek V4.1 Flash",
                                contextWindow = 1_000_000,
                                maxTokens = 384_000,
                            )
                        ),
                        roles = mapOf("chat" to "deepseek-flash", "fast" to "deepseek-flash"),
                        pricing = dev.myco.core.spec.Pricing(
                            currency = "CNY",
                            inputPerMillion = 1.0,
                            outputPerMillion = 4.0,
                            cachedInputPerMillion = 0.02,
                        ),
                    )
                ),
                expectedRevision = current.revision,
            )
            println("wrote DeepSeek provider (revision=$revision, key ref=DEEPSEEK_API_KEY)")
        }
        "init-local" -> {
            val baseUrl = args.getOrNull(1) ?: "http://127.0.0.1:8787/v1"
            val current = providers.load()
            val revision = providers.save(
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
                ),
                expectedRevision = current.revision,
            )
            println("wrote ${home.providersFile} (revision=$revision)")
            println("start fake server:  gradle :tools:fake-sse:run")
            println("then:               gradle :app-desktop:run --args=\"demo 你好\"")
        }
        "discover" -> {
            val pid = args.getOrNull(1) ?: return@runBlocking usage("discover <providerId>")
            val p = providers.load().providers.firstOrNull { it.id == pid }
                ?: return@runBlocking usage("provider not found: $pid")
            runCatching { ModelDiscovery.discover(p, credentials) }
                .onSuccess { models ->
                    println("discovered ${models.size} models from ${p.baseUrl}")
                    models.forEach { m ->
                        println("  ${m.id}${m.displayName?.let { "  ($it)" } ?: ""}${m.contextWindow?.let { "  ctx=$it" } ?: ""}")
                    }
                }
                .onFailure { println("[failed] ${it.message}") }
        }
        "provider-export" -> {
            val path = args.getOrNull(1) ?: home.root.resolve("providers-export.json").toString()
            val snap = providers.load()
            Files.writeString(
                java.nio.file.Path.of(path),
                """{"schemaVersion":1,"revision":${snap.revision},"providers":${dev.myco.core.spec.MycoJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(dev.myco.core.spec.ProviderProfile.serializer()), snap.providers
                )}}""",
            )
            println("exported ${snap.providers.size} providers → $path")
        }
        "provider-import" -> {
            val path = args.getOrNull(1) ?: return@runBlocking usage("provider-import <path>")
            val text = Files.readString(java.nio.file.Path.of(path))
            val root = dev.myco.core.spec.MycoJson.parseToJsonElement(text).jsonObject
            val payload = root["providers"] ?: root["data"] ?: root
            val list: List<ProviderProfile> = when (payload) {
                is kotlinx.serialization.json.JsonArray -> dev.myco.core.spec.MycoJson.decodeFromJsonElement(
                    ListSerializer(ProviderProfile.serializer()), payload
                )
                is kotlinx.serialization.json.JsonObject -> dev.myco.core.spec.MycoJson.decodeFromJsonElement(
                    dev.myco.core.spec.ProviderFile.serializer(), payload
                ).providers
                else -> return@runBlocking usage("provider-import: 不认识的 JSON 形状")
            }
            val current = providers.load()
            val rev = providers.save(list, expectedRevision = current.revision)
            println("imported ${list.size} providers (revision=$rev)")
        }
        "serve" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 8788
            serve(home, bridge, port)
        }
        else -> usage("unknown command: ${args.firstOrNull()}")
    }
    Unit
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

private fun serve(home: MycoHome, bridge: dev.myco.core.bridge.InProcessBridge, port: Int) {
    val token = UUID.randomUUID().toString()
    val server = dev.myco.bridge.http.HttpBridgeServer(bridge, token, port).start()
    val runtimeFile = home.root.resolve("runtime.json")
    Files.createDirectories(home.root)
    Files.writeString(
        runtimeFile,
        """{"url":"${server.baseUrl}","token":"$token","pid":${ProcessHandle.current().pid()},"startedAt":${System.currentTimeMillis()}}""",
    )
    System.err.println("http-sse bridge: ${server.baseUrl}")
    System.err.println("token: $token  (runtime: $runtimeFile)")
    System.err.println("try: curl -s ${server.baseUrl}/query/session.list -X POST -H \"Authorization: Bearer $token\" -d '{}'")
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { server.close() } })
    Thread.currentThread().join()
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
          discover <providerId>  显式"获取可用模型"（不自动探测）
          chat <id> <text...>    发一条消息（流式）
          demo [text...]         用现有/新建会话跑一次 demo
          serve [port]           启动 http-sse Bridge（浏览器/远程/调试；默认 8788）
          init-local [baseUrl]   写入本地 OpenAI 兼容 provider 模板（默认 fake-sse :8787）
        """.trimIndent()
    )
}

private fun usage(msg: String) {
    println("usage: $msg")
}
