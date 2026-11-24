package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 关闭条件/短命过滤/同页刷新/guard 集成，全部假时钟。 */
public class TraceAccumulatorTest {

    private final List<Trace> out = new ArrayList<>();

    private TraceAccumulator acc() {
        return new TraceAccumulator(out::add);
    }

    private TraceAccumulator acc(long timeout, long minDwell) {
        return new TraceAccumulator(timeout, minDwell, out::add);
    }

    @Test public void 多帧累积且相同文本去重() {
        TraceAccumulator a = new TraceAccumulator(15_000, 0, out::add);
        a.onEvent(1_000, "pkg", "Act", "标题", null, null);
        a.updateFrame(2_000, "第一屏", "shots/1.jpg", 1.0, "SCROLL");
        a.updateFrame(3_000, "第一屏", "shots/2.jpg", 1.0, "SCROLL");  // hash 相同
        a.updateFrame(4_000, "第二屏", "shots/3.jpg", 1.0, "SCROLL");
        a.flush(5_000);
        Trace t = out.get(0);
        assertEquals(2, t.frames.size());
        assertEquals("FIRST", t.frames.get(0).kind);
        assertEquals("第二屏", t.frames.get(1).text);
        assertEquals(2, t.nFrames);
        assertEquals("第二屏", t.text);   // 代表帧文本仍是最后一次 best
    }

    @Test public void 帧数上限20() {
        TraceAccumulator a = new TraceAccumulator(15_000, 0, out::add);
        a.onEvent(1_000, "pkg", "Act", "标题", null, null);
        for (int i = 0; i < 25; i++) a.updateFrame(2000 + i, "帧" + i, "shots/" + i + ".jpg", 1.0, "SCROLL");
        a.flush(5000);
        assertEquals(20, out.get(0).frames.size());
    }

    // ---------------- 关闭条件 ----------------

    private static String pid(String activity, String stableText) {
        return "act:" + activity + "#h8:"
                + SceneResolver.sha1(stableText == null ? "" : stableText, 16);
    }

    @Test public void 第一个事件只开场景不产出() {
        TraceAccumulator a = acc();
        a.onEvent(1_000, "pkg", "Act", "标题", null, null);
        assertNotNull(a.current());
        assertEquals(pid("Act", null), a.current().pageId);
        assertEquals("标题", a.current().pageTitle);
        assertTrue(out.isEmpty());
    }

    @Test public void pkg变化关闭上一条() {
        TraceAccumulator a = acc();
        a.onEvent(1_000, "pkgA", "Act", "标题A", null, null);
        a.updateContent(2_000, "正文A", "shots/a.jpg");
        a.onEvent(6_000, "pkgB", "Act", "标题B", null, null);

        assertEquals(1, out.size());
        Trace t = out.get(0);
        assertEquals("pkgA", t.pkg);
        assertEquals(6_000, t.t1);
        assertEquals(5_000, t.dwellMs);
        assertEquals("正文A", t.text);
        assertEquals("shots/a.jpg", t.shotRef);
    }

