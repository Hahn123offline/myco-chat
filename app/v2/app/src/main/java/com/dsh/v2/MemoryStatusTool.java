package com.dsh.v2;

import com.dsh.agent.AgentTool;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolResult;

import java.util.Map;

/** memory_status：块数/今天新增/来源/在场/最近/缺口。 */
public final class MemoryStatusTool implements AgentTool {

    private final AndroidMemorySource source;

    public MemoryStatusTool(AndroidMemorySource source) { this.source = source; }

    @Override public String name() { return "memory_status"; }

    @Override public String description() {
        return "查看记忆库概况：总数、今天新增、按来源、在场/不在场、最近写入、今日缺口。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        try {
            return ToolResult.ok(source.status());
        } catch (Throwable t) {
            return ToolResult.failed(t.getMessage());
        }
    }
}
