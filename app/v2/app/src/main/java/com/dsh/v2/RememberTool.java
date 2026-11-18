package com.dsh.v2;

import com.dsh.agent.AgentTool;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolResult;
import com.dsh.models.Json;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** remember：用户主动要记/承诺时写一条 chat NOTE（key=null，present=true）。 */
public final class RememberTool implements AgentTool {

    private final AndroidMemorySource source;

    public RememberTool(AndroidMemorySource source) { this.source = source; }

    @Override public String name() { return "remember"; }

    @Override public String description() {
        return "写入一条用户主动要求记住的内容。只有用户明确说“记一下/记住这个”"
                + "或提到未来要做/答应别人的事时才用；不要每轮都记。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\"},"
                + "\"kind\":{\"type\":\"string\",\"enum\":[\"note\",\"manual\"]},"
                + "\"when\":{\"type\":\"string\"},"
                + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},"
                + "\"required\":[\"text\"]}";
    }

    @Override public String[] requiredArgs() { return new String[]{"text"}; }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String text = Json.string(args.get("text"));
        if (text == null || text.trim().isEmpty()) return ToolResult.rejected("text 为空");
        String kind = Json.string(args.get("kind"));
        Long when = parseWhen(Json.string(args.get("when")));
        List<String> tags = new ArrayList<>();
        List<Object> arr = Json.asArray(args.get("tags"));
        if (arr != null) for (Object x : arr) {
            String s = Json.string(x);
            if (s != null && !s.isEmpty()) tags.add(s);
        }
        try {
            long id = source.remember(text, kind, when, tags, "chat", "");
            return ToolResult.ok("已记住（id=" + id + "，namespace="
                    + ("manual".equalsIgnoreCase(kind) ? "manual" : "chat") + "）。");
        } catch (Throwable t) {
            return ToolResult.failed(t.getMessage());
        }
    }

    private static Long parseWhen(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s.trim()).atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        } catch (Throwable t) {
            return null;
        }
    }
}
