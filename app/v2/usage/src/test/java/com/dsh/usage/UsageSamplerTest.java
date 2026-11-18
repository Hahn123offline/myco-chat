package com.dsh.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** UsageSampler：前台/后台切换 → 连续区间；跨周期保持 open；短命丢弃。 */
public class UsageSamplerTest {

    private static UsageEvent fg(String pkg, long ts) { return new UsageEvent(pkg, ts, true); }
    private static UsageEvent bg(String pkg, long ts) { return new UsageEvent(pkg, ts, false); }

    @Test public void 前台切app闭合上一段() {
        UsageSampler s = new UsageSampler();
        List<UsageSampler.Span> out = s.onEvents(20_000, Arrays.asList(
                fg("com.tencent.mm", 1_000),
                fg("tv.danmaku.bili", 11_000)));
        assertEquals(1, out.size());
        assertEquals("com.tencent.mm", out.get(0).pkg);
        assertEquals(1_000, out.get(0).t0);
        assertEquals(11_000, out.get(0).t1);
        assertEquals("tv.danmaku.bili", s.openPkg());
        assertEquals(11_000, s.openSince());
    }

    @Test public void 退到后台闭合当前段() {
        UsageSampler s = new UsageSampler();
        List<UsageSampler.Span> out = s.onEvents(20_000, Arrays.asList(
                fg("tv.danmaku.bili", 1_000),
                bg("tv.danmaku.bili", 8_000)));
        assertEquals(1, out.size());
        assertEquals(7_000, out.get(0).dwellMs());
        assertNull(s.openPkg());
    }

    @Test public void 同一app重复前台事件不重复开段() {
        UsageSampler s = new UsageSampler();
        s.onEvents(10_000, Arrays.asList(fg("com.tencent.mm", 1_000)));
        s.onEvents(20_000, Arrays.asList(
                fg("com.tencent.mm", 15_000),
                fg("com.tencent.mm", 16_000)));
        assertEquals("com.tencent.mm", s.openPkg());
        assertEquals("open 时间不应被后续前台事件重置", 1_000, s.openSince());
    }

    @Test public void 短命区间丢弃() {
        UsageSampler s = new UsageSampler(1_000);
        List<UsageSampler.Span> out = s.onEvents(20_000, Arrays.asList(
                fg("a", 1_000),
                fg("b", 1_500),     // a 只有 500ms
                fg("c", 10_000)));  // b 有 8500ms
        assertEquals(1, out.size());
        assertEquals("b", out.get(0).pkg);
    }

    @Test public void flush闭合open区间() {
        UsageSampler s = new UsageSampler();
        s.onEvents(10_000, Arrays.asList(fg("tv.danmaku.bili", 1_000)));
        List<UsageSampler.Span> out = s.flush(9_000);
        assertEquals(1, out.size());
        assertEquals(1_000, out.get(0).t0);
        assertEquals(9_000, out.get(0).t1);
        assertNull(s.openPkg());
    }

    @Test public void 忽略未来事件与空包名() {
        UsageSampler s = new UsageSampler();
        List<UsageSampler.Span> out = s.onEvents(5_000, Arrays.asList(
                fg("", 1_000),
                fg("a", 1_000),
                fg("b", 99_000)));   // 未来事件，忽略
        assertTrue(out.isEmpty());
        assertEquals("a", s.openPkg());
    }

    @Test public void 没有open时后台事件不产生区间() {
        UsageSampler s = new UsageSampler();
        assertTrue(s.onEvents(10_000, Arrays.asList(bg("a", 5_000))).isEmpty());
    }
}
