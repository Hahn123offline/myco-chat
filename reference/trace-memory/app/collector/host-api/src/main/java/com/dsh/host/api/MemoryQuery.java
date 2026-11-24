package com.dsh.host.api;

import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.memory.EgressTier;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆的只读查询接口。下游（对话、主动模式、检视页、脱敏批处理、未来的云端模块）
 * 通过它读记忆，不直接查库，也不知道存储实现。
 *
 * 检索：本地优先用 FTS5 trigram + BM25 相关度排序（R@1 显著优于按时间倒序）；
 * 系统 SQLite 不支持 trigram 时自动回退 LIKE。用 {@link #searchEngine()} 看实际引擎，
 * **不允许静默降级**（实现要把降级状态在 status/health/导出里暴露出来）。
 */
public interface MemoryQuery {

    List<MemoryBlock> recent(int n);

    List<MemoryBlock> between(long fromTs, long toTs);

    /**
     * 全文检索（中文子串可命中，不是语义检索）。约定：
     *  - FTS5 可用时按 BM25 相关度排序（{@link MemoryBlock#searchScore} 越小越相关），
     *    tie-break 时间倒序；
     *  - 回退 LIKE 时按时间倒序；
     *  - 空/纯空白 query 返回空列表，不抛异常；
     *  - 用户输入按字面量处理（FTS 短语转义 / LIKE 转义）。
     */
    List<MemoryBlock> search(String text, int limit);

    /**
     * 带出网档位过滤的检索（AND 组合）：只返回指定 tier。
     * 默认实现是"先检索再过滤"，实现类应下推到 SQL 以保持 BM25 排序不被截断。
     */
    default List<MemoryBlock> search(String text, int limit, EgressTier tier) {
        List<MemoryBlock> all = search(text, limit);
        if (tier == null) return all;
        List<MemoryBlock> out = new ArrayList<>();
        for (MemoryBlock b : all) {
            if (b != null && b.egressTier == tier) out.add(b);
        }
        return out;
    }

    long count();

    /** 按脱敏等级取块（批处理挑活：level=RULE，按时间从旧到新），最多 limit 条 */
    List<MemoryBlock> byRedactionLevel(int level, int limit);

    long countByRedactionLevel(int level);

    /**
     * 按出网分级取块（**未来云端模块的取数口**），按时间倒序，最多 limit 条。
     * 只返回指定 tier；调用方自己决定 WHITE/GRAY/BLACK 要不要取。
     */
    List<MemoryBlock> byEgressTier(EgressTier tier, int limit);

    /**
     * 升级一条块的脱敏结果（就地 UPDATE，不新增行）。
     *
     * **只允许往"掩码更多"的方向走**：实现必须拒绝把等级降回去，
     * 返回受影响行数（0 = 没找到 / 该块等级已经不低于目标 / 不允许降级）。
     *
     * @param maskedText    新的脱敏文本（调用方保证不比旧文本漏）
     * @param redactionLevel 目标等级，见 {@link com.dsh.host.api.data.RedactionLevel}
     * @param redactedAt    完成时间（毫秒）
     */
    int updateRedaction(long episodeId, String maskedText, int redactionLevel, long redactedAt);

    /**
     * 待云端打标的块：**只返回 egressTier=WHITE**，且 label 为空、labelStatus != SKIPPED。
     * 按时间升序（从旧到新）取最多 limit 条。
     *
     * 这是一条**隐私不变量**：实现必须把 tier 过滤写死在 SQL 里，
     * 调用方也要再校验一遍 block.egressTier == WHITE 才能真的发送。
     */
    List<MemoryBlock> blocksNeedingLabel(int limit);

    /**
     * 写入/更新打标结果（就地 UPDATE，按块 id）。
     *
     * @param label  产出的标签；失败时传 null（不落库）
     * @param model  产出模型名（可空）
     * @param ts     尝试时间（毫秒）
     * @param status 见 {@link com.dsh.host.api.data.LabelStatus}
     * @return 受影响行数
     */
    int updateLabel(long id, String label, String model, long ts, int status);

    /** 按打标状态计数（导出/状态页的 labels 统计） */
    long countByLabelStatus(int status);

    /**
     * 实际检索引擎："FTS5" 或 "LIKE"。
     * 降级必须是可见的（状态页/health/导出 JSON 都会带这个值），不能悄悄退。
     */
    String searchEngine();

    /**
     * 调试用一键导出：最近 n 条块（id/时间/包名/标题/脱敏文本/tier）的 JSON 字符串。
     * 宿主会周期写到 `files/memory/export_recent.json`，便于 adb 拉出来看。
     */
    String exportRecentJson(int n);
}
