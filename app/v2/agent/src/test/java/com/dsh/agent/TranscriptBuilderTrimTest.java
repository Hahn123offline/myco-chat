package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.dsh.models.ChatMessage;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;

public class TranscriptBuilderTrimTest {

    @Test public void 裁剪后仍合法且不切半组() {
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
        assertTrue(out.size() <= 40);
        assertEquals("", TranscriptValidator.check(out));
        // 第一条 assistant 必须紧跟它的 tool，不能是被切断的组
        for (int i = 0; i < out.size(); i++) {
            ChatMessage m = out.get(i);
            if ("assistant".equals(m.role) && m.hasToolCalls()) {
                assertEquals("tool", out.get(i + 1).role);
                assertEquals(m.toolCalls.get(0).id, out.get(i + 1).toolCallId);
            }
        }
    }
}
