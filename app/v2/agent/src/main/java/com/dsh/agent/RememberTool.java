package com.dsh.agent;

import com.dsh.memory.MemoryStore;
import com.dsh.memory.MemoryWriter;
import com.dsh.models.Json;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** remember 工具纯逻辑：MemoryStore 由宿主实现。 */
public final class RememberTool implements AgentTool {

    private final MemoryStore store;
    private final String sessionId;
    private final String userMessage;

    public RememberTool(MemoryStore store, String sessionId, String userMessage) {
        this.store = store;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
    }

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
            store.registerNamespace("chat", true);
            store.registerNamespace("manual", true);
            String original = explicitOriginal(userMessage);
            String finalText = original.isEmpty() ? text : original;
            String summary = original.isEmpty() ? "" : text;
            long id = store.put(MemoryWriter.remember(finalText, kind, when, tags,
                    sessionId, 0, userMessage, summary));
            return ToolResult.ok("已记住（id=" + id + "，namespace="
                    + ("manual".equalsIgnoreCase(kind) ? "manual" : "chat") + "）。");
        } catch (Throwable t) {
            return ToolResult.failed(t.getMessage());
        }
    }

    /** 用户明确说“记一下/记住”时，text 保留用户原话（去掉命令词）。 */
    private static String explicitOriginal(String userMessage) {
        if (userMessage == null) return "";
        String s = userMessage.trim();
        String[] prefixes = {"帮我记一下", "记一下", "记住这个", "记住", "记下"};
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length()).trim();
                s = s.replaceFirst("^[：:，, ]+", "");
                return s;
            }
        }
        return "";
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
