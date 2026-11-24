package com.dsh.models;

import com.dsh.core.Health;
import com.dsh.core.HostContext;
import com.dsh.core.Module;
import com.dsh.egress.EgressGate;
import com.dsh.egress.EgressResult;
import com.dsh.egress.EgressTier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 模型路由模块：装配 provider、出网闸、usage 审计，向宿主提供 ModelRouter。
 *
 * key 由 {@link ApiKeyProvider}（Android SecretStore）注入；配置里没有 key，
 * 日志/health 也永远不出现 key。
 */
public final class ModelsModule implements Module {

    public static final String ID = "models";
    public static final String VERSION = "1.0.0";

    private final ApiKeyProvider keys;
    private final EgressGate gate;
    private final UsageSink usage;
    private final HttpTransport http;

    private volatile HostContext ctx;
    private volatile ModelRouter router;
    private volatile ModelsConfig config;

    public ModelsModule(ApiKeyProvider keys, EgressGate gate, UsageSink usage, HttpTransport http) {
        this.keys = keys;
        this.gate = gate;
        this.usage = usage;
        this.http = http == null ? new UrlConnectionTransport() : http;
    }

    @Override public String id() { return ID; }
    @Override public String version() { return VERSION; }

    @Override public List<com.dsh.core.Capability> requires() {
        List<com.dsh.core.Capability> l = new ArrayList<>();
        l.add(com.dsh.core.Capability.NETWORK);
        return l;
    }

    @Override public void install(HostContext ctx) {
        this.ctx = ctx;
        reload();
        ctx.provide(ModelRouter.class, router);
    }

    /** 配置/密钥变化后重建 provider（UI 保存后调用） */
    public synchronized void reload() {
        HostContext c = ctx;
        ModelsConfig mc = ModelsConfig.parse(c != null ? c.config()
                : new com.dsh.core.MapConfig(java.util.Collections.emptyMap()));
        List<ModelProvider> providers = new ArrayList<>();
        for (ModelsConfig.ProviderDef def : mc.providers) {
            if (def.kind != ProviderKind.CLOUD) {
                // 端侧 1B 下一轮接入；现在不伪造 LOCAL provider
                continue;
            }
            String key = keys == null ? "" : keys.keyFor(def.name);
            providers.add(new OpenAiCompatProvider(def.name, def.baseUrl, key, def.model,
                    def.timeoutMs, def.caps, def.maxContext, def.costClass, http, gate, usage));
        }
        router = new ModelRouter(providers, mc.allowGrayCloud, gate, usage);
        config = mc;
        if (c != null) {
            c.log().i(ID, "router rebuilt providers=" + providers.size()
                    + (mc.problems.isEmpty() ? "" : " problems=" + mc.problems));
        }
    }

    public ModelRouter router() { return router; }

    public ModelsConfig config() { return config; }

    public Set<Capability> caps(String providerId) {
        for (ModelProvider p : router == null ? Collections.<ModelProvider>emptyList() : router.providers()) {
            if (p.id().equals(providerId)) return p.caps();
        }
        return Collections.emptySet();
    }

    /** UI「测试连接」：真发一次最小请求，返回延迟/模型名；失败原因不含 key。 */
    public ModelResponse testConnection(String providerId, ModelRequest req) {
        ModelRouter r = router;
        if (r == null) return ModelResponse.error("模型模块未就绪", providerId);
        ModelProvider chosen = null;
        for (ModelProvider p : r.providers()) {
            if (p.id().equals(providerId)) { chosen = p; break; }
        }
        if (chosen == null) return ModelResponse.error("provider 不存在: " + providerId, providerId);
        if (chosen.cloud()) {
            // 这里原来用 gate.check(..., sourcePackage=null)：conn 测试的 payload 走的是
            // 「来源未知」那条，provenance 判定被整条绕过，等于留了一个任意内容上云的后门。
            // 改成和 ModelRouter 同一条通路，provenance 必须由调用方声明。
            EgressResult gr = gate.checkProvenance("connect.test", req.systemPrompt, req.userPrompt,
                    EgressTier.WHITE, chosen.id(), true,
                    req.sources, req.stage,
                    req.excludedBlocks, req.appPolicy, req.maskedSpans, req.maskedKinds);
            if (!gr.allowed) return ModelResponse.error("egress: " + gr.reason, providerId);
            req = req.withPrompts(gr.sanitizedSystem, gr.sanitizedUser);
        }
        long t0 = System.nanoTime();
        ModelResponse resp;
        try {
            resp = chosen.complete(req);
        } catch (Throwable t) {
            resp = ModelResponse.error(t.getClass().getSimpleName() + ": " + t.getMessage(), providerId);
        }
        if (resp == null) resp = ModelResponse.error("provider 返回 null", providerId);
        if (resp.latencyMs <= 0) {
            resp = resp.withLatency(Math.max(0, (System.nanoTime() - t0) / 1_000_000L));
        }
        if (usage != null) {
            usage.record(new UsageRecord(System.currentTimeMillis(), "connect.test", providerId,
                    chosen.kind(), EgressTier.WHITE, resp.promptTokens, resp.completionTokens,
                    resp.promptCacheHitTokens, resp.latencyMs, resp.isOk(), resp.error));
        }
        return resp;
    }

    @Override public Health health() {
        ModelRouter r = router;
        ModelsConfig mc = config;
        if (r == null || mc == null) return Health.stopped();
        if (r.providers().isEmpty()) {
            return Health.degraded("no-cloud-provider configured"
                    + (mc.problems.isEmpty() ? "" : "：" + String.join("；", mc.problems)));
        }
        List<String> noKey = new ArrayList<>();
        for (ModelProvider p : r.providers()) {
            if (p.cloud()) {
                String k = keys == null ? "" : keys.keyFor(p.id());
                if (k == null || k.trim().isEmpty()) noKey.add(p.id());
            }
        }
        if (!noKey.isEmpty()) {
            return Health.degraded("api-key-missing:" + String.join(",", noKey));
        }
        return Health.ok("providers=" + r.providers().size());
    }
}
