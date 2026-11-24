package com.dsh.memory;

/** 宿主返回的“可上云图片”：已经过应用级策略 + 掩码。 */
public final class FrameRef {
    public byte[] jpeg;
    public String stage = "MASKED";
    public boolean blocked;
    public String error = "";

    public static FrameRef blocked(String why) {
        FrameRef r = new FrameRef();
        r.blocked = true;
        r.error = why == null ? "" : why;
        return r;
    }

    public static FrameRef error(String why) {
        FrameRef r = new FrameRef();
        r.error = why == null ? "" : why;
        return r;
    }

    public static FrameRef ok(byte[] jpeg) {
        FrameRef r = new FrameRef();
        r.jpeg = jpeg;
        return r;
    }
}
