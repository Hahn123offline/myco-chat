package com.dsh.usagetrace;

import android.content.Context;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.Module;
import com.dsh.host.api.data.Episode;
import com.dsh.host.api.events.EpisodeClosed;
import com.dsh.host.api.trace.EpisodeStats;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用痕迹模块（眼睛）。
 *
 * 第一层：数据层 + 策略引擎 + 资源闸门（已完成）
 * 第二层：场景解析 + 段落状态机（已完成，见 SceneResolver / EpisodeTracker）
 * 第三层：采集实现（AccessibilityService / 截图 / OCR）——进行中
 *
 * 健康：不能只看"系统设置里开了没有"。用 {@link A11yHealthMonitor} 的两个判据
 * （系统启用 + 最近真的收到事件）如实上报；没启用/没数据时 Host 与状态页都会告警。
 */
public final class UsageTraceModule implements Module {

    /** 无障碍服务是系统绑定的，需要一条到模块的桥。install 时设置，卸载时清空。 */
    static volatile UsageTraceModule INSTANCE;

    /**
     * 最近一次 onAccessibilityEvent 的墙钟时间（0 = 本进程从未收到）。
     * 由 {@link TraceAccessibilityService} 在每次事件入口更新。
     * 这是比"系统说启用了"更真实的判据：服务可能被杀/没绑上。
     */
    static final AtomicLong LAST_EVENT_AT = new AtomicLong(0);

    private final Context app;
    private volatile Health health = Health.stopped();

    private EpisodeStore store;
    private PolicyTable policies;
    private ResourceGovernor governor;
    private EpisodeTracker tracker;
    private HostContext ctx;
    private A11yHealthMonitor a11yMonitor;
    private A11yReminderPolicy a11yReminder;

    public UsageTraceModule(Context app) { this.app = app; }

    @Override public String id() { return "usagetrace"; }
    @Override public String version() { return "1.1.0-dev"; }

    @Override
    public List<Capability> requires() {
        return Arrays.asList(Capability.ACCESSIBILITY, Capability.SCREEN_CAPTURE, Capability.OCR);
    }

    @Override
    public void install(HostContext ctx) {
        this.ctx = ctx;
        store = new EpisodeStore(app);
        policies = PolicyTable.fromConfig(ctx.config(), CapturePolicy.def("*"));
        governor = ResourceGovernor.defaults();
        tracker = new EpisodeTracker(new EpisodeTracker.Sink() {
            @Override public void onOpen(Episode e) {
                store.insertOpen(e);
                ctx.log().i(id(), "open " + e.pkg + " sceneConf=" + e.sceneConf);
            }
            @Override public void onClose(Episode e) {
                store.closeEpisode(e);
            }
            @Override public void onEvent(EpisodeClosed ev) {
                ctx.bus().publish(ev);
                ctx.log().i(id(), "close " + ev.episode.pkg
                        + " dwell=" + ev.episode.dwellMs + "ms title=" + ev.episode.title);
            }
        }, 800);

        // 段落统计（条数对账用）：跨模块只读接口，不暴露 DB 路径
        ctx.provide(EpisodeStats.class, store);

        // 安静 15s 自动收尾
        ctx.schedule("idle", 5_000, () -> tracker.idleCheck(System.currentTimeMillis(), 15_000));

        // 无障碍健康：系统启用 + 最近是否真收到事件；周期检查 + 受限提醒
        a11yMonitor = new A11yHealthMonitor(
                new AndroidA11yEnabledChecker(app),
                new SystemA11yTimeSource(),
                () -> LAST_EVENT_AT.get(),
                ctx::config);
        a11yReminder = new A11yReminderPolicy(new SystemA11yTimeSource(), ctx::config);
        long checkPeriod = Math.max(15_000L, ctx.config().getLong("a11y.checkPeriodMs", 60_000L));
        ctx.schedule("a11y-check", checkPeriod, this::checkA11y);
        // 立即检查一次：重启后被系统关掉无障碍时尽快提醒（受开机静默期/节流约束）
        checkA11y();

        ctx.effect(() -> {
            try { A11yNotifier.cancel(app); } catch (Throwable ignored) { }
            if (store != null) store.close();
            INSTANCE = null;
        });

        INSTANCE = this;
        ctx.log().i(id(), "installed; policies=" + policies.size() + " today=" + store.countToday()
                + " a11y=" + a11ySnapshot().state.tag);
        health = Health.degraded("等待无障碍事件");
    }

