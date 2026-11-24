package com.dsh.pcharness;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.dsh.agent.RecallTool;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolResult;

import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** 真机库结构集成测试：11-18 ChatGPT 必须 2 段 92 行，骨架/内容同源。 */
public class ChatgptCoverageTest {

    private static ToolResult recall(String pkg) {
        String path = System.getProperty("dsh.mem",
                "<workspace>/raw/v2/device/mem4.db");
        assumeTrue("真机库不存在，跳过", new File(path).exists());
        JdbcMemorySource src = new JdbcMemorySource(path);
        Map<String, Object> a = new HashMap<>();
        a.put("pkg", pkg);
        a.put("from", "2026-11-18");
        a.put("to", "2026-11-19 00:00");
        a.put("limit", 100);
        return new RecallTool(src, null).execute(a, new ToolContext(null, new HashMap<>()));
    }

    @Test public void 完整包名返回Chatgpt两段92行() {
        ToolResult r = recall("com.openai.chatgpt");
        assertTrue(r.summary, r.isOk());
        assertTrue(r.summary, r.summary.contains("10:23"));
        assertTrue(r.summary, r.summary.contains("12:01"));
        assertTrue(r.summary, r.summary.contains("92"));
    }

    @Test public void 模糊包名chatgpt也能匹配() {
        ToolResult r = recall("chatgpt");
        assertTrue(r.summary, r.isOk());
        assertTrue(r.summary, r.summary.contains("10:23"));
        assertTrue(r.summary, r.summary.contains("12:01"));
    }
}
