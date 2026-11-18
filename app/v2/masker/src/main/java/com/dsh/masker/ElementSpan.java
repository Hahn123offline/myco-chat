package com.dsh.masker;

/** 判定器给出的元素内候选 span。 */
public final class ElementSpan {

    public final String elementId;
    public final int start;
    public final int end;
    public final String kind;

    public ElementSpan(String elementId, int start, int end, String kind) {
        this.elementId = elementId == null ? "" : elementId;
        this.start = start;
        this.end = end;
        this.kind = kind == null ? "other" : kind;
    }
}
