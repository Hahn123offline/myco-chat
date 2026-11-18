package com.dsh.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 危险信号提名（只提名，不判决）。纯逻辑，可 JVM 测试。 */
public final class DangerSignals {

    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[\\s-]?){15,19}\\d(?!\\d)");
    private static final Pattern SMS6 = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");
    private static final Pattern ID18 = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:[¥$￥]\\s*\\d[\\d,.]*|\\d[\\d,.]*\\s*(?:元|块))");

    private DangerSignals() { }

    public static List<String> nominate(String text, PageElement.Role role, String pageTitle) {
        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text;
        if (role == PageElement.Role.INPUT && !t.trim().isEmpty()) out.add("input_content");
        if (CARD.matcher(t).find()) out.add("card_candidate");
        if (ID18.matcher(t).find()) out.add("id_card");
        if (SMS6.matcher(t).find()) out.add("sms_code");
        if (PHONE.matcher(t).find()) out.add("contact_phone");
        if (EMAIL.matcher(t).find()) out.add("contact_email");
        if (AMOUNT.matcher(t).find()) out.add("amount");
        if (pageTitle != null && (pageTitle.contains("支付") || pageTitle.contains("转账")
                || pageTitle.contains("订单"))) {
            out.add("page_sensitive");
        }
        return out;
    }

    /** 页面级信号（标题命中时给所有元素提名）。 */
    public static boolean pageSensitive(String pageTitle) {
        return pageTitle != null && (pageTitle.contains("支付") || pageTitle.contains("转账")
                || pageTitle.contains("订单"));
    }
}
