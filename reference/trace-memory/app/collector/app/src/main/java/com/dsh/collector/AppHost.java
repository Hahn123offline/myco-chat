package com.dsh.collector;

import android.content.Context;
import android.util.Log;

import com.dsh.host.api.Health;
import com.dsh.host.core.Host;
import com.dsh.host.core.SimpleEventBus;
import com.dsh.host.core.SimpleLedger;
import com.dsh.host.core.SimpleLog;
import com.dsh.host.core.SimpleStore;
import com.dsh.usagetrace.UsageTraceModule;

import java.io.File;
import java.util.List;

/**
 * 宿主单例：整个 App 只有一个 Host，启动即装配插件。
 * 这是"启动 = 启动宿主"的落点。
 */
public final class AppHost {

    private static final String TAG = "AppHost";
    private static AppHost INSTANCE;

    public static synchronized AppHost get(Context c) {
        if (INSTANCE == null) INSTANCE = new AppHost(c.getApplicationContext());
        return INSTANCE;
    }

    private final Context app;
    private Host host;
    private SimpleEventBus bus;
    private SimpleLedger ledger;
    private SimpleStore store;
    private SimpleLog log;
    private boolean started = false;

    private AppHost(Context app) { this.app = app; }

    public synchronized void start() {
        if (started) return;
        File base = app.getExternalFilesDir(null);
        if (base == null) base = app.getFilesDir();
        File hostDir = new File(base, "host");
        if (!hostDir.exists()) hostDir.mkdirs();

        bus = new SimpleEventBus();
        ledger = new SimpleLedger(500);
        // Store 的根是外部 files 目录：模块私有目录 = files/<moduleId>（如 files/redaction/metrics.txt）
        store = new SimpleStore(base);
        // logcat + 文件双写：某些 ROM/场景看不到 logcat 时，files/host/log/host.log 仍可拉取诊断
        log = new SimpleLog(new File(hostDir, "log/host.log"));

        log.i("host", "=== AppHost.start ===");
        bus.setOnError((t, e) -> Log.e(TAG, "bus handler threw on " + e.getClass().getSimpleName(), t));

        host = new Host(bus, ledger, store, log, new com.dsh.host.core.ConfigStore(hostDir), (id, h) ->
                Log.i(TAG, "module " + id + " -> " + h));

        // 模型路由先装：它是全 app 的模型调用唯一出口，其它模块 require(LlmService) 时才拿得到
        host.register(new com.dsh.models.ModelRouterModule());
        host.register(new UsageTraceModule(app));
        host.register(new com.dsh.redaction.RedactionModule(app));
        host.register(new com.dsh.memory.MemoryModule(app));
        // 云端打标最后装：依赖 models(LlmService) + memory(MemoryQuery/EgressPolicy)，运行期 require
        host.register(new com.dsh.enrich.EnrichModule(app));
        host.startAll();
        started = true;
        log.i("host", "host started, modules=" + host.snapshot().size()
                + " log=" + log.file());
        Log.i(TAG, "host started, modules=" + host.snapshot().size());
    }

    public synchronized void stop() {
        if (host != null) host.stopAll();
        started = false;
    }

    public synchronized List<Host.Slot> snapshot() {
        return host == null ? java.util.Collections.emptyList() : host.snapshot();
    }

    public synchronized void restartModule(String id) {
        if (host != null) host.restart(id);
    }

    public synchronized SimpleEventBus bus() { return bus; }
    public synchronized SimpleLedger ledger() { return ledger; }

    public static boolean isStarted() { return INSTANCE != null && INSTANCE.started; }
}
