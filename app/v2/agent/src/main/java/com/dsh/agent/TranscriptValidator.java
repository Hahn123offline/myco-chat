package com.dsh.agent;

import com.dsh.models.ChatMessage;
import com.dsh.models.ToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发送前本地校验：assistant 的每个 tool_calls 必须被紧随其后的 tool 消息一一对应。
 *
 * 这是硬不变量：不合法时绝不发到云端，错误信息精确到第 N 条 assistant 的 call_id。
 */
public final class TranscriptValidator {

    public static final class IllegalTranscriptException extends RuntimeException {
        public IllegalTranscriptException(String message) { super(message); }
    }

    private TranscriptValidator() { }

    public static void assertLegal(List<ChatMessage> messages) {
        String err = check(messages);
        if (!err.isEmpty()) throw new IllegalTranscriptException(err);
    }

    /** @return 空串 = 合法。 */
    public static String check(List<ChatMessage> messages) {
        if (messages == null) return "transcript 为 null";
        List<String> active = null;
        int activeAssistantIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (m == null) return "第 " + i + " 条消息为 null";
            if ("assistant".equals(m.role)) {
                if (active != null && !active.isEmpty()) {
                    return "第 " + activeAssistantIndex + " 条 assistant 的 call_id="
                            + active.get(0) + " 等 " + active.size() + " 个没有结果";
                }
                if (m.hasToolCalls()) {
                    active = new ArrayList<>();
                    for (ToolCall c : m.toolCalls) {
                        if (c == null || c.id == null || c.id.isEmpty()) {
                            return "第 " + i + " 条 assistant 含空 call_id";
                        }
                        active.add(c.id);
                    }
                    activeAssistantIndex = i;
                }
            } else if ("tool".equals(m.role)) {
                if (active == null) {
                    return "第 " + i + " 条 tool 结果 id=" + m.toolCallId
                            + " 没有对应的 assistant tool_calls";
                }
                if (m.toolCallId == null || m.toolCallId.isEmpty()) {
                    return "第 " + i + " 条 tool 结果缺少 tool_call_id";
                }
                int idx = active.indexOf(m.toolCallId);
                if (idx < 0) {
                    return "第 " + i + " 条 tool 结果 id=" + m.toolCallId
                            + " 不在第 " + activeAssistantIndex + " 条 assistant 的 tool_calls 中";
                }
                active.remove(idx);
                if (active.isEmpty()) active = null;
            } else {
                if (active != null && !active.isEmpty()) {
                    return "第 " + activeAssistantIndex + " 条 assistant 的 call_id="
                            + active.get(0) + " 等 " + active.size() + " 个没有结果";
                }
            }
        }
        if (active != null && !active.isEmpty()) {
            return "第 " + activeAssistantIndex + " 条 assistant 的 call_id="
                    + active.get(0) + " 等 " + active.size() + " 个没有结果";
        }
        return "";
    }
}
