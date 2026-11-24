package com.dsh.usagetrace;

/**
 * 无障碍状态的**文案生成**（纯逻辑，可单测）。
 *
 * 硬规则：非 ACTIVE 时，任何"正在记录"字样都不能出现 —— 用户以为在记、实际没记，
 * 是这个产品最致命的失败模式。文案必须在 app 和系统通知里都如实。
 */
public final class A11yStatusText {

    private A11yStatusText() { }

    /** 前台服务通知的正文 */
    public static String notificationText(A11yState state, long lastEventAgeMs, long episodesToday) {
        if (state == A11yState.DISABLED) {
            return "未在记录：无障碍服务未启用（点开 App 去开启）";
        }
        if (state == A11yState.ENABLED_BUT_SILENT) {
            return "未确认在记录：无障碍已启用但最近没有数据"
                    + ageSuffix(lastEventAgeMs);
        }
        return "正在记录（今日 episodes=" + episodesToday + "）";
    }

    /** 前台服务通知的标题 */
    public static String notificationTitle(A11yState state) {
        if (state == A11yState.DISABLED) return "DSH 未在记录（无障碍未启用）";
        if (state == A11yState.ENABLED_BUT_SILENT) return "DSH 可能未在记录（无障碍无数据）";
        return "DSH 正在记录";
    }

    /** 状态页的大字横幅（ACTIVE 时为空串 = 不显示） */
    public static String banner(A11yState state) {
        if (state == A11yState.DISABLED) {
            return "⚠ 未在记录：无障碍服务未启用。采集已停止，点下方按钮去开启。";
        }
        if (state == A11yState.ENABLED_BUT_SILENT) {
            return "⚠ 无障碍已启用，但最近没有收到事件（可能被系统停掉）。";
        }
        return "";
    }

    /** 状态页一行详情：a11y=ACTIVE|SILENT|DISABLED lastEventAgeMs=… episodesToday=… */
    public static String statusLine(A11yState state, long lastEventAgeMs, long episodesToday) {
        return "a11y=" + state.tag
                + " lastEventAgeMs=" + lastEventAgeMs
                + " episodesToday=" + episodesToday;
    }

    /** 用于 Health.detail：同上（保持一致，便于日志 grep） */
    public static String healthDetail(A11yState state, long lastEventAgeMs, long episodesToday) {
        return statusLine(state, lastEventAgeMs, episodesToday);
    }

    /** 无障碍未启用时的提醒通知（自愈部分：只能提醒，不能替用户开） */
    public static String alertTitle() { return "DSH 采集已停止"; }

    public static String alertText() {
        return "无障碍服务被系统关闭了，应用无法自己开启。点此去系统设置里打开。";
    }

    private static String ageSuffix(long ageMs) {
        if (ageMs < 0) return "（从未收到事件）";
        if (ageMs < 60_000) return "（最近一次事件 " + ageMs + "ms 前）";
        return "（最近一次事件 " + (ageMs / 60_000) + " 分钟前）";
    }
}
