package com.dsh.usagetrace;

import java.util.Calendar;

/**
 * 资源闸门：截图 / OCR / 存储的硬预算。
 * 这是"能不能长期挂着"的核心，不是优化项。
 */
public final class ResourceGovernor {

    private final long minShotGapMs;
    private final long minOcrGapMs;
    private final int maxShotsPerDay;

    private long lastShotAt = 0;
    private long lastOcrAt = 0;
    private int shotsToday = 0;
    private int ocrToday = 0;
    private int dayKey = -1;

    public ResourceGovernor(long minShotGapMs, long minOcrGapMs, int maxShotsPerDay) {
        this.minShotGapMs = minShotGapMs;
        this.minOcrGapMs = minOcrGapMs;
        this.maxShotsPerDay = maxShotsPerDay;
    }

    public static ResourceGovernor defaults() {
        return new ResourceGovernor(8_000, 10_000, 3000);
    }

    private void rollIfNeeded(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int k = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
        if (k != dayKey) {
            dayKey = k;
            shotsToday = 0;
            ocrToday = 0;
        }
    }

    public synchronized boolean allowShot(long now) {
        rollIfNeeded(now);
        if (shotsToday >= maxShotsPerDay) return false;
        return now - lastShotAt >= minShotGapMs;
    }

    public synchronized void recordShot(long now) {
        rollIfNeeded(now);
        lastShotAt = now;
        shotsToday++;
    }

    public synchronized boolean allowOcr(long now) {
        rollIfNeeded(now);
        return now - lastOcrAt >= minOcrGapMs;
    }

    public synchronized void recordOcr(long now) {
        rollIfNeeded(now);
        lastOcrAt = now;
        ocrToday++;
    }

    public synchronized int shotsToday() { return shotsToday; }
    public synchronized int ocrToday() { return ocrToday; }
}
