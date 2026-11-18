package com.dsh.v2;

/** L3 输入选择不变式：只有 level=0 且 status=done 的链可进日叙事。 */
public final class DayInputSelector {

    private DayInputSelector() { }

    public static boolean eligible(long level, String status) {
        return level == 0 && "done".equals(status);
    }
}
