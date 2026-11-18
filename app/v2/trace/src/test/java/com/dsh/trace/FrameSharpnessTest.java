package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Laplacian 方差选帧：平整画面 0，纹理/边缘越大越清晰。 */
public class FrameSharpnessTest {

    private static final int W = 32;
    private static final int H = 32;

    private static int[] flat(int color) {
        int[] p = new int[W * H];
        java.util.Arrays.fill(p, color);
        return p;
    }

    @Test public void 纯色画面方差为0() {
        assertEquals(0.0, FrameSharpness.laplacianVariance(flat(0xFF808080), W, H), 1e-9);
    }

    @Test public void 棋盘纹理方差明显大于纯色() {
        int[] checker = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                checker[y * W + x] = ((x + y) % 2 == 0) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        double checkerVar = FrameSharpness.laplacianVariance(checker, W, H);
        double flatVar = FrameSharpness.laplacianVariance(flat(0xFF808080), W, H);
        assertTrue("棋盘应比纯色清晰: " + checkerVar + " vs " + flatVar, checkerVar > flatVar);
        assertTrue(checkerVar > 1000);
    }

    @Test public void 渐变比棋盘方差小() {
        int[] gradient = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int g = x * 255 / (W - 1);
                gradient[y * W + x] = 0xFF000000 | (g << 16) | (g << 8) | g;
            }
        }
        int[] checker = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                checker[y * W + x] = ((x + y) % 2 == 0) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        double gv = FrameSharpness.laplacianVariance(gradient, W, H);
        double cv = FrameSharpness.laplacianVariance(checker, W, H);
        assertTrue(cv > gv);
    }

    @Test public void 输入过小或为空返回0() {
        assertEquals(0.0, FrameSharpness.laplacianVariance(null, W, H), 1e-9);
        assertEquals(0.0, FrameSharpness.laplacianVariance(flat(0), 2, 2), 1e-9);
        assertEquals(0.0, FrameSharpness.laplacianVariance(new int[]{1, 2}, W, H), 1e-9);
    }
}
