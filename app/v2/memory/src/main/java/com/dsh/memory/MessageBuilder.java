package com.dsh.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 行 → 对象消息。
 * 原则：结构（sender/气泡）只来自 A11Y 节点或坐标几何；文本只来自 OCR。
 * 禁止从 OCR 字符串猜 sender / 消息边界。 */
public final class MessageBuilder {

    /** 相邻两行 y 间距小于该值视为同一消息（坐标几何，约 1.5 行高）。 */
    public static final int MERGE_GAP_PX = 90;
    public static final double SIM_THRESHOLD = 0.9;

    public static final class Anchor {
        public String text = "";
        public int left, top, right, bottom;
    }

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
        return build(lines, java.util.Collections.emptyMap());
    }

    public static List<Msg> build(List<LineItem> lines, Map<String, List<Anchor>> anchors) {
        List<Msg> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return out;
        Map<String, List<LineItem>> byObject = new LinkedHashMap<>();
        for (LineItem l : lines) {
            if (l == null || l.text == null || l.text.trim().isEmpty()) continue;
            if (l.kind != null && !"CONTENT".equals(l.kind) && !"SYSTEM".equals(l.kind)) continue;
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
                        addFrame(merged, e.getKey(), current, anchors); current = new ArrayList<>();
                    }
                    current.add(l); lastY = l.yTop;
                }
                if (!current.isEmpty()) addFrame(merged, e.getKey(), current, anchors);
            }
            out.addAll(merged.values());
        }
        return out;
    }

    private static void addFrame(Map<String, Msg> merged, String objectKey,
                                 List<LineItem> lines, Map<String, List<Anchor>> anchors) {
        if (lines.isEmpty()) return;
        LineItem first = lines.get(0);
        List<Anchor> as = anchors.get(first.shotRef == null ? "" : first.shotRef);
        String sender = senderFor(first, as);
        StringBuilder b = new StringBuilder();
        for (LineItem l : lines) {
            String t = l.text == null ? "" : l.text.trim();
            if (t.isEmpty()) continue;
            // A11Y sender 节点若被投影成内容行，结构上移除（不是文本猜 sender）
            if (as != null && isAnchorText(t, l.yTop, as)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(t);
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
            hit.pkg = first.pkg == null ? "" : first.pkg;
            hit.tFirst = first.time;
            hit.text = text;
            hit.textKey = key;
            hit.sender = sender;
            hit.blockId = first.blockId;
            hit.frameId = first.frameId;
            merged.put(key, hit);
        } else {
            hit.seenCount++;
            hit.tLast = Math.max(hit.tLast, lines.get(lines.size() - 1).time);
            if (hit.sender.isEmpty() && !sender.isEmpty()) hit.sender = sender;
        }
        hit.tLast = Math.max(hit.tLast, lines.get(lines.size() - 1).time);
        hit.seenCount = Math.max(1, hit.seenCount);
    }

    /** sender 只取 A11Y 锚点：上方/同一水平带、横向重叠、短文本；拿不到返回 ""，不猜。 */
    private static String senderFor(LineItem first, List<Anchor> anchors) {
        if (anchors == null || anchors.isEmpty()) return "";
        Anchor best = null; int bestGap = Integer.MAX_VALUE;
        for (Anchor a : anchors) {
            if (a.text == null || a.text.trim().isEmpty()) continue;
            if (a.text.length() > 30) continue;
            if (a.text.contains(":") || a.text.contains("：")) continue;
            if (a.top > first.yTop) continue;                 // sender 在上方
            int gap = first.yTop - a.top;
            if (gap > 180) continue;
            // 同一屏内按 y 最近取 A11Y 锚点；lines 暂未存 x，水平重叠留待 lines 带 left/right
            if (gap < bestGap) { bestGap = gap; best = a; }
        }
        return best == null ? "" : best.text.trim();
    }

    private static boolean isAnchorText(String t, int y, List<Anchor> as) {
        for (Anchor a : as) {
            if (a.text == null) continue;
            if (t.equals(a.text.trim()) && Math.abs(y - a.top) <= 100) return true;
        }
        return false;
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
