package com.dsh.v2;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 无障碍树 → PageElement（bbox + role + 提名）；不做敏感判决。 */
public final class PageParser {

    public static final int MAX_NODES = 300;
    public static final int MAX_ELEMENTS = 120;

    public static final class OcrLine {
        public final String text;
        public final int left, top, right, bottom;
        public OcrLine(String text, int l, int t, int r, int b) {
            this.text = text == null ? "" : text;
            this.left = l; this.top = t; this.right = r; this.bottom = b;
        }
    }

    public static final class Result {
        public final String pageTitle;
        public final List<PageElement> elements;
        public final long elapsedMs;
        Result(String title, List<PageElement> e, long ms) {
            this.pageTitle = title == null ? "" : title;
            this.elements = e == null ? new ArrayList<>() : e;
            this.elapsedMs = ms;
        }
    }

    private PageParser() { }

    public static final class TitleCandidate {
        public final String text;
        public final int left, top, right, bottom;
        public final PageElement.Role role;
        public TitleCandidate(String text, int l, int t, int r, int b, PageElement.Role role) {
            this.text = text; this.left = l; this.top = t; this.right = r;
            this.bottom = b; this.role = role;
        }
    }

    private static final java.util.regex.Pattern TITLE_NOISE = java.util.regex.Pattern.compile(
            "^(返回.*|聊天设置|听筒模式|设置|搜索|取消|更多|发送|语音|相册|拍照|表情|"
                    + "更多功能|消息|频道|联系人|动态|QQ|微信|现在|昨天|星期.|"
                    + "\\(\\d+\\)|\\d{1,2}:\\d{2}|\\d+条新消息|.*, ,.*)$");

    /** 从同屏节点推断标题栏：顶部区 + 水平居中 + BUTTON/PARAGRAPH，排除导航/工具栏。 */
    public static String inferTitle(List<TitleCandidate> nodes, int screenWidth) {
        if (nodes == null || nodes.isEmpty()) return "";
        int w = Math.max(1, screenWidth);
        TitleCandidate best = null;
        double bestScore = 1e9;
        for (TitleCandidate n : nodes) {
            if (n == null || n.text == null) continue;
            String t = n.text.trim();
            if (t.length() < 2 || t.length() > 40) continue;
            if (n.top > 350) continue;
            if (TITLE_NOISE.matcher(t).matches()) continue;
            if (n.role != PageElement.Role.BUTTON && n.role != PageElement.Role.PARAGRAPH
                    && n.role != PageElement.Role.TITLE) continue;
            int center = (n.left + n.right) / 2;
            if (center < w * 0.12 || center > w * 0.88) continue;
            double score = Math.abs(n.top - 170) / 100.0
                    + Math.abs(center - w / 2.0) / Math.max(1.0, w / 2.0);
            if (n.role == PageElement.Role.BUTTON) score -= 0.2;
            if (score < bestScore) { bestScore = score; best = n; }
        }
        return best == null ? "" : best.text.trim();
    }

    public static Result parse(AccessibilityNodeInfo root) {
        long t0 = System.currentTimeMillis();
        List<PageElement> out = new ArrayList<>();
        List<TitleCandidate> titleCandidates = new ArrayList<>();
        Rect rootBounds = new Rect();
        root.getBoundsInScreen(rootBounds);
        String pageTitle = "";
        if (root == null) return new Result("", out, System.currentTimeMillis() - t0);
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0, idx = 0;
        while (!queue.isEmpty() && visited < MAX_NODES && out.size() < MAX_ELEMENTS) {
            AccessibilityNodeInfo n = queue.poll();
            if (n == null) continue;
            visited++;
            CharSequence cs = n.getText();
            if (cs == null || cs.length() == 0) cs = n.getContentDescription();
            String text = cs == null ? "" : cs.toString().trim();
            if (!text.isEmpty() && n.isVisibleToUser()) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    PageElement.Role role = roleOf(n, text);
                    titleCandidates.add(new TitleCandidate(text, r.left, r.top, r.right,
                            r.bottom, role));
                    if (pageTitle.isEmpty() && role == PageElement.Role.TITLE) pageTitle = text;
                    List<String> signals = DangerSignals.nominate(text, role, pageTitle);
                    out.add(new PageElement("a" + (idx++), text, r.left, r.top, r.right, r.bottom,
                            role, PageElement.Source.A11Y, 1.0, signals));
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) queue.add(c);
            }
        }
        if (pageTitle.isEmpty()) pageTitle = inferTitle(titleCandidates, rootBounds.width());
        // 标题后置：给所有元素补 page_sensitive 提名
        if (DangerSignals.pageSensitive(pageTitle) && !out.isEmpty()) {
            List<PageElement> marked = new ArrayList<>();
            for (PageElement e : out) {
                List<String> sig = new ArrayList<>(e.signals);
                if (!sig.contains("page_sensitive")) sig.add("page_sensitive");
                marked.add(new PageElement(e.id, e.text, e.left, e.top, e.right, e.bottom,
                        e.role, e.source, e.confidence, sig));
            }
            out = marked;
        }
        return new Result(pageTitle, out, System.currentTimeMillis() - t0);
    }

    static PageElement.Role roleOf(AccessibilityNodeInfo n, String text) {
        String cls = n.getClassName() == null ? "" : n.getClassName().toString();
        return roleFor(n.isEditable(), n.isHeading(), cls, n.isClickable(), text);
    }

    /** 纯映射，便于单测。 */
    public static PageElement.Role roleFor(boolean editable, boolean heading, String cls,
                                           boolean clickable, String text) {
        String c = cls == null ? "" : cls;
        if (editable || c.contains("EditText")) return PageElement.Role.INPUT;
        if (heading) return PageElement.Role.TITLE;
        if (c.contains("Button")) return PageElement.Role.BUTTON;
        if (clickable && text != null && text.length() <= 6) {
            if (text.matches("\\d+")) return PageElement.Role.BADGE;
            return PageElement.Role.BUTTON;
        }
        if (c.contains("RecyclerView") || c.contains("ListView")) {
            return PageElement.Role.LIST_ITEM;
        }
        return PageElement.Role.PARAGRAPH;
    }

    /** OCR 坐标→元素；文本相同标 BOTH，新增 OCR-only。 */
    public static List<PageElement> mergeOcr(List<PageElement> a11y, List<OcrLine> lines,
                                             String pageTitle) {
        List<PageElement> out = new ArrayList<>(a11y == null ? new ArrayList<>() : a11y);
        if (lines == null) return out;
        int idx = out.size();
        for (OcrLine l : lines) {
            String norm = norm(l.text);
            if (norm.isEmpty()) continue;
            boolean matched = false;
            for (int i = 0; i < out.size(); i++) {
                PageElement e = out.get(i);
                if (norm(e.text).equals(norm)) {
                    out.set(i, e.withSource(PageElement.Source.BOTH, 1.0,
                            DangerSignals.nominate(l.text, e.role, pageTitle)));
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                PageElement.Role role = PageElement.Role.PARAGRAPH;
                out.add(new PageElement("o" + (idx++), l.text, l.left, l.top, l.right, l.bottom,
                        role, PageElement.Source.OCR, 0.8,
                        DangerSignals.nominate(l.text, role, pageTitle)));
            }
        }
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").trim();
    }
}