    @Test public void activity变化不再切块_只更新元数据() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "ActA", "标题", null, null);
        a.onEvent(3_000, "pkg", "ActB", "标题", null, null);
        assertTrue("activity 只是元数据，不能切块", out.isEmpty());
        assertEquals("ActB", a.current().activity);
        a.flush(5_000);
        assertEquals(1, out.size());
        assertEquals("ActB", out.get(0).activity);
        assertEquals(5_000, out.get(0).dwellMs);
    }

    @Test public void pageId变化不再切块_只更新元数据() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "页面1", "页面1", null);
        a.onEvent(4_000, "pkg", "Act", "页面2", "页面2", null);
        assertTrue("pageId 不再参与切块，不能关闭", out.isEmpty());
        assertEquals("同块内 pageId 更新为最新元数据", pid("Act", "页面2"), a.current().pageId);
        assertEquals(4_000, a.current().lastEventAt);
        a.flush(6_000);
        assertEquals(1, out.size());
        assertEquals(6_000, out.get(0).dwellMs);
        assertEquals(pid("Act", "页面2"), out.get(0).pageId);
    }

    @Test public void HSV切点关闭当前块并立即开新块() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", "内容", null);
        a.onVisualCut(5_000);
        assertEquals(1, out.size());
        assertEquals(5_000, out.get(0).dwellMs);
        assertNotNull("切点后立刻开新块，后续 OCR 不会丢", a.current());
        assertEquals("pkg", a.current().pkg);
        a.flush(9_000);
        assertEquals(2, out.size());
    }

    @Test public void 块内代表帧选Laplacian最清晰的一帧() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", "内容", null);
        a.updateContent(1_000, "模糊文本", "shot_blur", 10);
        a.updateContent(2_000, "清晰文本", "shot_sharp", 100);
        a.updateContent(3_000, "更糊文本", "shot_blur2", 5);
        a.flush(5_000);
        assertEquals(1, out.size());
        assertEquals("清晰文本", out.get(0).text);
        assertEquals("shot_sharp", out.get(0).shotRef);
    }

    @Test public void 超时15秒无事件关闭_t1为最后事件加15秒() {
        TraceAccumulator a = acc();
        a.onEvent(1_000, "pkg", "Act", "标题", null, null);
        a.onEvent(4_000, "pkg", "Act", "标题", null, null);   // 同一场景，刷新 lastEventAt
        a.updateContent(4_500, "正文", "shot.jpg");

        a.tick(18_999);   // 距最后事件 14_999 < 15_000
        assertTrue(out.isEmpty());
        a.tick(19_000);   // 正好 15_000
        assertEquals(1, out.size());
        assertEquals(19_000, out.get(0).t1);
        assertEquals(1_000, out.get(0).t0);
        assertEquals(18_000, out.get(0).dwellMs);
    }

    @Test public void tick未超时什么都不做() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.tick(10_000);
        assertTrue(out.isEmpty());
        assertNotNull(a.current());
    }

    @Test public void 关闭后无当前场景_后续tick无产出() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.tick(15_000);
        a.tick(100_000);
        assertEquals(1, out.size());
        assertNull(a.current());
    }

    // ---------------- 过滤 ----------------

    @Test public void dwell小于1秒直接丢弃() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkgA", "Act", "标题A", null, null);
        a.onEvent(999, "pkgB", "Act", "标题B", null, null);
        assertTrue(out.isEmpty());
        assertNotNull(a.current());

        a.onEvent(3_000, "pkgC", "Act", "标题C", null, null);
        assertEquals(1, out.size());
        assertEquals("pkgB", out.get(0).pkg);
        assertEquals(2_001, out.get(0).dwellMs);
    }

    @Test public void 超时关闭的短命场景也丢弃() {
        TraceAccumulator a = acc(15_000, 1_000);
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.tick(15_000);   // dwell 15_000，保留
        assertEquals(1, out.size());

        a.onEvent(20_000, "pkg", "Act", "标题", null, null);
        a.onEvent(20_100, "pkg2", "Act", "标题2", null, null);   // dwell 100 丢弃
        assertEquals(1, out.size());
    }

    @Test public void 自定义阈值生效() {
        TraceAccumulator a = acc(5_000, 2_000);
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.tick(4_000);
        assertTrue(out.isEmpty());
        a.tick(5_000);
        assertTrue("dwell 5s >= minDwell 2s", out.size() == 1);
    }

    // ---------------- 同页刷新 / guard ----------------

    @Test public void 同页事件不重置t0_文本取最新() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "旧文本", "shot1.jpg");
        a.onEvent(5_000, "pkg", "Act", "标题", null, null);
        a.updateContent(6_000, "新文本", "shot2.jpg");
        a.onEvent(8_000, "pkg2", "Act", "标题2", null, null);

        Trace t = out.get(0);
        assertEquals(0, t.t0);
        assertEquals(8_000, t.dwellMs);
        assertEquals("新文本", t.text);
        assertEquals("shot2.jpg", t.shotRef);
    }

    @Test public void 文本超过2000字截断() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3_000; i++) big.append('字');
        a.updateContent(500, big.toString(), null);
        a.flush(2_000);
        assertEquals(TraceAccumulator.MAX_TEXT_CHARS, out.get(0).text.length());
    }

    @Test public void MASK文本_guard为0_maskedText掩码_text保留原文() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "联系电话 13812345678", null);
        a.flush(2_000);
        Trace t = out.get(0);
        assertEquals(0, t.guard);
        assertNull(t.guardHit);
        assertEquals("联系电话 13812345678", t.text);
        assertEquals("联系电话 138****678", t.maskedText);
    }

    @Test public void BLOCK文本_guard为1_maskedText为null() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "卡号 4111111111111111", null);
        a.flush(2_000);
        Trace t = out.get(0);
        assertEquals(1, t.guard);
        assertEquals("bank_card", t.guardHit);
        assertNull(t.maskedText);
        assertEquals("卡号 4111111111111111", t.text);
    }

    @Test public void PASS文本_maskedText等于原文() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "普通屏幕内容", null);
        a.flush(2_000);
        assertEquals("普通屏幕内容", out.get(0).maskedText);
        assertEquals(0, out.get(0).guard);
    }

    @Test public void 场景已关闭后updateContent被忽略() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "第一段", null);
        a.flush(2_000);
        assertNull(a.current());
        a.updateContent(3_000, "迟到的OCR", null);
        assertEquals("第一段", out.get(0).text);
    }

    // ---------------- 结构性跳过（systemui/launcher/IME/自己） ----------------

    @Test public void systemui事件被忽略_由下一个内容事件关闭当前场景() {
        SkipPolicy skip = SkipPolicy.of("com.android.systemui", "com.dsh.v2");
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, skip, null, out::add);
        a.onEvent(0, "tv.danmaku.bili", "Act", "视频", null, null);
        a.updateContent(500, "正文", null);

        // systemui 的频繁 content-changed 不能把内容场景切碎/关闭
        a.onEvent(3_000, "com.android.systemui", "Act", "通知栏", null, null);
        a.onEvent(4_000, "com.android.systemui", "Act", "通知栏", null, null);
        assertTrue(out.isEmpty());
        assertEquals("tv.danmaku.bili", a.current().pkg);

        // 下一个真实内容事件（pkg 变化）正常关闭旧场景
        a.onEvent(6_000, "com.xingin.xhs", "Act", "笔记", null, null);
        assertEquals(1, out.size());
        assertEquals("tv.danmaku.bili", out.get(0).pkg);
        assertEquals(6_000, out.get(0).dwellMs);
        assertEquals("com.xingin.xhs", a.current().pkg);
    }

    @Test public void skipped事件完全不参与场景计算_只计数() {
        SkipPolicy skip = SkipPolicy.of("com.android.systemui");
        final int[] skipped = {0};
        TraceAccumulator.Observer obs = new TraceAccumulator.Observer() {
            @Override public void onSceneStart(String pkg, String pageId, long t0) { }
            @Override public void onSceneClose(Trace trace, String reason) { }
            @Override public void onSkippedEvent(String pkg, String activity) { skipped[0]++; }
        };
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, skip, null, out::add, obs);
        a.onEvent(0, "tv.danmaku.bili", "Act", "视频", "视频", null);
        a.updateContent(500, "正文", null);
        Scene before = a.current();
        long lastEventAtBefore = before.lastEventAt;
        long t0Before = before.t0;

        a.onEvent(5_000, "com.android.systemui", "Act", "通知栏", null, null);
        a.onEvent(7_000, "com.android.systemui", "Act", "通知栏", null, null);

        assertEquals("skip 必须只计数", 2, skipped[0]);
        assertEquals("skip 不能更新 lastEventAt", lastEventAtBefore, a.current().lastEventAt);
        assertEquals("skip 不能改 t0", t0Before, a.current().t0);
        assertEquals("正文不能被 skip 改", "正文", a.current().text);

        // 关闭时 dwell 只按真实内容事件计算（0 → 9_000），未被 systemui 事件影响
        a.onEvent(9_000, "com.xingin.xhs", "Act", "笔记", "笔记", null);
        assertEquals(1, out.size());
        assertEquals(9_000, out.get(0).dwellMs);
        assertEquals(pid("Act", "视频"), out.get(0).pageId);
    }

    @Test public void title只是字段_变化不影响pageId身份() {
        TraceAccumulator a = acc();
        a.onEvent(0, "pkg", "Act", "推荐", "固定内容文本", null);
        String id = a.current().pageId;
        a.onEvent(2_000, "pkg", "Act", "热点", "固定内容文本", null);
        assertEquals("title 变化不能改身份", id, a.current().pageId);
        assertEquals("热点", a.current().pageTitle);

        a.onEvent(4_000, "pkg", "Act", "", "固定内容文本", null);
        assertEquals("title 消失时保留上一次，不触发 close", "热点", a.current().pageTitle);
        a.flush(6_000);
        assertEquals(1, out.size());
        assertEquals(id, out.get(0).pageId);
        assertEquals("热点", out.get(0).pageTitle);
    }

    @Test public void 只有systemui事件时tick仍会关闭之前的场景() {
        SkipPolicy skip = SkipPolicy.of("com.android.systemui");        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, skip, null, out::add);
        a.onEvent(0, "tv.danmaku.bili", "Act", "视频", null, null);
        a.onEvent(5_000, "com.android.systemui", "Act", "通知栏", null, null);
        a.tick(20_000);   // 最后内容事件 0 + 15s
        assertEquals(1, out.size());
        assertEquals(15_000, out.get(0).t1);
    }

    @Test public void 跳过包名不会开启新场景() {
        SkipPolicy skip = SkipPolicy.of("com.android.systemui", "com.miui.home");
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, skip, null, out::add);
        a.onEvent(0, "com.android.systemui", "Act", "下拉", null, null);
        a.onEvent(2_000, "com.miui.home", "Act", "桌面", null, null);
        assertNull(a.current());
        assertTrue(out.isEmpty());
    }

    @Test public void 系统设置不被跳过() {
        SkipPolicy skip = SkipPolicy.of("com.android.systemui");
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, skip, null, out::add);
        a.onEvent(0, "com.android.settings", "Settings", "Wi-Fi", null, null);
        a.flush(2_000);
        assertEquals(1, out.size());
        assertEquals("com.android.settings", out.get(0).pkg);
    }

    // ---------------- 关闭时补 OCR ----------------

    @Test public void 关闭时text为空且有shot_ref_同步补OCR() {
        final String[] called = new String[1];
        TraceAccumulator.TextResolver resolver = (shotRef, maxWait) -> {
            called[0] = shotRef + "@" + maxWait;
            return "补采的正文";
        };
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000,
                SkipPolicy.none(), resolver, out::add);
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "", "shots/x.jpg");
        a.flush(2_000);
        assertEquals("shots/x.jpg@800", called[0]);
        assertEquals("补采的正文", out.get(0).text);
        assertEquals(0, out.get(0).guard);
    }

    @Test public void text非空时不补OCR() {
        final boolean[] called = {false};
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, SkipPolicy.none(),
                (shotRef, maxWait) -> { called[0] = true; return "不该用"; }, out::add);
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "已有正文", "shots/x.jpg");
        a.flush(2_000);
        assertTrue(!called[0]);
        assertEquals("已有正文", out.get(0).text);
    }

    @Test public void 没有shot_ref时不补OCR_也不崩() {
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, SkipPolicy.none(),
                (shotRef, maxWait) -> { throw new IllegalStateException("不该被调用"); }, out::add);
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.flush(2_000);
        assertEquals("", out.get(0).text);
    }

    @Test public void 补OCR抛异常不影响落盘() {
        TraceAccumulator a = new TraceAccumulator(15_000, 1_000, SkipPolicy.none(),
                (shotRef, maxWait) -> { throw new IllegalStateException("OCR boom"); }, out::add);
        a.onEvent(0, "pkg", "Act", "标题", null, null);
        a.updateContent(500, "", "shots/x.jpg");
        a.flush(2_000);
        assertEquals(1, out.size());
        assertEquals("", out.get(0).text);
    }
}
