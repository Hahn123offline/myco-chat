package com.dsh.redaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 脱敏链路的占用/耗电观测。纯 Java（不依赖 android.*），可 JVM 单测。
 *
 * 采什么（用户明确要求"占用与耗电"）：
 *  - CPU：/proc/self/stat 的 utime+stime（tick 差分 → ms）；
 *  - 内存：/proc/self/status 的 VmRSS / VmHWM（峰值常驻内存，最能说明 OOM 风险）；
 *  - 唤醒锁：本模块不持有（如实记 0；字段保留，将来接 WakeLock 时直接填）；
 *  - 每条处理的耗时：实时层 / 批处理层分开记 count、avg、P95、累计 CPU。
 *
 * 采样方式：Timer 在操作前后各读一次 CPU tick，差值累加到对应 lane；内存只在 snapshot 时读。
 * 所有 lane 方法同步，可被实时回调线程和批处理线程同时调用。
 */
public final class Metrics {

    public static final String LANE_REALTIME = "realtime";
    public static final String LANE_BATCH = "batch";

    /** Android/Linux 上 USER_HZ 固定 100（CLK_TCK）。 */
    public static final long CLK_TCK = 100;

    /** /proc 访问抽象：测试注入假字符串，不碰真实文件 */
    public interface ProcSource {
        /** 读不到返回 null */
        String read(String path);
    }

