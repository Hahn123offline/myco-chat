package com.dsh.redaction;

import android.content.Context;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.Module;
import com.dsh.host.api.data.Episode;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.events.EpisodeClosed;
import com.dsh.host.api.events.EpisodeRedactionUpgraded;
import com.dsh.host.api.events.EpisodeRedacted;
import com.dsh.host.api.llm.LlmService;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 脱敏模块：usage trace 的 Sink，两级脱敏。
 *
 * 实时层（EpisodeClosed，忙时）：
 *   1. 规则层先跑（快、确定、抓形态 + 校验位）；
 *   2. **立刻**发布 RULE 级 EpisodeRedacted —— 主流程不等模型；
 *   3. 若模型服务健康，异步补一轮模型脱敏；成功就发 EpisodeRedactionUpgraded 就地升级。
 *      模型慢/挂/返回垃圾 → 什么都不发生，块保持 RULE，等充电批处理。
 *
 * 批处理层（充电 + 息屏 + 电量 > 阈值）：抽 RULE 块，模型补充后 UPDATE 成 MODEL。
 *
 * 边界：不拼 prompt 具体措辞（可配），不自己发 HTTP（一律经 LlmService），
 * 模型只是"补充掩码"，能不能采纳由规则校验决定。
 */
public final class RedactionModule implements Module {

    /** 这些应用的上下文默认算高隐私（含第三方对话内容） */
    private static final Set<String> HIGH_PRIVACY_PKGS = new java.util.HashSet<>(Arrays.asList(
            "com.tencent.mm", "com.tencent.mobileqq", "com.android.mms"));

    /** app 为 null 时只做实时规则层（单测/无安卓环境） */
    private final Context app;
    private volatile Health health = Health.stopped();

    private HostContext ctx;
    private ModelRedactor modelRedactor;
    private Metrics metrics;
    private BatchRedactor batch;
    private PowerState power;
    private ExecutorService worker;
    private final AtomicBoolean batchBusy = new AtomicBoolean(false);

    private long processed = 0;
    private long withSecrets = 0;
    private long modelRequests = 0;
    private long modelUpgrades = 0;
    private long modelRejected = 0;
    private volatile String lastMetricsPath = "";

    public RedactionModule() { this(null); }

    public RedactionModule(Context app) { this.app = app; }

    @Override public String id() { return "redaction"; }
    @Override public String version() { return "1.1.0-dev"; }

    @Override public List<Capability> requires() {
        return Collections.emptyList();   // 纯本地规则 + 模型（经 LlmService 转发，不直接出网）
    }

