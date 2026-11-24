package com.dsh.memory;

import java.util.Map;

/** 无命中诊断：区分「没采集」「没打开过目标应用」「应用有记录但没匹配」。 */
public final class DayDiagnostic {
    public final String day;
    public final int traces;
    public final long coveredMs;
    public final Map<String, Integer> pkgs;
    public final int events;
    public final int gaps;

    public DayDiagnostic(String day, int traces, long coveredMs, Map<String, Integer> pkgs,
                         int events, int gaps) {
        this.day = day == null ? "" : day;
        this.traces = traces;
        this.coveredMs = coveredMs;
        this.pkgs = pkgs;
        this.events = events;
        this.gaps = gaps;
    }

    public String render(String query) {
        StringBuilder b = new StringBuilder("没找到匹配。\n当天概况（").append(day).append("）：")
                .append("痕迹 ").append(traces).append(" 条 · 有记录 ")
                .append(coveredMs / 60_000).append(" 分钟");
        if (pkgs != null && !pkgs.isEmpty()) {
            b.append(" · 应用：");
            int i = 0;
            for (Map.Entry<String, Integer> e : pkgs.entrySet()) {
                if (i++ > 8) break;
                b.append(e.getKey()).append(' ').append(e.getValue()).append("、");
            }
            b.setLength(b.length() - 1);
        }
        b.append(" · 事件 ").append(events).append(" · 缺口 ").append(gaps).append(" 段\n");
        if (traces == 0) {
            b.append("→ 那天没有采集到任何记录");
        } else {
            String target = targetPkg(query);
            if (target != null) {
                Integer n = pkgs == null ? null : pkgs.get(target);
                String label = targetLabel(query, target);
                if (n == null) b.append("→ 那天有记录，但没有打开过 ").append(label);
                else b.append("→ ").append(label).append(" 那天有 ").append(n)
                        .append(" 条记录，但没有匹配「").append(query).append("」的内容");
            } else {
                b.append("→ 那天有记录，但没有匹配「").append(query).append("」的内容");
            }
        }
        return b.toString();
    }

    private static String targetLabel(String query, String pkg) {
        if (query != null && query.contains("B站")) return "B站";
        if (query != null && query.contains("微信")) return "微信";
        if (query != null && query.contains("QQ")) return "QQ";
        return pkg;
    }

    private static String targetPkg(String query) {
        if (query == null) return null;
        if (query.contains("B站") || query.toLowerCase().contains("bilibili"))
            return "tv.danmaku.bili";
        if (query.contains("微信")) return "com.tencent.mm";
        if (query.contains("QQ")) return "com.tencent.mobileqq";
        return null;
    }
}
