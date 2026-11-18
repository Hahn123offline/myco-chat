package com.dsh.memory;

public enum DetailLevel {
    /** 有文本，可作为内容证据。 */
    FULL,
    /** 无文本、有截图。 */
    IMAGE_ONLY,
    /** 无文本、无截图，只有 pkg+时间（如 UsageStats 降级）。 */
    APP_ONLY
}
