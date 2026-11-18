package com.dsh.models;

import com.dsh.host.api.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link Config} 解析出的 provider 路由快照。
 *
 * 每次调用都重新 parse → 配合宿主 ConfigStore 的 mtime 热更新，改配置文件即生效。
 * 配置格式（config/models.properties）见 模型路由模块-设计-v0.1.md。
 *
 * 注意：这里不做任何"猜"——type 必须显式写 local / openai；
 * 写错就进 problems 并在 health 里如实显示，不会悄悄 fallback 到别的类型。
 */
public final class RouterConfig {

    public static final String DEFAULT_LOCAL_BASE_URL = "http://127.0.0.1:8080";
    public static final String DEFAULT_LOCAL_MODEL = "minicpm-v-4.7-1b";
    public static final int DEFAULT_LOCAL_TIMEOUT_MS = 120_000;
    public static final int DEFAULT_CLOUD_TIMEOUT_MS = 60_000;

    /** 有配置问题的 provider 不会进 providers，reason 进 problems */
    public final Map<String, ProviderConfig> providers;
    public final List<String> problems;

    private RouterConfig(Map<String, ProviderConfig> providers, List<String> problems) {
        this.providers = Collections.unmodifiableMap(providers);
        this.problems = Collections.unmodifiableList(problems);
    }

    public static RouterConfig parse(Config c) {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();

        String list = c.getString("providers", "");
        if (list.trim().isEmpty()) {
            problems.add("providers 未配置（应为逗号分隔的 provider 名，如 local,cloud）");
            return new RouterConfig(providers, problems);
        }
        for (String raw : list.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            String p = "provider." + name + ".";
            String typeStr = c.getString(p + "type", "").trim().toLowerCase();
            ProviderConfig.Type type;
            if ("local".equals(typeStr)) type = ProviderConfig.Type.LOCAL;
            else if ("openai".equals(typeStr)) type = ProviderConfig.Type.OPENAI;
            else {
                problems.add("provider." + name + ".type 必须是 local 或 openai（实际 '" + typeStr + "'）");
                continue;
            }
            boolean local = type == ProviderConfig.Type.LOCAL;
            String baseUrl = c.getString(p + "baseUrl", local ? DEFAULT_LOCAL_BASE_URL : "");
            String model = c.getString(p + "model", local ? DEFAULT_LOCAL_MODEL : "");
            String apiKey = c.getString(p + "apiKey", "");
            int timeout = c.getInt(p + "timeoutMs", local ? DEFAULT_LOCAL_TIMEOUT_MS : DEFAULT_CLOUD_TIMEOUT_MS);
            ProviderConfig pc = new ProviderConfig(name, type, baseUrl, apiKey, model, timeout);
            if (pc.baseUrl.isEmpty()) {
                problems.add("provider." + name + " 缺少 baseUrl");
                continue;
            }
            providers.put(name, pc);
        }
        if (providers.isEmpty()) {
            problems.add("没有任何可用的 provider");
        }
        return new RouterConfig(providers, problems);
    }

    /**
     * 取某任务的降级链：task.&lt;taskId&gt; → default。
     * 没用启发式匹配：taskId 精确读 key，读不到才走 default。
     */
    public static List<RouteTarget> route(Config c, String taskId) {
        String spec = c.getString("task." + taskId, null);
        if (spec == null) spec = c.getString("default", null);
        return RouteTarget.parseChain(spec);
    }

    @Override
    public String toString() {
        return "RouterConfig{providers=" + providers.values() + ", problems=" + problems + "}";
    }
}
