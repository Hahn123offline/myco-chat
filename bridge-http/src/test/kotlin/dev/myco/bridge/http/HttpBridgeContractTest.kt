package dev.myco.bridge.http

import dev.myco.core.MycoHome
import dev.myco.core.bridge.BridgeEvent
import dev.myco.core.bridge.Command
import dev.myco.core.bridge.CommandResult
import dev.myco.core.bridge.InProcessBridge
import dev.myco.core.bridge.Query
import dev.myco.core.bridge.QueryResult
import dev.myco.core.llm.ChatClient
import dev.myco.core.llm.FakeChatClient
import dev.myco.core.provider.CredentialResolver
import dev.myco.core.provider.ProviderStore
import dev.myco.core.session.SessionEvent
import dev.myco.core.session.SessionStore
import dev.myco.core.spec.AuthProfile
import dev.myco.core.spec.ModelProfile
import dev.myco.core.spec.ProviderProfile
import dev.myco.core.tools.ToolRegistry
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * 两种传输跑同一套契约：in-process 与 http-sse 必须给出同样的命令/查询结果与事件序列。
 */
class HttpBridgeContractTest {
    private val home: MycoHome = MycoHome(Files.createTempDirectory("myco-http-bridge")).also { it.ensureLayout() }
    private val sessions = SessionStore(home)
    private val providers = ProviderStore(home)
    private val credentials = CredentialResolver(home, emptyMap())
    private val tools = ToolRegistry()
    private val factory: (ProviderProfile) -> ChatClient = { FakeChatClient(tickMs = 1) }
    private val inProcess = InProcessBridge(home, sessions, providers, credentials, tools, factory)
    private val server = HttpBridgeServer(inProcess, token = "test-token").start()
    private val http = HttpBridgeClient(server.baseUrl, "test-token")

    init {
        providers.save(
            listOf(
                ProviderProfile(
                    id = "local", kind = "openai-completions", baseUrl = "http://127.0.0.1:1/v1",
                    auth = AuthProfile.None,
                    models = listOf(ModelProfile(id = "fake-chat", contextWindow = 1024, maxTokens = 128)),
                )
            ),
            expectedRevision = 0,
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `create and query sessions over http`() = runBlocking {
        val create = http.send(Command.SessionCreate(title = "http"))
        assertTrue(create is CommandResult.Ok)
        val list = http.query(Query.SessionList()) as QueryResult.Ok
        assertTrue(list.data.jsonArray.isNotEmpty())
        assertEquals("http", list.data.jsonArray.first().jsonObject["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `chat over http persists the same five events as in-process`() = runBlocking {
        val httpSession = (http.send(Command.SessionCreate(title = "h")) as CommandResult.Ok)
            .data.jsonObject["sessionId"]!!.jsonPrimitive.content
        val localSession = (inProcess.send(Command.SessionCreate(title = "l")) as CommandResult.Ok)
            .data.jsonObject["sessionId"]!!.jsonPrimitive.content

        assertTrue(http.send(Command.ChatSend(httpSession, "在吗")) is CommandResult.Ok)
        assertTrue(inProcess.send(Command.ChatSend(localSession, "在吗")) is CommandResult.Ok)

        val httpTypes = sessionTypes(httpSession)
        val localTypes = sessionTypes(localSession)
        assertEquals(localTypes, httpTypes)
        assertEquals(
            listOf("session/start", "turn/start", "user/message", "assistant/message", "turn/end"),
            httpTypes,
        )
    }

    @Test
    fun `events stream delivers delta and done`() = runBlocking {
        val sessionId = (http.send(Command.SessionCreate(title = "stream")) as CommandResult.Ok)
            .data.jsonObject["sessionId"]!!.jsonPrimitive.content

        val collected = mutableListOf<BridgeEvent>()
        val job = launch { http.events.collect { collected += it } }
        Thread.sleep(500)
        // 直接驱动同一个 bridge：本用例测的是 SSE 转发，不是 HTTP 命令（命令已在其它用例覆盖）
        inProcess.send(Command.ChatSend(sessionId, "你好"))
        withTimeout(20_000) {
            while (collected.none { it is BridgeEvent.AssistantDone }) {
                kotlinx.coroutines.delay(50)
            }
        }
        job.cancelAndJoin()

        System.err.println("[dbg] collected=${collected.size} types=${collected.map { it::class.simpleName }}")
        assertTrue(collected.any { it is BridgeEvent.AssistantDelta && it.sessionId == sessionId })
        assertTrue(collected.any { it is BridgeEvent.AssistantDone && it.sessionId == sessionId })
    }

    @Test
    fun `provider save and credential set over http, value never echoed`() = runBlocking {
        val profile = ProviderProfile(
            id = "deepseek-http", kind = "deepseek-messages",
            baseUrl = "https://api.deepseek.com/anthropic",
            auth = AuthProfile.ApiKey("MYCO_HTTP_TEST_KEY"),
            models = listOf(ModelProfile(id = "deepseek-flash", contextWindow = 1_000_000, maxTokens = 384_000)),
        )
        val snapshot = http.query(Query.Providers) as QueryResult.Ok
        val rev = snapshot.data.jsonObject["revision"]!!.jsonPrimitive.content.toLong()
        assertTrue(http.send(Command.ProviderSave(profile, expectedRevision = rev)) is CommandResult.Ok)
        assertTrue(http.send(Command.CredentialSet("MYCO_HTTP_TEST_KEY", "sk-http-secret")) is CommandResult.Ok)

        val after = http.query(Query.Providers) as QueryResult.Ok
        val text = after.data.toString()
        assertTrue(text.contains("deepseek-http"))
        assertTrue(!text.contains("sk-http-secret"))
        assertTrue(text.contains("\"configured\":true") || text.contains("\"configured\": true"))
    }

    @Test
    fun `provider export and replace over http`() = runBlocking {
        val exported = http.query(Query.ProviderExport) as QueryResult.Ok
        val obj = exported.data.jsonObject
        val rev = obj["revision"]!!.jsonPrimitive.content.toLong()
        val list = dev.myco.core.spec.MycoJson.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(ProviderProfile.serializer()),
            obj["providers"]!!,
        )
        assertTrue(http.send(Command.ProviderReplace(list, expectedRevision = rev)) is CommandResult.Ok)
    }

    @Test
    fun `bad token is rejected`() = runBlocking {
        val unauth = HttpBridgeClient(server.baseUrl, "wrong")
        val res = runCatching { unauth.query(Query.SessionList()) }
        assertTrue(res.isFailure || (res.getOrNull() is QueryResult.Err))
    }

    private fun sessionTypes(sessionId: String): List<String> =
        sessions.read(sessionId).map { e ->
            when (e) {
                is SessionEvent.SessionStart -> "session/start"
                is SessionEvent.TurnStart -> "turn/start"
                is SessionEvent.UserMessage -> "user/message"
                is SessionEvent.AssistantMessage -> "assistant/message"
                is SessionEvent.TurnEnd -> "turn/end"
                else -> "other"
            }
        }
}
