package com.dsh.memory;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class QueryTimeTest {

    private static long ms(int y, int m, int d, int h) {
        return LocalDateTime.of(y, m, d, h, 0).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
    }

    @Test public void 月日加下午解析为时间窗() {
        QueryTime.Range r = QueryTime.parse("11月18号下午我在干嘛",
                LocalDate.of(2026, 11, 19));
        assertNotNull(r);
        assertEquals(ms(2026, 11, 18, 12), r.from);
        assertEquals(ms(2026, 11, 18, 18), r.to);
    }

    @Test public void 昨天上午解析() {
        QueryTime.Range r = QueryTime.parse("昨天上午", LocalDate.of(2026, 11, 19));
        assertNotNull(r);
        assertEquals(ms(2026, 11, 18, 6), r.from);
        assertEquals(ms(2026, 11, 18, 12), r.to);
    }

    @Test public void 纯内容query不解析时间() {
        assertNull(QueryTime.parse("blender", LocalDate.of(2026, 11, 19)));
    }
}
