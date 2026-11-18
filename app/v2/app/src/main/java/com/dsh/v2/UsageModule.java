package com.dsh.v2;

import android.content.Context;

import com.dsh.core.Health;
import com.dsh.core.HostContext;
import com.dsh.core.Log;
import com.dsh.core.Module;
import com.dsh.trace.SkipPolicy;
import com.dsh.trace.Trace;
import com.dsh.usage.UsageDeduper;
import com.dsh.usage.UsageEvent;
import com.dsh.usage.UsageSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UsageStats 降级数据源（task-17）。
 *
 * 为什么存在：MIUI 的 a11y 会 bound-but-deaf；断流期间用 UsageStatsManager 至少能记录
 * 「哪个 app 在前台、从 t0 到 t1」。没有 pageId/text/shot，但比"什么都没有"好。
 *
 * 设计：
 *  - 只在 a11y 未启用或断流时采样（正常时 standby，省电、不双份）；
 *  - 写入同一张 traces 表，source="usage"；
 *  - 与 a11y 痕迹按 pkg + 时间重叠 >80% 去重；
 *  - 未授权 → Health=DEGRADED(usage_stats_not_granted)，不假装 OK；
 *  - 采样/查询在独立 usage-worker 上跑，主线程不碰数据库。
 */
public final class UsageModule implements Module {

    public static final String ID = "usage";

    private final Context app;
    private final Log log;
    private final UsageSampler sampler = new UsageSampler();
    private ExecutorService worker;
    private SkipPolicy skipPolicy = SkipPolicy.none();
    private volatile long lastQueryTo = 0;
    private volatile String mode = "not_started";
    private final AtomicLong spans = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong skippedDedupe = new AtomicLong();
    private final AtomicLong skippedPolicy = new AtomicLong();

    public UsageModule(Context app, Log log) {
        this.app = app;
        this.log = log;
    }

    @Override public String id() { return ID; }

    @Override public String version() { return "0.1.0"; }

    @Override public List<com.dsh.core.Capability> requires() {
        return java.util.Collections.emptyList();
    }

    @Override public void install(HostContext ctx) {
        skipPolicy = SkipPackages.build(app);
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "usage-worker");
            t.setDaemon(true);
            return t;
        });
        ctx.schedule("usage-sample", 60_000L, () -> {
            ExecutorService w = worker;
            if (w != null) w.execute(this::sample);
        });
        worker.execute(this::sample);
        ctx.effect(() -> {
            ExecutorService w = worker;
            if (w != null) w.shutdownNow();
        });
        log.i(ID, "installed; granted=" + UsageStatsSource.granted(app));
    }

    /** 采样一轮：60s 窗口的前台/后台事件 → 区间 → 去重 → 写 traces（source=usage） */
    private void sample() {
        long now = System.currentTimeMillis();
        if (!UsageStatsSource.granted(app)) {
            mode = "not_granted";
            log.w(ID, "usage_stats_not_granted（在系统设置里授权「使用情况访问」后才启用降级采样）");
            return;
        }
        TraceModule trace = TraceRuntime.get();
        if (trace == null || trace.store() == null) {
            mode = "no_trace";
            return;
        }
        // 判据以「最近 60s 是否真的收到 a11y 事件」为准，不使用系统声明列表
        // （MIUI 上系统列表可能不可靠；有事件就说明主路在工作，不需要双份采）
        boolean recentA11yEvent = trace.hasRecentA11yEvent();
        boolean shouldSample = !recentA11yEvent;

        if (!shouldSample) {
            List<UsageSampler.Span> open = sampler.flush(now);
            write(open, trace);
            mode = "standby";
            return;
        }
        mode = "sampling(no_recent_events)";

        long from = lastQueryTo <= 0 ? now - 60_000L : lastQueryTo;
        // standby 很久后再进入断流：查询窗口不要跨几小时，只回看最近 5 分钟
        if (from < now - 5 * 60_000L) from = now - 60_000L;
        if (from >= now) from = now - 60_000L;
        lastQueryTo = now;
        List<UsageEvent> events = UsageStatsSource.events(app, from, now);
        List<UsageSampler.Span> closed = sampler.onEvents(now, events);
        write(closed, trace);
        log.i(ID, "sample mode=" + mode + " events=" + events.size()
                + " spans=" + spans.get() + " written=" + written.get()
                + " dedupe=" + skippedDedupe.get());
    }

    private void write(List<UsageSampler.Span> list, TraceModule trace) {
        if (list == null || list.isEmpty()) return;
        for (UsageSampler.Span span : list) {
            spans.incrementAndGet();
            if (skipPolicy.shouldSkip(span.pkg)) {
                skippedPolicy.incrementAndGet();
                continue;
            }
            List<UsageDeduper.Covered> covered = new ArrayList<>();
            for (Trace t : trace.store().tracesBetween(span.t0, span.t1, "a11y")) {
                covered.add(new UsageDeduper.Covered(t.pkg, t.t0, t.t1));
            }
            if (UsageDeduper.shouldSkip(span, covered)) {
                skippedDedupe.incrementAndGet();
                continue;
            }
            Trace t = new Trace();
            t.t0 = span.t0;
            t.t1 = span.t1;
            t.dwellMs = (int) span.dwellMs();
            t.pkg = span.pkg;
            t.activity = "";
            t.pageId = "";
            t.text = "";
            t.shotRef = null;
            t.guard = 0;
            t.guardHit = null;
            t.cloudAt = 0;
            t.label = null;
            t.maskedText = "";
            t.source = "usage";
            try {
                trace.store().insert(t);
                written.incrementAndGet();
            } catch (Throwable e) {
                log.e(ID, "usage persist failed (" + e.getClass().getSimpleName() + ")", e);
            }
        }
    }

    public boolean granted() { return UsageStatsSource.granted(app); }

    public String statusLine() {
        return "usage_granted=" + granted()
                + " usage_mode=" + mode
                + " usage_spans=" + spans.get()
                + " usage_written=" + written.get()
                + " usage_dedupe=" + skippedDedupe.get()
                + " usage_skipped_policy=" + skippedPolicy.get();
    }

    @Override public Health health() {
        if (!UsageStatsSource.granted(app)) {
            return Health.degraded("usage_stats_not_granted");
        }
        return Health.ok("usage " + statusLine());
    }
}
