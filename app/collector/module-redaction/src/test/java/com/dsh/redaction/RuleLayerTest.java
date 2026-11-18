package com.dsh.redaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleLayerTest {

    // ---- 造合法数据 ----
    private static int[] W = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static String C = "10X98765432";

    private static String makeId(String prefix14) {
        String base = prefix14 + "001";
        int total = 0;
        for (int i = 0; i < 17; i++) total += (base.charAt(i) - '0') * W[i];
        return base + C.charAt(total % 11);
    }

    private static String withLuhn(String prefix) {
        for (char d = '0'; d <= '9'; d++) {
            if (RuleLayer.luhnOk(prefix + d)) return prefix + d;
        }
        return prefix + "0";
    }

    @Test public void 手机号被脱敏() {
        RuleLayer.Result r = RuleLayer.apply("联系我 13812345678 就行");
        assertEquals(1, r.count(RuleLayer.Type.PHONE));
        assertFalse(r.text.contains("13812345678"));
        assertTrue(r.text.contains("*"));
    }

    @Test public void 十三位数字不是手机号() {
        RuleLayer.Result r = RuleLayer.apply("订单号 1381234567890");
        assertEquals(0, r.count(RuleLayer.Type.PHONE));
    }

    @Test public void 身份证要过校验位才脱敏() {
        String ok = makeId("11010119900307");
        RuleLayer.Result r1 = RuleLayer.apply("身份证 " + ok);
        assertEquals(1, r1.count(RuleLayer.Type.ID_CARD));
        assertFalse(r1.text.contains(ok));

        String bad = ok.substring(0, 17) + (ok.charAt(17) == '0' ? '1' : '0');
        RuleLayer.Result r2 = RuleLayer.apply("号码 " + bad);
        assertEquals(0, r2.count(RuleLayer.Type.ID_CARD));
    }

    @Test public void 银行卡要过Luhn且排除退化形态() {
        String ok = withLuhn("622202123456789012");
        RuleLayer.Result r1 = RuleLayer.apply("卡号 " + ok);
        assertEquals(1, r1.count(RuleLayer.Type.BANK_CARD));

        RuleLayer.Result r2 = RuleLayer.apply("单号 0000000000000000");
        assertEquals(0, r2.count(RuleLayer.Type.BANK_CARD));

        RuleLayer.Result r3 = RuleLayer.apply("编号 1111111111111111");
        assertEquals(0, r3.count(RuleLayer.Type.BANK_CARD));
    }

    @Test public void 验证码直接丢弃而不是打码() {
        RuleLayer.Result r = RuleLayer.apply("您的验证码是 834271，5分钟内有效");
        assertEquals(1, r.count(RuleLayer.Type.VERIFY_CODE));
        assertFalse(r.text.contains("834271"));
        assertTrue(r.text.contains("[验证码已丢弃]"));
    }

    @Test public void 无关键词的数字不当验证码() {
        RuleLayer.Result r = RuleLayer.apply("订单金额 8342 元");
        assertEquals(0, r.count(RuleLayer.Type.VERIFY_CODE));
    }

    @Test public void 邮箱被脱敏() {
        RuleLayer.Result r = RuleLayer.apply("发到 zhangsan@example.com");
        assertEquals(1, r.count(RuleLayer.Type.EMAIL));
        assertFalse(r.text.contains("zhangsan@example.com"));
    }

    @Test public void 负例零误杀() {
        String[] negatives = {
                "计算 319×0.5 = 159.5",
                "日期 2025-11-06 10:22",
                "温度 36.8 度，电量 67%",
                "时间 01:20 至 02:17",
                "尺寸 1920x1080 共 552 人",
                "这是 2026 年 11 月 16 日",
        };
        for (String s : negatives) {
            RuleLayer.Result r = RuleLayer.apply(s);
            assertEquals("误杀: " + s, 0, r.total());
            assertEquals(s, r.text);
        }
    }

    @Test public void 校验位工具本身正确() {
        assertTrue(RuleLayer.idChecksumOk(makeId("44030120000101")));
        assertFalse(RuleLayer.idChecksumOk("440301200001010000"));
        assertTrue(RuleLayer.luhnOk(withLuhn("4111111111111111".substring(0, 15))));
    }

    @Test public void 打码保留首三尾三() {
        assertEquals("138*****678", RuleLayer.mask("13812345678"));
    }

    @Test public void 输出已知掩码token供模型层做span保护() {
        RuleLayer.Result r = RuleLayer.apply("联系我 13812345678，邮箱 zhangsan@example.com");
        assertEquals(2, r.maskedTokens.size());
        assertTrue(r.maskedTokens.get(0).contains("*"));
        assertTrue(r.maskedTokens.get(1).contains("*"));
        for (String token : r.maskedTokens) {
            assertTrue("token 应该真的在脱敏文本里: " + token, r.text.contains(token));
        }
    }

    @Test public void 验证码丢弃也留下受保护token() {
        RuleLayer.Result r = RuleLayer.apply("您的验证码是 123456，请勿泄露");
        assertEquals(1, r.count(RuleLayer.Type.VERIFY_CODE));
        assertTrue(r.text.contains(MaskTokens.VERIFY_DROPPED));
        assertTrue(r.maskedTokens.contains(MaskTokens.VERIFY_DROPPED));
    }

    @Test public void 没有命中时没有掩码token() {
        RuleLayer.Result r = RuleLayer.apply("今天天气不错，温度 26 度");
        assertTrue(r.maskedTokens.isEmpty());
    }
}
