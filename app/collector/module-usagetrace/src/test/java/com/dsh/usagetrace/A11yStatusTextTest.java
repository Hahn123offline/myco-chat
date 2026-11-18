package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 文案硬规则：非 ACTIVE 时**任何地方都不能出现"正在记录"**。
 * 这是产品级致命失败模式的护栏（用户以为在记、其实没记）。
 */
public class A11yStatusTextTest {

    @Test public void 前台通知_DISABLED不能出现正在记录() {
        String text = A11yStatusText.notificationText(A11yState.DISABLED, -1, 0);
        assertTrue(text, text.contains("未在记录"));
        assertTrue(text, text.contains("未启用"));
        assertFalse(text, text.contains("正在记录"));
        assertEquals("DSH 未在记录（无障碍未启用）", A11yStatusText.notificationTitle(A11yState.DISABLED));
    }

    @Test public void 前台通知_SILENT也不能出现正在记录() {
        String text = A11yStatusText.notificationText(A11yState.ENABLED_BUT_SILENT, 12 * 60_000L, 3);
        assertTrue(text, text.contains("未确认在记录"));
        assertFalse(text, text.contains("正在记录"));
        assertTrue(text, text.contains("分钟前"));
        assertFalse(A11yStatusText.notificationTitle(A11yState.ENABLED_BUT_SILENT).contains("正在记录"));
    }

    @Test public void 前台通知_ACTIVE才说正在记录() {
        String text = A11yStatusText.notificationText(A11yState.ACTIVE, 1_000, 7);
        assertTrue(text, text.contains("正在记录"));
        assertTrue(text, text.contains("7"));
        assertTrue(A11yStatusText.notificationTitle(A11yState.ACTIVE).contains("正在记录"));
    }

    @Test public void 状态页横幅_只有非ACTIVE才显示() {
        assertEquals("", A11yStatusText.banner(A11yState.ACTIVE));
        assertFalse(A11yStatusText.banner(A11yState.DISABLED).isEmpty());
        assertTrue(A11yStatusText.banner(A11yState.DISABLED).contains("未在记录"));
        assertFalse(A11yStatusText.banner(A11yState.ENABLED_BUT_SILENT).isEmpty());
    }

    @Test public void 状态页详情包含三个观测值() {
        String line = A11yStatusText.statusLine(A11yState.ENABLED_BUT_SILENT, 12_345, 9);
        assertTrue(line, line.contains("a11y=SILENT"));
        assertTrue(line, line.contains("lastEventAgeMs=12345"));
        assertTrue(line, line.contains("episodesToday=9"));
        assertEquals(line, A11yStatusText.healthDetail(A11yState.ENABLED_BUT_SILENT, 12_345, 9));
    }

    @Test public void 从未收到事件的文案说明() {
        String text = A11yStatusText.notificationText(A11yState.ENABLED_BUT_SILENT, -1, 0);
        assertTrue(text, text.contains("从未收到事件"));
    }

    @Test public void 提醒通知文案不假装成功() {
        assertTrue(A11yStatusText.alertText().contains("无法自己开启"));
        assertTrue(A11yStatusText.alertTitle().contains("停止"));
    }
}
