package com.dsh.agent;

/** 看图次数预算（钱的保险：一条链最多 6 次 read_*）。 */
public final class ImageBudget {

    private int remaining;

    public ImageBudget(int max) { this.remaining = Math.max(0, max); }

    public synchronized boolean tryAcquire() {
        if (remaining <= 0) return false;
        remaining--;
        return true;
    }

    public synchronized int remaining() { return remaining; }
}
