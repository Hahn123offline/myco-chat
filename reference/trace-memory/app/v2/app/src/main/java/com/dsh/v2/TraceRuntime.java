package com.dsh.v2;

/**
 * 服务 ↔ 模块的显式静态桥。
 *
 * 旧做法是服务直接读 {@code TraceApp.instance()}——Application 初始化顺序/进程重启时
 * 容易出现拿到 null 却静默丢事件。现在由 TraceModule.install 设置、dispose 清除；
 * 服务取不到时打 ERROR（限流），不再静默。
 */
public final class TraceRuntime {

    private static volatile TraceModule module;

    private TraceRuntime() { }

    static void set(TraceModule m) { module = m; }

    static void clear() { module = null; }

    public static TraceModule get() { return module; }
}
