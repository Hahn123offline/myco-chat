package com.dsh.usage;

import java.util.List;

/**
 * 两路去重：同一 pkg + 时间重叠 > 80% 的 usage 痕迹不再写（a11y 已经记过）。
 * 纯逻辑、可单测。
 */
public final class UsageDeduper {

    public static final double OVERLAP_RATIO = 0.80;

    /** a11y 已覆盖的区间 */
    public static final class Covered {
        public final String pkg;
        public final long t0;
        public final long t1;

        public Covered(String pkg, long t0, long t1) {
            this.pkg = pkg == null ? "" : pkg;
            this.t0 = t0;
            this.t1 = t1;
        }
    }

    private UsageDeduper() { }

    public static boolean shouldSkip(UsageSampler.Span span, List<Covered> covered) {
        if (span == null || span.t1 <= span.t0) return true;
        if (covered == null) return false;
        long len = span.t1 - span.t0;
        for (Covered c : covered) {
            if (c == null || !span.pkg.equals(c.pkg)) continue;
            long overlap = Math.min(span.t1, c.t1) - Math.max(span.t0, c.t0);
            if (overlap > 0 && overlap >= (long) (len * OVERLAP_RATIO)) return true;
        }
        return false;
    }
}
