package com.dsh.pcharness;

import com.dsh.memory.RecallHit;
import com.dsh.models.Json;
import com.dsh.models.ModelProvider;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** top1 分数低时问模型：这些片段真的能回答 query 吗？ */
public final class RelevanceJudge {

    public static final class Result {
        public final boolean match;
        public final Set<Long> ids;
        public final String reason;
        public final boolean error;
        Result(boolean m, Set<Long> i, String r, boolean e) {
            match = m; ids = i; reason = r; error = e;
        }
    }

    private static final String SYSTEM = "判断给出的记忆片段能否回答用户 query。"
            + "只输出 JSON：{\"match\":true/false,\"ids\":[命中片段id],"
            + "\"reason\":\"一句话说明\"}。"
            + "match=false 必须给出具体理由（主题不同/只是同词/query 超出片段范围）。"
            + "不要因为出现同一个词就判 true；但片段确实相关时要判 true。";

    private RelevanceJudge() { }

    public static Result judge(ModelProvider provider, String query, List<RecallHit> hits) {
        if (provider == null || hits == null || hits.isEmpty()) {
            return new Result(true, new HashSet<>(), "", false);
        }
        StringBuilder b = new StringBuilder("query=").append(query).append('\n');
        for (int i = 0; i < Math.min(5, hits.size()); i++) {
            RecallHit h = hits.get(i);
            b.append("id=").append(h.itemId).append(" pkg=").append(h.pkg)
                    .append(" text=").append(shorten(h.text)).append('\n');
        }
        try {
            ModelResponse r = provider.complete(ModelRequest.text(SYSTEM, b.toString())
                    .withThinking("disabled"));
            ModelMeter.record(r);
            if (r == null || !r.isOk() || r.text == null) throw new RuntimeException("model");
            Map<String, Object> o = Json.asObject(Json.parse(extract(r.text)));
            boolean match = Boolean.TRUE.equals(o == null ? null : o.get("match"));
            Set<Long> ids = new HashSet<>();
            List<Object> arr = Json.asArray(o == null ? null : o.get("ids"));
            if (arr != null) for (Object x : arr) {
                if (x instanceof Number) ids.add(((Number) x).longValue());
            }
            return new Result(match, ids, Json.string(o == null ? null : o.get("reason")), false);
        } catch (Throwable t) {
            return new Result(true, new HashSet<>(), "judge error: " + t.getMessage(), true);
        }
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String x = s.replace('\n', ' ').trim();
        return x.length() <= 200 ? x : x.substring(0, 200);
    }

    private static String extract(String s) {
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return a >= 0 && b > a ? s.substring(a, b + 1) : s;
    }
}
