package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 提醒节流：只有 DISABLED 提醒；开机 10 分钟静默；之后最多 30 分钟一次。 */
public class A11yReminderPolicyTest {

    private static final long MIN = 60_000L;

    private final TestSupport.FakeConfig cfg = new TestSupport.FakeConfig();
    private final TestSupport.FakeTime time = new TestSupport.FakeTime(1_000_000L, 0L);

    private A11yReminderPolicy policy() {
        return new A11yReminderPolicy(time, () -> cfg);
    }

    @Test public void 刚开机静默期内不提醒() {
        time.uptime = 5 * MIN;
        assertFalse(policy().shouldRemind(A11yState.DISABLED));
    }

    @Test public void 静默期过后提醒一次() {
        time.uptime = 11 * MIN;
        A11yReminderPolicy p = policy();
        assertTrue(p.shouldRemind(A11yState.DISABLED));
        p.noteReminded();
        assertFalse("刚提醒过不能连续轰炸", p.shouldRemind(A11yState.DISABLED));
    }

    @Test public void 超过30分钟再提醒() {
        time.uptime = 11 * MIN;
        A11yReminderPolicy p = policy();
        assertTrue(p.shouldRemind(A11yState.DISABLED));
        p.noteReminded();
        time.now += 29 * MIN;
        assertFalse(p.shouldRemind(A11yState.DISABLED));
        time.now += 2 * MIN;
        assertTrue(p.shouldRemind(A11yState.DISABLED));
    }

    @Test public void 只有DISABLED才提醒() {
        time.uptime = 60 * MIN;
        A11yReminderPolicy p = policy();
        assertFalse(p.shouldRemind(A11yState.ACTIVE));
        assertFalse(p.shouldRemind(A11yState.ENABLED_BUT_SILENT));
        assertTrue(p.shouldRemind(A11yState.DISABLED));
    }

    @Test public void 静默期与提醒间隔可配() {
        cfg.set("a11y.bootGraceMs", "0");
        cfg.set("a11y.reminderIntervalMs", "60000");
        A11yReminderPolicy p = policy();
        assertTrue(p.shouldRemind(A11yState.DISABLED));
        p.noteReminded();
        time.now += 61_000;
        assertTrue(p.shouldRemind(A11yState.DISABLED));
    }

    @Test public void 记录上次提醒时间() {
        time.uptime = 11 * MIN;
        A11yReminderPolicy p = policy();
        assertEquals(0, p.lastReminderAtMs());
        p.noteReminded();
        assertEquals(time.now, p.lastReminderAtMs());
    }
}
