package com.dsh.trace;

import com.dsh.guard.Guard;
import com.dsh.guard.GuardResult;

/**
 * 痕迹的纯逻辑状态机（不 import Android，假时钟可测）。
 *
 * 一条 Scene 的生命周期（规格 v1.0 §2）：
 *  - 开始：无障碍事件表明 pkg / activity / pageId 发生变化，或从无到有
 *  - 关闭：pkg 变 / activity 变 / pageId 变 / 连续 15s 无事件（由 tick 驱动）
 *  - 过滤：dwell < 1000ms 的直接丢弃（不落盘）
 *
 * 同页更新：只有 text/shotRef 会刷新（OCR/截图异步到达）；t0 不变。
 * 15s 超时关闭时 t1 = lastEventAt + 15s（场景在那一刻被判为结束），其余关闭 t1 = 新事件时间。
 */
public final class TraceAccumulator {

    public interface Listener {
        void onTrace(Trace trace);
    }

    /** 诊断用观察者：场景建立/关闭/被跳过事件（不改变行为） */
    public interface Observer {
        void onSceneStart(String pkg, String pageId, long t0);

        void onSceneClose(Trace trace, String reason);

        void onSkippedEvent(String pkg, String activity);
    }

    /**
     * 关闭时 text 为空、但有代表帧时的同步补采钩子（Android 端用 ML Kit，最多等 maxWaitMs）。
     * 纯逻辑层只负责"要不要补、补完是否采用"，不做 OCR。
     */
    public interface TextResolver {
        String resolve(String shotRef, long maxWaitMs);
    }

    public static final long DEFAULT_TIMEOUT_MS = 15_000L;
    public static final long DEFAULT_MIN_DWELL_MS = 1_000L;
    public static final int MAX_TEXT_CHARS = 2_000;
    /** 关闭时补 OCR 的最长等待（task-12 真机验收要求） */
    public static final long BACKFILL_TIMEOUT_MS = 800L;

    private final long timeoutMs;
    private final long minDwellMs;
    private final SkipPolicy skipPolicy;
    private final TextResolver textResolver;
    private final Listener listener;
    private final Observer observer;
    private final Guard guard;

    private Scene current;
    /** 最近一次内容事件的结构信号：HSV 切点后用它开新场景（pageId 仅元数据） */
    private String lastPkg = "";
    private String lastActivity = "";
    private String lastTitle = "";
    private String lastStableText = "";
    private String lastShotHash = "";

