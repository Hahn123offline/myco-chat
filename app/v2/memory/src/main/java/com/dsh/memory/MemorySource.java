package com.dsh.memory;

import java.util.List;

/** 宿主唯一需要实现的东西：按日期/包名/namespace 取候选块。 */
public interface MemorySource {
    List<MemItem> candidates(RecallQuery q);

    /** level=day：按日期取日叙事；没有返回 null。 */
    default DayRecall day(String day) { return null; }

    /** 语料 IDF；null = 由 RecallCore 用候选现算。 */
    default IdfModel idf() { return null; }

    /** 该 shot_ref 的原始画面是否在本机；默认 true，证据库缺失时由实现返回 false。 */
    default boolean evidenceAvailable(String shotRef) { return true; }

    /** 某天概况；null = 不支持。 */
    default DayDiagnostic diagnose(String day) { return null; }

    /** 按块 id 取原始文本/截图指针；null = 不支持或不存在。 */
    default BlockRef readBlock(long id) { return null; }

    /** 按截图指针读 JPEG；null = 不可用。 */
    default byte[] readShot(String shotRef) { return null; }

    /** 已经是掩码/策略处理后的可上云图片；null = 不支持。 */
    default FrameRef readFrame(long blockId) { return null; }

    /** 行级观察；按 object/from/to 过滤。 */
    default List<LineItem> lines(RecallQuery q) { return java.util.Collections.emptyList(); }

    /** 某时间窗的 trace 数；用于区分「没有痕迹」与「有痕迹无文本」。 */
    default int traceCount(String from, String to) { return 0; }

    /** 某时间窗的 health 心跳文本；默认不参与内容检索。 */
    default java.util.List<String> healthNotes(String from, String to) {
        return java.util.Collections.emptyList();
    }

    /** 对象级消息序列；空表示未投影。 */
    default java.util.List<ObjectMessage> messages(String object, int limit) {
        return java.util.Collections.emptyList();
    }
}
