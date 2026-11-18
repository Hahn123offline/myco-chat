package com.dsh.core;

import java.util.List;

/**
 * 插件契约：宿主只认这个接口。
 *
 * 只有一个入口 install()。没有 onStart/onStop 双钩子——那一定会漏销毁。
 * 模块里任何需要撤销的东西（订阅、定时器、回调、句柄）都必须通过
 * HostContext.effect() 注册，宿主卸载时倒序关闭。
 */
public interface Module {

    /** 唯一标识，如 "usagetrace" */
    String id();

    String version();

    /** 需要的能力；required 缺一个宿主就不会 start 它 */
    List<Capability> requires();

    /**
     * 唯一入口。可被宿主多次调用（restart = 关闭旧 scope 后重新 install）。
     * 抛出异常 → 该模块标记 FAILED，宿主与其它模块继续。
     */
    void install(HostContext ctx) throws Exception;

    /** 如实反映当前能不能干活。不能假装 OK。 */
    Health health();
}
