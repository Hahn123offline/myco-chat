package com.dsh.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 行级检索：轻量 BM25（不依赖 FTS），中文按 2-gram + 英文 token。 */
public final class LineSearch {

    private LineSearch() { }

    public static List<LineItem> search(List<LineItem> lines, String query, int limit) {
        List<LineItem> out = new ArrayList<>();
        if (lines == null || query == null || query.trim().isEmpty()) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> terms = terms(q);
        if (terms.isEmpty()) return out;
        double n = lines.size();
        java.util.Map<String, Integer> df = new java.util.HashMap<>();
        for (LineItem l : lines) {
            String t = l.text == null ? "" : l.text.toLowerCase(Locale.ROOT);
            for (String term : terms) if (t.contains(term)) df.merge(term, 1, Integer::sum);
        }
        for (LineItem l : lines) {
            String t = l.text == null ? "" : l.text.toLowerCase(Locale.ROOT);
            double score = 0;
            for (String term : terms) {
                if (!t.contains(term)) continue;
                int f = count(t, term);
                int d = df.getOrDefault(term, 1);
                double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
                score += idf * (f * 2.2) / (f + 1.2);
            }
            if (score > 0) {
                LineItem x = l;
                x.score = score;
                out.add(x);
            }
        }
        out.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            return c != 0 ? c : Long.compare(b.time, a.time);
        });
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** 精确命中为 0 时的汉字 2-gram 兜底（重叠≥0.6）。 */
    public static List<LineItem> fuzzy(List<LineItem> lines, String query, int limit) {
        List<LineItem> out = new ArrayList<>();
        if (lines == null || query == null) return out;
        java.util.Set<String> qg = cjkBigrams(query);
        if (qg.isEmpty()) return out;
        for (LineItem l : lines) {
            java.util.Set<String> lg = cjkBigrams(l.text);
            if (lg.isEmpty()) continue;
            int inter = 0;
            for (String g : qg) if (lg.contains(g)) inter++;
            double ratio = inter / (double) qg.size();
            if (ratio >= 0.6) {
                l.score = ratio;
                l.match = "fuzzy";
                out.add(l);
            }
        }
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    static java.util.Set<String> cjkBigrams(String s) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (s == null) return out;
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char c = i == s.length() ? 0 : s.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) { cjk.append(c); continue; }
            for (int j = 0; j + 2 <= cjk.length(); j++) out.add(cjk.substring(j, j + 2));
            cjk.setLength(0);
        }
        return out;
    }

    static List<String> terms(String q) {
        List<String> out = new ArrayList<>();
        StringBuilder en = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c >= 'a' && c <= 'z') { en.append(c); continue; }
            if (en.length() > 0) { if (en.length() >= 2) out.add(en.toString()); en.setLength(0); }
            if (c >= 0x4e00 && c <= 0x9fff) cjk.append(c);
            else {
                if (cjk.length() >= 2) addBigrams(cjk.toString(), out);
                cjk.setLength(0);
            }
        }
        if (en.length() >= 2) out.add(en.toString());
        if (cjk.length() >= 2) addBigrams(cjk.toString(), out);
        return out;
    }

    private static void addBigrams(String s, List<String> out) {
        for (int i = 0; i + 2 <= s.length(); i++) {
            String g = s.substring(i, i + 2);
            if (!out.contains(g)) out.add(g);
        }
    }

    private static int count(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
