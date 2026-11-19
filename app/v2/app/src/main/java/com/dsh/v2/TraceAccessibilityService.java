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
    private volatile PageParser.Result lastPageResult;
    private volatile long shotPageT0;
    private volatile String shotPagePkg = "";
    private volatile PageParser.Result shotPageResult;
    private final long[] parseSamples = new long[100];
    private final int[] elementSamples = new int[100];
    private int parseSampleIdx;
    private long lastShotAt = 0;
    private long eventCount = 0;
    private long lastEventAtMs = 0;
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
            CharSequence pkgCs = event.getPackageName();
            String pkg = pkgCs == null ? "" : pkgCs.toString();
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
                CharSequence cls = event.getClassName();
                if (cls != null && !cls.toString().isEmpty()) currentActivity = cls.toString();
            }

            String[] st = extractStructure();
            long pageNow = System.currentTimeMillis();
            lastPageT0 = pageNow;
            lastPagePkg = pkg;
            shotPageT0 = pageNow;
            shotPagePkg = pkg;
            shotPageResult = lastPageResult;
            PageParser.Result pr = lastPageResult;
            if (pr != null) {
                parseSamples[parseSampleIdx % parseSamples.length] = pr.elapsedMs;
                elementSamples[parseSampleIdx % elementSamples.length] = pr.elements.size();
                parseSampleIdx++;
                if (pr.elapsedMs > 30) {
                    Log.w(TAG, "page parse slow " + pr.elapsedMs + "ms elements="
                            + pr.elements.size());
                }
                if (parseSampleIdx % 50 == 0) {
                    Log.i(TAG, "page_parse " + parseStats());
                }
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

    private void takeInteractionShot() {
        pendingInteractionShot = null;
        long now = SystemClock.uptimeMillis();
        long elapsed = now - lastShotAt;
        if (elapsed >= SHOT_MIN_INTERACTION_MS) {
            lastShotAt = now;
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

    /**
     * 结构遍历（固定 BFS 顺序）：
     *  - 标题：第一个 isHeading 文本节点（保留 title 级）
     *  - 稳定文本：前 N=8 个有用文本节点（过滤纯数字/纯标点/长度<2），用 \u0001 拼接
     * **只取文本，不取可点击性**（task-19：可点击文本会随推荐位/计数变化）。
     */
    private String[] extractStructure() {
        PageParser.Result pr = PageParser.parse(getRootInActiveWindow());
        lastPageResult = pr;
        String title = pr.pageTitle == null ? "" : pr.pageTitle;
        List<String> texts = new ArrayList<>();
        for (PageElement e : pr.elements) {
            if (texts.size() >= TextFingerprint.N) break;
            if (TextFingerprint.useful(e.text)) texts.add(e.text.trim());
        }
        return new String[]{title, TextFingerprint.joined(texts)};
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
                        saveAndOcr(sw, sharpness);
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
                            m.onContent(text.getText(), shotRef, sharpness, shotPagePkg);
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
            String name = "shot_" + System.currentTimeMillis() + ".jpg";
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, out);
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
