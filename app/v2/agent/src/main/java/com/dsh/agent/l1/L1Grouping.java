package com.dsh.agent.l1;

import com.dsh.agent.LlmCaller;
import com.dsh.models.Json;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L1 子链拆分：一次普通文本模型调用（thinking 关闭、max_tokens 2048）。
 *
 * 硬要求：互斥且覆盖全部会话（允许时间交错）；JSON 非法/漏项/重复 → 整个 L0 块退化为一条子链。
 */
public final class L1Grouping {

    public static final String SYSTEM = "" +
            "你在把一段手机使用记录按「小目的」拆成子链。\n" +
            "规则：\n" +
            "1. 同一目的被打断多次，仍属于同一条子链（允许时间交错）\n" +
            "2. 每条会话必须且只能属于一条子链（互斥、覆盖全部）\n" +
            "3. 可以跨应用合并\n" +
            "4. 零碎的归入「杂项」\n" +
            "5. 标题不超过 15 个汉字，只说明在做什么，不要评价\n" +
            "只输出 JSON：{\"subchains\":[{\"title\":\"...\",\"session_ids\":[1,2]}]}，不要任何解释。";

    public static final class Result {
        public final List<L1Subchain> subchains;
        public final boolean fallback;
        public final String reason;

        Result(List<L1Subchain> subchains, boolean fallback, String reason) {
            this.subchains = subchains;
            this.fallback = fallback;
            this.reason = reason == null ? "" : reason;
        }
    }

    private L1Grouping() { }

    public static String userPrompt(List<L1Session> sessions) {
        StringBuilder b = new StringBuilder();
        b.append("会话清单（共 ").append(sessions.size()).append(" 条）：\n");
        for (L1Session s : sessions) {
            b.append('#').append(s.index).append(' ')
                    .append(fmt(s.t0)).append('-').append(fmt(s.t1)).append(' ')
                    .append(s.pkg).append(" 块数=").append(s.blocks)
                    .append(" 首「").append(trunc(s.head)).append("」")
                    .append(" 末「").append(trunc(s.tail)).append("」\n");
        }
        return b.toString();
    }

    public static Result group(List<L1Session> sessions, LlmCaller caller) {
        int n = sessions.size();
        if (n == 0) return new Result(new ArrayList<>(), true, "无会话");
        ModelResponse resp;
        try {
            java.util.List<String> sources = new java.util.ArrayList<>();
            for (L1Session s : sessions) if (s.pkg != null && !s.pkg.isEmpty()
                    && !sources.contains(s.pkg)) sources.add(s.pkg);
            resp = caller.call(ModelRequest.text(SYSTEM, userPrompt(sessions))
                    .withProvenance(sources, com.dsh.egress.EgressStage.MASKED)
                    .withThinking("disabled"));
        } catch (Throwable t) {
            return fallback(sessions, "调用异常: " + t.getClass().getSimpleName());
        }
        if (resp == null || !resp.isOk()) {
            return fallback(sessions, "模型失败: " + (resp == null ? "null" : resp.error));
        }
        return parse(resp.text, n, sessions);
    }

    /** 解析 + 校验；任何不合法都退化为「一条子链覆盖全部」。 */
    public static Result parse(String content, int n, List<L1Session> sessions) {
        try {
            String json = extractJson(content);
            Map<String, Object> root = Json.asObject(Json.parse(json));
            if (root == null) return fallback(sessions, "不是 JSON object");
            List<Object> arr = Json.asArray(root.get("subchains"));
            if (arr == null || arr.isEmpty()) return fallback(sessions, "缺少 subchains");
            List<L1Subchain> out = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (Object o : arr) {
                Map<String, Object> m = Json.asObject(o);
                if (m == null) return fallback(sessions, "subchain 不是对象");
                String title = Json.string(m.get("title"));
                if (title == null || title.trim().isEmpty() || title.trim().length() > 15) {
                    return fallback(sessions, "标题非法: " + title);
                }
                List<Object> ids = Json.asArray(m.get("session_ids"));
                if (ids == null || ids.isEmpty()) return fallback(sessions, "session_ids 为空");
                List<Integer> list = new ArrayList<>();
                for (Object idObj : ids) {
                    if (!(idObj instanceof Number)) return fallback(sessions, "session_id 不是数字");
                    int id = ((Number) idObj).intValue();
                    if (id < 1 || id > n) return fallback(sessions, "session_id 越界: " + id);
                    if (!seen.add(id)) return fallback(sessions, "session_id 重复: " + id);
                    list.add(id);
                }
                out.add(new L1Subchain(title.trim(), list));
            }
            if (seen.size() != n) return fallback(sessions, "覆盖不全 " + seen.size() + "/" + n);
            return new Result(out, false, "");
        } catch (Throwable t) {
            return fallback(sessions, "解析失败: " + t.getMessage());
        }
    }

    public static Result fallback(List<L1Session> sessions, String reason) {
        List<Integer> all = new ArrayList<>();
        for (L1Session s : sessions) all.add(s.index);
        String title = sessions.isEmpty() ? "杂项" : "杂项（" + sessions.get(0).pkg + "）";
        if (title.length() > 15) title = title.substring(0, 15);
        List<L1Subchain> list = new ArrayList<>();
        list.add(new L1Subchain(title, all));
        return new Result(list, true, reason);
    }

    private static String extractJson(String s) {
        if (s == null) return "";
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        return i >= 0 && j > i ? s.substring(i, j + 1) : s;
    }

    private static String trunc(String s) {
        if (s == null) return "";
        String x = s.replace('\n', ' ');
        return x.length() > 60 ? x.substring(0, 60) + "…" : x;
    }

    private static String fmt(long ms) {
        long min = (ms / 60_000) % 1440;
        return String.format("%02d:%02d", min / 60, min % 60);
    }
}
