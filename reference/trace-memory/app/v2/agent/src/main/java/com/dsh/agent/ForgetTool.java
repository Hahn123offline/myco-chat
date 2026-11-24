package com.dsh.agent;

import com.dsh.memory.MemItem;
import com.dsh.memory.MemoryStore;
import com.dsh.models.Json;

import java.util.Map;

/** forget：软删，不物理删除。 */
public final class ForgetTool implements AgentTool {

    private final MemoryStore store;

    public ForgetTool(MemoryStore store) { this.store = store; }

    @Override public String name() { return "forget"; }

    @Override public String description() {
        return "忘掉一条记忆（软删，仍保留可追溯）。参数 memory_id / reason。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"memory_id\":{\"type\":\"integer\"},"
                + "\"reason\":{\"type\":\"string\"}},\"required\":[\"memory_id\"]}";
    }

    @Override public String[] requiredArgs() { return new String[]{"memory_id"}; }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        long id = longArg(args.get("memory_id"), -1);
        String reason = Json.string(args.get("reason"));
        if (id <= 0) return ToolResult.rejected("memory_id 无效");
        try {
            MemItem old = store.get(id);
            if (old == null) return ToolResult.failed("记忆不存在: " + id);
            java.util.Map<String, Object> patch = new java.util.LinkedHashMap<>();
            patch.put("forgotten_reason", reason == null ? "" : reason);
            store.supersede(id, null, patch);
            return ToolResult.ok("已忘掉 id=" + id + "（软删，原记录保留）。");
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
