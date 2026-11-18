package com.dsh.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用 GET /v1/models 探测本地 llama-server 是否在跑。
 * 超时很短（≤1.5s），避免健康探测拖住调用方；云端 provider 不主动探测
 * （会产生无关请求），它们的可用性由真实调用结果更新。
 *
 * 失败原因必须带出来（ConnectException/超时/HTTP 状态码/主线程违规），
 * 不能只返回 false —— 否则真机上完全无法诊断。
 */
public final class HttpProviderProbe implements ProviderProbe {

    private static final int MAX_PROBE_TIMEOUT_MS = 1_500;
    private final HttpTransport http;

    public HttpProviderProbe(HttpTransport http) {
        this.http = http;
    }

    @Override
    public ProbeResult probe(ProviderConfig cfg) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            if (!cfg.apiKey.isEmpty()) headers.put("Authorization", "Bearer " + cfg.apiKey);
            int timeout = Math.min(cfg.timeoutMs, MAX_PROBE_TIMEOUT_MS);
            HttpTransport.Response r = http.get(cfg.modelsEndpoint(), headers, timeout);
            if (r.isOk()) return ProbeResult.up();
            return ProbeResult.down("http-" + r.status,
                    "HTTP " + r.status + " " + OpenAiChatCodec.errorSnippet(r.body), r.status);
        } catch (Throwable t) {
            return ProbeResult.of(t);
        }
    }
}
