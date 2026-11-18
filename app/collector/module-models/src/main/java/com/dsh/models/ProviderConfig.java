package com.dsh.models;

/**
 * 一个 provider 的静态配置（从 config/models.properties 解析而来）。
 * 不可变；路由可以覆盖 model，但 baseUrl / apiKey / 超时属于 provider 自身。
 */
public final class ProviderConfig {

    public enum Type {
        /** 本机推理（llama-server 等），并发上限 1，默认 127.0.0.1:8080 */
        LOCAL,
        /** 任意云端 OpenAI 兼容端点，需要 baseUrl + apiKey */
        OPENAI
    }

    public final String name;
    public final Type type;
    /** 不带尾部 '/' */
    public final String baseUrl;
    /** 可为空（本地通常不需要；云端通常需要） */
    public final String apiKey;
    /** provider 默认模型；路由里写了 model 就以路由为准 */
    public final String model;
    public final int timeoutMs;

    public ProviderConfig(String name, Type type, String baseUrl, String apiKey,
                          String model, int timeoutMs) {
        this.name = name;
        this.type = type;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 60_000;
    }

    public boolean local() { return type == Type.LOCAL; }

    public String chatEndpoint() { return baseUrl + "/v1/chat/completions"; }

    public String modelsEndpoint() { return baseUrl + "/v1/models"; }

    public ProviderConfig withModel(String m) {
        return new ProviderConfig(name, type, baseUrl, apiKey, m, timeoutMs);
    }

    static String normalizeBaseUrl(String url) {
        String s = url == null ? "" : url.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    public String toString() {
        return "Provider{" + name + ", " + type + ", " + baseUrl
                + ", model=" + model + ", timeout=" + timeoutMs + "ms}";
    }
}
