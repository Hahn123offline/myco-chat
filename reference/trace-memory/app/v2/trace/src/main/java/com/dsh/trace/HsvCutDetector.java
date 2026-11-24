package com.dsh.trace;

/**
 * 画面切点检测：HSV 三通道直方图 + Bhattacharyya 距离（task-21）。
 *
 * 为什么不用 pageId 文本哈希切块：真机上 title/节点文本时有时无，身份会翻；
 * 画面变化是更直接的结构信号。pageId 只保留为元数据。
 *
 * 算法：
 *  - 输入是已缩到 32×32 的 ARGB 像素（服务端缩放）
 *  - HSV 三通道直方图：H 12 bins / S 8 / V 8，拼接后整体归一化（和为 1）
 *  - 相邻帧距离 = Bhattacharyya 距离 = sqrt(1 - Σ sqrt(p_i·q_i))
 *  - 距离 > threshold（默认 0.35）→ 判定为切点
 *
 * 无第三方依赖，纯函数 + 小状态机（上一帧直方图）。
 */
public final class HsvCutDetector {

    public static final int SIZE = 32;
    public static final double DEFAULT_THRESHOLD = 0.35;
    public static final int H_BINS = 12;
    public static final int S_BINS = 8;
    public static final int V_BINS = 8;
    public static final int BINS = H_BINS + S_BINS + V_BINS;

    private double threshold;
    private double[] lastHistogram;
    private double lastDistance;

    public HsvCutDetector() { this(DEFAULT_THRESHOLD); }

    public HsvCutDetector(double threshold) {
        this.threshold = Math.max(0.0, threshold);
    }

    /** 首帧只建立基线，不判切点 */
    public boolean isCut(int[] argb) {
        double[] h = histogram(argb);
        if (lastHistogram == null) {
            lastHistogram = h;
            lastDistance = 0;
            return false;
        }
        double d = distance(lastHistogram, h);
        lastDistance = d;
        lastHistogram = h;
        return d > threshold;
    }

    public double lastDistance() { return lastDistance; }

    public void setThreshold(double t) { this.threshold = Math.max(0.0, t); }

    public double threshold() { return threshold; }

    public void reset() {
        lastHistogram = null;
        lastDistance = 0;
    }

    /** HSV 直方图（拼接、和为 1）；argb 长度应为 SIZE*SIZE */
    public static double[] histogram(int[] argb) {
        double[] hist = new double[BINS];
        if (argb == null || argb.length == 0) return hist;
        float[] hsv = new float[3];
        for (int p : argb) {
            androidColorToHsv(p, hsv);
            int h = (int) (hsv[0] / 360f * H_BINS);
            int s = (int) (hsv[1] * S_BINS);
            int v = (int) (hsv[2] * V_BINS);
            if (h < 0) h = 0;
            if (h >= H_BINS) h = H_BINS - 1;
            if (s < 0) s = 0;
            if (s >= S_BINS) s = S_BINS - 1;
            if (v < 0) v = 0;
            if (v >= V_BINS) v = V_BINS - 1;
            hist[h]++;
            hist[H_BINS + s]++;
            hist[H_BINS + S_BINS + v]++;
        }
        double total = 3.0 * argb.length;       // 每个像素贡献三个 bin
        for (int i = 0; i < BINS; i++) hist[i] /= total;
        return hist;
    }

    /** Bhattacharyya 距离：0 = 相同，1 = 完全不重叠 */
    public static double distance(double[] p, double[] q) {
        if (p == null || q == null || p.length != q.length) return 1.0;
        double bc = 0;
        for (int i = 0; i < p.length; i++) {
            double v = p[i] * q[i];
            if (v > 0) bc += Math.sqrt(v);
        }
        if (bc > 1) bc = 1;
        return Math.sqrt(Math.max(0.0, 1.0 - bc));
    }

    private static void androidColorToHsv(int color, float[] out) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float v = max / 255f;
        float s = max == 0 ? 0 : (max - min) / (float) max;
        float h = 0;
        if (max != min) {
            float d = max - min;
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        out[0] = h;
        out[1] = s;
        out[2] = v;
    }
}
