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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class InProcessBridgeTest {
    private class Fixture(withProvider: Boolean = true) {
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
            val factory: (ProviderProfile) -> ChatClient = { FakeChatClient() }
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
    fun `session list query respects app filter`() = runBlocking {
        val f = Fixture()
        f.sessions.create("chat", "chat-default")
        f.sessions.create("work", "work-default")
        val chatOnly = f.bridge.query(Query.SessionList(app = "chat"))
        assertTrue(chatOnly is QueryResult.Ok)
    }
}
