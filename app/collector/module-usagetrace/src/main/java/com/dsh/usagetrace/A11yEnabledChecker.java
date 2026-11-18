package com.dsh.usagetrace;

/**
 * "我们的无障碍服务是否在系统里启用"的判据抽象。
 * 生产实现走 AccessibilityManager；单测注入假实现（不碰 android API）。
 */
public interface A11yEnabledChecker {

    boolean isOurServiceEnabled();
}
