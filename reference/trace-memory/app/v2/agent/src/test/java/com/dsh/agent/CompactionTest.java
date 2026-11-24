package com.dsh.agent;

import static org.junit.Assert.*;

import com.dsh.models.ChatMessage;
import com.dsh.models.ToolCall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompactionTest {

    private static ChatMessage group(int i) {
        return ChatMessage.assistant("thinking " + i,
                Collections.singletonList(new ToolCall("c" + i, "t", "{}")), "");
    }

    private static List<ChatMessage> history(int groups) {
        List<ChatMessage> out = new ArrayList<>();
        out.add(ChatMessage.system("sys"));
        for (int i = 0; i < groups; i++) {
            out.add(group(i));
            out.add(ChatMessage.tool("c" + i, "result " + i));
            out.add(ChatMessage.user("x".repeat(800) + i));
        }
        return out;
    }

    @Test public void 超阈值压缩_组不切开_摘要头存在() {
        List<ChatMessage> in = history(30);
        Compaction.Result r = Compaction.maybe(in,
                req -> com.dsh.models.ModelResponse.ok("我回忆：之前在处理代理问题。", "m", "p"), null);
        assertTrue(r.ok);
        assertFalse(r.skipped);
        assertTrue(r.dropped > 0);
        assertEquals("", TranscriptValidator.check(r.messages));
        assertTrue(r.messages.get(1).content.contains("【早前对话的压缩摘要】"));
    }

    @Test public void 模型失败不丢消息_返回okfalse与原因() {
        List<ChatMessage> in = history(30);
        Compaction.Result r = Compaction.maybe(in, req -> {
            throw new RuntimeException("gateway 500");
        }, null);
        assertFalse(r.ok);
        assertTrue(r.error.contains("summarize-fail"));
        assertEquals(in, r.messages);
    }
    @Test public void 压缩后的摘要可在下一轮装配注回() {
        String[] saved = new String[1];
        Compaction.Result r = Compaction.maybe(history(30),
                req -> com.dsh.models.ModelResponse.ok("我回忆：代理问题", "m", "p"),
                (summary, dropped) -> saved[0] = summary);
        assertTrue(r.ok);
        assertFalse(r.skipped);
        MemoryAgentStore store = new MemoryAgentStore();
        store.append("s", "user", "继续");
        List<ChatMessage> out = new TranscriptBuilder(store, "s", "sys",
                new java.util.HashMap<>(), saved[0]).build();
        assertTrue(out.get(1).content.contains("【早前对话的压缩摘要】"));
        assertTrue(out.get(1).content.contains("代理问题"));
        assertEquals("", TranscriptValidator.check(out));
    }

}
