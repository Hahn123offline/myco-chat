package com.dsh.host.api;

/**
 * 能力账本：每次敏感能力使用落一笔。
 * 三合一用途：调试、用户可控（"它读过什么"）、资源统计。
 */
public interface CapabilityLedger {

    void record(String moduleId, Capability capability, String target, String outcome, long costMs);

    /** 最近 N 条，给状态页用 */
    java.util.List<Entry> recent(int n);

    final class Entry {
        public final long ts;
        public final String moduleId;
        public final Capability capability;
        public final String target;
        public final String outcome;
        public final long costMs;

        public Entry(long ts, String moduleId, Capability capability,
                     String target, String outcome, long costMs) {
            this.ts = ts;
            this.moduleId = moduleId;
            this.capability = capability;
            this.target = target;
            this.outcome = outcome;
            this.costMs = costMs;
        }
    }
}
