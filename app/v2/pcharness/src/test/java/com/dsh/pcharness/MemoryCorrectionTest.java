package com.dsh.pcharness;

import static org.junit.Assert.*;

import com.dsh.agent.CorrectTool;
import com.dsh.agent.ForgetTool;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolResult;
import com.dsh.memory.Kind;
import com.dsh.memory.MemItem;
import com.dsh.memory.Evidence;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;

import org.junit.Test;

import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryCorrectionTest {

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test public void correct软删原块并新增修正块_forget后召回不到() throws Exception {
        java.nio.file.Path p = Files.createTempFile("memcorr", ".db");
        JdbcMemoryStore store = new JdbcMemoryStore(p.toString());
        store.registerNamespace("chat", true);
        long now = System.currentTimeMillis();
        MemItem m = new MemItem("chat", Kind.NOTE, "note:1", "我记错了", "{}");
        m.evidence = Collections.singletonList(new Evidence("chat", "x", now));
        m.t0 = m.t1 = now;
        m.detailLevel = com.dsh.memory.DetailLevel.FULL;
        long id = store.put(m);

        ToolResult cr = new CorrectTool(store).execute(
                args("memory_id", id, "new_text", "我修正后的内容", "reason", "用户纠正"),
                new ToolContext(null, new HashMap<>()));
        assertTrue(cr.error, cr.isOk());
        MemItem old = store.get(id);
        assertTrue(old.superseded);
        List<RecallHit> hits = RecallCore.recall(new JdbcMemorySource(p.toString()),
                new RecallQuery().query("修正").from("2000-01-01").to("2100-01-01"));
        boolean foundNew = false;
        for (RecallHit h : hits) {
            assertNotEquals("原块不应参与召回", id, h.itemId);
            if (h.text.contains("我修正后的内容")) foundNew = true;
        }
        assertTrue(foundNew);

        long newId = -1;
        for (MemItem x : store.query(new com.dsh.memory.Query().namespace("chat").limit(100))) {
            if (x.text.contains("我修正后的内容")) newId = x.id;
        }
        assertTrue(newId > 0);
        ToolResult fr = new ForgetTool(store).execute(args("memory_id", newId, "reason", "用户要求"),
                new ToolContext(null, new HashMap<>()));
        assertTrue(fr.error, fr.isOk());
        List<RecallHit> after = RecallCore.recall(new JdbcMemorySource(p.toString()),
                new RecallQuery().query("修正").from("2000-01-01").to("2100-01-01"));
        for (RecallHit h : after) assertNotEquals(newId, h.itemId);
        store.close();
    }

    @Test public void recall渲染带id() {
        MemItem m = new MemItem("chat", Kind.NOTE, null, "代理问题", "{}");
        m.id = 42;
        m.t0 = m.t1 = System.currentTimeMillis();
        m.detailLevel = com.dsh.memory.DetailLevel.FULL;
        com.dsh.memory.MemorySource src = q -> Collections.singletonList(m);
        List<RecallHit> hits = RecallCore.recall(src,
                new RecallQuery().query("代理").from("2000-01-01").to("2100-01-01"));
        assertTrue(RecallCore.render(hits).contains("id=42"));
    }
}
