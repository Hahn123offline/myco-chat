package com.dsh.guard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * :guard 的验收测试。重点：
 *  - 五类 BLOCK 的格式/校验位；上下文类（支付密码/验证码/CVC）**有上下文才命中**；
 *  - MASK 只改手机号/邮箱，其余文本一字不动；
 *  - 人名/地址/机构名必须原样通过（这是规格决定，不是漏做）；
 *  - 50KB < 20ms。
 */
public class GuardTest {

    private final Guard guard = new Guard();

    // ---------------- 银行卡 ----------------

    @Test public void 合法卡号命中_含分组空格() {
        GuardResult r = guard.check("我的卡是 4111 1111 1111 1111");
        assertEquals(Action.BLOCK, r.action);
        assertEquals(Guard.K_BANK_CARD, r.blockedKind);
        assertNull("BLOCK 时不能返回上云文本", r.maskedText);
    }

    @Test public void 合法卡号命中_无分隔() {
        assertEquals(Action.BLOCK, guard.check("6222021234567890128").action);
    }

    @Test public void Luhn失败不命中() {
        GuardResult r = guard.check("4111111111111112");
        assertFalse(r.isBlocked());
        assertEquals(Action.PASS, r.action);
    }

    @Test public void 全同数字不算卡号() {
        // 8888888888888888：Luhn 本身能通过，但按"退化数字"排除；0000… 同时命中首位 0
        assertTrue("测试数据本身必须 Luhn 合法", Guard.luhnValid("8888888888888888"));
        assertFalse(guard.check("8888888888888888").isBlocked());
        assertFalse(guard.check("4444444444444444").isBlocked());
        assertFalse(guard.check("0000000000000000").isBlocked());
    }

    @Test public void 首位0不算卡号() {
        String n = withLuhn("0" + "62220212345678");   // 16 位、Luhn 合法、首位 0
        assertTrue("测试数据本身必须 Luhn 合法: " + n, Guard.luhnValid(n));
        assertFalse(guard.check(n).isBlocked());
    }

    @Test public void 长度越界不算卡号() {
        String tooShort = withLuhn("41111111111");     // 12 位
        String tooLong = withLuhn("4111111111111111111"); // 20 位
        assertEquals(12, tooShort.length());
        assertEquals(20, tooLong.length());
        assertFalse(guard.check(tooShort).isBlocked());
        assertFalse(guard.check(tooLong).isBlocked());
    }

    @Test public void luhn算法本身() {
        assertTrue(Guard.luhnValid("4111111111111111"));
        assertTrue(Guard.luhnValid("378282246310005"));
        assertFalse(Guard.luhnValid("4111111111111112"));
        assertFalse(Guard.luhnValid("123"));
    }

    // ---------------- 身份证 ----------------

    @Test public void 合法身份证命中_含X结尾() {
        GuardResult r = guard.check("身份证 11010519491231002X");
        assertEquals(Action.BLOCK, r.action);
        assertEquals(Guard.K_ID_CARD, r.blockedKind);
    }

    @Test public void 合法身份证小写x也算() {
        assertEquals(Guard.K_ID_CARD, guard.check("11010519491231002x").blockedKind);
    }

    @Test public void 身份证校验位错不命中() {
        String bad = "110105194912310021";
        assertFalse(Guard.gb11643Valid(bad));
        assertFalse(guard.check(bad).isBlocked());
    }

    @Test public void gb11643算法本身() {
        assertTrue(Guard.gb11643Valid("11010519491231002X"));
        assertFalse(Guard.gb11643Valid("110105194912310021"));
        assertFalse(Guard.gb11643Valid("11010519491231002Y"));
    }

    // ---------------- 上下文类：有上下文才命中 ----------------

