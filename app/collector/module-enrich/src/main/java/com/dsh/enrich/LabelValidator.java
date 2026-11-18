package com.dsh.enrich;

/**
 * 打标结果的**规则校验**（不靠模型自省）：空 / 超长 / 纯标点 / 复读都算失败。
 *
 * 为什么必须校验：小模型会把 prompt 说明当答案回吐、会把整段输入原样返回。
 * 校验只做"能证明这不是一句内容"的否决，不做语义理解（和门控规则同一原则）。
 */
public final class LabelValidator {

    /** 标签最长字符数（Unicode 码点） */
    public static final int MAX_CHARS = 60;

    private LabelValidator() { }

    /** 清洗：去首尾空白、合并内部换行/连续空白为一个空格、去掉包裹引号 */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace('\r', ' ').replace('\n', ' ').trim();
        s = s.replaceAll("\\s{2,}", " ");
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            boolean wrapped = (a == '"' && b == '"') || (a == '“' && b == '”')
                    || (a == '「' && b == '」') || (a == '\'' && b == '\'');
            if (wrapped) s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /**
     * @return null = 合法；否则返回失败原因（用于日志/状态）
     */
    public static String problem(String label, String maskedText) {
        String s = clean(label);
        if (s.isEmpty()) return "empty";
        if (s.codePointCount(0, s.length()) > MAX_CHARS) return "too-long";
        if (!hasContentChar(s)) return "punctuation-only";
        if (maskedText != null && !maskedText.trim().isEmpty()
                && s.equalsIgnoreCase(maskedText.trim())) {
            return "echo";
        }
        return null;
    }

    static boolean hasContentChar(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }
}
