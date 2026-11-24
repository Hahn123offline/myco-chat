package com.dsh.host.api;

/** 敏感能力枚举。宿主用它做启动前检查 + 能力账本记账。 */
public enum Capability {
    /** 读屏 / 无障碍事件 */
    ACCESSIBILITY,
    /** 截图 */
    SCREEN_CAPTURE,
    /** 本地 OCR */
    OCR,
    /** 前台应用 / 使用统计 */
    USAGE_STATS,
    /** 通知监听 */
    NOTIFICATION_LISTEN,
    /** 出网（只允许 LLM API） */
    NETWORK,
    /** 本地文件读写 */
    STORAGE
}
