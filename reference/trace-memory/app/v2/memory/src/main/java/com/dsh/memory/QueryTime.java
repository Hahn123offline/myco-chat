package com.dsh.memory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从自然语言 query 解析日期/时段，避免依赖模型传 from/to。 */
public final class QueryTime {

    public static final class Range {
        public final long from, to;
        public final String label;
        public Range(long from, long to, String label) {
            this.from = from; this.to = to; this.label = label;
        }
    }

    private static final Pattern DATE = Pattern.compile(
            "(?:(\\d{4})\\s*[-/年])?(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern REL = Pattern.compile("(今天|昨天|前天|大前天)");
    private static final Pattern PERIOD = Pattern.compile(
            "(凌晨|早上|上午|中午|下午|傍晚|晚上|夜里|昨晚|今晚)");

    private QueryTime() { }

    /** 解析不到时间表达返回 null。 */
    public static Range parse(String query, LocalDate today) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim();
        Matcher dm = DATE.matcher(q);
        LocalDate day = null;
        if (dm.find()) {
            int mon = Integer.parseInt(dm.group(2));
            int dd = Integer.parseInt(dm.group(3));
            int year = dm.group(1) == null ? today.getYear() : Integer.parseInt(dm.group(1));
            try { day = LocalDate.of(year, mon, dd); } catch (Throwable ignored) { }
        }
        Matcher rm = REL.matcher(q);
        String rel = rm.find() ? rm.group(1) : null;
        if (day == null && rel != null) {
            if ("今天".equals(rel)) day = today;
            else if ("昨天".equals(rel)) day = today.minusDays(1);
            else if ("前天".equals(rel)) day = today.minusDays(2);
            else if ("大前天".equals(rel)) day = today.minusDays(3);
        }
        Matcher pm = PERIOD.matcher(q);
        String p = pm.find() ? pm.group(1) : null;
        if (day == null && p == null) return null;
        if (day == null) day = today;
        int h0 = 0, h1 = 24;
        if (p != null) {
            switch (p) {
                case "凌晨": h0 = 0; h1 = 6; break;
                case "早上": case "上午": h0 = 6; h1 = 12; break;
                case "中午": h0 = 11; h1 = 14; break;
                case "下午": h0 = 12; h1 = 18; break;
                case "傍晚": h0 = 17; h1 = 20; break;
                case "晚上": case "夜里": case "昨晚": case "今晚": h0 = 18; h1 = 24; break;
                default: break;
            }
        }
        long from = day.atTime(LocalTime.of(h0, 0)).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        long to = day.atTime(LocalTime.of(0, 0)).plusDays(1).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        if (h1 < 24) {
            to = day.atTime(LocalTime.of(h1, 0)).atZone(ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        }
        return new Range(from, to, day + " " + (p == null ? "全天" : p));
    }
}
