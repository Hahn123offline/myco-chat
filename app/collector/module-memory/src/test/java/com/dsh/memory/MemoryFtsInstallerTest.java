package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 降级路径：用假 Db 模拟"FTS5 创建失败"的环境，验证自动回退 LIKE、
 * **失败原因可诊断**（不是只返回一个 LIKE）。
 */
public class MemoryFtsInstallerTest {

    private static final class FakeDb implements MemoryFtsInstaller.Db {
        final List<String> sql = new ArrayList<>();
        final Set<String> triggers = new HashSet<>();
        boolean ftsTableExists = false;
        Exception createError = null;
        Exception rebuildError = null;

        @Override public boolean tableExists(String table) { return ftsTableExists; }

        @Override public boolean triggerExists(String trigger) { return triggers.contains(trigger); }

        @Override public void execSQL(String sql) throws Exception {
            this.sql.add(sql);
            if (createError != null && sql.contains("CREATE VIRTUAL TABLE")) throw createError;
            if (rebuildError != null && sql.contains("'rebuild'")) throw rebuildError;
            if (sql.contains("CREATE TRIGGER")) {
                for (String name : MemoryFts.TRIGGER_NAMES) {
                    if (sql.contains(name)) triggers.add(name);
                }
            }
            if (sql.contains("DROP TABLE")) ftsTableExists = false;
        }

        boolean has(String needle) {
            for (String s : sql) if (s.contains(needle)) return true;
            return false;
        }
    }

    @Test public void 全新库_建表加三触发器加回填_引擎FTS5原因ok() {
        FakeDb db = new FakeDb();
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_FTS5, r.engine);
        assertEquals(MemoryFtsInstaller.REASON_OK, r.reason);
        assertTrue(r.isFts5());
        assertTrue(db.has("CREATE VIRTUAL TABLE"));
        assertTrue(db.has("AFTER INSERT ON blocks"));
        assertTrue(db.has("AFTER DELETE ON blocks"));
        assertTrue(db.has("AFTER UPDATE ON blocks"));
        assertTrue(db.has("'rebuild'"));
        assertEquals(3, db.triggers.size());
    }

    @Test public void 无FTS5模块_回退LIKE且原因fts5_unavailable() {
        FakeDb db = new FakeDb();
        db.createError = new Exception("no such module: fts5");
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_LIKE, r.engine);
        assertEquals(MemoryFtsInstaller.REASON_FTS5_UNAVAILABLE, r.reason);
        // 清理半成品：先删触发器再删虚表
        assertTrue(db.has("DROP TRIGGER IF EXISTS blocks_fts_ai"));
        assertTrue(db.has("DROP TABLE IF EXISTS blocks_fts"));
    }

    @Test public void 无trigram_tokenizer_回退LIKE且原因no_trigram() {
        FakeDb db = new FakeDb();
        db.createError = new Exception("unrecognized tokenizer: trigram");
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_LIKE, r.engine);
        assertEquals(MemoryFtsInstaller.REASON_NO_TRIGRAM, r.reason);
    }

    @Test public void 其它SQL错误_原因带原始message() {
        FakeDb db = new FakeDb();
        db.createError = new Exception("disk I/O error");
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_LIKE, r.engine);
        assertTrue(r.reason, r.reason.startsWith("sql-error:"));
        assertTrue(r.reason, r.reason.contains("disk I/O error"));
    }

    @Test public void 回填失败也回退_不半残() {
        FakeDb db = new FakeDb();
        db.rebuildError = new Exception("rebuild failed");
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_LIKE, r.engine);
        assertTrue(db.has("DROP TABLE IF EXISTS blocks_fts"));
    }

    @Test public void 表与触发器都在_不重复建表和回填() {
        FakeDb db = new FakeDb();
        db.ftsTableExists = true;
        for (String name : MemoryFts.TRIGGER_NAMES) db.triggers.add(name);
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_FTS5, r.engine);
        assertFalse(db.has("CREATE VIRTUAL TABLE"));
        assertFalse(db.has("'rebuild'"));
        assertTrue(db.has("IF NOT EXISTS"));
    }

    @Test public void 半迁移_表在但触发器缺_补触发器并回填修复() {
        FakeDb db = new FakeDb();
        db.ftsTableExists = true;
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(db);
        assertEquals(MemoryFtsInstaller.ENGINE_FTS5, r.engine);
        assertFalse(db.has("CREATE VIRTUAL TABLE"));
        assertTrue("必须回填，否则旧行检索不到", db.has("'rebuild'"));
        assertEquals(3, db.triggers.size());
    }

    @Test public void null数据库回退LIKE且原因可读() {
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(null);
        assertEquals(MemoryFtsInstaller.ENGINE_LIKE, r.engine);
        assertTrue(r.reason.startsWith("sql-error:"));
    }

    @Test public void 降级文案带原因_不能假装FTS5() {
        assertTrue(MemoryFtsInstaller.degradedDetail(MemoryFtsInstaller.ENGINE_FTS5,
                MemoryFtsInstaller.REASON_OK).contains("FTS5"));
        String d = MemoryFtsInstaller.degradedDetail(MemoryFtsInstaller.ENGINE_LIKE,
                MemoryFtsInstaller.REASON_FTS5_UNAVAILABLE);
        assertTrue(d, d.contains("LIKE"));
        assertTrue(d, d.contains("fts5-unavailable"));
        assertTrue(d, d.contains("回退"));
    }
}
