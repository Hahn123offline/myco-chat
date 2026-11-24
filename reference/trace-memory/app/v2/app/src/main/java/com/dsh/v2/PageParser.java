package com.dsh.v2;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 无障碍树 → PageElement（bbox + role + 提名）；不做敏感判决。 */
public final class PageParser {

    // 访问上限只防病态树；元素配额只算"有文本"的节点，容器不占配额。
    public static final int MAX_NODES = 1500;
    public static final int MAX_ELEMENTS = 300;

    public static final class OcrLine {
        public final String text;
        public final int left, top, right, bottom;
        public OcrLine(String text, int l, int t, int r, int b) {
            this.text = text == null ? "" : text;
            this.left = l; this.top = t; this.right = r; this.bottom = b;
        }
    }

    /**
     * 滚动容器探针。注意：{@link AccessibilityNodeInfo} **没有** scrollX/scrollY 读取器
     * （只有 isScrollable()），所以位移不可能从"容器滚动位置"得到。
     * 位移只能来自：①同一节点在两次观察中的 bbox 差 ②像素栅格对齐。
     */
    public static final class ScrollProbe {
        public final String cls;
        public final String resId;
        public final int rows, cols;      // CollectionInfo，-1 = 非集合
        public final int left, top, right, bottom;
        public final int order;
        ScrollProbe(String cls, String resId, int rows, int cols,
                    int l, int t, int r, int b, int order) {
            this.cls = cls == null ? "" : cls;
            this.resId = resId == null ? "" : resId;
            this.rows = rows; this.cols = cols;
            this.left = l; this.top = t; this.right = r; this.bottom = b;
            this.order = order;
        }
        @Override public String toString() {
            String c = cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls;
            return c + "#" + order + "{id=" + resId + ",r=" + rows + ",c=" + cols
                    + ",b=[" + left + "," + top + "," + right + "," + bottom + "]}";
        }
    }

    /** 一行式输出，便于 logcat 观察。 */
    public static String describeScrollables(List<ScrollProbe> list) {
        if (list == null || list.isEmpty()) return "n=0";
        StringBuilder sb = new StringBuilder("n=").append(list.size());
        for (ScrollProbe s : list) sb.append(' ').append(s);
        return sb.toString();
    }

    public static final class Result {
        public final String pageTitle;
        public final List<PageElement> elements;
        public final long elapsedMs;
        public final List<ScrollProbe> scrollables;
        /** 实际遍历的节点数（诊断：解析成本 ≈ visited × 每次 getChild） */
        public final int visited;
        /** 诊断用分段耗时：text/visible/bounds/role/signals/children/scrollProbe */
        public final long[] stageNs;
        Result(String title, List<PageElement> e, long ms, List<ScrollProbe> sc, long[] st,
               int visited) {
            this.pageTitle = title == null ? "" : title;
            this.elements = e == null ? new ArrayList<>() : e;
            this.elapsedMs = ms;
            this.scrollables = sc == null ? new ArrayList<>() : sc;
            this.stageNs = st == null ? new long[0] : st;
            this.visited = visited;
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
        return parse(root, MAX_NODES, MAX_ELEMENTS);
    }

    /** 内容变化事件用更小配额（高频，避免打字时卡主线程）。 */
    public static Result parse(AccessibilityNodeInfo root, int maxNodes, int maxElements) {
        return parse(root, maxNodes, maxElements, 0);
    }

