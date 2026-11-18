package com.dsh.host.core;

import com.dsh.host.api.Store;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 存储实现：KV 内存 + 每模块目录。落盘下一版接 SharedPreferences / SQLite。 */
public final class SimpleStore implements Store {

    private final File root;
    private final Map<String, String> s = new ConcurrentHashMap<>();
    private final Map<String, Long> l = new ConcurrentHashMap<>();
    private final Map<String, Boolean> b = new ConcurrentHashMap<>();

    public SimpleStore(File root) { this.root = root; }

    @Override
    public File dir(String moduleId) {
        File d = new File(root, moduleId);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    @Override public void putString(String k, String v) { if (v == null) s.remove(k); else s.put(k, v); }
    @Override public String getString(String k, String d) { return s.getOrDefault(k, d); }

    @Override public void putLong(String k, long v) { l.put(k, v); }
    @Override public long getLong(String k, long d) { return l.getOrDefault(k, d); }

    @Override public void putBoolean(String k, boolean v) { b.put(k, v); }
    @Override public boolean getBoolean(String k, boolean d) { return b.getOrDefault(k, d); }
}
