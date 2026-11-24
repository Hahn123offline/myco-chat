package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.models.ChatMessage;
import com.dsh.models.ToolCall;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TranscriptValidatorTest {

    private static ToolCall call(String id) { return new ToolCall(id, "t", "{}"); }

    @Test public void 合法转录通过() {
        List<ChatMessage> m = Arrays.asList(
                ChatMessage.system("s"),
                ChatMessage.user("u"),
                ChatMessage.assistant("", Arrays.asList(call("a"), call("b"))),
                ChatMessage.tool("a", "ok"), ChatMessage.tool("b", "ok"),
                ChatMessage.assistant("done", new ArrayList<>()));
        assertEquals("", TranscriptValidator.check(m));
        TranscriptValidator.assertLegal(m);
    }

    @Test public void 缺tool结果拒绝并报id() {
        List<ChatMessage> m = Arrays.asList(
                ChatMessage.assistant("", Arrays.asList(call("a"), call("b"))),
                ChatMessage.tool("a", "ok"));
        String err = TranscriptValidator.check(m);
        assertTrue(err, err.contains("b"));
        assertFalse(err.isEmpty());
    }

    @Test public void 多余tool结果拒绝() {
        List<ChatMessage> m = Arrays.asList(
                ChatMessage.assistant("", Arrays.asList(call("a"))),
                ChatMessage.tool("a", "ok"), ChatMessage.tool("x", "orphan"));
        assertTrue(TranscriptValidator.check(m).contains("x"));
    }

    @Test public void 图片夹在两个tool之间拒绝() {
        List<ChatMessage> m = Arrays.asList(
                ChatMessage.assistant("", Arrays.asList(call("a"), call("b"))),
                ChatMessage.tool("a", "ok"),
                ChatMessage.userImage("img", new byte[]{1}),
                ChatMessage.tool("b", "ok"));
        assertFalse(TranscriptValidator.check(m).isEmpty());
    }
}
