// 验收 ID：CMP-48 CMP-49 CMP-50 CMP-51 CMP-53 CMP-54 CMP-55 CMP-56 CMP-57 CMP-77 CMP-78
//
// `CommandPalette.kt`（`/` 本地静态命令候选）的 JVM 单测：
//  * 命令表 = **恰好 5 条**本地 UI 动作（不发请求）；
//  * token 判定（行首/空白后、query 无空白）；**`@` 不触发**（CMP-78）；
//  * 前缀过滤、无匹配整体关闭、手动关闭记忆；
//  * 选中 = 移除 `/query` token + 不写命令名 + **不自动发送**（CMP-56）。
package dev.myco.android.ui.m5.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteTest {

    // ---------------------------------------------------------------- 命令表（CMP-77）

    @Test
    fun `command table has exactly five local ui commands`() {
        assertEquals(5, ComposerCommands.ALL.size)
        assertEquals(
            listOf("/new", "/model", "/settings", "/attach", "/clear"),
            ComposerCommands.ALL.map { it.slashName },
        )
        assertEquals(5, ComposerCommandId.entries.size)
    }

    @Test
    fun `command ids map to slash names`() {
        assertEquals("/new", ComposerCommands.byId(ComposerCommandId.NEW).slashName)
        assertEquals("/model", ComposerCommands.byId(ComposerCommandId.MODEL).slashName)
        assertEquals("/settings", ComposerCommands.byId(ComposerCommandId.SETTINGS).slashName)
        assertEquals("/attach", ComposerCommands.byId(ComposerCommandId.ATTACH).slashName)
        assertEquals("/clear", ComposerCommands.byId(ComposerCommandId.CLEAR).slashName)
    }

    // ---------------------------------------------------------------- token 判定

    @Test
    fun `slash at line start opens token`() {
        val token = CommandPaletteLogic.detectSlashToken("/", 1)
        assertNotNull(token)
        assertEquals(0, token!!.start)
        assertEquals(1, token.end)
        assertEquals("", token.query)
    }

    @Test
    fun `slash after whitespace opens token`() {
        val token = CommandPaletteLogic.detectSlashToken("你好 /mo", 6)
        assertNotNull(token)
        assertEquals(3, token!!.start)
        assertEquals(6, token.end)
        assertEquals("mo", token.query)
    }

    @Test
    fun `slash after newline opens token`() {
        val token = CommandPaletteLogic.detectSlashToken("第一行\n/att", 8)
        assertNotNull(token)
        assertEquals(4, token!!.start)
        assertEquals("att", token.query)
    }

    @Test
    fun `slash inside a word does not open token`() {
        assertNull(CommandPaletteLogic.detectSlashToken("ab/cd", 5))
        assertNull(CommandPaletteLogic.detectSlashToken("ab/cd", 3))
    }

    @Test
    fun `mention at-sign never opens the palette`() {
        // CMP-78：`@` 提及首版不做 —— 无任何 @ 触发分支
        assertNull(CommandPaletteLogic.detectSlashToken("@", 1))
        assertNull(CommandPaletteLogic.detectSlashToken("@model", 6))
        assertNull(CommandPaletteLogic.detectSlashToken("你好 @mod", 8))
    }

    @Test
    fun `empty text or cursor at zero has no token`() {
        assertNull(CommandPaletteLogic.detectSlashToken("", 0))
        assertNull(CommandPaletteLogic.detectSlashToken("abc", 0))
    }

    @Test
    fun `token end extends past cursor to avoid leftover half command`() {
        // 光标停在 token 中间：选中后删除整段，避免残留 "del"
        val token = CommandPaletteLogic.detectSlashToken("/model", 3)
        assertEquals(0, token!!.start)
        assertEquals(6, token.end)
        assertEquals("mo", token.query)
    }

    // ---------------------------------------------------------------- 过滤 / 可见性

    @Test
    fun `prefix filter`() {
        assertEquals(5, CommandPaletteLogic.filter("").size)
        assertEquals(listOf("/model"), CommandPaletteLogic.filter("m").map { it.slashName })
        assertEquals(listOf("/model"), CommandPaletteLogic.filter("MO").map { it.slashName })
        assertEquals(listOf("/settings"), CommandPaletteLogic.filter("se").map { it.slashName })
        assertEquals(listOf("/attach"), CommandPaletteLogic.filter("at").map { it.slashName })
        assertTrue(CommandPaletteLogic.filter("zzz").isEmpty())
    }

    @Test
    fun `no prefix match closes the panel without an empty shell`() {
        // CMP-50：/zzz → 面板整体关闭
        val visibility = CommandPaletteLogic.visibility("/zzz", 4)
        assertFalse(visibility.visible)
        assertNull(visibility.token)
    }

    @Test
    fun `typing slash opens with five candidates`() {
        val visibility = CommandPaletteLogic.visibility("/", 1)
        assertTrue(visibility.visible)
        assertEquals(5, visibility.commands.size)
    }

    @Test
    fun `manual dismiss remembers the same token`() {
        val memory = CommandPaletteMemory().dismissFor(SlashToken(0, 2, "m"))
        // 同一 token 不再自动弹（§3.15 字段 5 ④）
        assertFalse(CommandPaletteLogic.visibility("/m", 2, memory).visible)
        // 换成别的 token → 重新弹（记忆只绑当时的 token）
        assertTrue(CommandPaletteLogic.visibility("/model", 6, memory).visible)
        assertTrue(CommandPaletteLogic.visibility("/", 1, memory).visible)
    }

    @Test
    fun `memory clears when token changes or is deleted`() {
        val memory = CommandPaletteMemory().dismissFor(SlashToken(0, 2, "m"))
        assertEquals(memory, CommandPaletteLogic.memoryAfterTextChange(memory, "/m", 2))
        assertNull(CommandPaletteLogic.memoryAfterTextChange(memory, "/mo", 3).dismissedToken)
        assertNull(CommandPaletteLogic.memoryAfterTextChange(memory, "", 0).dismissedToken)
    }

    // ---------------------------------------------------------------- 选中（CMP-56/57）

    @Test
    fun `select removes token and does not write command into draft`() {
        val command = ComposerCommands.byId(ComposerCommandId.MODEL)
        val token = SlashToken(0, 4, "mod")
        val selection = CommandPaletteLogic.select("/mod", token, command)
        assertEquals("", selection.draft) // 不把 /model 写进草稿
        assertEquals(0, selection.cursor)
    }

    @Test
    fun `select keeps surrounding text and puts cursor at token start`() {
        val command = ComposerCommands.byId(ComposerCommandId.MODEL)
        // "// 前置说明" 里的 token 在中间
        val draft = "先看图 /mod 再说"
        val token = SlashToken(4, 8, "mod")
        val selection = CommandPaletteLogic.select(draft, token, command)
        assertEquals("先看图  再说", selection.draft)
        assertEquals(4, selection.cursor)
    }

    @Test
    fun `selection never sends anything`() {
        // 选中 ≠ 提交：本 API 只返回草稿/光标，没有任何"发送"字段或副作用
        val selection = CommandPaletteLogic.select(
            "/attach 后面的话",
            SlashToken(0, 7, "attach"),
            ComposerCommands.byId(ComposerCommandId.ATTACH),
        )
        assertEquals(" 后面的话", selection.draft)
        assertFalse(selection.draft.contains("attach"))
    }

    @Test
    fun `select tolerates out-of-range token`() {
        val command = ComposerCommands.byId(ComposerCommandId.CLEAR)
        val selection = CommandPaletteLogic.select("abc", SlashToken(10, 20, "x"), command)
        assertEquals("abc", selection.draft)
    }

    @Test
    fun `accessibility label contains command name and description`() {
        val command = ComposerCommands.byId(ComposerCommandId.MODEL)
        assertEquals("命令 model，打开模型与推理档位", command.accessibilityLabel)
    }
}