    @Test public void 支付密码必须有上下文() {
        GuardResult with = guard.check("支付密码：123456");
        assertEquals(Action.BLOCK, with.action);
        assertEquals(Guard.K_PAYMENT_PASSWORD, with.blockedKind);

        GuardResult pwdWord = guard.check("请输入密码 654321");
        assertEquals(Guard.K_PAYMENT_PASSWORD, pwdWord.blockedKind);

        // 无上下文：6 位数字绝不能当密码
        assertEquals(Action.PASS, guard.check("123456").action);
        assertEquals(Action.PASS, guard.check("订单号 123456，共 3 件").action);
    }

    @Test public void 支付密码上下文距离超30字不命中() {
        String far = "支付密码" + repeat("这是无关的说明文字。", 5) + "123456";   // >30 字
        assertEquals(Action.PASS, guard.check(far).action);
        String near = "支付密码" + repeat("短", 10) + "123456";
        assertEquals(Guard.K_PAYMENT_PASSWORD, guard.check(near).blockedKind);
    }

    @Test public void 验证码必须有上下文() {
        GuardResult r = guard.check("验证码：8888");
        assertEquals(Action.BLOCK, r.action);
        assertEquals(Guard.K_SMS_CODE, r.blockedKind);

        assertEquals(Guard.K_SMS_CODE, guard.check("校验码 123456").blockedKind);
        assertEquals(Guard.K_SMS_CODE, guard.check("动态码是 4321").blockedKind);
        // 无上下文：4~6 位数字只是普通数字
        assertEquals(Action.PASS, guard.check("8888").action);
        assertEquals(Action.PASS, guard.check("我们约在 123456 号").action);
    }

    @Test public void 验证码上下文距离超15字不命中() {
        String far = "验证码" + repeat("无关", 10) + "8888";
        assertEquals(Action.PASS, guard.check(far).action);
        String near = "验证码" + repeat("短", 5) + "8888";
        assertEquals(Guard.K_SMS_CODE, guard.check(near).blockedKind);
    }

    @Test public void CVC必须有上下文() {
        GuardResult r = guard.check("CVV 123");
        assertEquals(Action.BLOCK, r.action);
        assertEquals(Guard.K_CVC, r.blockedKind);

        assertEquals(Guard.K_CVC, guard.check("安全码：4567").blockedKind);
        assertEquals(Guard.K_CVC, guard.check("卡片背面 123").blockedKind);
        assertEquals(Action.PASS, guard.check("123").action);
    }

    @Test public void 银行卡号不会因为上下文规则误判成支付密码() {
        // 11 位手机号即使旁边有「密码」，也不能被 6 位数字规则切开
        GuardResult r = guard.check("密码 13812345678");
        assertEquals(Action.MASK, r.action);   // 只掩手机号
        assertEquals(Guard.K_PHONE, r.hits.get(0).kind);
    }

    // ---------------- MASK ----------------

    @Test public void 手机号掩码格式_其余文本不变() {
        GuardResult r = guard.check("联系我 13812345678 谢谢");
        assertEquals(Action.MASK, r.action);
        assertEquals("联系我 138****678 谢谢", r.maskedText);
        assertEquals(1, r.hits.size());
        assertEquals(Guard.K_PHONE, r.hits.get(0).kind);
    }

    @Test public void 邮箱掩码格式_保留首字符与域名() {
        GuardResult r = guard.check("邮箱 lisi@example.com");
        assertEquals(Action.MASK, r.action);
        assertEquals("邮箱 l***@example.com", r.maskedText);
        assertEquals(Guard.K_EMAIL, r.hits.get(0).kind);
    }

    @Test public void 同时有手机和邮箱_都掩码() {
        GuardResult r = guard.check("a 13812345678 b lisi@example.com c");
        assertEquals(Action.MASK, r.action);
        assertEquals("a 138****678 b l***@example.com c", r.maskedText);
        assertEquals(2, r.hits.size());
    }

    // ---------------- 优先级 / 边界 ----------------

