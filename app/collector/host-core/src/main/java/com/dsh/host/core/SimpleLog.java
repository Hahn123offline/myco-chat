package com.dsh.host.core;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 宿主日志：logcat + 滚动文件。
 *
 * 为什么要有文件：真机上某些 ROM / 场景（未授权开发者选项、logcat buffer 被清、过滤条件不对）
 * 会看不到 logcat。文件落到 files/host/log/host.log，adb pull / run-as cat 一定能拿到，
 * 是"能不能在真机诊断"的兜底。
 *
 * 线程安全：模块回调可能来自事件总线线程/模型回调线程/主线程，统一加锁追加。
 */
public final class SimpleLog implements com.dsh.host.api.Log {

    /** logcat tag：adb logcat -s DSHHost 可见 */
    private static final String PRE = "DSHHost";

    private static final long MAX_BYTES = 2 * 1024 * 1024;   // 2MB 滚动
    private static final long ROTATE_CHECK_EVERY = 50;       // 每 50 行检查一次大小，避免每次都 stat
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private final File file;
    private final Object lock = new Object();
    private long lines = 0;

    /** 只写 logcat（测试/无文件环境） */
    public SimpleLog() { this(null); }

    /** 同时写文件（生产路径，文件追加、2MB 滚动为 .1） */
    public SimpleLog(File file) {
        this.file = file;
        if (file != null) {
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
            } catch (Throwable ignored) { }
        }
    }

    public File file() { return file; }

    @Override
    public void i(String tag, String msg) {
        Log.i(PRE, tag + ": " + msg);
        append("I", tag, msg, null);
    }

    @Override
    public void w(String tag, String msg) {
        Log.w(PRE, tag + ": " + msg);
        append("W", tag, msg, null);
    }

    @Override
    public void e(String tag, String msg, Throwable t) {
        Log.e(PRE, tag + ": " + msg, t);
        append("E", tag, msg, t);
    }

    private void append(String level, String tag, String msg, Throwable t) {
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
                // 某些文件系统 rename 失败：直接截断，宁可丢旧日志也不能无限增长
                try (FileOutputStream out = new FileOutputStream(file, false)) { }
            }
        } catch (Throwable ignored) { }
    }
}
