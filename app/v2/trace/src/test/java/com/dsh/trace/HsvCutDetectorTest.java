package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** HSV 切点：Bhattacharyya 距离 + 阈值判定。 */
public class HsvCutDetectorTest {

    private static final int N = HsvCutDetector.SIZE * HsvCutDetector.SIZE;

    private static int[] solid(int color) {
        int[] p = new int[N];
        java.util.Arrays.fill(p, color);
        return p;
    }

    private static int[] half(int c1, int c2) {
        int[] p = new int[N];
        for (int i = 0; i < N; i++) p[i] = i < N / 2 ? c1 : c2;
        return p;
    }

    @Test public void 同一画面距离为0() {
        double[] a = HsvCutDetector.histogram(solid(0xFF3366CC));
        double[] b = HsvCutDetector.histogram(solid(0xFF3366CC));
        assertEquals(0.0, HsvCutDetector.distance(a, b), 1e-9);
    }

    @Test public void 红蓝差异是切点() {
        HsvCutDetector d = new HsvCutDetector();
        assertFalse("首帧只建立基线", d.isCut(solid(0xFFFF0000)));
        assertTrue("红→蓝应判切点", d.isCut(solid(0xFF0000FF)));
        assertTrue(d.lastDistance() > HsvCutDetector.DEFAULT_THRESHOLD);
    }

    @Test public void 相邻相似帧不切() {
        HsvCutDetector d = new HsvCutDetector();
        assertFalse(d.isCut(solid(0xFF3366CC)));
        int[] similar = solid(0xFF3366CE);
        assertFalse(d.isCut(similar));
    }

    @Test public void 阈值可配置() {
        HsvCutDetector strict = new HsvCutDetector(0.99);
        strict.isCut(solid(0xFFFF0000));
        assertFalse("阈值极高时红→蓝不切", strict.isCut(solid(0xFF0000FF)));

        HsvCutDetector loose = new HsvCutDetector(0.0);
        loose.isCut(solid(0xFFFF0000));
        assertTrue("阈值 0 时任何变化都切", loose.isCut(solid(0xFF00FF00)));
    }

    @Test public void 半屏变化也能检测() {
        HsvCutDetector d = new HsvCutDetector(0.2);
        assertFalse(d.isCut(half(0xFFFFFFFF, 0xFFFFFFFF)));
        assertTrue(d.isCut(half(0xFF000000, 0xFFFF0000)));
    }

    @Test public void 直方图归一化() {
        double[] h = HsvCutDetector.histogram(half(0xFF000000, 0xFFFF0000));
        double sum = 0;
        for (double v : h) sum += v;
        assertEquals(1.0, sum, 1e-9);
        assertEquals(HsvCutDetector.BINS, h.length);
    }

    @Test public void 序列重置后首帧不切() {
        HsvCutDetector d = new HsvCutDetector();
        d.isCut(solid(0xFFFF0000));
        assertTrue(d.isCut(solid(0xFF0000FF)));
        d.reset();
        assertFalse("reset 后重新建立基线", d.isCut(solid(0xFFFFFFFF)));
    }

    @Test public void 空输入不崩() {
        HsvCutDetector d = new HsvCutDetector();
        d.isCut(null);          // 不抛异常即可；空输入建立的是空直方图基线
        d.isCut(new int[0]);
        assertTrue(true);
    }
}
