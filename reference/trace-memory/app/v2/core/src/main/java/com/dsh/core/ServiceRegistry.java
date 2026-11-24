package com.dsh.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类型安全的服务注册表（借 Cordis 的 provide/inject 思想，但不用字符串名）。
 * 模块在 install 里 provide，别的模块 require；模块卸载时自动撤销。
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void put(Class<T> api, T impl) {
        services.put(api, impl);
    }

    public void remove(Class<?> api) { services.remove(api); }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> api) {
        Object o = services.get(api);
        return o == null ? null : (T) o;
    }

    public int size() { return services.size(); }
}
