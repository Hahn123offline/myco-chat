package com.dsh.v2;

/**
 * 滚动触发截图：
 *  - 累计 |dy|（或 |dx|）≥ 0.7 屏高 → 一帧（留 30% 重叠，防边界切断）
 *  - 300ms 无新滚动且累计 ≥0.2 屏高 → 补一帧
 */
public final class ScrollTrigger {

    public static final float PAGE_RATIO = 0.7f;
    public static final float IDLE_RATIO = 0.2f;
    public static final long IDLE_MS = 300L;
    public static final long MIN_INTERVAL_MS = 500L;

    private float accY, accX;
    private long lastScrollAt, lastShotAt;

    public boolean onScroll(long now, int dx, int dy, int screenH) {
        accY += Math.abs(dy);
        accX += Math.abs(dx);
        lastScrollAt = now;
        float threshold = Math.max(1f, screenH * PAGE_RATIO);
        if ((accY >= threshold || accX >= threshold) && now - lastShotAt >= MIN_INTERVAL_MS) {
            accY = 0; accX = 0; lastShotAt = now;
            return true;
        }
        return false;
    }

    /** 滚动停止回调；返回 true 表示要补一帧。 */
    public boolean onIdle(long now, int screenH) {
        if (now - lastScrollAt < IDLE_MS) return false;
        float threshold = Math.max(1f, screenH * IDLE_RATIO);
        if (accY >= threshold || accX >= threshold) {
            accY = 0; accX = 0;
            if (now - lastShotAt >= MIN_INTERVAL_MS) {
                lastShotAt = now;
                return true;
            }
        }
        return false;
    }

    public void reset() {
        accY = 0; accX = 0;
        lastScrollAt = 0; lastShotAt = 0;
    }
}
