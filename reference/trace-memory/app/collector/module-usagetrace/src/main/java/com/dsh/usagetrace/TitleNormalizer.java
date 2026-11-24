package com.dsh.usagetrace;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题归一：纯函数，可单测。
 * 目标：把 OCR 出来的脏字符串变成稳定的场景名。
 */
public final class TitleNormalizer {

    private TitleNormalizer() { }

    private static final Pattern HAN_RUN = Pattern.compile(
            "(?:[0-9]{1,4})?[\\u4e00-\\u9fff][\\u4e00-\\u9fff0-9A-Za-z（）()·\\-。:： ]{1,22}");
    private static final Pattern COUNT_SUFFIX = Pattern.compile("[（(]\\s*\\d+\\s*[)）]");
    private static final Pattern TIME_LIKE = Pattern.compile(".*\\d{1,2}[:：]\\d{2}.*");
    private static final Pattern PURE_NUM = Pattern.compile("^[\\d\\s.%,:/：]+$");

    /** 常见 OCR 混淆纠正（保守：只改整词里明确错的） */
    private static final String[][] TYPO = {
            {"伎输", "传输"}, {"传輸", "传输"}, {"伎輸", "传输"},
            {"文件伎", "文件传"},
    };

    public static int cjkCount(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fff) n++;
        }
        return n;
    }

    /** @return 归一后的标题；不合格返回 "" */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (PURE_NUM.matcher(s).matches()) return "";
        if (TIME_LIKE.matcher(s).matches()) return "";

        // 取最长汉字段，切掉首尾残渣
        Matcher m = HAN_RUN.matcher(s);
        String best = "";
        while (m.find()) {
            if (cjkCount(m.group()) > cjkCount(best)) best = m.group();
        }
        if (best.isEmpty()) return "";

        // 去尾部非汉字（保留右括号）
        best = best.replaceAll("[^\\u4e00-\\u9fff)）]+$", "").trim();
        // 去未读数/人数
        best = COUNT_SUFFIX.matcher(best).replaceAll("").trim();
        if (cjkCount(best) < 2) return "";

        // 错字纠正
        for (String[] p : TYPO) best = best.replace(p[0], p[1]);

        return best.length() > 20 ? best.substring(0, 20) : best;
    }

    /** 两个标题是否指同一页：包含 / 长公共前缀 / 编辑距离很小（吃掉 OCR 错字） */
    public static boolean sameTitle(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.contains(b) || b.contains(a)) return true;
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        if (i >= 4) return true;
        return levenshtein(a, b) <= 2;
    }

    static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }
}
