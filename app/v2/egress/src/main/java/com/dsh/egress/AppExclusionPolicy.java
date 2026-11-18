package com.dsh.egress;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 三级应用出网策略（task-34）：
 *  - NEVER_CLOUD：整屏敏感（支付/银行/相册/密码），代码层面拒绝上云
 *  - REDACT_THEN_CLOUD：社交内容，掩码后才能上云（Masker 接入前不出网）
 *  - NORMAL：直接原文
 *
 * 三级判定只在 :egress；用户 per-app 覆盖保存在本地。
 */
public final class AppExclusionPolicy {

    public enum Level { NEVER_CLOUD, REDACT_THEN_CLOUD, NORMAL }

    public static final Set<String> NEVER_EXACT = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "com.eg.android.AlipayGphone", "com.unionpay",
                    "com.miui.gallery", "com.google.android.apps.photos",
                    "com.coloros.gallery3d", "com.android.gallery3d",
                    "com.oneplus.gallery", "com.sec.android.gallery3d",
                    "com.agilebits.onepassword", "com.bitwarden.android",
                    "com.lastpass.lpandroid", "com.keepassdroid",
                    "com.x8bit.bitwarden", "com.tencent.qqpimsecure"
            )));

    /** 社交：内容大部分无害，掩码后可以上云（Masker 前不出网）。 */
    public static final Set<String> REDACT_EXACT = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "com.tencent.mm",            // 微信
                    "com.tencent.mobileqq",      // QQ
                    "com.tencent.wework",        // 企业微信
                    "com.alibaba.android.rimet", // 钉钉
                    "com.tencent.qqmail", "com.netease.mail",
                    "com.microsoft.office.outlook"
            )));

    public static final String[] NEVER_KEYWORDS = {
            "bank", "icbc", "ccb", "abc", "bocom", "cmb", "spdb", "citic",
            "cib", "psbc", "abchina", "agricultural", "pay", "wallet",
            "gallery", "photos", "password", "keepass", "bitwarden",
            "1password", "lastpass", "authenticator"
    };

    private static final Map<String, Level> overrides = new LinkedHashMap<>();

    private AppExclusionPolicy() { }

    public static synchronized Level level(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return Level.NORMAL;
        String key = pkg.trim();
        String lower = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Level> e : overrides.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        if (NEVER_EXACT.contains(key)) return Level.NEVER_CLOUD;
        if (REDACT_EXACT.contains(key)) return Level.REDACT_THEN_CLOUD;
        for (String kw : NEVER_KEYWORDS) {
            if (lower.contains(kw)) return Level.NEVER_CLOUD;
        }
        return Level.NORMAL;
    }

    /** 兼容旧调用：只有 NEVER_CLOUD 算“排除”。 */
    public static boolean isExcluded(String pkg) {
        return level(pkg) == Level.NEVER_CLOUD;
    }

    public static boolean requiresMasking(String pkg) {
        return level(pkg) == Level.REDACT_THEN_CLOUD;
    }

    /** 时间线占位可说的类别（不来自内容推断）。 */
    public static String category(String pkg) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        if (p.contains("alipay") || p.contains("pay") || p.contains("wallet")
                || p.contains("unionpay")) return "支付类";
        if (p.matches(".*(bank|icbc|ccb|abc|bocom|cmb|spdb|citic|cib|psbc).*")) return "银行类";
        if (p.contains("gallery") || p.contains("photos")) return "相册类";
        if (p.contains("password") || p.contains("keepass") || p.contains("bitwarden")
                || p.contains("1password") || p.contains("lastpass")
                || p.contains("authenticator")) return "密码管理类";
        if (p.contains("tencent.mm") || p.contains("mobileqq") || p.contains("wework")
                || p.contains("rimet") || p.contains("mail")) return "社交类";
        return "其他";
    }

    public static synchronized void setLevel(String pkg, Level level) {
        if (pkg == null || pkg.trim().isEmpty()) return;
        if (level == null) overrides.remove(pkg.trim());
        else overrides.put(pkg.trim(), level);
    }

    public static synchronized void clearOverrides() { overrides.clear(); }

    public static synchronized Map<String, Level> overrides() {
        return new LinkedHashMap<>(overrides);
    }

    /** 设置页展示：默认 + 覆盖的合并快照。 */
    public static synchronized Map<String, Level> effectiveLevels() {
        Map<String, Level> out = new LinkedHashMap<>();
        for (String p : NEVER_EXACT) out.put(p, Level.NEVER_CLOUD);
        for (String p : REDACT_EXACT) out.put(p, Level.REDACT_THEN_CLOUD);
        out.putAll(overrides);
        return out;
    }

    // 兼容旧 API
    public static void addUserPackage(String pkg) { setLevel(pkg, Level.NEVER_CLOUD); }

    public static void removePackage(String pkg) { setLevel(pkg, Level.NORMAL); }

    public static void restoreDefaults() { clearOverrides(); }

    public static Set<String> userExtra() { return overrides.keySet(); }

    public static Set<String> removedDefaults() { return Collections.emptySet(); }
}
