package com.dsh.core;

/** 模块自己的配置段（来自宿主配置文件，可按模块热更新）。 */
public interface Config {
    String getString(String key, String def);
    int getInt(String key, int def);
    long getLong(String key, long def);
    boolean getBoolean(String key, boolean def);
    /** 该模块是否有此键 */
    boolean has(String key);
}
