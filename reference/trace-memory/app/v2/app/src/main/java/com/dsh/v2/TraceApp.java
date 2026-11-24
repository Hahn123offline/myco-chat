package com.dsh.v2;

import android.app.Application;
import android.content.Intent;

import com.dsh.core.ConfigStore;
import com.dsh.core.Host;
import com.dsh.core.SimpleEventBus;
import com.dsh.core.SimpleLedger;
import com.dsh.core.SimpleLog;
import com.dsh.core.SimpleStore;
import com.dsh.egress.EgressGate;
import com.dsh.guard.Guard;
import com.dsh.models.ModelsModule;
import com.dsh.models.UrlConnectionTransport;

import java.io.File;

/** v2 应用入口：装配 :core Host + :trace 模块。Android 壳本身不写业务逻辑。 */
public final class TraceApp extends Application {

    private static TraceApp instance;

    private Host host;
    private TraceModule traceModule;
    private UsageModule usageModule;
    private ModelsModule modelsModule;
    private SecretStore secrets;
    private ConfigStore configs;
    private java.util.concurrent.ScheduledExecutorService maintenance;

    public static TraceApp get(android.content.Context c) {
        return (TraceApp) c.getApplicationContext();
    }

    public static TraceApp instance() { return instance; }

    /**
     * 包一层投影任务：计时 + 失败可见。
     *
     * 不在这里单独存"投影到哪了"——进度由数据库自身的时间前沿推导
     * （MAX(lines.time) / MAX(object_messages.t_last)），
     * recall 的 freshness 直接读这两个值算 lag，不需要额外状态、也不会与数据不一致。
     */
    private static void runProjection(String name, Runnable task) {
        long t0 = System.currentTimeMillis();
        try {
            task.run();
            long ms = System.currentTimeMillis() - t0;
            if (ms > 3000) {
                android.util.Log.w("DSHL2", "projection " + name + " slow " + ms + "ms");
            }
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "projection " + name + " failed", t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppExclusionStore.load(this);
        // 记忆库单一大 helper：先建出空表，迁移在后续小步接
        DbMigrator.migrateIfNeeded(this);
        MemoryDb.checkMigrationState(this);
        if (MemoryDb.migrationWarning.isEmpty()) {
            try {
                MemoryProjector.projectEvents(this);
                MemoryProjector.projectTraceBlocks(this);
                MemoryProjector.projectDays(this);
            } catch (Throwable t) {
                android.util.Log.e("DSHL2", "startup projection failed; app continues", t);
            }
            final android.content.Context ctx = this;
            // 三条投影各自独立线程：原来串成一条链（lines → objmsg → backfill），
            // 任何一个慢就把后面的饿死。实测 backfill 永不收敛、projectLines 积压时能跑
            // 十几分钟，结果 object_messages 滞后 18 小时，系统对有证据的事答"没看到"。
            new Thread(() -> {
                try { Thread.sleep(15000); } catch (Throwable ignored) { }
                runProjection("lines", () -> MemoryProjector.projectLines(ctx));
            }, "proj-lines").start();
            new Thread(() -> {
                try { Thread.sleep(16000); } catch (Throwable ignored) { }
                runProjection("objmsg", () -> MemoryProjector.projectObjectMessages(ctx));
            }, "proj-objmsg").start();
            new Thread(() -> {
                try { Thread.sleep(20000); } catch (Throwable ignored) { }
                runProjection("backfill", () -> MemoryProjector.backfillFrameObjects(ctx));
            }, "proj-backfill").start();
            new Thread(() -> {
                try { Thread.sleep(25000); } catch (Throwable ignored) { }
                TraceModule.backfillEmptyText(ctx);
            }, "ocr-empty-backfill").start();
            new Thread(() -> {
                try { Thread.sleep(40000); } catch (Throwable ignored) { }
                java.io.File shots = new java.io.File(getFilesDir(), "trace/shots");
                ShotGc.Result g = ShotGc.run(shots, System.currentTimeMillis(),
                        ShotGc.MAX_BYTES);
                try {
                    new com.dsh.v2.TraceStore(getApplicationContext()).clearShotRefs(g.deletedFiles);
                } catch (Throwable ignored) { }
                android.util.Log.i("DSHL2", "gc drop_shots=" + g.dropShots
                        + " freed_mb=" + (g.freedBytes / 1024 / 1024)
                        + " total_mb=" + (g.totalBytes / 1024 / 1024)
                        + " low_disk=" + g.lowDisk);
            }, "shot-gc").start();
            ChainCoverage.backfill(this);
            final android.content.Context mctx = this;
            maintenance = java.util.concurrent.Executors.newScheduledThreadPool(3, r -> {
                Thread t = new Thread(r, "maintenance");
                t.setDaemon(true);
                return t;
            });
            // 三个任务各自定时，不再串成一条链。projectObjectMessages 是增量且快，
            // 给它更短的周期（60s），保证 object_messages 的新鲜度不被 lines 积压拖住。
            maintenance.scheduleWithFixedDelay(
                    () -> runProjection("lines", () -> MemoryProjector.projectLines(mctx)),
                    180, 180, java.util.concurrent.TimeUnit.SECONDS);
            maintenance.scheduleWithFixedDelay(
                    () -> runProjection("objmsg", () -> MemoryProjector.projectObjectMessages(mctx)),
                    60, 60, java.util.concurrent.TimeUnit.SECONDS);
            maintenance.scheduleWithFixedDelay(
                    () -> runProjection("backfill", () -> MemoryProjector.backfillFrameObjects(mctx)),
                    240, 240, java.util.concurrent.TimeUnit.SECONDS);
            maintenance.scheduleWithFixedDelay(() -> {
                try {
                    HealthHeartbeat.write(mctx);
                } catch (Throwable t) {
                    android.util.Log.e("DSHL2", "health heartbeat failed", t);
                }
            }, 30, 300, java.util.concurrent.TimeUnit.SECONDS);
        }
        File base = getFilesDir();
        File hostDir = new File(base, "host");
        if (!hostDir.exists()) hostDir.mkdirs();

        SimpleEventBus bus = new SimpleEventBus();
        SimpleLedger ledger = new SimpleLedger(200);
        SimpleStore store = new SimpleStore(base);
        SimpleLog log = new SimpleLog(new File(hostDir, "log/host.log"), new AndroidLogSink());
        configs = new ConfigStore(hostDir);
        secrets = new SecretStore(this);

        host = new Host(bus, ledger, store, log, configs, new HandlerScheduler0(), (id, h) ->
                log.i("host", "module " + id + " -> " + h));
        traceModule = new TraceModule(this, log);
        host.register(traceModule);
        usageModule = new UsageModule(this, log);
        host.register(usageModule);

        File auditDir = new File(hostDir, "audit");
        EgressGate gate = new EgressGate(new Guard(),
                new FileAuditSink(new File(auditDir, "egress.log")));
        modelsModule = new ModelsModule(secrets, gate,
                new FileUsageSink(new File(auditDir, "usage.log")),
                new UrlConnectionTransport());
        host.register(modelsModule);
        host.startAll();
        // 召回 IDF 后台预热：不阻塞主线程
        AndroidMemorySource.warmAsync(this);
    }

    public Host host() { return host; }

    public TraceModule traceModule() { return traceModule; }

    public UsageModule usageModule() { return usageModule; }

    public ModelsModule modelsModule() { return modelsModule; }

    public SecretStore secrets() { return secrets; }

    public ConfigStore configs() { return configs; }

    /** 供通知/测试看当前无障碍状态 */
    public void startForegroundService() {
        startService(new Intent(this, HostService.class));
    }
}
