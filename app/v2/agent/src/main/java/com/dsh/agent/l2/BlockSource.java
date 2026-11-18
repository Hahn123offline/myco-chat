package com.dsh.agent.l2;

import java.util.List;

/** L2 会话读取块/图片的接口（Android 实现接 trace.db + Bitmap；PC/测试接内存）。 */
public interface BlockSource {

    List<L2Block> blocks(String chainId);

    /** 把最多 16 帧拼成一张图（Android Bitmap 实现；测试可用假 JPEG） */
    byte[] mosaic(String chainId, List<Long> blockIds);

    /** 单帧高清 */
    byte[] frame(String chainId, long blockId, int maxEdge);

    /** 是否含应用级排除块（partial 链要求 body 有缺口声明）。 */
    default boolean hasExcludedBlocks() { return false; }

    /** 被应用级排除的块数（审计用）。 */
    default int excludedBlocks() { return 0; }

    /** 掩码 span 数与 kinds（审计用）。 */
    default int maskedSpans() { return 0; }

    default String maskedKinds() { return ""; }
}
