package com.dsh.usage;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 UsageStatsManager 的前台/后台事件流采样成「某 app 在前台的连续时间段」。
 *
 * 纯逻辑、假时钟可测。usage 是降级源：只给出 pkg + t0/t1，没有 pageId/text/shot。
 * 采样跨 60s 周期保持 open 状态；app 切换/退到后台时闭合 span。
 */
public final class UsageSampler {

    public static final long DEFAULT_MIN_DWELL_MS = 1_000L;

    /** 闭合后的使用区间 */
    public static final class Span {
        public final String pkg;
        public final long t0;
        public final long t1;

        public Span(String pkg, long t0, long t1) {
            this.pkg = pkg;
            this.t0 = t0;
            this.t1 = t1;
        }

        public long dwellMs() { return Math.max(0L, t1 - t0); }

        @Override public String toString() {
            return pkg + "[" + t0 + "," + t1 + ")";
        }
    }

    private final long minDwellMs;
    private String openPkg;
    private long openSince;

    public UsageSampler() { this(DEFAULT_MIN_DWELL_MS); }

    public UsageSampler(long minDwellMs) {
        this.minDwellMs = Math.max(0L, minDwellMs);
    }

    /** 输入应按 ts 升序；返回本次闭合的 span（未闭合的留在内部） */
    public List<Span> onEvents(long now, List<UsageEvent> events) {
        List<Span> out = new ArrayList<>();
        if (events == null) return out;
        for (UsageEvent e : events) {
            if (e == null || e.pkg.isEmpty() || e.ts > now) continue;
            if (e.foreground) {
                if (openPkg != null && !openPkg.equals(e.pkg)) {
                    close(openPkg, openSince, e.ts, out);
                }
                if (openPkg == null || !openPkg.equals(e.pkg)) {
                    openPkg = e.pkg;
                    openSince = e.ts;
                }
            } else {
                if (openPkg != null && openPkg.equals(e.pkg)) {
                    close(openPkg, openSince, e.ts, out);
                }
            }
        }
        return out;
    }

    /** 主动结束当前 open 区间（例如 a11y 恢复、停止降级采样时） */
    public List<Span> flush(long endTs) {
        List<Span> out = new ArrayList<>();
        if (openPkg != null && endTs > openSince) {
            close(openPkg, openSince, endTs, out);
        }
        return out;
    }

    public String openPkg() { return openPkg; }

    public long openSince() { return openSince; }

    private void close(String pkg, long t0, long t1, List<Span> out) {
        openPkg = null;
        openSince = 0;
        if (t1 - t0 < minDwellMs) return;      // 瞬时切换：噪声，丢弃
        out.add(new Span(pkg, t0, t1));
    }

    public long minDwellMs() { return minDwellMs; }
}