    @Test public void BLOCK优先于MASK() {
        GuardResult r = guard.check("卡 4111111111111111 电话 13812345678");
        assertEquals(Action.BLOCK, r.action);
        assertNull(r.maskedText);
        assertEquals(Action.BLOCK, r.action);
    }

    @Test public void 空串与纯标点PASS() {
        GuardResult empty = guard.check("");
        assertEquals(Action.PASS, empty.action);
        assertEquals("", empty.maskedText);
        assertTrue(empty.hits.isEmpty());

        String punct = "！？。，、；：…——（）【】";
        GuardResult p = guard.check(punct);
        assertEquals(Action.PASS, p.action);
        assertEquals(punct, p.maskedText);

        GuardResult nul = guard.check(null);
        assertEquals(Action.PASS, nul.action);
        assertEquals("", nul.maskedText);
    }

    @Test public void 人名地址机构名地名一律不脱敏() {
        String text = "张三住在北京市朝阳区建国路88号，电话 13812345678，在清华大学上班";
        GuardResult r = guard.check(text);
        assertEquals(Action.MASK, r.action);
        assertEquals("张三住在北京市朝阳区建国路88号，电话 138****678，在清华大学上班", r.maskedText);
    }

    @Test public void 纯人名地址文本原样PASS() {
        String text = "张三 北京市海淀区中关村大街1号 清华大学";
        GuardResult r = guard.check(text);
        assertEquals(Action.PASS, r.action);
        assertEquals(text, r.maskedText);
    }

    @Test public void isBlocked只判死线() {
        assertTrue(guard.isBlocked("卡号 4111111111111111"));
        assertTrue(guard.isBlocked("身份证 11010519491231002X"));
        assertFalse(guard.isBlocked("电话 13812345678"));
        assertFalse(guard.isBlocked("普通文本"));
        assertFalse(guard.isBlocked(null));
        assertFalse(guard.isBlocked(""));
    }

    @Test public void 性能_50KB文本check小于20ms() {
        String unit = "这是一段普通的屏幕文本，没有敏感信息。";
        StringBuilder sb = new StringBuilder(50 * 1024 + unit.length());
        while (sb.length() < 50 * 1024) sb.append(unit);
        String big = sb.toString();
        assertTrue(big.length() >= 50 * 1024);

        // 性能断言不能用墙钟：并行跑测试时线程会被抢占，墙钟量到的是"排队时间"不是"计算时间"。
        // 上一版用预热+取 min，并行下仍偶发 28ms 假红。改用**本线程 CPU 时间**
        // （ThreadMXBean.getCurrentThreadCpuTime），它只计本线程真正在 CPU 上跑的时间，
        // 被 descheduled 不算——这才是这条断言想守的东西（防止正则回溯退化成 O(n²)）。
        GuardResult r = null;
        for (int i = 0; i < 40; i++) r = guard.check(big);   // 预热：类加载 + C1/C2
        assertEquals(Action.PASS, r.action);

        java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpuOk = mx.isCurrentThreadCpuTimeSupported();
        long bestNanos = Long.MAX_VALUE;
        for (int i = 0; i < 7; i++) {
            long t0 = cpuOk ? mx.getCurrentThreadCpuTime() : System.nanoTime();
            guard.check(big);
            long t1 = cpuOk ? mx.getCurrentThreadCpuTime() : System.nanoTime();
            bestNanos = Math.min(bestNanos, t1 - t0);
        }
        long ms = bestNanos / 1_000_000L;
        assertTrue("50KB check() 必须 <20ms（" + (cpuOk ? "本线程CPU" : "墙钟") + "），实际最快 " + ms + "ms", ms < 20);
    }

    // ---------------- 工具 ----------------

    private static String withLuhn(String prefix) {
        for (char d = '0'; d <= '9'; d++) {
            String candidate = prefix + d;
            if (Guard.luhnValid(candidate)) return candidate;
        }
        throw new IllegalStateException("没有 Luhn 校验位: " + prefix);
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }
}