    /** 生产实现：直接读 /proc（纯 Java，安卓上可读） */
    public static final class FileProcSource implements ProcSource {
        @Override
        public String read(String path) {
            try (InputStream in = new FileInputStream(path)) {
                byte[] buf = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                return sb.toString();
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /** 单条 lane 的计数（count / 总耗时 / 累计 CPU / 最近 512 次耗时样本用于 P95） */
    public static final class Lane {
        private static final int SAMPLES = 512;
        private long count;
        private long totalMs;
        private long cpuMs;
        private final long[] durations = new long[SAMPLES];
        private int sampleCount;
        private int sampleIdx;

        public synchronized void record(long durationMs, long cpuMsDelta) {
            count++;
            totalMs += Math.max(0, durationMs);
            cpuMs += Math.max(0, cpuMsDelta);
            durations[sampleIdx] = Math.max(0, durationMs);
            sampleIdx = (sampleIdx + 1) % SAMPLES;
            if (sampleCount < SAMPLES) sampleCount++;
        }

        public synchronized long count() { return count; }
        public synchronized long totalMs() { return totalMs; }
        public synchronized long cpuMs() { return cpuMs; }
        public synchronized long avgMs() { return count == 0 ? 0 : totalMs / count; }

        /** P95：对最近样本排序取第 95 百分位（样本不足时就是当前最大值） */
        public synchronized long p95Ms() {
            if (sampleCount == 0) return 0;
            long[] a = new long[sampleCount];
            System.arraycopy(durations, 0, a, 0, sampleCount);
            java.util.Arrays.sort(a);
            int idx = (int) Math.ceil(sampleCount * 0.95) - 1;
            return a[Math.max(0, Math.min(sampleCount - 1, idx))];
        }
    }

    /** 某一时刻的快照（写文件/上状态页用） */
    public static final class Snapshot {
        public final long ts;
        public final long rssKb;
        public final long hwmKb;
        public final int wakeLocksHeld;
        public final long realtimeCount, realtimeAvgMs, realtimeP95Ms, realtimeCpuMs;
        public final long batchCount, batchAvgMs, batchP95Ms, batchCpuMs;
        /** 最近一轮批处理为什么跑/没跑（not_charging/screen_on/ok/forced_debug…） */
        public final String batchLastReason;
        public final long batchLastAt;

        Snapshot(long ts, long rssKb, long hwmKb, int wakeLocksHeld, Lane rt, Lane batch,
                 String batchLastReason, long batchLastAt) {
            this.ts = ts;
            this.rssKb = rssKb;
            this.hwmKb = hwmKb;
            this.wakeLocksHeld = wakeLocksHeld;
            this.realtimeCount = rt.count();
            this.realtimeAvgMs = rt.avgMs();
            this.realtimeP95Ms = rt.p95Ms();
            this.realtimeCpuMs = rt.cpuMs();
            this.batchCount = batch.count();
            this.batchAvgMs = batch.avgMs();
            this.batchP95Ms = batch.p95Ms();
            this.batchCpuMs = batch.cpuMs();
            this.batchLastReason = batchLastReason;
            this.batchLastAt = batchLastAt;
        }
    }

    private final ProcSource proc;
    private final Lane realtime = new Lane();
    private final Lane batch = new Lane();
    private final long createdAtMs = System.currentTimeMillis();
    private volatile int wakeLocksHeld = 0;
    /** 最近一轮批处理结果原因与时间（每 tick 更新，包括跳过） */
    private volatile String batchLastReason = "not_run_yet";
    private volatile long batchLastAt = 0;

    public Metrics(ProcSource proc) {
        this.proc = proc == null ? new FileProcSource() : proc;
    }

    /** 开始一次计时（CPU 基线 + 墙钟基线） */
    public Timer start() {
        return new Timer(cpuTicks());
    }

    public final class Timer {
        private final long t0 = System.nanoTime();
        private final long cpuTicks0;
        private boolean stopped = false;

        private Timer(long cpuTicks0) { this.cpuTicks0 = cpuTicks0; }

        /** 结束并记账；重复调用无副作用 */
        public void stop(String lane) {
            if (stopped) return;
            stopped = true;
            long durationMs = Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
            long cpuDeltaMs = 0;
            if (cpuTicks0 >= 0) {
                long now = cpuTicks();
                if (now >= 0) cpuDeltaMs = ticksToMs(Math.max(0, now - cpuTicks0));
            }
            laneRecord(lane, durationMs, cpuDeltaMs);
        }
    }

    private void laneRecord(String lane, long durationMs, long cpuMs) {
        if (LANE_BATCH.equals(lane)) batch.record(durationMs, cpuMs);
        else realtime.record(durationMs, cpuMs);
    }

    /** 直接记账（例如批量测试/统计用） */
    public void record(String lane, long durationMs, long cpuMs) {
        laneRecord(lane, durationMs, cpuMs);
    }

    public Snapshot snapshot() {
        String status = proc.read("/proc/self/status");
        return new Snapshot(System.currentTimeMillis(),
                kbFromStatus(status, "VmRSS"),
                kbFromStatus(status, "VmHWM"),
                wakeLocksHeld, realtime, batch, batchLastReason, batchLastAt);
    }

    /** 记录最近一轮批处理结果（每 tick 都记，包括跳过；供 metrics.txt 定位"为什么没跑"） */
    public void recordBatchOutcome(String reason, long ts) {
        this.batchLastReason = reason == null || reason.isEmpty() ? "unknown" : reason;
        this.batchLastAt = ts <= 0 ? System.currentTimeMillis() : ts;
    }

    public String batchLastReason() { return batchLastReason; }

    public long batchLastAt() { return batchLastAt; }

    public void setWakeLocksHeld(int n) { this.wakeLocksHeld = Math.max(0, n); }

    public long processCpuTicks() { return cpuTicks(); }

    public static long ticksToMs(long ticks) { return ticks <= 0 ? 0 : ticks * 1000L / CLK_TCK; }

    /** 导出成扁平文本（adb pull 后直接看/喂脚本） */
    public String exportText() {
        Snapshot s = snapshot();
        StringBuilder b = new StringBuilder(512);
        b.append("# dsh redaction metrics\n");
        b.append("ts=").append(s.ts).append('\n');
        b.append("runtime_ms=").append(System.currentTimeMillis() - createdAtMs).append('\n');
        b.append("process_rss_kb=").append(s.rssKb).append('\n');
        b.append("process_hwm_kb=").append(s.hwmKb).append('\n');
        b.append("wake_locks_held=").append(s.wakeLocksHeld).append('\n');
        b.append("realtime_count=").append(s.realtimeCount).append('\n');
        b.append("realtime_avg_ms=").append(s.realtimeAvgMs).append('\n');
        b.append("realtime_p95_ms=").append(s.realtimeP95Ms).append('\n');
        b.append("realtime_cpu_ms=").append(s.realtimeCpuMs).append('\n');
        b.append("batch_count=").append(s.batchCount).append('\n');
        b.append("batch_avg_ms=").append(s.batchAvgMs).append('\n');
        b.append("batch_p95_ms=").append(s.batchP95Ms).append('\n');
        b.append("batch_cpu_ms=").append(s.batchCpuMs).append('\n');
        b.append("batch_last_reason=").append(s.batchLastReason).append('\n');
        b.append("batch_last_at=").append(s.batchLastAt).append('\n');
        return b.toString();
    }

    public String summaryLine(Snapshot s) {
        return "rt n=" + s.realtimeCount + " avg=" + s.realtimeAvgMs + "ms p95=" + s.realtimeP95Ms
                + "ms cpu=" + s.realtimeCpuMs + "ms | batch n=" + s.batchCount
                + " avg=" + s.batchAvgMs + "ms p95=" + s.batchP95Ms + "ms cpu=" + s.batchCpuMs
                + "ms | rss=" + s.rssKb + "kB hwm=" + s.hwmKb + "kB";
    }

    /** 写文件；失败返回 false（观测不能反过来影响主流程） */
    public boolean writeTo(File f) {
        if (f == null) return false;
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(exportText());
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------- /proc 解析（包内可见，单测直接测） ----------------

    /**
     * /proc/self/stat 的 utime + stime（单位 tick）。
     * 字段 2 是进程名，可能含空格/括号，所以从最后一个 ')' 之后开始数；
     * 之后第一个 token 是字段 3(state)，utime 字段 14 → 下标 11，stime 15 → 下标 12。
     */
    long cpuTicks() {
        String stat = proc.read("/proc/self/stat");
        return parseCpuTicks(stat);
    }

    static long parseCpuTicks(String stat) {
        if (stat == null) return -1;
        int rp = stat.lastIndexOf(')');
        if (rp < 0 || rp + 1 >= stat.length()) return -1;
        String[] f = stat.substring(rp + 1).trim().split("\\s+");
        if (f.length < 13) return -1;
        try {
            return Long.parseLong(f[11]) + Long.parseLong(f[12]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** /proc/self/status 里 "VmHWM:   123456 kB" 的数值（kB）；读不到返回 -1 */
    static long kbFromStatus(String status, String key) {
        if (status == null || key == null) return -1;
        for (String line : status.split("\\r?\\n")) {
            if (!line.startsWith(key)) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String rest = line.substring(colon + 1).trim();
            int sp = rest.indexOf(' ');
            String num = sp < 0 ? rest : rest.substring(0, sp);
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
