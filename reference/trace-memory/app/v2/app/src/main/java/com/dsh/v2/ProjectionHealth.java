package com.dsh.v2;

/** 投影水位/健康指标，供周期投影和 health 心跳共用。 */
public final class ProjectionHealth {
    public static volatile long lastProjectAt = 0;
    public static volatile long lastBlock = 0;
    public static volatile long lastLineBlock = 0;
    public static volatile int lastTraces = 0;
    public static volatile int lastFrames = 0;

    public static long lagMin(android.content.Context app) {
        try {
            long blockTime = new TraceStore(app).lastTraceT1();
            if (blockTime <= 0) return -1;
            return Math.max(0, (System.currentTimeMillis() - blockTime) / 60_000L);
        } catch (Throwable t) { return -1; }
    }

    private ProjectionHealth() { }
}
