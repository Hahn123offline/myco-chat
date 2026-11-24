package com.dsh.usage;

/** 一条原始使用事件（来自 UsageStatsManager：前台/后台切换）。 */
public final class UsageEvent {

    public final String pkg;
    public final long ts;
    public final boolean foreground;

    public UsageEvent(String pkg, long ts, boolean foreground) {
        this.pkg = pkg == null ? "" : pkg;
        this.ts = ts;
        this.foreground = foreground;
    }
}
