package com.dsh.core;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 宿主日志：可插拔 sink（Android 端接 android.util.Log）+ 滚动文件。
 *
 * 核心库不依赖 Android：sink 由 app 层注入；没有 sink 时打到 stdout（桌面/测试）。
 * 文件兜底保留（真机 logcat 看不见时 host.log 仍可拉）。
 */
public final class SimpleLog implements Log {

    /** 行级输出目标（app 层用 android.util.Log 实现） */
    public interface Sink {
        void onLine(String level, String tag, String msg, Throwable t);
    }

    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final long ROTATE_CHECK_EVERY = 50;
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private final File file;
    private final Sink sink;
    private final Object lock = new Object();
    private long lines = 0;

    public SimpleLog() { this(null, null); }

    public SimpleLog(File file) { this(file, null); }

    public SimpleLog(File file, Sink sink) {
        this.file = file;
        this.sink = sink;
        if (file != null) {
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
            } catch (Throwable ignored) { }
        }
    }

    public File file() { return file; }

    @Override public void i(String tag, String msg) { emit("I", tag, msg, null); }
    @Override public void w(String tag, String msg) { emit("W", tag, msg, null); }
    @Override public void e(String tag, String msg, Throwable t) { emit("E", tag, msg, t); }

    private void emit(String level, String tag, String msg, Throwable t) {
        Sink s = sink;
        if (s != null) {
            try { s.onLine(level, tag, msg, t); } catch (Throwable ignored) { }
        } else {
            System.out.println(level + "/" + tag + ": " + msg);
        }
        appendFile(level, tag, msg, t);
    }

    private void appendFile(String level, String tag, String msg, Throwable t) {
        if (file == null) return;
        synchronized (lock) {
            try {
                StringBuilder line = new StringBuilder(160);
                line.append(TS.format(new Date())).append(' ')
                        .append(level).append('/').append(tag).append(": ").append(msg);
                if (t != null) {
                    line.append(" | ").append(t.getClass().getSimpleName());
                    if (t.getMessage() != null) line.append(": ").append(t.getMessage());
                }
                line.append('\n');
                if (++lines % ROTATE_CHECK_EVERY == 0) rotateIfNeeded();
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(line.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
                // 日志落盘失败不能影响宿主
            }
        }
    }

    private void rotateIfNeeded() {
        try {
            if (!file.exists() || file.length() < MAX_BYTES) return;
            File old = new File(file.getParentFile(), file.getName() + ".1");
            if (old.exists()) old.delete();
            if (!file.renameTo(old)) {
                try (FileOutputStream out = new FileOutputStream(file, false)) { }
            }
        } catch (Throwable ignored) { }
    }
}
