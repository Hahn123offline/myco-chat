package com.dsh.memory;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DayDiagnosticTest {

    private static Map<String, Integer> pkgs(String k, int n) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(k, n);
        return m;
    }

    @Test public void 无痕迹说完全没有记录() {
        String s = new DayDiagnostic("2026-11-18", 0, 0, null, 0, 0).render("B站");
        assertTrue(s.contains("没有采集到任何记录"));
        assertFalse(s.contains("没有打开过"));
    }

    @Test public void 有记录但没有目标应用() {
        String s = new DayDiagnostic("2026-11-18", 100, 3_200_000,
                pkgs("com.tencent.mm", 100), 2, 1).render("B站");
        assertTrue(s.contains("没有打开过 B站"));
        assertFalse(s.contains("没有采集到任何记录"));
    }

    @Test public void 目标应用有记录但没匹配() {
        String s = new DayDiagnostic("2026-11-17", 918, 5_000_000,
                pkgs("tv.danmaku.bili", 37), 9, 3).render("B站");
        assertTrue(s.contains("B站 那天有 37 条记录"));
        assertTrue(s.contains("没有匹配"));
        assertFalse(s.contains("没有采集到任何记录"));
    }
}
