package com.dsh.masker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 确定性规则判定器（第一版；1B 以后实现同一接口复核）。 */
public final class RuleMaskJudge implements MaskJudge {

    private static final Pattern ID18 = Pattern.compile(
            "(?<!\\d)\\d{17}[\\dXx](?:\\d{1,4})?(?!\\d)");
    private static final Pattern CARD = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){15,24}\\d(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern PHONE_LONG = Pattern.compile("(?<!\\d)1[3-9]\\d{10,15}(?!\\d)");
    private static final Pattern CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");
    private static final Pattern CODE_LONG = Pattern.compile("(?<!\\d)\\d{6,8}(?!\\d)");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:[¥$￥]\\s*\\d[\\d,.]*|\\d[\\d,.]*\\s*(?:元|块))");
    private static final Pattern CODE_HINT = Pattern.compile("验证码|动态码|校验码|验证代码|code");

    @Override public List<ElementSpan> judge(List<MaskElement> elements) {
        List<ElementSpan> out = new ArrayList<>();
        if (elements == null) return out;
        for (MaskElement e : elements) {
            List<ElementSpan> found = new ArrayList<>();
            add(found, e, ID18, "id_card");
            add(found, e, CARD, "card");
            add(found, e, EMAIL, "email");
            add(found, e, PHONE, "phone");
            add(found, e, PHONE_LONG, "phone");
            codeOnlyWithHint(found, e);
            add(found, e, AMOUNT, "amount");
            found.sort(Comparator.comparingInt(s -> s.start));
            int lastEnd = -1;
            for (ElementSpan s : found) {
                if (s.start < lastEnd) continue;  // 重叠只保留优先级更高的
                out.add(s);
                lastEnd = s.end;
            }
        }
        return out;
    }

    private static void add(List<ElementSpan> out, MaskElement e, Pattern p, String kind) {
        Matcher m = p.matcher(e.text);
        while (m.find()) out.add(new ElementSpan(e.id, m.start(), m.end(), kind));
    }

    /** 6 位纯数字只有在验证码上下文里才提名，避免普通评论里的数字被误伤。 */
    private static void codeOnlyWithHint(List<ElementSpan> out, MaskElement e) {
        Matcher m = CODE_LONG.matcher(e.text);
        while (m.find()) {
            int lo = Math.max(0, m.start() - 10);
            int hi = Math.min(e.text.length(), m.end() + 10);
            String around = e.text.substring(lo, hi);
            if (CODE_HINT.matcher(around).find()) {
                out.add(new ElementSpan(e.id, m.start(), m.end(), "code"));
            }
        }
    }
}
