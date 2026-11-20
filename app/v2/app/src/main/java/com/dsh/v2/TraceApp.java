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
            new Thread(() -> {
                try { Thread.sleep(15000); } catch (Throwable ignored) { }
                MemoryProjector.projectLines(ctx);
                MemoryProjector.backfillFrameObjects(ctx);
                MemoryProjector.projectObjectMessages(ctx);
            }, "line-projector").start();
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
            maintenance = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "maintenance");
                t.setDaemon(true);
                return t;
            });
            maintenance.scheduleWithFixedDelay(() -> {
                try {
                    MemoryProjector.projectLines(mctx);
                    MemoryProjector.backfillFrameObjects(mctx);
                    MemoryProjector.projectObjectMessages(mctx);
                } catch (Throwable t) {
                    android.util.Log.e("DSHL2", "periodic line projection failed", t);
                }
            }, 180, 180, java.util.concurrent.TimeUnit.SECONDS);
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
