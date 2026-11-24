package com.dsh.memory;

import com.dsh.host.api.data.MemoryBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LIKE 路径的相关度排序（Android 的 SQLite 没有 FTS5 时这是主路径）。
 *
 * 评分规则（478 条真实 episode / 40 条探针验证过：R@1 42.5% → 65.0%）：
 *   title 命中次数 × 10
 *   正文命中次数按长度归一：cb / (len(body)/100 + 1)，上限 5
 *   title 与 query 完全相等额外 +2
 * 排序：分数降序；同分按 t_start 降序（最近优先）。取前 limit。
 *
 * 纯逻辑、不碰 SQL/Android，可直接 JVM 单测。
 */
public final class LikeRanker {

    /** 候选池默认上限：比最终 limit 大得多，避免"最近 N 条"把相关结果截掉 */
    public static final int DEFAULT_CANDIDATE_CAP = 800;
    public static final int MAX_CANDIDATE_CAP = 5_000;

    static final double TITLE_WEIGHT = 10.0;
    static final double BODY_CAP = 5.0;
    /** label 命中权重：低于标题/正文，只用来"能搜到"，不喧宾夺主 */
    static final double LABEL_WEIGHT = 3.0;
    static final double TITLE_EXACT_BONUS = 2.0;

    private LikeRanker() { }

    public static final class Scored {
        public final MemoryBlock block;
        public final double score;

        Scored(MemoryBlock block, double score) {
            this.block = block;
            this.score = score;
        }
    }

    /** 单条打分（不修改 block） */
    public static double score(MemoryBlock b, String query) {
        if (b == null || query == null) return 0;
        String q = query.trim();
        if (q.isEmpty()) return 0;
        String qLower = q.toLowerCase(Locale.ROOT);

        int titleHits = countOccurrences(b.title, qLower);
        int bodyHits = countOccurrences(b.maskedText, qLower);
        // label 也能被搜到，但权重低（label 是云端摘要，不是原文证据）
        int labelHits = countOccurrences(b.label, qLower);
        double s = titleHits * TITLE_WEIGHT + labelHits * LABEL_WEIGHT;

        if (bodyHits > 0) {
            int bodyLen = b.maskedText == null ? 0 : b.maskedText.length();
            double normalized = bodyHits / (bodyLen / 100.0 + 1.0);
            s += Math.min(BODY_CAP, normalized);
        }
        if (b.title != null && b.title.trim().equalsIgnoreCase(q)) {
            s += TITLE_EXACT_BONUS;
        }
        return s;
    }

    /**
     * 对候选池打分排序后取前 limit。
     * 空 query 时全部 0 分 → 按时间倒序返回（与老行为一致）。
     */
    public static List<MemoryBlock> rank(List<MemoryBlock> candidates, String query, int limit) {
        int lim = Math.max(1, limit);
        List<MemoryBlock> out = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return out;

        List<Scored> scored = new ArrayList<>(candidates.size());
        for (MemoryBlock b : candidates) {
            if (b != null) scored.add(new Scored(b, score(b, query)));
        }
        scored.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            if (c != 0) return c;
            return Long.compare(b.block.tStart, a.block.tStart);   // 同分：时间倒序
        });
        for (int i = 0; i < scored.size() && i < lim; i++) out.add(scored.get(i).block);
        return out;
    }

    /** 出现次数（不区分大小写、不重叠） */
    public static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return 0;
        String h = haystack.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        int count = 0;
        int from = 0;
        while (from <= h.length() - n.length()) {
            int idx = h.indexOf(n, from);
            if (idx < 0) break;
            count++;
            from = idx + Math.max(1, n.length());
        }
        return count;
    }

    /** 候选池上限（配置越界时收敛到安全范围） */
    public static int clampCap(int cap) {
        return Math.max(1, Math.min(MAX_CANDIDATE_CAP, cap));
    }
}
