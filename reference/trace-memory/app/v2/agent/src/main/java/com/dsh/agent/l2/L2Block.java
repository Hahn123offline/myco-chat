package com.dsh.agent.l2;

/** L2 会话的一个块（已过 egress 闸的展示文本）。 */
public final class L2Block {

    public final long id;
    public final String pkg;
    public final String title;
    public final String maskedText;
    public final long t0;
    public final long t1;
    public final boolean blocked;
    /** 应用级出网排除（文本已替换为 [APP_EXCLUDED]） */
    public final boolean excluded;

    public L2Block(long id, String pkg, String title, String maskedText,
                   long t0, long t1, boolean blocked) {
        this(id, pkg, title, maskedText, t0, t1, blocked, false);
    }

    public L2Block(long id, String pkg, String title, String maskedText,
                   long t0, long t1, boolean blocked, boolean excluded) {
        this.id = id;
        this.pkg = pkg == null ? "" : pkg;
        this.title = title == null ? "" : title;
        this.maskedText = maskedText == null ? "" : maskedText;
        this.t0 = t0;
        this.t1 = t1;
        this.blocked = blocked;
        this.excluded = excluded;
    }
}
