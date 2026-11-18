package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneResolverTest {

    private static final String WX = "com.tencent.mm";

    @Test public void 节点标题最可信() {
        Scene s = SceneResolver.resolve(WX, WX + ".ui.LauncherUI", "文件传输助手", null, null);
        assertEquals("node", s.titleSrc);
        assertEquals(0.95f, s.conf, 0.001);
        assertEquals("文件传输助手", s.title);
    }

    @Test public void 没有节点标题时用OCR() {
        Scene s = SceneResolver.resolve(WX, "", "", "深港驾校小米", null);
        assertEquals("ocr", s.titleSrc);
        assertEquals(0.70f, s.conf, 0.001);
        assertEquals(WX + "|O|深港驾校小米", s.key);
    }

    @Test public void 只有activity时置信度更低() {
        Scene s = SceneResolver.resolve(WX, WX + ".ui.LauncherUI", "", "", null);
        assertEquals("none", s.titleSrc);
        assertEquals(0.50f, s.conf, 0.001);
    }

    @Test public void 都不行时退到画面哈希() {
        Scene s = SceneResolver.resolve(WX, "", "", "", "abc123");
        assertEquals(0.30f, s.conf, 0.001);
        assertTrue(s.key.contains("abc123"));
    }

    @Test public void 丢掉上一个应用残留的activity() {
        Scene s = SceneResolver.resolve("com.android.browser",
                "com.tencent.mm.ui.LauncherUI", "", "", null);
        assertEquals("", s.activity);
    }

    @Test public void 同应用同activity且一方无标题视为同页() {
        Scene a = SceneResolver.resolve(WX, WX + ".ui.LauncherUI", "", "", null);
        Scene b = SceneResolver.resolve(WX, WX + ".ui.LauncherUI", "", "微信", null);
        assertTrue(SceneResolver.sameScene(a, b));
    }

    @Test public void 不同应用永远不是同一页() {
        Scene a = SceneResolver.resolve(WX, "", "", "微信", null);
        Scene b = SceneResolver.resolve("com.tencent.mobileqq", "", "", "微信", null);
        assertFalse(SceneResolver.sameScene(a, b));
    }
}
