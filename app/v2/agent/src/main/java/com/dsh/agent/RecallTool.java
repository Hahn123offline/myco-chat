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
        return "统一检索入口。问某段时间做了什么 → 用 from/to，不要带 query；"
                + "query=内容（行级 BM25）；object=对象累积视图（群/视频等）；"
                + "pkg 包名；level=block|day；limit。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\"},\"from\":{\"type\":\"string\"},"
                + "\"to\":{\"type\":\"string\"},\"pkg\":{\"type\":\"string\"},"
                + "\"level\":{\"type\":\"string\",\"enum\":[\"block\",\"day\"]},"
                + "\"object\":{\"type\":\"string\"},"
                + "\"include_chrome\":{\"type\":\"boolean\"},"
                + "\"limit\":{\"type\":\"integer\"}},\"required\":[]}";
    }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        RecallQuery q = new RecallQuery()
                .query(Json.string(args.get("query")))
                .from(Json.string(args.get("from")))
                .to(Json.string(args.get("to")))
                .pkg(Json.string(args.get("pkg")))
                .level(Json.string(args.get("level")))
                .object(Json.string(args.get("object")))
                .limit(intArg(args.get("limit"), 8));
        if ("day".equals(q.level)) {
            return ToolResult.ok(RecallCore.renderDay(RecallCore.day(source, q.from)));
        }
        // 对象视图 / 行级内容视图：同一批 lines 的两种切法
        List<com.dsh.memory.LineItem> lines = source.lines(q);
        boolean includeChrome = Boolean.TRUE.equals(args.get("include_chrome"));
        if (!includeChrome) lines = contentOnly(lines);
        if (q.object != null && !q.object.isEmpty()) {
            if (lines.isEmpty()) {
                return ToolResult.ok("对象「" + q.object + "」没有行记录（可能尚未投影 lines，或不在本机）。");
            }
            return ToolResult.ok(renderLines(lines, q.limit));
        }
        boolean timeWord = q.query != null && q.query.matches(
                ".*(上午|下午|中午|晚上|凌晨|白天|昨天|今天|昨晚|今早).*");
        if ((q.query == null || q.query.isEmpty() || timeWord)
                && (q.from != null && !q.from.isEmpty() || q.to != null && !q.to.isEmpty())) {
            return ToolResult.ok(renderTimeView(lines, q.limit));
        }
        if (q.query != null && !q.query.isEmpty() && !lines.isEmpty()) {
            List<com.dsh.memory.LineItem> hits = com.dsh.memory.LineSearch.search(
                    lines, q.query, q.limit);
            if (hits.isEmpty()) {
                hits = com.dsh.memory.LineSearch.fuzzy(lines, q.query, q.limit);
            }
            if (!hits.isEmpty()) {
                if (provenance != null) {
                    for (com.dsh.memory.LineItem h : hits) {
                        if (h.pkg != null && !h.pkg.isEmpty()
                                && !provenance.sources().contains(h.pkg)
                                && !provenance.strippedSources().contains(h.pkg)) {
                            provenance.sources().add(h.pkg);
                        }
                    }
                    provenance.markRecalled();
                }
                if (trace != null) trace.accept("lines query=\"" + q.query
                        + "\" → " + hits.size() + " hits");
                return ToolResult.ok(renderLines(hits, q.limit));
            }
        }
        List<RecallHit> hits = RecallCore.recall(source, q);
        if (hits.isEmpty()) {
            String day = q.from != null && !q.from.isEmpty() ? q.from
                    : java.time.LocalDate.now().toString();
            try {
                com.dsh.memory.DayDiagnostic d = source.diagnose(day);
                if (d != null) return ToolResult.ok(d.render(q.query));
            } catch (Throwable ignored) { }
            return ToolResult.ok("没有找到记录。");
        }
        RecallCore.Sanitized s = RecallCore.sanitize(hits, maskProvider);
        if (provenance != null) {
            for (String p : s.sources) if (!provenance.sources().contains(p)) provenance.sources().add(p);
            for (String p : s.strippedSources) {
                if (!provenance.strippedSources().contains(p)) provenance.strippedSources().add(p);
            }
            if (!hits.isEmpty()) provenance.markRecalled();
            provenance.addMaskedMissing(s.maskedMissing);
            provenance.setMaskStatus(s.maskStatus());
        }
        if (trace != null) {
            String top = s.hits.isEmpty() ? "none"
                    : s.hits.get(0).namespace + "/" + s.hits.get(0).detailLevel + " "
                    + "id=" + s.hits.get(0).itemId + " score=" + String.format("%.2f", s.hits.get(0).score)
                    + " present=" + s.hits.get(0).present;
            trace.accept("query=\"" + q.query + "\" → " + s.hits.size() + " hits, top=" + top);
        }
        return ToolResult.ok(RecallCore.render(s.hits));
    }

    private static List<com.dsh.memory.LineItem> contentOnly(
            List<com.dsh.memory.LineItem> xs) {
        List<com.dsh.memory.LineItem> out = new java.util.ArrayList<>();
        for (com.dsh.memory.LineItem l : xs) {
            if ("CONTENT".equals(l.kind) || "TITLE".equals(l.kind)
                    || "SYSTEM".equals(l.kind)) out.add(l);
        }
        return out;
    }

    static String renderTimeView(List<com.dsh.memory.LineItem> lines, int limit) {
        if (lines.isEmpty()) return "这段时间没有可检索的 CONTENT 行。";
        java.util.Map<String, java.util.List<com.dsh.memory.LineItem>> byObj =
                new java.util.LinkedHashMap<>();
        for (com.dsh.memory.LineItem l : lines) {
            String k = l.objectKey == null || l.objectKey.isEmpty() ? l.pkg : l.objectKey;
            byObj.computeIfAbsent(k, x -> new java.util.ArrayList<>()).add(l);
        }
        StringBuilder b = new StringBuilder("时间视图：").append(lines.size()).append(" 行 / ")
                .append(byObj.size()).append(" 个对象\n");
        int objs = 0;
        for (java.util.Map.Entry<String, java.util.List<com.dsh.memory.LineItem>> e
                : byObj.entrySet()) {
            if (objs++ >= 8) break;
            java.util.List<com.dsh.memory.LineItem> xs = e.getValue();
            xs.sort((a, c) -> Long.compare(a.time, c.time));
            b.append("## ").append(e.getKey()).append(" (").append(xs.size()).append(" 行)\n");
            int n = 0;
            for (com.dsh.memory.LineItem l : xs) {
                if (n++ >= 6 || n > limit) break;
                b.append("  ").append(fmt(l.time)).append(' ')
                        .append(l.text == null ? "" : l.text).append("  [block ")
                        .append(l.blockId).append("]\n");
            }
        }
        return b.toString();
    }

    static String renderLines(List<com.dsh.memory.LineItem> lines, int limit) {
        StringBuilder b = new StringBuilder("命中 ").append(lines.size()).append(" 行：\n");
        java.util.Set<String> objs = new java.util.LinkedHashSet<>();
        for (com.dsh.memory.LineItem l : lines) {
            if (l.objectKey != null && !l.objectKey.isEmpty()) objs.add(l.objectKey);
        }
        if (!objs.isEmpty()) b.append("对象：").append(String.join("、", objs)).append('\n');
        int n = 0;
        for (com.dsh.memory.LineItem l : lines) {
            if (n++ >= limit) break;
            b.append("  ").append(fmt(l.time)).append(' ')
                    .append(l.text == null ? "" : l.text).append("  [block ")
                    .append(l.blockId).append(']');
            if ("fuzzy".equals(l.match)) b.append(" [match=fuzzy]");
            if (l.shotRef != null && !l.shotRef.isEmpty()) b.append(" [有截图]");
            b.append('\n');
        }
        return b.toString();
    }

    private static String fmt(long ms) {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(ms));
    }

    private static int intArg(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) try { return Integer.parseInt((String) o); }
        catch (Throwable ignored) { }
        return def;
    }
}
