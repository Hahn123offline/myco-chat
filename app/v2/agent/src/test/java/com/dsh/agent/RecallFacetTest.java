package com.dsh.agent;

import static org.junit.Assert.*;

import com.dsh.memory.LineItem;
import com.dsh.memory.MemorySource;
import com.dsh.memory.RecallQuery;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecallFacetTest {

    private static MemorySource source() {
        return new MemorySource() {
            @Override public List<com.dsh.memory.MemItem> candidates(RecallQuery q) {
                return java.util.Collections.emptyList();
            }
            @Override public List<LineItem> lines(RecallQuery q) {
                LineItem a = new LineItem();
                a.time = 1_700_000_000_000L; a.objectKey = "com.tencent.mobileqq/零杯";
                a.pkg = "com.tencent.mobileqq"; a.text = "blender你如果搞过了就不用再搞了";
                a.blockId = 1902; a.shotRef = "shots/a.jpg"; a.kind = "CONTENT";
                LineItem b = new LineItem();
                b.time = 1_700_000_100_000L; b.objectKey = "com.tencent.mobileqq/零杯";
                b.pkg = "com.tencent.mobileqq"; b.text = "倒在自我验收的路上了";
                b.blockId = 1907; b.kind = "CONTENT";
                LineItem c = new LineItem();
                c.time = 1_700_000_200_000L; c.objectKey = "x";
                c.pkg = "com.tencent.mobileqq"; c.text = "发送";
                c.kind = "CHROME"; c.blockId = 3;
                return Arrays.asList(a, b, c);
            }
        };
    }

    private static ToolResult call(Map<String, Object> args) {
        return new RecallTool(source(), null).execute(args,
                new ToolContext(null, new HashMap<>()));
    }

    @Test public void object视图返回对象行() {
        Map<String, Object> a = new HashMap<>();
        a.put("object", "零杯");
        ToolResult r = call(a);
        assertTrue(r.isOk());
        assertTrue(r.summary.contains("blender"));
        assertTrue(r.summary.contains("block 1902"));
    }

    @Test public void query视图返回具体行和时间block() {
        Map<String, Object> a = new HashMap<>();
        a.put("query", "blender");
        ToolResult r = call(a);
        assertTrue(r.isOk());
        assertTrue(r.summary.contains("blender"));
        assertTrue(r.summary.contains("block 1902"));
        assertFalse(r.summary.contains("发送"));
    }

    @Test public void 时间段视图按对象聚合() {
        Map<String, Object> a = new HashMap<>();
        a.put("from", "2023-11-14");
        a.put("to", "2023-11-14");
        ToolResult r = call(a);
        assertTrue(r.isOk());
        assertTrue(r.summary.contains("【覆盖】"));
        assertTrue(r.summary.contains("com.tencent.mobileqq/零杯"));
        assertTrue(r.summary.contains("【完整】"));
    }

    @Test public void 稀疏记录并入大空段() {
        java.util.List<LineItem> xs = new java.util.ArrayList<>();
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 20; i++) xs.add(lineAt(base + i * 1000L, "正文" + i));
        xs.add(lineAt(base + 101 * 60_000L, "闲鱼：附近有人急出"));
        for (int i = 0; i < 20; i++) xs.add(lineAt(base + 202 * 60_000L + i * 1000L, "晚上正文" + i));
        String out = RecallTool.renderTimeView(xs, 50, 100, "2023-11-14 00:00", "2023-11-14 23:59");
        assertTrue(out, out.contains("孤立记录"));
        assertTrue(out, out.contains("已全部列出"));
    }

    private static LineItem lineAt(long t, String text) {
        LineItem l = new LineItem();
        l.time = t; l.text = text; l.kind = "CONTENT"; l.blockId = 1;
        l.objectKey = "x"; l.pkg = "com.x"; return l;
    }

    @Test public void includeChrome才返回状态栏行() {
        Map<String, Object> a = new HashMap<>();
        a.put("object", "零杯");
        a.put("include_chrome", true);
        ToolResult r = call(a);
        assertTrue(r.isOk());
        assertTrue(r.summary.contains("发送"));
    }
}
