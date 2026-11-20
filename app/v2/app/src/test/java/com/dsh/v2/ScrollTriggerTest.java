package com.dsh.v2;

import static org.junit.Assert.*;

import org.junit.Test;

public class ScrollTriggerTest {

    @Test public void 累计07屏高触发一帧() {
        ScrollTrigger t = new ScrollTrigger();
        assertFalse(t.onScroll(1000, 0, 300, 1000));
        assertTrue(t.onScroll(1100, 0, 450, 1000));   // 750 >= 700
    }

    @Test public void 停止时累计02屏高补一帧() {
        ScrollTrigger t = new ScrollTrigger();
        assertFalse(t.onScroll(1000, 0, 250, 1000));
        assertTrue(t.onIdle(1400, 1000));             // 250 >= 200
    }

    @Test public void 同一次停止不重复补帧() {
        ScrollTrigger t = new ScrollTrigger();
        t.onScroll(1000, 0, 250, 1000);
        assertTrue(t.onIdle(1400, 1000));
        assertFalse(t.onIdle(1500, 1000));
    }

    @Test public void 水平翻页也触发() {
        ScrollTrigger t = new ScrollTrigger();
        assertTrue(t.onScroll(1000, 750, 0, 1000));
    }

    @Test public void 五百毫秒内不连续截图() {
        ScrollTrigger t = new ScrollTrigger();
        assertTrue(t.onScroll(1000, 0, 800, 1000));
        assertFalse(t.onScroll(1200, 0, 800, 1000));
        assertTrue(t.onScroll(1600, 0, 800, 1000));
    }
}
