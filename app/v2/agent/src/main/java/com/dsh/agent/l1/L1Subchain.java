package com.dsh.agent.l1;

import java.util.ArrayList;
import java.util.List;

/** L1 一条子链（可能跨应用、时间不连续）。 */
public final class L1Subchain {

    public final String title;
    public final List<Integer> sessionIndexes;

    public L1Subchain(String title, List<Integer> sessionIndexes) {
        this.title = title == null ? "" : title;
        this.sessionIndexes = sessionIndexes == null ? new ArrayList<>() : new ArrayList<>(sessionIndexes);
    }
}