    public TraceAccumulator(Listener listener) {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MIN_DWELL_MS, SkipPolicy.none(), null, listener, null);
    }

    public TraceAccumulator(long timeoutMs, long minDwellMs, Listener listener) {
        this(timeoutMs, minDwellMs, SkipPolicy.none(), null, listener, null);
    }

    public TraceAccumulator(long timeoutMs, long minDwellMs, SkipPolicy skipPolicy,
                            TextResolver textResolver, Listener listener) {
        this(timeoutMs, minDwellMs, skipPolicy, textResolver, listener, null);
    }

    public TraceAccumulator(long timeoutMs, long minDwellMs, SkipPolicy skipPolicy,
                            TextResolver textResolver, Listener listener, Observer observer) {
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.minDwellMs = Math.max(0L, minDwellMs);
        this.skipPolicy = skipPolicy == null ? SkipPolicy.none() : skipPolicy;
        this.textResolver = textResolver;
        this.listener = listener;
        this.observer = observer;
        this.guard = new Guard();
    }

    /**
     * 一个无障碍/页面结构信号。text/shotRef 由 OCR 异步补齐，不在这里传。
     * 调用方应放在后台线程（Android 端由 TraceModule 丢线程池），这里不阻塞。
     *
     * 结构性跳过（systemui/launcher/输入法/自己的包）：**不建场景、也不关闭当前场景**。
     * 为什么不在 skip 时 close：systemui 的 content-changed 事件很频繁，若每次都 close，
     * 内容页会被切成大量 <1s 的碎片全部丢弃（task-14 真机 0 行的根因）。
     * 当前场景由「下一个真实内容事件（pkg 变化）」或「15s 超时 tick」正常关闭。
     */
    /**
     * 一个无障碍/页面结构信号。
     *
     * @param pageTitleNode 标题节点文本：**只作为 {@link Trace#pageTitle} 字段**，
     *                      不参与 pageId 身份（task-20：身份必须单一路径）
     * @param stableText    前 N 个有用文本节点用 \u0001 拼接后的字符串
     */
    public void onEvent(long nowMs, String pkg, String activity,
                        String pageTitleNode, String stableText, String shotHash) {
        String p = pkg == null ? "" : pkg;
        if (skipPolicy.shouldSkip(p)) {
            if (observer != null) observer.onSkippedEvent(p, activity);
            return;             // 忽略系统界面事件；当前内容场景等下一个内容事件/超时关闭
        }
        String pageId = SceneResolver.resolve(activity, stableText, shotHash);
        String a = activity == null ? "" : activity;
        String title = pageTitleNode == null ? "" : pageTitleNode;
        String stable = stableText == null ? "" : stableText;
        lastPkg = p;
        lastActivity = a;
        lastTitle = title;
        lastStableText = stable;
        lastShotHash = shotHash == null ? "" : shotHash;

        // 切块只看 pkg（结构性应用切换）+ 画面 HSV；activity/pageId 都只是元数据。
        // IME/弹窗的 WINDOW_STATE_CHANGED 会污染 activity 类名，不能让它切块（task-22）。
        if (current != null) {
            String reason = !current.pkg.equals(p) ? "pkg_changed" : null;
            if (reason != null) close(nowMs, reason);
        }
        if (current == null) {
            current = new Scene(p, a, pageId, nowMs);
            current.pageTitle = title;
            if (observer != null) observer.onSceneStart(p, pageId, nowMs);
        } else {
            current.lastEventAt = nowMs;
            // pageId/activity 只是元数据：同块内变化只更新，不切块
            current.pageId = pageId;
            current.activity = a;
            // title 只是展示字段：有就刷新，没有就保留上一次
            if (!title.isEmpty()) current.pageTitle = title;
        }
    }

    /**
     * 画面切点（HSV 距离超阈值）：关闭当前块，并用最近一次结构信号开新块。
     * 新块的 pageId 只是元数据；切块原因记为 hsv_cut。
     */
    public void onVisualCut(long nowMs) {
        if (current == null) return;
        close(nowMs, "hsv_cut");
        if (lastPkg.isEmpty()) return;
        String pageId = SceneResolver.resolve(lastActivity, lastStableText, lastShotHash);
        current = new Scene(lastPkg, lastActivity, pageId, nowMs);
        current.pageTitle = lastTitle;
        if (observer != null) observer.onSceneStart(lastPkg, pageId, nowMs);
    }

    /**
     * OCR/截图结果到达：刷新当前场景内容，不延长 15s 窗口。
     * 按 Laplacian 清晰度选块内代表帧（shotRef + 对应 text）；越大越清晰。
     */
    public void updateContent(long nowMs, String text, String shotRef) {
        updateContent(nowMs, text, shotRef, 0.0);
    }

    public void updateContent(long nowMs, String text, String shotRef, double sharpness) {
        updateContent(nowMs, text, shotRef, sharpness, "");
    }

    public void updateContent(long nowMs, String text, String shotRef, double sharpness,
                              String pagePkg) {
        updateFrame(nowMs, text, shotRef, sharpness, "CONTENT_CHANGE");
    }

    public void updateFrame(long nowMs, String text, String shotRef, double sharpness,
                            String kind) {
        Scene s = current;
        if (s == null) return;
        String frameText = text == null ? "" : text;
        String h = Integer.toHexString(frameText.hashCode());
        boolean dup = false;
        for (TraceFrame f : s.frames) if (h.equals(f.textHash)) { dup = true; break; }
        if (!dup && s.frames.size() < 20
                && ((frameText != null && !frameText.isEmpty())
                    || (shotRef != null && !shotRef.isEmpty()))) {
            TraceFrame f = new TraceFrame();
            f.seq = s.frames.size();
            f.tMs = nowMs;
            f.shotRef = shotRef;
            f.text = frameText.length() > MAX_TEXT_CHARS ? frameText.substring(0, MAX_TEXT_CHARS) : frameText;
            f.textHash = h;
            f.sharpness = sharpness;
            f.kind = kind == null ? "CONTENT_CHANGE" : kind;
            if (f.seq == 0) f.kind = "FIRST";
            s.frames.add(f);
        }
        boolean hasText = text != null && !text.isEmpty();
        boolean better = s.shotRef == null
                || (hasText && !s.bestHasText)
                || (hasText == s.bestHasText && sharpness >= s.bestSharpness);
        if (!better) return;
        if (text != null) {
            s.text = text.length() > MAX_TEXT_CHARS
                    ? text.substring(0, MAX_TEXT_CHARS) : text;
        }
        if (shotRef != null) s.shotRef = shotRef;
        s.bestSharpness = Math.max(s.bestSharpness, sharpness);
        s.bestHasText = hasText;
    }

    /** 周期检查：连续 timeout 无事件 → 关闭当前场景 */
    public void tick(long nowMs) {
        Scene s = current;
        if (s == null) return;
        long idle = nowMs - s.lastEventAt;
        if (idle >= timeoutMs) {
            long end = s.lastEventAt + timeoutMs;
            close(end > nowMs ? nowMs : end, "timeout");
        }
    }

    /** 卸载/测试用：立刻关闭当前场景（t1 = now） */
    public void flush(long nowMs) {
        close(nowMs, "flush");
    }

    public Scene current() { return current; }

    public long timeoutMs() { return timeoutMs; }

    public long minDwellMs() { return minDwellMs; }

    private void close(long endMs, String reason) {
        Scene s = current;
        current = null;
        if (s == null) return;
        long t1 = Math.max(s.t0, endMs);
        int dwell = (int) (t1 - s.t0);
        if (dwell < minDwellMs) {                 // 噪声：只丢弃，不落盘
            if (observer != null) observer.onSceneClose(null, reason + ":short_dwell");
            return;
        }

        Trace t = new Trace();
        t.t0 = s.t0;
        t.t1 = t1;
        t.dwellMs = dwell;
        t.pkg = s.pagePkg == null || s.pagePkg.isEmpty() ? s.pkg : s.pagePkg;
        t.activity = s.activity;
        t.pageId = s.pageId;
        t.pageTitle = s.pageTitle == null ? "" : s.pageTitle;
        t.text = s.text == null ? "" : s.text;
        t.shotRef = s.shotRef;
        if (!s.frames.isEmpty()) {
            t.frames = new java.util.ArrayList<>(s.frames);
            t.nFrames = t.frames.size();
        }

        // 关闭时 text 为空、但有代表帧：同步补一次 OCR（最多 BACKFILL_TIMEOUT_MS）
        if (t.text.isEmpty() && t.shotRef != null && textResolver != null) {
            try {
                String backfilled = textResolver.resolve(t.shotRef, BACKFILL_TIMEOUT_MS);
                if (backfilled != null && !backfilled.isEmpty()) {
                    t.text = backfilled.length() > MAX_TEXT_CHARS
                            ? backfilled.substring(0, MAX_TEXT_CHARS) : backfilled;
                }
            } catch (Throwable ignored) {
                // 补采失败不影响落盘；计数由 Android 端 resolver 负责
            }
        }

        GuardResult gr = guard.check(t.text);
        t.guard = gr.isBlocked() ? 1 : 0;
        t.guardHit = gr.blockedKind;
        t.maskedText = gr.maskedText;
        if (observer != null) observer.onSceneClose(t, reason);
        if (listener != null) listener.onTrace(t);
    }
}
