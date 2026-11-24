package com.dsh.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * pageId 第二级的「稳定文本指纹」：前 N 个有用文本节点，按固定顺序用 \u0001 拼接后 SHA-1。
 *
 * 为什么不用「首个可点击容器」：真机数据（779 条 trace）显示同 activity 内
 * distinct/trace = 0.52——推荐位/计数/广告词一变就换 pageId，把连续停留切碎。
 * 这里**只取文本**，不取可点击性；遍历顺序由调用方固定（BFS）。
 *
 * 「有用文本」= 去首尾空白后长度 ≥2、含至少一个字母/数字、且不是纯数字。
 * （纯数字/纯标点/过短的是计数与图标文字，不能当分页信号。）
 *
 * 不做任何语义判断/标题归一化/相似度阈值——只是过滤结构噪声 + 取哈希。
 */
public final class TextFingerprint {

    /** 取前 N 个有用文本节点 */
    public static final int N = 8;
    /** 节点文本拼接分隔符 */
    public static final char SEP = '\u0001';

    private TextFingerprint() { }

    /** 该节点文本是否适合作为分页信号 */
    public static boolean useful(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.codePointCount(0, s.length()) < 2) return false;
        boolean hasLetterOrDigit = false;
        boolean allDigits = true;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) hasLetterOrDigit = true;
            if (!Character.isDigit(cp)) allDigits = false;
            i += Character.charCount(cp);
        }
        return hasLetterOrDigit && !allDigits;
    }

    /** 过滤 + 取前 N 个，保持遍历顺序 */
    public static List<String> firstUseful(List<String> nodeTexts) {
        List<String> out = new ArrayList<>();
        if (nodeTexts == null) return out;
        for (String s : nodeTexts) {
            if (useful(s)) {
                out.add(s.trim());
                if (out.size() >= N) break;
            }
        }
        return out;
    }

    /** 按顺序用 \u0001 拼接；没有有用文本时返回空串 */
    public static String joined(List<String> nodeTexts) {
        List<String> useful = firstUseful(nodeTexts);
        if (useful.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < useful.size(); i++) {
            if (i > 0) b.append(SEP);
            b.append(useful.get(i));
        }
        return b.toString();
    }

    /** 前 N 个有用文本的 SHA-1 前 16 位；没有有用文本返回空串 */
    public static String hash(List<String> nodeTexts) {
        String j = joined(nodeTexts);
        return j.isEmpty() ? "" : SceneResolver.sha1(j, 16);
    }
}
