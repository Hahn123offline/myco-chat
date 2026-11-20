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
        if (q.query != null && !q.query.isEmpty()) {
            com.dsh.memory.QueryTime.Range tr = com.dsh.memory.QueryTime.parse(
                    q.query, java.time.LocalDate.now());
            if (tr != null) {
                q.from = iso(tr.from);
                q.to = iso(tr.to);
                q.query = null;   // 纯时间视图
            }
        }
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
            int tc = source.traceCount(q.from, q.to);
            java.util.List<String> health = source.healthNotes(q.from, q.to);
            if (lines.isEmpty()) {
                if (tc == 0) return ToolResult.ok("这段时间没有痕迹：没有采集到任何记录。");
                return ToolResult.ok("有 " + tc + " 条痕迹，但没有可检索的 CONTENT 行；"
                        + "可能只有截图/无文本块，可先用 read_frame(block_id) 看原图。");
            }
            return ToolResult.ok(renderTimeView(lines, q.limit, tc, q.from, q.to, health));
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
        if (lines.isEmpty() && (q.from != null && !q.from.isEmpty()
                || q.to != null && !q.to.isEmpty())) {
            int tc = source.traceCount(q.from, q.to);
            if (tc > 0) {
                return ToolResult.ok("有 " + tc + " 条痕迹，但没有可检索的 CONTENT 行；"
                        + "可能只有截图/无文本块，可先用 read_frame(block_id) 看原图。");
            }
            return ToolResult.ok("这段时间没有痕迹：没有采集到任何记录。");
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
            if ("CONTENT".equals(l.kind)) out.add(l);
        }
        return out;
    }

    private static final long SEG_GAP_MS = 20 * 60_000L;
    /** 空段里只隔着一小段稀疏记录时，把它并进空段：<5min 且 <10 行，避免一条一秒记录切断大空段。 */
    private static final long SPARSE_SEG_MS = 5 * 60_000L;
    private static final int SPARSE_SEG_LINES = 10;
    private static final int MAX_SEGMENTS = 30;

    static String renderTimeView(List<com.dsh.memory.LineItem> lines, int limit) {
        return renderTimeView(lines, limit, 0, "", "");
    }

    private static final class Seg {
        long start, end;
        final java.util.List<com.dsh.memory.LineItem> xs = new java.util.ArrayList<>();
    }

    private static java.util.List<Seg> segments(java.util.List<com.dsh.memory.LineItem> xs) {
        java.util.List<Seg> out = new java.util.ArrayList<>();
        java.util.List<com.dsh.memory.LineItem> sorted = new java.util.ArrayList<>(xs);
        sorted.sort((a, b) -> Long.compare(a.time, b.time));
        for (com.dsh.memory.LineItem l : sorted) {
            Seg s = out.isEmpty() ? null : out.get(out.size() - 1);
            if (s == null || l.time - s.end > SEG_GAP_MS) {
                s = new Seg(); s.start = l.time; s.end = l.time; out.add(s);
            }
            s.xs.add(l);
            s.end = Math.max(s.end, l.time);
        }
        return out;
    }

    private static long[] rangeMs(String from, String to, java.util.List<com.dsh.memory.LineItem> lines) {
        long a = Long.MAX_VALUE, b = Long.MIN_VALUE;
        if (from != null && !from.isEmpty()) a = parseMs(from);
        if (to != null && !to.isEmpty()) b = parseMs(to);
        for (com.dsh.memory.LineItem l : lines) {
            if (l.time < a) a = l.time;
            if (l.time > b) b = l.time;
        }
        return new long[]{a, b};
    }

    private static long parseMs(String s) {
        try {
            String t = s.trim().replace(' ', 'T');
            if (t.length() > 10) {
                return java.time.LocalDateTime.parse(t.substring(0, 16))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            return java.time.LocalDate.parse(t).atStartOfDay(
                    java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Throwable t) { return 0; }
    }

    static String renderTimeView(List<com.dsh.memory.LineItem> lines, int limit,
                                 int traces, String from, String to) {
        return renderTimeView(lines, limit, traces, from, to,
                java.util.Collections.emptyList());
    }

    static String renderTimeView(List<com.dsh.memory.LineItem> lines, int limit,
                                 int traces, String from, String to,
                                 java.util.List<String> health) {
        if (lines.isEmpty()) return "这段时间没有可检索的 CONTENT 行。";
        long[] r = rangeMs(from, to, lines);
        long rangeStart = r[0] == Long.MAX_VALUE ? lines.get(0).time : r[0];
        long rangeEnd = r[1] == Long.MIN_VALUE ? lines.get(0).time : r[1];
        java.util.List<Seg> all = segments(lines);
        java.util.List<Seg> shownRaw = all.size() > MAX_SEGMENTS
                ? new java.util.ArrayList<>(all.subList(0, MAX_SEGMENTS)) : all;
        int omitted = all.size() - shownRaw.size();
        java.util.List<Seg> shown = new java.util.ArrayList<>();
        java.util.List<Seg> sparse = new java.util.ArrayList<>();
        for (Seg s : shownRaw) {
            if (s.end - s.start <= SPARSE_SEG_MS && s.xs.size() < SPARSE_SEG_LINES) {
                sparse.add(s);
            } else {
                shown.add(s);
            }
        }
        StringBuilder b = new StringBuilder();
        b.append("【覆盖】").append(fmt(rangeStart)).append("–").append(fmt(rangeEnd)).append("\n");
        b.append("  有记录段：");
        long covered = 0;
        for (int i = 0; i < shown.size(); i++) {
            Seg s = shown.get(i);
            covered += s.end - s.start;
            if (i > 0) b.append("｜");
            b.append(fmt(s.start)).append("–").append(fmt(s.end))
                    .append("（").append(s.xs.size()).append(" 行）");
        }
        if (shown.isEmpty()) b.append("无");
        b.append("\n  无记录段：");
        long cursor = rangeStart;
        java.util.List<long[]> gaps = new java.util.ArrayList<>();
        for (Seg s : shown) {
            if (s.start > cursor) gaps.add(new long[]{cursor, s.start});
            cursor = Math.max(cursor, s.end);
        }
        if (rangeEnd > cursor) gaps.add(new long[]{cursor, rangeEnd});
        for (int i = 0; i < gaps.size(); i++) {
            if (i > 0) b.append("｜");
            long[] g = gaps.get(i);
            b.append(fmt(g[0])).append("–").append(fmt(g[1]));
            java.util.List<Seg> inside = new java.util.ArrayList<>();
            for (Seg s : sparse) {
                if (s.start >= g[0] && s.end <= g[1]) inside.add(s);
            }
            if (!inside.isEmpty()) {
                b.append("（其中 ");
                for (int j = 0; j < inside.size(); j++) {
                    if (j > 0) b.append("、");
                    Seg sp = inside.get(j);
                    b.append(fmt(sp.start));
                    if (sp.end > sp.start) b.append("–").append(fmt(sp.end));
                    b.append(" 有 ").append(sp.xs.size()).append(" 条孤立记录：")
                            .append(sp.xs.get(0).text == null ? "" : sp.xs.get(0).text);
                    if (sp.xs.get(0).text != null && sp.xs.get(0).text.length() > 20) {
                        b.setLength(b.length());
                    }
                }
                b.append("）");
            }
        }
        if (gaps.isEmpty()) b.append("无");
        b.append("\n  合计：有记录 ").append(shown.size()).append(" 段 ")
                .append(covered / 60_000).append("min / 无记录 ")
                .append(gaps.size()).append(" 段 / 内容 ").append(lines.size()).append(" 行\n");
        if (omitted > 0) {
            b.append("【截断】按 ").append(MAX_SEGMENTS).append(" 段上限裁到 ")
                    .append(shown.size()).append(" 段；还有 ").append(omitted)
                    .append(" 段未显示。收窄：给 from/to 或指定 object。\n");
        } else {
            b.append("【完整】本时间段共 ").append(shown.size())
                    .append(" 段（含 ").append(sparse.size())
                    .append(" 条稀疏记录并入空段），已全部列出；不要重复查询同一范围。\n");
        }
        // R2：对象生命周期 + 段
        java.util.Map<String, java.util.List<com.dsh.memory.LineItem>> byObj =
                new java.util.LinkedHashMap<>();
        for (com.dsh.memory.LineItem l : lines) {
            String k = l.objectKey == null || l.objectKey.isEmpty() ? l.pkg : l.objectKey;
            byObj.computeIfAbsent(k, x -> new java.util.ArrayList<>()).add(l);
        }
        b.append("【对象】\n");
        int objCount = 0;
        for (java.util.Map.Entry<String, java.util.List<com.dsh.memory.LineItem>> e
                : byObj.entrySet()) {
            if (objCount++ >= 8) { b.append("  （对象已截断）\n"); break; }
            java.util.List<com.dsh.memory.LineItem> xs = e.getValue();
            xs.sort((a, c) -> Long.compare(a.time, c.time));
            java.util.List<Seg> os = segments(xs);
            b.append("  ").append(e.getKey()).append("：first=").append(fmt(xs.get(0).time))
                    .append(" last=").append(fmt(xs.get(xs.size() - 1).time))
                    .append(" ").append(os.size()).append(" 段 ").append(xs.size()).append(" 行\n");
            int segShow = 0;
            for (Seg s : os) {
                if (segShow++ >= 5) { b.append("    …段已截断\n"); break; }
                b.append("    段 ").append(fmt(s.start)).append("–").append(fmt(s.end))
                        .append("（").append(s.xs.size()).append(" 行）：");
                int n = 0;
                for (com.dsh.memory.LineItem l : s.xs) {
                    if (n++ >= 3) break;
                    b.append(l.text == null ? "" : l.text).append("；");
                }
                b.append("\n");
            }
        }
        b.append("【不知道时】这段无记录可以：①换时间段 ②看前后对象活动 ③按 app 查活跃段；"
                + "如果有痕迹无文本，用 read_block/read_frame 看原图。\n");
        if (health != null && !health.isEmpty()) {
            b.append("【心跳】\n");
            int hn = 0;
            for (String h : health) {
                if (hn++ >= 8) { b.append("  …已截断\n"); break; }
                b.append("  ").append(h).append("\n");
            }
        }
        b.append("【下钻】read_block(block_id) / read_frame(block_id)。\n");
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

    private static String iso(long ms) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(ms));
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
