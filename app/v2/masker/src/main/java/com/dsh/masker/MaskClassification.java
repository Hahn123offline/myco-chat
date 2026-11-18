package com.dsh.masker;

/** 每个元素的判定结果，供审计与调试。 */
public final class MaskClassification {

    public final String elementId;
    public final int start;
    public final int end;
    public final String kind;
    public final boolean masked;
    public final String reason;

    public MaskClassification(String elementId, int start, int end, String kind,
                              boolean masked, String reason) {
        this.elementId = elementId == null ? "" : elementId;
        this.start = start;
        this.end = end;
        this.kind = kind == null ? "other" : kind;
        this.masked = masked;
        this.reason = reason == null ? "" : reason;
    }
}
