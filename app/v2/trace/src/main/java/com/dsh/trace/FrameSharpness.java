package com.dsh.trace;

/**
 * Laplacian 方差：衡量画面清晰度（task-21）。
 *
 * 用途：块内多帧时选最清晰的一帧当代表帧——转场/动画帧是糊的，OCR 会把文字认错。
 * 3×3 核 [0,1,0; 1,-4,1; 0,1,0]，对内部像素卷积后取响应方差；越大越清晰。
 * 无第三方依赖。
 */
public final class FrameSharpness {

    private FrameSharpness() { }

    /**
     * @param argb 像素（宽×高，通常已缩到 32×32）
     * @param width  宽
     * @param height 高
     * @return Laplacian 响应方差；输入太小/为空时返回 0
     */
    public static double laplacianVariance(int[] argb, int width, int height) {
        if (argb == null || width < 3 || height < 3 || argb.length < width * height) return 0;
        double[] gray = new double[width * height];
        for (int i = 0; i < width * height; i++) {
            int c = argb[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b;
        }
        int n = 0;
        double sum = 0, sumSq = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int idx = y * width + x;
                double lap = -4 * gray[idx]
                        + gray[idx - 1] + gray[idx + 1]
                        + gray[idx - width] + gray[idx + width];
                sum += lap;
                sumSq += lap * lap;
                n++;
            }
        }
        if (n == 0) return 0;
        double mean = sum / n;
        return Math.max(0.0, sumSq / n - mean * mean);
    }
}
