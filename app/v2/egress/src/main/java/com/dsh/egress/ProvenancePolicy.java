package com.dsh.egress;

import java.util.List;

/** provenance 判定矩阵：sources 取最严级别，再与 stage 组合。 */
public final class ProvenancePolicy {

    public enum Verdict { ALLOW, DENY_RAW, DENY_MASKED, DENY_UNKNOWN }

    /** 行 = 最严级别（NORMAL/REDACT/NEVER/UNKNOWN），列 = stage（RAW/MASKED/DERIVED）。 */
    private static final boolean[][] ALLOW = {
            // RAW, MASKED, DERIVED
            {true, true, true},    // NORMAL
            {false, true, true},   // REDACT_THEN_CLOUD
            {false, false, true},  // NEVER_CLOUD
            {false, false, true}   // UNKNOWN（sources 为空）
    };

    private ProvenancePolicy() { }

    /** 返回最严级别；sources 为空返回 null（未知）。 */
    public static AppExclusionPolicy.Level strictest(List<String> sources) {
        if (sources == null || sources.isEmpty()) return null;
        AppExclusionPolicy.Level worst = AppExclusionPolicy.Level.NORMAL;
        for (String pkg : sources) {
            if (pkg == null) continue;
            AppExclusionPolicy.Level l = AppExclusionPolicy.level(pkg);
            if (severity(l) > severity(worst)) worst = l;
        }
        return worst;
    }

    public static boolean allows(List<String> sources, EgressStage stage, boolean cloud) {
        if (!cloud) return true;
        AppExclusionPolicy.Level level = strictest(sources);
        int row;
        if (level == null) row = 3;
        else if (level == AppExclusionPolicy.Level.NEVER_CLOUD) row = 2;
        else if (level == AppExclusionPolicy.Level.REDACT_THEN_CLOUD) row = 1;
        else row = 0;
        int col = stage == null ? 0 : stage.ordinal();
        return ALLOW[row][Math.max(0, Math.min(2, col))];
    }

    public static String policyName(AppExclusionPolicy.Level level) {
        return level == null ? "UNKNOWN" : level.name();
    }

    public static String reason(List<String> sources, EgressStage stage) {
        AppExclusionPolicy.Level level = strictest(sources);
        String p = policyName(level);
        if (level == null) {
            return "sources 为空（未知来源）" + (stage == EgressStage.RAW
                    || stage == EgressStage.MASKED ? "，禁止上云" : "");
        }
        if (level == AppExclusionPolicy.Level.NEVER_CLOUD) {
            return "NEVER_CLOUD：" + (stage == EgressStage.MASKED
                    ? "MASKED 仍禁止上云" : "禁止上云");
        }
        if (level == AppExclusionPolicy.Level.REDACT_THEN_CLOUD
                && stage == EgressStage.RAW) {
            return "REDACT_THEN_CLOUD：RAW 需先脱敏";
        }
        return "provenance " + p + " stage=" + (stage == null ? "RAW" : stage.name());
    }

    private static int severity(AppExclusionPolicy.Level l) {
        if (l == AppExclusionPolicy.Level.NEVER_CLOUD) return 3;
        if (l == AppExclusionPolicy.Level.REDACT_THEN_CLOUD) return 2;
        return 1;
    }
}
