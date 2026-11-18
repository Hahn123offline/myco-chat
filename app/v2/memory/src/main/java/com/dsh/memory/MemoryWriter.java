package com.dsh.memory;

import com.dsh.models.Json;

import java.util.Collections;
import java.util.List;

/** remember 写入口：纯逻辑，Guard 通过才产生 MemItem。 */
public final class MemoryWriter {

    private MemoryWriter() { }

    public static MemItem remember(String text, String kind, Long when,
                                   List<String> tags, String sessionId, int turn,
                                   String userMessage) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text 为空");
        }
        com.dsh.guard.GuardResult gr = new com.dsh.guard.Guard().check(text);
        if (gr.isBlocked()) throw new IllegalArgumentException("guard BLOCK，禁止写入记忆");
        String k = kind == null ? "note" : kind.toLowerCase(java.util.Locale.ROOT);
        String ns = "manual".equals(k) ? MemoryPolicy.NS_MANUAL : "chat";
        long now = System.currentTimeMillis();
        long t = when != null ? when : now;
        MemItem m = new MemItem(ns, Kind.NOTE, null, text.trim(), data(sessionId, turn, tags, userMessage));
        m.evidence = Collections.singletonList(new Evidence("chat",
                (sessionId == null ? "chat" : sessionId) + "#" + turn, now));
        m.weight = 1.5;
        m.t0 = t;
        m.t1 = t;
        m.detailLevel = DetailLevel.FULL;
        m.createdAt = now;
        m.updatedAt = now;
        return m;
    }

    private static String data(String sessionId, int turn, List<String> tags, String userMessage) {
        StringBuilder b = new StringBuilder("{");
        b.append("\"session_id\":").append(Json.quote(sessionId == null ? "chat" : sessionId));
        b.append(",\"turn\":").append(turn);
        b.append(",\"tags\":[");
        if (tags != null) for (int i = 0; i < tags.size(); i++) {
            if (i > 0) b.append(',');
            b.append(Json.quote(tags.get(i)));
        }
        b.append("],\"user_message\":").append(Json.quote(userMessage == null ? "" : userMessage));
        return b.append('}').toString();
    }
}
