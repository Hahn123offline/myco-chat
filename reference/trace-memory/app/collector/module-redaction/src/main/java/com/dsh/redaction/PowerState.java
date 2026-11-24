package com.dsh.redaction;

/**
 * 电源状态的最小抽象：批处理触发条件只看这三件事。
 * 抽成接口是为了单测能用假时钟/假电源验证"只在充电 + 息屏 + 电量足够时触发"。
 */
public interface PowerState {

    boolean isCharging();

    /** 屏幕不亮（PowerManager.isInteractive() == false） */
    boolean isScreenOff();

    /** 电量百分比 0..100；读不到返回 -1 */
    int batteryPercent();
}
