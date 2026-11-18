package com.dsh.v2;

import com.dsh.agent.AgentTool;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolResult;
import com.dsh.memory.DayRecall;
import com.dsh.memory.MemorySource;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;
import com.dsh.models.Json;

import java.util.List;
import java.util.Map;

/** recall 的 Android 薄壳：解析参数 → RecallCore → 渲染。 */
public final class RecallTool implements AgentTool {

    private final MemorySource source;
    private final ChatProvenance provenance;
    private final com.dsh.memory.MaskProvider maskProvider;

    public RecallTool(MemorySource source, ChatProvenance provenance) {
        this(source, provenance, null);
    }

    public RecallTool(MemorySource source, ChatProvenance provenance,
                      com.dsh.memory.MaskProvider maskProvider) {
        this.source = source;
        this.provenance = provenance;
        this.maskProvider = maskProvider;
    }

    @Override public String name() { return "recall"; }

    @Override public String description() {
        return "检索记忆。query 关键词；from/to yyyy-MM-dd；pkg 包名；"
                + "level=block|day；limit。返回带 pkg/page/shot pointer 的文本。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\"},\"from\":{\"type\":\"string\"},"
                + "\"to\":{\"type\":\"string\"},\"pkg\":{\"type\":\"string\"},"
                + "\"level\":{\"type\":\"string\",\"enum\":[\"block\",\"day\"]},"
                + "\"limit\":{\"type\":\"integer\"}},\"required\":[]}";
    }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        RecallQuery q = new RecallQuery()
                .query(Json.string(args.get("query")))
                .from(Json.string(args.get("from")))
                .to(Json.string(args.get("to")))
                .pkg(Json.string(args.get("pkg")))
                .level(Json.string(args.get("level")))
                .limit(intArg(args.get("limit"), 8));
        if ("day".equals(q.level)) {
            DayRecall d = RecallCore.day(source, q.from);
            return ToolResult.ok(RecallCore.renderDay(d));
        }
        List<RecallHit> hits = RecallCore.recall(source, q);
        com.dsh.memory.RecallCore.Sanitized s = RecallCore.sanitize(hits, maskProvider);
        for (String p : s.sources) if (!provenance.sources.contains(p)) provenance.sources.add(p);
        for (String p : s.strippedSources) {
            if (!provenance.strippedSources.contains(p)) provenance.strippedSources.add(p);
        }
        if (!hits.isEmpty()) provenance.recalled = true;
        provenance.maskedMissing += s.maskedMissing;
        if (s.maskStatus() != null) provenance.maskStatus = s.maskStatus();
        return ToolResult.ok(RecallCore.render(s.hits));
    }

    private static int intArg(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) try { return Integer.parseInt((String) o); }
        catch (Throwable ignored) { }
        return def;
    }
}
