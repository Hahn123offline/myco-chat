package com.dsh.core;

import com.dsh.core.Health;
import com.dsh.core.HostContext;
import com.dsh.core.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主核心：注册表 + 生命周期 + 失败隔离。
 *
 * 生命周期：install 一次 = 一个 scope。
 * 重启 = 关闭旧 scope（倒序撤销）→ 重新 install。
 */
public final class Host {

    public static final class Slot {
        public final Module module;
        public Health health = Health.stopped();
        public Scope scope;
        public HostContext context;
        public long installedAt = 0;
        public int restarts = 0;
        /** install 是否成功；失败的模块 health() 可能是 OK（模块自己不知道），不能被 snapshot 覆盖掉 */
        public boolean installOk = false;

        Slot(Module m) { this.module = m; }
    }

    private final SimpleEventBus bus;
    private final SimpleLedger ledger;
    private final SimpleStore store;
    private final SimpleLog log;
    private final ConfigStore configs;
    private final Scheduler scheduler;
    private final ServiceRegistry registry = new ServiceRegistry();
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final Listener listener;

    public interface Listener {
        void onModuleState(String moduleId, Health health);
    }

    public Host(SimpleEventBus bus, SimpleLedger ledger, SimpleStore store,
                SimpleLog log, ConfigStore configs, Listener l) {
        this(bus, ledger, store, log, configs, new Scheduler.NoopScheduler(), l);
    }

    public Host(SimpleEventBus bus, SimpleLedger ledger, SimpleStore store,
                SimpleLog log, ConfigStore configs, Scheduler scheduler, Listener l) {
        this.bus = bus;
        this.ledger = ledger;
        this.store = store;
        this.log = log;
        this.configs = configs;
        this.scheduler = scheduler == null ? new Scheduler.NoopScheduler() : scheduler;
        this.listener = l;
    }

    public synchronized void register(Module m) {
        slots.put(m.id(), new Slot(m));
    }

    /** 按注册顺序启动；单个模块失败只标记自己。 */
    public synchronized void startAll() {
        for (Slot s : slots.values()) install(s);
    }

    private void install(Slot s) {
        s.scope = new Scope(s.module.id());
        // 传 ConfigStore + moduleId 而不是 Config 快照：模块热更新配置才真正生效
        s.context = new HostContextImpl(bus, ledger, store, log, s.scope, configs,
                s.module.id(), registry, scheduler);
        try {
            s.module.install(s.context);
            s.installOk = true;
            s.health = s.module.health();
            s.installedAt = System.currentTimeMillis();
            log.i("host", "installed " + s.module.id() + " scope=" + s.scope.size());
        } catch (Throwable t) {
            s.installOk = false;
            s.health = Health.failed(t.getClass().getSimpleName() + ": " + t.getMessage());
            log.e("host", "install failed " + s.module.id(), t);
            try { s.scope.close(); } catch (Throwable ignored) { }
        }
        notify(s);
    }

    public synchronized void stopAll() {
        for (Slot s : slots.values()) {
            try {
                if (s.scope != null) s.scope.close();
            } catch (Throwable t) {
                log.e("host", "close scope " + s.module.id(), t);
            }
            s.health = Health.stopped();
            notify(s);
        }
    }

    /** 单模块重启：关闭旧 scope → 重新 install。 */
    public synchronized void restart(String id) {
        Slot s = slots.get(id);
        if (s == null) return;
        if (s.scope != null) {
            try { s.scope.close(); } catch (Throwable ignored) { }
        }
        install(s);
        s.restarts++;
    }

    /** 状态页轮询：让各模块刷新 health（install 失败的模块保持 FAILED，不被模块自己的 OK 覆盖）。 */
    public synchronized List<Slot> snapshot() {
        for (Slot s : slots.values()) {
            if (!s.installOk) continue;
            try { s.health = s.module.health(); } catch (Throwable t) {
                s.health = Health.failed("health() threw: " + t.getMessage());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(slots.values()));
    }

    private void notify(Slot s) {
        if (listener != null) {
            try { listener.onModuleState(s.module.id(), s.health); } catch (Throwable ignored) { }
        }
    }
}
