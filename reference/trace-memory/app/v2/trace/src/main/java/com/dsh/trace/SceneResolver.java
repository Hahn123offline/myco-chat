package com.dsh.trace;

/**
 * pageId 判定：**唯一需要判断的地方，只看结构，不看语义**（规格 v1.0 §4 / task-20）。
 *
 * 身份必须**单一、稳定、可比较**：任何「有 A 用 A，没 A 用 B」的方案，只要 A 会时有时无，
 * 身份就会翻（真机教训：title 节点时有时无，同一页在 `|title:` 和 `#h8:` 之间来回翻，
 * 每次翻都触发 page_changed → 又把停留切碎）。
 *
 * 所以只有两级：
 *  1. `act:<activity>#h8:<前 8 个有用文本节点拼接的 SHA-1>`
 *     —— 不管有没有 title 节点，都走这一条；没有有用文本时对空串求哈希（保持单一路径）。
 *  2. `shot:<截图哈希>`（activity 拿不到时的兜底，例如 `android` 这种窗口）
 *
 * title 不再参与身份判定（降级为 {@link Trace#pageTitle} 字段，供显示/检索）。
 * 旧数据里的 `|title:` / `#click:` 值保持不动。
 *
 * **不许**：标题归一化、同义词合并、错别字表、编辑距离/相似度阈值。
 */
public final class SceneResolver {

    public static final String UNKNOWN = "unknown";

    private SceneResolver() { }

    /**
     * @param stableText 前 N 个有用文本节点用 \u0001 拼接后的字符串（由
     *                   {@link TextFingerprint#joined} 生成；空 = 没有有用文本，仍对空串求哈希）
     */
    public static String resolve(String activity, String stableText, String shotHash) {
        if (notEmpty(activity)) {
            String input = stableText == null ? "" : stableText;
            return "act:" + activity + "#h" + TextFingerprint.N + ":" + sha1(input, 16);
        }
        if (notEmpty(shotHash)) {
            return "shot:" + shotHash;
        }
        return UNKNOWN;
    }

    static String sha1(String s, int hexLen) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(digest.length * 2);
            for (byte x : digest) b.append(String.format("%02x", x));
            return b.substring(0, Math.min(hexLen, b.length()));
        } catch (Throwable t) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
