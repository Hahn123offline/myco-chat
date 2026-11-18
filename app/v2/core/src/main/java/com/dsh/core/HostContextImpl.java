package com.dsh.core;

/**
 * 每个模块拿到的是"带 scope 的上下文"：
 * 所有注册都进该模块的 scope，卸载时倒序撤销。
 *
 * 配置是**活引用**：config() 每次都问 ConfigStore 要最新的那一份。
 * ConfigStore.load 自己按文件 mtime 判断是否重读，所以这里不会每次真读盘；
 * 反过来若把 Config 快照存在字段里，热更新就永远不生效（改文件必须重启模块）。
 *
 * 核心库不依赖 Android：周期任务通过 {@link Scheduler} 注入（Android 端用 Handler 实现）。
 */
public final class HostContextImpl implements HostContext {

    private final EventBus bus;
    private final CapabilityLedger ledger;
    private final Store store;
    private final Log log;
    private final Scope scope;
    private final ConfigStore configs;
    private final String moduleId;
    private final ServiceRegistry registry;
    private final Scheduler scheduler;

    public HostContextImpl(EventBus bus, CapabilityLedger ledger, Store store,
                           Log log, Scope scope, ConfigStore configs, String moduleId,
                           ServiceRegistry registry, Scheduler scheduler) {
        this.bus = bus;
        this.ledger = ledger;
        this.store = store;
        this.log = log;
        this.scope = scope;
        this.configs = configs;
        this.moduleId = moduleId;
        this.registry = registry;
        this.scheduler = scheduler == null ? new Scheduler.NoopScheduler() : scheduler;
    }

    @Override public EventBus bus() { return bus; }
    @Override public CapabilityLedger ledger() { return ledger; }
    @Override public Store store() { return store; }

    /** 每次取都拿最新配置；ConfigStore.load 内部有 mtime 缓存，开销只有一次 stat */
    @Override public Config config() { return configs.load(moduleId); }

    /** 只读访问别的模块配置段（宿主级公共项，如 prompt）。同样是活引用。 */
    @Override public Config configOf(String otherModuleId) { return configs.load(otherModuleId); }

    @Override public Log log() { return log; }

    @Override public AutoCloseable effect(Runnable onDispose) { return scope.add(onDispose); }

    @Override
    public <T extends Event> void on(Class<T> type, EventBus.Handler<T> h) {
        bus.subscribe(type, h);
        scope.add(() -> bus.unsubscribe(h));
    }

    @Override
    public void schedule(String name, long periodMs, Runnable task) {
        final AutoCloseable[] handle = new AutoCloseable[1];
        try {
            handle[0] = scheduler.schedule(name, periodMs, () -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    log.e(name, "scheduled task threw", t);
                }
            });
        } catch (Throwable t) {
            log.e(name, "schedule failed", t);
            return;
        }
        scope.add(() -> {
            try {
                if (handle[0] != null) handle[0].close();
            } catch (Throwable ignored) { }
        });
    }

    @Override
    public <T> void provide(Class<T> api, T impl) {
        registry.put(api, impl);
        scope.add(() -> registry.remove(api));
    }

    @Override
    public <T> T require(Class<T> api) {
        return registry.get(api);
    }

    /** 超出 scope 的额外上下文能力（供宿主内部用） */
    public Scope scope() { return scope; }
}
