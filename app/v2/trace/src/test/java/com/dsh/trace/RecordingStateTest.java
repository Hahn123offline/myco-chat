package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 记录状态判据：事件优先于系统声明；三处（通知/状态页/Health）共用同一套。 */
public class RecordingStateTest {

    private static final long MIN = 60_000L;

    @Test public void 最近有事件就是ACTIVE_不管系统怎么说() {
        assertEquals(RecordingState.ACTIVE,
                RecordingState.decide(1_000, false, true, false));
        assertEquals(RecordingState.ACTIVE,
                RecordingState.decide(0, false, false, false));
        assertEquals(RecordingState.ACTIVE,
                RecordingState.decide(MIN - 1, false, false, false));
    }

    @Test public void 超过60秒且亮屏且服务在跑才是STALLED() {
        assertEquals(RecordingState.STALLED,
                RecordingState.decide(MIN, true, true, true));
        assertEquals(RecordingState.ENABLED_BUT_SILENT,
                RecordingState.decide(MIN, true, false, true));   // 熄屏不判断流
        assertEquals(RecordingState.ENABLED_BUT_SILENT,
                RecordingState.decide(MIN, true, true, false));   // 服务没跑不判断流
    }

    @Test public void 从未收到事件_系统启用是SILENT() {
        assertEquals(RecordingState.ENABLED_BUT_SILENT,
                RecordingState.decide(-1, true, true, true));
    }

    @Test public void 从未收到事件_系统未启用是DISABLED() {
        assertEquals(RecordingState.DISABLED,
                RecordingState.decide(-1, false, true, true));
    }

    @Test public void 超过60秒没事件_系统未启用是DISABLED而不是STALLED() {
        assertEquals(RecordingState.DISABLED,
                RecordingState.decide(2 * MIN, false, true, true));
    }

    @Test public void 状态语义() {
        assertTrue(RecordingState.ACTIVE.isRecording());
        assertFalse(RecordingState.STALLED.isRecording());
        assertTrue(RecordingState.STALLED.isStalled());
        assertEquals("ENABLED_BUT_SILENT", RecordingState.ENABLED_BUT_SILENT.label());
    }
}
