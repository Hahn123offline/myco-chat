package com.dsh.memory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** recall v0 纯逻辑：过滤 + 打分 + 排序 + 渲染；零 Android。 */
public final class RecallCore {

    /** 上一次 recall 的分段耗时：候选查询 / IDF 构建 / 其余（逐条打分）。诊断用。 */
    public static volatile long lastCandidatesMs;
    public static volatile long lastIdfMs;
    public static volatile int lastCandCount;

    /** 低于此相似度视为“没有找到”，不拿近似结果糊。 */
    public static final double MIN_SIM = 0.05;

    private RecallCore() { }

    public static List<RecallHit> recall(MemorySource source, RecallQuery q) {
        if (q == null) q = new RecallQuery();
        if ("day".equals(q.level)) return new ArrayList<>(); // 用 day() 取
        LocalDate today = LocalDate.now();
        LocalDate from = parseDate(q.from, today.minusDays(7));
        LocalDate to = parseDate(q.to, today);
        long fromMs = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long toMs = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        long __t0 = System.currentTimeMillis();
        List<MemItem> candidates = source.candidates(q);
        long __t1 = System.currentTimeMillis();
        IdfModel idf = source.idf();
        long __t2 = System.currentTimeMillis();
        lastCandidatesMs = __t1 - __t0;
        lastIdfMs = __t2 - __t1;
        lastCandCount = candidates == null ? 0 : candidates.size();
        if (idf == null) {
            List<String> texts = new ArrayList<>();
            for (MemItem m : candidates) texts.add(m.text);
            idf = IdfModel.fromTexts(texts);
        }
        java.util.Set<String> qTerms = (q.query != null && !q.query.trim().isEmpty())
                ? RecallRanker.queryGrams(q.query) : java.util.Collections.emptySet();
        List<RecallHit> hits = new ArrayList<>();
        for (MemItem m : candidates) {
            if (m == null || m.superseded) continue;
            if ("compaction".equals(m.namespace)) continue;
            // health 心跳只用于按时间核对，默认不参与内容 BM25
            if ("health".equals(m.namespace) && q.query != null && !q.query.isEmpty()) continue;
            if (m.t0 < fromMs || m.t0 > toMs) continue;
            if (q.namespace != null && !q.namespace.isEmpty()
                    && !q.namespace.equals(m.namespace)) continue;
            Map<String, Object> data = data(m.data);
            String pkg = str(data.get("pkg"));
            if (q.pkg != null && !q.pkg.isEmpty() && !q.pkg.equals(pkg)) continue;
            // APP_ONLY 永远不能作为内容证据
            if (m.detailLevel == DetailLevel.APP_ONLY) continue;
            boolean hasQuery = q.query != null && !q.query.trim().isEmpty();
            if (m.detailLevel == DetailLevel.IMAGE_ONLY && hasQuery) continue;
            double sim;
            RecallRanker.Coverage cov = new RecallRanker.Coverage(1, 1);
            if (hasQuery) {
                cov = RecallRanker.coverage(q.query, m.text, idf);
                if (RecallRanker.MIN_COVERAGE > 0) {
                    if (cov.coverage < RecallRanker.MIN_COVERAGE) continue;
                    if (cov.maxIdf < idf.median) continue;
                }
                sim = cov.coverage;
            } else {
                sim = 0.0;
            }
            long ageDays = Math.max(0, (System.currentTimeMillis() - m.t1) / 86_400_000L);
            double decay = RecallRanker.decay(ageDays, m.namespace);
            double base = RecallRanker.tieBreak(q.query, m.text,
                    str(data.get("page_title")),
                    hasQuery ? RecallRanker.bm25(qTerms, m.text, idf) : 1.0);
            double score = base * m.weight * decay;
            if (MemoryPolicy.NS_MANUAL.equals(m.namespace)) score *= 1.25;
            RecallHit hit = new RecallHit(m.id, score, sim, m.weight, decay,
                    MemoryPolicy.present(m), m.detailLevel, m.namespace, m.text,
                    pkg, str(data.get("page_id")), str(data.get("shot_ref")),
                    str(data.get("page_title")), m.t0, str(data.get("masked_text")));
            hit.coverage = cov.coverage;
            hit.maxIdf = cov.maxIdf;
            hit.evidenceAvailable = source.evidenceAvailable(hit.shotRef);
            hit.t1 = m.t1;
            Object cm = data.get("covered_ms");
            hit.coveredMs = cm instanceof Number ? ((Number) cm).longValue() : 0;
            Object kb = data.get("key_block_ids");
            if (kb instanceof String) hit.blockIds = (String) kb;
            hits.add(hit);
        }
        hits.sort(Comparator.comparingDouble((RecallHit h) -> h.score).reversed()
                .thenComparingLong(h -> -h.t0)
                .thenComparingLong(h -> h.itemId));
        if (hits.size() > q.limit) return new ArrayList<>(hits.subList(0, q.limit));
        return hits;
    }

