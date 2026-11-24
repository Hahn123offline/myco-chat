package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.memory.EgressTier;

import org.junit.Test;

/**
 * 门控规则：白/黑/GRAY、白名单包名遇敏感词降级、配置热加载、保守默认。
 */
public class RuleEgressPolicyTest {

    private final TestSupport.FakeConfig cfg = new TestSupport.FakeConfig();
    private final RuleEgressPolicy policy = new RuleEgressPolicy(() -> cfg);

    private EgressTier tier(String pkg, String title, String text) {
        return policy.tierFor(pkg, "", title, text);
    }

    // ---------------- 默认档位 ----------------

    @Test public void 黑名单应用直接BLACK() {
        assertEquals(EgressTier.BLACK, tier("com.tencent.mm", "", ""));
        assertEquals(EgressTier.BLACK, tier("com.eg.android.AlipayGphone", "账单", "余额 100"));
        assertEquals(EgressTier.BLACK, tier("com.android.mms", "验证码", "123456"));
    }

    @Test public void 白名单公共内容WHITE() {
        assertEquals(EgressTier.WHITE, tier("tv.danmaku.bili", "影之刃零 攻略", "UP主分享"));
        assertEquals(EgressTier.WHITE, tier("com.xingin.xhs", "今日穿搭", "好物分享"));
        assertEquals(EgressTier.WHITE, tier("com.android.chrome", "新闻", "公开资讯"));
    }

    @Test public void 未知应用默认GRAY_不是WHITE() {
        assertEquals(EgressTier.GRAY, tier("com.some.unknown.app", "", "公开内容"));
        assertEquals(EgressTier.GRAY, tier(null, "", ""));
        assertEquals(EgressTier.GRAY, tier("   ", "", ""));
    }

    // ---------------- 关键事故防护：白名单 + 敏感词 → GRAY ----------------

    @Test public void 白名单标题命中敏感词降级GRAY() {
        assertEquals(EgressTier.GRAY, tier("tv.danmaku.bili", "我的订单", "视频内容"));
        assertEquals(EgressTier.GRAY, tier("com.taobao.taobao", "收货地址", "商品页"));
    }

    @Test public void 白名单正文命中敏感词也降级GRAY() {
        assertEquals(EgressTier.GRAY, tier("com.xingin.xhs", "好物分享", "私信里发了验证码"));
        assertEquals(EgressTier.GRAY, tier("com.android.chrome", "页面", "银行卡余额提醒"));
    }

    @Test public void 黑名单优先于敏感词_即使标题很公共也是BLACK() {
        assertEquals(EgressTier.BLACK, tier("com.tencent.mm", "影之刃零 攻略", "视频链接"));
    }

    // ---------------- 配置与热加载 ----------------

    @Test public void 白名单来自配置_可覆盖默认() {
        cfg.set("egress.white.pkgs", "com.foo.app");
        assertEquals(EgressTier.WHITE, tier("com.foo.app", "公开内容", ""));
        assertEquals(EgressTier.GRAY, tier("tv.danmaku.bili", "影之刃零", ""));   // 默认白名单被替换
    }

    @Test public void 黑名单来自配置_可显式清空() {
        cfg.set("egress.black.pkgs", "");
        assertEquals(EgressTier.GRAY, tier("com.tencent.mm", "", ""));    // 不再 BLACK，但也不是 WHITE
    }

    @Test public void 敏感词来自配置_可覆盖默认() {
        cfg.set("egress.gray.titleHints", "购物");
        cfg.set("egress.gray.textHints", "购物");
        // 默认的"订单"不再命中 → 白名单回到 WHITE
        assertEquals(EgressTier.WHITE, tier("tv.danmaku.bili", "我的订单", ""));
        assertEquals(EgressTier.GRAY, tier("tv.danmaku.bili", "购物车", ""));
    }

    @Test public void 配置热加载_同一个policy立即生效() {
        assertEquals(EgressTier.BLACK, tier("com.tencent.mm", "", ""));
        cfg.set("egress.black.pkgs", "com.other.app");
        assertEquals(EgressTier.GRAY, tier("com.tencent.mm", "", ""));
        cfg.set("egress.white.pkgs", "com.tencent.mm");
        assertEquals(EgressTier.WHITE, tier("com.tencent.mm", "", ""));
    }

    @Test public void 包名大小写与空白被规范化() {
        assertEquals(EgressTier.WHITE, tier(" TV.Danmaku.Bili ", "影之刃零", ""));
        assertEquals(EgressTier.BLACK, tier(" COM.TENCENT.MM ", "", ""));
    }

    @Test public void 英文敏感词不区分大小写() {
        cfg.set("egress.gray.titleHints", "Order");
        assertEquals(EgressTier.GRAY, tier("tv.danmaku.bili", "My ORDER page", ""));
    }

    @Test public void 默认敏感词覆盖真机漏判_付款码与医疗住址() {
        // 真机采到 com.miui.personalassistant「付款码」曾被判 GRAY（当时 hints 只有支付/订单）
        assertEquals(EgressTier.GRAY, tier("tv.danmaku.bili", "付款码", "请扫码出示"));
        assertEquals(EgressTier.GRAY, tier("com.xingin.xhs", "体检报告", ""));
        assertEquals(EgressTier.GRAY, tier("com.taobao.taobao", "收货住址", ""));
        assertEquals(EgressTier.GRAY, tier("com.android.chrome", "理财基金行情", ""));
        // 未命中敏感词的白名单仍是 WHITE
        assertEquals(EgressTier.WHITE, tier("tv.danmaku.bili", "影之刃零 攻略", ""));
    }

    @Test public void 配置里的hints可以完全覆盖默认值() {
        cfg.set("egress.gray.titleHints", "只有这个词");
        cfg.set("egress.gray.textHints", "只有这个词");
        assertEquals(EgressTier.WHITE, tier("tv.danmaku.bili", "付款码", ""));
        assertEquals(EgressTier.GRAY, tier("tv.danmaku.bili", "只有这个词", ""));
    }

    @Test public void tier枚举语义_独立性辅助方法() {
        assertTrue(EgressTier.WHITE.isPublicContent());
        assertTrue(EgressTier.GRAY.requiresRedaction());
        assertTrue(EgressTier.BLACK.neverLeavesDevice());
        assertTrue(EgressTier.UNKNOWN.neverLeavesDevice());    // 未知也不允许出
        assertFalse(EgressTier.WHITE.neverLeavesDevice());
        assertEquals(EgressTier.GRAY, EgressTier.fromCode(2));
        assertEquals(EgressTier.UNKNOWN, EgressTier.fromCode(99));
    }
}
