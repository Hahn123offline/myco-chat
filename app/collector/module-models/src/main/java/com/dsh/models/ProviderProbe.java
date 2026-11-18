package com.dsh.models;

/**
 * provider 可达性探测（只用于本地 provider）。
 *
 * "本地模型没起来就是 DOWN" 是硬要求：health 必须来自真实探测，而不是"配置写了就算 OK"。
 * 抽成接口是为了单测里注入假探测，不真连网。
 *
 * **实现会在后台线程被调用**（ModelRouter.probeAsync）；不要在主线程/install 里同步调用探测。
 */
public interface ProviderProbe {

    /** 真实发一次探测；失败返回带原因的结果，不抛异常。 */
    ProbeResult probe(ProviderConfig cfg);
}
