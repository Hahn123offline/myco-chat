package com.dsh.models;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任意 OpenAI 兼容云端端点（DeepSeek / OpenAI / vLLM / 自建网关）。
 * 差异只在 baseUrl / model / key，全部来自构造参数；不为每家写类。
 */
public class OpenAiCompatProvider implements ModelProvider {

    private final String id;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final java.util.Set<Capability> caps;
    private final int maxContext;
    private final CostClass costClass;
    private final HttpTransport http;
    private final com.dsh.egress.EgressGate gate;
    private final UsageSink usage;

    public OpenAiCompatProvider(String id, String baseUrl, String apiKey, String model,
                                int timeoutMs, java.util.Set<Capability> caps,
                                int maxContext, CostClass costClass, HttpTransport http) {
        this(id, baseUrl, apiKey, model, timeoutMs, caps, maxContext, costClass, http,
                null, null);
    }

    /** 云端 provider 必须带 gate；不带 gate 的实例 complete() 直接安全拒绝。 */
    public OpenAiCompatProvider(String id, String baseUrl, String apiKey, String model,
                                int timeoutMs, java.util.Set<Capability> caps,
                                int maxContext, CostClass costClass, HttpTransport http,
                                com.dsh.egress.EgressGate gate, UsageSink usage) {
        this.id = id;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 60_000;
        this.caps = caps == null ? java.util.Collections.emptySet()
                : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(caps));
        this.maxContext = maxContext;
        this.costClass = costClass == null ? CostClass.CHEAP : costClass;
        this.http = http;
        this.gate = gate;
        this.usage = usage;
    }

    @Override public String id() { return id; }
    @Override public ProviderKind kind() { return ProviderKind.CLOUD; }
    @Override public java.util.Set<Capability> caps() { return caps; }
    @Override public int maxContext() { return maxContext; }
    @Override public CostClass costClass() { return costClass; }

    @Override public ModelResponse complete(ModelRequest req) throws Exception {
        if (gate == null) {
            return ModelResponse.error("安全拒绝：云端 provider 缺少 EgressGate", id);
        }
        com.dsh.egress.EgressResult gr = gate.checkProvenance("provider", req.systemPrompt,
                req.userPrompt, com.dsh.egress.EgressTier.WHITE, id, true,
                req.sources, req.strippedSources, req.stage, req.excludedBlocks,
                req.appPolicy, req.maskedSpans, req.maskedKinds, req.maskStatus);
        if (!gr.allowed) return ModelResponse.error("egress: " + gr.reason, id);
        if (model.isEmpty()) return ModelResponse.error("provider " + id + " 未配置 model", id);
        String body = OpenAiChatCodec.buildRequest(model, req);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (!apiKey.isEmpty()) headers.put("Authorization", "Bearer " + apiKey);

        long t0 = System.nanoTime();
        HttpTransport.Response r = http.post(chatEndpoint(), headers,
                body.getBytes(StandardCharsets.UTF_8), timeoutMs);
        long ms = elapsedMs(t0);
        if (!r.isOk()) {
            return ModelResponse.error("HTTP " + r.status + ": "
                    + OpenAiChatCodec.errorSnippet(r.body), id).withLatency(ms);
        }
        return OpenAiChatCodec.parseResponse(r.body, model).withLatency(ms).withProvider(id);
    }

    public String chatEndpoint() { return baseUrl + "/v1/chat/completions"; }

    public static String normalizeBaseUrl(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    /** 密钥不进 toString/日志 */
    @Override public String toString() {
        return "OpenAiCompat{" + id + ", " + baseUrl + ", model=" + model + "}";
    }
}
