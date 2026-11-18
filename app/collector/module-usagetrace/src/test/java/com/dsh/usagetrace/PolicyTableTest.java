package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.Config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PolicyTableTest {

    private static final class FakeConfig implements Config {
        final Map<String, String> m = new HashMap<>();
        @Override public String getString(String k, String d) { return m.getOrDefault(k, d); }
        @Override public int getInt(String k, int d) { return d; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { return d; }
        @Override public boolean has(String k) { return m.containsKey(k); }
    }

    @Test public void 内置默认里微信是定时加OCR() {
        CapturePolicy p = PolicyTable.defaults().forPkg("com.tencent.mm");
        assertEquals(CapturePolicy.Mode.TICK, p.mode);
        assertEquals(10_000, p.tickMs);
        assertTrue(p.ocr);
    }

    @Test public void 内置默认里浏览器是事件驱动() {
        CapturePolicy p = PolicyTable.defaults().forPkg("com.android.browser");
        assertEquals(CapturePolicy.Mode.EVENT, p.mode);
        assertFalse(p.ocr);
    }

    @Test public void 未知应用走默认策略() {
        CapturePolicy p = PolicyTable.defaults().forPkg("com.unknown.app");
        assertEquals(CapturePolicy.Mode.EVENT, p.mode);
        assertFalse(p.ocr);
    }

    @Test public void 从配置解析出定时与OCR() {
        FakeConfig c = new FakeConfig();
        c.m.put("policy", "com.tencent.mm=tick:10000:ocr:0.25;com.foo=off");
        PolicyTable t = PolicyTable.fromConfig(c, CapturePolicy.def("*"));

        CapturePolicy wx = t.forPkg("com.tencent.mm");
        assertEquals(CapturePolicy.Mode.TICK, wx.mode);
        assertEquals(10_000, wx.tickMs);
        assertTrue(wx.ocr);
        assertEquals(0.25f, wx.titleRegion, 0.001);

        assertEquals(CapturePolicy.Mode.OFF, t.forPkg("com.foo").mode);
    }

    @Test public void 坏配置行被忽略而不是崩溃() {
        FakeConfig c = new FakeConfig();
        c.m.put("policy", "没有等号;=空包名;com.x=NOT_A_MODE;com.y=event");
        PolicyTable t = PolicyTable.fromConfig(c, CapturePolicy.def("*"));
        // 只有 com.y 应该生效
        assertEquals(CapturePolicy.Mode.EVENT, t.forPkg("com.y").mode);
        assertEquals(1, t.size());
    }

    @Test public void 空配置不报错() {
        PolicyTable t = PolicyTable.fromConfig(new FakeConfig(), CapturePolicy.def("*"));
        assertEquals(0, t.size());
    }

    @Test public void 标题区域可以按应用覆盖() {
        FakeConfig c = new FakeConfig();
        c.m.put("policy", "tv.danmaku.bili=event:ocr:0.45");
        PolicyTable t = PolicyTable.fromConfig(c, CapturePolicy.def("*"));
        CapturePolicy p = t.forPkg("tv.danmaku.bili");
        assertEquals(0.45f, p.titleRegion, 0.001);
        assertTrue(p.ocr);
    }
}
