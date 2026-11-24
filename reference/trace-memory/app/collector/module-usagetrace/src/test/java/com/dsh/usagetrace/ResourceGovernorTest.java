package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResourceGovernorTest {

    @Test public void 截图有最小间隔() {
        ResourceGovernor g = new ResourceGovernor(8_000, 10_000, 100);
        long t = 1_000_000L;
        assertTrue(g.allowShot(t));
        g.recordShot(t);
        assertFalse(g.allowShot(t + 1_000));
        assertTrue(g.allowShot(t + 8_000));
    }

    @Test public void OCR有最小间隔() {
        ResourceGovernor g = new ResourceGovernor(8_000, 10_000, 100);
        long t = 1_000_000L;
        assertTrue(g.allowOcr(t));
        g.recordOcr(t);
        assertFalse(g.allowOcr(t + 5_000));
        assertTrue(g.allowOcr(t + 10_000));
    }

    @Test public void 并发多次调用时计数正确() {
        ResourceGovernor g = new ResourceGovernor(1, 1, 3);
        long t = 1_000_000L;
        for (int i = 0; i < 3; i++) {
            assertTrue(g.allowShot(t + i * 10));
            g.recordShot(t + i * 10);
        }
        assertEquals(3, g.shotsToday());
        // 超出单日上限
        assertFalse(g.allowShot(t + 1000));
    }

    @Test public void 跨天自动清零() {
        ResourceGovernor g = new ResourceGovernor(1, 1, 2);
        long day1 = 1_000_000L;
        g.recordShot(day1);
        g.recordOcr(day1);
        assertEquals(1, g.shotsToday());

        long day2 = day1 + 24L * 3600 * 1000;
        // 触发跨天滚动
        g.allowShot(day2);
        assertEquals(0, g.shotsToday());
    }

    @Test public void 截图与OCR互不影响() {
        ResourceGovernor g = new ResourceGovernor(8_000, 10_000, 100);
        long t = 1_000_000L;
        g.recordShot(t);
        assertTrue(g.allowOcr(t));     // 截图不占用 OCR 窗口
        g.recordOcr(t);
        assertFalse(g.allowOcr(t + 1_000));
        assertEquals(1, g.shotsToday());
        assertEquals(1, g.ocrToday());
    }
}
