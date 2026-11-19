package com.dsh.memory;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LineSearchTest {

    private static LineItem line(long t, String ok, String text, long block) {
        LineItem l = new LineItem();
        l.time = t; l.objectKey = ok; l.text = text; l.blockId = block; l.pkg = "com.tencent.mobileqq";
        return l;
    }

    @Test public void 行级BM25命中且带block指针() {
        List<LineItem> lines = Arrays.asList(
                line(1_700_000_000_000L, "QQ/零杯", "blender你如果搞过了就不用再搞了", 1902),
                line(1_700_000_100_000L, "QQ/零杯", "倒在自我验收的路上了", 1907),
                line(1_700_000_200_000L, "QQ/课程", "今天作业", 1908));
        List<LineItem> hits = LineSearch.search(lines, "blender", 8);
        assertEquals(1, hits.size());
        assertEquals(1902, hits.get(0).blockId);
        assertEquals("QQ/零杯", hits.get(0).objectKey);
    }

    @Test public void 中文bigram检索() {
        List<LineItem> lines = Arrays.asList(
                line(1L, "QQ/零杯", "倒在自我验收的路上了", 1),
                line(2L, "QQ/课程", "布儒斯特角公式", 2));
        List<LineItem> hits = LineSearch.search(lines, "验收", 8);
        assertEquals(1, hits.size());
        assertEquals(1, hits.get(0).blockId);
    }
}