    /**
     * 无障碍服务喂进来的画面样本。所有决策在这里做，服务只负责"看"。
     */
    public void onScreenSample(String pkg, String activity,
                               String nodeTitle, String ocrTitle, String frameHash,
                               String framePath, String titlebarPath,
                               String textSnippet, long now) {
        if (tracker == null || policies == null) return;
        CapturePolicy p = policies.forPkg(pkg);
        if (p.mode == CapturePolicy.Mode.OFF) return;

        Scene scene = SceneResolver.resolve(pkg, activity, nodeTitle, ocrTitle, frameHash);
        tracker.onSample(scene, now, framePath, titlebarPath, textSnippet, p.mode.name().toLowerCase());
    }

    /** 无障碍服务连上/断开时记一笔；真实健康以事件与系统状态为准 */
    public void onServiceConnected(boolean connected) {
        if (ctx != null) {
            ctx.log().i(id(), "accessibility service " + (connected ? "connected" : "disconnected"));
        }
        checkA11y();
    }

    /** 当前无障碍真实状态（给 HostService/MainActivity 用） */
    public A11yHealthMonitor.Result a11ySnapshot() {
        A11yHealthMonitor m = a11yMonitor;
        if (m == null) return new A11yHealthMonitor.Result(A11yState.DISABLED, -1);
        try {
            return m.evaluate();
        } catch (Throwable t) {
            return new A11yHealthMonitor.Result(A11yState.DISABLED, -1);
        }
    }

    public A11yState a11yState() { return a11ySnapshot().state; }

    public long episodesToday() {
        EpisodeStore s = store;
        if (s == null) return -1;
        try {
            return s.countToday();
        } catch (Throwable t) {
            return -1;
        }
    }

    public EpisodeStore store() { return store; }
    public PolicyTable policies() { return policies; }
    public ResourceGovernor governor() { return governor; }

    /** 截图与标题条落盘目录（由宿主统一给出的 files 目录派生） */
    public java.io.File framesDir() {
        java.io.File base = app.getExternalFilesDir(null);
        if (base == null) base = app.getFilesDir();
        java.io.File d = new java.io.File(base, "trace/frames");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ---------------- 健康 / 提醒 ----------------

    @Override
    public Health health() {
        A11yHealthMonitor m = a11yMonitor;
        EpisodeStore s = store;
        if (m == null || s == null) return health;
        try {
            A11yHealthMonitor.Result r = m.evaluate();
            long episodesToday = -1;
            try { episodesToday = s.countToday(); } catch (Throwable ignored) { }
            String detail = A11yStatusText.healthDetail(r.state, r.lastEventAgeMs, episodesToday);
            switch (r.state) {
                case ACTIVE: return Health.ok(detail);
                case ENABLED_BUT_SILENT: return Health.degraded(detail);
                default: return Health.failed(detail);
            }
        } catch (Throwable t) {
            return Health.failed("a11y health threw: " + t.getClass().getSimpleName());
        }
    }

    /** 周期检查：DISABLED 时按节流提醒用户；其它状态清掉提醒 */
    private void checkA11y() {
        A11yHealthMonitor m = a11yMonitor;
        A11yReminderPolicy p = a11yReminder;
        if (m == null || p == null) return;
        A11yHealthMonitor.Result r = m.evaluate();
        if (r.state == A11yState.DISABLED) {
            if (p.shouldRemind(r.state)) {
                A11yNotifier.remind(app);
                p.noteReminded();
                if (ctx != null) {
                    ctx.log().w(id(), "a11y DISABLED; reminder posted (lastEventAgeMs="
                            + r.lastEventAgeMs + ")");
                }
            }
        } else {
            A11yNotifier.cancel(app);
        }
    }
}
