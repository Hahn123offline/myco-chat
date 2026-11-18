package com.dsh.host.api.llm;

/**
 * 一次模型调用的结果。
 *
 * 失败不用异常：error 非空即失败。这样调用方（脱敏 / 记忆 / 标题）能统一地
 * "降级 / 跳过 / 记一笔" 而不必为了一个网络错误把整条链路 abort。
 */
public final class LlmResponse {

    public String text = "";
    /** 实际返回的模型名（由 provider 回报，可能和请求里的不同） */
    public String model = "";
    /** 端到端耗时（从 complete() 被调用到回调触发） */
    public long latencyMs;
    /** 失败原因；空串表示成功 */
    public String error = "";

    private LlmResponse() { }

    public static LlmResponse ok(String text, String model) {
        LlmResponse r = new LlmResponse();
        r.text = text == null ? "" : text;
        r.model = model == null ? "" : model;
        return r;
    }

    public static LlmResponse error(String error) {
        return error(error, "");
    }

    public static LlmResponse error(String error, String model) {
        LlmResponse r = new LlmResponse();
        r.error = error == null ? "unknown error" : error;
        r.model = model == null ? "" : model;
        return r;
    }

    /** 设置耗时并返回自身（路由器用） */
    public LlmResponse withLatency(long ms) {
        this.latencyMs = ms;
        return this;
    }

    public boolean isOk() { return error == null || error.isEmpty(); }

    @Override
    public String toString() {
        return isOk()
                ? "LlmResponse{ok, model=" + model + ", latency=" + latencyMs + "ms, text="
                        + (text.length() > 60 ? text.substring(0, 60) + "…" : text) + "}"
                : "LlmResponse{error=" + error + ", model=" + model + ", latency=" + latencyMs + "ms}";
    }
}
