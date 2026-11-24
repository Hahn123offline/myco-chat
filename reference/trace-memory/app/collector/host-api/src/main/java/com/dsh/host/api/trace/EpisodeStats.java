package com.dsh.host.api.trace;

/**
 * 注意轨迹的只读统计。跨模块只读访问，不暴露 DB 结构/路径。
 *
 * 用途：状态页/导出做「episodes 与 blocks 条数对账」——
 * 采集到了 N 条 episode，记忆只落 M 条（脱敏失败/丢弃/规则跳过），差值为 K。
 * K≠0 必须能被看见，而不是静默丢数据。
 */
public interface EpisodeStats {

    /** 累计 episode 条数（含历史） */
    long countTotal();

    /** 今天（本地时区 0 点起）的 episode 条数 */
    long countToday();
}
