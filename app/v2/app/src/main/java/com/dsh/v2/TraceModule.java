package com.dsh.v2;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.database.Cursor;
import android.os.PowerManager;

import com.dsh.core.Capability;
import com.dsh.core.Health;
import com.dsh.core.HostContext;
import com.dsh.core.Log;
import com.dsh.core.Module;
import com.dsh.guard.Guard;
import com.dsh.guard.GuardResult;
import com.dsh.trace.HsvCutDetector;
import com.dsh.trace.RecordingState;
import com.dsh.trace.Scene;
import com.dsh.trace.SkipPolicy;
import com.dsh.trace.Trace;
import com.dsh.trace.TraceAccumulator;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * :trace 的 Android 装配：Accumulator 纯逻辑 + trace-worker 单线程（落盘在后台）。
 *
 * 线程模型（旧工程教训）：Scheduler tick 只在主线程做 submit；所有落盘/OCR 结果处理
 * 都丢到 trace-worker；主线程绝不碰数据库。
 *
 * 可观测性（task-14）：事件入口/场景开始/场景关闭/落盘失败全部有日志与计数；
 * Health 行带 events/scenesOpen/traces/lastEventAgoMs，用于区分"断流"与"逻辑没跑"。
 */
public final class TraceModule implements Module {

    public static final String ID = "trace";

    private final Context app;
    private final Log log;
    private ExecutorService worker;
    private java.util.concurrent.ScheduledExecutorService ticker;
    private TraceStore store;
    private TraceAccumulator accumulator;
    private SkipPolicy skipPolicy = SkipPolicy.none();
    private volatile Health health = Health.stopped();

    private final AtomicLong events = new AtomicLong();
    private final AtomicLong skippedEvents = new AtomicLong();
    private final AtomicLong scenesStarted = new AtomicLong();
    private final AtomicLong tracesSaved = new AtomicLong();
    private final AtomicLong ocrPrimaryOk = new AtomicLong();
    private final AtomicLong ocrEmpty = new AtomicLong();
    private final AtomicLong ocrFailed = new AtomicLong();
    private final AtomicLong ocrBackfillOk = new AtomicLong();
    private final AtomicLong shotFailInterval = new AtomicLong();
    private final AtomicLong shotFailInvalidDisplay = new AtomicLong();
    private final AtomicLong shotFailNoAccess = new AtomicLong();
    private final AtomicLong shotFailOther = new AtomicLong();
    private final AtomicLong evidenceNone = new AtomicLong();
    private final AtomicLong forcedShotOk = new AtomicLong();
    private final AtomicLong forcedShotFail = new AtomicLong();
    private final AtomicLong shotRetryOk = new AtomicLong();
    private final AtomicLong shotRetryFail = new AtomicLong();
    private volatile long lastEventAt = 0;
    private final AtomicLong ticks = new AtomicLong();
    private volatile long lastTickAt = 0;
    private final AtomicLong tickFailures = new AtomicLong();
    private volatile long lastStallLogAt = 0;
    private volatile HostContext ctx;
    private volatile boolean paused;
    private static final String PREFS = "dsh_state";
    private static final String PREF_PAUSED = "records.paused";
    /** 截图触发来源计数（task-21） */
    private final AtomicLong shotInt = new AtomicLong();
    private final AtomicLong shotThr = new AtomicLong();
    /** 切块原因计数（task-21） */
    private final AtomicLong cutByHsv = new AtomicLong();
    private final AtomicLong cutByIdle = new AtomicLong();
    private final AtomicLong cutByPkg = new AtomicLong();

    public TraceModule(Context app, Log log) {
        this.app = app;
        this.log = log;
    }

    @Override public String id() { return ID; }

    @Override public String version() { return "0.1.0"; }

    @Override public List<Capability> requires() {
        return Arrays.asList(Capability.ACCESSIBILITY, Capability.SCREEN_CAPTURE,
                Capability.OCR, Capability.STORAGE);
    }

