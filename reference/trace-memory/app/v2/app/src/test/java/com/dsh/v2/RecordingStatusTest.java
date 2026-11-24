package com.dsh.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RecordingStatusTest {

    @Test public void 最近60秒有事件_即使系统说关也ACTIVE() {
        assertEquals(RecordingStatus.State.ACTIVE,
                RecordingStatus.decide(5_000, false, false));
    }

    @Test public void 手动关无障碍_无事件_OFF() {
        assertEquals(RecordingStatus.State.OFF,
                RecordingStatus.decide(-1, false, true));
        assertEquals(RecordingStatus.State.OFF,
                RecordingStatus.decide(30 * 60_000L, false, true));
    }

    @Test public void 超过10分钟无事件且usage活跃_STALLED() {
        assertEquals(RecordingStatus.State.STALLED,
                RecordingStatus.decide(11 * 60_000L, true, true));
    }

    @Test public void 睡觉场景_无事件且usage无活动_IDLE不误报() {
        assertEquals(RecordingStatus.State.IDLE,
                RecordingStatus.decide(60 * 60_000L, true, false));
        assertEquals(RecordingStatus.State.IDLE,
                RecordingStatus.decide(5 * 60_000L, true, true));
    }

    @Test public void 从未收到事件但系统启用_IDLE等待不报错() {
        assertEquals(RecordingStatus.State.IDLE,
                RecordingStatus.decide(-1, true, true));
    }

    @Test public void 可读时间() {
        assertEquals("3 秒前", RecordingStatus.readableAgo(3_000));
        assertEquals("2 分钟前", RecordingStatus.readableAgo(120_000));
        assertEquals("从未", RecordingStatus.readableAgo(-1));
    }
}
