package com.dsh.guard;

/**
 * 一次命中的规则与位置。
 *
 * 只保留"什么类型、在哪、替换成什么"，**不保留被命中的原文**（避免死线内容被日志/导出二次传播）。
 */
public final class Hit {

    /** 规则类型，见 {@link Guard} 的 K_* 常量 */
    public final String kind;
    public final Action action;
    /** 在输入文本中的字符偏移（半开区间） */
    public final int start;
    public final int end;
    /** MASK 的替换文本；BLOCK 为 null */
    public final String replacement;

    Hit(String kind, Action action, int start, int end, String replacement) {
        this.kind = kind;
        this.action = action;
        this.start = start;
        this.end = end;
        this.replacement = replacement;
    }

    public int length() { return end - start; }

    @Override public String toString() {
        return kind + "[" + start + "," + end + ")";
    }
}
