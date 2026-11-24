package com.dsh.agent.l1;

import java.util.ArrayList;
import java.util.List;

/** L1 输入的一条会话摘要（时间/应用/块数/首末文本前 60 字）。 */
public final class L1Session {

    public final int index;         // 1..N
    public final long t0;
    public final long t1;
    public final String pkg;
    public final int blocks;
    public final String head;
    public final String tail;

    public L1Session(int index, long t0, long t1, String pkg, int blocks,
                     String head, String tail) {
        this.index = index;
        this.t0 = t0;
        this.t1 = t1;
        this.pkg = pkg == null ? "" : pkg;
        this.blocks = blocks;
        this.head = head == null ? "" : head;
        this.tail = tail == null ? "" : tail;
    }

    public List<Integer> blockIdsPlaceholder() { return new ArrayList<>(); }
}