    /**
     * @param maxTexts >0 时收集到这么多文本就提前结束遍历。
     *                 指纹只需要前 8 条文本，但整树 getChild() 是解析成本的 95%，
     *                 早退可以少走一大半节点。
     */
    public static Result parse(AccessibilityNodeInfo root, int maxNodes, int maxElements,
                               int maxTexts) {
        long t0 = System.currentTimeMillis();
        List<PageElement> out = new ArrayList<>();
        List<TitleCandidate> titleCandidates = new ArrayList<>();
        List<ScrollProbe> scrollables = new ArrayList<>();
        long[] st = new long[7];
        if (root == null) {
            return new Result("", out, System.currentTimeMillis() - t0, scrollables, st, 0);
        }
        Rect rootBounds = new Rect();
        root.getBoundsInScreen(rootBounds);
        String pageTitle = "";
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0, idx = 0;
        while (!queue.isEmpty() && visited < maxNodes && out.size() < maxElements) {
            if (maxTexts > 0 && out.size() >= maxTexts) break;
            AccessibilityNodeInfo n = queue.poll();
            if (n == null) continue;
            visited++;
            long ts = System.nanoTime();
            // 滚动容器探针：只记录事实（class / resource-id / 集合形状 / 屏幕 bbox）
            try {
                if (n.isScrollable()) {
                    Rect sr = new Rect();
                    n.getBoundsInScreen(sr);
                    AccessibilityNodeInfo.CollectionInfo ci = n.getCollectionInfo();
                    scrollables.add(new ScrollProbe(
                            n.getClassName() == null ? "" : n.getClassName().toString(),
                            n.getViewIdResourceName(),
                            ci == null ? -1 : ci.getRowCount(),
                            ci == null ? -1 : ci.getColumnCount(),
                            sr.left, sr.top, sr.right, sr.bottom, visited));
                }
            } catch (Throwable ignored) { }
            long tn = System.nanoTime();
            st[6] += tn - ts;
            CharSequence cs = n.getText();
            if (cs == null || cs.length() == 0) cs = n.getContentDescription();
            String text = cs == null ? "" : cs.toString().trim();
            long tv0 = System.nanoTime();
            st[0] += tv0 - tn;
            boolean visible = n.isVisibleToUser();
            long tv1 = System.nanoTime();
            st[1] += tv1 - tv0;
            if (!text.isEmpty() && visible) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                long tb0 = System.nanoTime();
                st[2] += tb0 - tv1;
                if (r.width() > 0 && r.height() > 0) {
                    PageElement.Role role = roleOf(n, text);
                    long tr0 = System.nanoTime();
                    st[3] += tr0 - tb0;
                    titleCandidates.add(new TitleCandidate(text, r.left, r.top, r.right,
                            r.bottom, role));
                    if (pageTitle.isEmpty() && role == PageElement.Role.TITLE) pageTitle = text;
                    List<String> signals = DangerSignals.nominate(text, role, pageTitle);
                    long tg0 = System.nanoTime();
                    st[4] += tg0 - tr0;
                    out.add(new PageElement("a" + (idx++), text, r.left, r.top, r.right, r.bottom,
                            role, PageElement.Source.A11Y, 1.0, signals));
                }
            }
            long tc0 = System.nanoTime();
            // 视口裁剪：完全在屏幕外的子树不下钻。getChild() 是解析成本的全部，
            // 少下一个节点就少一次开销（实测 visited 是唯一有效杠杆）。
            Rect nb = new Rect();
            n.getBoundsInScreen(nb);
            boolean offScreen = nb.width() > 0 && nb.height() > 0
                    && (nb.right <= 0 || nb.left >= rootBounds.width()
                        || nb.bottom <= 0 || nb.top >= rootBounds.height());
            if (!offScreen) {
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo c = n.getChild(i);
                    if (c != null) queue.add(c);
                }
            }
            st[5] += System.nanoTime() - tc0;
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
        return new Result(pageTitle, out, System.currentTimeMillis() - t0, scrollables, st, visited);
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
                // 同位置重叠 >60%：A11Y 文本更干净，采用 A11Y 文本（位置仍用 OCR bbox）
                int best = -1;
                double bestRatio = 0;
                for (int i = 0; i < out.size(); i++) {
                    PageElement e = out.get(i);
                    if (e.source == PageElement.Source.OCR) continue;
                    double r = overlapRatio(l, e);
                    if (r > bestRatio) { bestRatio = r; best = i; }
                }
                if (best >= 0 && bestRatio > 0.6) {
                    PageElement e = out.get(best);
                    int lo = Math.min(e.text.length(), l.text.length());
                    int hi = Math.max(e.text.length(), l.text.length());
                    if (lo * 2 < hi) best = -1;   // 文本长度差 2 倍：不是同一段，别替换
                }
                if (best >= 0 && bestRatio > 0.6) {
                    PageElement e = out.get(best);
                    PageElement merged = new PageElement(e.id, e.text, l.left, l.top,
                            l.right, l.bottom, e.role, PageElement.Source.BOTH, 1.0,
                            DangerSignals.nominate(e.text, e.role, pageTitle));
                    out.set(best, merged);
                    matched = true;
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

    private static double overlapRatio(OcrLine l, PageElement e) {
        int x1 = Math.max(l.left, e.left), y1 = Math.max(l.top, e.top);
        int x2 = Math.min(l.right, e.right), y2 = Math.min(l.bottom, e.bottom);
        int inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int ocrArea = Math.max(1, (l.right - l.left) * (l.bottom - l.top));
        return inter / (double) ocrArea;
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").trim();
    }
}
