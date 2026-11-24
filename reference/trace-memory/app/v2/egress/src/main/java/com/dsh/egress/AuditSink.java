package com.dsh.egress;

public interface AuditSink {
    void record(AuditEntry entry);
}
