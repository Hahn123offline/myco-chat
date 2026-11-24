package com.dsh.egress;

import java.util.ArrayList;
import java.util.List;

/** 测试/无文件环境用内存审计。 */
public final class MemoryAudit implements AuditSink {

    public final List<AuditEntry> entries = new ArrayList<>();

    @Override public synchronized void record(AuditEntry entry) {
        entries.add(entry);
    }

    public synchronized int size() { return entries.size(); }

    public synchronized AuditEntry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }
}
