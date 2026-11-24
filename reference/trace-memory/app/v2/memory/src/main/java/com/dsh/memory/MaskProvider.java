package com.dsh.memory;

/** 运行时 Masker：有结构走 FULL，无结构走 TEXT_ONLY，都不可用才 NONE。 */
public interface MaskProvider {
    Masked maskedTextFor(long itemId, String pkg, long t0, long t1, String rawText);
}
