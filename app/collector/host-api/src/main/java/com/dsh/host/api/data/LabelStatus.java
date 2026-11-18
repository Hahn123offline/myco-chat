package com.dsh.host.api.data;

/**
 * 云端打标状态（labelStatus）。int 便于 SQLite 落库与旧行兼容（默认 PENDING=0）。
 *
 *  - PENDING：还没打标（新块默认）
 *  - OK     ：模型产出通过校验，label 已落库
 *  - FAILED ：这一次失败（模型报错/输出不合格）；仍可重试
 *  - SKIPPED：连续失败达到上限，不再自动重试（避免坏块永远烧电）
 */
public final class LabelStatus {

    public static final int PENDING = 0;
    public static final int OK = 1;
    public static final int FAILED = 2;
    public static final int SKIPPED = 3;

    private LabelStatus() { }

    public static boolean isValid(int v) { return v >= PENDING && v <= SKIPPED; }

    public static String name(int v) {
        switch (v) {
            case OK: return "OK";
            case FAILED: return "FAILED";
            case SKIPPED: return "SKIPPED";
            default: return "PENDING";
        }
    }
}
