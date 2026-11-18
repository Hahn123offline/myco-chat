package com.dsh.v2;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** 闹钟时间计算（纯逻辑，可 JVM 测试）。 */
public final class PipelineSchedule {

    public static final int DEFAULT_HOUR = 2;
    public static final int DEFAULT_MINUTE = 0;

    private PipelineSchedule() { }

    /** 下一次触发（严格在 now 之后）；每天 hour:minute。 */
    public static long nextTriggerMillis(long nowMillis, int hour, int minute, ZoneId zone) {
        Instant now = Instant.ofEpochMilli(nowMillis);
        LocalDate date = now.atZone(zone).toLocalDate();
        LocalTime time = LocalTime.of(Math.max(0, Math.min(23, hour)),
                Math.max(0, Math.min(59, minute)));
        LocalDateTime next = LocalDateTime.of(date, time);
        if (!next.atZone(zone).toInstant().isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.atZone(zone).toInstant().toEpochMilli();
    }

    public static String describe(long nextMillis, ZoneId zone) {
        if (nextMillis <= 0) return "未设置";
        LocalDateTime t = Instant.ofEpochMilli(nextMillis).atZone(zone).toLocalDateTime();
        LocalDate today = LocalDate.now(zone);
        String day;
        if (t.toLocalDate().equals(today)) day = "今天";
        else if (t.toLocalDate().equals(today.plusDays(1))) day = "明天";
        else day = t.toLocalDate().toString();
        return day + String.format(" %02d:%02d", t.getHour(), t.getMinute());
    }
}
