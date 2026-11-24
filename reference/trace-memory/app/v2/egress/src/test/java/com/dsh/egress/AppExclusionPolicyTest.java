package com.dsh.egress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class AppExclusionPolicyTest {

    @After public void reset() { AppExclusionPolicy.restoreDefaults(); }

    @Test public void 默认三级名单() {
        // 支付/银行/相册/密码 → NEVER_CLOUD
        assertTrue(AppExclusionPolicy.isExcluded("com.eg.android.AlipayGphone"));
        assertTrue(AppExclusionPolicy.isExcluded("com.miui.gallery"));
        assertTrue(AppExclusionPolicy.isExcluded("com.icbc.mobile"));
        assertTrue(AppExclusionPolicy.isExcluded("com.bitwarden.android"));
        // 社交 → REDACT_THEN_CLOUD（不是 NEVER）
        assertFalse(AppExclusionPolicy.isExcluded("com.tencent.mm"));
        assertFalse(AppExclusionPolicy.isExcluded("com.tencent.mobileqq"));
        assertTrue(AppExclusionPolicy.requiresMasking("com.tencent.mm"));
        assertTrue(AppExclusionPolicy.requiresMasking("com.tencent.mobileqq"));
        // 普通
        assertEquals(AppExclusionPolicy.Level.NORMAL,
                AppExclusionPolicy.level("com.android.browser"));
    }

    @Test public void 用户可改级别() {
        AppExclusionPolicy.setLevel("com.example.secret",
                AppExclusionPolicy.Level.NEVER_CLOUD);
        assertTrue(AppExclusionPolicy.isExcluded("com.example.secret"));
        AppExclusionPolicy.setLevel("com.example.secret",
                AppExclusionPolicy.Level.REDACT_THEN_CLOUD);
        assertTrue(AppExclusionPolicy.requiresMasking("com.example.secret"));
        AppExclusionPolicy.setLevel("com.example.secret",
                AppExclusionPolicy.Level.NORMAL);
        assertFalse(AppExclusionPolicy.isExcluded("com.example.secret"));
    }

    @Test public void NEVER_CLOUD在gate里硬拒绝() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        EgressResult r = gate.check("t", "sys", "支付内容 6222", EgressTier.WHITE,
                "cloud", true, "com.eg.android.AlipayGphone");
        assertFalse(r.reason, r.allowed);
        assertTrue(r.appExcluded);
        assertEquals(EgressGate.APP_EXCLUDED_PLACEHOLDER, r.sanitizedUser);
        assertFalse(audit.last().toJsonLine().contains("支付内容"));
        assertTrue(audit.last().appPolicy.contains("NEVER_CLOUD"));
    }

    @Test public void REDACT在Masker前拒绝上云() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        EgressResult r = gate.check("t", "sys", "微信聊天内容 苏荟文", EgressTier.WHITE,
                "cloud", true, "com.tencent.mm");
        assertFalse(r.reason, r.allowed);
        assertTrue(r.reason.contains("REDACT_THEN_CLOUD"));
        assertFalse(r.appExcluded);
        assertTrue(audit.last().appPolicy.contains("REDACT_THEN_CLOUD"));
    }

    @Test public void 普通App不被误杀() {
        EgressGate gate = new EgressGate(null, null);
        EgressResult r = gate.check("t", "sys", "浏览器搜索内容", EgressTier.WHITE,
                "cloud", true, "com.android.browser");
        assertFalse(r.appExcluded);
        assertTrue(r.allowed);
        assertTrue(r.sanitizedUser.contains("浏览器"));
    }
}
