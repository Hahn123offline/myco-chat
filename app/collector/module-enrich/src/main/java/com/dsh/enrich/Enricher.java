package com.dsh.enrich;

import com.dsh.host.api.Config;
import com.dsh.host.api.Log;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.data.LabelStatus;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 云端打标的核心逻辑（不碰 android.*，纯 JVM 可测）。
 *
 * **隐私不变量（唯一底线）**：只处理 egressTier=WHITE 的块。
 *  - 取数：MemoryQuery.blocksNeedingLabel 的 SQL 已经写死 egress_tier=WHITE；
 *  - 发送前再校验一次 block.egressTier == WHITE（防御性双闸），UNKNOWN/GRAY/BLACK 一律不发；
 *  - 拿不到 EgressPolicy 就整轮不做（绝不"没有策略就默认放行"）；
 *  - 发送的只有 block.maskedText（已脱敏），不发原文/截图。
 *
 * 失败处理：单块失败记录 streak；连续 {@link #MAX_FAIL_STREAK} 次 → SKIPPED，不再选中。
 * 输出校验：见 {@link LabelValidator}（空/超长/纯标点/复读都算 FAILED，不落 label）。
 */
public final class Enricher {

    public static final String TAG = "enrich";
    public static final String TASK_ID = "label";

    public static final int DEFAULT_BATCH = 10;
    public static final int DEFAULT_PERIOD_MS = 300_000;
    public static final int DEFAULT_SLEEP_MS = 1_000;
    public static final int DEFAULT_TIMEOUT_MS = 90_000;
    public static final int DEFAULT_MAX_TOKENS = 48;
    public static final int MAX_FAIL_STREAK = 3;

    /** 网络条件（requireWifi 用）；不引 Android 依赖，便于单测 */
    public interface Network {
        boolean isWifi();
    }

    /** 条间限流（单测注入 no-op） */
    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    /** 一轮结果 */
    public static final class Report {
        public final int picked;
        public final int ok;
        public final int failed;
        public final int skipped;
        /** null = 正常执行；否则是"整轮跳过"的原因（disabled/no-wifi/no-egress-policy/no-llm/no-memory） */
        public final String skipReason;

        Report(int picked, int ok, int failed, int skipped, String skipReason) {
            this.picked = picked;
            this.ok = ok;
            this.failed = failed;
            this.skipped = skipped;
            this.skipReason = skipReason;
        }

        public boolean ran() { return skipReason == null; }

        @Override public String toString() {
            return skipReason != null
                    ? "skipped(" + skipReason + ")"
                    : "picked=" + picked + " ok=" + ok + " failed=" + failed + " skipped=" + skipped;
        }
    }

    private final Supplier<Config> config;
    /** prompt 配置来源：优先模块自己的 enrich.properties，其次 models.properties（prompt 的公共家） */
    private final Supplier<Config> promptConfig;
    private final Supplier<MemoryQuery> memory;
    private final Supplier<LlmService> llm;
    private final Supplier<EgressPolicy> policy;
    private final Log log;
    private final Network network;
    private final Sleeper sleeper;
    private final LongSupplier clock;
    /** 连续失败计数（进程内；SKIPPED 会落库，重启后不会再选中） */
    private final Map<Long, Integer> failStreak = new ConcurrentHashMap<>();

    public Enricher(Supplier<Config> config, Supplier<MemoryQuery> memory,
                    Supplier<LlmService> llm, Supplier<EgressPolicy> policy,
                    Log log, Network network, Sleeper sleeper, LongSupplier clock) {
        this(config, config, memory, llm, policy, log, network, sleeper, clock);
    }

    public Enricher(Supplier<Config> config, Supplier<Config> promptConfig,
                    Supplier<MemoryQuery> memory, Supplier<LlmService> llm,
                    Supplier<EgressPolicy> policy, Log log, Network network,
                    Sleeper sleeper, LongSupplier clock) {
        this.config = config;
        this.promptConfig = promptConfig;
        this.memory = memory;
        this.llm = llm;
        this.policy = policy;
        this.log = log;
        this.network = network;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    /** 跑一轮：取最多 batch 条 WHITE 未打标块逐条打标。任何异常都不外抛（不拖垮宿主）。 */
    public Report runOnce() {
        try {
            return doRun();
        } catch (Throwable t) {
            log.e(TAG, "runOnce threw", t);
            return new Report(0, 0, 0, 0, "exception:" + t.getClass().getSimpleName());
        }
    }

    private Report doRun() {
        Config c = config.get();
        if (c == null) return skip("no-config");
        if (!c.getBoolean("enrich.enabled", true)) return skip("disabled");
        if (c.getBoolean("enrich.requireWifi", false) && (network == null || !network.isWifi())) {
            return skip("no-wifi");
        }
        // 没有策略就什么都不做：绝不默认放行
        if (policy == null || policy.get() == null) return skip("no-egress-policy");
        LlmService service = llm == null ? null : llm.get();
        if (service == null) return skip("no-llm");
        MemoryQuery mq = memory == null ? null : memory.get();
        if (mq == null) return skip("no-memory");

        int batch = clamp(c.getInt("enrich.batch", DEFAULT_BATCH), 1, 50);
        int sleepMs = clamp(c.getInt("enrich.sleepMs", DEFAULT_SLEEP_MS), 0, 60_000);
        int timeoutMs = clamp(c.getInt("enrich.timeoutMs", DEFAULT_TIMEOUT_MS), 1_000, 300_000);
        int maxTokens = clamp(c.getInt("enrich.maxTokens", DEFAULT_MAX_TOKENS), 8, 256);
        String template = resolveTemplate(c);

        List<MemoryBlock> blocks = mq.blocksNeedingLabel(batch);
        int picked = 0, ok = 0, failed = 0, skipped = 0;
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                MemoryBlock b = blocks.get(i);
                if (b == null) continue;
                // 双闸：即便取数 SQL 已过滤，这里也不允许非 WHITE 出网（UNKNOWN ≠ WHITE）
                if (b.egressTier != EgressTier.WHITE) {
                    log.w(TAG, "refuse non-white id=" + b.id + " tier=" + b.egressTier);
                    continue;
                }
                if (b.maskedText == null || b.maskedText.trim().isEmpty()) {
                    failed++;
                    recordFailure(mq, b, "", "empty-text");
                    continue;
                }
                picked++;
                LlmResponse r = call(service, LabelPrompt.request(template, b.maskedText, maxTokens), timeoutMs);
                if (r == null || !r.isOk()) {
                    failed++;
                    recordFailure(mq, b, r == null ? "" : r.model, r == null ? "null-response" : r.error);
                } else {
                    String label = LabelValidator.clean(r.text);
                    String problem = LabelValidator.problem(label, b.maskedText);
                    if (problem != null) {
                        failed++;
                        recordFailure(mq, b, r.model, problem);
                    } else {
                        int rows = mq.updateLabel(b.id, label, r.model, clock.getAsLong(), LabelStatus.OK);
                        if (rows > 0) {
                            ok++;
                            failStreak.remove(b.id);
                        } else {
                            failed++;
                            log.w(TAG, "updateLabel 0 rows id=" + b.id);
                        }
                    }
                }
                if (sleepMs > 0 && i < blocks.size() - 1) {
                    try {
                        sleeper.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        Report report = new Report(picked, ok, failed, skipped, null);
        log.i(TAG, "picked=" + picked + " ok=" + ok + " failed=" + failed + " skipped=" + skipped
                + " batch=" + batch);
        return report;
    }

    /** 单块失败：streak+1，写 FAILED；达到上限写 SKIPPED（之后取数 SQL 会排除） */
    private void recordFailure(MemoryQuery mq, MemoryBlock b, String model, String reason) {
        int streak = failStreak.merge(b.id, 1, Integer::sum);
        int status = streak >= MAX_FAIL_STREAK ? LabelStatus.SKIPPED : LabelStatus.FAILED;
        try {
            mq.updateLabel(b.id, null, model, clock.getAsLong(), status);
        } catch (Throwable t) {
            log.e(TAG, "updateLabel failed id=" + b.id, t);
        }
        log.w(TAG, "label failed id=" + b.id + " streak=" + streak
                + " status=" + LabelStatus.name(status) + " reason=" + reason);
    }

    /** 同步桥接：路由器的回调可能来自任意线程，这里等结果但带超时上界 */
    private LlmResponse call(LlmService service, LlmRequest req, int timeoutMs) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LlmResponse> out = new AtomicReference<>();
        try {
            service.complete(req, r -> {
                out.set(r);
                latch.countDown();
            });
        } catch (Throwable t) {
            return LlmResponse.error("complete threw: " + t.getClass().getSimpleName());
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return LlmResponse.error("timeout-" + timeoutMs + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LlmResponse.error("interrupted");
        }
        return out.get();
    }

    /** 自己的配置优先；没写就回落到 models.properties（prompt 的公共家）；都没有用默认 */
    private String resolveTemplate(Config own) {
        if (own != null && own.has(LabelPrompt.KEY_PROMPT)) return LabelPrompt.template(own);
        try {
            Config models = promptConfig == null ? null : promptConfig.get();
            if (models != null && models.has(LabelPrompt.KEY_PROMPT)) return LabelPrompt.template(models);
        } catch (Throwable ignored) {
            // 读别的模块配置失败不能影响打标
        }
        return LabelPrompt.template(own);
    }

    private Report skip(String reason) {
        log.i(TAG, "skip: " + reason);
        return new Report(0, 0, 0, 0, reason);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
