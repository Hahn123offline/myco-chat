package com.dsh.v2;

import android.content.Context;

import com.dsh.usage.UsageEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 唯一状态计算者：HostService 通知、App 状态页、设置入口都调它。
 * 不缓存状态（除 usage 查询 60s），避免三处各自推断。
 */
public final class RecordingStatusCenter {

    public static final class Snapshot {
        public final RecordingStatus.State state;
        public final long lastEventAgoMs;
        public final long todayCount;
        public final boolean systemEnabled;
        public final boolean usageGranted;
        public final int usageSwitches;
        public final int selfHealFailures;
        public final long generatedAt;

        Snapshot(RecordingStatus.State state, long ago, long today, boolean sys,
                 boolean granted, int switches, int failures) {
            this.state = state;
            this.lastEventAgoMs = ago;
            this.todayCount = today;
            this.systemEnabled = sys;
            this.usageGranted = granted;
            this.usageSwitches = switches;
            this.selfHealFailures = failures;
            this.generatedAt = System.currentTimeMillis();
        }
    }

    private static volatile int selfHealFailures = 0;
    private static volatile boolean highPriorityShown = false;
    private static volatile long usageCachedAt = 0;
    private static volatile int usageCachedSwitches = 0;
    private static volatile boolean usageCachedGranted = false;

    private RecordingStatusCenter() { }

    public static Snapshot current(Context ctx) {
        long now = System.currentTimeMillis();
        long last = A11yHealth.lastEventAt();
        long ago = last <= 0 ? -1 : Math.max(0, now - last);
        boolean sys = A11yHealth.isEnabled(ctx);
        int switches = switchesLast10Min(ctx, now);
        boolean granted = usageCachedGranted;
        RecordingStatus.State state = RecordingStatus.decide(ago, sys, switches > 0);
        long today = 0;
        try {
            TraceApp app = TraceApp.get(ctx);
            if (app != null && app.traceModule() != null) today = app.traceModule().todayCount();
        } catch (Throwable ignored) { }
        return new Snapshot(state, ago, today, sys, granted, switches, selfHealFailures);
    }

    /** 最近 10min 非 DSH 的 MOVE_TO_FOREGROUND 次数（缓存 60s，避免看门狗过重）。 */
    public static synchronized int switchesLast10Min(Context ctx, long now) {
        if (now - usageCachedAt < 60_000L) return usageCachedSwitches;
        int count = 0;
        boolean granted = UsageStatsSource.granted(ctx);
        if (granted) {
            try {
                List<UsageEvent> events = UsageStatsSource.events(ctx,
                        now - RecordingStatus.STALLED_AFTER_MS, now);
                Set<String> pkgs = new HashSet<>();
                for (UsageEvent e : events) {
                    if (e == null || !e.foreground) continue;
                    String p = e.pkg;
                    if (p == null || p.isEmpty()) continue;
                    if (p.equals(ctx.getPackageName())) continue;
                    if (p.equals("com.android.systemui")) continue;
                    pkgs.add(p);
                }
                count = pkgs.size();
            } catch (Throwable ignored) { }
        }
        usageCachedAt = now;
        usageCachedSwitches = count;
        usageCachedGranted = granted;
        return count;
    }

    public static synchronized void noteSelfHealFailure() { selfHealFailures++; }

    public static synchronized void clearSelfHealFailures() { selfHealFailures = 0; }

    public static int selfHealFailures() { return selfHealFailures; }

    public static boolean highPriorityShown() { return highPriorityShown; }

    public static void setHighPriorityShown(boolean shown) { highPriorityShown = shown; }
}