    public static DayRecall day(MemorySource source, String day) {
        if (source == null || day == null || day.isEmpty()) return null;
        DayRecall d = source.day(day);
        if (d != null) return d;
        for (MemItem m : source.candidates(new RecallQuery().limit(10000))) {
            if (m == null || !"day".equals(m.namespace) || !Kind.DAY.equals(m.kind)) continue;
            if (m.key != null && m.key.equals("day:" + day)) {
                String narrative = "";
                try {
                    Map<String, Object> o = data(m.data);
                    narrative = str(o.get("narrative"));
                } catch (Throwable ignored) { }
                return new DayRecall(day, narrative, "[]", "[]");
            }
        }
        return null;
    }

    public static final class Sanitized {
        public final List<RecallHit> hits;
        public final List<String> sources;
        public final List<String> strippedSources;
        public final int maskedMissing;
        public final int maskedSuccess;
        public final int fullMasked;
        public final int textOnlyMasked;
        public final int blocked;
        Sanitized(List<RecallHit> hits, List<String> sources,
                  List<String> strippedSources, int maskedMissing) {
            this(hits, sources, strippedSources, maskedMissing, 0, 0, 0, 0);
        }
        Sanitized(List<RecallHit> hits, List<String> sources,
                  List<String> strippedSources, int maskedMissing, int maskedSuccess) {
            this(hits, sources, strippedSources, maskedMissing, maskedSuccess, 0, 0, 0);
        }
        Sanitized(List<RecallHit> hits, List<String> sources,
                  List<String> strippedSources, int maskedMissing, int maskedSuccess,
                  int fullMasked, int textOnlyMasked, int blocked) {
            this.hits = hits; this.sources = sources;
            this.strippedSources = strippedSources; this.maskedMissing = maskedMissing;
            this.maskedSuccess = maskedSuccess;
            this.fullMasked = fullMasked; this.textOnlyMasked = textOnlyMasked;
            this.blocked = blocked;
        }
        public String maskStatus() {
            if (blocked > 0) return "BLOCKED";
            if (maskedMissing > 0) return "PLACEHOLDER";
            if (textOnlyMasked > 0) return "TEXT_ONLY_MASKED";
            if (fullMasked > 0) return "FULL_MASKED";
            return "";
        }
    }

    /** 兼容旧调用：没有运行时 Masker 时 REDACT 一律占位符。 */
    public static Sanitized sanitize(List<RecallHit> hits) {
        return sanitize(hits, null);
    }

