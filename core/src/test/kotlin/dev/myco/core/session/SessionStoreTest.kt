package dev.myco.core.session

import dev.myco.core.MycoHome
import dev.myco.core.spec.SessionMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class SessionStoreTest {
    private fun home(): MycoHome = MycoHome(Files.createTempDirectory("myco-test")).also { it.ensureLayout() }

    @Test
    fun `create and append assigns monotonic seq`() {
        val h = home()
        val store = SessionStore(h)
        val meta = store.create("chat", "chat-default", "测试")
        assertEquals(1, store.read(meta.id).size) // 只有 session/start

        store.append(meta.id, SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data("你好")))
        store.append(meta.id, SessionEvent.AssistantMessage(data = SessionEvent.AssistantMessage.Data("在")))

        val events = store.read(meta.id)
        assertEquals(3, events.size)
        assertEquals(listOf(0L, 1L, 2L), events.map { it.seq })
        assertTrue(events[1] is SessionEvent.UserMessage)
    }

    @Test
    fun `seq continues across store instances`() {
        val h = home()
        val first = SessionStore(h)
        val meta = first.create("chat", "chat-default")
        first.append(meta.id, SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data("a")))

        val second = SessionStore(h)
        val appended = second.append(meta.id, SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data("b")))
        assertEquals(2L, appended.seq)
        assertEquals(3, second.read(meta.id).size)
    }

    @Test
    fun `incomplete tail line is dropped`() {
        val h = home()
        val store = SessionStore(h)
        val meta = store.create("chat", "chat-default")
        store.append(meta.id, SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data("ok")))
        val file = h.sessions.resolve(meta.id).resolve(SessionStore.FILE_NAME)
        Files.writeString(file, "{\"type\":\"user/message\",\"seq\":2", java.nio.file.StandardOpenOption.APPEND)

        val events = store.read(meta.id)
        assertEquals(2, events.size) // session/start + user/message
    }

    @Test
    fun `corruption in the middle throws`() {
        val h = home()
        val store = SessionStore(h)
        val meta = store.create("chat", "chat-default")
        val file = h.sessions.resolve(meta.id).resolve(SessionStore.FILE_NAME)
        Files.writeString(file, "not-json\n", java.nio.file.StandardOpenOption.APPEND)
        store.append(meta.id, SessionEvent.UserMessage(data = SessionEvent.UserMessage.Data("after")))
        assertThrows(CorruptSessionException::class.java) { store.read(meta.id) }
    }

    @Test
    fun `unknown format version is rejected`() {
        val h = home()
        val id = "01UNKNOWNFORMAT0000000000"
        val dir = h.sessions.resolve(id)
        Files.createDirectories(dir)
        val meta = SessionMeta(id, "t", 1, "chat", "chat-default", formatVersion = 2)
        val line = dev.myco.core.spec.MycoJson.encodeToString(
            SessionEvent.serializer(), SessionEvent.SessionStart(seq = 0, at = 1, data = meta)
        )
        Files.writeString(dir.resolve(SessionStore.FILE_NAME), line + "\n")
        assertThrows(UnknownSessionFormatException::class.java) { SessionStore(h).read(id) }
    }

    @Test
    fun `list returns newest first`() {
        val h = home()
        val store = SessionStore(h)
        val a = store.create("chat", "chat-default", "a")
        Thread.sleep(5)
        val b = store.create("chat", "chat-default", "b")
        val list = store.list()
        assertEquals(listOf(b.id, a.id), list.map { it.id })
    }
}
