package com.dsh.v2;

import com.dsh.egress.AuditEntry;
import com.dsh.egress.AuditSink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** 出网审计落盘：本地 JSONL；不含原文/密钥。 */
public final class FileAuditSink implements AuditSink {

    private final File file;

    public FileAuditSink(File file) {
        this.file = file;
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    @Override public synchronized void record(AuditEntry entry) {
        if (entry == null) return;
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(entry.toJsonLine());
            w.write('\n');
        } catch (Throwable ignored) {
        }
    }
}