    /** 注入 prompt 前按 pkg 分级：NEVER 占位、REDACT 运行时 Masker、NORMAL 原文。 */
    public static Sanitized sanitize(List<RecallHit> hits, MaskProvider masker) {
        List<RecallHit> out = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> stripped = new ArrayList<>();
        int missing = 0, success = 0, full = 0, textOnly = 0, blocked = 0;
        if (hits != null) for (RecallHit h : hits) {
            com.dsh.egress.AppExclusionPolicy.Level level =
                    com.dsh.egress.AppExclusionPolicy.level(h.pkg);
            if (level == com.dsh.egress.AppExclusionPolicy.Level.NEVER_CLOUD) {
                String cat = com.dsh.egress.AppExclusionPolicy.category(h.pkg);
                out.add(h.withText("[有一段敏感界面活动（" + cat + "），内容不展示]"));
                if (!stripped.contains(h.pkg)) stripped.add(h.pkg);
            } else if (level == com.dsh.egress.AppExclusionPolicy.Level.REDACT_THEN_CLOUD) {
                // guard BLOCK 不是“掩码”，整条必须占位符
                if (new com.dsh.guard.Guard().check(h.text).isBlocked()) {
                    out.add(h.withText("[敏感内容被 guard 拦截，已隐藏]"));
                    if (!stripped.contains(h.pkg)) stripped.add(h.pkg);
                    missing++;
                    blocked++;
                    continue;
                }
                Masked masked = masker == null ? null
                        : masker.maskedTextFor(h.itemId, h.pkg, h.t0, h.t0, h.text);
                if (masked != null && masked.usable()) {
                    out.add(h.withMask(masked.text, masked.mode));
                    if (h.pkg != null && !h.pkg.isEmpty() && !sources.contains(h.pkg)) {
                        sources.add(h.pkg);
                    }
                    success++;
                    if (masked.mode == MaskMode.FULL) full++;
                    else if (masked.mode == MaskMode.TEXT_ONLY) textOnly++;
                } else {
                    out.add(h.withText("[敏感内容缺少有效掩码文本，已隐藏]"));
                    if (!stripped.contains(h.pkg)) stripped.add(h.pkg);
                    missing++;
                }
            } else {
                out.add(h);
                if (h.pkg != null && !h.pkg.isEmpty() && !sources.contains(h.pkg)) {
                    sources.add(h.pkg);
                }
            }
        }
        return new Sanitized(out, sources, stripped, missing, success, full, textOnly, blocked);
    }

    /** 渲染成给人读的文本；空结果明确「没有找到记录」。 */
    public static String render(List<RecallHit> hits) {
        if (hits == null || hits.isEmpty()) return "没有找到记录。";
        StringBuilder b = new StringBuilder("找到 " + hits.size() + " 条：\n");
        for (RecallHit h : hits) {
            b.append("\n[id=").append(h.itemId).append("] [").append(fmt(h.t0))
                    .append("] ").append(h.pkg).append(" · ").append(h.pageTitle).append('\n');
            if (h.detailLevel == DetailLevel.IMAGE_ONLY) {
                b.append("  只有截图，没有识别到文字\n");
            } else {
                b.append("  ").append(shorten(h.text)).append('\n');
            }
            if ("event".equals(h.namespace) && h.coveredMs > 0) {
                long gap = (h.t1 - h.t0) - h.coveredMs;
                if (gap > 20 * 60_000L) {
                    if (!h.blockIds.isEmpty()) {
                        b.append("  （块 id：").append(h.blockIds)
                                .append("，核对细节可看原图）\n");
                    }
                    b.append("  （该事件时间跨度 ").append((h.t1 - h.t0) / 60_000)
                            .append(" 分钟，其中约 ").append(gap / 60_000)
                            .append(" 分钟无记录）\n");
                }
            }
            if (h.manual()) b.append("  （你当时特意让我记住这个）\n");
            else if (h.present) b.append("  （那会儿我们在聊这个）\n");
            else b.append("  （我翻记录看到的，那会儿我没在看）\n");
            if (h.shotRef != null && !h.shotRef.isEmpty() && !h.evidenceAvailable) {
                b.append("  （原始画面不在这台设备上）\n");
            } else {
                b.append("  pointer: pkg=").append(h.pkg)
                        .append(" page=").append(h.pageId)
                        .append(" shot=").append(h.shotRef).append('\n');
            }
        }
        return b.toString();
    }

    public static String renderDay(DayRecall d) {
        if (d == null) return "没有找到这一天的日叙事。";
        return "[" + d.day + "]\n" + d.narrative
                + "\nmainlines: " + d.mainlines + "\ngaps: " + d.gaps;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(String json) {
        try {
            Map<String, Object> m = com.dsh.models.Json.asObject(
                    com.dsh.models.Json.parse(json == null ? "{}" : json));
            return m == null ? java.util.Collections.emptyMap() : m;
        } catch (Throwable t) { return java.util.Collections.emptyMap(); }
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static LocalDate parseDate(String s, LocalDate def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return LocalDate.parse(s.trim()); } catch (Throwable t) { return def; }
    }

    private static String fmt(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format("%02d-%02d %02d:%02d",
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String x = s.replace('\n', ' ').trim();
        return x.length() <= 160 ? x : x.substring(0, 160) + "…";
    }
}
