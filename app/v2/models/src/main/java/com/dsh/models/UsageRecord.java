package com.dsh.models;

/** 一次调用的 usage 审计（本地文件/日志）。 */
public final class UsageRecord {

    public final long atMs;
    public final String taskId;
    public final String providerId;
    public final ProviderKind kind;
    public final com.dsh.egress.EgressTier tier;
    public final int promptTokens;
    public final int completionTokens;
    public final int promptCacheHitTokens;
    public final long latencyMs;
    public final boolean ok;
    public final String error;

    public UsageRecord(long atMs, String taskId, String providerId, ProviderKind kind,
                       com.dsh.egress.EgressTier tier, int promptTokens, int completionTokens,
                       int promptCacheHitTokens, long latencyMs, boolean ok, String error) {
        this.atMs = atMs;
        this.taskId = taskId == null ? "" : taskId;
        this.providerId = providerId == null ? "" : providerId;
        this.kind = kind;
        this.tier = tier;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.promptCacheHitTokens = promptCacheHitTokens;
        this.latencyMs = latencyMs;
        this.ok = ok;
        this.error = error == null ? "" : error;
    }

    public String toLine() {
        return "usage task=" + taskId
                + " provider=" + providerId
                + " kind=" + kind
                + " tier=" + tier
                + " prompt_tokens=" + promptTokens
                + " completion_tokens=" + completionTokens
                + " prompt_cache_hit_tokens=" + promptCacheHitTokens
                + " latency_ms=" + latencyMs
                + " ok=" + ok
                + (error.isEmpty() ? "" : " error=" + error);
    }
}
