package com.dsh.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 程序算缺口语义：>20 分钟的空洞。
 * presence（有没有任何记录）和 content（有没有画面/文本）分开算：
 *  - recording_off：用户关了记录
 *  - app_only：有 trace 但只有 pkg+时间（UsageStats），没有画面
 *  - no_record：完全不知道为什么不记录
 */
public final class GapFinder {

    public static final long MIN_GAP_MS = 20 * 60_000L;

    public static final class Span {
        public final long t0;
        public final long t1;
        public Span(long t0, long t1) { this.t0 = t0; this.t1 = t1; }
    }

    public static final class Known {
        public final long t0;
        public final long t1; // 0 = 未闭合
        public final String reason;
        public Known(long t0, long t1, String reason) {
            this.t0 = t0; this.t1 = t1; this.reason = reason;
        }
    }

    public static final class Gap {
        public final long t0;
        public final long t1;
        public final String reason;
        public final String text;
        public Gap(long t0, long t1, String reason) {
            this.t0 = t0; this.t1 = t1;
            this.reason = reason == null ? "no_record" : reason;
            this.text = fmt(t0) + "–" + fmt(t1) + " " + describe(this.reason)
                    + "（" + dur(t1 - t0) + "）";
        }
        private static String describe(String r) {
            if ("recording_off".equals(r)) return "你把我关了";
            if ("app_only".equals(r)) return "只有应用记录，没有画面";
            return "没有记录，我不知道为什么";
        }
    }

    private GapFinder() { }

    /** 兼容旧调用：presence 与 content 相同。 */
    public static List<Gap> find(List<Span> traces, List<Known> known,
                                 long dayStart, long dayEnd) {
        return find(traces, traces, known, dayStart, dayEnd);
    }

    public static List<Gap> find(List<Span> presence, List<Span> content,
                                 List<Known> known, long dayStart, long dayEnd) {
        List<Span> p = normalize(presence, dayStart, dayEnd);
        List<Span> c = normalize(content, dayStart, dayEnd);
        long effectiveStart = p.isEmpty() ? dayStart : p.get(0).t0;
        long effectiveEnd = p.isEmpty() ? dayEnd : p.get(p.size() - 1).t1;
        boolean hasOpenKnown = false;
        if (known != null) for (Known k : known) if (k.t1 <= 0) hasOpenKnown = true;
        if (hasOpenKnown) effectiveEnd = dayEnd;

        List<Gap> out = new ArrayList<>();
        long cursor = effectiveStart;
        for (Span s : p) {
            if (s.t0 - cursor > MIN_GAP_MS) out.add(gap(cursor, s.t0, known, "no_record"));
            cursor = Math.max(cursor, s.t1);
        }
        if (effectiveEnd - cursor > MIN_GAP_MS) {
            out.add(gap(cursor, effectiveEnd, known, "no_record"));
        }

        // presence 内没有 content 的时段 = app_only
        for (Span ps : p) {
            long cc = ps.t0;
            for (Span cs : c) {
                long a = Math.max(cs.t0, ps.t0), b = Math.min(cs.t1, ps.t1);
                if (b <= a) continue;
                if (a - cc > MIN_GAP_MS) out.add(new Gap(cc, a, "app_only"));
                cc = Math.max(cc, b);
            }
            if (ps.t1 - cc > MIN_GAP_MS) out.add(new Gap(cc, ps.t1, "app_only"));
        }

        // 已知 recording_off 是事实：整段报一次，压掉内部细碎缺口
        List<Gap> forced = new ArrayList<>();
        if (known != null) {
            for (Known k : known) {
                if (!"recording_off".equals(k.reason)) continue;
                long a = Math.max(k.t0, dayStart);
                long b = Math.min(k.t1 <= 0 ? dayEnd : k.t1, dayEnd);
                if (b - a <= MIN_GAP_MS) continue;
                forced.add(new Gap(a, b, "recording_off"));
            }
        }
        if (!forced.isEmpty()) {
            out.removeIf(g -> forced.stream().anyMatch(f -> f.t0 < g.t1 && g.t0 < f.t1));
            out.addAll(forced);
        }
        Collections.sort(out, Comparator.comparingLong(g -> g.t0));
        // 相邻的 app_only 缺口（中间只隔很短时间）合并成一段
        List<Gap> merged = new ArrayList<>();
        for (Gap g : out) {
            if (!merged.isEmpty()) {
                Gap prev = merged.get(merged.size() - 1);
                if ("app_only".equals(prev.reason) && "app_only".equals(g.reason)
                        && g.t0 - prev.t1 <= MIN_GAP_MS) {
                    merged.set(merged.size() - 1, new Gap(prev.t0, g.t1, "app_only"));
                    continue;
                }
            }
            merged.add(g);
        }
        return merged;
    }

    private static List<Span> normalize(List<Span> spans, long from, long to) {
        List<Span> out = new ArrayList<>();
        if (spans == null) return out;
        for (Span s : spans) {
            if (s == null) continue;
            long a = Math.max(s.t0, from);
            long b = Math.min(Math.max(s.t1, s.t0 + 1), to);
            if (b > a) out.add(new Span(a, b));
        }
        Collections.sort(out, Comparator.comparingLong(s -> s.t0));
        List<Span> merged = new ArrayList<>();
        for (Span s : out) {
            if (merged.isEmpty()) { merged.add(s); continue; }
            Span last = merged.get(merged.size() - 1);
            if (s.t0 <= last.t1) {
                merged.set(merged.size() - 1, new Span(last.t0, Math.max(last.t1, s.t1)));
            } else {
                merged.add(s);
            }
        }
        return merged;
    }

    private static Gap gap(long a, long b, List<Known> known, String def) {
        String reason = def;
        if (known != null) {
            for (Known k : known) {
                long kt1 = k.t1 <= 0 ? b : k.t1;
                if (k.t0 < b && kt1 > a) {
                    if ("recording_off".equals(k.reason)) { reason = "recording_off"; break; }
                    if ("app_only".equals(k.reason)) reason = "app_only";
                }
            }
        }
        return new Gap(a, b, reason);
    }

    public static String fmt(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
    }

    public static String dur(long ms) {
        long min = Math.max(0, ms / 60_000L);
        if (min >= 60) return (min / 60) + "h" + (min % 60) + "m";
        return min + "m";
    }
}
