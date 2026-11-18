package com.dsh.models;

import java.util.Set;

/**
 * 模型 provider 抽象。
 *
 * 约束：provider 不自己路由/不重试/不降级；失败返回 error 响应或抛异常由路由器兜住。
 * 不允许读全局用户配置；密钥由构造时注入，且绝不进日志。
 */
public interface ModelProvider {

    String id();

    ProviderKind kind();

    Set<Capability> caps();

    int maxContext();

    CostClass costClass();

    /** 阻塞调用（路由器工作线程），返回 usage。 */
    ModelResponse complete(ModelRequest request) throws Exception;

    default boolean cloud() { return kind() == ProviderKind.CLOUD; }

    default boolean supports(Set<Capability> need) {
        return need == null || caps().containsAll(need);
    }
}
