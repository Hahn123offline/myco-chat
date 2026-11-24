package com.dsh.egress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 出网闸：BLOCK 永不出网、MASK 后上云、tier/provider 不匹配拒绝、每次审计。 */
public class EgressGateTest {

    private static final String PHONE_TEXT = "我的手机 13812345678，请联系我";
    private static final String BLOCK_CARD = "卡号 4111 1111 1111 1111 请勿泄露";
    private static final String BLOCK_PAY = "支付密码 123456";
    private static final String BLOCK_SMS = "验证码 1234";
    private static final String BLOCK_ID = "身份证 11010519491231002X";

    @Test public void 白名单文本上云通过且审计() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        EgressResult r = gate.check("t1", "sys", "正常内容", EgressTier.WHITE, "cloud", true);
        assertTrue(r.reason, r.allowed);
        assertEquals(EgressTier.WHITE, r.effectiveTier);
        assertEquals(0, r.maskCount);
        assertEquals(1, audit.size());
        assertEquals("cloud", audit.last().providerId);
        assertEquals(0, audit.last().maskCount);
        assertFalse(audit.last().toJsonLine().contains("正常内容"));  // 审计不含原文
    }

    @Test public void 手机号被掩码后上云() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        EgressResult r = gate.check("t2", "", PHONE_TEXT, EgressTier.WHITE, "cloud", true);
        assertTrue(r.reason, r.allowed);
        assertTrue(r.sanitizedUser.contains("138****678"));
        assertFalse(r.sanitizedUser.contains("13812345678"));
        assertEquals(1, r.maskCount);
        assertEquals(1, audit.last().maskCount);
    }

    @Test public void BLOCK卡号_云端拒绝_内容替换为占位() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        EgressResult r = gate.check("t3", "", BLOCK_CARD, EgressTier.WHITE, "cloud", true);
        assertFalse(r.reason, r.allowed);
        assertEquals(EgressTier.BLACK, r.effectiveTier);
        assertEquals("bank_card", r.blockedKind);
        assertEquals(EgressGate.BLOCKED_PLACEHOLDER, r.sanitizedUser);
        assertFalse(r.sanitizedUser.contains("4111"));
        assertEquals("bank_card", audit.last().blockedKind);
        assertFalse(audit.last().allowed);
    }

    @Test public void BLOCK身份证_支付密码_验证码都拒绝上云() {
        EgressGate gate = new EgressGate(null, null);
        for (String text : new String[]{BLOCK_ID, BLOCK_PAY, BLOCK_SMS}) {
            EgressResult r = gate.check("t", "", text, EgressTier.WHITE, "cloud", true);
            assertFalse(text, r.allowed);
            assertEquals(EgressTier.BLACK, r.effectiveTier);
            assertEquals(EgressGate.BLOCKED_PLACEHOLDER, r.sanitizedUser);
        }
    }

    @Test public void 死线命中时本地provider允许但内容已占位() {
        EgressGate gate = new EgressGate(null, new MemoryAudit());
        EgressResult r = gate.check("t", "", BLOCK_PAY, EgressTier.WHITE, "local", false);
        assertTrue(r.reason, r.allowed);
        assertEquals(EgressTier.BLACK, r.effectiveTier);
        assertEquals(EgressGate.BLOCKED_PLACEHOLDER, r.sanitizedUser);
    }

    @Test public void BLACK请求云端直接拒绝_不发任何东西() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        EgressResult r = gate.check("t", "", "普通文本", EgressTier.BLACK, "cloud", true);
        assertFalse(r.reason, r.allowed);
        assertEquals(EgressTier.BLACK, r.effectiveTier);
        assertFalse(audit.last().allowed);
    }

    @Test public void GRAY云端默认拒绝_显式允许才通过() {
        EgressGate strict = new EgressGate(null, new MemoryAudit());
        assertFalse(strict.check("t", "", "灰色", EgressTier.GRAY, "cloud", true).allowed);

        EgressGate loose = new EgressGate(null, new MemoryAudit(), true);
        assertTrue(loose.check("t", "", "灰色", EgressTier.GRAY, "cloud", true).allowed);
    }

    @Test public void 每次出网都有审计_通过和拒绝都记() {
        MemoryAudit audit = new MemoryAudit();
        EgressGate gate = new EgressGate(null, audit);
        gate.check("t1", "", "正常", EgressTier.WHITE, "cloud", true);
        gate.check("t2", "", BLOCK_CARD, EgressTier.WHITE, "cloud", true);
        gate.check("t3", "", "普通", EgressTier.BLACK, "cloud", true);
        assertEquals(3, audit.size());
        assertTrue(audit.entries.get(0).allowed);
        assertFalse(audit.entries.get(1).allowed);
        assertFalse(audit.entries.get(2).allowed);
    }
}
