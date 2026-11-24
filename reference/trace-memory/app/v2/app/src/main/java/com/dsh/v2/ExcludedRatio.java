package com.dsh.v2;

/**
 * 排除块占比判定（task-33）：
 * 0 → FULL；0<r<阈值 → PARTIAL；r>=阈值 → NEEDS_LOCAL_MODEL。
 */
public final class ExcludedRatio {

    public enum Decision { FULL, PARTIAL, NEEDS_LOCAL_MODEL }

    public static final double DEFAULT_THRESHOLD = 0.3;

    private ExcludedRatio() { }

    public static Decision decide(long excluded, long total, double threshold) {
        if (total <= 0) return Decision.FULL;
        double r = Math.max(0, Math.min(excluded, total)) / (double) total;
        if (r <= 0) return Decision.FULL;
        if (r >= threshold) return Decision.NEEDS_LOCAL_MODEL;
        return Decision.PARTIAL;
    }

    public static double ratio(long excluded, long total) {
        if (total <= 0) return 0;
        return Math.max(0, Math.min(excluded, total)) / (double) total;
    }

    public static String textSource(Decision d) {
        switch (d) {
            case FULL: return "full";
            case PARTIAL: return "partial";
            default: return "excluded";
        }
    }
}
