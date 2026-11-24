package com.dsh.redaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MetricsTest {

    private final TestSupport.FakeProc proc = new TestSupport.FakeProc();
    private final Metrics metrics = new Metrics(proc);

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void 解析proc_self_stat的utime与stime() {
        assertEquals(150, Metrics.parseCpuTicks(TestSupport.statLine(100, 50)));
        assertEquals(-1, Metrics.parseCpuTicks(null));
        assertEquals(-1, Metrics.parseCpuTicks("garbage"));
    }

    @Test public void tick换算成毫秒_假设USER_HZ为100() {
        assertEquals(1000, Metrics.ticksToMs(100));
        assertEquals(1500, Metrics.ticksToMs(150));
        assertEquals(0, Metrics.ticksToMs(0));
        assertEquals(0, Metrics.ticksToMs(-5));
    }

    @Test public void 解析proc_self_status的内存() {
        String status = TestSupport.statusLine(12_345, 67_890);
        assertEquals(12_345, Metrics.kbFromStatus(status, "VmRSS"));
        assertEquals(67_890, Metrics.kbFromStatus(status, "VmHWM"));
        assertEquals(-1, Metrics.kbFromStatus(status, "VmPeakMissing"));
        assertEquals(-1, Metrics.kbFromStatus(null, "VmRSS"));
    }

    @Test public void 一次计时的墙钟与CPU差分都被记账() {
        proc.stat = TestSupport.statLine(100, 0);
        Metrics.Timer t = metrics.start();
        proc.stat = TestSupport.statLine(130, 0);   // 30 ticks = 300ms
        t.stop(Metrics.LANE_REALTIME);

        Metrics.Snapshot s = metrics.snapshot();
        assertEquals(1, s.realtimeCount);
        assertEquals(300, s.realtimeCpuMs);
    }

    @Test public void 重复stop只记一次() {
        proc.stat = TestSupport.statLine(0, 0);
        Metrics.Timer t = metrics.start();
        t.stop(Metrics.LANE_REALTIME);
        t.stop(Metrics.LANE_REALTIME);
        assertEquals(1, metrics.snapshot().realtimeCount);
    }

    @Test public void 平均耗时与P95() {
        proc.status = TestSupport.statusLine(100, 200);
        for (int i = 1; i <= 100; i++) metrics.record(Metrics.LANE_REALTIME, i, 0);
        Metrics.Snapshot s = metrics.snapshot();
        assertEquals(100, s.realtimeCount);
        assertEquals(50, s.realtimeAvgMs);
        assertEquals(95, s.realtimeP95Ms);
    }

    @Test public void 实时与批处理分开统计() {
        metrics.record(Metrics.LANE_REALTIME, 10, 1);
        metrics.record(Metrics.LANE_BATCH, 20, 2);
        metrics.record(Metrics.LANE_BATCH, 40, 4);
        Metrics.Snapshot s = metrics.snapshot();
        assertEquals(1, s.realtimeCount);
        assertEquals(2, s.batchCount);
        assertEquals(30, s.batchAvgMs);
        assertEquals(1, s.realtimeCpuMs);
        assertEquals(6, s.batchCpuMs);
    }

    @Test public void 快照包含内存与唤醒锁() {
        proc.status = TestSupport.statusLine(2048, 4096);
        metrics.setWakeLocksHeld(3);
        Metrics.Snapshot s = metrics.snapshot();
        assertEquals(2048, s.rssKb);
        assertEquals(4096, s.hwmKb);
        assertEquals(3, s.wakeLocksHeld);
    }

    @Test public void 导出文本字段齐全_便于adb拉取分析() {
        metrics.record(Metrics.LANE_REALTIME, 12, 3);
        metrics.record(Metrics.LANE_BATCH, 34, 5);
        String text = metrics.exportText();
        assertTrue(text.contains("realtime_count=1"));
        assertTrue(text.contains("realtime_avg_ms=12"));
        assertTrue(text.contains("realtime_p95_ms=12"));
        assertTrue(text.contains("realtime_cpu_ms=3"));
        assertTrue(text.contains("batch_count=1"));
        assertTrue(text.contains("batch_avg_ms=34"));
        assertTrue(text.contains("batch_p95_ms=34"));
        assertTrue(text.contains("batch_cpu_ms=5"));
        assertTrue(text.contains("process_hwm_kb="));
        assertTrue(text.contains("wake_locks_held="));
    }

    @Test public void 导出文本包含批处理最后原因与时间() {
        metrics.record(Metrics.LANE_BATCH, 7, 1);
        metrics.recordBatchOutcome("screen_on", 123456789L);
        String text = metrics.exportText();
        assertTrue(text, text.contains("batch_last_reason=screen_on"));
        assertTrue(text, text.contains("batch_last_at=123456789"));
        assertEquals("screen_on", metrics.snapshot().batchLastReason);
        assertEquals(123456789L, metrics.snapshot().batchLastAt);
    }

    @Test public void 可以写到文件() throws Exception {
        metrics.record(Metrics.LANE_BATCH, 7, 1);
        metrics.recordBatchOutcome("ok", 1L);
        File f = new File(tmp.getRoot(), "redaction/metrics.txt");
        assertTrue(metrics.writeTo(f));
        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("batch_count=1"));
        assertTrue(content.contains("batch_last_reason=ok"));
        assertTrue(content.contains("batch_last_at=1"));
        assertTrue(f.getParentFile().exists());
    }

    @Test public void summaryLine包含两层计数() {
        metrics.record(Metrics.LANE_REALTIME, 10, 1);
        metrics.record(Metrics.LANE_BATCH, 20, 2);
        String line = metrics.summaryLine(metrics.snapshot());
        assertNotNull(line);
        assertTrue(line.contains("rt n=1"));
        assertTrue(line.contains("batch n=1"));
    }
}
