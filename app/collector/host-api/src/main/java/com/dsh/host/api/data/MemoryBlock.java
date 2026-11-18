package com.dsh.host.api.data;

import com.dsh.host.api.memory.EgressTier;

/**
 * 记忆块：脱敏后、可进入记忆库的最小单元。
 * 注意：这里不含任何原始敏感值，只含已脱敏文本。
 */
public final class MemoryBlock {

    public static final int SCHEMA_VER = 5;

    public long id;
    /** 来源注意轨迹（可追溯回原始 episode，但 episode 里有原文，访问受控） */
    public long episodeId;
    public long tStart;
    public long tEnd;
    public String pkg = "";
    public String title = "";
    /** 脱敏后的文本 */
    public String maskedText = "";
    /** low | high */
    public String privacyTier = "low";
    /** 敏感命中摘要，如 "PHONE:2" */
    public String hitSummary = "";
    /**
     * 证据引用：来自 EpisodeRedacted 的**不透明** token（如 "trace:42"）。
     * 记忆模块只负责存与查：不解析、不拼路径、不 import 生产者的类。
     * 旧库（schema v1）迁移后该列为 NULL，读出来是 null。
     */
    public String evidenceRef;
    /**
     * 脱敏做到了什么程度（RULE / MODEL），见 {@link RedactionLevel}。默认 RULE（旧行也按 RULE）。
     *
     * **注意：这和 {@link #egressTier} 是两个独立维度，不要耦合。**
     * 它只回答"文本被遮到什么程度"，不回答"能不能出设备"——
     * 例如 BLACK 档的内容即使已脱敏也永不出设备；WHITE 档也可能没做模型补充。
     */
    public int redactionLevel = RedactionLevel.RULE;
    /** 最近一次脱敏完成时间（毫秒）；旧行 0 */
    public long redactedAt;
    /**
     * 出网分级，见 {@link EgressTier}：描述"这条记忆能不能离开设备"，由门控规则决定。
     *
     * 与 {@link #redactionLevel} 独立：不是"脱敏过 = 能上云"。旧行迁移后为 UNKNOWN，
     * 任何上云路径都不得把 UNKNOWN 当 WHITE。
     */
    public EgressTier egressTier = EgressTier.UNKNOWN;
    /**
     * 云端打标产出的"这一屏在讲什么"短标签（不超过 60 字，已过规则校验）。
     * 未打标/失败时为 null。它只是**展示用摘要**，不参与出网门控（能不能出设备只看 egressTier）。
     */
    public String label;
    /** 产出该 label 的模型名（可空） */
    public String labelModel;
    /** 最近一次打标尝试时间（毫秒） */
    public long labeledAt;
    /** 打标状态，见 {@link LabelStatus}；旧行默认 PENDING */
    public int labelStatus = LabelStatus.PENDING;
    /**
     * FTS5 BM25 相关度分数：**越小越相关**。只在 search() 走 FTS5 时填充；
     * LIKE 回退路径为 0（表示"没有相关性排序"）。仅用于调试/将来阈值，不落库。
     */
    public double searchScore = 0;
    public int schemaVer = SCHEMA_VER;
}
