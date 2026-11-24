package com.dsh.host.api.llm;

import com.dsh.host.api.Health;

/**
 * 全 app 模型调用的**唯一出口**。
 *
 * 硬约束：任何模块都不得自己开 HTTP 去调模型。要模型能力就 require(LlmService.class)，
 * 这样"用哪个 provider / 哪个模型 / 并发多少 / 超时多久 / 失败降级到哪"全部由模型路由模块
 * 一处决定，调用方只说"我要做什么任务"。
 *
 * 为什么是回调式：安卓主线程不能阻塞，一次推理可能是几百毫秒到几十秒。
 * 实现约定：
 *  - complete() 立即返回，不阻塞调用线程；
 *  - 回调**恰好一次**，在后台线程执行（调用方自己决定要不要 post 回 UI）；
 *  - 失败不抛异常，用 {@link LlmResponse#error} 表达（调用方决定怎么处理）。
 */
public interface LlmService {

    /** 发起一次模型调用。req 为 null 或已关闭时也走回调，返回 error 响应。 */
    void complete(LlmRequest req, Callback cb);

    /**
     * 模型服务当前能不能干活（如实反映，见 Health 约定）。
     *
     * 调用方用它决定"要不要现在做模型这一步"：例如实时脱敏只在健康时补模型，
     * 不健康就落规则级、等充电批处理。不要自己猜 provider 状态。
     */
    Health health();

    interface Callback {
        void onResult(LlmResponse resp);
    }
}