    @Override public void install(HostContext ctx) {
        this.ctx = ctx;
        store = new TraceStore(app);
        paused = app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(PREF_PAUSED, false);
        try {
            long last = store.lastTraceT1();
            store.closeOpenGaps(last > 0 ? last : System.currentTimeMillis());
        } catch (Throwable ignored) { }
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "trace-worker");
            t.setDaemon(true);
            return t;
        });
        skipPolicy = SkipPackages.build(app);
        TraceRuntime.set(this);

        accumulator = new TraceAccumulator(
                TraceAccumulator.DEFAULT_TIMEOUT_MS,
                TraceAccumulator.DEFAULT_MIN_DWELL_MS,
                skipPolicy,
                (shotRef, maxWaitMs) -> {
                    String text = OcrBackfill.resolve(app, shotRef, maxWaitMs);
                    if (text == null || text.trim().isEmpty()) {
                        ocrEmpty.incrementAndGet();
                        return "";
                    }
                    ocrBackfillOk.incrementAndGet();
                    return text;
                },
                this::persist,
                new TraceAccumulator.Observer() {
                    @Override public void onSceneStart(String pkg, String pageId, long t0) {
                        scenesStarted.incrementAndGet();
                        log.i(ID, "scene start pkg=" + pkg + " pageId=" + pageId);
                    }

                    @Override public void onSceneClose(Trace trace, String reason) {
                        String base = reason == null ? "" : reason.split(":")[0];
                        if ("hsv_cut".equals(base)) cutByHsv.incrementAndGet();
                        else if ("timeout".equals(base)) cutByIdle.incrementAndGet();
                        else if ("pkg_changed".equals(base)) cutByPkg.incrementAndGet();
                        if (trace == null) {
                            log.i(ID, "scene close dwell=<short> reason=" + reason);
                        } else {
                            log.i(ID, "scene close dwell=" + trace.dwellMs + "ms reason=" + reason
                                    + " pageId=" + trace.pageId);
                        }
                    }

                    @Override public void onSkippedEvent(String pkg, String activity) {
                        skippedEvents.incrementAndGet();
                    }
                });

        // 周期 tick：独立调度线程，不依赖主线程/流水线线程池；心跳先打再入 worker
        ticker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trace-ticker");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleAtFixedRate(() -> {
            try {
                lastTickAt = System.currentTimeMillis();
                ExecutorService w = worker;
                TraceAccumulator a = accumulator;
                if (w == null || a == null) return;
                long n = ticks.incrementAndGet();
                if (n % 60 == 0) {
                    log.i(ID, "tick heartbeat events=" + events.get()
                            + " ticks=" + n + " workerBusy=true");
                }
                w.execute(() -> {
                    try {
                        a.tick(System.currentTimeMillis());
                        long now = System.currentTimeMillis();
                        RecordingState state = recordingState();
                        if (state == RecordingState.STALLED
                                && now - lastStallLogAt > 60_000L) {
                            lastStallLogAt = now;
                            log.e(ID, "event stream STALLED lastEventAgoMs="
                                    + lastEventAgoMs()
                                    + " hostService=running a11y=system-enabled screenOn=true"
                                    + "（MIUI bound-but-deaf；需要用户重新开关无障碍）", null);
                        }
                        if (n % 60 == 0) {
                            log.i(ID, "tick events=" + events.get()
                                    + " skipped=" + skippedEvents.get()
                                    + " sceneOpen=" + (a.current() != null)
                                    + " traces=" + tracesSaved.get()
                                    + " recording=" + state.label()
                                    + " stalled=" + (state == RecordingState.STALLED)
                                    + " lastEventAgoMs=" + lastEventAgoMs());
                            TraceStore qs = store;
                            if (qs != null) log.i(ID, qualityLine());
                        }
                    } catch (Throwable t) {
                        tickFailures.incrementAndGet();
                        log.e(ID, "tick worker failed", t);
                    }
                });
            } catch (Throwable t) {
                // 周期任务一旦外抛异常会被 ScheduledExecutorService 永久取消
                tickFailures.incrementAndGet();
                log.e(ID, "tick failed (scheduler task kept alive)", t);
            }
        }, 1_000L, 1_000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 独立看门狗：心跳连续 3 个周期不更新就报 ERROR，绝不外抛
        ticker.scheduleWithFixedDelay(() -> {
            try {
                long last = lastTickAt;
                if (last > 0 && System.currentTimeMillis() - last > 180_000L) {
                    log.e(ID, "tick watchdog: no heartbeat for "
                            + ((System.currentTimeMillis() - last) / 1000)
                            + "s failures=" + tickFailures.get(), null);
                }
            } catch (Throwable t) {
                log.e(ID, "tick watchdog failed", t);
            }
        }, 180_000L, 180_000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        ctx.effect(() -> {
            TraceAccumulator a = accumulator;
            if (a != null) a.flush(System.currentTimeMillis());
            ExecutorService w = worker;
            if (w != null) w.shutdownNow();
            java.util.concurrent.ScheduledExecutorService tk = ticker;
            if (tk != null) tk.shutdownNow();
            TraceStore s = store;
            if (s != null) s.close();
            TraceRuntime.clear();
        });

        health = Health.ok("trace recording");
        log.i(ID, "installed; db=" + store.dbFile().getAbsolutePath()
                + " timeoutMs=" + accumulator.timeoutMs()
                + " skipped=" + skipPolicy.packages());
    }

    /** OCR 结果合并到同一帧；元素、shotRef、OCR 三者原子落库。 */
    public void onOcrLines(long pageT0, String pkg, String pageTitle, String shotRef,
                           java.util.List<PageElement> base,
                           java.util.List<PageParser.OcrLine> lines) {
        if (pageT0 <= 0) return;
        ExecutorService w = worker;
        TraceStore s = store;
        if (w == null || s == null) return;
        w.execute(() -> {
            try {
                java.util.List<PageElement> merged = PageParser.mergeOcr(
                        base == null ? java.util.Collections.emptyList() : base,
                        lines == null ? java.util.Collections.emptyList() : lines, pageTitle);
                s.replacePageElements(pageT0, pkg, shotRef, merged);
            } catch (Throwable ignored) { }
        });
    }

    /**
     * 无障碍事件（调用方在主线程）：只计数/日志 + submit，不做任何 IO。
     * @return true = 该包名在结构性跳过列表里（服务端日志用）
     */
    public boolean onPageEvent(long nowMs, String pkg, String activity,
                               String titleNodeText, String firstClickable, String shotHash) {
        return onPageEvent(nowMs, pkg, activity, titleNodeText, firstClickable, shotHash, null);
    }

    /** 带结构化页面元素的重载；元素在 trace-worker 落库，不阻塞 a11y 主线程。 */
    public boolean onPageEvent(long nowMs, String pkg, String activity,
                               String titleNodeText, String firstClickable, String shotHash,
                               java.util.List<PageElement> elements) {
        final java.util.List<PageElement> elems = elements;
        final String epkg = pkg;
        ExecutorService ew = worker;
        TraceStore es = store;
        if (ew != null && es != null && elems != null && !elems.isEmpty()) {
            ew.execute(() -> {
                try { es.replacePageElements(nowMs, epkg, elems); } catch (Throwable ignored) { }
            });
        }
        long n = events.incrementAndGet();
        lastEventAt = nowMs;
        boolean skipped = skipPolicy.shouldSkip(pkg);
        if (skipped) skippedEvents.incrementAndGet();
        ExecutorService w = worker;
        TraceAccumulator a = accumulator;
        if (w == null || a == null) {
            if (n == 1 || n % 50 == 0) {
                log.e(ID, "event dropped: module not ready event#" + n + " pkg=" + pkg, null);
            }
            return skipped;
        }
        if (n == 1 || n % 50 == 0) {
            log.i(ID, "event#" + n + " pkg=" + pkg + " act=" + activity
                    + " skipped=" + skipped + " sceneOpen=" + (a.current() != null));
        }
        w.execute(() -> a.onEvent(nowMs, pkg, activity, titleNodeText, firstClickable, shotHash));
        return skipped;
    }

    /** 服务先判 skip：键盘/systemui/toast 不能污染 currentActivity（task-22） */
    public boolean shouldSkip(String pkg) {
        return skipPolicy.shouldSkip(pkg);
    }

    /**
     * skipped 事件的心跳：只计数 + 刷新 lastEventAt（断流判据需要），
     * 不抽结构、不碰 activity、不触发截图。
     */
    public void onSkippedHeartbeat(long nowMs, String pkg, String activity) {
        long n = events.incrementAndGet();
        skippedEvents.incrementAndGet();
        lastEventAt = nowMs;
        if (n == 1 || n % 50 == 0) {
            log.i(ID, "event#" + n + " pkg=" + pkg + " act=" + activity
                    + " skipped=true sceneOpen="
                    + (accumulator != null && accumulator.current() != null));
        }
    }

    /** OCR 结果（可能来自 ML Kit 回调线程）：计数 + submit；sharpness 用于块内选最清晰帧 */
    public void onContent(String text, String shotRef) {
        onContent(text, shotRef, 0.0);
    }

    public void onContent(String text, String shotRef, double sharpness) {
        onContent(text, shotRef, sharpness, "");
    }

    public void onContent(String text, String shotRef, double sharpness, String pagePkg) {
        if (text == null || text.trim().isEmpty()) ocrEmpty.incrementAndGet();
        else ocrPrimaryOk.incrementAndGet();
        ExecutorService w = worker;
        TraceAccumulator a = accumulator;
        if (w == null || a == null) return;
        w.execute(() -> a.updateContent(System.currentTimeMillis(), text, shotRef, sharpness, pagePkg));
    }

    /** OCR 任务失败（ML Kit 报错） */
    public void onOcrFailed() { ocrFailed.incrementAndGet(); }

    /** takeScreenshot 失败：按 errorCode 分类计数，写进 health/statusLine，不静默 */
    public void onScreenshotFailed(int errorCode) {
        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
            shotFailInterval.incrementAndGet();
        } else if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY) {
            shotFailInvalidDisplay.incrementAndGet();
        } else if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
            shotFailNoAccess.incrementAndGet();
        } else {
            shotFailOther.incrementAndGet();
        }
        log.w(ID, "screenshot failed code=" + errorCode
                + " (interval=" + shotFailInterval.get()
                + " invalidDisplay=" + shotFailInvalidDisplay.get()
                + " noAccess=" + shotFailNoAccess.get()
                + " other=" + shotFailOther.get() + ")");
    }

    /** 场景关闭后的落盘（在 trace-worker 上执行，异常必须带类名打出来） */
    private void persist(Trace trace) {
        if (paused) {
            log.i(ID, "trace skipped: recording paused pkg=" + trace.pkg
                    + " t0=" + trace.t0);
            return;
        }
        boolean hasText = trace.text != null && !trace.text.trim().isEmpty();
        boolean hasShot = trace.shotRef != null && !trace.shotRef.isEmpty();
        // 只在「即将变成 NONE」时强制补一帧；不无脑每块截图。
        if (!hasText && !hasShot) {
            TraceAccessibilityService svc = TraceAccessibilityService.instance();
            if (svc != null) {
                TraceAccessibilityService.Shot shot = svc.captureOnce(3_000L);
                if (shot != null) {
                    forcedShotOk.incrementAndGet();
                    trace.shotRef = shot.shotRef;
                    if (shot.text != null && !shot.text.trim().isEmpty()) {
                        trace.text = shot.text.length() > TraceAccumulator.MAX_TEXT_CHARS
                                ? shot.text.substring(0, TraceAccumulator.MAX_TEXT_CHARS)
                                : shot.text;
                        GuardResult gr = new Guard().check(trace.text);
                        trace.guard = gr.isBlocked() ? 1 : 0;
                        trace.guardHit = gr.blockedKind;
                        trace.maskedText = gr.maskedText;
                    }
                    hasText = trace.text != null && !trace.text.trim().isEmpty();
                    hasShot = true;
                } else {
                    forcedShotFail.incrementAndGet();
                }
            } else {
                forcedShotFail.incrementAndGet();
            }
        }
        // shot=null：代表帧没拿到（常见 takeScreenshot INTERVAL_TIME_SHORT）。
        // 延后 2s（避开最小间隔）再补一次截图+OCR，然后才落盘；代价是这条痕迹延迟。
        if (trace.shotRef == null) {
            log.i(ID, "trace missing shot, retry in 2000ms pkg=" + trace.pkg);
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            TraceAccessibilityService svc = TraceAccessibilityService.instance();
            if (svc == null) {
                shotRetryFail.incrementAndGet();
                log.w(ID, "shot retry skipped: a11y service unavailable");
            } else {
                TraceAccessibilityService.Shot shot = svc.captureOnce(3_000L);
                if (shot == null) {
                    shotRetryFail.incrementAndGet();
                    log.w(ID, "shot retry failed");
                } else {
                    shotRetryOk.incrementAndGet();
                    trace.shotRef = shot.shotRef;
                    if ((trace.text == null || trace.text.isEmpty())
                            && !shot.text.trim().isEmpty()) {
                        trace.text = shot.text.length() > TraceAccumulator.MAX_TEXT_CHARS
                                ? shot.text.substring(0, TraceAccumulator.MAX_TEXT_CHARS)
                                : shot.text;
                        // 补出来的文本可能含死线内容，必须重新过 guard
                        GuardResult gr = new Guard().check(trace.text);
                        trace.guard = gr.isBlocked() ? 1 : 0;
                        trace.guardHit = gr.blockedKind;
                        trace.maskedText = gr.maskedText;
                    }
                    log.i(ID, "shot retry ok ref=" + shot.shotRef
                            + " textLen=" + (shot.text == null ? 0 : shot.text.length()));
                }
            }
        }
        hasText = trace.text != null && !trace.text.trim().isEmpty();
        hasShot = trace.shotRef != null && !trace.shotRef.isEmpty();
        trace.evidence = hasText && hasShot ? "BOTH" : hasText ? "TEXT"
                : hasShot ? "SHOT" : "NONE";
        if ("NONE".equals(trace.evidence)) evidenceNone.incrementAndGet();
        try {
            store.insert(trace);
            long n = tracesSaved.incrementAndGet();
            log.i(ID, "trace saved t0=" + trace.t0 + " dwell=" + trace.dwellMs
                    + "ms pkg=" + trace.pkg + " pageId=" + trace.pageId
                    + " textLen=" + (trace.text == null ? 0 : trace.text.length())
                    + " shot=" + trace.shotRef + " guard=" + trace.guard
                    + " traces=" + n
                    + " ocr_empty=" + ocrEmpty.get()
                    + " ocr_failed=" + ocrFailed.get());
        } catch (Throwable e) {
            log.e(ID, "persist failed (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /** 启动后台回填：有图无文的旧块补 OCR（67 条级别）。 */
    public static void backfillEmptyText(Context app) {
        try {
            TraceStore store = new TraceStore(app);
            java.util.List<long[]> ids = store.tracesWithEmptyText();
            int ok = 0, fail = 0;
            for (long[] row : ids) {
                String shot;
                try (Cursor c = store.getReadableDatabase().rawQuery(
                        "SELECT shot_ref FROM " + TraceStore.TABLE + " WHERE id=?",
                        new String[]{String.valueOf(row[0])})) {
                    if (!c.moveToFirst()) continue;
                    shot = c.getString(0);
                }
                String text = OcrBackfill.resolve(app, shot, 5_000L);
                if (text == null || text.trim().isEmpty()) { fail++; continue; }
                if (text.length() > TraceAccumulator.MAX_TEXT_CHARS) {
                    text = text.substring(0, TraceAccumulator.MAX_TEXT_CHARS);
                }
                com.dsh.guard.GuardResult gr = new com.dsh.guard.Guard().check(text);
                store.updateText(row[0], text, gr.isBlocked() ? 1 : 0, gr.blockedKind,
                        gr.maskedText, "TEXT");
                ok++;
            }
            android.util.Log.i("DSHL2", "ocr backfill empty: total=" + ids.size()
                    + " ok=" + ok + " fail=" + fail);
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "ocr backfill empty failed", t);
        }
    }

    public long todayCount() {
        TraceStore s = store;
        if (s == null) return 0;
        try { return s.countToday(); } catch (Throwable t) { return 0; }
    }

    public TraceStore store() { return store; }

    public boolean isPaused() { return paused; }

    /** 记录开关：暂停时不落 traces，但无障碍服务继续跑；开一条 recording_off 缺口。 */
    public void setPaused(boolean p) {
        if (paused == p) return;
        long now = System.currentTimeMillis();
        TraceStore s = store;
        if (p) {
            paused = true;
            if (s != null) {
                try { s.openGap(now, "recording_off"); } catch (Throwable ignored) { }
            }
            TraceAccumulator a = accumulator;
            if (a != null) a.flush(now);
        } else {
            paused = false;
            if (s != null) {
                try { s.closeOpenGaps(now); } catch (Throwable ignored) { }
            }
        }
        app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_PAUSED, p).apply();
        log.i(ID, "records.paused=" + p + " at=" + now);
    }

    /** 「记住这一帧」：当前帧截图 + 当前 scene 文本 → manual 块（present=true）。 */
    public boolean rememberCurrentFrame() {
        long ts = System.currentTimeMillis();
        TraceAccessibilityService svc = TraceAccessibilityService.instance();
        if (svc == null) {
            log.e(ID, "remember failed: a11y service unavailable", null);
            return false;
        }
        TraceAccessibilityService.Shot shot = svc.captureOnce(3_000L);
        if (shot == null || shot.shotRef == null || shot.shotRef.isEmpty()) {
            log.e(ID, "remember failed: screenshot unavailable", null);
            return false;
        }
        Scene scene = accumulator == null ? null : accumulator.current();
        String text = scene != null && scene.text != null ? scene.text : shot.text;
        if (text == null) text = "";
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("pkg", scene == null ? "" : scene.pkg);
        d.put("activity", scene == null ? "" : scene.activity);
        d.put("page_id", scene == null ? "" : scene.pageId);
        d.put("page_title", scene == null ? "" : scene.pageTitle);
        d.put("shot_ref", shot.shotRef);
        d.put("manual", true);
        d.put("manual_at", ts);
        AndroidMemoryStore ms = new AndroidMemoryStore(app);
        ms.registerNamespace("manual", true);
        com.dsh.memory.MemItem item = new com.dsh.memory.MemItem("manual",
                com.dsh.memory.Kind.BLOCK, "manual:" + ts, text,
                com.dsh.memory.MemoryCodec.toJson(d));
        item.t0 = ts;
        item.t1 = ts;
        item.weight = 2.0;
        item.evidence.add(new com.dsh.memory.Evidence("manual", "tile", ts));
        long id = ms.put(item);
        log.i(ID, "remember saved id=" + id + " textLen=" + text.length()
                + " shot=" + shot.shotRef + " present=true");
        return true;
    }

    /** 事件流是否已断（系统启用 + 亮屏 + HostService 在跑 + >60s 无事件）。状态页/通知用。 */
    /**
     * 唯一判据（通知/状态页/Health 共用）：以实际事件为准，系统声明只在「从未收到事件」时用。
     */
    public RecordingState recordingState() {
        long last = lastEventAt;
        long ago = last <= 0 ? -1 : Math.max(0, System.currentTimeMillis() - last);
        return RecordingState.decide(ago, A11yHealth.isEnabled(app),
                screenInteractive(), HostService.isRunning());
    }

    /** 最近 60s 内是否收到过 a11y 事件（UsageStats 降级采样用它决定 standby/sampling） */
    public boolean hasRecentA11yEvent() {
        return recordingState() == RecordingState.ACTIVE;
    }

    /** 兼容旧调用：等价于 recordingState()==STALLED */
    public boolean isEventStreamStalled() {
        return recordingState() == RecordingState.STALLED;
    }

    /** 交互触发截图成功计数（task-21）；shot_thr 只统计被 500ms 间隔节流的交互 */
    public void noteScreenshot(boolean interactive) {
        if (interactive) shotInt.incrementAndGet();
    }

    /** 交互事件因 500ms 最小间隔被节流（task-21 统计） */
    public void noteShotThrottled() { shotThr.incrementAndGet(); }

    /** 画面 HSV 切点：关闭当前块并用最近结构信号开新块（在工作线程执行） */
    public void onVisualCut(long nowMs) {
        TraceAccumulator a = accumulator;
        ExecutorService w = worker;
        if (a == null || w == null) return;
        w.execute(() -> a.onVisualCut(nowMs));
    }

    /** 配置：trace.cut.hsvThreshold，默认 0.35（真机校准用） */
    public double hsvThreshold() {
        HostContext c = ctx;
        if (c != null) {
            try {
                String s = c.config().getString("trace.cut.hsvThreshold", "0.35");
                return Double.parseDouble(s.trim());
            } catch (Throwable ignored) { }
        }
        return HsvCutDetector.DEFAULT_THRESHOLD;
    }

    /** quality 行：DB 统计 + 运行时切点/截图计数（task-21 验收用） */
    public String qualityLine() {
        TraceStore qs = store;
        long[] q = qs == null ? new long[]{0, 0, 0, 0, 0} : qs.qualityStats();
        return "quality: blocks=" + q[0]
                + " short_le5s=" + q[2]
                + " noText=" + q[3]
                + " noShot=" + q[4]
                + " cut_by_hsv=" + cutByHsv.get()
                + " cut_by_idle=" + cutByIdle.get()
                + " cut_by_pkg=" + cutByPkg.get()
                + " shot_int=" + shotInt.get()
                + " shot_thr=" + shotThr.get();
    }

    private boolean screenInteractive() {
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 状态页/日志：事件、场景、落盘、OCR 计数不能静默 */
    public String statusLine() {
        TraceAccumulator a = accumulator;
        boolean open = a != null && a.current() != null;
        return "events=" + events.get()
                + " skipped=" + skippedEvents.get()
                + " scenesOpen=" + (open ? 1 : 0)
                + " scenes=" + scenesStarted.get()
                + " traces=" + tracesSaved.get()
                + " recording=" + recordingState().label()
                + " stalled=" + isEventStreamStalled()
                + " lastEventAgoMs=" + lastEventAgoMs()
                + " cut_hsv=" + cutByHsv.get()
                + " shot_int=" + shotInt.get()
                + " ocr_empty=" + ocrEmpty.get()
                + " ocr_failed=" + ocrFailed.get()
                + " ocr_backfill_ok=" + ocrBackfillOk.get()
                + " ocr_primary_ok=" + ocrPrimaryOk.get()
                + " evidence_none=" + evidenceNone.get()
                + " forced_shot_ok=" + forcedShotOk.get()
                + " forced_shot_fail=" + forcedShotFail.get()
                + " shot_fail_interval=" + shotFailInterval.get()
                + " shot_fail_invalid_display=" + shotFailInvalidDisplay.get()
                + " shot_fail_no_access=" + shotFailNoAccess.get()
                + " shot_fail_other=" + shotFailOther.get()
                + " shot_retry_ok=" + shotRetryOk.get()
                + " shot_retry_fail=" + shotRetryFail.get()
                + " tracesToday=" + todayCount();
    }

    private long lastEventAgoMs() {
        long last = lastEventAt;
        return last <= 0 ? -1 : Math.max(0, System.currentTimeMillis() - last);
    }

    @Override public Health health() {
        RecordingState s = recordingState();
        if (s == RecordingState.ACTIVE) {
            return Health.ok("recording=ACTIVE " + statusLine());
        }
        return Health.degraded("recording=" + s.label()
                + " lastEventAgoMs=" + lastEventAgoMs() + " " + statusLine());
    }
}
