package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.dsh.models.ChatMessage;
import com.dsh.models.Json;

import org.junit.Test;

import java.util.List;

/** llm_view 修复：缺 result 补「未执行」，孤儿 result 丢弃。 */
public class TranscriptBuilderTest {

    private static String assistant(String callId, String name) {
        return "{\"content\":\"\",\"tool_calls\":[{\"id\":" + Json.quote(callId)
                + ",\"name\":" + Json.quote(name) + ",\"arguments\":\"{}\"}]}";
    }

    @Test public void 有call无result时补未执行() {
        MemoryAgentStore store = new MemoryAgentStore();
        store.append("s", "user", "hello");
        store.append("s", "assistant", assistant("c1", "read_frame"));
        List<ChatMessage> msgs = new TranscriptBuilder(store, "s", "sys", null).build();
        boolean sawNotExecuted = false;
        for (ChatMessage m : msgs) {
            if ("tool".equals(m.role) && m.toolCallId.equals("c1")
                    && m.content.contains("NOT_EXECUTED")) sawNotExecuted = true;
        }
        assertTrue("缺失 result 必须补 NOT_EXECUTED", sawNotExecuted);
        assertEquals("每个 call 恰好一条 result",
                1, msgs.stream().filter(m -> "tool".equals(m.role)).count());
    }

    @Test public void 孤儿result被丢弃() {
        MemoryAgentStore store = new MemoryAgentStore();
        store.append("s", "user", "hello");
        store.append("s", "tool_result",
                "{\"tool_call_id\":\"ghost\",\"status\":\"OK\",\"summary\":\"x\"}");
        List<ChatMessage> msgs = new TranscriptBuilder(store, "s", "sys", null).build();
        assertEquals(0, msgs.stream().filter(m -> "tool".equals(m.role)).count());
    }

    @Test public void 正常配对_call后紧跟result() {
        MemoryAgentStore store = new MemoryAgentStore();
        store.append("s", "user", "hello");
        store.append("s", "assistant", assistant("c1", "list_blocks"));
        store.append("s", "tool_result",
                "{\"tool_call_id\":\"c1\",\"status\":\"OK\",\"summary\":\"blocks\"}");
        List<ChatMessage> msgs = new TranscriptBuilder(store, "s", "sys", null).build();
        int ai = -1, ti = -1;
        for (int i = 0; i < msgs.size(); i++) {
            if ("assistant".equals(msgs.get(i).role)) ai = i;
            if ("tool".equals(msgs.get(i).role)) ti = i;
        }
        assertTrue(ai >= 0 && ti == ai + 1);
    }

    @Test public void 裁剪从user边界开始_不裁开call_result() {
        MemoryAgentStore store = new MemoryAgentStore();
        for (int i = 0; i < 30; i++) {
            store.append("s", "user", "u" + i);
            store.append("s", "assistant", assistant("c" + i, "a"));
            store.append("s", "tool_result",
                    "{\"tool_call_id\":\"c" + i + "\",\"status\":\"OK\",\"summary\":\"x\"}");
        }
        List<ChatMessage> msgs = new TranscriptBuilder(store, "s", "sys", null).build();
        assertTrue("旧40条截断已移除", msgs.size() > 40);
        for (int i = 0; i < msgs.size(); i++) {
            if ("tool".equals(msgs.get(i).role)) {
                assertTrue("tool 前必须是 assistant 或另一个 tool",
                        i > 0 && ("assistant".equals(msgs.get(i - 1).role)
                                || "tool".equals(msgs.get(i - 1).role)));
            }
        }
    }
}
