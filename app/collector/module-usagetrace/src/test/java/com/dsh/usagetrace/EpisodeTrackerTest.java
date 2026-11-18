package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.data.Episode;
import com.dsh.host.api.events.EpisodeClosed;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EpisodeTrackerTest {

    private static final class Capture implements EpisodeTracker.Sink {
        final List<Episode> opened = new ArrayList<>();
        final List<Episode> closed = new ArrayList<>();
        final List<EpisodeClosed> events = new ArrayList<>();
        int events2 = 0;

        @Override public void onOpen(Episode e) { opened.add(e); }
        @Override public void onClose(Episode e) { closed.add(e); }
        @Override public void onEvent(EpisodeClosed ev) { events.add(ev); events2++; }

        EpisodeClosed lastEvent() { return events.isEmpty() ? null : events.get(events.size() - 1); }
    }

    private Scene scene(String pkg, String act, String title, String src) {
        return SceneResolver.resolve(pkg, act, title, null, null);
    }

    @Test public void 同一页多次采样只开一段() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        Scene s = scene("com.tencent.mm", "X", "微信", "node");
        t.onSample(s, 1000, "f1", null, "t", "event");
        t.onSample(s, 2000, "f2", null, "t", "event");
        t.onSample(s, 3000, "f3", null, "t", "event");
        assertEquals(1, c.opened.size());
        assertEquals(3, c.opened.get(0).framesCount);
    }

    @Test public void 换页会关旧开新() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "X", "微信", "node"), 1000, null, null, "", "event");
        t.onSample(scene("com.tencent.mm", "Y", "深港驾校小米", "node"), 5000, null, null, "", "event");
        assertEquals(2, c.opened.size());
        assertEquals(1, c.closed.size());
        assertEquals(4000, c.closed.get(0).dwellMs);
    }

    @Test public void 证据升级_先无标题后有标题() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "", "", "none"), 1000, null, null, "", "tick");
        t.onSample(scene("com.tencent.mm", "", "", "none"), 2000, null, null, "", "tick");
        // OCR 回来了：同应用、同 activity、一方无标题 → 视为同一页
        Scene withTitle = SceneResolver.resolve("com.tencent.mm", "", "", "深港驾校小米", null);
        t.onSample(withTitle, 2500, "frame", "tb", "", "tick");
        assertEquals(1, c.opened.size());
        assertEquals("深港驾校小米", t.current().title);
        assertEquals("ocr", t.current().titleSrc);
    }

    @Test public void 太短的段不落库() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "X", "微信", "node"), 1000, null, null, "", "event");
        t.onSample(scene("com.tencent.mm", "Y", "别的", "node"), 1200, null, null, "", "event");
        assertEquals(0, c.closed.size());
        assertEquals(0, c.events2);
    }

    @Test public void 安静太久自动收尾() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "X", "微信", "node"), 1000, null, null, "", "event");
        t.idleCheck(2000, 15000);
        assertEquals(0, c.closed.size());
        t.idleCheck(20000, 15000);
        assertEquals(1, c.closed.size());
        assertTrue(c.events2 == 1);
        assertNull(t.current());
    }

    @Test public void 收尾时落定时长与结束时间() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "X", "微信", "node"), 1000, null, null, "", "event");
        t.idleCheck(20000, 15000);
        Episode e = c.closed.get(0);
        assertEquals(20000, e.tEnd);
        assertEquals(19000, e.dwellMs);
        assertNotNull(e.sceneKey);
    }

    @Test public void 关闭时由生产者生成不透明证据引用() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "X", "微信", "node"), 1000, null, null, "", "event");
        // 生产环境里 id 由 EpisodeStore.insertOpen 落库后回填
        t.current().id = 7;
        t.idleCheck(20000, 15000);
        assertEquals(1, c.events2);
        assertEquals("trace:7", c.lastEvent().evidenceRef);
    }

    @Test public void 未落库的段不编造证据引用() {
        Capture c = new Capture();
        EpisodeTracker t = new EpisodeTracker(c, 800);
        t.onSample(scene("com.tencent.mm", "X", "微信", "node"), 1000, null, null, "", "event");
        // id 仍为 0（store 没写成功）→ 不给引用
        t.idleCheck(20000, 15000);
        assertNull(c.lastEvent().evidenceRef);
    }

    @Test public void 证据引用只是token_不带路径语义() {
        assertEquals("trace:42", EvidenceRef.forEpisode(42));
        assertEquals(null, EvidenceRef.forEpisode(0));
        assertEquals(null, EvidenceRef.forEpisode(-1));
        // 不接受任何路径分隔符语义：token 里只有 scheme + 数字
        assertTrue(EvidenceRef.forEpisode(42).matches("^trace:\\d+$"));
    }
}
