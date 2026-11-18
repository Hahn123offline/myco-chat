package com.dsh.memory;

import java.util.List;

/** 宿主唯一需要实现的东西：按日期/包名/namespace 取候选块。 */
public interface MemorySource {
    List<MemItem> candidates(RecallQuery q);

    /** level=day：按日期取日叙事；没有返回 null。 */
    default DayRecall day(String day) { return null; }

    /** 语料 IDF；null = 由 RecallCore 用候选现算。 */
    default IdfModel idf() { return null; }
}
