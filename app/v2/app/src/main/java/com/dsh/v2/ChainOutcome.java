package com.dsh.v2;

/** 父链/链的结局枚举（task-31）：日叙事读字段，不从散文猜。 */
public enum ChainOutcome {
    RESOLVED("resolved"),
    IN_PROGRESS("in_progress"),
    NO_ACTION("no_action"),
    UNKNOWN("unknown");

    public final String dbValue;

    ChainOutcome(String v) { this.dbValue = v; }

    /** 只有 resolved 才允许断言「解决了」；其余强制范围限定语。 */
    public static boolean canAssertResolved(String dbValue) {
        return RESOLVED.dbValue.equals(dbValue);
    }

    public static ChainOutcome parse(String s) {
        if (s == null) return UNKNOWN;
        for (ChainOutcome o : values()) if (o.dbValue.equals(s)) return o;
        return UNKNOWN;
    }

    /**
     * 从 body 只做保守推断：
     *  - 明确看到解决结果 → resolved（仅当文本含「已解决/恢复正常」）
     *  - 有动作但没落地 → no_action
     *  - 其余 → in_progress
     * 不把「无从判断」等越界句当依据（它们本来就会被 ParentBodySpec 拒绝）。
     */
    public static ChainOutcome inferFromBody(String body) {
        if (body == null || body.trim().isEmpty()) return UNKNOWN;
        String s = body;
        if (s.contains("已解决") || s.contains("恢复正常") || s.contains("恢复了正常")) {
            return RESOLVED;
        }
        if (s.contains("没有出现") || s.contains("没有落地") || s.contains("没有执行")
                || s.contains("没有实际动作") || s.contains("没有改动")
                || s.contains("尚未进行") || s.contains("停在")) {
            return NO_ACTION;
        }
        return IN_PROGRESS;
    }
}
