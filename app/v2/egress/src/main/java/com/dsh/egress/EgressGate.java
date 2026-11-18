package com.dsh.egress;

import com.dsh.guard.Action;
import com.dsh.guard.Guard;
import com.dsh.guard.GuardResult;
import com.dsh.guard.Hit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 出网闸：任何上云请求必须先过这里。
 *
 * 流程（硬规则，不可绕过）：
 *  1. 对 system/user 两段分别跑 Guard
 *  2. 任一段 BLOCK → 该段替换为占位符，effectiveTier 降为 BLACK
 *  3. tier 与 provider 不匹配 → 拒绝（BLACK 只允许 LOCAL；GRAY 云端需显式允许）
 *  4. 无论通过/拒绝都落本地审计（不含原文，只含 hash/长度/动作计数）
 *
 * 失败永远返回 allowed=false，调用方不许"失败后发原文"。
 */
public final class EgressGate {

    public static final String BLOCKED_PLACEHOLDER = "[BLOCKED_BY_EGRESS]";
    public static final String APP_EXCLUDED_PLACEHOLDER = "[APP_EXCLUDED]";

    /** 源 App 文本是否必须替换；调用方构建 prompt 前也必须用这个（gate 再兜底）。 */
    public static String sanitizeAppText(String pkg, String text) {
        return AppExclusionPolicy.isExcluded(pkg)
                ? APP_EXCLUDED_PLACEHOLDER : (text == null ? "" : text);
    }

    private final Guard guard;
    private final AuditSink audit;
    private final boolean allowGrayCloud;

    public EgressGate(Guard guard, AuditSink audit) {
        this(guard, audit, false);
    }

    public EgressGate(Guard guard, AuditSink audit, boolean allowGrayCloud) {
        this.guard = guard == null ? new Guard() : guard;
        this.audit = audit;
        this.allowGrayCloud = allowGrayCloud;
    }

    public EgressResult check(String taskId, String system, String user,
                              EgressTier declaredTier, String providerId, boolean cloud) {
        return check(taskId, system, user, declaredTier, providerId, cloud, null);
    }

    /** @param sourcePackage 可选：内容来源 App；在排除名单时整段替换，防止旁路。 */
    public EgressResult check(String taskId, String system, String user,
                              EgressTier declaredTier, String providerId, boolean cloud,
                              String sourcePackage) {
        return check(taskId, system, user, declaredTier, providerId, cloud,
                sourcePackage, 0, "REDACT_THEN_CLOUD");
    }

    /** @param excludedBlocks 本 payload 含的排除块数；@param appPolicy 三级策略标记 */
    public EgressResult check(String taskId, String system, String user,
                              EgressTier declaredTier, String providerId, boolean cloud,
                              String sourcePackage, int excludedBlocks, String appPolicy) {
        return check(taskId, system, user, declaredTier, providerId, cloud, sourcePackage,
                excludedBlocks, appPolicy, 0, "");
    }

    public EgressResult check(String taskId, String system, String user,
                              EgressTier declaredTier, String providerId, boolean cloud,
                              String sourcePackage, int excludedBlocks, String appPolicy,
                              int maskerSpans, String maskerKinds) {
        EgressTier declared = declaredTier == null ? EgressTier.WHITE : declaredTier;
        String sys = system == null ? "" : system;
        String usr = user == null ? "" : user;

        boolean appExcluded = AppExclusionPolicy.isExcluded(sourcePackage);
        boolean redactOnly = AppExclusionPolicy.requiresMasking(sourcePackage);
        String policy = appExcluded ? "NEVER_CLOUD"
                : redactOnly ? "REDACT_THEN_CLOUD" : appPolicy;
        if (appExcluded) {
            sys = APP_EXCLUDED_PLACEHOLDER;
            usr = APP_EXCLUDED_PLACEHOLDER;
        }

        GuardResult gs = guard.check(sys);
        GuardResult gu = guard.check(usr);
        int maskCount = countMasks(gs) + countMasks(gu);

        boolean blocked = gs.isBlocked() || gu.isBlocked();
        String blockedKind = gs.isBlocked() ? gs.blockedKind
                : (gu.isBlocked() ? gu.blockedKind : null);
        EgressTier effective = blocked ? EgressTier.BLACK : declared;
        String sanitizedSystem = blocked && gs.isBlocked()
                ? BLOCKED_PLACEHOLDER : safeMasked(gs, sys);
        String sanitizedUser = blocked && gu.isBlocked()
                ? BLOCKED_PLACEHOLDER : safeMasked(gu, usr);

        String reason = "";
        boolean allowed;
        if (blocked) {
            if (cloud) {
                allowed = false;
                reason = "BLOCK 死线命中(" + blockedKind + ")：禁止上云";
            } else {
                allowed = true;                 // 本地可处理，但内容已替换为占位
                reason = "BLOCK 死线命中(" + blockedKind + ")：仅本地处理";
            }
        } else if (appExcluded) {
            // NEVER_CLOUD 是硬约束：云端直接拒绝，不是靠 prompt
            allowed = !cloud;
            reason = allowed ? "NEVER_CLOUD：仅本地" : "NEVER_CLOUD：禁止上云";
        } else if (redactOnly && cloud) {
            // Masker 尚未接入：REDACT_THEN_CLOUD 内容不得出网
            allowed = false;
            reason = "REDACT_THEN_CLOUD：Masker 未接入，禁止上云";
        } else if (effective == EgressTier.BLACK) {
            allowed = !cloud;
            reason = allowed ? "BLACK tier：仅本地" : "BLACK tier 请求云端：拒绝";
        } else if (effective == EgressTier.GRAY && cloud && !allowGrayCloud) {
            allowed = false;
            reason = "GRAY tier 请求云端：用户未显式允许";
        } else {
            allowed = true;
            reason = "allowed";
        }

        if (audit != null) {
            String payload = sanitizedSystem + "\u0001" + sanitizedUser;
            audit.record(new AuditEntry(System.currentTimeMillis(),
                    taskId, providerId, cloud, declared, effective, allowed,
                    blockedKind, maskCount, excludedBlocks, policy,
                    payload.length(), sha1(payload), reason, maskerSpans, maskerKinds));
        }
        return new EgressResult(allowed, sanitizedSystem, sanitizedUser,
                declared, effective, blockedKind, maskCount, reason, appExcluded);
    }

