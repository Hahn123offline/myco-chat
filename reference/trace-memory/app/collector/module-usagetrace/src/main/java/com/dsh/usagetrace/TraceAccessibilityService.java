package com.dsh.usagetrace;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍服务：Android 胶水层。只负责"看"，所有决策交给 UsageTraceModule。
 */
public final class TraceAccessibilityService extends AccessibilityService {

    private static final long DEBOUNCE_MS = 300;

    private Handler h;
    private Runnable pending;
    private String curClass = "";
    private String seenPkg = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        h = new Handler(Looper.getMainLooper());
        UsageTraceModule m = UsageTraceModule.INSTANCE;
        if (m != null) m.onServiceConnected(true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || h == null) return;
        // 事实判据：只要真的收到事件就记时间（哪怕后面被过滤掉）
        UsageTraceModule.LAST_EVENT_AT.set(System.currentTimeMillis());
        int t = e.getEventType();

        CharSequence cn = e.getClassName();
        if (cn != null) {
            String c = cn.toString();
            boolean looksPage = c.contains("Activity") || c.contains(".ui.")
                    || c.contains("Launcher") || c.contains("Page");
            boolean looksView = c.contains("View") || c.contains("Layout")
                    || c.contains("Widget") || c.startsWith("androidx") || c.startsWith("android.");
            if (looksPage && !looksView) curClass = c;
        }

        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && t != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && t != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        if (pending != null) h.removeCallbacks(pending);
        pending = this::sample;
        h.postDelayed(pending, DEBOUNCE_MS);
    }

    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        UsageTraceModule m = UsageTraceModule.INSTANCE;
        if (m != null) m.onServiceConnected(false);
        super.onDestroy();
    }

    // ---------------- 采样 ----------------

    private void sample() {
        UsageTraceModule m = UsageTraceModule.INSTANCE;
        if (m == null) return;

        long now = System.currentTimeMillis();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        CharSequence pk = root.getPackageName();
        if (pk == null) return;
        String pkg = pk.toString();

        if (pkg.equals(getPackageName())) return;
        if (pkg.equals("com.android.systemui") || pkg.contains("launcher")
                || pkg.equals("com.miui.home") || pkg.equals("com.android.settings")) return;

        if (!pkg.equals(seenPkg)) { seenPkg = pkg; curClass = ""; }
        String act = sanitize(pkg, curClass);
        if (act.toLowerCase().contains("softinputwindow")
                || act.toLowerCase().contains("inputmethod")) return;

        String nodeTitle = pickNodeTitle(root);
        String text = collectText(root, 500);

        // 第一遍：先用节点树信息定场景
        m.onScreenSample(pkg, act, nodeTitle, null, null, null, null, text, now);

        // 第二遍（异步）：节点树没标题且策略要求 OCR 时，拍标题条补
        CapturePolicy p = m.policies().forPkg(pkg);
        if (nodeTitle.isEmpty() && p.ocr) {
            ResourceGovernor g = m.governor();
            if (!g.allowShot(now)) return;
            g.recordShot(now);
            ScreenGrabber.grab(this, m.framesDir(), p.titleRegion, r -> {
                if (r == null) return;
                long t2 = System.currentTimeMillis();
                if (r.titlebarFile != null && g.allowOcr(t2)) {
                    g.recordOcr(t2);
                    OcrTitleExtractor.extract(r.titlebarFile, (title, conf) ->
                            m.onScreenSample(pkg, act, "", title,
                                    String.valueOf(r.topHash), r.framePath, r.titlebarPath,
                                    text, System.currentTimeMillis()));
                } else {
                    m.onScreenSample(pkg, act, "", null,
                            String.valueOf(r.topHash), r.framePath, r.titlebarPath,
                            text, System.currentTimeMillis());
                }
            });
        }
    }

    private String sanitize(String pkg, String act) {
        if (act == null) act = "";
        if (!act.isEmpty() && !act.startsWith(pkg)) act = "";
        if (act.startsWith("android.widget.") || act.startsWith("android.view.")) act = "";
        return act;
    }

    // ---------------- 节点树 ----------------

    private static final String[] TITLE_BAD = {
            "按钮", "点我", "点击", "图片", "头像", "封面", "返回", "更多", "分享",
            "发送", "输入", "二维码", "表情", "弹幕", "设置", "登录", "打开", "关注"
    };

    private static final class Cand {
        String text;
        int top;
        boolean centered;
        boolean fromDesc;
    }

    private String pickNodeTitle(AccessibilityNodeInfo root) {
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int limit = (int) (screenH * 0.25f);
        List<Cand> cands = new ArrayList<>();
        walk(root, limit, cands, screenW, 0);

        Cand best = null;
        double bestScore = -1;
        for (Cand c : cands) {
            double score = 0;
            int len = c.text.length();
            if (len >= 4 && len <= 18) score += 2;
            else if (len >= 2 && len <= 24) score += 1;
            if (c.centered) score += 1.5;
            score += (1.0 - (double) c.top / Math.max(1, limit));
            if (bad(c.text)) score -= 3;
            if (c.fromDesc) score -= 1;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        if (best == null || bestScore <= 1.0) return "";
        return TitleNormalizer.normalize(best.text);
    }

    private boolean bad(String s) {
        for (String b : TITLE_BAD) if (s.contains(b)) return true;
        if (s.matches("^[\\d\\s.%,:/：]+$")) return true;
        return s.matches(".*\\d{1,2}[:：]\\d{2}.*");
    }

    private void walk(AccessibilityNodeInfo n, int limit, List<Cand> out, int screenW, int depth) {
        if (n == null || depth > 25 || out.size() > 80) return;
        CharSequence tx = n.getText();
        boolean fromDesc = false;
        if (tx == null || tx.length() == 0) { tx = n.getContentDescription(); fromDesc = tx != null; }
        if (tx != null && tx.length() >= 2) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.top >= 0 && r.top < limit && r.width() > 0) {
                String s = tx.toString().trim();
                if (!s.isEmpty() && !s.matches("^(\\d{1,2}[:：]\\d{2}|[\\d.%]+)$")) {
                    Cand c = new Cand();
                    c.text = s; c.top = r.top; c.fromDesc = fromDesc;
                    c.centered = Math.abs(r.centerX() - screenW / 2) < screenW * 0.18;
                    out.add(c);
                }
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) { walk(c, limit, out, screenW, depth + 1); c.recycle(); }
        }
    }

    private String collectText(AccessibilityNodeInfo root, int cap) {
        StringBuilder sb = new StringBuilder();
        walkText(root, sb, 0, cap);
        return sb.length() > cap ? sb.substring(0, cap) : sb.toString();
    }

    private void walkText(AccessibilityNodeInfo n, StringBuilder sb, int depth, int cap) {
        if (n == null || depth > 30 || sb.length() >= cap) return;
        CharSequence tx = n.getText();
        if (tx != null && tx.length() > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(tx.toString().replace('\n', ' '));
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) { walkText(c, sb, depth + 1, cap); c.recycle(); }
        }
    }
}
