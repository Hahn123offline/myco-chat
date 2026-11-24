package com.dsh.v2;

/** 记录状态的 UI 文案：通知/状态页共用，保证三处口径一致。 */
public final class RecordingText {

    private RecordingText() { }

    public static String title(RecordingStatus.State state) {
        switch (state) {
            case ACTIVE: return "DSH 正在记录";
            case IDLE: return "DSH 正在记录 · 最近 10 分钟无活动";
            case STALLED: return "DSH 未在记录（无障碍无响应）";
            default: return "DSH 未在记录（无障碍未开启）";
        }
    }

    public static String text(RecordingStatus.State state, long tracesToday) {
        switch (state) {
            case ACTIVE:
                return "正在记录 · 今日痕迹 " + tracesToday + " 条";
            case IDLE:
                return "服务正常，最近无活动 · 今日 " + tracesToday + " 条";
            case STALLED:
                return "需要手动开关一次无障碍 · 今日 " + tracesToday + " 条";
            default:
                return "点开 App 去开启无障碍 · 今日 " + tracesToday + " 条";
        }
    }

    /** 高优先级只在“无响应/未开启”时出现；IDLE 不打扰用户。 */
    public static boolean needsAttention(RecordingStatus.State state) {
        return state == RecordingStatus.State.STALLED || state == RecordingStatus.State.OFF;
    }

    public static boolean actionable(RecordingStatus.State state) {
        return state != RecordingStatus.State.ACTIVE;
    }
}
