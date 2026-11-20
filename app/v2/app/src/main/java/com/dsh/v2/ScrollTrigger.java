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
    /** ROM 不报 delta 时，同窗口 300ms 内 3 个滚动事件近似一屏。 */
    public static final int EVENT_FALLBACK_COUNT = 3;
    public static final long EVENT_WINDOW_MS = 300L;

    private float accY, accX;
    private long lastScrollAt, lastShotAt;
    private int lastScrollY = -1;
    private int scrollEvents;
    private long firstEventAt;

    public boolean onScroll(long now, int dx, int dy, int screenH) {
        return onScroll(now, dx, dy, -1, screenH);
    }

    /** sy = event.getScrollY() 的绝对值；delta 全 0 时用连续事件数兜底。 */
    public boolean onScroll(long now, int dx, int dy, int sy, int screenH) {
        int ady = Math.abs(dy);
        int adx = Math.abs(dx);
        if (ady == 0 && sy >= 0 && lastScrollY >= 0) ady = Math.abs(sy - lastScrollY);
        if (sy >= 0) lastScrollY = sy;
        accY += ady;
        accX += adx;
        lastScrollAt = now;
        if (now - firstEventAt > EVENT_WINDOW_MS) { scrollEvents = 0; firstEventAt = now; }
        scrollEvents++;
        float threshold = Math.max(1f, screenH * PAGE_RATIO);
        boolean byPixels = accY >= threshold || accX >= threshold;
        boolean byCount = ady == 0 && adx == 0 && scrollEvents >= EVENT_FALLBACK_COUNT;
        if ((byPixels || byCount) && now - lastShotAt >= MIN_INTERVAL_MS) {
            accY = 0; accX = 0; scrollEvents = 0; firstEventAt = 0;
            lastShotAt = now;
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
