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
 * L1 之后的合并 pass：只判断「同一 L0 块内哪些子链服务同一目的」。
 * 失败/覆盖不全/缺 why → 不合并（保留原子链，不猜）。
 */
public final class MergeGrouping {

    public static final String SYSTEM = "" +
            "给你同一时间窗内的一组子链。判断哪些子链服务于同一个具体目的、应该合并成一条。\n" +
            "规则：\n" +
            "1. 只有明显是同一件事才合并；不确定就不要合并（宁缺毋滥）\n" +
            "2. 时间交错但目的不同不能合并；同一个 app、同属群聊、时间相邻都不等于同一目的\n" +
            "3. 反例：同一时间窗内「布儒斯特角公式学习」和「QQ群聊零杯大赛」交错出现，" +
            "一个是学公式、一个是群聊，不是同一件事，绝对不许合并\n" +
            "4. 每个子链最多出现在一个组；可以不输出所有子链（未输出的保持独立）\n" +
            "5. 只合并 size>=2 的组；每组必须给 why，且 why 要指出具体的同一目标，" +
            "不能只写「都是群聊」「同一 app」这种泛类别\n" +
            "6. 父标题不超过 20 字，只说明整体在做什么\n" +
            "只输出 JSON：{\"merge_groups\":[{\"children\":[0,1,2],\"title\":\"...\",\"why\":\"...\"}]}";

    public static final class Item {
        public final int index;
        public final String title;
        public final String body;
        public final long t0;
        public final long t1;
        public final int blocks;

        public Item(int index, String title, String body, long t0, long t1, int blocks) {
            this.index = index;
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.t0 = t0;
            this.t1 = t1;
            this.blocks = blocks;
        }
    }

    public static final class Group {
        public final List<Integer> children;
        public final String title;
        public final String why;

        public Group(List<Integer> children, String title, String why) {
            this.children = new ArrayList<>(children);
            this.title = title == null ? "" : title;
            this.why = why == null ? "" : why;
        }
    }

    public static final class Result {
        public final List<Group> groups;     // 只含 size>=2
        public final String reason;

        Result(List<Group> groups, String reason) {
            this.groups = groups;
            this.reason = reason == null ? "" : reason;
        }

        public boolean empty() { return groups.isEmpty(); }
    }

    private MergeGrouping() { }

    public static Result group(List<Item> items, LlmCaller caller) {
        if (items == null || items.size() < 2) return new Result(new ArrayList<>(), "子链不足 2 条");
        StringBuilder user = new StringBuilder("子链清单：\n");
        for (Item it : items) {
            user.append('[').append(it.index).append("] ")
                    .append(it.title).append(" 块数=").append(it.blocks)
                    .append(" 时间=").append(it.t0 / 60_000 % 1440).append("min")
                    .append(" body摘要=「").append(trunc(it.body)).append("」\n");
        }
        ModelResponse resp;
        try {
            resp = caller.call(ModelRequest.text(SYSTEM, user.toString())
                    .withProvenance(java.util.Collections.emptyList(),
                            com.dsh.egress.EgressStage.DERIVED)
                    .withThinking("disabled"));
        } catch (Throwable t) {
            return new Result(new ArrayList<>(), "调用异常: " + t.getClass().getSimpleName());
        }
        if (resp == null || !resp.isOk()) {
            return new Result(new ArrayList<>(), "模型失败: " + (resp == null ? "null" : resp.error));
        }
        return parse(resp.text, items.size());
    }

    public static Result parse(String content, int n) {
        try {
            String json = extractJson(content);
            Map<String, Object> root = Json.asObject(Json.parse(json));
            if (root == null) return new Result(new ArrayList<>(), "不是 JSON object");
            List<Object> arr = Json.asArray(root.get("merge_groups"));
            if (arr == null || arr.isEmpty()) return new Result(new ArrayList<>(), "无 merge_groups");
            List<Group> groups = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (Object o : arr) {
                List<Object> children;
                String title = "";
                String why = "";
                if (o instanceof List) {
                    children = Json.asArray(o);
                    title = "合并链";
                    why = "模型判定同一目的";
                } else {
                    Map<String, Object> m = Json.asObject(o);
                    if (m == null) return new Result(new ArrayList<>(), "组不是对象/数组");
                    children = Json.asArray(m.get("children"));
                    title = Json.string(m.get("title"));
                    why = Json.string(m.get("why"));
                }
                if (children == null || children.isEmpty()) {
                    return new Result(new ArrayList<>(), "children 为空");
                }
                List<Integer> ids = new ArrayList<>();
                for (Object idObj : children) {
                    if (!(idObj instanceof Number)) return new Result(new ArrayList<>(), "child 非数字");
                    int id = ((Number) idObj).intValue();
                    if (id < 0 || id >= n) return new Result(new ArrayList<>(), "child 越界 " + id);
                    if (!seen.add(id)) return new Result(new ArrayList<>(), "child 重复 " + id);
                    ids.add(id);
                }
                if (ids.size() >= 2) {
                    if (why == null || why.trim().isEmpty()) {
                        return new Result(new ArrayList<>(), "缺 why");
                    }
                    if (title == null || title.trim().isEmpty() || title.trim().length() > 20) {
                        return new Result(new ArrayList<>(), "父标题非法");
                    }
                    groups.add(new Group(ids, title.trim(), why.trim()));
                }
            }
            if (seen.size() > n) {
                return new Result(new ArrayList<>(), "覆盖越界");
            }
            // 允许模型只输出它确定的合并组；未出现的子链保持独立，不强行合
            return new Result(groups, "");
        } catch (Throwable t) {
            return new Result(new ArrayList<>(), "解析失败: " + t.getMessage());
        }
    }

    /** 父链 body 的 prompt：要求重新写，不拼接子链原文。 */
    public static String parentBodyPrompt(List<Item> items, Group g) {
        StringBuilder b = new StringBuilder();
        b.append("根据同一件事的多个子链记录，重新写成一段连贯、客观的叙述（不要逐条拼接）。\n");
        b.append("按时间顺序交代：先遇到什么、做了什么、结果如何。只写事实，不评价，不推测。\n");
        b.append("300–800 字。\n\n");
        for (Integer i : g.children) {
            if (i < 0 || i >= items.size()) continue;
            Item it = items.get(i);
            b.append("【").append(it.title).append("】\n").append(it.body).append("\n\n");
        }
        return b.toString();
    }

    private static String extractJson(String s) {
        if (s == null) return "";
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        return i >= 0 && j > i ? s.substring(i, j + 1) : s;
    }

    private static String trunc(String s) {
        String x = s == null ? "" : s.replace('\n', ' ');
        return x.length() > 160 ? x.substring(0, 160) + "…" : x;
    }
}
