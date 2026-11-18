package com.dsh.host.api;

import java.io.File;

/**
 * 统一存储：模块不得自己开 SharedPreferences / 自己找目录。
 * 所有路径都由宿主给出，方便备份、清理、迁移。
 */
public interface Store {

    /** 模块私有目录（会被清理策略管理） */
    File dir(String moduleId);

    void putString(String key, String value);
    String getString(String key, String defaultValue);

    void putLong(String key, long value);
    long getLong(String key, long defaultValue);

    void putBoolean(String key, boolean value);
    boolean getBoolean(String key, boolean defaultValue);
}
