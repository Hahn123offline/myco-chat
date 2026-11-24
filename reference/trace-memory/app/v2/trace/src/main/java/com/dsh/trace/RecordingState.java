package com.dsh.trace;

/**
 * 「现在到底在不在记录」的唯一判据（task-18）。
 *
 * 为什么不用系统声明：MIUI 上通过 settings put 打开的无障碍服务经常不在
 * AccessibilityManager/Settings.Secure 的启用列表里，但服务实际绑定且在工作。
 * 所以**以观察到的事件为准**，系统声明只用于「从未收到过事件」时的提示。
 *
 * 优先级（高 → 低）：
 * 1. 最近 60s 内收到过 a11y 事件 → ACTIVE（不管系统怎么说，事件在流就是铁证）
 * 2. 系统声明未启用 → DISABLED（此时已超过 60s 没事件）
 * 3. 超过 60s 没事件 + 亮屏 + HostService 在跑 → STALLED（事件流断了）
 * 4. 从未收到事件 + 系统声明已启用 → ENABLED_BUT_SILENT（刚启动，等事件）
 */
public enum RecordingState {

    ACTIVE,
    STALLED,
    ENABLED_BUT_SILENT,
    DISABLED;

    /** 最近多久有事件就算「正在记录」 */
    public static final long RECENT_MS = 60_000L;

    public boolean isRecording() {
        return this == ACTIVE;
    }

    public boolean isStalled() {
        return this == STALLED;
    }

    public String label() {
        switch (this) {
            case ACTIVE: return "ACTIVE";
            case STALLED: return "STALLED";
            case ENABLED_BUT_SILENT: return "ENABLED_BUT_SILENT";
            default: return "DISABLED";
        }
    }

    /**
     * @param lastEventAgoMs   最近事件距今毫秒；-1 = 从未收到过事件
     * @param systemEnabled    系统声明列表里是否有我们（只在没有事件时用于区分 SILENT/DISABLED）
     * @param screenInteractive 屏幕是否亮着
     * @param hostRunning      HostService 是否在跑
     */
    public static RecordingState decide(long lastEventAgoMs, boolean systemEnabled,
                                        boolean screenInteractive, boolean hostRunning) {
        if (lastEventAgoMs >= 0 && lastEventAgoMs < RECENT_MS) {
            return ACTIVE;                    // 事件在流：最高优先级
        }
        if (!systemEnabled) {
            return DISABLED;
        }
        if (lastEventAgoMs >= 0 && screenInteractive && hostRunning) {
            return STALLED;
        }
        return ENABLED_BUT_SILENT;
    }
}
