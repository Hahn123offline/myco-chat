package com.dsh.v2;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.dsh.trace.FrameSharpness;
import com.dsh.trace.HsvCutDetector;
import com.dsh.trace.TextFingerprint;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 无障碍服务：只负责"取结构信号 + 触发截图/OCR"，其余逻辑在 :trace 纯逻辑里。
 *
 * 线程：
 *  - onAccessibilityEvent 在主线程，只做轻量结构遍历（≤300 节点）后 submit；
 *  - takeScreenshot 的回调在 io 线程；ML Kit OCR 回调也在后台；
 *  - 数据库落盘在 TraceModule 的 trace-worker。
 */
public final class TraceAccessibilityService extends AccessibilityService {

    private static final String TAG = "DSHHost";
    /** 普通定时采样的最小间隔 */
    private static final long SHOT_MIN_THRESHOLD_MS = 2_000L;
    /** 交互触发截图的最小间隔（点击/输入/页面切换） */
    private static final long SHOT_MIN_INTERACTION_MS = 500L;
    /** 连续交互 debounce：最后一个事件之后 300ms 才截 */
    private static final long INTERACTION_DEBOUNCE_MS = 300L;

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "trace-ocr");
        t.setDaemon(true);
        return t;
    });
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingInteractionShot;
    private final HsvCutDetector cutDetector = new HsvCutDetector();
    private TextRecognizer ocr;
    private volatile String currentActivity = "";
    private volatile String lastShotHash = "";
    private volatile long lastPageT0 = 0;
    private volatile String lastPagePkg = "";
    /** 上一次解析结果；内容变化路径现在是早退版（只够指纹），完整结果在截图时单独解析。 */
    private volatile PageParser.Result lastPageResult;
    /** 最近一次活动窗口根节点：等画面稳定后的解析用它。 */
    private volatile AccessibilityNodeInfo lastRootForShot;
    /** 画面稳定后延迟解析：滚动中解析要 400ms+，稳定后只要 40ms。 */
    private static final long PARSE_SETTLE_MS = 250L;
    private final Runnable parseSettle = this::doSettleParse;
    private volatile long shotPageT0;
    private volatile String shotPagePkg = "";
    private volatile PageParser.Result shotPageResult;
    private final long[] parseSamples = new long[100];
    private final int[] elementSamples = new int[100];
    private int parseSampleIdx;
    private long lastShotAt = 0;
    private long eventCount = 0;
    private long lastEventAtMs = 0;
    private volatile long lastRootAt = 0;
    private volatile String lastRootPkg = "";
    private volatile String lastContentParsePkg = "";
    private volatile long lastContentParseAt = 0;
    private final ScrollTrigger scrollTrigger = new ScrollTrigger();
    private volatile String shotKind = "CONTENT_CHANGE";
    private static final java.util.Set<String> HARD_SKIP = new java.util.HashSet<>(
            java.util.Arrays.asList("com.dsh.v2", "com.android.systemui",
                    "com.android.inputmethod.latin", "com.sohu.inputmethod.sogou",
                    "com.baidu.input", "com.iflytek.inputmethod"));
    private static volatile TraceAccessibilityService instance;

    /** 供 TraceModule 在 trace-worker 上请求"补一张代表帧" */
    public static TraceAccessibilityService instance() { return instance; }

    /**
     * 看门狗自愈：重设 service info（解绑/重绑的本地等价动作），不碰 settings secure。
     * @return true = 已发起；false = 服务实例不存在（只能升级给用户）
     */
    public static boolean requestRebind(String reason) {
        TraceAccessibilityService s = instance;
        if (s == null) {
            android.util.Log.w(TAG, "selfHealAttempt rebind skipped: no service instance reason="
                    + reason);
            return false;
        }
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = s.getServiceInfo();
            if (info != null) s.setServiceInfo(info);
            android.util.Log.i(TAG, "selfHealAttempt rebind reason=" + reason);
            return true;
        } catch (Throwable t) {
            android.util.Log.e(TAG, "selfHealAttempt rebind failed reason=" + reason, t);
            return false;
        }
    }

    /** captureOnce 的结果 */
    public static final class Shot {
        public final String shotRef;
        public final String text;

        Shot(String shotRef, String text) {
            this.shotRef = shotRef;
            this.text = text == null ? "" : text;
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        if (ocr == null) {
            ocr = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        }
        // 关键：a11y 被系统绑定时也要拉起前台服务，否则进程会被冻结、事件断流
        HostService.ensureRunning(this);
        Log.i(TAG, "TraceAccessibilityService connected; hostService=" + HostService.isRunning());
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        long started = SystemClock.uptimeMillis();
        try {
            handleEvent(event);
        } finally {
            long ms = SystemClock.uptimeMillis() - started;
            if (ms > 16) {
                Log.w(TAG, "event slow type=" + (event == null ? -1 : event.getEventType())
                        + " pkg=" + (event == null || event.getPackageName() == null ? ""
                        : event.getPackageName()) + " ms=" + ms);
            }
        }
    }

    private void handleEvent(AccessibilityEvent event) {
        A11yHealth.onEvent();
        eventCount++;
        lastEventAtMs = System.currentTimeMillis();
        if (eventCount == 1 || eventCount % 50 == 0) {
            if (!HostService.isRunning()) {
                Log.e(TAG, "event#" + eventCount + " HostService NOT running; ensure start");
                HostService.ensureRunning(this);
            }
        }
        if (event == null) return;
        try {
            int type = event.getEventType();
            TraceModule m0 = TraceRuntime.get();
            CharSequence ep0 = event.getPackageName();
            String evPkg = ep0 == null ? "" : ep0.toString();
            // 廉价前置过滤：输入法/自身/系统 UI 只当心跳，绝不做树遍历
            if (HARD_SKIP.contains(evPkg)) {
                if (m0 != null) m0.onSkippedHeartbeat(System.currentTimeMillis(), evPkg, currentActivity);
                return;
            }
            if (m0 != null && m0.shouldSkip(evPkg)) {
                m0.onSkippedHeartbeat(System.currentTimeMillis(), evPkg, currentActivity);
                return;
            }
            // 通知/时钟状态事件只当心跳
            if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                if (m0 != null) m0.onSkippedHeartbeat(System.currentTimeMillis(), "", currentActivity);
                return;
            }
            // 文本变化（每次按键）永不做树遍历
            if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                if (m0 != null) m0.onSkippedHeartbeat(System.currentTimeMillis(), evPkg, currentActivity);
                return;
            }
            // 滚动触发：不遍历树，只累计位移；每 0.7 屏高截一帧，停止后补一帧
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                int h = getResources().getDisplayMetrics().heightPixels;
                long now = SystemClock.uptimeMillis();
                int sdy = event.getScrollDeltaY();
                int sdx = event.getScrollDeltaX();
                int sy = event.getScrollY();
                android.util.Log.d(TAG, "scroll event deltaY=" + sdy + " deltaX=" + sdx
                        + " scrollY=" + sy + " pkg=" + evPkg);
                boolean shot = scrollTrigger.onScroll(now, sdx, sdy, sy, h);
                handler.removeCallbacks(scrollIdle);
                handler.postDelayed(scrollIdle, ScrollTrigger.IDLE_MS + 20);
                if (shot) {
                    lastShotAt = now;
                    shotKind = "SCROLL";
                    requestScreenshot();
                }
                return;
            }
            long nowEv = SystemClock.uptimeMillis();
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && nowEv - lastRootAt < 300 && evPkg.equals(lastRootPkg)) {
                if (m0 != null) m0.onSkippedHeartbeat(System.currentTimeMillis(), evPkg, currentActivity);
                return;
            }
            // 内容变化是高频事件（打字/动画/进度条）：同 pkg 300ms 内不重复解析
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                if (evPkg.equals(lastContentParsePkg) && nowEv - lastContentParseAt < 300) {
                    if (m0 != null) m0.onSkippedHeartbeat(System.currentTimeMillis(), evPkg, currentActivity);
                    return;
                }
                lastContentParsePkg = evPkg;
                lastContentParseAt = nowEv;
            }
            lastRootAt = nowEv;
            lastRootPkg = evPkg;
            AccessibilityNodeInfo active = activeRoot();
            lastRootForShot = active;
            String pkg = rootPkg(active);
            if (pkg.isEmpty()) {
                CharSequence pkgCs = event.getPackageName();
                pkg = pkgCs == null ? "" : pkgCs.toString();
            }
            if (pkg.isEmpty()) {
                if (eventCount == 1 || eventCount % 50 == 0) {
                    Log.i(TAG, "event#" + eventCount + " pkg=<empty> dropped");
                }
                return;
            }

            TraceModule module = TraceRuntime.get();
            if (module == null) {
                // 服务桥为空：必须可见，不能静默丢事件
                if (eventCount == 1 || eventCount % 50 == 0) {
                    Log.e(TAG, "event#" + eventCount + " trace module UNAVAILABLE pkg=" + pkg);
                }
                return;
            }

            // task-22：先判 skip，再更新 activity。
            // 键盘/systemui/toast 的 WINDOW_STATE_CHANGED 也会带 IME/系统类名；
            // 如果先写 currentActivity，会把内容 app 的 activity 污染成输入法窗口类，
            // 导致 activity_changed 在 app ↔ IME 之间来回切块。
            if (module.shouldSkip(pkg)) {
                module.onSkippedHeartbeat(System.currentTimeMillis(), pkg, currentActivity);
                if (eventCount == 1 || eventCount % 50 == 0) {
                    Log.i(TAG, "event#" + eventCount + " pkg=" + pkg
                            + " skipped=true lastEventAgoMs=0");
                }
                return;                 // 不动 activity、不抽结构、不触发截图
            }

            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                CharSequence ep = event.getPackageName();
                if (ep != null && ep.toString().equals(pkg)) {
                    CharSequence cls = event.getClassName();
                    if (cls != null && !cls.toString().isEmpty()) currentActivity = cls.toString();
                }
            }

            // 只有窗口切换才做完整树；内容变化用 500/120 的紧凑配额
            boolean full = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            long pageNow = System.currentTimeMillis();
            lastPageT0 = pageNow;
            lastPagePkg = pkg;
            shotPageT0 = pageNow;
            shotPagePkg = pkg;
            // 解析成本 95% 在 getChild()，但真正的杠杆不是节点数而是时机：
            // 实测同一棵树 visited=261，滚动中 422–546ms、静止后 37–49ms（13 倍差）。
            // 原因是滚动时目标 App 主线程忙，每次 getChild 的 binder 往返都在等它。
            // 所以高频的内容变化路径不立即解析，等画面稳定后再解析一次。
            String[] st;
            if (full) {
                st = extractStructure(active, true);
                recordParseStats(lastPageResult, true);
            } else {
                handler.removeCallbacks(parseSettle);
                handler.postDelayed(parseSettle, PARSE_SETTLE_MS);
                st = structureOf(lastPageResult);
            }
            // 截图用的完整 elements 在 requestScreenshot() 里按截图节奏单独全量解析，
            // 不再每个事件都全量走一遍树。
            shotPageResult = lastPageResult;
            PageParser.Result pr = lastPageResult;
            // 滚动容器探针：只记录事实（class / resource-id / 集合形状 / bbox）
            if (pr != null && !pr.scrollables.isEmpty()) {
                Log.d(TAG, "scrollables pkg=" + pkg + " "
                        + PageParser.describeScrollables(pr.scrollables));
            }
            boolean skipped = module.onPageEvent(pageNow, pkg, currentActivity,
                    st[0], st[1], lastShotHash, pr == null ? null : pr.elements);
            if (eventCount == 1 || eventCount % 50 == 0) {
                Log.i(TAG, "event#" + eventCount + " pkg=" + pkg
                        + " cls=" + currentActivity + " skipped=" + skipped
                        + " lastEventAgoMs=0");
            }

            long now = SystemClock.uptimeMillis();
            boolean interaction = type == AccessibilityEvent.TYPE_VIEW_CLICKED
                    || type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            if (interaction) {
                scheduleInteractionShot();
            } else if (now - lastShotAt >= SHOT_MIN_THRESHOLD_MS) {
                lastShotAt = now;
                shotKind = "CONTENT_CHANGE";
                requestScreenshot();
            }
        } catch (Throwable t) {
            Log.w(TAG, "a11y event handling failed", t);
        }
    }

    /** 交互事件：debounce 300ms，最后一个事件后只截一次；最小间隔 500ms */
    private void scheduleInteractionShot() {
        if (pendingInteractionShot != null) handler.removeCallbacks(pendingInteractionShot);
        pendingInteractionShot = this::takeInteractionShot;
        handler.postDelayed(pendingInteractionShot, INTERACTION_DEBOUNCE_MS);
    }

    private final Runnable scrollIdle = () -> {
        int h = getResources().getDisplayMetrics().heightPixels;
        long now = SystemClock.uptimeMillis();
        if (scrollTrigger.onIdle(now, h)) {
            lastShotAt = now;
            shotKind = "SCROLL";
            requestScreenshot();
        }
    };

    private void takeInteractionShot() {
        pendingInteractionShot = null;
        long now = SystemClock.uptimeMillis();
        long elapsed = now - lastShotAt;
        if (elapsed >= SHOT_MIN_INTERACTION_MS) {
            lastShotAt = now;
            shotKind = "INTERACTION";
            TraceModule m = TraceRuntime.get();
            if (m != null) m.noteScreenshot(true);
            requestScreenshot();
            return;
        }
        TraceModule m = TraceRuntime.get();
        if (m != null) m.noteShotThrottled();
        long wait = SHOT_MIN_INTERACTION_MS - elapsed;
        pendingInteractionShot = this::takeInteractionShot;
        handler.postDelayed(pendingInteractionShot, wait);
    }

    /** 活动窗口的根节点：优先 TYPE_APPLICATION 且面积最大的窗口，避开状态栏/时钟/通知。 */
    private AccessibilityNodeInfo activeRoot() {
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            AccessibilityNodeInfo best = null;
            long bestArea = -1;
            if (wins != null) {
                for (AccessibilityWindowInfo w : wins) {
                    if (w == null) continue;
                    AccessibilityNodeInfo r = w.getRoot();
                    if (r == null) continue;
                    if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    android.graphics.Rect b = new android.graphics.Rect();
                    r.getBoundsInScreen(b);
                    long area = (long) Math.max(0, b.width()) * Math.max(0, b.height());
                    if (area > bestArea) { bestArea = area; best = r; }
                }
            }
            if (best != null) return best;
        } catch (Throwable ignored) { }
        return getRootInActiveWindow();
    }

    private static String rootPkg(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null) return "";
        return root.getPackageName().toString();
    }

    /**
     * 结构遍历（固定 BFS 顺序）：
     *  - 标题：第一个 isHeading 文本节点（保留 title 级）
     *  - 稳定文本：前 N=8 个有用文本节点（过滤纯数字/纯标点/长度<2），用 \u0001 拼接
     * **只取文本，不取可点击性**（task-19：可点击文本会随推荐位/计数变化）。
     */
    private String[] extractStructure(AccessibilityNodeInfo root) {
        return extractStructure(root, true);
    }

    private String[] extractStructure(AccessibilityNodeInfo root, boolean full) {
        // 内容变化路径：只取指纹所需的文本量，靠视口裁剪把树走过路降到最低
        PageParser.Result pr = full ? PageParser.parse(root)
                : PageParser.parse(root, 400, 60);
        lastPageResult = pr;
        String title = pr.pageTitle == null ? "" : pr.pageTitle;
        List<String> texts = new ArrayList<>();
        for (PageElement e : pr.elements) {
            if (texts.size() >= TextFingerprint.N) break;
            if (TextFingerprint.useful(e.text)) texts.add(e.text.trim());
        }
        return new String[]{title, TextFingerprint.joined(texts)};
    }

    /** 画面稳定后的解析（主线程，此时目标 App 空闲，getChild 便宜一个数量级）。 */
    private void doSettleParse() {
        AccessibilityNodeInfo root = lastRootForShot;
        if (root == null) return;
        try {
            PageParser.Result pr = PageParser.parse(root, 500, 120);
            lastPageResult = pr;
            shotPageResult = pr;
            recordParseStats(pr, true);
        } catch (Throwable t) {
            Log.w(TAG, "settle parse failed", t);
        }
    }

    /** 从上一次解析结果取结构指纹；还没有结果时返回空串（不阻塞等解析）。 */
    private String[] structureOf(PageParser.Result pr) {
        if (pr == null) return new String[]{"", ""};
        String title = pr.pageTitle == null ? "" : pr.pageTitle;
        List<String> texts = new ArrayList<>();
        for (PageElement e : pr.elements) {
            if (texts.size() >= TextFingerprint.N) break;
            if (TextFingerprint.useful(e.text)) texts.add(e.text.trim());
        }
        return new String[]{title, TextFingerprint.joined(texts)};
    }

    /** 记录解析统计（后台线程与主线程都会调）。 */
    private synchronized void recordParseStats(PageParser.Result pr, boolean log) {
        if (pr == null) return;
        parseSamples[parseSampleIdx % parseSamples.length] = pr.elapsedMs;
        elementSamples[parseSampleIdx % elementSamples.length] = pr.elements.size();
        parseSampleIdx++;
        if (log && pr.elapsedMs > 30) {
            Log.w(TAG, "page parse slow " + pr.elapsedMs + "ms elements="
                    + pr.elements.size() + " visited=" + pr.visited + " "
                    + stageBreakdown(pr.stageNs));
        }
        if (log && parseSampleIdx % 50 == 0) {
            Log.i(TAG, "page_parse " + parseStats());
        }
    }

    /** 分段耗时（毫秒）：text/visible/bbox/role/signals/children/scrollProbe。诊断用。 */
    private static String stageBreakdown(long[] st) {
        if (st == null || st.length < 7) return "";
        String[] names = {"text", "vis", "bbox", "role", "sig", "child", "scroll"};
        long sum = 0;
        StringBuilder sb = new StringBuilder("stages=[");
        for (int i = 0; i < 7; i++) {
            sum += st[i];
            if (i > 0) sb.append(' ');
            sb.append(names[i]).append('=').append(st[i] / 1_000_000L);
        }
        sb.append(" sum=").append(sum / 1_000_000L).append("]");
        return sb.toString();
    }

    private String parseStats() {
        int n = Math.min(parseSampleIdx, parseSamples.length);
        if (n == 0) return "n=0";
        long[] ms = new long[n];
        int[] el = new int[n];
        for (int i = 0; i < n; i++) {
            ms[i] = parseSamples[i];
            el[i] = elementSamples[i];
        }
        java.util.Arrays.sort(ms);
        java.util.Arrays.sort(el);
        return "n=" + n + " parse_p50=" + ms[n / 2] + "ms parse_p95="
                + ms[Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)] + "ms"
                + " elem_p50=" + el[n / 2] + " elem_p95="
                + el[Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)]
                + " elem_max=" + el[n - 1];
    }

    private void requestScreenshot() {
        final String kindForShot = shotKind;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, io, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer hb = result.getHardwareBuffer();
                    if (hb == null) return;
                    try {
                        Bitmap bmp = Bitmap.wrapHardwareBuffer(hb,
                                ColorSpace.get(ColorSpace.Named.SRGB));
                        if (bmp == null) return;
                        Bitmap sw = bmp.copy(Bitmap.Config.ARGB_8888, false);
                        if (sw == null) return;
                        lastShotHash = quickHash(sw);
                        // HSV 切点 + Laplacian 清晰度：都基于 32×32 缩略图，纯逻辑在 :trace
                        double sharpness = 0;
                        try {
                            Bitmap small = Bitmap.createScaledBitmap(sw,
                                    HsvCutDetector.SIZE, HsvCutDetector.SIZE, true);
                            int[] px = new int[HsvCutDetector.SIZE * HsvCutDetector.SIZE];
                            small.getPixels(px, 0, HsvCutDetector.SIZE, 0, 0,
                                    HsvCutDetector.SIZE, HsvCutDetector.SIZE);
                            sharpness = FrameSharpness.laplacianVariance(
                                    px, HsvCutDetector.SIZE, HsvCutDetector.SIZE);
                            TraceModule m = TraceRuntime.get();
                            if (m != null) {
                                cutDetector.setThreshold(m.hsvThreshold());
                                if (cutDetector.isCut(px)) {
                                    m.onVisualCut(System.currentTimeMillis());
                                }
                            }
                            if (small != sw) small.recycle();
                        } catch (Throwable t) {
                            Log.w(TAG, "hsv/sharpness failed", t);
                        }
                        saveAndOcr(sw, sharpness, kindForShot);
                    } catch (Throwable t) {
                        Log.w(TAG, "screenshot bitmap failed", t);
                    } finally {
                        try { hb.close(); } catch (Throwable ignored) { }
                    }
                }

                @Override public void onFailure(int errorCode) {
                    Log.w(TAG, "takeScreenshot failed code=" + errorCode);
                    TraceModule m = TraceRuntime.get();
                    if (m != null) m.onScreenshotFailed(errorCode);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "takeScreenshot unavailable", t);
        }
    }

    /** 8x8 灰度均值哈希（pageId 第四级用；不参与内容判断） */
    private static String quickHash(Bitmap bmp) {
        try {
            Bitmap small = Bitmap.createScaledBitmap(bmp, 8, 8, true);
            long avg = 0;
            int[] px = new int[64];
            small.getPixels(px, 0, 8, 0, 0, 8, 8);
            for (int i = 0; i < 64; i++) {
                int r = (px[i] >> 16) & 0xff, g = (px[i] >> 8) & 0xff, b = px[i] & 0xff;
                px[i] = (r + g + b) / 3;
                avg += px[i];
            }
            avg /= 64;
            long h = 0;
            for (int i = 0; i < 64; i++) h = (h << 1) | (px[i] >= avg ? 1 : 0);
            return Long.toHexString(h);
        } catch (Throwable t) {
            return "";
        }
    }

    private void saveAndOcr(Bitmap bmp, double sharpness) {
        saveAndOcr(bmp, sharpness, "CONTENT_CHANGE");
    }

    private void saveAndOcr(Bitmap bmp, double sharpness, String kind) {
        final String shotRef = saveShotFile(bmp);
        TextRecognizer recognizer = ocr;
        if (recognizer == null || shotRef == null) {
            bmp.recycle();
            return;
        }
        try {
            InputImage image = InputImage.fromBitmap(bmp, 0);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        TraceModule m = TraceRuntime.get();
                        if (m != null) {
                            android.util.Log.d(TAG, "onContent kind=" + kind + " shot=" + shotRef);
                            m.onContent(text.getText(), shotRef, sharpness, shotPagePkg, kind);
                            try {
                                List<PageParser.OcrLine> lines = new ArrayList<>();
                                for (Text.TextBlock b : text.getTextBlocks()) {
                                    for (Text.Line ln : b.getLines()) {
                                        android.graphics.Rect r = ln.getBoundingBox();
                                        if (r != null) lines.add(new PageParser.OcrLine(
                                                ln.getText(), r.left, r.top, r.right, r.bottom));
                                    }
                                }
                                PageParser.Result pr = shotPageResult;
                                m.onOcrLines(shotPageT0, shotPagePkg,
                                        pr == null ? "" : pr.pageTitle, shotRef,
                                        pr == null ? null : pr.elements, lines);
                            } catch (Throwable ignored) { }
                        }
                        bmp.recycle();
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "OCR failed", e);
                        TraceModule m = TraceRuntime.get();
                        if (m != null) m.onOcrFailed();
                        bmp.recycle();
                    });
        } catch (Throwable t) {
            Log.w(TAG, "OCR dispatch failed", t);
            bmp.recycle();
        }
    }

    /** 保存代表帧，返回不透明 shotRef；失败返回 null */
    private String saveShotFile(Bitmap bmp) {
        try {
            File dir = new File(getFilesDir(), "trace/shots");
            if (!dir.exists()) dir.mkdirs();
            File tmpDir = new File(getFilesDir(), "trace/tmp");
            if (!tmpDir.exists()) tmpDir.mkdirs();
            String name = "shot_" + System.currentTimeMillis() + ".jpg";
            File tmp = new File(tmpDir, name);
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, out);
            }
            if (!ShotGc.commitTemp(tmp, f)) {
                Log.w(TAG, "save shot commit failed " + name);
                return null;
            }
            if (ShotGc.lowDisk(dir)) {
                Log.w(TAG, "low disk <2GB: 仅首帧降级告警");
            }
            return "shots/" + name;
        } catch (Throwable t) {
            Log.w(TAG, "save shot failed", t);
            return null;
        }
    }

    /**
     * 同步补一张代表帧 + OCR（供 TraceModule 在 trace-worker 上调用；最多等 timeoutMs）。
     * 用于 shot=null 的痕迹：先延迟 2s 避开 takeScreenshot 的最小间隔，再试一次。
     */
    public Shot captureOnce(long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Bitmap> out = new AtomicReference<>();
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, io, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer hb = result.getHardwareBuffer();
                    if (hb != null) {
                        try {
                            Bitmap bmp = Bitmap.wrapHardwareBuffer(hb,
                                    ColorSpace.get(ColorSpace.Named.SRGB));
                            if (bmp != null) out.set(bmp.copy(Bitmap.Config.ARGB_8888, false));
                        } catch (Throwable t) {
                            Log.w(TAG, "captureOnce bitmap failed", t);
                        } finally {
                            try { hb.close(); } catch (Throwable ignored) { }
                        }
                    }
                    latch.countDown();
                }

                @Override public void onFailure(int errorCode) {
                    Log.w(TAG, "captureOnce screenshot failed code=" + errorCode);
                    TraceModule m = TraceRuntime.get();
                    if (m != null) m.onScreenshotFailed(errorCode);
                    latch.countDown();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "captureOnce takeScreenshot unavailable", t);
            return null;
        }
        try {
            latch.await(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        Bitmap bmp = out.get();
        if (bmp == null) return null;
        String shotRef = saveShotFile(bmp);
        String text = "";
        TextRecognizer recognizer = ocr;
        if (recognizer != null) {
            try {
                Text t = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)),
                        Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
                text = t == null ? "" : t.getText();
            } catch (Throwable ignored) {
                // OCR 失败：shotRef 仍然有效，text 空由调用方计数
            }
        }
        bmp.recycle();
        return shotRef == null ? null : new Shot(shotRef, text);
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        instance = null;
        try { io.shutdownNow(); } catch (Throwable ignored) { }
        try { if (ocr != null) ocr.close(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
