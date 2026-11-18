package com.dsh.egress;

/**
 * 一次出网审计记录（本地可查）。
 *
 * **不存原文**：只存任务名、provider、tier、脱敏动作计数、payload 长度与 SHA-1。
 * 这样审计不会变成第二个敏感数据副本。
 */
public final class AuditEntry {

    public final long atMs;
    public final String taskId;
    public final String providerId;
    public final boolean cloud;
    public final EgressTier declaredTier;
    public final EgressTier effectiveTier;
    public final boolean allowed;
    public final String blockedKind;
    public final int maskCount;
    public final int excludedBlocks;
    public final int maskedSpans;
    public final String appPolicy;
    public final int maskerSpans;
    public final String maskerKinds;
    public final int payloadChars;
    public final String payloadSha1;
    public final String reason;
    /** provenance：payload 阶段与来源，便于事后回答“为什么放行”。 */
    public final String stage;
    public final String sources;
    public final String effectivePolicy;
    public final int sourcesCount;
    public final String strippedSources;
    public final String maskStatus;

    public AuditEntry(long atMs, String taskId, String providerId, boolean cloud,
                      EgressTier declaredTier, EgressTier effectiveTier, boolean allowed,
                      String blockedKind, int maskCount, int payloadChars,
                      String payloadSha1, String reason) {
        this(atMs, taskId, providerId, cloud, declaredTier, effectiveTier, allowed,
                blockedKind, maskCount, 0, "REDACT_THEN_CLOUD",
                payloadChars, payloadSha1, reason);
    }

    public AuditEntry(long atMs, String taskId, String providerId, boolean cloud,
                      EgressTier declaredTier, EgressTier effectiveTier, boolean allowed,
                      String blockedKind, int maskCount, int excludedBlocks,
                      String appPolicy, int payloadChars, String payloadSha1, String reason) {
        this(atMs, taskId, providerId, cloud, declaredTier, effectiveTier, allowed,
                blockedKind, maskCount, excludedBlocks, appPolicy,
                payloadChars, payloadSha1, reason, 0, "");
    }

    public AuditEntry(long atMs, String taskId, String providerId, boolean cloud,
                      EgressTier declaredTier, EgressTier effectiveTier, boolean allowed,
                      String blockedKind, int maskCount, int excludedBlocks,
                      String appPolicy, int payloadChars, String payloadSha1, String reason,
                      int maskerSpans, String maskerKinds) {
        this(atMs, taskId, providerId, cloud, declaredTier, effectiveTier, allowed,
                blockedKind, maskCount, excludedBlocks, appPolicy, payloadChars, payloadSha1,
                reason, maskerSpans, maskerKinds, "RAW", "", appPolicy, 0);
    }

    public AuditEntry(long atMs, String taskId, String providerId, boolean cloud,
                      EgressTier declaredTier, EgressTier effectiveTier, boolean allowed,
                      String blockedKind, int maskCount, int excludedBlocks,
                      String appPolicy, int payloadChars, String payloadSha1, String reason,
                      int maskerSpans, String maskerKinds, String stage, String sources,
                      String effectivePolicy, int sourcesCount) {
        this(atMs, taskId, providerId, cloud, declaredTier, effectiveTier, allowed,
                blockedKind, maskCount, excludedBlocks, appPolicy, payloadChars, payloadSha1,
                reason, maskerSpans, maskerKinds, stage, sources, effectivePolicy,
                sourcesCount, "");
    }

    public AuditEntry(long atMs, String taskId, String providerId, boolean cloud,
                      EgressTier declaredTier, EgressTier effectiveTier, boolean allowed,
                      String blockedKind, int maskCount, int excludedBlocks,
                      String appPolicy, int payloadChars, String payloadSha1, String reason,
                      int maskerSpans, String maskerKinds, String stage, String sources,
                      String effectivePolicy, int sourcesCount, String strippedSources) {
        this(atMs, taskId, providerId, cloud, declaredTier, effectiveTier, allowed,
                blockedKind, maskCount, excludedBlocks, appPolicy, payloadChars, payloadSha1,
                reason, maskerSpans, maskerKinds, stage, sources, effectivePolicy,
                sourcesCount, strippedSources, "");
    }

    public AuditEntry(long atMs, String taskId, String providerId, boolean cloud,
                      EgressTier declaredTier, EgressTier effectiveTier, boolean allowed,
                      String blockedKind, int maskCount, int excludedBlocks,
                      String appPolicy, int payloadChars, String payloadSha1, String reason,
                      int maskerSpans, String maskerKinds, String stage, String sources,
                      String effectivePolicy, int sourcesCount, String strippedSources,
                      String maskStatus) {
        this.atMs = atMs;
        this.taskId = taskId == null ? "" : taskId;
        this.providerId = providerId == null ? "" : providerId;
        this.cloud = cloud;
        this.declaredTier = declaredTier;
        this.effectiveTier = effectiveTier;
        this.allowed = allowed;
        this.blockedKind = blockedKind;
        this.maskCount = maskCount;
        this.excludedBlocks = Math.max(0, excludedBlocks);
        this.maskedSpans = Math.max(maskCount, maskerSpans);
        this.appPolicy = appPolicy == null || appPolicy.isEmpty()
                ? "REDACT_THEN_CLOUD" : appPolicy;
        this.maskerSpans = Math.max(0, maskerSpans);
        this.maskerKinds = maskerKinds == null ? "" : maskerKinds;
        this.payloadChars = payloadChars;
        this.payloadSha1 = payloadSha1 == null ? "" : payloadSha1;
        this.reason = reason == null ? "" : reason;
        this.stage = stage == null ? "RAW" : stage;
        this.sources = sources == null ? "" : sources;
        this.effectivePolicy = effectivePolicy == null ? "" : effectivePolicy;
        this.sourcesCount = Math.max(0, sourcesCount);
        this.strippedSources = strippedSources == null ? "" : strippedSources;
        this.maskStatus = maskStatus == null ? "" : maskStatus;
    }

    /** 单行可落盘格式：不包含任何原文/密钥。 */
    public String toJsonLine() {
        return "{\"at\":" + atMs
                + ",\"task\":" + q(taskId)
                + ",\"provider\":" + q(providerId)
                + ",\"cloud\":" + cloud
                + ",\"tier\":" + q(declaredTier == null ? null : declaredTier.name())
                + ",\"effectiveTier\":" + q(effectiveTier == null ? null : effectiveTier.name())
                + ",\"allowed\":" + allowed
                + ",\"blockedKind\":" + (blockedKind == null ? "null" : q(blockedKind))
                + ",\"maskCount\":" + maskCount
                + ",\"excludedBlocks\":" + excludedBlocks
                + ",\"maskedSpans\":" + maskedSpans
                + ",\"maskerSpans\":" + maskerSpans
                + ",\"maskerKinds\":" + q(maskerKinds)
                + ",\"appPolicy\":" + q(appPolicy)
                + ",\"payloadChars\":" + payloadChars
                + ",\"payloadSha1\":" + q(payloadSha1)
                + ",\"reason\":" + q(reason)
                + ",\"stage\":" + q(stage)
                + ",\"sources\":" + q(sources)
                + ",\"effectivePolicy\":" + q(effectivePolicy)
                + ",\"sourcesCount\":" + sourcesCount
                + ",\"strippedSources\":" + q(strippedSources)
                + ",\"maskStatus\":" + q(maskStatus)
                + "}";
    }

    /** 最小 JSON 字符串转义（避免 egress 反向依赖 models.Json） */
    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 8).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
}
