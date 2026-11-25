package dev.myco.core.attachment

import dev.myco.core.MycoHome
import dev.myco.core.spec.AttachmentRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AttachmentStoreTest {
    private fun store(): Pair<MycoHome, AttachmentStore> {
        val home = MycoHome(Files.createTempDirectory("myco-att")).also { it.ensureLayout() }
        return home to AttachmentStore(home)
    }

    @Test
    fun `saves and reads text attachment with sanitized name`() {
        val (_, s) = store()
        val ref = s.save("s1", "笔记.md", "text/markdown", "# 标题\n正文".toByteArray())
        assertEquals("text", ref.kind)
        assertEquals("笔记.md", ref.name)
        assertTrue(ref.size > 0)
        val text = s.textContent("s1", ref)
        assertTrue(text.contains("正文"))
    }

    @Test
    fun `image classified as image`() {
        val (_, s) = store()
        val ref = s.save("s1", "a.png", "image/png", byteArrayOf(1, 2, 3))
        assertEquals("image", ref.kind)
    }

    @Test
    fun `rejects oversized files`() {
        val (_, s) = store()
        val big = ByteArray((8 * 1024 * 1024) + 1)
        assertThrows(AttachmentTooLargeException::class.java) {
            s.save("s1", "big.bin", "application/octet-stream", big)
        }
    }

    @Test
    fun `ref from another session is not readable`() {
        val (_, s) = store()
        val ref = s.save("s1", "a.txt", "text/plain", "x".toByteArray())
        assertThrows(AttachmentNotFoundException::class.java) { s.read("s2", ref) }
    }

    @Test
    fun `traversal in id cannot escape session dir`() {
        val (home, s) = store()
        val ref = AttachmentRef("../evil", "a.txt", "text/plain", 1, "text")
        assertThrows(AttachmentOutsideRootException::class.java) { s.read("s1", ref) }
        assertTrue(!Files.exists(home.root.resolve("evil.txt")))
    }
}
