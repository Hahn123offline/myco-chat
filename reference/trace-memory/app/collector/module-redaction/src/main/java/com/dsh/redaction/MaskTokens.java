package com.dsh.redaction;

import java.util.ArrayList;
import java.util.List;

/**
 * 掩码 token 的识别与定位。纯形态判断（规则做校验，不做理解）。
 *
 * 用途：
 *  1) span 保护：模型只能**补充**掩码，不能动规则层已经掩过的字符区间；
 *  2) 批量升级时，从已入库的 masked_text 里重新认出受保护区间（批处理没有原始 span 列表）。
 *
 * 识别依据是"形态"：连续的 '*' 串（前后可带前后缀，如 138*****678 / 广东省****号）
 * 和固定的验证码丢弃标记。不判断这个 token 原来是什么。
 */
public final class MaskTokens {

    /** RuleLayer 丢弃验证码后留下的固定标记 */
    public static final String VERIFY_DROPPED = "[验证码已丢弃]";

    /** 视为 token 边界的字符：空白 + 常见中英文标点/括号 + '*'（相邻掩码不合并成一个巨大的区间） */
    private static final String BOUNDARY = " \t\r\n*，。！？；：、,.!?;:()（）[]【】{}<>《》\"'“”‘’`‖|/\\";

    /** 一个受保护区间（[start, end)） */
    public static final class Span {
        public final int start;
        public final int end;
        public final String text;

        public Span(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text == null ? "" : text;
        }

        public boolean overlaps(int a, int b) { return a < end && b > start; }

        public boolean contains(int a, int b) { return start <= a && b <= end; }

        @Override public String toString() { return "[" + start + "," + end + ")=" + text; }
    }

    private MaskTokens() { }

    /** 找出文本里所有形态上的掩码 token 区间（按起点排序、重叠合并） */
    public static List<Span> scan(String text) {
        List<Span> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) return spans;

        int i = 0;
        while ((i = text.indexOf(VERIFY_DROPPED, i)) >= 0) {
            spans.add(new Span(i, i + VERIFY_DROPPED.length(), VERIFY_DROPPED));
            i += VERIFY_DROPPED.length();
        }

        int p = 0;
        while (p < text.length()) {
            if (text.charAt(p) != '*') { p++; continue; }
            int runStart = p;
            while (p < text.length() && text.charAt(p) == '*') p++;
            int runLen = p - runStart;
            if (runLen < 2) continue;

            // 向两侧扩展到非边界字符：规则掩码可能保留前后缀（138*****678 / 广东省****号）
            int s = runStart;
            while (s > 0 && !isBoundary(text.charAt(s - 1))) s--;
            int e = p;
            while (e < text.length() && !isBoundary(text.charAt(e))) e++;
            spans.add(new Span(s, e, text.substring(s, e)));
        }
        return merge(spans);
    }

    public static boolean overlapsAny(List<Span> spans, int start, int end) {
        if (spans == null) return false;
        for (Span s : spans) if (s.overlaps(start, end)) return true;
        return false;
    }

    /** 合并重叠/相接区间（去重后按起点排序）。scan 与"已知 token 定位"的并集用它。 */
    public static List<Span> union(List<Span> spans) {
        if (spans == null || spans.isEmpty()) return new ArrayList<>();
        return merge(new ArrayList<>(spans));
    }

    /**
     * 把一个候选片段变成**等长**的掩码：保留首字符，其余打星。
     * 等长是刻意的：这样替换不会移动其它字符的位置，规则掩码区间的位置校验才能做。
     */
    public static String maskFragment(String fragment) {
        if (fragment == null || fragment.isEmpty()) return "";
        int n = fragment.length();
        if (n == 1) return "*";
        StringBuilder sb = new StringBuilder(n);
        sb.append(fragment.charAt(0));
        for (int i = 1; i < n; i++) sb.append('*');
        return sb.toString();
    }

    private static boolean isBoundary(char c) {
        return Character.isWhitespace(c) || BOUNDARY.indexOf(c) >= 0;
    }

    private static List<Span> merge(List<Span> spans) {
        if (spans.size() <= 1) return spans;
        spans.sort((a, b) -> Integer.compare(a.start, b.start));
        List<Span> out = new ArrayList<>();
        Span cur = spans.get(0);
        for (int i = 1; i < spans.size(); i++) {
            Span n = spans.get(i);
            if (n.start <= cur.end) {
                int end = Math.max(cur.end, n.end);
                cur = new Span(cur.start, end, cur.text);
            } else {
                out.add(cur);
                cur = n;
            }
        }
        out.add(cur);
        return out;
    }
}
