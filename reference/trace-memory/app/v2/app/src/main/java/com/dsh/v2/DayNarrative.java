package com.dsh.v2;

import java.util.ArrayList;
import java.util.List;

/** L3 日叙事的结构化索引。 */
public final class DayNarrative {

    public static final class Mainline {
        public final String title;
        public final String text;
        public final List<Long> eventIds;
        public final List<Long> memoryIds;
        public final String timeHint;
        public Mainline(String title, String text, List<Long> eventIds, String timeHint) {
            this(title, text, eventIds, new ArrayList<>(), timeHint);
        }
        public Mainline(String title, String text, List<Long> eventIds,
                        List<Long> memoryIds, String timeHint) {
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
            this.eventIds = eventIds == null ? new ArrayList<>() : new ArrayList<>(eventIds);
            this.memoryIds = memoryIds == null ? new ArrayList<>() : new ArrayList<>(memoryIds);
            this.timeHint = timeHint == null ? "" : timeHint;
        }
    }

    public static final class Segment {
        public final String text;
        public final List<Long> eventIds;
        public final String timeHint;
        public Segment(String text, List<Long> eventIds, String timeHint) {
            this.text = text == null ? "" : text;
            this.eventIds = eventIds == null ? new ArrayList<>() : new ArrayList<>(eventIds);
            this.timeHint = timeHint == null ? "" : timeHint;
        }
    }

    public static final class Thread {
        public final String text;
        public final List<Long> eventIds;
        public final List<Long> memoryIds;
        public Thread(String text, List<Long> eventIds) {
            this(text, eventIds, new ArrayList<>());
        }
        public Thread(String text, List<Long> eventIds, List<Long> memoryIds) {
            this.text = text == null ? "" : text;
            this.eventIds = eventIds == null ? new ArrayList<>() : new ArrayList<>(eventIds);
            this.memoryIds = memoryIds == null ? new ArrayList<>() : new ArrayList<>(memoryIds);
        }
    }

    public final String day;
    public final String narrative;
    public final List<Segment> segments;
    public final List<Mainline> mainlines;
    public final List<Thread> openThreads;
    public final List<String> gaps;
    public final List<Long> unmentioned;
    public final int indexPartial;
    public final String error;

    public DayNarrative(String day, String narrative, List<Segment> segments,
                        List<Thread> openThreads, List<String> gaps,
                        List<Long> unmentioned, int indexPartial, String error) {
        this(day, narrative, segments, new ArrayList<>(), openThreads, gaps,
                unmentioned, indexPartial, error);
    }

    public DayNarrative(String day, String narrative, List<Segment> segments,
                        List<Mainline> mainlines, List<Thread> openThreads, List<String> gaps,
                        List<Long> unmentioned, int indexPartial, String error) {
        this.day = day == null ? "" : day;
        this.narrative = narrative == null ? "" : narrative;
        this.segments = segments == null ? new ArrayList<>() : segments;
        this.mainlines = mainlines == null ? new ArrayList<>() : mainlines;
        this.openThreads = openThreads == null ? new ArrayList<>() : openThreads;
        this.gaps = gaps == null ? new ArrayList<>() : gaps;
        this.unmentioned = unmentioned == null ? new ArrayList<>() : unmentioned;
        this.indexPartial = indexPartial;
        this.error = error == null ? "" : error;
    }
}
