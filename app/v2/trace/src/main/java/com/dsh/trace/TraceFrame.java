package com.dsh.trace;

/** 块内一帧（首帧/滚动/内容变化/兜底）。text 可为空，shotRef 指向 evidence 图。 */
public final class TraceFrame {
    public int seq;
    public long tMs;
    public String shotRef;
    public String text;
    public String textHash;
    public double sharpness;
    public String kind = "CONTENT_CHANGE";
}