    @Override
    public void install(HostContext ctx) {
        this.ctx = ctx;
        metrics = new Metrics(new Metrics.FileProcSource());
        modelRedactor = new ModelRedactor(modelConfig(ctx),
                () -> ctx.require(LlmService.class), ctx.log());

        ctx.on(EpisodeClosed.class, ev -> onEpisodeClosed(ctx, ev));

        if (app != null) {
            power = new AndroidPowerState(app);
            batch = new BatchRedactor(() -> ctx.require(MemoryQuery.class),
                    () -> ctx.require(LlmService.class), modelRedactor, power, metrics,
                    ctx::config, ctx.log());
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "redaction-batch");
                t.setDaemon(true);
                return t;
            });
            ctx.effect(() -> {
                ExecutorService w = worker;
                if (w != null) w.shutdownNow();
            });

            long batchPeriod = Math.max(10_000L, ctx.config().getLong("redact.batch.periodMs", 60_000L));
            ctx.schedule("redact-batch", batchPeriod, this::maybeRunBatch);
            // 周期 ≤60s（默认 30s）；install 时立刻先写一次，避免"刚装完看不到文件"
            long metricsPeriod = Math.max(10_000L, ctx.config().getLong("metrics.periodMs", 30_000L));
            ctx.schedule("redact-metrics", metricsPeriod, this::scheduleMetricsExport);
            scheduleMetricsExport();

            ctx.log().i(id(), "batch scheduler on: charging+screen-off+battery>"
                    + ctx.config().getInt("redact.batch.minBattery", 50)
                    + " period=" + batchPeriod + "ms"
                    + ", metrics period=" + metricsPeriod + "ms");
        }

        ctx.log().i(id(), "installed (rule layer live; model layer async)");
        health = Health.ok();
    }

    // ---------------- 实时层 ----------------

    private void onEpisodeClosed(HostContext ctx, EpisodeClosed ev) {
        Episode e = ev.episode;
        try {
            Metrics.Timer timer = metrics != null ? metrics.start() : null;

            RuleLayer.Result r = RuleLayer.apply(e.textSnippet);
            String tier = decideTier(e.pkg, r);

            // 先落规则级：主流程不等模型（哪怕模型 3 秒后才回来也不影响入库）
            ctx.bus().publish(new EpisodeRedacted(e.id, e.pkg, r.text, summary(r), tier,
                    ev.evidenceRef, RedactionLevel.RULE));
            processed++;
            if (r.hasSecret()) withSecrets++;

            LlmService llm = ctx.require(LlmService.class);
            boolean modelEligible = llm != null
                    && ctx.config().getBoolean("redact.model.enabled", true)
                    && e.textSnippet != null
                    && e.textSnippet.length() <= Math.max(1,
                            ctx.config().getInt("redact.maxChars", 2_000))
                    && llm.health().isHealthy();

            if (modelEligible) {
                modelRequests++;
                modelRedactor.redactAsync(r.text, r.maskedTokens, res -> {
                    if (timer != null) timer.stop(Metrics.LANE_REALTIME);
                    if (res.level == RedactionLevel.MODEL) {
                        modelUpgrades++;
                        // 就地升级同一条块；记忆层找不到（还没落库）就交给批处理兜
                        ctx.bus().publish(new EpisodeRedactionUpgraded(
                                e.id, res.text, RedactionLevel.MODEL));
                        ctx.log().i(id(), "episode " + e.id + " -> MODEL (+"
                                + res.addedMasks + " masks)");
                    } else {
                        modelRejected++;
                        if (!res.rejectReason.isEmpty()) {
                            ctx.log().w(id(), "model 结果未采纳 episode " + e.id + ": "
                                    + res.rejectReason);
                        }
                    }
                });
            } else {
                if (timer != null) timer.stop(Metrics.LANE_REALTIME);
                if (llm == null) {
                    ctx.log().w(id(), "无 LlmService，episode " + e.id + " 保持 RULE");
                }
            }
        } catch (Throwable t) {
            ctx.log().e(id(), "redact failed for " + e.id, t);
        }
    }

    // ---------------- 批处理 / 观测 ----------------

    /** 主线程 tick：只做便宜的触发检查；条件不满足也打日志（task-10：可定位"为什么没跑"） */
    private void maybeRunBatch() {
        BatchRedactor b = batch;
        ExecutorService w = worker;
        if (b == null || w == null) return;
        try {
            if (batchBusy.get()) {
                b.markSkipped("busy");
                return;
            }
            if (!b.shouldRunNow()) return;      // 内部已打 batch ran=false reason=…
            if (!batchBusy.compareAndSet(false, true)) {
                b.markSkipped("busy");
                return;
            }
            w.execute(() -> {
                try {
                    b.runOnce();
                } catch (Throwable t) {
                    // 后台线程异常也必须留痕（否则又变成"没跑但不知道为什么"）
                    ctx.log().e(id(), "batch run threw", t);
                    b.markSkipped("exception:" + t.getClass().getSimpleName());
                } finally {
                    batchBusy.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            batchBusy.set(false);
            b.markSkipped("rejected");
        }
    }

    /** 周期导出观测数据到模块目录 + 日志；失败不影响脱敏 */
    private void scheduleMetricsExport() {
        Metrics m = metrics;
        ExecutorService w = worker;
        if (m == null || w == null) return;
        try {
            w.execute(() -> {
                try {
                    // 本模块不持有唤醒锁；如实记 0（字段保留，接入时直接填）
                    m.setWakeLocksHeld(0);
                    Metrics.Snapshot s = m.snapshot();
                    File dir = ctx != null && ctx.store() != null ? ctx.store().dir(id()) : null;
                    if (dir != null) {
                        File f = new File(dir, "metrics.txt");
                        if (m.writeTo(f)) lastMetricsPath = f.getAbsolutePath();
                    }
                    if (ctx != null) {
                        ctx.log().i(id(), m.summaryLine(s)
                                + (lastMetricsPath.isEmpty() ? "" : " -> " + lastMetricsPath));
                    }
                } catch (Throwable t) {
                    if (ctx != null) ctx.log().e(id(), "metrics export failed", t);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // 已卸载
        }
    }

    /** 状态页用：最近一次 metrics 落盘路径（空 = 还没写成功） */
    public String metricsPath() { return lastMetricsPath; }

    // ---------------- 工具 ----------------

    /**
     * 模型层配置：prompt 优先 redaction.properties，其次 models.properties（评测后写在模型配置里），
     * 都没有就用 ModelRedactor 的极简占位。其余字段（maxTokens/maxChars…）仍读本模块配置。
     */
    private static java.util.function.Supplier<com.dsh.host.api.Config> modelConfig(HostContext ctx) {
        return () -> {
            com.dsh.host.api.Config own = ctx.config();
            com.dsh.host.api.Config models = null;
            try {
                models = ctx.configOf("models");
            } catch (Throwable ignored) {
                // 无宿主/测试环境：退回只读自己的配置
            }
            return new PromptConfig(own, models);
        };
    }

    private static final class PromptConfig implements com.dsh.host.api.Config {
        private final com.dsh.host.api.Config own;
        private final com.dsh.host.api.Config models;

        PromptConfig(com.dsh.host.api.Config own, com.dsh.host.api.Config models) {
            this.own = own;
            this.models = models;
        }

        @Override public String getString(String k, String d) {
            if ("prompt.redact".equals(k)) {
                String v = own == null ? null : own.getString(k, null);
                if (v != null && !v.trim().isEmpty()) return v;
                String m = models == null ? null : models.getString(k, null);
                if (m != null && !m.trim().isEmpty()) return m;
                return d;
            }
            if (own != null) return own.getString(k, d);
            return models == null ? d : models.getString(k, d);
        }

        @Override public int getInt(String k, int d) { return own == null ? d : own.getInt(k, d); }
        @Override public long getLong(String k, long d) { return own == null ? d : own.getLong(k, d); }
        @Override public boolean getBoolean(String k, boolean d) { return own == null ? d : own.getBoolean(k, d); }

        @Override public boolean has(String k) {
            if ("prompt.redact".equals(k)) {
                return (own != null && own.has(k)) || (models != null && models.has(k));
            }
            return own != null && own.has(k);
        }
    }

    private String decideTier(String pkg, RuleLayer.Result r) {
        if (r.hasSecret()) return EpisodeRedacted.TIER_HIGH;
        if (pkg != null && HIGH_PRIVACY_PKGS.contains(pkg)) return EpisodeRedacted.TIER_HIGH;
        return EpisodeRedacted.TIER_LOW;
    }

    private String summary(RuleLayer.Result r) {
        StringBuilder sb = new StringBuilder();
        for (RuleLayer.Type t : RuleLayer.Type.values()) {
            int n = r.count(t);
            if (n > 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(t.name()).append(':').append(n);
            }
        }
        return sb.toString();
    }

    public long processed() { return processed; }
    public long withSecrets() { return withSecrets; }
    public long modelRequests() { return modelRequests; }
    public long modelUpgrades() { return modelUpgrades; }
    public long modelRejected() { return modelRejected; }

    /** 观测数据（状态页/测试） */
    public Metrics metrics() { return metrics; }

    /** 批处理（未装 Android 环境时为 null） */
    public BatchRedactor batch() { return batch; }

    /** 最近一次批处理报告（可查询的进度/失败计数） */
    public BatchRedactor.Report lastBatchReport() {
        BatchRedactor b = batch;
        return b == null ? null : b.lastReport();
    }

    @Override
    public Health health() {
        Health h = health;
        if (h.isHealthy() && processed > 0) {
            return Health.ok();
        }
        return h;
    }
}
