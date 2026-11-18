package com.dsh.host.core;

import com.dsh.host.api.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每模块一份 properties 配置，放在 host/config/&lt;moduleId&gt;.properties。
 *
 * 为什么用文件而不是 SharedPreferences：
 *  - 可被 adb 直接改，便于调试与热更新
 *  - 可 diff、可备份
 *  - 解析失败只影响该模块，不会连带宿主
 */
public final class ConfigStore {

    private final File dir;
    private final Map<String, Cache> cache = new ConcurrentHashMap<>();

    private static final class Cache {
        long lastModified;
        Config config;
    }

    public ConfigStore(File hostDir) {
        this.dir = new File(hostDir, "config");
        if (!dir.exists()) dir.mkdirs();
    }

    public File fileFor(String moduleId) {
        return new File(dir, moduleId + ".properties");
    }

    /** 读配置；文件变了会自动重载（热更新） */
    public Config load(String moduleId) {
        File f = fileFor(moduleId);
        long mtime = f.exists() ? f.lastModified() : -1;
        Cache c = cache.get(moduleId);
        if (c != null && c.lastModified == mtime && c.config != null) return c.config;

        Map<String, String> map = new LinkedHashMap<>();
        if (f.exists()) {
            try (InputStreamReader r = new InputStreamReader(
                    new FileInputStream(f), StandardCharsets.UTF_8)) {
                Properties p = new Properties();
                p.load(r);
                for (String k : p.stringPropertyNames()) map.put(k, p.getProperty(k));
            } catch (Exception ignored) {
                // 解析失败 → 用空配置，模块自己兜底默认值
            }
        }
        Cache nc = new Cache();
        nc.lastModified = mtime;
        nc.config = new MapConfig(new ConcurrentHashMap<>(map));
        cache.put(moduleId, nc);
        return nc.config;
    }

    /** 写入（给状态页 / 首次初始化用） */
    public void save(String moduleId, Map<String, String> values) {
        File f = fileFor(moduleId);
        Properties p = new Properties();
        for (Map.Entry<String, String> e : values.entrySet()) {
            p.setProperty(e.getKey(), e.getValue());
        }
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8)) {
            p.store(w, "managed by DSH host");
        } catch (Exception ignored) { }
        cache.remove(moduleId);
    }

    public boolean exists(String moduleId) { return fileFor(moduleId).exists(); }
}
