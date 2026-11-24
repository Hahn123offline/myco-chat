package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.dsh.models.ChatMessage;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;

public class TranscriptBuilderTrimTest {

    @Test public void 摘要注回系统提示之后对话之前() {
        MemoryAgentStore store = new MemoryAgentStore();
        store.append("s", "user", "初始指令");
        List<ChatMessage> out = new TranscriptBuilder(store, "s", "sys",
                new HashMap<>(), "之前在处理代理问题").build();
        assertEquals("system", out.get(0).role);
        assertTrue(out.get(1).content.contains("【早前对话的压缩摘要】"));
        assertTrue(out.get(1).content.contains("之前在处理代理问题"));
        assertEquals("", TranscriptValidator.check(out));
    }

    @Test public void 不再按40条静默截断() {
        MemoryAgentStore store = new MemoryAgentStore();
        store.append("s", "user", "初始指令");
        for (int i = 0; i < 30; i++) {
            String id = "c" + i;
            store.append("s", "assistant",
                    "{\"content\":\"\",\"tool_calls\":[{\"id\":\"" + id
                            + "\",\"name\":\"t\",\"arguments\":\"{}\"}]}");
            store.append("s", "tool_result",
                    "{\"tool_call_id\":\"" + id + "\",\"status\":\"OK\",\"summary\":\"ok\"}");
        }
        List<ChatMessage> out = new TranscriptBuilder(store, "s", "sys",
                new HashMap<>()).build();
        assertTrue("旧 40 条截断应已移除", out.size() > 40);
        assertEquals("", TranscriptValidator.check(out));
    }
}
