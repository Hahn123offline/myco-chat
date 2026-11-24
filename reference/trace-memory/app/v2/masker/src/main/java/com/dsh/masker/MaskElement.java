package com.dsh.masker;

/** 纯数据元素；不依赖 Android / Trace / 网络。 */
public final class MaskElement {

    public final String id;
    public final String text;
    public final int left, top, right, bottom;
    public final String role;
    public final java.util.List<String> signals;

    public MaskElement(String id, String text, int left, int top, int right, int bottom,
                       String role, java.util.List<String> signals) {
        this.id = id == null ? "" : id;
        this.text = text == null ? "" : text;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.role = role == null ? "PARAGRAPH" : role;
        this.signals = signals == null ? java.util.Collections.emptyList() : signals;
    }
}
