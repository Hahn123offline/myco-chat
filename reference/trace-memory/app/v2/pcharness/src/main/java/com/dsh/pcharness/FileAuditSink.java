package com.dsh.pcharness;

import com.dsh.egress.AuditEntry;
import com.dsh.egress.AuditSink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** 桌面 harness 的 egress.log（只写审计行）。 */
public final class FileAuditSink implements AuditSink {

    private final File file;

    public FileAuditSink(String path) {
        file = new File(path);
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
    }

    @Override public synchronized void record(AuditEntry entry) {
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(entry.toJsonLine());
            w.write('\n');
        } catch (Throwable ignored) { }
    }
}
