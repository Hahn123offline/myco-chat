package com.dsh.models;

import com.dsh.core.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 :core Config（models.properties）解析 provider 定义。
 * 不含 API key：key 由 Android SecretStore 注入。
 */
public final class ModelsConfig {

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-flash";
    public static final int DEFAULT_TIMEOUT_MS = 60_000;

    public static final class ProviderDef {
        public final String name;
        public final ProviderKind kind;
        public final String baseUrl;
        public final String model;
        public final Set<Capability> caps;
        public final int timeoutMs;
        public final int maxContext;
        public final CostClass costClass;

        ProviderDef(String name, ProviderKind kind, String baseUrl, String model,
                    Set<Capability> caps, int timeoutMs, int maxContext, CostClass costClass) {
            this.name = name;
            this.kind = kind;
            this.baseUrl = baseUrl;
            this.model = model;
            this.caps = Collections.unmodifiableSet(caps);
            this.timeoutMs = timeoutMs;
            this.maxContext = maxContext;
            this.costClass = costClass;
        }
    }

    public final List<ProviderDef> providers;
    public final List<String> problems;
    public final boolean allowGrayCloud;

    private ModelsConfig(List<ProviderDef> providers, List<String> problems, boolean allowGrayCloud) {
        this.providers = Collections.unmodifiableList(providers);
        this.problems = Collections.unmodifiableList(problems);
        this.allowGrayCloud = allowGrayCloud;
    }

    public static ModelsConfig parse(Config c) {
        List<ProviderDef> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String list = c.getString("providers", "").trim();
        boolean allowGray = c.getBoolean("allowGrayCloud", false);

        if (list.isEmpty()) {
            // 默认 provider：DeepSeek（用户可在 UI 里改 baseUrl/model）
            out.add(new ProviderDef("deepseek", ProviderKind.CLOUD, DEFAULT_BASE_URL,
                    DEFAULT_MODEL, parseCaps(c.getString("provider.deepseek.caps",
                            "JSON,PROMPT_CACHE,LONG_CTX")), DEFAULT_TIMEOUT_MS, 64000,
                    CostClass.CHEAP));
            return new ModelsConfig(out, problems, allowGray);
        }
        for (String raw : list.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            String p = "provider." + name + ".";
            ProviderKind kind = "local".equalsIgnoreCase(c.getString(p + "kind", "cloud"))
                    ? ProviderKind.LOCAL : ProviderKind.CLOUD;
            String baseUrl = c.getString(p + "baseUrl",
                    kind == ProviderKind.CLOUD ? DEFAULT_BASE_URL : "http://127.0.0.1:8080");
            String model = c.getString(p + "model",
                    kind == ProviderKind.CLOUD ? DEFAULT_MODEL : "");
            String caps = c.getString(p + "caps",
                    kind == ProviderKind.CLOUD ? "JSON,PROMPT_CACHE,LONG_CTX" : "");
            int timeout = c.getInt(p + "timeoutMs",
                    kind == ProviderKind.CLOUD ? DEFAULT_TIMEOUT_MS : 120_000);
            int maxCtx = c.getInt(p + "maxContext", kind == ProviderKind.CLOUD ? 64000 : 8192);
            CostClass cost = parseCost(c.getString(p + "cost", "cheap"));
            if (baseUrl.isEmpty()) {
                problems.add("provider." + name + " 缺少 baseUrl");
                continue;
            }
            out.add(new ProviderDef(name, kind, baseUrl, model, parseCaps(caps),
                    timeout, maxCtx, cost));
        }
        if (out.isEmpty()) problems.add("没有任何可用的 provider");
        return new ModelsConfig(out, problems, allowGray);
    }

    public static Set<Capability> parseCaps(String csv) {
        Set<Capability> set = new LinkedHashSet<>();
        if (csv == null) return set;
        for (String s : csv.split(",")) {
            String v = s.trim().toUpperCase();
            if (v.isEmpty()) continue;
            try { set.add(Capability.valueOf(v)); } catch (Throwable ignored) { }
        }
        return set;
    }

    private static CostClass parseCost(String s) {
        if (s == null) return CostClass.CHEAP;
        String v = s.trim().toUpperCase();
        try { return CostClass.valueOf(v); } catch (Throwable ignored) { return CostClass.CHEAP; }
    }
}
