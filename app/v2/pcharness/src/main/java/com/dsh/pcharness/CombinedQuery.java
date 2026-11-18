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

/** Stage 2 单次调用：QU + Judge 合并。 */
public final class CombinedQuery {

    public static final class Result {
        public final List<String> keywords;
        public final Set<Long> ids;
        public final boolean none;
        public final String reason;
        public final String answer;
        public final boolean error;
        Result(List<String> k, Set<Long> i, boolean n, String r, String a, boolean e) {
            keywords = k; ids = i; none = n; reason = r; answer = a; error = e;
        }
    }

    private static final String SYSTEM = "用候选片段回答用户 query，只输出 JSON："
            + "{\"answer\":\"一句话直接回答query；答不了就 null\",\"ids\":[用到的片段id],"
            + "\"keywords\":[\"翻译后的检索词\"],\"reason\":\"一句话\"}。"
            + "answer 必须依据片段内容；片段只是同词、主题不同就 answer=null、ids=[]。";

    private CombinedQuery() { }

    public static Result run(ModelProvider provider, String query, List<RecallHit> hits) {
        if (provider == null) {
            return new Result(java.util.Collections.singletonList(query),
                    new HashSet<>(), false, "no model", null, true);
        }
        StringBuilder b = new StringBuilder("query=").append(query).append('\n');
        for (int i = 0; i < Math.min(5, hits.size()); i++) {
            RecallHit h = hits.get(i);
            b.append("id=").append(h.itemId).append(" text=")
                    .append(shorten(h.text)).append('\n');
        }
        try {
            ModelResponse r = provider.complete(ModelRequest.text(SYSTEM, b.toString())
                    .withThinking("disabled"));
            ModelMeter.record(r);
            if (r == null || !r.isOk() || r.text == null) throw new RuntimeException("model");
            Map<String, Object> o = Json.asObject(Json.parse(extract(r.text)));
            List<String> words = new ArrayList<>();
            List<Object> arr = Json.asArray(o == null ? null : o.get("keywords"));
            if (arr != null) for (Object x : arr) {
                String s = Json.string(x);
                if (s != null && !s.trim().isEmpty()) words.add(s.trim());
            }
            Set<Long> ids = new HashSet<>();
            List<Object> idArr = Json.asArray(o == null ? null : o.get("ids"));
            if (idArr != null) for (Object x : idArr) {
                if (x instanceof Number) ids.add(((Number) x).longValue());
            }
            String answer = Json.string(o == null ? null : o.get("answer"));
            boolean none = answer == null || answer.trim().isEmpty() || ids.isEmpty();
            return new Result(words, ids, none,
                    Json.string(o == null ? null : o.get("reason")), answer, false);
        } catch (Throwable t) {
            return new Result(java.util.Collections.singletonList(query),
                    new HashSet<>(), false, "error: " + t.getMessage(), null, true);
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
