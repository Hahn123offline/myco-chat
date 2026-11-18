package com.dsh.models;

import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任意云端 OpenAI 兼容端点（DeepSeek / OpenAI / vLLM / 自建网关…）。
 *
 * 同一套 /v1/chat/completions 协议；差异只在 baseUrl / apiKey / model，
 * 全部来自配置，不需要为每家写一个类。
 */
public class OpenAiCompatProvider implements LlmProvider {

    protected final ProviderConfig cfg;
    protected final HttpTransport http;

    public OpenAiCompatProvider(ProviderConfig cfg, HttpTransport http) {
        this.cfg = cfg;
        this.http = http;
    }

    @Override
    public LlmResponse complete(LlmRequest req) throws Exception {
        if (cfg.model == null || cfg.model.isEmpty()) {
            return LlmResponse.error("provider " + cfg.name + " 未配置 model");
        }
        String body = OpenAiChatCodec.buildRequest(cfg.model, req);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (!cfg.apiKey.isEmpty()) headers.put("Authorization", "Bearer " + cfg.apiKey);

        long t0 = System.nanoTime();
        HttpTransport.Response r = http.post(cfg.chatEndpoint(), headers,
                body.getBytes(StandardCharsets.UTF_8), cfg.timeoutMs);
        long ms = elapsedMs(t0);
        if (!r.isOk()) {
            return LlmResponse.error("HTTP " + r.status + ": "
                    + OpenAiChatCodec.errorSnippet(r.body), cfg.model).withLatency(ms);
        }
        return OpenAiChatCodec.parseResponse(r.body, cfg.model).withLatency(ms);
    }

    static long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
