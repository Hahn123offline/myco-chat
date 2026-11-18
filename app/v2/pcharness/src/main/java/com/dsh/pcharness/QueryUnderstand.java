package com.dsh.pcharness;

import com.dsh.models.Json;
import com.dsh.models.ModelProvider;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 口语 query → 检索词 + 时间窗（只翻译，不判断）。 */
public final class QueryUnderstand {

    public static final class Result {
        public final List<String> keywords;
        public final String from;
        public final String to;
        Result(List<String> k, String f, String t) {
            keywords = k; from = f; to = t;
        }
    }

    private static final String SYSTEM = "把用户的检索口语翻译成检索词和时间窗。只输出 JSON："
            + "{\"keywords\":[\"词1\",\"词2\"],\"from\":\"yyyy-MM-dd 或 null\","
            + "\"to\":\"yyyy-MM-dd 或 null\"}。只翻译，不判断有没有记录；"
            + "keywords 保留专有名词/英文/错误码。";

    private QueryUnderstand() { }

    public static Result understand(ModelProvider provider, String query, LocalDate today) {
        if (provider == null || query == null || query.trim().isEmpty()) {
            return new Result(simpleKeywords(query), null, null);
        }
        try {
            ModelResponse r = provider.complete(ModelRequest.text(SYSTEM,
                    "今天=" + today + "\nquery=" + query).withThinking("disabled"));
            ModelMeter.record(r);
            if (r == null || !r.isOk() || r.text == null) throw new RuntimeException("model");
            Map<String, Object> o = Json.asObject(Json.parse(extract(r.text)));
            List<String> words = new ArrayList<>();
            List<Object> arr = Json.asArray(o == null ? null : o.get("keywords"));
            if (arr != null) for (Object x : arr) {
                String s = Json.string(x);
                if (s != null && !s.trim().isEmpty()) words.add(s.trim());
            }
            if (words.isEmpty()) words = simpleKeywords(query);
            boolean explicit = hasExplicitDate(query);
            return new Result(words,
                    explicit ? Json.string(o == null ? null : o.get("from")) : null,
                    explicit ? Json.string(o == null ? null : o.get("to")) : null);
        } catch (Throwable t) {
            return new Result(simpleKeywords(query), null, null);
        }
    }

    /** 只有明确日期表达才允许 QU 收窄时间窗；「那天/最近/这几天」不算。 */
    static boolean hasExplicitDate(String q) {
        if (q == null) return false;
        return q.matches(".*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号].*")
                || q.contains("昨天") || q.contains("前天") || q.contains("今天")
                || q.contains("上周") || q.contains("这周");
    }

    private static List<String> simpleKeywords(String query) {
        List<String> out = new ArrayList<>();
        if (query == null) return out;
        for (String s : query.split("\\s+")) if (!s.isEmpty()) out.add(s);
        if (out.isEmpty()) out.add(query);
        return out;
    }

    private static String extract(String s) {
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return a >= 0 && b > a ? s.substring(a, b + 1) : s;
    }
}
