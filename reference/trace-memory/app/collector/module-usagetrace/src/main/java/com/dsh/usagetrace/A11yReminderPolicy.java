package com.dsh.usagetrace;

import com.dsh.host.api.Config;

import java.util.function.Supplier;

/**
 * 提醒节流（纯逻辑，假时钟可测）：
 *  - 只有 DISABLED 才提醒（SILENT 可能是用户没动手机，不轰炸）；
 *  - 刚开机 bootGraceMs 内不提醒（启动阶段系统/用户都在忙）；
 *  - 之后最多每 reminderIntervalMs 提醒一次（默认 30 分钟）。
 */
public final class A11yReminderPolicy {

    public static final long DEFAULT_BOOT_GRACE_MS = 10 * 60_000L;
    public static final long DEFAULT_INTERVAL_MS = 30 * 60_000L;
    public static final long MIN_INTERVAL_MS = 60_000L;

    private final A11yTimeSource time;
    private final Supplier<Config> configs;
    private long lastReminderAtMs = 0;

    public A11yReminderPolicy(A11yTimeSource time, Supplier<Config> configs) {
        this.time = time;
        this.configs = configs;
    }

    public boolean shouldRemind(A11yState state) {
        if (state != A11yState.DISABLED) return false;
        Config c = configs == null ? null : configs.get();
        long grace = configLong(c, "a11y.bootGraceMs", DEFAULT_BOOT_GRACE_MS);
        if (time.uptimeMs() < Math.max(0, grace)) return false;
        long interval = Math.max(MIN_INTERVAL_MS, configLong(c, "a11y.reminderIntervalMs", DEFAULT_INTERVAL_MS));
        long now = time.nowMs();
        return lastReminderAtMs <= 0 || now - lastReminderAtMs >= interval;
    }

    public void noteReminded() {
        lastReminderAtMs = time.nowMs();
    }

    /** 测试/状态页：上次提醒时间（0 = 从未） */
    public long lastReminderAtMs() { return lastReminderAtMs; }

    private static long configLong(Config c, String key, long def) {
        return c == null ? def : c.getLong(key, def);
    }
}
