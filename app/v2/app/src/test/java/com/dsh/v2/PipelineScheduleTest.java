package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;

public class PipelineScheduleTest {

    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");

    @Test public void 凌晨时间未到_今天触发() {
        long now = Instant.parse("2026-11-17T17:00:00Z").toEpochMilli(); // 01:00 CST
        long next = PipelineSchedule.nextTriggerMillis(now, 2, 0, Z);
        assertEquals("2026-11-17T18:00:00Z", Instant.ofEpochMilli(next).toString());
    }

    @Test public void 时间已过_明天触发() {
        long now = Instant.parse("2026-11-16T20:00:00Z").toEpochMilli(); // 04:00 CST
        long next = PipelineSchedule.nextTriggerMillis(now, 2, 0, Z);
        assertEquals("2026-11-17T18:00:00Z", Instant.ofEpochMilli(next).toString());
    }

    @Test public void 恰好到点_顺延到明天() {
        long now = Instant.parse("2026-11-17T18:00:00Z").toEpochMilli();
        long next = PipelineSchedule.nextTriggerMillis(now, 2, 0, Z);
        assertEquals("2026-11-18T18:00:00Z", Instant.ofEpochMilli(next).toString());
    }

    @Test public void 描述今天明天() {
        long now = Instant.parse("2026-11-16T17:00:00Z").toEpochMilli(); // 11-17 01:00 CST
        long next = PipelineSchedule.nextTriggerMillis(now, 2, 0, Z);
        assertTrue(PipelineSchedule.describe(next, Z).contains("02:00"));
    }
}
