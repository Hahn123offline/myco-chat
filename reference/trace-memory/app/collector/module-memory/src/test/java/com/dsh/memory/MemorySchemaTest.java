package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * 迁移逻辑的纯逻辑护栏：不建真库、不碰 android.database，只断言"该执行哪些 SQL"。
 * 真库建表/迁移在真机上按 tools/verify_pipeline.md 验证。
 */
public class MemorySchemaTest {

    @Test public void v1升级到最新_补齐证据列_脱敏列_出网列_打标列() {
        List<String> sql = MemorySchema.upgradeSql(1);
        assertEquals(8, sql.size());
        assertTrue(sql.get(0).contains(MemorySchema.COL_EVIDENCE_REF));
        assertTrue(sql.get(1).contains(MemorySchema.COL_REDACTION_LEVEL));
        assertTrue(sql.get(2).contains(MemorySchema.COL_REDACTED_AT));
        assertTrue(sql.get(3).contains(MemorySchema.COL_EGRESS_TIER));
        assertTrue(sql.get(4).contains(MemorySchema.COL_LABEL));
        assertTrue(sql.get(5).contains(MemorySchema.COL_LABEL_MODEL));
        assertTrue(sql.get(6).contains(MemorySchema.COL_LABELED_AT));
        assertTrue(sql.get(7).contains(MemorySchema.COL_LABEL_STATUS));
        for (String s : sql) {
            assertTrue("必须是 ALTER TABLE ADD COLUMN: " + s, s.startsWith("ALTER TABLE blocks ADD COLUMN "));
        }
    }

    @Test public void v2升级到最新_补脱敏列出网列与打标列() {
        List<String> sql = MemorySchema.upgradeSql(2);
        assertEquals(7, sql.size());
        assertEquals(MemorySchema.ALTER_ADD_REDACTION_LEVEL, sql.get(0));
        assertEquals(MemorySchema.ALTER_ADD_REDACTED_AT, sql.get(1));
        assertEquals(MemorySchema.ALTER_ADD_EGRESS_TIER, sql.get(2));
        assertFalse(sql.toString().contains("ADD COLUMN " + MemorySchema.COL_EVIDENCE_REF + " "));
    }

    @Test public void v3升级到最新_补出网列与打标列() {
        List<String> sql = MemorySchema.upgradeSql(3);
        assertEquals(5, sql.size());
        assertEquals(MemorySchema.ALTER_ADD_EGRESS_TIER, sql.get(0));
        assertEquals(MemorySchema.ALTER_ADD_LABEL, sql.get(1));
    }

    @Test public void v4升级到v6_补打标列() {
        List<String> sql = MemorySchema.upgradeSql(4);
        assertEquals(4, sql.size());
        assertEquals(MemorySchema.ALTER_ADD_LABEL, sql.get(0));
        assertEquals(MemorySchema.ALTER_ADD_LABEL_MODEL, sql.get(1));
        assertEquals(MemorySchema.ALTER_ADD_LABELED_AT, sql.get(2));
        assertEquals(MemorySchema.ALTER_ADD_LABEL_STATUS, sql.get(3));
    }

    @Test public void v5升级到v6_只补打标列() {
        List<String> sql = MemorySchema.upgradeSql(5);
        assertEquals(4, sql.size());
        assertEquals(6, MemorySchema.DB_VER);
        // FTS 不是列：建表/触发器 SQL 在 MemoryFts，由 MemoryFtsInstaller 安装
        assertTrue(MemoryFts.CREATE.contains("blocks_fts"));
        assertEquals(3, MemoryFts.TRIGGERS.length);
    }

    @Test public void 已是最新版本不需要执行任何语句() {
        assertTrue(MemorySchema.upgradeSql(MemorySchema.DB_VER).isEmpty());
        assertTrue(MemorySchema.upgradeSql(99).isEmpty());
    }

