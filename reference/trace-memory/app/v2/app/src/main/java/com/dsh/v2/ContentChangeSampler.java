package com.dsh.v2;

/** 内容变化采样：每 2s 才允许“看一眼”，hash 不同才截图/OCR。 */
public final class ContentChangeSampler {

    public static final long SAMPLE_INTERVAL_MS = 2_000L;
    public static final int MAX_FRAMES_PER_BLOCK = 20;

    private String lastHash = "";
    private long lastAt = 0;
    private int frames = 0;

    /** @return true = 允许截图；false = 太小/没变/块帧数满 */
    public boolean shouldCapture(long nowMs, String hash) {
        if (frames >= MAX_FRAMES_PER_BLOCK) return false;
        if (nowMs - lastAt < SAMPLE_INTERVAL_MS) return false;
        String h = hash == null ? "" : hash;
        if (h.equals(lastHash)) return false;
        lastAt = nowMs;
        lastHash = h;
        frames++;
        return true;
    }

    public void resetBlock() {
        frames = 0;
        lastAt = 0;
        lastHash = "";
    }

    public int frames() { return frames; }
}
