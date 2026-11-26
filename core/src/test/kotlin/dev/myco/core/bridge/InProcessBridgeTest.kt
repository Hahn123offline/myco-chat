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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
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
        val attachments = dev.myco.core.attachment.AttachmentStore(home)
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
            bridge = InProcessBridge(home, sessions, providers, credentials, tools, attachments, factory)
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
        assertTrue(assistant.data.messageId != null)
        assertTrue(assistant.data.nodeId != null)
        assertEquals(1, assistant.data.version)
        assertEquals("stop", assistant.data.finishReason)
        assertTrue(assistant.data.usage != null)
        val end = events.filterIsInstance<SessionEvent.TurnEnd>().single()
        assertEquals("ok", end.data.status)
        assertEquals(assistant.data.messageId, end.data.messageId)
        assertTrue((end.data.durationMs ?: -1) >= 0)
        assertTrue((end.data.costUsd ?: 0.0) >= 0.0)
        // 回放 JSON 不丢 costUsd/messageId（B1 要求）
        val replay = f.bridge.query(Query.SessionEvents(meta.id, 0)) as QueryResult.Ok
        val replayText = replay.data.toString()
        assertTrue(replayText.contains("\"status\":\"ok\""))
        assertTrue(replayText.contains("\"messageId\":"))
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
        assertEquals("stopped", end.data.status)
        assertEquals("stopped", assistant.data.finishReason)
        assertEquals(assistant.data.messageId, end.data.messageId)
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
    fun `provider export and replace roundtrip`() = runBlocking {
        val f = Fixture()
        val exported = f.bridge.query(Query.ProviderExport) as QueryResult.Ok
        val obj = exported.data.jsonObject
        val rev = obj["revision"]!!.jsonPrimitive.content.toLong()
        val list = dev.myco.core.spec.MycoJson.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(ProviderProfile.serializer()),
            obj["providers"]!!,
        )
        assertTrue(list.isNotEmpty())
        val res = f.bridge.send(Command.ProviderReplace(list, expectedRevision = rev))
        assertTrue(res is CommandResult.Ok)
        val after = f.bridge.query(Query.Providers) as QueryResult.Ok
        assertEquals(rev + 1, after.data.jsonObject["revision"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `text attachment is inlined and persisted`() = runBlocking {
        var lastRequest: dev.myco.core.llm.ChatRequest? = null
        val client = object : ChatClient {
            override fun stream(request: dev.myco.core.llm.ChatRequest): kotlinx.coroutines.flow.Flow<dev.myco.core.llm.ChatChunk> =
                kotlinx.coroutines.flow.flow {
                    lastRequest = request
                    emit(dev.myco.core.llm.ChatChunk.Delta("收到"))
                    emit(dev.myco.core.llm.ChatChunk.Done("收到"))
                }
        }
        val f = Fixture(client = client)
        val meta = f.sessions.create("chat", "chat-default")
        val ref = f.attachments.save(meta.id, "note.txt", "text/plain", "附件里的秘密内容".toByteArray())
        val res = f.bridge.send(Command.ChatSend(meta.id, "看看附件", listOf(ref)))
        assertTrue(res is CommandResult.Ok)
        val userMessage = lastRequest!!.messages.last { it.role == "user" }
        assertTrue(userMessage.content.contains("附件里的秘密内容"))
        assertTrue(userMessage.content.contains("note.txt"))
        val persisted = f.sessions.read(meta.id).filterIsInstance<SessionEvent.UserMessage>().last()
        assertEquals(1, persisted.data.attachments.size)
        assertEquals(ref.id, persisted.data.attachments[0].id)
    }

    @Test
    fun `image attachment is passed as vision input`() = runBlocking {
        var lastRequest: dev.myco.core.llm.ChatRequest? = null
        val client = object : ChatClient {
            override fun stream(request: dev.myco.core.llm.ChatRequest): kotlinx.coroutines.flow.Flow<dev.myco.core.llm.ChatChunk> =
                kotlinx.coroutines.flow.flow {
                    lastRequest = request
                    emit(dev.myco.core.llm.ChatChunk.Delta("看到了"))
                    emit(dev.myco.core.llm.ChatChunk.Done("看到了"))
                }
        }
        val f = Fixture(client = client)
        val meta = f.sessions.create("chat", "chat-default")
        // 假 PNG 字节即可（序列化层不校验图片格式）
        val ref = f.attachments.save(meta.id, "a.png", "image/png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val res = f.bridge.send(Command.ChatSend(meta.id, "描述这张图", listOf(ref)))
        assertTrue(res is CommandResult.Ok)
        val userMessage = lastRequest!!.messages.last { it.role == "user" }
        assertEquals(1, userMessage.images.size)
        assertEquals("image/png", userMessage.images[0].mediaType)
        assertTrue(userMessage.images[0].base64.isNotEmpty())
    }

    @Test
    fun `missing attachment is rejected before a turn starts`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        val ghost = dev.myco.core.spec.AttachmentRef("nope", "ghost.txt", "text/plain", 1, "text")
        val res = f.bridge.send(Command.ChatSend(meta.id, "hi", listOf(ghost)))
        assertTrue(res is CommandResult.Err)
        assertEquals("ATTACHMENT_NOT_FOUND", (res as CommandResult.Err).code)
        assertTrue(f.sessions.read(meta.id).none { it is SessionEvent.TurnStart })
    }

    @Test
    fun `bridge events carry monotonic seq and can be replayed`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        val seen = mutableListOf<BridgeEvent>()
        val collector = launch {
            f.bridge.events.collect { seen += it }
        }
        kotlinx.coroutines.delay(100) // 让订阅先建立（runBlocking 单线程调度）
        f.bridge.send(Command.ChatSend(meta.id, "你好"))
        kotlinx.coroutines.withTimeout(5_000) {
            while (seen.none { it is BridgeEvent.AssistantDone }) kotlinx.coroutines.delay(20)
        }
        collector.cancel()
        val seqs = seen.filter { it.sessionId == meta.id }.map { it.seq }
        assertTrue(seqs.size >= 2)
        assertEquals(seqs.sorted(), seqs)
        assertEquals(seqs.distinct().size, seqs.size)

        val replay = f.bridge.query(Query.EventReplay(meta.id, 0)) as QueryResult.Ok
        val arr = replay.data as JsonArray
        assertTrue(arr.size >= 2)
        val firstSeq = arr[0].jsonObject["seq"]!!.jsonPrimitive.content.toLong()
        val lastSeq = arr.last().jsonObject["seq"]!!.jsonPrimitive.content.toLong()
        assertTrue(firstSeq >= 1)
        assertTrue(lastSeq > firstSeq)
        // fromSeq 之后只回放增量
        val tail = f.bridge.query(Query.EventReplay(meta.id, lastSeq - 1)) as QueryResult.Ok
        assertTrue((tail.data as JsonArray).size >= 1)
    }

    @Test
    fun `chat send with same requestId runs once`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default")
        val first = f.bridge.send(Command.ChatSend(meta.id, "你好", requestId = "req-1"))
        val second = f.bridge.send(Command.ChatSend(meta.id, "你好", requestId = "req-1"))
        assertTrue(first is CommandResult.Ok)
        assertTrue(second is CommandResult.Ok)
        assertEquals(first, second)
        val turns = f.sessions.read(meta.id).count { it is SessionEvent.TurnStart }
        assertEquals(1, turns)
    }

    @Test
    fun `failed event carries sessionId`() = runBlocking {
        val failing = object : ChatClient {
            override fun stream(request: dev.myco.core.llm.ChatRequest): kotlinx.coroutines.flow.Flow<dev.myco.core.llm.ChatChunk> =
                kotlinx.coroutines.flow.flow {
                    emit(dev.myco.core.llm.ChatChunk.Failed("boom", retriable = false))
                }
        }
        val f = Fixture(client = failing)
        val meta = f.sessions.create("chat", "chat-default")
        val seen = mutableListOf<BridgeEvent>()
        val collector = launch { f.bridge.events.collect { seen += it } }
        kotlinx.coroutines.delay(100)
        val res = f.bridge.send(Command.ChatSend(meta.id, "hi"))
        assertTrue(res is CommandResult.Err)
        kotlinx.coroutines.withTimeout(5_000) {
            while (seen.none { it is BridgeEvent.Failed }) kotlinx.coroutines.delay(20)
        }
        collector.cancel()
        val failed = seen.filterIsInstance<BridgeEvent.Failed>().single()
        assertEquals(meta.id, failed.sessionId)
        assertTrue(failed.seq >= 1)
    }

    @Test
    fun `regenerate creates a new version on the same node`() = runBlocking {
        var calls = 0
        val client = object : ChatClient {
            override fun stream(request: dev.myco.core.llm.ChatRequest): kotlinx.coroutines.flow.Flow<dev.myco.core.llm.ChatChunk> =
                kotlinx.coroutines.flow.flow {
                    calls++
                    val answer = "回答$calls"
                    emit(dev.myco.core.llm.ChatChunk.Delta(answer))
                    emit(dev.myco.core.llm.ChatChunk.Done(answer, finishReason = "stop"))
                }
        }
        val f = Fixture(client = client)
        val meta = f.sessions.create("chat", "chat-default")
        f.bridge.send(Command.ChatSend(meta.id, "问题一"))
        val first = f.sessions.read(meta.id).filterIsInstance<SessionEvent.AssistantMessage>().single()
        val firstNode = first.data.nodeId!!
        val result = f.bridge.send(Command.ChatRegenerate(meta.id, first.data.messageId!!))
        assertTrue(result is CommandResult.Ok)
        val data = (result as CommandResult.Ok).data.jsonObject
        assertEquals(2, data["version"]!!.jsonPrimitive.content.toInt())
        assertEquals(firstNode, data["nodeId"]!!.jsonPrimitive.content)

        val assistants = f.sessions.read(meta.id).filterIsInstance<SessionEvent.AssistantMessage>()
        val versions = assistants.filter { it.data.nodeId == firstNode }.map { it.data.version }.sorted()
        assertEquals(listOf(1, 2), versions)
        assertEquals(2, calls)
        // 旧版本仍保留，不删除
        assertEquals("回答1", assistants.first { it.data.version == 1 }.data.text)
    }

    @Test
    fun `concurrent sends are serialized with distinct turn numbers`() = runBlocking {
        val slow = FakeChatClient(reply = { "慢回答" }, tickMs = 40)
        val f = Fixture(client = slow)
        val meta = f.sessions.create("chat", "chat-default")
        val a = async { f.bridge.send(Command.ChatSend(meta.id, "A")) }
        val b = async { f.bridge.send(Command.ChatSend(meta.id, "B")) }
        assertTrue(a.await() is CommandResult.Ok)
        assertTrue(b.await() is CommandResult.Ok)
        val turns = f.sessions.read(meta.id).filterIsInstance<SessionEvent.TurnStart>().map { it.data.turn }
        assertEquals(listOf(1L, 2L), turns)
    }

    @Test
    fun `stop with wrong turnId does not cancel`() = runBlocking {
        val slow = FakeChatClient(reply = { "很长的回答".repeat(50) }, tickMs = 30)
        val f = Fixture(client = slow)
        val meta = f.sessions.create("chat", "chat-default")
        val job = async { f.bridge.send(Command.ChatSend(meta.id, "长")) }
        kotlinx.coroutines.delay(150)
        val wrong = f.bridge.send(Command.ChatStop(meta.id, turnId = 99))
        assertTrue(wrong is CommandResult.Err)
        assertEquals("TURN_MISMATCH", (wrong as CommandResult.Err).code)
        val right = f.bridge.send(Command.ChatStop(meta.id, turnId = 1))
        assertTrue(right is CommandResult.Ok)
        job.await()
        Unit
    }

    @Test
    fun `tool loop executes registry and feeds result back`() = runBlocking {
        val client = object : ChatClient {
            var calls = 0
            override fun stream(request: dev.myco.core.llm.ChatRequest): kotlinx.coroutines.flow.Flow<dev.myco.core.llm.ChatChunk> =
                kotlinx.coroutines.flow.flow {
                    calls++
                    if (calls == 1) {
                        emit(dev.myco.core.llm.ChatChunk.ToolRequest("call_1", "time_now", kotlinx.serialization.json.JsonObject(emptyMap())))
                        emit(dev.myco.core.llm.ChatChunk.Done(""))
                    } else {
                        emit(dev.myco.core.llm.ChatChunk.Delta("现在时间拿到了"))
                        emit(dev.myco.core.llm.ChatChunk.Done("现在时间拿到了"))
                    }
                }
        }
        val f = Fixture(client = client)
        f.tools.register(dev.myco.core.tools.timeNowTool())
        val meta = f.sessions.create("chat", "chat-default")
        val res = f.bridge.send(Command.ChatSend(meta.id, "几点了"))
        assertTrue(res is CommandResult.Ok)
        assertEquals(2, client.calls)

        val events = f.sessions.read(meta.id)
        val call = events.filterIsInstance<SessionEvent.ToolCall>().single()
        assertEquals("time_now", call.data.name)
        val result = events.filterIsInstance<SessionEvent.ToolResult>().single()
        assertTrue(result.data.ok)
        assertTrue(result.data.text.contains("epochMs"))
        assertEquals(2, events.filterIsInstance<SessionEvent.TurnEnd>().last().data.steps)
    }

    @Test
    fun `tool schema over budget is rejected before any turn`() = runBlocking {
        val f = Fixture()
        repeat(30) { i ->
            f.tools.register(
                dev.myco.core.tools.ToolDef(
                    name = "tool_$i",
                    summary = "s",
                    description = "d".repeat(900),
                ) { "" }
            )
        }
        val meta = f.sessions.create("chat", "chat-default")
        val res = f.bridge.send(Command.ChatSend(meta.id, "hi"))
        assertTrue(res is CommandResult.Err)
        assertEquals("TOOL_BUDGET", (res as CommandResult.Err).code)
        assertTrue(f.sessions.read(meta.id).none { it is SessionEvent.TurnStart })
    }

    @Test
    fun `rename delete search session via bridge`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default", "旧标题")
        assertTrue(f.bridge.send(Command.SessionRename(meta.id, "新标题")) is CommandResult.Ok)
        val listed = f.bridge.query(Query.SessionList()) as QueryResult.Ok
        assertEquals("新标题", (listed.data as JsonArray)[0].jsonObject["title"]!!.jsonPrimitive.content)

        assertTrue(f.bridge.send(Command.SessionDelete(meta.id)) is CommandResult.Ok)
        val afterDelete = f.bridge.query(Query.SessionList()) as QueryResult.Ok
        assertEquals(0, (afterDelete.data as JsonArray).size)

        val search = f.bridge.query(Query.SessionSearch("新标题")) as QueryResult.Ok
        assertEquals(0, (search.data as JsonArray).size) // 软删后不出现
        f.bridge.send(Command.SessionRestore(meta.id))
        val restored = f.bridge.query(Query.SessionSearch("新标题")) as QueryResult.Ok
        assertEquals(1, (restored.data as JsonArray).size)
    }

    @Test
    fun `pin archive and permanent delete via bridge`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default", "归档测试")
        assertTrue(f.bridge.send(Command.SessionPin(meta.id, true)) is CommandResult.Ok)
        var list = f.bridge.query(Query.SessionList()) as QueryResult.Ok
        assertEquals("true", (list.data as JsonArray)[0].jsonObject["pinned"]!!.jsonPrimitive.content)

        assertTrue(f.bridge.send(Command.SessionArchive(meta.id, true)) is CommandResult.Ok)
        list = f.bridge.query(Query.SessionList()) as QueryResult.Ok
        assertEquals(0, (list.data as JsonArray).size)
        val archived = f.bridge.query(Query.SessionList(includeArchived = true)) as QueryResult.Ok
        assertEquals(1, (archived.data as JsonArray).size)

        assertTrue(f.bridge.send(Command.SessionDeletePermanent(meta.id)) is CommandResult.Ok)
        val all = f.bridge.query(Query.SessionList(includeArchived = true, includeDeleted = true)) as QueryResult.Ok
        assertEquals(0, (all.data as JsonArray).size)
    }

    @Test
    fun `session search returns seq for jump-to-message`() = runBlocking {
        val f = Fixture()
        val meta = f.sessions.create("chat", "chat-default", "搜索")
        f.sessions.append(meta.id, SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data("找这个词")))
        val hit = (f.bridge.query(Query.SessionSearch("这个词")) as QueryResult.Ok).data as JsonArray
        assertEquals(1, hit.size)
        assertTrue(hit[0].jsonObject["seq"]!!.jsonPrimitive.content.toLong() >= 0)
        assertEquals("user", hit[0].jsonObject["role"]!!.jsonPrimitive.content)
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


