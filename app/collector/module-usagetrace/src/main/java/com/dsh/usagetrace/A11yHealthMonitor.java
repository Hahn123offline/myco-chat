package com.dsh.usagetrace;

import com.dsh.host.api.Config;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 无障碍健康状态机。纯逻辑（不碰 android API），可 JVM 单测。
 *
 * 判据两个，互为印证：
 *  1) 系统层：我们的服务是否在已启用列表里（{@link A11yEnabledChecker}）；
 *  2) 事实层：最近一次 onAccessibilityEvent 是多久以前（{@link #lastEventAt}）。
 * 第二判据更真实：系统可能"显示启用"但服务已被杀/没绑上。
 *
 * 映射：
 *  - 系统未启用 → DISABLED
 *  - 启用 + 最近 silentMs 内有过事件 → ACTIVE
 *  - 启用 + 没有/太久没事件 → ENABLED_BUT_SILENT（宁可不报 OK，也不假装在记录）
 */
public final class A11yHealthMonitor {

    public static final long DEFAULT_SILENT_MS = 10 * 60_000L;
    public static final long MIN_SILENT_MS = 60_000L;

    public static final class Result {
        public final A11yState state;
        /** 最近事件距今毫秒；从未收到过为 -1 */
        public final long lastEventAgeMs;

        public Result(A11yState state, long lastEventAgeMs) {
            this.state = state;
            this.lastEventAgeMs = lastEventAgeMs;
        }

        @Override public String toString() { return state.tag + " lastEventAgeMs=" + lastEventAgeMs; }
    }

    private final A11yEnabledChecker enabledChecker;
    private final A11yTimeSource time;
    private final LongSupplier lastEventAt;
    private final Supplier<Config> configs;

    public A11yHealthMonitor(A11yEnabledChecker enabledChecker, A11yTimeSource time,
                             LongSupplier lastEventAt, Supplier<Config> configs) {
        this.enabledChecker = enabledChecker;
        this.time = time;
        this.lastEventAt = lastEventAt;
        this.configs = configs;
    }

    public Result evaluate() {
        long now = time.nowMs();
        boolean enabled;
        try {
            enabled = enabledChecker != null && enabledChecker.isOurServiceEnabled();
        } catch (Throwable t) {
            enabled = false;
        }
        long last = lastEventAt == null ? 0 : lastEventAt.getAsLong();
        long age = last <= 0 ? -1 : Math.max(0, now - last);

        if (!enabled) return new Result(A11yState.DISABLED, age);

        long silentMs = silentMs();
        if (age < 0 || age > silentMs) return new Result(A11yState.ENABLED_BUT_SILENT, age);
        return new Result(A11yState.ACTIVE, age);
    }

    /** 配置热加载：每次判状态重新读 */
    public long silentMs() {
        Config c = configs == null ? null : configs.get();
        if (c == null) return DEFAULT_SILENT_MS;
        return Math.max(MIN_SILENT_MS, c.getLong("a11y.silentMs", DEFAULT_SILENT_MS));
    }
}
