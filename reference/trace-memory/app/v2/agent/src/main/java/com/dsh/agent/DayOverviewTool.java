package com.dsh.agent;

import com.dsh.memory.DayDiagnostic;
import com.dsh.memory.MemorySource;
import com.dsh.models.Json;

import java.util.Map;

/** day_overview：当天痕迹数/覆盖/应用分布/事件/缺口。 */
public final class DayOverviewTool implements AgentTool {
    private final MemorySource source;
    public DayOverviewTool(MemorySource source) { this.source = source; }

    @Override public String name() { return "day_overview"; }
    @Override public String description() {
        return "查看某天的记录概况：痕迹数、有记录时长、应用分布、事件数、缺口段数。";
    }
    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\"}},"
                + "\"required\":[\"date\"]}";
    }
    @Override public String[] requiredArgs() { return new String[]{"date"}; }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String date = Json.string(args.get("date"));
        if (date == null || date.isEmpty()) date = java.time.LocalDate.now().toString();
        DayDiagnostic d = source.diagnose(date);
        if (d == null) return ToolResult.failed("无法读取当天概况");
        return ToolResult.ok(d.render(""));
    }
}
