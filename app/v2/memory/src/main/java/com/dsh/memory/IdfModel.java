package com.dsh.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从语料预计算的 IDF；简单内存结构，可序列化到 tsv。 */
public final class IdfModel {
    public final int n;
    public final Map<String, Double> idf = new HashMap<>();
    public final Map<String, Integer> df = new HashMap<>();
    public final double avgLen;
    public final double median;
    private final Map<String, Set<String>> gramCache = new java.util.concurrent.ConcurrentHashMap<>();

    private IdfModel(int n, Map<String, Double> idf, Map<String, Integer> df, double avgLen) {
        this.n = n;
        this.idf.putAll(idf);
        this.df.putAll(df);
        this.avgLen = avgLen;
        List<Double> vals = new ArrayList<>(idf.values());
        java.util.Collections.sort(vals);
        this.median = vals.isEmpty() ? 0 : vals.get(vals.size() / 2);
    }

    public static IdfModel fromTexts(List<String> texts) {
        Map<String, Integer> df = new HashMap<>();
        int n = 0;
        long lenSum = 0;
        for (String t : texts) {
            if (t == null || t.isEmpty()) continue;
            n++;
            lenSum += t.length();
            Set<String> grams = RecallRanker.grams(t);
            for (String g : grams) df.merge(g, 1, Integer::sum);
        }
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            out.put(e.getKey(), Math.log(Math.max(1, n) / (double) Math.max(1, e.getValue())));
        }
        IdfModel m = new IdfModel(n, out, df, n == 0 ? 1 : lenSum / (double) n);
        for (String t : texts) m.gramsFor(t); // 预热每篇文档的 grams，首查询不阻塞
        return m;
    }

    /** 标准 BM25 IDF：df=0 项贡献 0。 */
    public double bm25Idf(String term) {
        int d = df.getOrDefault(term, 0);
        if (d <= 0) return 0;
        return Math.log(1 + (n - d + 0.5) / (d + 0.5));
    }

    /** 每篇文档的 gram 集合只切一次（进程内缓存）。 */
    public Set<String> gramsFor(String text) {
        if (text == null) return java.util.Collections.emptySet();
        return gramCache.computeIfAbsent(text, RecallRanker::grams);
    }

    public double idf(String term) {
        Double v = idf.get(term);
        return v == null ? Math.log(Math.max(1, n)) : v;
    }

    /** 命中覆盖率与最大 IDF。 */
    public RecallRanker.Coverage coverage(Set<String> query, Set<String> text) {
        double total = 0, hit = 0, max = 0;
        for (String g : query) {
            double w = idf(g);
            total += w;
            if (text.contains(g)) {
                hit += w;
                if (w > max) max = w;
            }
        }
        return new RecallRanker.Coverage(total <= 0 ? 0 : hit / total, max);
    }
}
