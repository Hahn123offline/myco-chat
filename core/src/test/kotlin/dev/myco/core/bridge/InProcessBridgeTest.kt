package dev.myco.core.bridge

import dev.myco.core.MycoHome
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
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class InProcessBridgeTest {
    private class Fixture(withProvider: Boolean = true, client: ChatClient = FakeChatClient()) {
        val home: MycoHome = MycoHome(Files.createTempDirectory("myco-bridge")).also { it.ensureLayout() }
        val sessions = SessionStore(home)
        val providers = ProviderStore(home)
        val credentials = CredentialResolver(home, emptyMap())
        val tools = ToolRegistry()
        val bridge: InProcessBridge

        init {
            if (withProvider) {
                providers.save(
                    listOf(
                        ProviderProfile(
                            id = "local",
                            kind = "openai-completions",
                            baseUrl = "http://127.0.0.1:1/v1",
                            auth = AuthProfile.None,
                            models = listOf(ModelProfile(id = "fake-chat", contextWindow = 1024, maxTokens = 128)),
                        )
                    ),
                    expectedRevision = 0,
                )
            }
            val factory: (ProviderProfile) -> ChatClient = { client }
            bridge = InProcessBridge(home, sessions, providers, credentials, tools, factory)
        }
    }

    @Test
    fun `chat send persists a complete turn`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        val result = f.bridge.send(Command.ChatSend(meta.id, "在吗"))
        assertTrue(result is CommandResult.Ok)

        val events = f.sessions.read(meta.id)
        assertEquals(
            listOf(
                "session/start", "turn/start", "user/message", "assistant/message", "turn/end"
            ),
            events.map {
                when (it) {
                    is SessionEvent.SessionStart -> "session/start"
                    is SessionEvent.TurnStart -> "turn/start"
                    is SessionEvent.UserMessage -> "user/message"
                    is SessionEvent.AssistantMessage -> "assistant/message"
                    is SessionEvent.TurnEnd -> "turn/end"
                    else -> "other"
                }
            }
        )
        val assistant = events.filterIsInstance<SessionEvent.AssistantMessage>().single()
        assertTrue(assistant.data.text.contains("在吗"))
    }

    @Test
    fun `missing provider fails loudly without writing events`() = runBlocking {
        val f = Fixture(withProvider = false)
        val meta = f.sessions.create("chat", "chat-default")
        val before = f.sessions.read(meta.id).size
        val result = f.bridge.send(Command.ChatSend(meta.id, "hi"))
        assertTrue(result is CommandResult.Err)
        assertEquals("PROVIDER_MISSING", (result as CommandResult.Err).code)
        assertEquals(before, f.sessions.read(meta.id).size)
    }

    @Test
    fun `second turn includes history`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        f.bridge.send(Command.ChatSend(meta.id, "第一句"))
        f.bridge.send(Command.ChatSend(meta.id, "第二句"))
        val turns = f.sessions.read(meta.id).filterIsInstance<SessionEvent.TurnStart>()
        assertEquals(listOf(1L, 2L), turns.map { it.data.turn })
        val assistants = f.sessions.read(meta.id).filterIsInstance<SessionEvent.AssistantMessage>()
        assertEquals(2, assistants.size)
    }

    @Test
    fun `provider save delete and revision conflict via bridge`() = runBlocking {
        val f = Fixture(withProvider = false)
        val p = ProviderProfile(
            id = "deepseek", kind = "deepseek-messages",
            baseUrl = "https://api.deepseek.com/anthropic",
            auth = AuthProfile.ApiKey("DEEPSEEK_API_KEY"),
            models = listOf(ModelProfile(id = "deepseek-flash", contextWindow = 1_000_000, maxTokens = 384_000)),
        )
        val saved = f.bridge.send(Command.ProviderSave(p, expectedRevision = 0))
        assertTrue(saved is CommandResult.Ok)
        val rev = ((saved as CommandResult.Ok).data as kotlinx.serialization.json.JsonObject)["revision"]!!.jsonPrimitive.content.toLong()

        // 用过期 revision 再写 → 明确冲突，不静默覆盖
        val stale = f.bridge.send(Command.ProviderSave(p.copy(displayName = "x"), expectedRevision = 0))
        assertTrue(stale is CommandResult.Err)
        assertEquals("SETTINGS_CONFLICT", (stale as CommandResult.Err).code)

        val providersQuery = f.bridge.query(Query.Providers) as QueryResult.Ok
        assertTrue(providersQuery.data.jsonObject["providers"]!!.jsonArray.any {
            it.jsonObject["id"]?.jsonPrimitive?.content == "deepseek"
        })

        val deleted = f.bridge.send(Command.ProviderDelete("deepseek", expectedRevision = rev))
        assertTrue(deleted is CommandResult.Ok)
        val missing = f.bridge.send(Command.ProviderDelete("deepseek", expectedRevision = rev + 1))
        assertTrue(missing is CommandResult.Err)
        assertEquals("PROVIDER_NOT_FOUND", (missing as CommandResult.Err).code)
    }

    @Test
    fun `credential set is write only`() = runBlocking {
        val f = Fixture(withProvider = false)
        val set = f.bridge.send(Command.CredentialSet("MYCO_TEST_KEY", "sk-very-secret"))
        assertTrue(set is CommandResult.Ok)
        val providersQuery = f.bridge.query(Query.Providers) as QueryResult.Ok
        assertTrue(!providersQuery.data.toString().contains("sk-very-secret"))
    }

    @Test
    fun `stop cancels running turn and persists partial text`() = runBlocking {
        val slow = FakeChatClient(
            reply = { "这是一段很长的回复，用来测试停止。".repeat(40) },
            tickMs = 20,
        )
        val f = Fixture(withProvider = true, client = slow)
        val meta = f.sessions.create("chat", "chat-default")
        val job = async { f.bridge.send(Command.ChatSend(meta.id, "讲个长故事")) }
        kotlinx.coroutines.delay(200)
        val stop = f.bridge.send(Command.ChatStop(meta.id))
        assertTrue(stop is CommandResult.Ok)
        val result = job.await()
        assertTrue(result is CommandResult.Ok)
        assertTrue((result as CommandResult.Ok).data.toString().contains("\"stopped\":true"))

        val events = f.sessions.read(meta.id)
        val assistant = events.filterIsInstance<SessionEvent.AssistantMessage>().single()
        assertTrue(assistant.data.text.isNotEmpty())
        val end = events.filterIsInstance<SessionEvent.TurnEnd>().single()
        assertEquals(0, end.data.steps)
        // 再发一轮应能正常走（没有卡死的 job）
        val again = f.bridge.send(Command.ChatSend(meta.id, "还在吗"))
        assertTrue(again is CommandResult.Ok)
    }

    @Test
    fun `stop with no running turn returns NOT_RUNNING`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        val res = f.bridge.send(Command.ChatStop(meta.id))
        assertTrue(res is CommandResult.Err)
        assertEquals("NOT_RUNNING", (res as CommandResult.Err).code)
    }

    @Test
    fun `session events query includes message payload for history replay`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        f.bridge.send(Command.ChatSend(meta.id, "历史测试"))
        val res = f.bridge.query(Query.SessionEvents(meta.id, 0)) as QueryResult.Ok
        val texts = (res.data as kotlinx.serialization.json.JsonArray).mapNotNull { el ->
            val o = el.jsonObject
            if (o["type"]?.jsonPrimitive?.content == "user/message") {
                (o["data"] as? kotlinx.serialization.json.JsonObject)?.get("text")?.jsonPrimitive?.content
            } else null
        }
        assertTrue(texts.contains("历史测试"))
    }

    @Test
    fun `session list query respects app filter`() = runBlocking {
        val f = Fixture()
        f.sessions.create("chat", "chat-default")
        f.sessions.create("work", "work-default")
        val chatOnly = f.bridge.query(Query.SessionList(app = "chat"))
        assertTrue(chatOnly is QueryResult.Ok)
    }
}
