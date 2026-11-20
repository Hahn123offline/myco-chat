package com.dsh.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 行 → 对象消息：按 frame 内 y 邻近合并，跨 frame 去重，sender 从前缀提取。 */
public final class MessageBuilder {

    /** 相邻两行 y 间距小于该值视为同一条消息（约 1.5 行高，屏幕坐标）。 */
    public static final int MERGE_GAP_PX = 90;
    /** 文本相似度 >= 0.9 视为 OCR 变体。 */
    public static final double SIM_THRESHOLD = 0.9;

    public static final class Msg {
        public String objectKey = "";
        public String pkg = "";
        public long tFirst, tLast;
        public int seenCount;
        public String sender = "";
        public String text = "";
        public String textKey = "";
        public long blockId, frameId;
    }

    private MessageBuilder() { }

    public static List<Msg> build(List<LineItem> lines) {
        List<Msg> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return out;
        Map<String, List<LineItem>> byObject = new LinkedHashMap<>();
        for (LineItem l : lines) {
            if (l == null || l.text == null || l.text.trim().isEmpty()) continue;
            if (l.kind != null && !"CONTENT".equals(l.kind) && !"SYSTEM".equals(l.kind)
                    && !isSenderOnly(l.text)) continue;
            String k = l.objectKey == null || l.objectKey.isEmpty() ? l.pkg : l.objectKey;
            byObject.computeIfAbsent(k, x -> new ArrayList<>()).add(l);
        }
        for (Map.Entry<String, List<LineItem>> e : byObject.entrySet()) {
            List<LineItem> xs = e.getValue();
            xs.sort(Comparator.comparingLong(a -> a.time));
            Map<String, List<LineItem>> frames = new LinkedHashMap<>();
            for (LineItem l : xs) frames.computeIfAbsent(l.frameId + ":" + l.blockId,
                    x -> new ArrayList<>()).add(l);
            Map<String, Msg> merged = new LinkedHashMap<>();
            for (List<LineItem> frame : frames.values()) {
                frame.sort(Comparator.comparingInt(a -> a.yTop));
                List<LineItem> current = new ArrayList<>();
                int lastY = Integer.MIN_VALUE;
                for (LineItem l : frame) {
                    if (lastY != Integer.MIN_VALUE && Math.abs(l.yTop - lastY) > MERGE_GAP_PX) {
                        addFrame(out, merged, e.getKey(), current); current = new ArrayList<>();
                    }
                    current.add(l); lastY = l.yTop;
                }
                if (!current.isEmpty()) addFrame(out, merged, e.getKey(), current);
            }
            out.addAll(merged.values());
        }
        for (Msg m : out) {
            if (m.textKey.isEmpty()) m.textKey = normalize(m.text);
        }
        return out;
    }

    private static void addFrame(List<Msg> out, Map<String, Msg> merged, String objectKey,
                                 List<LineItem> lines) {
        if (lines.isEmpty()) return;
        StringBuilder b = new StringBuilder();
        String sender = "";
        for (LineItem l : lines) {
            String t = l.text == null ? "" : l.text.trim();
            if (t.isEmpty()) continue;
            if (isSenderOnly(t)) {
                String s = cleanSender(t);
                if (!s.isEmpty()) sender = s;
                continue;   // sender-only 行不单独成为消息，归给下一条
            }
            SenderPrefix sp = splitSender(t);
            if (!sp.sender.isEmpty()) sender = sp.sender;
            if (b.length() > 0) b.append(' ');
            b.append(sp.text);
        }
        String text = b.toString().trim();
        if (text.isEmpty()) return;
        String key = normalize(text);
        if (key.isEmpty()) return;
        Msg hit = null;
        for (Msg m : merged.values()) {
            if (m.textKey.equals(key)) { hit = m; break; }
            if (m.textKey.length() >= 8 && key.length() >= 8
                    && similarity(m.textKey, key) >= SIM_THRESHOLD) { hit = m; break; }
        }
        if (hit == null) {
            hit = new Msg();
            hit.objectKey = objectKey;
            hit.pkg = lines.get(0).pkg == null ? "" : lines.get(0).pkg;
            hit.tFirst = lines.get(0).time;
            hit.text = text;
            hit.textKey = key;
            hit.sender = sender;
            hit.blockId = lines.get(0).blockId;
            hit.frameId = lines.get(0).frameId;
            merged.put(key, hit);
        } else {
            hit.seenCount++;
            hit.tLast = Math.max(hit.tLast, lines.get(lines.size() - 1).time);
            if (hit.sender.isEmpty() && !sender.isEmpty()) hit.sender = sender;
        }
        hit.tLast = Math.max(hit.tLast, lines.get(lines.size() - 1).time);
        hit.seenCount = Math.max(1, hit.seenCount);
    }

    static final class SenderPrefix {
        final String sender, text;
        SenderPrefix(String s, String t) { sender = s; text = t; }
    }

    /** 只有发送者名、没有消息正文的行：不单独成消息，归给下一条。 */
    public static boolean isSenderOnly(String t) {
        if (t == null) return false;
        String s = t.trim().replaceFirst("^[\\p{Punct}\\s]+", "");
        if (s.isEmpty() || s.length() > 40) return false;
        if (s.contains(":") || s.contains("：")) return false;
        if (s.startsWith("[群主") || s.startsWith("(群主") || s.startsWith("（群主")
                || s.startsWith("群主")) return true;
        if (s.matches("^\\d{2}\\s*-.*")) return true;           // 25-鄂-计算机-曾嘉睿
        if (s.matches("^[A-Za-z0-9_\\-]{2,20}$") && !s.contains(".")) return true;
        return false;
    }

    private static String cleanSender(String t) {
        String s = t.trim();
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end > 0) return s.substring(0, end + 1);
        }
        if (s.startsWith("(") || s.startsWith("（")) {
            int end = Math.max(s.indexOf(')'), s.indexOf('）'));
            if (end > 0) return s.substring(0, end + 1);
        }
        int i = s.indexOf(':');
        if (i <= 0) i = s.indexOf('：');
        return i > 0 ? s.substring(0, i).trim() : s;
    }

    private static SenderPrefix splitSender(String t) {
        String s = t;
        if (s.startsWith("[群主") || s.startsWith("(群主") || s.startsWith("（群主")) {
            int end = Math.max(Math.max(s.indexOf(']'), s.indexOf(')')), s.indexOf('）'));
            if (end > 0) return new SenderPrefix(s.substring(0, end + 1), s.substring(end + 1).trim());
        }
        int i = s.indexOf(':');
        if (i <= 0 || i > 24) i = s.indexOf('：');
        if (i > 0 && i <= 24) {
            String sender = s.substring(0, i).trim();
            if (!sender.isEmpty() && !sender.contains("http")) {
                return new SenderPrefix(sender, s.substring(i + 1).trim());
            }
        }
        return new SenderPrefix("", t);
    }

    /** 去空白/标点、统一全半角，作为去重键。 */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '！' && c <= '～') c = (char) (c - 0xFEE0);
            if (Character.isLetterOrDigit(c)) b.append(Character.toLowerCase(c));
        }
        return b.toString();
    }

    /** 汉字 2-gram + 拉丁字符的 Jaccard 近似。 */
    public static double similarity(String a, String b) {
        java.util.Set<String> x = grams(a), y = grams(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        int inter = 0;
        for (String g : x) if (y.contains(g)) inter++;
        return inter / (double) (x.size() + y.size() - inter);
    }

    private static java.util.Set<String> grams(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (int i = 0; i + 2 <= s.length(); i++) out.add(s.substring(i, i + 2));
        return out;
    }
}
