package com.dsh.usagetrace;

/**
 * 时间源抽象：状态机与提醒节流都只依赖这两个值，单测注入假时钟。
 *  - nowMs    ：墙钟（判断"最近一次事件多久以前"）
 *  - uptimeMs ：开机以来（判断"刚开机静默期"；包含休眠）
 */
public interface A11yTimeSource {

    long nowMs();

    long uptimeMs();
}
