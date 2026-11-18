package com.dsh.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/** append-only 会话事件（真源）。payload 用 JSON 字符串保存。 */
public final class AgentEvent {

    public final long seq;
    public final String type;       // user | assistant | tool_result | error
    public final String payload;
    public final long atMs;

    public AgentEvent(long seq, String type, String payload, long atMs) {
        this.seq = seq;
        this.type = type == null ? "" : type;
        this.payload = payload == null ? "{}" : payload;
        this.atMs = atMs;
    }

    @Override public String toString() {
        return "#" + seq + " " + type + " " + payload;
    }

    public static Map<String, Object> payloadMap(String type, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("content", content);
        return m;
    }
}
