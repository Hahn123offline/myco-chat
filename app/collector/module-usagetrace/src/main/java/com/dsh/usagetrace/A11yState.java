package com.dsh.usagetrace;

/**
 * 无障碍采集的真实状态。不是"系统设置里有没有"，而是"到底有没有在收到事件"。
 *
 *  - ACTIVE             ：系统启用 + 最近 N 分钟真的收到过无障碍事件
 *  - ENABLED_BUT_SILENT ：系统显示启用，但超过 N 分钟没有任何事件（可能是用户没动手机，
 *                         也可能是服务被杀/没绑上 —— 后者是重启后最常见的静默失败）
 *  - DISABLED           ：系统层未启用（重启后被厂商系统关掉就是这一档）
 */
public enum A11yState {

    ACTIVE("ACTIVE"),
    ENABLED_BUT_SILENT("SILENT"),
    DISABLED("DISABLED");

    /** 写进 Health.detail / 状态页的短标签：ACTIVE | SILENT | DISABLED */
    public final String tag;

    A11yState(String tag) { this.tag = tag; }

    public boolean isRecording() { return this == ACTIVE; }
}
