package com.dsh.agent;

import java.util.Map;
import java.util.function.Supplier;

/** memory_status 工具：状态文本由宿主提供（缺口需要证据库，纯模块不碰文件）。 */
public final class MemoryStatusTool implements AgentTool {

    private final Supplier<String> status;

    public MemoryStatusTool(Supplier<String> status) { this.status = status; }

    @Override public String name() { return "memory_status"; }

    @Override public String description() {
        return "查看记忆库概况：总数、今天新增、按来源、在场/不在场、最近写入、今日缺口。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        try { return ToolResult.ok(status.get()); }
        catch (Throwable t) { return ToolResult.failed(t.getMessage()); }
    }
}
