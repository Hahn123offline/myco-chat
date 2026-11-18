package com.dsh.agent;

import com.dsh.memory.MemItem;
import com.dsh.memory.MemoryStore;
import com.dsh.models.Json;

import java.util.Map;

/** correct：新增修正记录，原块软删并保留可追溯链。 */
public final class CorrectTool implements AgentTool {

    private final MemoryStore store;

    public CorrectTool(MemoryStore store) { this.store = store; }

    @Override public String name() { return "correct"; }

    @Override public String description() {
        return "修正一条记错的记忆。新增修正记录，原记录保留但不参与召回；"
                + "参数 memory_id / new_text / reason。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"memory_id\":{\"type\":\"integer\"},"
                + "\"new_text\":{\"type\":\"string\"},"
                + "\"reason\":{\"type\":\"string\"}},\"required\":[\"memory_id\",\"new_text\"]}";
    }

    @Override public String[] requiredArgs() { return new String[]{"memory_id", "new_text"}; }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        long id = longArg(args.get("memory_id"), -1);
        String text = Json.string(args.get("new_text"));
        String reason = Json.string(args.get("reason"));
        if (id <= 0 || text == null || text.trim().isEmpty()) {
            return ToolResult.rejected("memory_id/new_text 无效");
        }
        try {
            MemItem old = store.get(id);
            if (old == null) return ToolResult.failed("记忆不存在: " + id);
            MemItem n = new MemItem(old.namespace, old.kind, null, text.trim(),
                    "{\"correction_of\":" + id + ",\"reason\":" + Json.quote(reason)
                            + ",\"summary\":" + Json.quote(old.text) + "}");
            n.evidence = old.evidence;
            n.weight = old.weight;
            n.t0 = old.t0;
            n.t1 = old.t1;
            n.detailLevel = old.detailLevel;
            long newId = store.put(n);
            String retired = (old.key == null || old.key.isEmpty() ? "id:" + id : old.key)
                    + "#superseded-by:" + newId;
            java.util.Map<String, Object> patch = new java.util.LinkedHashMap<>();
            patch.put("superseded_by", newId);
            patch.put("correction_reason", reason == null ? "" : reason);
            store.supersede(id, retired, patch);
            return ToolResult.ok("已修正：新 id=" + newId + "，原 id=" + id
                    + " 已保留为 superseded。");
        } catch (Throwable t) {
            return ToolResult.failed(t.getMessage());
        }
    }

    private static long longArg(Object o, long def) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) try { return Long.parseLong((String) o); }
        catch (Throwable ignored) { }
        return def;
    }
}
