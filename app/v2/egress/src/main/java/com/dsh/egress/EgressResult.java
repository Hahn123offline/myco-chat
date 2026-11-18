package com.dsh.egress;

/** EgressGate.check 的结果：失败时不允许发送，语义与原因必须明确。 */
public final class EgressResult {

    public final boolean allowed;
    public final String sanitizedSystem;
    public final String sanitizedUser;
    public final EgressTier declaredTier;
    public final EgressTier effectiveTier;
    public final String blockedKind;
    public final int maskCount;
    public final String reason;
    /** 源 App 在排除名单 → 文本已替换为 [APP_EXCLUDED]。 */
    public final boolean appExcluded;

    EgressResult(boolean allowed, String sanitizedSystem, String sanitizedUser,
                 EgressTier declaredTier, EgressTier effectiveTier,
                 String blockedKind, int maskCount, String reason) {
        this(allowed, sanitizedSystem, sanitizedUser, declaredTier, effectiveTier,
                blockedKind, maskCount, reason, false);
    }

    EgressResult(boolean allowed, String sanitizedSystem, String sanitizedUser,
                 EgressTier declaredTier, EgressTier effectiveTier,
                 String blockedKind, int maskCount, String reason, boolean appExcluded) {
        this.allowed = allowed;
        this.sanitizedSystem = sanitizedSystem == null ? "" : sanitizedSystem;
        this.sanitizedUser = sanitizedUser == null ? "" : sanitizedUser;
        this.declaredTier = declaredTier;
        this.effectiveTier = effectiveTier;
        this.blockedKind = blockedKind;
        this.maskCount = maskCount;
        this.reason = reason == null ? "" : reason;
        this.appExcluded = appExcluded;
    }
}
