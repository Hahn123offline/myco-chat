package com.dsh.masker;

/** 文本里的一个掩码区间（全局 offset，左闭右开）。 */
public final class MaskSpan {

    public final int start;
    public final int end;
    public final String kind;

    public MaskSpan(int start, int end, String kind) {
        this.start = start;
        this.end = end;
        this.kind = kind == null ? "other" : kind;
    }
}
