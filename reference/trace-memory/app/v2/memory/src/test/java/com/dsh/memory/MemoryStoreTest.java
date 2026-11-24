package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryStoreTest {

    private InMemoryMemoryStore store;

    @Before public void setup() {
        store = new InMemoryMemoryStore();
        store.registerNamespace("trace", true);
        store.registerNamespace("chat", true);
        store.registerNamespace("manual", true);
        store.registerNamespace("note", false);
    }

    private static MemItem item(String ns, String key, String text) {
        MemItem m = new MemItem(ns, Kind.BLOCK, key, text, "");
        m.t0 = 1000;
        return m;
    }

    private static Evidence ev(String type) {
        return new Evidence(type, "1", 1000L);
    }

    @Test public void key非空即upsert() {
        MemItem a = item("trace", "trace:1", "旧");
        a.evidence.add(ev("trace"));
        long id1 = store.put(a);
        MemItem b = item("trace", "trace:1", "新");
        b.evidence.add(ev("trace"));
        long id2 = store.put(b);
        assertEquals(id1, id2);
        assertEquals(1, store.query(new Query().namespace("trace")).size());
        assertEquals("新", store.get(id1).text);
    }

    @Test public void 空key每次新增() {
        MemItem a = item("note", null, "a");
        MemItem b = item("note", null, "b");
        long id1 = store.put(a);
        long id2 = store.put(b);
        assertTrue(id1 != id2);
        assertEquals(2, store.query(new Query().namespace("note")).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void text和data都空抛() {
        store.put(item("note", null, ""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void 未注册namespace抛() {
        store.put(item("ghost", null, "x"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireEvidence无证据抛() {
        store.put(item("trace", "trace:2", "黑屏 15 秒"));
    }

    @Test public void present五种组合() {
        MemItem t = item("trace", "a", "x"); t.evidence.add(ev("trace"));
        assertFalse(MemoryPolicy.present(t));
        MemItem b = item("trace", "b", "x"); b.evidence.add(ev("backfill"));
        assertFalse(MemoryPolicy.present(b));
        MemItem f = item("trace", "c", "x"); f.evidence.add(ev("feed"));
        assertFalse(MemoryPolicy.present(f));
        MemItem tc = item("trace", "d", "x");
        tc.evidence.add(ev("trace")); tc.evidence.add(ev("chat"));
        assertTrue(MemoryPolicy.present(tc));
        MemItem m = item("manual", "e", "x"); m.evidence.add(ev("manual"));
        assertTrue(MemoryPolicy.present(m));
    }

    @Test public void query过滤namespace_kind_时间窗_superseded() {
        MemItem a = item("trace", "trace:10", "a"); a.evidence.add(ev("trace")); a.t0 = 100;
        MemItem b = item("trace", "trace:11", "b"); b.evidence.add(ev("trace")); b.t0 = 500;
        b.kind = Kind.NOTE;
        MemItem c = item("note", null, "c"); c.t0 = 900;
        store.put(a); store.put(b); store.put(c);
        assertEquals(2, store.query(new Query().namespace("trace")).size());
        assertEquals(1, store.query(new Query().namespace("trace").kind(Kind.NOTE)).size());
        assertEquals(1, store.query(new Query().namespace("trace").window(400L, 1000L)).size());
        store.remove(a.id);
        assertEquals(1, store.query(new Query().namespace("trace")).size());
        assertTrue(store.query(new Query().namespace("trace").includeSuperseded(true)).size() >= 2);
    }

    @Test public void boost封顶且计数() {
        MemItem a = item("note", null, "a");
        long id = store.put(a);
        store.boost(id, 0.5, 1.2);
        assertEquals(1.2, store.get(id).weight, 1e-9);
        assertEquals(1, store.get(id).recallCount);
        assertTrue(store.get(id).lastRecalledAt > 0);
    }

    @Test public void patch合并data保留原字段() {
        MemItem a = new MemItem("note", Kind.NOTE, null, "", "{\"pkg\":\"x\",\"n\":1}");
        long id = store.put(a);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("n", 2);
        patch.put("extra", "y");
        store.patch(id, patch);
        MemItem got = store.get(id);
        assertTrue(got.data.contains("\"pkg\":\"x\""));
        assertTrue(got.data.contains("\"n\":2"));
        assertTrue(got.data.contains("\"extra\":\"y\""));
    }

    @Test public void remove软删_get仍能拿到() {
        MemItem a = item("note", null, "a");
        long id = store.put(a);
        store.remove(id);
        assertTrue(store.query(new Query().namespace("note")).isEmpty());
        assertNotNull(store.get(id));
        assertTrue(store.get(id).superseded);
    }

    @Test public void detailLevel与内容证据() {
        MemItem full = item("note", null, "a");
        MemItem img = item("trace", "trace:img", "");
        img.data = "{\"shot_ref\":\"x\"}"; img.detailLevel = DetailLevel.IMAGE_ONLY;
        MemItem app = item("trace", "trace:app", "");
        app.data = "{\"pkg\":\"x\"}"; app.detailLevel = DetailLevel.APP_ONLY;
        assertTrue(MemoryPolicy.contentEvidence(full));
        assertFalse(MemoryPolicy.contentEvidence(img));
        assertFalse(MemoryPolicy.contentEvidence(app));
    }

    @Test public void manual特殊策略() {
        assertTrue(MemoryPolicy.noDecay("manual"));
        assertTrue(MemoryPolicy.skipNoiseFilter("manual"));
        assertEquals(1.25, MemoryPolicy.recallBoost("manual"), 1e-9);
        assertFalse(MemoryPolicy.noDecay("trace"));
        assertEquals(1.0, MemoryPolicy.recallBoost("trace"), 1e-9);
    }

    @Test public void schema含唯一索引和证据列() {
        assertTrue(MemorySchema.CREATE_TABLE.contains("namespace"));
        assertTrue(MemorySchema.CREATE_TABLE.contains("evidence"));
        assertTrue(MemorySchema.CREATE_UNIQUE_KEY.contains("WHERE key IS NOT NULL"));
    }
}
