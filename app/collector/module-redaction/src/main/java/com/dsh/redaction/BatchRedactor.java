package com.dsh.redaction;

import com.dsh.host.api.Config;
import com.dsh.host.api.Health;
import com.dsh.host.api.Log;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.llm.LlmService;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 批处理层：充电 + 息屏 + 电量足够时，把 RULE 级的记忆块用模型升级成 MODEL 级。
 *
 * 为什么放在这个时间窗：模型推理（尤其本地大模型）吃 CPU/GPU、会发热，
 * 只有在"插着电 + 屏幕不亮 + 电量充足"时做，用户感知最小。触发条件必须可判定，
 * 不靠"猜用户闲不闲"。
 *
 * **可观测性是硬要求**（task-10）：每轮 tick 都要打一行
 * `batch ran=false reason=screen_on picked=0 upgraded=0 failed=0`，
 * 条件不满足也要打；原因同时写进 metrics.txt 的 `batch_last_reason/at`。
 * 之前只在真正跑的时候才 log，导致真机上"为什么没跑"只能猜。
 *
 * 测试钩子：`redact.batch.debugForce=true` 跳过电源条件（仍要求 LlmService 可用、
 * 仍只处理 RULE 档），日志 reason 显式写 `forced_debug`。
 *
 * 安全边界：
 *  - 只挑 {@link RedactionLevel#RULE} 的块；
 *  - 每条升级都经 {@link ModelRedactor} 的 span 保护 + 输出校验；
 *  - 更新走 MemoryQuery.updateRedaction（实现里卡"只升不降"），0 行说明没升级成功；
 *  - 每条之间 sleep（可配），避免连续推理把温度打上去；
 *  - 任何异常只记失败计数，不中断整批、不影响主流程。
 */
public final class BatchRedactor {

    /** 一次批处理的结果（可查询，供状态页/日志/测试断言） */
    public static final class Report {
        public final long ts;
        public final boolean ran;
        /** ran=false 时的跳过原因（not_charging / screen_on / battery_xx / llm_not_available …） */
        public final String reason;
        public final int picked;
        public final int upgraded;
        public final int failed;
        public final long elapsedMs;

        Report(boolean ran, String reason, int picked, int upgraded, int failed, long elapsedMs) {
            this.ts = System.currentTimeMillis();
            this.ran = ran;
            this.reason = reason == null ? "" : reason;
            this.picked = picked;
            this.upgraded = upgraded;
            this.failed = failed;
            this.elapsedMs = elapsedMs;
        }

        /** 固定格式，便于 grep：batch ran=true/false reason=… picked=… upgraded=… failed=… [cost=…] */
        public String summary() {
            return ran
                    ? "batch ran=true reason=" + reason + " picked=" + picked
                            + " upgraded=" + upgraded + " failed=" + failed
                            + " cost=" + elapsedMs + "ms"
                    : "batch ran=false reason=" + reason + " picked=" + picked
                            + " upgraded=" + upgraded + " failed=" + failed;
        }
    }

    private final Supplier<MemoryQuery> memory;
    private final Supplier<LlmService> llm;
    private final ModelRedactor redactor;
    private final PowerState power;
    private final Metrics metrics;
    private final Supplier<Config> configs;
    private final Log log;

    private volatile Report lastReport =
            new Report(false, "not_run_yet", 0, 0, 0, 0);

    public BatchRedactor(Supplier<MemoryQuery> memory, Supplier<LlmService> llm,
                         ModelRedactor redactor, PowerState power, Metrics metrics,
                         Supplier<Config> configs, Log log) {
        this.memory = memory;
        this.llm = llm;
        this.redactor = redactor;
        this.power = power;
        this.metrics = metrics;
        this.configs = configs;
        this.log = log;
    }

    /** 只检查"电源条件"（便宜、可在主线程 tick 里调），不发请求、不碰数据库 */
    public boolean triggersSatisfied() {
        return powerSkipReason(configs.get()) == null;
    }

    /**
     * 主线程 tick 的唯一入口：条件不满足时**打日志并记录原因**，返回 false；
     * 满足时返回 true（调用方再把 {@link #runOnce()} 丢到后台线程）。
     */
    public boolean shouldRunNow() {
        long t0 = System.nanoTime();
        Config c = configs.get();
        String reason = skipReason(c);
        if (reason == null) return true;
        finish(false, reason, 0, 0, 0, t0);
        return false;
    }

    /** 忙/线程池拒绝等"这一 tick 没跑"的显式记录 */
    public Report markSkipped(String reason) {
        return finish(false, reason == null ? "skipped" : reason, 0, 0, 0, System.nanoTime());
    }

    /** 跑一轮。返回本次报告；跳过时 ran=false 并带原因。 */
    public Report runOnce() {
        long t0 = System.nanoTime();
        Config c = configs.get();
        String skip = skipReason(c);
        if (skip != null) return finish(false, skip, 0, 0, 0, t0);

        boolean forced = c.getBoolean("redact.batch.debugForce", false);
        int max = clamp(c.getInt("redact.batch.maxPerRun", 20), 1, 200);
        long timeoutMs = Math.max(500L, c.getLong("redact.timeoutMs", 20_000L));
        long sleepMs = Math.max(0L, c.getLong("redact.batch.sleepMs", 1_500L));
        LlmService svc = llm.get();
        MemoryQuery mq = memory.get();

        List<MemoryBlock> blocks;
        try {
            blocks = mq.byRedactionLevel(RedactionLevel.RULE, max);
        } catch (Throwable t) {
            logE("pick RULE blocks failed", t);
            return finish(false, "pick_failed", 0, 0, 0, t0);
        }

        int picked = blocks == null ? 0 : blocks.size();
        int upgraded = 0, failed = 0;
        for (int i = 0; i < picked; i++) {
            MemoryBlock b = blocks.get(i);
            Metrics.Timer timer = metrics != null ? metrics.start() : null;
            boolean ok = false;
            try {
                ModelRedactor.Result r = redactor.redactBlocking(
                        b.maskedText, Collections.emptyList(), timeoutMs);
                if (r != null && r.level == RedactionLevel.MODEL) {
                    int rows = mq.updateRedaction(b.episodeId, r.text,
                            RedactionLevel.MODEL, System.currentTimeMillis());
                    ok = rows > 0;
                    if (!ok) logW("upgrade 0 rows episode=" + b.episodeId);
                } else if (r != null) {
                    logW("model rejected episode=" + b.episodeId + ": " + r.rejectReason);
                }
            } catch (Throwable t) {
                logE("batch item failed episode=" + (b == null ? -1 : b.episodeId), t);
            } finally {
                if (timer != null) timer.stop(Metrics.LANE_BATCH);
            }
            if (ok) upgraded++; else failed++;

            // 两条之间歇一下：连续推理会持续拉高 CPU/温度
            if (i < picked - 1 && sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logW("batch interrupted at " + i);
                    break;
                }
            }
        }
        return finish(true, forced ? "forced_debug" : "ok", picked, upgraded, failed, t0);
    }

    public Report lastReport() { return lastReport; }

    // ---------------- 条件判断 ----------------

    /** 电源/开关条件（不含依赖服务）；debugForce 时跳过电源条件 */
    private String powerSkipReason(Config c) {
        if (c == null) return "no_config";
        if (!c.getBoolean("redact.batch.enabled", true)) return "disabled";
        if (c.getBoolean("redact.batch.debugForce", false)) return null;   // 只跳过电源条件
        if (!power.isCharging()) return "not_charging";
        if (!power.isScreenOff()) return "screen_on";
        int pct = power.batteryPercent();
        int minBattery = c.getInt("redact.batch.minBattery", 50);
        if (pct <= minBattery) return "battery_" + pct + "_le_" + minBattery;
        return null;
    }

    /** 完整跳过原因（电源 + 依赖服务）；null = 可以跑 */
    private String skipReason(Config c) {
        String powerReason = powerSkipReason(c);
        if (powerReason != null) return powerReason;

        LlmService svc = llm.get();
        if (svc == null) return "no_llm_service";
        Health h = svc.health();
        if (h == null || h.state == Health.State.FAILED || h.state == Health.State.STOPPED) {
            return "llm_not_available:" + h;
        }
        if (memory.get() == null) return "no_memory_service";
        return null;
    }

    // ---------------- 日志 / 指标 ----------------

    private Report finish(boolean ran, String reason, int picked, int upgraded, int failed, long t0) {
        long elapsed = Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
        Report r = new Report(ran, reason, picked, upgraded, failed, elapsed);
        lastReport = r;
        if (metrics != null) {
            try {
                metrics.recordBatchOutcome(r.reason, r.ts);
            } catch (Throwable ignored) {
                // 观测失败不能影响主流程
            }
        }
        if (log != null) log.i("redaction", r.summary());
        return r;
    }

    private void logW(String msg) { if (log != null) log.w("redaction", msg); }
    private void logE(String msg, Throwable t) { if (log != null) log.e("redaction", msg, t); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
