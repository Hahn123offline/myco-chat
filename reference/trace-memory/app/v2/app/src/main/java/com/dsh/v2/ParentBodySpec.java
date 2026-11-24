package com.dsh.v2;

import com.dsh.agent.l1.MergeGrouping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 父链 body 规格（task-29）：
 *  - 400–800 字
 *  - 概括，不复述；按目的组织，不按时间逐条重放
 *  - 禁止继承子链的范围性断言
 */
public final class ParentBodySpec {

    public static final int MIN_CHARS = 400;
    public static final int MAX_CHARS = 800;

    /** 硬断言：父链视角下这些句式必然失真。 */
    public static final Pattern FORBIDDEN = Pattern.compile(
            "全程停留在|整个过程都在|整个过程都|这段时间一直在|(?<!尚)未看到|没有发生|"
                    + "无从判断|最终解决了|后来没再用");

    public static final String SYSTEM = "" +
            "你是父链的概括者，不是复述者。子链已经把细节写过了。\n" +
            "要求：\n" +
            "1. 你是概括，不是复述。不要按时间逐条重放，要按目的组织。\n" +
            "2. 不要重复子链里写过的界面细节（列表项、按钮、具体数值）。\n" +
            "3. 禁止范围性断言：「全程停留在 X」「整个过程都在 X」「这段时间一直在 X」" +
            "「未看到 Y」「没有发生 Z」——子链没看到，不等于父链范围内没发生。\n" +
            "4. 禁止对范围外判决：「无从判断」「最终解决了」「后来没再用」。\n" +
            "   没看到明确结束就用限定语：「截至记录结束，尚未观察到 X」\n" +
            "5. 只允许父链层面的真判断，例如「他先遇到 X，然后去 Y 查，最后看了 Z，" +
            "但没有看到实际修改的动作」「这件事没有明确的结束——最后停在 Z」。\n" +
            "6. 输出 400–800 字，只输出正文，不要 JSON、不要标题、不要解释。";

    private ParentBodySpec() { }

    public static String userPrompt(List<MergeGrouping.Item> items,
                                    MergeGrouping.Group group) {
        return userPrompt(items, group, 0);
    }

    public static String userPrompt(List<MergeGrouping.Item> items,
                                    MergeGrouping.Group group, int retryChars) {
        StringBuilder b = new StringBuilder();
        if (retryChars > 0) {
            b.append("你上一次输出 ").append(retryChars).append(" 字，超过 800。")
                    .append("请压缩到 800 以内，保留结论和结尾的范围限定句；")
                    .append("不要截断，也不要删除因果顺序。\n\n");
        }
        b.append("这是同一件事的 ").append(group.children.size()).append(" 个子链，")
                .append("请概括整件事（400–800 字）：\n\n");
        for (Integer i : group.children) {
            if (i == null || i < 0 || i >= items.size()) continue;
            MergeGrouping.Item it = items.get(i);
            b.append("【").append(it.title).append("】")
                    .append(" 时间=").append(fmt(it.t0)).append("-").append(fmt(it.t1))
                    .append(" 块数=").append(it.blocks).append('\n')
                    .append(it.body).append("\n\n");
        }
        b.append("提醒：不要按时间逐条重放，不要复制界面细节；")
                .append("不得出现「全程停留在/未看到/整个过程都/无从判断/最终解决了」这类断言。");
        return b.toString();
    }

    /** 统一成不带裸「未看到」的限定语，避免硬断言误伤。 */
    public static String normalize(String body) {
        if (body == null) return "";
        return body.replace("尚未看到", "尚未观察到")
                .replace("未看到", "没有看到");
    }

    /** @return 空串 = 合法；否则为拒绝原因。 */
    public static String validate(String body) {
        if (body == null || body.trim().isEmpty()) return "空 body";
        String s = body.trim();
        if (s.length() > MAX_CHARS) return "太长 " + s.length() + " > " + MAX_CHARS;
        // 短于 400 接受：可能真没那么多内容，标 short_body
        java.util.regex.Matcher m = FORBIDDEN.matcher(s);
        if (m.find()) return "含禁止范围断言: " + m.group();
        return "";
    }

    /** 从子链 key_block_ids 里挑 1–5 个：每子链先取一个，再补足，去重。 */
    public static String pickKeyIds(List<String> childKeyIds) {
        List<Long> picked = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (String csv : childKeyIds) {
            for (String s : split(csv)) {
                long id;
                try { id = Long.parseLong(s); } catch (Throwable t) { continue; }
                if (seen.add(id)) { picked.add(id); break; }
            }
        }
        for (String csv : childKeyIds) {
            for (String s : split(csv)) {
                if (picked.size() >= 5) break;
                long id;
                try { id = Long.parseLong(s); } catch (Throwable t) { continue; }
                if (seen.add(id)) picked.add(id);
            }
        }
        if (picked.size() > 5) picked = picked.subList(0, 5);
        StringBuilder b = new StringBuilder();
        for (Long id : picked) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return b.toString();
    }

    private static List<String> split(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        String x = csv.replace("[", "").replace("]", "");
        for (String s : x.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String fmt(long ms) {
        long min = (ms / 60_000) % 1440;
        return String.format("%02d:%02d", min / 60, min % 60);
    }
}
