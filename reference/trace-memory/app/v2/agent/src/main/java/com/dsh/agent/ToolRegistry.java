package com.dsh.agent;

import com.dsh.models.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表 + 校验层。
 * 未知工具 / 参数不是 JSON 对象 / 缺 required 字段 → 都返回错误结果（不是异常）。
 */
public final class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry add(AgentTool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public AgentTool get(String name) { return tools.get(name); }

    public List<AgentTool> all() { return new ArrayList<>(tools.values()); }

    public List<com.dsh.models.ToolSpec> specs() {
        List<com.dsh.models.ToolSpec> out = new ArrayList<>();
        for (AgentTool t : tools.values()) {
            out.add(new com.dsh.models.ToolSpec(t.name(), t.description(), t.parametersJson()));
        }
        return out;
    }

    /** 校验并执行；任何异常都转成 FAILED 结果。 */
    public ToolResult execute(String name, String argumentsJson, ToolContext ctx) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failed("未知工具: " + name + "（可用: " + tools.keySet() + "）");
        }
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) argumentsJson = "{}";
        Object parsed;
        try {
            parsed = Json.parse(argumentsJson);
        } catch (Throwable t) {
            return ToolResult.failed("参数不是合法 JSON: " + t.getMessage());
        }
        Map<String, Object> args = Json.asObject(parsed);
        if (args == null) return ToolResult.failed("参数必须是 JSON object");
        for (String req : tool.requiredArgs()) {
            if (!args.containsKey(req) || args.get(req) == null) {
                return ToolResult.failed("缺少必填参数: " + req);
            }
        }
        try {
            ToolResult r = tool.execute(args, ctx);
            return r == null ? ToolResult.failed("工具返回 null") : r;
        } catch (Throwable t) {
            String m = t.getMessage();
            return ToolResult.failed(t.getClass().getSimpleName()
                    + (m == null || m.isEmpty() ? "" : ": " + m));
        }
    }
}
