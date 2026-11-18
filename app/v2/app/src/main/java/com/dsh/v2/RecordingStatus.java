package com.dsh.v2;

/**
 * 记录状态（task-30 唯一判据）。纯逻辑，可 JVM 测试。
 *
 * 关键：状态由“实际行为”推出，不由系统声明推出：
 *  - 最近 60s 有 a11y 事件 → ACTIVE（即使系统说关）
 *  - 系统明确未启用且无事件 → OFF（用户手动关）
 *  - >10min 无事件且 usage 显示有应用切换 → STALLED
 *  - 其余（含睡觉/息屏/刚启动）→ IDLE，不误报
 */
public final class RecordingStatus {

    public enum State {
        ACTIVE, IDLE, STALLED, OFF
    }

    public static final long ACTIVE_MS = 60_000L;
    public static final long STALLED_AFTER_MS = 10 * 60_000L;

    private RecordingStatus() { }

    /** @param lastEventAgoMs -1 = 从未收到事件；@param usageActive 最近 10min 有其他应用切换 */
    public static State decide(long lastEventAgoMs, boolean systemEnabled, boolean usageActive) {
        if (lastEventAgoMs >= 0 && lastEventAgoMs < ACTIVE_MS) return State.ACTIVE;
        if (!systemEnabled) return State.OFF;
        if (lastEventAgoMs >= 0 && lastEventAgoMs > STALLED_AFTER_MS && usageActive) {
            return State.STALLED;
        }
        return State.IDLE;
    }

    public static String label(State s) {
        switch (s) {
            case ACTIVE: return "ACTIVE";
            case IDLE: return "IDLE";
            case STALLED: return "STALLED";
            default: return "OFF";
        }
    }

    public static String readableAgo(long agoMs) {
        if (agoMs < 0) return "从未";
        if (agoMs < 60_000L) return Math.max(1, agoMs / 1000) + " 秒前";
        if (agoMs < 3600_000L) return (agoMs / 60_000L) + " 分钟前";
        return (agoMs / 3600_000L) + " 小时前";
    }
}
