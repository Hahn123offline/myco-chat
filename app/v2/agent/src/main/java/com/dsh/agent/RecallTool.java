package com.dsh.agent;

import com.dsh.memory.MaskProvider;
import com.dsh.memory.MemorySource;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;
import com.dsh.models.Json;

import java.util.List;
import java.util.Map;

/** recall 工具纯逻辑：Android/PC 共用。 */
public final class RecallTool implements AgentTool {

    private final MemorySource source;
    private final Provenance provenance;
    private final MaskProvider maskProvider;
    private final java.util.function.Consumer<String> trace;

    public RecallTool(MemorySource source, Provenance provenance) {
        this(source, provenance, null, null);
    }

    public RecallTool(MemorySource source, Provenance provenance, MaskProvider maskProvider) {
        this(source, provenance, maskProvider, null);
    }

    public RecallTool(MemorySource source, Provenance provenance, MaskProvider maskProvider,
                      java.util.function.Consumer<String> trace) {
        this.source = source;
        this.provenance = provenance;
        this.maskProvider = maskProvider;
        this.trace = trace;
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
            return ToolResult.ok(RecallCore.renderDay(RecallCore.day(source, q.from)));
        }
        List<RecallHit> hits = RecallCore.recall(source, q);
        RecallCore.Sanitized s = RecallCore.sanitize(hits, maskProvider);
        for (String p : s.sources) if (!provenance.sources().contains(p)) provenance.sources().add(p);
        for (String p : s.strippedSources) {
            if (!provenance.strippedSources().contains(p)) provenance.strippedSources().add(p);
        }
        if (!hits.isEmpty()) provenance.markRecalled();
        provenance.addMaskedMissing(s.maskedMissing);
        provenance.setMaskStatus(s.maskStatus());
        if (trace != null) {
            String top = s.hits.isEmpty() ? "none"
                    : s.hits.get(0).namespace + "/" + s.hits.get(0).detailLevel + " "
                    + "id=" + s.hits.get(0).itemId + " score=" + String.format("%.2f", s.hits.get(0).score)
                    + " present=" + s.hits.get(0).present;
            trace.accept("query=\"" + q.query + "\" → " + s.hits.size() + " hits, top=" + top);
        }
        return ToolResult.ok(RecallCore.render(s.hits));
    }

    private static int intArg(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) try { return Integer.parseInt((String) o); }
        catch (Throwable ignored) { }
        return def;
    }
}
