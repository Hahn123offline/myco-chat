package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 状态机：系统启用 + 最近事件两个判据，映射 ACTIVE / SILENT / DISABLED。
 * 用假 checker/时钟/配置，不碰 android API。
 */
public class A11yHealthMonitorTest {

    private final TestSupport.FakeConfig cfg = new TestSupport.FakeConfig();
    private final TestSupport.FakeTime time = new TestSupport.FakeTime(100_000L, 20 * 60_000L);
    private final TestSupport.FakeChecker checker = new TestSupport.FakeChecker(true);
    private final AtomicLong lastEventAt = new AtomicLong(0);

    private A11yHealthMonitor monitor() {
        return new A11yHealthMonitor(checker, time, lastEventAt::get, () -> cfg);
    }

    @Test public void 系统未启用就是DISABLED() {
        checker.enabled = false;
        lastEventAt.set(time.now - 1_000);
        A11yHealthMonitor.Result r = monitor().evaluate();
        assertEquals(A11yState.DISABLED, r.state);
        assertEquals(1_000, r.lastEventAgeMs);
    }

    @Test public void 启用且最近有事件是ACTIVE() {
        lastEventAt.set(time.now - 1_500);
        A11yHealthMonitor.Result r = monitor().evaluate();
        assertEquals(A11yState.ACTIVE, r.state);
        assertEquals(1_500, r.lastEventAgeMs);
    }

    @Test public void 启用但从没收到事件是SILENT_不能报ACTIVE() {
        lastEventAt.set(0);
        A11yHealthMonitor.Result r = monitor().evaluate();
        assertEquals(A11yState.ENABLED_BUT_SILENT, r.state);
        assertEquals(-1, r.lastEventAgeMs);
    }

    @Test public void 启用但超过静默阈值是SILENT() {
        cfg.set("a11y.silentMs", "60000");
        lastEventAt.set(time.now - 61_000);
        assertEquals(A11yState.ENABLED_BUT_SILENT, monitor().evaluate().state);
    }

    @Test public void 刚好等于阈值仍算ACTIVE_边界() {
        cfg.set("a11y.silentMs", "60000");
        lastEventAt.set(time.now - 60_000);
        assertEquals(A11yState.ACTIVE, monitor().evaluate().state);
    }

    @Test public void 静默阈值配置热加载() {
        lastEventAt.set(time.now - 90_000);
        assertEquals(A11yState.ACTIVE, monitor().evaluate().state);   // 默认 10 分钟
        cfg.set("a11y.silentMs", "60000");
        assertEquals(A11yState.ENABLED_BUT_SILENT, monitor().evaluate().state);
    }

    @Test public void 系统查询抛异常按DISABLED处理_保守() {
        checker.throwsOnCall = true;
        assertEquals(A11yState.DISABLED, monitor().evaluate().state);
    }

    @Test public void 状态标签与语义() {
        assertEquals("ACTIVE", A11yState.ACTIVE.tag);
        assertEquals("SILENT", A11yState.ENABLED_BUT_SILENT.tag);
        assertEquals("DISABLED", A11yState.DISABLED.tag);
        assertEquals(true, A11yState.ACTIVE.isRecording());
        assertEquals(false, A11yState.ENABLED_BUT_SILENT.isRecording());
        assertEquals(false, A11yState.DISABLED.isRecording());
    }
}
