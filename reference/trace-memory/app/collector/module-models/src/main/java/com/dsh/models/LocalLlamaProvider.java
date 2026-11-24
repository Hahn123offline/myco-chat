package com.dsh.models;

/**
 * 本机推理 provider：默认指向 127.0.0.1:8080 的 llama-server（OpenAI 兼容）。
 *
 * 协议与 {@link OpenAiCompatProvider} 完全一致，所以复用同一实现；
 * 独立成类是为了在类型层面把"本地"标出来：
 *  - 它在路由器里走**本地车道**（并发上限 1，超出的请求排队）；
 *  - 它的 baseUrl 默认是本机回环，通常不需要 apiKey；
 *  - health 会主动探测它（GET /v1/models），没起就是 DOWN。
 */
public final class LocalLlamaProvider extends OpenAiCompatProvider {

    public LocalLlamaProvider(ProviderConfig cfg, HttpTransport http) {
        super(cfg, http);
    }
}
