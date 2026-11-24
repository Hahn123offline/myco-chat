package com.dsh.masker;

/** 图片上要涂黑的区域（原图坐标，未缩放前）。 */
public final class MaskBBox {

    public final int x, y, w, h;
    public final String kind;

    public MaskBBox(int x, int y, int w, int h, String kind) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.kind = kind == null ? "other" : kind;
    }
}
