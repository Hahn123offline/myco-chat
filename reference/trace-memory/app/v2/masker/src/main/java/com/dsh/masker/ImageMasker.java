package com.dsh.masker;

import java.util.List;

/** 纯像素图片掩码：在 bbox（外扩后）上涂不透明黑；应在缩放拼图之前调用。 */
public final class ImageMasker {

    public static final int BLACK = 0xFF000000;

    private ImageMasker() { }

    /** @param pixels ARGB_8888 一维数组（长度 width*height） */
    public static int[] paint(int width, int height, int[] pixels,
                              List<MaskBBox> bboxes, int padding) {
        if (pixels == null || width <= 0 || height <= 0) return pixels;
        if (bboxes == null) return pixels;
        for (MaskBBox b : bboxes) {
            int l = Math.max(0, b.x - padding);
            int t = Math.max(0, b.y - padding);
            int r = Math.min(width, b.x + b.w + padding);
            int bo = Math.min(height, b.y + b.h + padding);
            for (int y = t; y < bo; y++) {
                int row = y * width;
                for (int x = l; x < r; x++) pixels[row + x] = BLACK;
            }
        }
        return pixels;
    }
}
