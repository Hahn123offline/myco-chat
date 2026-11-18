package com.dsh.guard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 硬死线：只做**格式校验**，不做内容理解。
 *
 * ============================ 明确不做（不要加回来） ============================
 * 本类**故意不脱敏**以下内容，这不是"暂时不做"，是规格 v1.0 的决定：
 *   - 人名 / 地址 / 机构名 / 地名（模型判断不可靠，且标签不需要知道"谁""在哪"）
 *   - 语义级敏感判断（"这条重不重要"不是规则该回答的问题）
 *   - 误报时靠猜的启发式（宁可漏判，也不许用规则猜语义）
 * 如果将来要加，先改《痕迹记录-规格-v1.0.md》，不要直接改这里。
 * =============================================================================
 *
 * BLOCK（整条不上云，本地仍存原文）：
 *   - 银行卡号：13–19 位数字（允许空格/连字符分组）+ Luhn + 非全同 + 首位非 0
 *   - 身份证号：18 位 + GB11643 校验位（末位可为 X）
 *   - 支付密码：6 位数字，且前后 30 字内出现「支付密码/付款密码/密码」
 *   - 短信验证码：4–6 位数字，且前后 15 字内出现「验证码/校验码/动态码/口令」
 *   - 卡背面 CVC：3–4 位数字，且前后 15 字内出现「CVC/CVV/安全码/背面」
 *
 * MASK（只改这一处，其余原样上云）：
 *   - 手机号：1[3-9] 开头 11 位 → 138****678（前 3 + **** + 后 3）
 *   - 邮箱：保留首字符 + 域名 → l***@example.com
 *
 * 纯函数、零依赖、不碰 Android；50KB 文本 check() < 20ms。
 */
public final class Guard {

    public static final String K_BANK_CARD = "bank_card";
    public static final String K_ID_CARD = "id_card";
    public static final String K_PAYMENT_PASSWORD = "payment_password";
    public static final String K_SMS_CODE = "sms_code";
    public static final String K_CVC = "cvc";
    public static final String K_PHONE = "phone";
    public static final String K_EMAIL = "email";

