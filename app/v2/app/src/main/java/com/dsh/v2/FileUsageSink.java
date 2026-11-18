package com.dsh.v2;

import com.dsh.models.UsageRecord;
import com.dsh.models.UsageSink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** usage 审计落盘：task/provider/tokens/cache_hit/latency；不含 key/原文。 */
public final class FileUsageSink implements UsageSink {

    private final File file;

    public FileUsageSink(File file) {
        this.file = file;
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }

    @Override public synchronized void record(UsageRecord r) {
        if (r == null) return;
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(r.toLine());
            w.write('\n');
        } catch (Throwable ignored) {
        }
    }
}
