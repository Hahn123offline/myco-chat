package com.dsh.v2;

/**
 * 成本估算（flash 空闲价，USD/百万 token）：
 * cache hit $0.003、miss $0.15、output $0.6；人民币按 7.1。
 */
public final class CostEstimator {

    public static final double HIT_USD_PER_M = 0.003;
    public static final double MISS_USD_PER_M = 0.15;
    public static final double OUT_USD_PER_M = 0.6;
    public static final double CNY_PER_USD = 7.1;

    private CostEstimator() { }

    /** @param prompt total prompt tokens; @param cacheHit cache hit tokens (<=prompt) */
    public static double usd(long prompt, long cacheHit, long completion) {
        long hit = Math.max(0, Math.min(cacheHit, prompt));
        long miss = Math.max(0, prompt - hit);
        return hit / 1_000_000.0 * HIT_USD_PER_M
                + miss / 1_000_000.0 * MISS_USD_PER_M
                + Math.max(0, completion) / 1_000_000.0 * OUT_USD_PER_M;
    }

    public static String cny(long prompt, long cacheHit, long completion) {
        return String.format("¥%.2f", usd(prompt, cacheHit, completion) * CNY_PER_USD);
    }
}
