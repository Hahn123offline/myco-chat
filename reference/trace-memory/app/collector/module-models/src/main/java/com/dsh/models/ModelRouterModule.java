package com.dsh.models;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.Module;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import java.util.Collections;
import java.util.List;

/**
 * 模型路由插件：把 {@link ModelRouter} 装进宿主。
 *
 * install 里做三件事：
 *  1. ctx.provide(LlmService.class, router) —— 全 app 的模型调用唯一出口；
 *  2. ctx.effect(router::close) —— 卸载时关线程池（Cordis 式反向释放）；
 *  3. ctx.schedule(健康探测) —— 周期探测本地 provider，health 必须来自事实。
 *
 * install 不联网：首次探测异步发起，本地模型起得慢也不能拖住宿主启动。
 */
public final class ModelRouterModule implements Module {

    public static final String ID = "models";
    public static final String VERSION = "1.0.0-dev";

    private final HttpTransport http;
    /** null = 用 HttpProviderProbe（生产路径）；测试注入假探测 */
    private final ProviderProbe probe;

    private volatile ModelRouter router;

    public ModelRouterModule() {
        this(new UrlConnectionTransport(), null);
    }

    public ModelRouterModule(HttpTransport http, ProviderProbe probe) {
        this.http = http;
        this.probe = probe;
    }

    @Override public String id() { return ID; }
    @Override public String version() { return VERSION; }

    @Override public List<Capability> requires() {
        // 本模块是全 app 唯一出网点
        return Collections.singletonList(Capability.NETWORK);
    }

    @Override
    public void install(HostContext ctx) {
        ProviderProbe p = probe != null ? probe : new HttpProviderProbe(http);

        ModelRouter r = new ModelRouter(ctx::config, cfg -> {
            if (cfg.local()) return new LocalLlamaProvider(cfg, http);
            return new OpenAiCompatProvider(cfg, http);
        }, p, ctx.log());

        router = r;
        ctx.provide(LlmService.class, r);
        ctx.effect(r::close);

        long period = Math.max(5_000L, ctx.config().getLong("health.probeMs", 30_000L));
        // 关键：ctx.schedule 的任务跑在主线程；探测必须 probeAsync 提交到后台线程，
        // 否则主线程发 HTTP → NetworkOnMainThreadException → 被吞成 DOWN（真机踩过）。
        ctx.schedule("models-health", period, r::probeAsync);
        r.probeAsync();

        RouterConfig rc = RouterConfig.parse(ctx.config());
        ctx.log().i(ID, "installed; providers=" + rc.providers.keySet()
                + (rc.problems.isEmpty() ? "" : " problems=" + rc.problems));
    }

    @Override
    public Health health() {
        ModelRouter r = router;
        return r == null ? Health.stopped() : r.health();
    }

    /** 给状态页 / 测试用；未安装或已卸载时为 null */
    public ModelRouter router() { return router; }

    /** 便捷：同步取服务（宿主已安装时非 null） */
    public static LlmService service(HostContext ctx) {
        return ctx.require(LlmService.class);
    }

    /** 仅供测试/状态页：直接跑一次完整调用并返回响应 */
    public static LlmResponse callSync(LlmService svc, com.dsh.host.api.llm.LlmRequest req,
                                       long timeoutMs) throws InterruptedException {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final LlmResponse[] out = new LlmResponse[1];
        svc.complete(req, r -> {
            out[0] = r;
            latch.countDown();
        });
        latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        return out[0] == null ? LlmResponse.error("callSync 等待超时") : out[0];
    }
}
