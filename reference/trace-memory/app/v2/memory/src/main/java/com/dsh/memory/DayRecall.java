package com.dsh.memory;

/** level=day 的结果。 */
public final class DayRecall {
    public final String day;
    public final String narrative;
    public final String mainlines;
    public final String gaps;

    public DayRecall(String day, String narrative, String mainlines, String gaps) {
        this.day = day == null ? "" : day;
        this.narrative = narrative == null ? "" : narrative;
        this.mainlines = mainlines == null ? "" : mainlines;
        this.gaps = gaps == null ? "" : gaps;
    }
}
