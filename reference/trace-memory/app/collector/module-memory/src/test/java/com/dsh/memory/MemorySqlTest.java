package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 检索/打标 SQL 的纯逻辑单测（真库行为在 verify_pipeline.md 里上机验）。
 * 覆盖：中文子串模式、大小写不敏感表达式、时间倒序、LIKE 元字符转义、tier 过滤、
 * 待打标查询（SQL 里写死 WHITE）、label 字段匹配。
 */
public class MemorySqlTest {

    @Test public void 中文查询生成子串LIKE模式() {
        assertEquals("%影之刃零%", MemorySql.searchPattern("影之刃零"));
        assertEquals("%影之刃零%", MemorySql.searchPattern("  影之刃零  "));
    }

    @Test public void 英文大小写不敏感_匹配title正文与label() {
        assertTrue(MemorySql.SEARCH_WHERE.contains("LOWER(masked_text) LIKE LOWER(?)"));
        assertTrue(MemorySql.SEARCH_WHERE.contains("LOWER(title) LIKE LOWER(?)"));
        assertTrue("label 也要能搜到", MemorySql.SEARCH_WHERE.contains("COALESCE(label,'')"));
        assertEquals(3, MemorySql.SEARCH_PATTERN_COUNT);
    }

    @Test public void 时间倒序() {
        assertTrue(MemorySql.searchSql(10).contains("ORDER BY t_start DESC"));
        assertTrue(MemorySql.byEgressTierSql(10).contains("ORDER BY t_start DESC"));
    }

    @Test public void LIKE元字符按字面量转义() {
        String pattern = MemorySql.searchPattern("50%_off");
        assertTrue(pattern, pattern.contains("\\%"));
        assertTrue(pattern, pattern.contains("\\_"));
        assertTrue(MemorySql.searchSql(5).contains("ESCAPE '\\'"));
    }

    @Test public void 转义反斜杠本身() {
        assertEquals("a\\\\b", MemorySql.escapeLike("a\\b"));
    }

    @Test public void tier过滤SQL() {
        String sql = MemorySql.byEgressTierSql(20);
        assertTrue(sql, sql.contains("WHERE egress_tier=?"));
        assertTrue(sql, sql.contains("LIMIT 20"));
        assertTrue(MemorySql.countByEgressTierSql().contains("SELECT COUNT(*)"));
    }

    @Test public void 待打标查询在SQL层写死WHITE且排除SKIPPED() {
        String sql = MemorySql.blocksNeedingLabelSql(10);
        assertTrue(sql, sql.contains("egress_tier=?"));
        assertTrue(sql, sql.contains("label IS NULL"));
        assertTrue(sql, sql.contains("label_status<>3"));
        assertTrue(sql, sql.contains("WHERE"));
        assertTrue("从旧到新处理", sql.contains("ORDER BY t_start ASC"));
        assertTrue(sql.contains("LIMIT 10"));
        // 绝不能出现 WHITE 以外的档位
        assertFalse(sql.contains("egress_tier=1"));
    }

    @Test public void 打标状态计数SQL() {
        assertTrue(MemorySql.countByLabelStatusSql().contains("label_status=?"));
    }

    @Test public void limit下限保护() {
        assertTrue(MemorySql.searchSql(0).endsWith("LIMIT 1"));
        assertTrue(MemorySql.byEgressTierSql(-5).endsWith("LIMIT 1"));
        assertTrue(MemorySql.blocksNeedingLabelSql(0).endsWith("LIMIT 1"));
    }

    @Test public void 空查询模式也只是通配() {
        assertEquals("%%", MemorySql.searchPattern(""));
        assertEquals("%", MemorySql.searchPattern(null));
        assertFalse(MemorySql.searchPattern("中文").isEmpty());
    }
}