    @Test public void 从v0升级也把列都补上_不跳版本() {
        List<String> sql = MemorySchema.upgradeSql(0);
        assertEquals(8, sql.size());
        assertTrue(sql.toString().contains(MemorySchema.COL_EVIDENCE_REF));
        assertTrue(sql.toString().contains(MemorySchema.COL_REDACTION_LEVEL));
        assertTrue(sql.toString().contains(MemorySchema.COL_REDACTED_AT));
        assertTrue(sql.toString().contains(MemorySchema.COL_EGRESS_TIER));
        assertTrue(sql.toString().contains(MemorySchema.COL_LABEL));
    }

    @Test public void 建表语句包含全部列_与升级后的形态一致() {
        String create = MemorySchema.CREATE_BLOCKS;
        assertTrue(create.contains(MemorySchema.COL_EVIDENCE_REF + " TEXT"));
        assertTrue(create.contains(MemorySchema.COL_REDACTION_LEVEL + " INTEGER NOT NULL DEFAULT 0"));
        assertTrue(create.contains(MemorySchema.COL_REDACTED_AT + " INTEGER"));
        assertTrue(create.contains(MemorySchema.COL_EGRESS_TIER + " INTEGER NOT NULL DEFAULT 0"));
        assertTrue(create.contains(MemorySchema.COL_LABEL + " TEXT"));
        assertTrue(create.contains(MemorySchema.COL_LABEL_MODEL + " TEXT"));
        assertTrue(create.contains(MemorySchema.COL_LABELED_AT + " INTEGER"));
        assertTrue(create.contains(MemorySchema.COL_LABEL_STATUS + " INTEGER NOT NULL DEFAULT 0"));
        // 既有列不能被弄丢（旧查询还在用）
        for (String col : new String[]{"episode_id", "t_start", "t_end", "pkg", "title",
                "masked_text", "privacy_tier", "hit_summary", "schema_ver"}) {
            assertTrue("建表缺列 " + col, create.contains(col));
        }
    }

    @Test public void 兜底列清单覆盖全部列且每条都是ALTER() {
        Map<String, String> ddls = MemorySchema.COLUMN_DDL;
        assertEquals(8, ddls.size());
        for (String col : new String[]{MemorySchema.COL_EVIDENCE_REF,
                MemorySchema.COL_REDACTION_LEVEL, MemorySchema.COL_REDACTED_AT,
                MemorySchema.COL_EGRESS_TIER, MemorySchema.COL_LABEL,
                MemorySchema.COL_LABEL_MODEL, MemorySchema.COL_LABELED_AT,
                MemorySchema.COL_LABEL_STATUS}) {
            assertTrue("缺兜底列 " + col, ddls.containsKey(col));
            assertTrue(ddls.get(col).startsWith("ALTER TABLE blocks ADD COLUMN " + col));
        }
    }

    @Test public void 两个维度的默认值都是保守的() {
        assertTrue(MemorySchema.ALTER_ADD_REDACTION_LEVEL.endsWith("DEFAULT 0"));
        assertTrue(MemorySchema.ALTER_ADD_EGRESS_TIER.endsWith("DEFAULT 0"));
        assertTrue("新块默认 PENDING，不会漏打标", MemorySchema.ALTER_ADD_LABEL_STATUS.endsWith("DEFAULT 0"));
        assertEquals(0, com.dsh.host.api.data.RedactionLevel.RULE);
        assertEquals(1, com.dsh.host.api.data.RedactionLevel.MODEL);
        // egress tier 的 code 显式落库：0=UNKNOWN(旧行)，绝不能把旧行当 WHITE
        assertEquals(0, com.dsh.host.api.memory.EgressTier.UNKNOWN.code);
        assertEquals(1, com.dsh.host.api.memory.EgressTier.WHITE.code);
        assertEquals(2, com.dsh.host.api.memory.EgressTier.GRAY.code);
        assertEquals(3, com.dsh.host.api.memory.EgressTier.BLACK.code);
        // 打标状态：0=PENDING 1=OK 2=FAILED 3=SKIPPED
        assertEquals(0, com.dsh.host.api.data.LabelStatus.PENDING);
        assertEquals(1, com.dsh.host.api.data.LabelStatus.OK);
        assertEquals(2, com.dsh.host.api.data.LabelStatus.FAILED);
        assertEquals(3, com.dsh.host.api.data.LabelStatus.SKIPPED);
    }
}
