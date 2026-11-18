package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GapFinderTest {

    private static final long M = 60_000L;
    private static final long H = 3_600_000L;

    private static long at(int h, int min) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(2026, java.util.Calendar.NOVEMBER, 17, h, min, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Test public void 无缺口() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(10, 10), at(11, 0)));
        assertTrue(GapFinder.find(spans, new ArrayList<>(), at(8, 0), at(12, 0)).isEmpty());
    }

    @Test public void 单缺口超20分钟() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(11, 0), at(12, 0)));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, new ArrayList<>(), at(8, 0), at(13, 0));
        assertEquals(1, gaps.size());
        assertEquals("no_record", gaps.get(0).reason);
        assertTrue(gaps.get(0).text.contains("没有记录，我不知道为什么"));
    }

    @Test public void 已知关闭缺口文案() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(11, 0), at(12, 0)));
        List<GapFinder.Known> known = Arrays.asList(
                new GapFinder.Known(at(10, 0), at(11, 0), "recording_off"));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, known, at(8, 0), at(13, 0));
        assertEquals("recording_off", gaps.get(0).reason);
        assertTrue(gaps.get(0).text.contains("你把我关了"));
    }

    @Test public void 未闭合区间用日终() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)));
        List<GapFinder.Known> known = Arrays.asList(
                new GapFinder.Known(at(10, 0), 0, "recording_off"));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, known, at(8, 0), at(12, 0));
        assertEquals(1, gaps.size());
        assertEquals("recording_off", gaps.get(0).reason);
    }

    @Test public void 贴边无文本trace不改判() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(11, 0), at(12, 0)));
        // 这条 app_only 只贴在缺口右边缘，不能把 10:00–11:00 改判
        List<GapFinder.Known> known = Arrays.asList(
                new GapFinder.Known(at(11, 0), at(11, 0), "app_only"));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, known, at(8, 0), at(13, 0));
        assertEquals(1, gaps.size());
        assertEquals("no_record", gaps.get(0).reason);
    }

    @Test public void presence连续但content缺30分钟() {
        List<GapFinder.Span> presenceAll = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(12, 0)));
        List<GapFinder.Span> content = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(10, 30), at(12, 0)));
        List<GapFinder.Gap> gaps = GapFinder.find(presenceAll, content,
                new ArrayList<>(), at(8, 0), at(13, 0));
        assertEquals(1, gaps.size());
        assertEquals("app_only", gaps.get(0).reason);
        assertTrue(gaps.get(0).text.contains("只有应用记录"));
    }

    @Test public void 只有应用记录被标为app_only() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(11, 0), at(12, 0)));
        List<GapFinder.Known> known = Arrays.asList(
                new GapFinder.Known(at(10, 30), at(10, 30), "app_only"));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, known, at(8, 0), at(13, 0));
        assertEquals(1, gaps.size());
        assertEquals("app_only", gaps.get(0).reason);
        assertTrue(gaps.get(0).text.contains("只有应用记录"));
    }

    @Test public void app_only缺口() {
        List<GapFinder.Span> presence = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(12, 0)));
        List<GapFinder.Span> content = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)));
        List<GapFinder.Gap> gaps = GapFinder.find(presence, content,
                new ArrayList<>(), at(8, 0), at(13, 0));
        assertEquals(1, gaps.size());
        assertEquals("app_only", gaps.get(0).reason);
        assertTrue(gaps.get(0).text.contains("只有应用记录，没有画面"));
    }

    @Test public void 两种reason混合() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(11, 0), at(12, 0)),
                new GapFinder.Span(at(13, 0), at(14, 0)));
        List<GapFinder.Known> known = Arrays.asList(
                new GapFinder.Known(at(10, 0), at(11, 0), "recording_off"),
                new GapFinder.Known(at(12, 0), at(13, 0), "unknown"));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, known, at(8, 0), at(15, 0));
        assertEquals(2, gaps.size());
        assertEquals("recording_off", gaps.get(0).reason);
        assertEquals("no_record", gaps.get(1).reason);
    }

    @Test public void 跨天边界被裁掉() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(0, 30), at(2, 0)));
        List<GapFinder.Known> known = Arrays.asList(
                new GapFinder.Known(at(0, 0), 0, "recording_off"));
        List<GapFinder.Gap> gaps = GapFinder.find(spans, known,
                at(0, 0), at(3, 0));
        assertEquals(1, gaps.size());
        assertTrue(gaps.get(0).text.startsWith("00:00"));
    }

    @Test public void 短于20分钟不报() {
        List<GapFinder.Span> spans = Arrays.asList(
                new GapFinder.Span(at(9, 0), at(10, 0)),
                new GapFinder.Span(at(10, 15), at(11, 0)));
        assertTrue(GapFinder.find(spans, new ArrayList<>(), at(8, 0), at(12, 0)).isEmpty());
    }
}