    /** provenance 版本：sources + stage 一起判定，禁止再靠 sourcePackage=null 放行。 */
    public EgressResult checkProvenance(String taskId, String system, String user,
                                        EgressTier declaredTier, String providerId,
                                        boolean cloud, java.util.List<String> sources,
                                        EgressStage stage, int excludedBlocks,
                                        String appPolicy, int maskerSpans,
                                        String maskerKinds) {
        return checkProvenance(taskId, system, user, declaredTier, providerId, cloud,
                sources, java.util.Collections.emptyList(), stage, excludedBlocks,
                appPolicy, maskerSpans, maskerKinds, "");
    }

    public EgressResult checkProvenance(String taskId, String system, String user,
                                        EgressTier declaredTier, String providerId,
                                        boolean cloud, java.util.List<String> sources,
                                        java.util.List<String> strippedSources,
                                        EgressStage stage, int excludedBlocks,
                                        String appPolicy, int maskerSpans,
                                        String maskerKinds) {
        return checkProvenance(taskId, system, user, declaredTier, providerId, cloud,
                sources, strippedSources, stage, excludedBlocks, appPolicy, maskerSpans,
                maskerKinds, "");
    }

    public EgressResult checkProvenance(String taskId, String system, String user,
                                        EgressTier declaredTier, String providerId,
                                        boolean cloud, java.util.List<String> sources,
                                        java.util.List<String> strippedSources,
                                        EgressStage stage, int excludedBlocks,
                                        String appPolicy, int maskerSpans,
                                        String maskerKinds, String maskStatus) {
        EgressTier declared = declaredTier == null ? EgressTier.WHITE : declaredTier;
        EgressStage st = stage == null ? EgressStage.RAW : stage;
        String sys = system == null ? "" : system;
        String usr = user == null ? "" : user;
        AppExclusionPolicy.Level level = ProvenancePolicy.strictest(sources);
        String policy = ProvenancePolicy.policyName(level);
        String sourceCsv = sources == null ? "" : String.join(",", sources);

        GuardResult gs = guard.check(sys);
        GuardResult gu = guard.check(usr);
        int maskCount = countMasks(gs) + countMasks(gu);
        boolean blocked = gs.isBlocked() || gu.isBlocked();
        String blockedKind = gs.isBlocked() ? gs.blockedKind
                : (gu.isBlocked() ? gu.blockedKind : null);
        EgressTier effective = blocked ? EgressTier.BLACK : declared;
        String sanitizedSystem = blocked && gs.isBlocked()
                ? BLOCKED_PLACEHOLDER : safeMasked(gs, sys);
        String sanitizedUser = blocked && gu.isBlocked()
                ? BLOCKED_PLACEHOLDER : safeMasked(gu, usr);

        boolean allowed;
        String reason;
        if (blocked) {
            allowed = !cloud;
            reason = "BLOCK 死线命中(" + blockedKind + ")"
                    + (cloud ? "：禁止上云" : "：仅本地处理");
        } else if (cloud && !ProvenancePolicy.allows(sources, st, true)) {
            allowed = false;
            reason = "provenance: " + ProvenancePolicy.reason(sources, st);
        } else if (effective == EgressTier.BLACK) {
            allowed = !cloud;
            reason = allowed ? "BLACK tier：仅本地" : "BLACK tier 请求云端：拒绝";
        } else if (effective == EgressTier.GRAY && cloud && !allowGrayCloud) {
            allowed = false;
            reason = "GRAY tier 请求云端：用户未显式允许";
        } else {
            allowed = true;
            reason = "allowed provenance " + policy + " stage=" + st.name();
        }

        if (audit != null) {
            String payload = sanitizedSystem + "\u0001" + sanitizedUser;
            audit.record(new AuditEntry(System.currentTimeMillis(), taskId, providerId,
                    cloud, declared, effective, allowed, blockedKind, maskCount,
                    excludedBlocks, policy, payload.length(), sha1(payload), reason,
                    maskerSpans, maskerKinds, st.name(), sourceCsv, policy,
                    sources == null ? 0 : sources.size(),
                    strippedSources == null ? "" : String.join(",", strippedSources),
                    maskStatus == null ? "" : maskStatus));
        }
        return new EgressResult(allowed, sanitizedSystem, sanitizedUser,
                declared, effective, blockedKind, maskCount, reason,
                level == AppExclusionPolicy.Level.NEVER_CLOUD);
    }

    private static String safeMasked(GuardResult r, String original) {
        if (r == null) return original;
        if (r.maskedText != null) return r.maskedText;
        return original;                        // PASS 时 maskedText 也是原文
    }

    private static int countMasks(GuardResult r) {
        if (r == null || r.hits == null) return 0;
        int n = 0;
        for (Hit h : r.hits) if (h.action == Action.MASK) n++;
        return n;
    }

    static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Throwable t) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
