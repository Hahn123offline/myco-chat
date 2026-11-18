package com.dsh.memory;

/** recall v0 打分：sim × weight × decay（manual 额外 1.25）。纯逻辑。 */
public final class RecallRanker {

    /** 小固定虚词表；切 2-gram 前先剥掉这些字符。 */
    public static final String STOP = "的了吗呢啊吧那这我你他在是有个把被就都也还哪么什一";
    public static final double MIN_COVERAGE = 0.0;

    public static final class Coverage {
        public final double coverage;
        public final double maxIdf;
        public Coverage(double c, double m) { coverage = c; maxIdf = m; }
    }

    private RecallRanker() { }

    /** 内容 2-gram + 英文/数字 token。 */
    public static java.util.Set<String> grams(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (s == null) return out;
        String x = s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i + 2 <= x.length(); i++) {
            String g = x.substring(i, i + 2);
            if (!g.replaceAll("[\\p{Punct}\\s]", "").isEmpty()) out.add(g);
        }
        for (String tok : s.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9_]+")) {
            if (tok.length() >= 2) out.add(tok);
        }
        return out;
    }

    /** 查询归一：去虚词字符后再切 2-gram。 */
    public static java.util.Set<String> queryGrams(String query) {
        if (query == null) return new java.util.HashSet<>();
        StringBuilder b = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (STOP.indexOf(c) >= 0) continue;
            b.append(c);
        }
        return grams(b.toString());
    }

    public static Coverage coverage(String query, String text, IdfModel idf) {
        if (query == null || query.trim().isEmpty()) return new Coverage(1, 1);
        String q = query.replaceAll("\\s+", "");
        if (q.length() >= 2 && text != null && text.contains(q)) {
            return new Coverage(1, idf == null ? 1 : Math.max(1, idf.idf(q)));
        }
        java.util.Set<String> qs = queryGrams(query);
        if (idf == null) return new Coverage(0, 0);
        return idf.coverage(qs, grams(text));
    }

    public static double similarity(String query, String text) {
        if (query == null || query.trim().isEmpty()) return 1.0;
        String q = norm(query), t = norm(text == null ? "" : text);
        if (t.isEmpty()) return 0.0;
        double j = jaccard2gram(q, t);
        double hit = t.contains(q) ? 1.0 : 0.0;
        return 0.7 * j + 0.3 * hit;
    }

    public static double decay(long ageDays, String namespace) {
        if (MemoryPolicy.NS_MANUAL.equals(namespace)) return 1.0; // 不衰减
        if ("event".equals(namespace)) return Math.exp(-Math.max(0, ageDays) / 60.0);
        return Math.exp(-Math.max(0, ageDays) / 180.0);
    }

    public static double score(String query, String text, double weight,
                               long ageDays, String namespace) {
        double s = similarity(query, text) * weight * decay(ageDays, namespace);
        if (MemoryPolicy.NS_MANUAL.equals(namespace)) s *= 1.25;
        return s;
    }

    public static final double BM25_K1 = 1.2;
    public static final double BM25_B = 0.75;

    /** 标准 BM25 项求和；df=0 的 query term 贡献 0，不进分母。 */
    public static double bm25(String query, String text, IdfModel idf) {
        return bm25(queryGrams(query), text, idf);
    }

    public static double bm25(java.util.Set<String> terms, String text, IdfModel idf) {
        if (terms == null || terms.isEmpty()) return 1.0;
        if (idf == null || text == null) return 0;
        java.util.Set<String> all = idf.gramsFor(text);
        double len = Math.max(1, text.length());
        double norm = 1 - BM25_B + BM25_B * (len / Math.max(1, idf.avgLen));
        double score = 0;
        for (String g : terms) {
            if (!all.contains(g)) continue;
            int c = count(text, g);
            if (c <= 0) continue;
            double idfV = idf.bm25Idf(g);
            if (idfV <= 0) continue;
            double sat = c * (BM25_K1 + 1) / (c + BM25_K1);
            score += idfV * sat / norm;
        }
        return score;
    }

    /** LikeRanker 连续 tie-breaker：titleBoost + density + phraseBonus。 */
    public static double tieBreak(String query, String text, String title, double coverage) {
        if (query == null || query.trim().isEmpty()) return coverage;
        String[] words = query.trim().split("\\s+");
        double titleBoost = 0, density, phraseBonus = 0;
        int hits = 0;
        String q = query.replaceAll("\\s+", "");
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            double weight = w.length() == 1 ? 0.2 : 1.0;
            if (title != null && title.contains(w)) titleBoost = 0.15;
            int c = count(text, w);
            hits += (int) (c * weight);
        }
        density = Math.min(0.20, hits / (Math.max(1, text == null ? 1 : text.length()) / 100.0 + 1));
        if (q.length() >= 4 && text != null && text.contains(q)) phraseBonus = 0.10;
        return coverage + titleBoost + density + phraseBonus;
    }

    private static int count(String text, String w) {
        if (text == null || w == null || w.isEmpty()) return 0;
        int n = 0, i = 0;
        while ((i = text.indexOf(w, i)) >= 0) { n++; i += Math.max(1, w.length()); }
        return n;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static double jaccard2gram(String a, String b) {
        java.util.Set<String> x = grams(a), y = grams(b);
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        java.util.Set<String> inter = new java.util.HashSet<>(x);
        inter.retainAll(y);
        java.util.Set<String> uni = new java.util.HashSet<>(x);
        uni.addAll(y);
        return uni.isEmpty() ? 0.0 : (double) inter.size() / uni.size();
    }

    private static java.util.Set<String> unusedGrams(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (int i = 0; i + 2 <= s.length(); i++) out.add(s.substring(i, i + 2));
        return out;
    }
}


