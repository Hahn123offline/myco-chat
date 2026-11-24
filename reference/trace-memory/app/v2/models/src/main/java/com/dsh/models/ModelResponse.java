package com.dsh.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 模型调用结果（含 usage、tool_calls、thinking 的 reasoning_content）。 */
public final class ModelResponse {

    public final boolean ok;
    public final String text;
    public final String model;
    public final String providerId;
    public final int promptTokens;
    public final int completionTokens;
    public final int promptCacheHitTokens;
    public final long latencyMs;
    public final String error;
    public final List<ToolCall> toolCalls;
    public final String reasoningContent;

    private ModelResponse(boolean ok, String text, String model, String providerId,
                          int promptTokens, int completionTokens, int promptCacheHitTokens,
                          long latencyMs, String error, List<ToolCall> toolCalls,
                          String reasoningContent) {
        this.ok = ok;
        this.text = text == null ? "" : text;
        this.model = model == null ? "" : model;
        this.providerId = providerId == null ? "" : providerId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.promptCacheHitTokens = promptCacheHitTokens;
        this.latencyMs = latencyMs;
        this.error = error == null ? "" : error;
        this.toolCalls = toolCalls == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    public static ModelResponse ok(String text, String model, String providerId) {
        return new ModelResponse(true, text, model, providerId, 0, 0, 0, 0, "",
                Collections.emptyList(), "");
    }

    public static ModelResponse error(String error, String providerId) {
        return new ModelResponse(false, "", "", providerId, 0, 0, 0, 0, error,
                Collections.emptyList(), "");
    }

    public ModelResponse withUsage(int prompt, int completion, int cacheHit) {
        return new ModelResponse(ok, text, model, providerId, prompt, completion,
                cacheHit, latencyMs, error, toolCalls, reasoningContent);
    }

    public ModelResponse withLatency(long ms) {
        return new ModelResponse(ok, text, model, providerId, promptTokens,
                completionTokens, promptCacheHitTokens, ms, error, toolCalls,
                reasoningContent);
    }

    public ModelResponse withProvider(String id) {
        return new ModelResponse(ok, text, model, id, promptTokens,
                completionTokens, promptCacheHitTokens, latencyMs, error, toolCalls,
                reasoningContent);
    }

    public ModelResponse withToolCalls(List<ToolCall> calls) {
        return new ModelResponse(true, text, model, providerId, promptTokens,
                completionTokens, promptCacheHitTokens, latencyMs, error, calls,
                reasoningContent);
    }

    public ModelResponse withReasoning(String reasoning) {
        return new ModelResponse(ok, text, model, providerId, promptTokens,
                completionTokens, promptCacheHitTokens, latencyMs, error, toolCalls,
                reasoning);
    }

    public boolean isOk() { return ok; }

    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

    @Override public String toString() {
        return "ModelResponse{ok=" + ok + ", model=" + model + ", provider=" + providerId
                + ", prompt=" + promptTokens + ", completion=" + completionTokens
                + ", cacheHit=" + promptCacheHitTokens + ", latency=" + latencyMs + "ms"
                + ", toolCalls=" + toolCalls.size()
                + ", reasoningChars=" + reasoningContent.length()
                + (error.isEmpty() ? "" : ", error=" + error) + "}";
    }
}
