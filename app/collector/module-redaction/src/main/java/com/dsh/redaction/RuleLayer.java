package com.dsh.redaction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则层脱敏：纯函数、可单测。
 *
 * 设计依据（实测）：规则强在"形态 + 校验位"——没有神经网络会算校验位。
 * 不追求覆盖自由文本里的人名/地址，那些交给下游（角色化 + 模板化）。
 */
public final class RuleLayer {

    public enum Type { PHONE, ID_CARD, BANK_CARD, EMAIL, VERIFY_CODE, ADDRESS }

    public static final class Result {
        public final String text;
        public final Map<Type, Integer> counts = new EnumMap<>(Type.class);
        /**
         * 本层实际产出的掩码 token（如 "138*****678"、"[验证码已丢弃]"）。
         * 给模型层做 span 保护用：模型只能补充掩码，不能动这些区间。
         */
        public final List<String> maskedTokens = new ArrayList<>();

        Result(String text) { this.text = text; }

        public int count(Type t) { return counts.getOrDefault(t, 0); }
        public int total() {
            int n = 0;
            for (int v : counts.values()) n += v;
            return n;
        }
        public boolean hasSecret() { return total() > 0; }

        @Override public String toString() { return counts + " -> " + text; }
    }

    // 中文边界坑：\b 在汉字旁失效（汉字算 \w），必须用 (?<!\d)…(?!\d)
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID18 = Pattern.compile("(?<![0-9A-Za-z])\\d{17}[\\dXx](?![0-9A-Za-z])");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CODE_KW = Pattern.compile("(验证码|动态码|校验码|确认码|口令|code)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");
    private static final Pattern ADDRESS = Pattern.compile(
            "[\\u4e00-\\u9fff]{2,8}(?:省|市|区|县)[\\u4e00-\\u9fff]{0,12}(?:路|街|道|巷|小区)[\\u4e00-\\u9fa5\\d]{0,10}号?");

    private static final int[] ID_W = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final String ID_C = "10X98765432";

    /** 验证码是速朽的：不脱敏，直接丢弃 */
    public static boolean dropVerifyCode() { return true; }

    public static Result apply(String input) {
        if (input == null) return new Result("");
        String s = input;
        Result r = new Result("");

        // 1) 验证码：关键词窗口内的连续数字 → 整段丢弃
        if (dropVerifyCode()) {
            StringBuilder sb = new StringBuilder(s);
            Matcher num = DIGITS.matcher(s);
            List<int[]> hits = new ArrayList<>();
            while (num.find()) {
                int a = Math.max(0, num.start() - 15);
                int b = Math.min(s.length(), num.end() + 15);
                if (CODE_KW.matcher(s.substring(a, b)).find()) {
                    hits.add(new int[]{num.start(), num.end()});
                }
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                int[] h = hits.get(i);
                sb.replace(h[0], h[1], "[验证码已丢弃]");
                r.counts.merge(Type.VERIFY_CODE, 1, Integer::sum);
            }
            if (!hits.isEmpty()) {
                for (int i = 0; i < hits.size(); i++) r.maskedTokens.add("[验证码已丢弃]");
            }
            s = sb.toString();
        }

        // 2) 邮箱
        s = replace(s, EMAIL, Type.EMAIL, r);

        // 3) 身份证：必须过 mod11-2，否则不算
        s = replaceChecked(s, ID18, Type.ID_CARD, r, RuleLayer::idChecksumOk);

        // 4) 银行卡：Luhn + 排除退化形态 + IIN 粗筛
        s = replaceChecked(s, CARD, Type.BANK_CARD, r, RuleLayer::cardOk);

        // 5) 手机号
        s = replace(s, PHONE, Type.PHONE, r);

        // 6) 地址（只做粗脱敏，自由格式会漏）
        s = replace(s, ADDRESS, Type.ADDRESS, r);

        Result out = new Result(s);
        out.counts.putAll(r.counts);
        out.maskedTokens.addAll(r.maskedTokens);
        return out;
    }

    // ---------- 校验位 ----------

    public static boolean idChecksumOk(String s) {
        if (s == null || s.length() != 18) return false;
        String body = s.substring(0, 17);
        for (int i = 0; i < 17; i++) if (!Character.isDigit(body.charAt(i))) return false;
        int total = 0;
        for (int i = 0; i < 17; i++) total += (body.charAt(i) - '0') * ID_W[i];
        return ID_C.charAt(total % 11) == Character.toUpperCase(s.charAt(17));
    }

    public static boolean luhnOk(String s) {
        int[] d = new int[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) d[n++] = c - '0';
        }
        if (n < 13) return false;
        int total = 0;
        boolean alt = false;
        for (int i = n - 1; i >= 0; i--) {
            int v = d[i];
            if (alt) { v *= 2; if (v > 9) v -= 9; }
            total += v;
            alt = !alt;
        }
        return total % 10 == 0;
    }

    /** 银行卡额外约束：位数、退化形态、首位非 0、IIN 粗筛 */
    public static boolean cardOk(String s) {
        int n = s.length();
        if (n < 16 || n > 19) return false;
        java.util.HashSet<Character> distinct = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) distinct.add(s.charAt(i));
        if (distinct.size() <= 2) return false;      // 全 0 / 全同数字能平凡通过 Luhn
        if (s.charAt(0) == '0') return false;
        char c0 = s.charAt(0);
        int head2 = (c0 - '0') * 10 + (s.charAt(1) - '0');
        boolean iinOk = c0 == '4' || c0 == '5' || c0 == '3' || head2 == 62 || head2 == 35;
        if (!iinOk) return false;
        return luhnOk(s);
    }

    // ---------- 工具 ----------

    private interface Check { boolean ok(String s); }

    private static String replace(String s, Pattern p, Type type, Result r) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        int n = 0;
        while (m.find()) {
            String masked = mask(m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
            r.maskedTokens.add(masked);
            n++;
        }
        m.appendTail(sb);
        if (n > 0) r.counts.merge(type, n, Integer::sum);
        return sb.toString();
    }

    private static String replaceChecked(String s, Pattern p, Type type, Result r, Check check) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        int n = 0;
        while (m.find()) {
            String g = m.group();
            boolean ok = check.ok(g);
            if (ok) {
                String masked = mask(g);
                m.appendReplacement(sb, Matcher.quoteReplacement(masked));
                r.maskedTokens.add(masked);
                n++;
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(g));
            }
        }
        m.appendTail(sb);
        if (n > 0) r.counts.merge(type, n, Integer::sum);
        return sb.toString();
    }

    /** 保留前 3 后 3，中间打星 */
    public static String mask(String s) {
        int n = s.length();
        if (n <= 4) return repeat('*', n);
        int keep = 3;
        if (n <= 6) return repeat('*', n - 2) + s.substring(n - 2);
        return s.substring(0, keep) + repeat('*', n - keep * 2) + s.substring(n - keep);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
