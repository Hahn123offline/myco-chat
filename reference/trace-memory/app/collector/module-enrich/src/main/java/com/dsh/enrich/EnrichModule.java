package com.dsh.enrich;

import android.content.Context;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.Module;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.llm.LlmService;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 云端打标模块：周期取 WHITE 未打标块，用 LlmService 生成"这一屏在讲什么"的 label。
 *
 * 线程模型（task-8 教训）：`ctx.schedule` 的回调跑在**主线程**。
 * 所以这里只做 submit，真正取数/HTTP/落库都在单线程 `enrich-worker` 上跑；
 * install 里那次立即打标也走同一线程，不阻塞宿主启动。
 */
public final class EnrichModule implements Module {

    public static final String ID = "enrich";
    private static final String WORKER_NAME = "enrich-worker";

    private final Context app;
    private final Enricher.Network injectedNetwork;
    private volatile HostContext ctx;
    private volatile Enricher enricher;
    private volatile ExecutorService worker;
    private volatile Enricher.Report lastReport;

    public EnrichModule(Context app) {
        this(app, null);
    }

    /** 测试用：注入 Network，避免 JVM 里碰 Android ConnectivityManager */
    EnrichModule(Context app, Enricher.Network network) {
        this.app = app;
        this.injectedNetwork = network;
    }

    @Override public String id() { return ID; }

    @Override public String version() { return "0.1.0-dev"; }

    @Override public List<Capability> requires() {
        return Collections.singletonList(Capability.NETWORK);
    }

    @Override
    public void install(HostContext ctx) {
        this.ctx = ctx;
        Enricher.Network network = injectedNetwork != null ? injectedNetwork : new AndroidNetwork(app);
        enricher = new Enricher(ctx::config, this::promptConfigOf,
                () -> requireSafe(MemoryQuery.class),
                () -> requireSafe(LlmService.class),
                () -> requireSafe(EgressPolicy.class),
                ctx.log(), network, Thread::sleep, System::currentTimeMillis);
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, WORKER_NAME);
            t.setDaemon(true);
            return t;
        });
        ctx.effect(() -> {
            ExecutorService w = worker;
            if (w != null) w.shutdownNow();
        });

        long period = Math.max(10_000L, ctx.config().getLong("enrich.periodMs", Enricher.DEFAULT_PERIOD_MS));
        ctx.schedule("enrich", period, this::submit);
        ctx.log().i(ID, "installed; periodMs=" + period
                + " batch=" + ctx.config().getInt("enrich.batch", Enricher.DEFAULT_BATCH)
                + " requireWifi=" + ctx.config().getBoolean("enrich.requireWifi", false)
                + " promptConfig=" + LabelPrompt.KEY_PROMPT);
        // 立即在后台跑一轮（不阻塞 install；没有可打标的块时就是一次空转）
        submit();
    }

    /** 只提交，不在这里执行（主线程不能发 HTTP / 做重活） */
    void submit() {
        ExecutorService w = worker;
        Enricher e = enricher;
        HostContext c = ctx;
        if (w == null || e == null || c == null) return;
        try {
            w.execute(() -> {
                Enricher.Report r = e.runOnce();
                lastReport = r;
                if (!r.ran()) {
                    c.log().i(ID, "round skipped: " + r.skipReason);
                }
            });
        } catch (Throwable t) {
            c.log().e(ID, "submit failed", t);
        }
    }

    /** 最近一轮结果（状态页/测试用；null = 还没跑完） */
    public Enricher.Report lastReport() { return lastReport; }

    @Override
    public Health health() {
        HostContext c = ctx;
        if (c == null) return Health.stopped();
        if (requireSafe(LlmService.class) == null) return Health.degraded("no-llm");
        if (requireSafe(EgressPolicy.class) == null) return Health.degraded("no-egress-policy");
        MemoryQuery mq = requireSafe(MemoryQuery.class);
        if (mq == null) return Health.degraded("no-memory");
        try {
            List<MemoryBlock> white = mq.byEgressTier(EgressTier.WHITE, 1);
            if (white == null || white.isEmpty()) return Health.degraded("no-whitelist");
        } catch (Throwable t) {
            return Health.degraded("memory query failed: " + t.getClass().getSimpleName());
        }
        Enricher.Report r = lastReport;
        return Health.ok("label enrichment ready" + (r == null ? "" : "; last=" + r));
    }

    private <T> T requireSafe(Class<T> api) {
        HostContext c = ctx;
        if (c == null) return null;
        try {
            return c.require(api);
        } catch (Throwable t) {
            return null;
        }
    }

    /** prompt 的公共家：models.properties（`prompt.label`），拿不到返回 null */
    private com.dsh.host.api.Config promptConfigOf() {
        HostContext c = ctx;
        if (c == null) return null;
        try {
            return c.configOf("models");
        } catch (Throwable t) {
            return null;
        }
    }
}
