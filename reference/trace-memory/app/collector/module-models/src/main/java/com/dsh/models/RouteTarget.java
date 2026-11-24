package com.dsh.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 路由链上的一环：provider + 可选 model。
 * 配置写法 "local:minicpm-v-4.7-1b"，省略 model 时用 provider 的默认模型。
 */
public final class RouteTarget {

    public final String provider;
    /** null = 用 provider 默认模型 */
    public final String model;

    public RouteTarget(String provider, String model) {
        this.provider = provider == null ? "" : provider.trim();
        String m = model == null ? "" : model.trim();
        this.model = m.isEmpty() ? null : m;
    }

    /** 解析 "local:model,cloud:model2"；空段忽略。 */
    public static List<RouteTarget> parseChain(String spec) {
        if (spec == null || spec.trim().isEmpty()) return Collections.emptyList();
        List<RouteTarget> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            RouteTarget t = parse(s);
            if (t.provider.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    public static RouteTarget parse(String spec) {
        String s = spec == null ? "" : spec.trim();
        int c = s.indexOf(':');
        if (c < 0) return new RouteTarget(s, null);
        return new RouteTarget(s.substring(0, c), s.substring(c + 1));
    }

    @Override
    public String toString() {
        return provider + (model == null ? "" : ":" + model);
    }
}
