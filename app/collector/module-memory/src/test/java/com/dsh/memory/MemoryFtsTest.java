package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** FTS5 SQL 常量与用户 query 转义（纯逻辑）。 */
public class MemoryFtsTest {

    @Test public void 建表语句用trigram与外部内容表() {
        String sql = MemoryFts.CREATE;
        assertTrue(sql.contains("fts5("));
        assertTrue(sql, sql.contains("tokenize='trigram'"));      // 默认 unicode61 检索不到中文子串
        assertTrue(sql, sql.contains("content='blocks'"));
        assertTrue(sql, sql.contains("content_rowid='id'"));
        assertTrue(sql, sql.contains("title"));
        assertTrue(sql, sql.contains("masked_text"));
    }

    @Test public void 三个触发器分别覆盖insert_delete_update() {
        assertEquals(3, MemoryFts.TRIGGERS.length);
        assertTrue(MemoryFts.TRIGGER_INSERT.contains("AFTER INSERT ON blocks"));
        assertTrue(MemoryFts.TRIGGER_INSERT.contains("new.id"));
        assertTrue(MemoryFts.TRIGGER_DELETE.contains("AFTER DELETE ON blocks"));
        assertTrue(MemoryFts.TRIGGER_DELETE.contains("'delete'"));
        assertTrue(MemoryFts.TRIGGER_DELETE.contains("old.masked_text"));
        assertTrue(MemoryFts.TRIGGER_UPDATE.contains("AFTER UPDATE ON blocks"));
        assertTrue(MemoryFts.TRIGGER_UPDATE.contains("'delete'"));
        assertTrue(MemoryFts.TRIGGER_UPDATE.contains("new.masked_text"));
        for (String t : MemoryFts.TRIGGERS) {
            assertTrue("必须幂等: " + t, t.contains("IF NOT EXISTS"));
        }
    }

    @Test public void 回填语句存在() {
        assertTrue(MemoryFts.REBUILD.contains("'rebuild'"));
        assertTrue(MemoryFts.REBUILD.contains(MemoryFts.TABLE));
    }

    @Test public void 查询语句用MATCH与BM25且带score() {
        String sql = MemoryFts.searchSql(5);
        assertTrue(sql, sql.contains(MemoryFts.TABLE + " MATCH ?"));
        assertTrue(sql, sql.contains("bm25(" + MemoryFts.TABLE + ")"));
        assertTrue(sql, sql.contains("AS search_score"));
        assertTrue(sql, sql.contains("ORDER BY bm25("));
        assertTrue(sql.contains("LIMIT 5"));
        assertTrue("tie-break 时间倒序", sql.contains("b.t_start DESC"));
    }

    @Test public void 带出网档位过滤的AND组合() {
        String sql = MemoryFts.searchSqlWithTier(10);
        assertTrue(sql, sql.contains("MATCH ?"));
        assertTrue(sql, sql.contains("AND b.egress_tier=?"));
    }

    @Test public void 用户输入包成FTS短语_内部引号双写() {
        assertEquals("\"影之刃零\"", MemoryFts.phrase("影之刃零"));
        assertEquals("\"影之刃零\"", MemoryFts.phrase(" 影之刃零 "));
        assertEquals("\"he said \"\"hi\"\"\"", MemoryFts.phrase("he said \"hi\""));
        assertEquals("\"- * (\"", MemoryFts.phrase("- * ("));
        assertEquals("\"a-b*c\"", MemoryFts.phrase("a-b*c"));
    }

    @Test public void 空输入不生成短语() {
        assertEquals("", MemoryFts.phrase(""));
        assertEquals("", MemoryFts.phrase("   "));
        assertEquals("", MemoryFts.phrase(null));
    }

    @Test public void 短于3码点不走FTS() {
        assertFalse(MemoryFts.eligible(null));
        assertFalse(MemoryFts.eligible(""));
        assertFalse(MemoryFts.eligible("影"));
        assertFalse(MemoryFts.eligible("影之"));       // 2 个汉字 = 2 码点，trigram 命中不了
        assertTrue(MemoryFts.eligible("影之刃"));
        assertTrue(MemoryFts.eligible("abc"));
        assertTrue(MemoryFts.eligible("PS5"));
    }

    @Test public void 一列清单里的长度阈值一致() {
        assertEquals(3, MemoryFts.MIN_TRIGRAM_CHARS);
    }
}
