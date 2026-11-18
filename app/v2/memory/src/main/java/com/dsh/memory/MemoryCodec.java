package com.dsh.memory;

import com.dsh.models.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemoryCodec {

    private MemoryCodec() { }

    public static String evidenceJson(List<Evidence> xs) {
        StringBuilder b = new StringBuilder("[");
        if (xs != null) {
            for (int i = 0; i < xs.size(); i++) {
                if (i > 0) b.append(',');
                Evidence e = xs.get(i);
                b.append("{\"type\":").append(Json.quote(e.type))
                        .append(",\"ref\":").append(Json.quote(e.ref))
                        .append(",\"t\":").append(e.t == null ? "null" : e.t).append('}');
            }
        }
        return b.append(']').toString();
    }

    public static List<Evidence> parseEvidence(String json) {
        List<Evidence> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            List<Object> arr = Json.asArray(Json.parse(json));
            if (arr == null) return out;
            for (Object x : arr) {
                Map<String, Object> m = Json.asObject(x);
                if (m == null) continue;
                Object t = m.get("t");
                out.add(new Evidence(Json.string(m.get("type")), Json.string(m.get("ref")),
                        t instanceof Number ? ((Number) t).longValue() : null));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** 合并 patch 到 data JSON；原字段保留。 */
    public static String patchData(String data, Map<String, Object> patch) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (data != null && !data.trim().isEmpty()) {
            try {
                Map<String, Object> old = Json.asObject(Json.parse(data));
                if (old != null) m.putAll(old);
            } catch (Throwable ignored) { }
        }
        if (patch != null) m.putAll(patch);
        return toJson(m);
    }

    public static String toJson(Map<String, Object> m) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append(Json.quote(e.getKey())).append(':').append(value(e.getValue()));
        }
        return b.append('}').toString();
    }

    private static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v instanceof Map) return toJson((Map<String, Object>) v);
        return Json.quote(String.valueOf(v));
    }
}
