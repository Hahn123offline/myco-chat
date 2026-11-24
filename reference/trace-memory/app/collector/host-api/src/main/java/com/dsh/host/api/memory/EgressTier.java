package com.dsh.host.api.memory;

/**
 * 出网分级：描述**这条记忆能不能离开设备**，是"门控"维度。
 *
 * 与 {@link com.dsh.host.api.data.RedactionLevel}（脱敏做到什么程度）是**两个独立维度**，
 * 不要互相推导：
 *  - "脱敏过了" ≠ "可以上云"（例如 BLACK 档的聊天记录，即使脱敏了也永不出设备）；
 *  - "可以上云" ≠ "不需要脱敏"（GRAY 档必须先脱敏）。
 * 任何根据 redactionLevel 自动放行/拦截 egressTier 的耦合逻辑都是错的。
 *
 *  - WHITE：公共内容（视频、资讯、购物、搜索），原样可上云；
 *  - GRAY ：需要脱敏才可上云（默认档：拿不准就 GRAY，保守）；
 *  - BLACK：永不出设备（IM、支付、证件、医疗、位置等）；
 *  - UNKNOWN：没有评估过（旧数据/策略缺失）。任何上云路径都不得把 UNKNOWN 当 WHITE。
 *
 * 显式 code：落 SQLite 用；不要用 ordinal（顺序一变旧数据就错）。
 */
public enum EgressTier {

    UNKNOWN(0),
    WHITE(1),
    GRAY(2),
    BLACK(3);

    public final int code;

    EgressTier(int code) { this.code = code; }

    public static EgressTier fromCode(int code) {
        for (EgressTier t : values()) {
            if (t.code == code) return t;
        }
        return UNKNOWN;
    }

    /** 是否允许原样出设备（只有 WHITE） */
    public boolean isPublicContent() { return this == WHITE; }

    /** 是否需要先脱敏才考虑出设备（GRAY） */
    public boolean requiresRedaction() { return this == GRAY; }

    /** 是否永不出设备（BLACK；UNKNOWN 也不允许出） */
    public boolean neverLeavesDevice() { return this == BLACK || this == UNKNOWN; }
}
