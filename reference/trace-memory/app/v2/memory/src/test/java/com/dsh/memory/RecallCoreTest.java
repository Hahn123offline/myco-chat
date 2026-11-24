package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecallCoreTest {

    private static MemItem item(long id, DetailLevel lvl, String text, String ns) {
        MemItem m = new MemItem(ns, Kind.BLOCK, "k" + id, text, "{\"pkg\":\"com.x\"}");
        m.id = id;
        m.detailLevel = lvl;
        m.t0 = System.currentTimeMillis();
        m.t1 = m.t0;
        m.weight = 1.0;
        m.evidence.add(new Evidence("trace", "1", m.t0));
        return m;
    }

    private static MemorySource source(List<MemItem> items) {
        return q -> items;
    }

    @Test public void 负例词面覆盖低_由模型判否() {
        List<MemItem> items = Arrays.asList(item(1, DetailLevel.FULL, "概率论第5题", "trace"));
        List<RecallHit> hits = RecallCore.recall(source(items),
                new RecallQuery().query("量子计算机的股票代码"));
        for (RecallHit h : hits) assertTrue(h.coverage < 0.3);
    }

    @Test public void app_only永不返回() {
        MemItem app = item(2, DetailLevel.APP_ONLY, "代理报错", "trace");
        List<RecallHit> hits = RecallCore.recall(source(Collections.singletonList(app)),
                new RecallQuery().query("代理报错"));
        assertTrue(hits.isEmpty());
    }

    @Test public void image_only文案() {
        List<RecallHit> hits = RecallCore.recall(source(Collections.singletonList(
                        item(3, DetailLevel.IMAGE_ONLY, "", "trace"))),
                new RecallQuery().query("").from("2000-01-01").to("2100-01-01"));
        assertEquals(1, hits.size());
        assertTrue(RecallCore.render(hits).contains("只有截图，没有识别到文字"));
    }

    @Test public void present文案两种() {
        MemItem trace = item(4, DetailLevel.FULL, "今天看了视频", "trace");
        MemItem manual = item(5, DetailLevel.FULL, "记住这个", "manual");
        manual.evidence = new ArrayList<>();
        manual.evidence.add(new Evidence("manual", "tile", manual.t0));
        List<RecallHit> hits = RecallCore.recall(
                source(Arrays.asList(trace, manual)),
                new RecallQuery().query("").from("2000-01-01").to("2100-01-01"));
        String text = RecallCore.render(hits);
        assertTrue(text.contains("我翻记录看到的，那会儿我没在看"));
        assertTrue(text.contains("你当时特意让我记住这个"));
    }

    @Test public void day级别() {
        MemorySource src = new MemorySource() {
            @Override public List<MemItem> candidates(RecallQuery q) { return Collections.emptyList(); }
            @Override public DayRecall day(String day) {
                return new DayRecall(day, "日叙事", "[main]", "[]");
            }
        };
        DayRecall d = RecallCore.day(src, "2026-11-17");
        String s = RecallCore.renderDay(d);
        assertTrue(s.contains("日叙事"));
        assertTrue(s.contains("main"));
    }
    @Test public void event块gap大于20分钟时渲染附注() {
        long base = System.currentTimeMillis();
        MemItem m = item(9, DetailLevel.FULL, "排查代理", "event");
        m.t0 = base;
        m.t1 = base + 60 * 60_000L;
        m.data = "{\"covered_ms\":600000}";
        List<RecallHit> hits = RecallCore.recall(source(Collections.singletonList(m)),
                new RecallQuery().query("").from("2000-01-01").to("2100-01-01"));
        String text = RecallCore.render(hits);
        assertTrue(text.contains("分钟无记录"));
    }

}
