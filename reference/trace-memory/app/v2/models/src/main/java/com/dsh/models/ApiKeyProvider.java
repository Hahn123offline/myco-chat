package com.dsh.models;

/** API key 注入接口：实现方（Android SecretStore）负责私有存储；模型层不落盘、不日志。 */
public interface ApiKeyProvider {
    String keyFor(String providerId);
}
