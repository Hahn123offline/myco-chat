package com.dsh.memory;

/** 掩码结果：text=null 表示不可用；mode 指示用了结构还是纯文本降级。 */
public final class Masked {
    public final String text;
    public final MaskMode mode;
    public Masked(String text, MaskMode mode) {
        this.text = text;
        this.mode = mode == null ? MaskMode.NONE : mode;
    }
    public static Masked none() { return new Masked(null, MaskMode.NONE); }
    public boolean usable() { return text != null && !text.trim().isEmpty(); }
}
