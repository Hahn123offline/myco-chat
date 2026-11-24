package com.dsh.models;

import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;

/**
 * provider 抽象：一次**同步**的模型调用。
 *
 * 约束：
 *  - 不自己管并发、不自己重试、不自己降级 —— 那些是 ModelRouter 的职责；
 *  - 失败不抛异常给调用方，返回 error 响应（transport 抛出的异常由路由兜住）；
 *  - 实现里不允许读全局单例 / 配置，一切从 ProviderConfig 与 LlmRequest 来。
 */
public interface LlmProvider {

    /** 阻塞式调用（在路由器的工作线程里跑，不在安卓主线程）。 */
    LlmResponse complete(LlmRequest req) throws Exception;
}
