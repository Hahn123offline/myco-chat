package com.dsh.host.api;

/**
 * 模块能碰到的全部依赖。
 *
 * 所有注册类 API 都自动并入该模块的 scope：
 * 宿主卸载模块时倒序撤销，模块自己忘了清也不会泄漏。
 */
public interface HostContext {

    EventBus bus();
    CapabilityLedger ledger();
    Store store();
    Config config();
    Log log();

    /**
     * 注册一个"需要撤销"的东西。返回的句柄可提前取消。
     * 例：定时器、系统回调、文件句柄、线程。
     */
    AutoCloseable effect(Runnable onDispose);

    /** 订阅事件，自动并入 scope（卸载时自动退订）。 */
    <T extends Event> void on(Class<T> type, EventBus.Handler<T> handler);

    /** 周期性任务，自动并入 scope。 */
    void schedule(String name, long periodMs, Runnable task);

    /**
     * 注册一个服务，供别的模块 require。
     * 卸载时自动撤销（借 Cordis 的 provide/inject，但用类型而不是字符串名）。
     */
    <T> void provide(Class<T> api, T impl);

    /** 取服务；没有则返回 null（模块需自行处理缺失） */
    <T> T require(Class<T> api);

    /**
     * 只读访问**另一个模块**的配置段（宿主级公共项用，例如模型 prompt 放在
     * models.properties 里，由脱敏模块读取）。
     *
     * 默认退回自己的配置：测试/无宿主环境下行为不变。实现方（HostContextImpl）
     * 按 moduleId 从 ConfigStore 读，同样是 mtime 热更新。
     *
     * 这是**只读**的；模块仍然只能写自己的配置，不允许借此改别人。
     */
    default Config configOf(String moduleId) { return config(); }
}
