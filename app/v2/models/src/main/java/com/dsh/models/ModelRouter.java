package com.dsh.models;

import com.dsh.egress.EgressGate;
import com.dsh.egress.EgressResult;
import com.dsh.egress.EgressTier;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型路由器：调用方只声明 need/quality/egress，不指定模型。
 *
 * 硬规则（不做任何"偷偷降级"）：
 *  - BLACK → 只允许 LOCAL 且能力满足；没有本地模型就失败，绝不回落云端
 *  - GRAY  → 优先 LOCAL；云端只在 allowGrayCloud=true 时允许
 *  - WHITE → DRAFT 选 LOCAL、GOOD 选 CLOUD；目标 kind 不存在就失败
 *
 * 云端调用前必须过 {@link EgressGate}；被拒的请求不会到达 provider（也不发网络）。
 * 每次实际调用（成功/失败）都写 usage（含 prompt_cache_hit_tokens）。
 */
public final class ModelRouter {

    public static final class Route {
        public final ModelProvider provider;
        public final String error;

        private Route(ModelProvider p, String e) {
            this.provider = p;
            this.error = e == null ? "" : e;
        }

        public boolean ok() { return provider != null; }
        public static Route of(ModelProvider p) { return new Route(p, ""); }
        public static Route fail(String why) { return new Route(null, why); }
    }

    private final List<ModelProvider> providers;
    private final boolean allowGrayCloud;
    private final EgressGate gate;
    private final UsageSink usage;

    public ModelRouter(List<ModelProvider> providers, boolean allowGrayCloud,
                       EgressGate gate, UsageSink usage) {
        this.providers = providers == null ? new ArrayList<>() : new ArrayList<>(providers);
        this.allowGrayCloud = allowGrayCloud;
        this.gate = gate;
        this.usage = usage;
    }

    public List<ModelProvider> providers() { return new ArrayList<>(providers); }

    public Route route(ModelTask task) {
        if (task == null) return Route.fail("task == null");
        java.util.Set<Capability> need = task.need() == null
                ? java.util.Collections.emptySet() : task.need();
        EgressTier tier = task.egress() == null ? EgressTier.WHITE : task.egress();

        if (tier == EgressTier.BLACK) {
            for (ModelProvider p : providers) {
                if (p.kind() == ProviderKind.LOCAL && p.supports(need)) return Route.of(p);
            }
            return Route.fail("BLACK tier：没有满足能力 " + need + " 的 LOCAL provider（禁止回落云端）");
        }
        if (tier == EgressTier.GRAY) {
            ModelProvider local = first(ProviderKind.LOCAL, need);
            if (local != null) return Route.of(local);
            if (!allowGrayCloud) {
                return Route.fail("GRAY tier：只有云端 provider，但用户未显式允许上云");
            }
            ModelProvider cloud = first(ProviderKind.CLOUD, need);
            return cloud != null ? Route.of(cloud)
                    : Route.fail("GRAY tier：没有满足能力 " + need + " 的 CLOUD provider");
        }
        // WHITE
        ProviderKind want = task.quality() == Quality.GOOD
                ? ProviderKind.CLOUD : ProviderKind.LOCAL;
        ModelProvider p = first(want, need);
        if (p == null) {
            return Route.fail("WHITE tier 需要 " + want + " provider（能力 " + need + "），没有可用项");
        }
        return Route.of(p);
    }

    /** 路由 + 出网闸 + provider 调用；所有结果都记录 usage。 */
    public ModelResponse execute(ModelTask task, ModelRequest req) {
        Route r = route(task);
        if (!r.ok()) return ModelResponse.error("route: " + r.error, "");
        ModelProvider p = r.provider;
        ModelRequest effective = req;

        if (p.cloud()) {
            if (gate == null) {
                return ModelResponse.error("安全拒绝：云端 provider 缺少 EgressGate", p.id());
            }
            if (req.hasMessages()) {
                java.util.List<ChatMessage> safe = new java.util.ArrayList<>();
                for (ChatMessage m : req.messages) {
                    if (m == null) continue;
                    EgressResult gr = gate.checkProvenance(task.taskName(), "", m.content,
                            task.egress(), p.id(), true, req.sources, req.strippedSources,
                            req.stage, req.excludedBlocks, req.appPolicy, req.maskedSpans,
                            req.maskedKinds, req.maskStatus);
                    if (!gr.allowed) {
                        ModelResponse rejected = ModelResponse.error(
                                "egress: " + gr.reason + "（消息角色 " + m.role + "）", p.id());
                        recordRejected(task, p, rejected);
                        return rejected;
                    }
                    safe.add(m.withContent(gr.sanitizedUser));
                }
                effective = new ModelRequest(req.systemPrompt, req.userPrompt, req.jpeg,
                        req.imageDetail, req.maxTokens, req.temperature, safe, req.tools,
                        req.thinking, req.excludedBlocks, req.appPolicy,
                        req.maskedSpans, req.maskedKinds, req.sources, req.stage);
            } else {
                EgressResult gateResult = gate.checkProvenance(task.taskName(),
                        req.systemPrompt, req.userPrompt, task.egress(), p.id(), true,
                        req.sources, req.strippedSources, req.stage,
                        req.excludedBlocks, req.appPolicy, req.maskedSpans, req.maskedKinds,
                        req.maskStatus);
                if (!gateResult.allowed) {
                    ModelResponse rejected = ModelResponse.error("egress: " + gateResult.reason, p.id());
                    recordRejected(task, p, rejected);
                    return rejected;
                }
                effective = req.withPrompts(gateResult.sanitizedSystem, gateResult.sanitizedUser);
            }
        }

        long t0 = System.nanoTime();
        ModelResponse resp;
        try {
            resp = p.complete(effective);
        } catch (Throwable t) {
            resp = ModelResponse.error(p.id() + ": " + describe(t), p.id());
        }
        if (resp == null) resp = ModelResponse.error("provider 返回 null", p.id());
        if (resp.latencyMs <= 0) resp = resp.withLatency(elapsedMs(t0));

        if (usage != null) {
            usage.record(new UsageRecord(System.currentTimeMillis(), task.taskName(), p.id(),
                    p.kind(), task.egress(), resp.promptTokens, resp.completionTokens,
                    resp.promptCacheHitTokens, resp.latencyMs, resp.isOk(), resp.error));
        }
        return resp;
    }

    private ModelProvider first(ProviderKind kind, java.util.Set<Capability> need) {
        for (ModelProvider p : providers) {
            if (p.kind() == kind && p.supports(need)) return p;
        }
        return null;
    }

    private void recordRejected(ModelTask task, ModelProvider p, ModelResponse rejected) {
        if (usage == null) return;
        usage.record(new UsageRecord(System.currentTimeMillis(), task.taskName(), p.id(),
                p.kind(), task.egress(), 0, 0, 0, 0, false, rejected.error));
    }

    private static long elapsedMs(long t0) {
        return Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
    }

    private static String describe(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
    }
}