    // ---------------- 正则 ----------------
    // 卡号：13–19 位数字，允许单个空格/连字符分组；边界保证不会从长数字中间截一段
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(\\d(?:[ -]?\\d){12,18})(?!\\d)");
    // 身份证：17 位数字 + 数字/X
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9A-Za-z])(\\d{17}[0-9Xx])(?![0-9A-Za-z])");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    private static final Pattern PAY_CTX = Pattern.compile("支付密码|付款密码|密码");
    private static final Pattern SMS_CTX = Pattern.compile("验证码|校验码|动态码|口令");
    private static final Pattern CVC_CTX = Pattern.compile("CVC|CVV|安全码|背面",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CARD_CTX = Pattern.compile("卡号|银行卡|信用卡|储蓄卡|尾号|账号");

    private static final Pattern DIGITS_EXACT_6 = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final Pattern DIGITS_4_TO_6 = Pattern.compile("(?<!\\d)(\\d{4,6})(?!\\d)");
    private static final Pattern DIGITS_3_TO_4 = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)");

    private static final int PAY_WINDOW = 30;
    private static final int SMS_WINDOW = 15;
    private static final int CVC_WINDOW = 15;
    private static final int CARD_CTX_WINDOW = 30;

    private static final int[] GB_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] GB_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /** BLOCK 的优先级（同一段命中多类时 blockedKind 取列表里最靠前的） */
    private static final String[] BLOCK_ORDER = {
            K_BANK_CARD, K_ID_CARD, K_PAYMENT_PASSWORD, K_SMS_CODE, K_CVC
    };

    /** 判定 + 生成上云文本。text 为 null 视为空串。 */
    public GuardResult check(String text) {
        String t = text == null ? "" : text;
        if (t.isEmpty()) return new GuardResult(Action.PASS, null, t, new ArrayList<>());

        Map<String, List<Hit>> blocks = blockHitsByKind(t);
        List<Hit> maskHits = maskHits(t);

        if (!blocks.isEmpty()) {
            List<Hit> all = new ArrayList<>();
            for (String kind : BLOCK_ORDER) {
                List<Hit> hs = blocks.get(kind);
                if (hs != null) all.addAll(hs);
            }
            all.addAll(maskHits);
            all.sort(Comparator.comparingInt(h -> h.start));
            String blockedKind = BLOCK_ORDER[0];
            for (String kind : BLOCK_ORDER) {
                if (blocks.containsKey(kind)) { blockedKind = kind; break; }
            }
            return new GuardResult(Action.BLOCK, blockedKind, null, all);
        }
        if (!maskHits.isEmpty()) {
            return new GuardResult(Action.MASK, null, applyMasks(t, maskHits), maskHits);
        }
        return new GuardResult(Action.PASS, null, t, new ArrayList<>());
    }

    /** 便宜版：只判 BLOCK（不做掩码，不构造结果对象列表之外的东西） */
    public boolean isBlocked(String text) {
        if (text == null || text.isEmpty()) return false;
        return !blockHitsByKind(text).isEmpty();
    }

    // ---------------- BLOCK ----------------

    private Map<String, List<Hit>> blockHitsByKind(String text) {
        Map<String, List<Hit>> out = new LinkedHashMap<>();
        List<Hit> cards = scanCards(text);
        if (!cards.isEmpty()) out.put(K_BANK_CARD, cards);
        List<Hit> ids = scanIds(text);
        if (!ids.isEmpty()) out.put(K_ID_CARD, ids);
        List<Hit> pay = scanContext(text, PAY_CTX, DIGITS_EXACT_6, PAY_WINDOW, K_PAYMENT_PASSWORD);
        if (!pay.isEmpty()) out.put(K_PAYMENT_PASSWORD, pay);
        List<Hit> sms = scanContext(text, SMS_CTX, DIGITS_4_TO_6, SMS_WINDOW, K_SMS_CODE);
        if (!sms.isEmpty()) out.put(K_SMS_CODE, sms);
        List<Hit> cvc = scanContext(text, CVC_CTX, DIGITS_3_TO_4, CVC_WINDOW, K_CVC);
        if (!cvc.isEmpty()) out.put(K_CVC, cvc);
        dedupeAcrossKinds(out);
        return out;
    }

    private static List<Hit> scanCards(String text) {
        List<Hit> out = new ArrayList<>();
        Matcher m = CARD.matcher(text);
        while (m.find()) {
            String digits = m.group(1).replace(" ", "").replace("-", "");
            int len = digits.length();
            if (len < 13 || len > 19) continue;
            if (digits.charAt(0) == '0') continue;
            if (allSameDigit(digits)) continue;
            if (!luhnValid(digits)) continue;
            // 13/15 位与毫秒时间戳形态无法区分：必须有卡上下文才判卡号
            if ((len == 13 || len == 15) && !hasCardContext(text, m.start(1), m.end(1))) {
                continue;
            }
            out.add(new Hit(K_BANK_CARD, Action.BLOCK, m.start(1), m.end(1), null));
        }
        return out;
    }

    private static boolean hasCardContext(String text, int start, int end) {
        int from = Math.max(0, start - CARD_CTX_WINDOW);
        int to = Math.min(text.length(), end + CARD_CTX_WINDOW);
        return CARD_CTX.matcher(text.substring(from, to)).find();
    }

    private static List<Hit> scanIds(String text) {
        List<Hit> out = new ArrayList<>();
        Matcher m = ID_CARD.matcher(text);
        while (m.find()) {
            String id = m.group(1);
            if (!gb11643Valid(id)) continue;
            out.add(new Hit(K_ID_CARD, Action.BLOCK, m.start(1), m.end(1), null));
        }
        return out;
    }

    /**
     * 上下文命中：先找关键词位置，再找格式数字；只有「格式对 + 附近有关键词」才算。
     * 这是本类最容易写错的地方：没有关键词时，6 位数字**绝不能**当支付密码。
     */
    private static List<Hit> scanContext(String text, Pattern keyword, Pattern digits,
                                         int window, String kind) {
        List<int[]> ranges = new ArrayList<>();
        Matcher km = keyword.matcher(text);
        while (km.find()) ranges.add(new int[]{km.start(), km.end()});
        if (ranges.isEmpty()) return new ArrayList<>();

        List<Hit> out = new ArrayList<>();
        Matcher dm = digits.matcher(text);
        while (dm.find()) {
            int s = dm.start(), e = dm.end();
            if (near(ranges, s, e, window)) {
                out.add(new Hit(kind, Action.BLOCK, s, e, null));
            }
        }
        return out;
    }

    private static boolean near(List<int[]> ranges, int start, int end, int window) {
        for (int[] r : ranges) {
            // 关键词 end >= 数字 start - window 且关键词 start <= 数字 end + window
            if (r[1] >= start - window && r[0] <= end + window) return true;
        }
        return false;
    }

    /** 同一段数字同时满足多类 BLOCK（如「密码/验证码」都写在旁边）时只保留优先级最高的一条 */
    private static void dedupeAcrossKinds(Map<String, List<Hit>> out) {
        Map<String, String> owner = new LinkedHashMap<>();
        for (Map.Entry<String, List<Hit>> e : new ArrayList<>(out.entrySet())) {
            List<Hit> kept = new ArrayList<>();
            for (Hit h : e.getValue()) {
                String key = h.start + ":" + h.end;
                String existing = owner.get(key);
                if (existing == null) {
                    owner.put(key, e.getKey());
                    kept.add(h);
                }
            }
            if (kept.isEmpty()) out.remove(e.getKey()); else e.setValue(kept);
        }
    }

    // ---------------- MASK ----------------

    private static List<Hit> maskHits(String text) {
        List<Hit> out = new ArrayList<>();
        Matcher pm = PHONE.matcher(text);
        while (pm.find()) {
            String phone = pm.group();
            String masked = phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
            out.add(new Hit(K_PHONE, Action.MASK, pm.start(), pm.end(), masked));
        }
        Matcher em = EMAIL.matcher(text);
        while (em.find()) {
            String email = em.group();
            int at = email.indexOf('@');
            if (at <= 0) continue;
            String masked = email.substring(0, 1) + "***" + email.substring(at);
            out.add(new Hit(K_EMAIL, Action.MASK, em.start(), em.end(), masked));
        }
        out.sort(Comparator.comparingInt(h -> h.start));
        return out;
    }

    private static String applyMasks(String text, List<Hit> hits) {
        StringBuilder sb = new StringBuilder(text);
        // 从后往前替换：替换长度会变（掩码通常更短），从后往前才能保持前面的原始偏移有效
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.replacement == null) continue;
            sb.replace(h.start, h.end, h.replacement);
        }
        return sb.toString();
    }

    // ---------------- 校验位 ----------------

    /** Luhn：从右往左，偶数位 ×2（>9 则 -9），总和 %10 == 0 */
    static boolean luhnValid(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) return false;
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /** GB 11643：17 位加权和 % 11 → 校验字符 */
    static boolean gb11643Valid(String id) {
        if (id == null || id.length() != 18) return false;
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') return false;
            sum += (c - '0') * GB_WEIGHTS[i];
        }
        char expect = GB_CHECK[sum % 11];
        char got = Character.toUpperCase(id.charAt(17));
        return got == expect;
    }

    private static boolean allSameDigit(String digits) {
        char c = digits.charAt(0);
        for (int i = 1; i < digits.length(); i++) if (digits.charAt(i) != c) return false;
        return true;
    }

    public Guard() { }
}
