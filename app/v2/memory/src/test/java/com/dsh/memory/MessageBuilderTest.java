package com.dsh.memory;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MessageBuilderTest {

    private static LineItem line(String text, int y, long t, long frame) {
        LineItem l = new LineItem();
        l.objectKey = "qq/零杯"; l.pkg = "com.tencent.mobileqq";
        l.text = text; l.yTop = y; l.time = t; l.blockId = 1; l.frameId = frame;
        l.kind = "CONTENT"; l.shotRef = "s1"; l.left = 200; l.right = 900; return l;
    }

    @Test public void 同消息跨帧去重并累加seen() {
        List<LineItem> xs = new ArrayList<>();
        xs.add(line("周子涵要不多指导几句", 100, 1000, 1));
        xs.add(line("周子涵要不多指导几句", 100, 2000, 2));
        xs.add(line("周子涵要不多指导几句", 100, 3000, 3));
        List<MessageBuilder.Msg> out = MessageBuilder.build(xs);
        assertEquals(1, out.size());
        assertEquals(3, out.get(0).seenCount);
        assertEquals(3000, out.get(0).tLast);
    }

    @Test public void y邻近合并为一条且sender来自A11Y锚点() {
        List<LineItem> xs = new ArrayList<>();
        xs.add(line("第一行", 140, 1000, 1));
        xs.add(line("第二行", 180, 1000, 1));
        MessageBuilder.Anchor a = new MessageBuilder.Anchor();
        a.text = "群主"; a.left = 180; a.top = 80; a.right = 260; a.bottom = 120;
        java.util.Map<String, List<MessageBuilder.Anchor>> anchors = new java.util.HashMap<>();
        anchors.put("s1", java.util.Arrays.asList(a));
        List<MessageBuilder.Msg> out = MessageBuilder.build(xs, anchors);
        assertEquals(1, out.size());
        assertTrue(out.get(0).text.contains("第一行"));
        assertTrue(out.get(0).text.contains("第二行"));
        assertEquals("群主", out.get(0).sender);
    }

    @Test public void OCR变体相似度合并() {
        List<LineItem> xs = new ArrayList<>();
        xs.add(line("下载学校的免费AU😋", 100, 1000, 1));
        xs.add(line("下载学校的免费AU😋！", 100, 2000, 2));
        assertEquals(1, MessageBuilder.build(xs).size());
    }

    @Test public void chrome不进入消息() {
        List<LineItem> xs = new ArrayList<>();
        LineItem c = line("发送", 100, 1000, 1); c.kind = "CHROME";
        xs.add(c);
        assertEquals(0, MessageBuilder.build(xs).size());
    }
}
