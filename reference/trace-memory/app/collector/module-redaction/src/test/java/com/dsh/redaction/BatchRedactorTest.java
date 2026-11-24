package com.dsh.redaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.Health;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.llm.LlmResponse;

import org.junit.Test;

/**
 * 批处理：只在"充电 + 息屏 + 电量 > 阈值 + 模型可用"时触发，且逐条可观测。
 * 用假电源/假 MemoryQuery/假 LlmService，不真连网、不真睡。
 */
public class BatchRedactorTest {

    private final TestSupport.FakeConfig cfg = new TestSupport.FakeConfig();
    private final TestSupport.FakeMem mem = new TestSupport.FakeMem();
    private final TestSupport.FakePower power = new TestSupport.FakePower();
    private final TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
    private final TestSupport.FakeLog log = new TestSupport.FakeLog();
    private final TestSupport.FakeProc proc = new TestSupport.FakeProc();
    private final Metrics metrics = new Metrics(proc);

    private BatchRedactor batch() {
        cfg.set("redact.batch.sleepMs", "0");
        cfg.set("redact.timeoutMs", "1000");
        ModelRedactor redactor = new ModelRedactor(() -> cfg, () -> llm, log);
        return new BatchRedactor(() -> mem, () -> llm, redactor, power, metrics, () -> cfg, log);
    }

    private void twoRuleBlocks() {
        mem.ruleBlocks.add(TestSupport.block(1, "张伟在A"));
        mem.ruleBlocks.add(TestSupport.block(2, "李娜在B"));
    }

    // ---------------- 触发条件 ----------------

    @Test public void 不充电不触发() {
        power.charging = false;
        BatchRedactor.Report r = batch().runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("not_charging"));
        assertTrue(mem.updatedEpisodeIds.isEmpty());
        assertEquals(0, metrics.snapshot().batchCount);
    }

    @Test public void 亮屏不触发() {
        power.screenOff = false;
        BatchRedactor.Report r = batch().runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("screen_on"));
    }

    @Test public void 电量不高于阈值不触发() {
        power.pct = 50;   // 阈值默认 50，必须严格大于
        BatchRedactor.Report r = batch().runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("battery"));
        assertTrue(mem.updatedEpisodeIds.isEmpty());
    }

    @Test public void 三个条件都满足才跑() {
        twoRuleBlocks();
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertTrue(r.reason, r.ran);
        assertEquals(2, r.picked);
        assertEquals(2, r.upgraded);
        assertEquals(0, r.failed);
        assertEquals(2, mem.updatedEpisodeIds.size());
        for (Integer level : mem.updatedLevels) assertEquals(RedactionLevel.MODEL, (int) level);
        assertEquals(2, metrics.snapshot().batchCount);
    }

    @Test public void 模型不可用时跳过_不空跑20次超时() {
        twoRuleBlocks();
        llm.health = Health.failed("local down");
        BatchRedactor.Report r = batch().runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("llm_not_available"));
        assertEquals(0, llm.calls.get());
    }

    @Test public void 降级状态DEGRADED仍允许跑_可能还有云端可用() {
        twoRuleBlocks();
        llm.health = Health.degraded("本地尚未探测");
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertTrue(r.reason, r.ran);
    }

    @Test public void 没有LlmService时跳过() {
        cfg.set("redact.batch.sleepMs", "0");
        ModelRedactor redactor = new ModelRedactor(() -> cfg, () -> null, log);
        BatchRedactor b = new BatchRedactor(() -> mem, () -> null, redactor,
                power, metrics, () -> cfg, log);
        BatchRedactor.Report r = b.runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("no_llm_service"));
    }

    @Test public void 没有记忆服务时跳过() {
        cfg.set("redact.batch.sleepMs", "0");
        ModelRedactor redactor = new ModelRedactor(() -> cfg, () -> llm, log);
        BatchRedactor b = new BatchRedactor(() -> null, () -> llm, redactor,
                power, metrics, () -> cfg, log);
        BatchRedactor.Report r = b.runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("no_memory_service"));
    }

    @Test public void 关闭开关后不触发() {
        cfg.set("redact.batch.enabled", "false");
        BatchRedactor.Report r = batch().runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason.contains("disabled"));
        assertFalse(batch().triggersSatisfied());
    }

    @Test public void triggersSatisfied只做便宜检查() {
        BatchRedactor b = batch();
        assertTrue(b.triggersSatisfied());
        power.charging = false;
        assertFalse(b.triggersSatisfied());
        power.charging = true;
        power.screenOff = false;
        assertFalse(b.triggersSatisfied());
        power.screenOff = true;
        power.pct = 10;
        assertFalse(b.triggersSatisfied());
    }

    // ---------------- 处理逻辑 ----------------

    @Test public void 每次最多处理N条() {
        for (int i = 1; i <= 5; i++) mem.ruleBlocks.add(TestSupport.block(i, "张伟" + i));
        cfg.set("redact.batch.maxPerRun", "2");
        llm.response = LlmResponse.ok("张伟", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertEquals(2, r.picked);
        assertEquals(2, mem.updatedEpisodeIds.size());
    }

    @Test public void 模型拒绝时只计失败_不更新() {
        twoRuleBlocks();
        llm.response = LlmResponse.ok("这段文本没有隐私信息", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertTrue(r.ran);
        assertEquals(2, r.picked);
        assertEquals(0, r.upgraded);
        assertEquals(2, r.failed);
        assertTrue(mem.updatedEpisodeIds.isEmpty());
    }

    @Test public void update返回0行也算失败() {
        twoRuleBlocks();
        mem.updateRows = 0;
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertEquals(0, r.upgraded);
        assertEquals(2, r.failed);
    }

    @Test public void 模型说NONE也算模型看过了_升级等级但文本不变() {
        mem.ruleBlocks.add(TestSupport.block(7, "普通文本"));
        llm.response = LlmResponse.ok("NONE", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertEquals(1, r.upgraded);
        assertEquals("普通文本", mem.updatedTexts.get(0));
        assertEquals(RedactionLevel.MODEL, (int) mem.updatedLevels.get(0));
    }

    @Test public void 最近一次报告可查询() {
        twoRuleBlocks();
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        BatchRedactor b = batch();
        BatchRedactor.Report r = b.runOnce();
        assertEquals(r.summary(), b.lastReport().summary());
        assertTrue(r.summary().contains("upgraded=2"));
    }

    // ---------------- task-10：可观测性 / debugForce ----------------

    @Test public void 模型调用抛异常时整批不中断() {
        twoRuleBlocks();
        llm.throwOnComplete = true;
        BatchRedactor.Report r = batch().runOnce();
        assertTrue(r.ran);
        assertEquals(2, r.failed);
    }

    @Test public void 跳过也打日志_带reason() {
        power.charging = false;
        BatchRedactor.Report r = batch().runOnce();
        assertFalse(r.ran);
        assertTrue(log.lines.toString(),
                log.lines.contains("I/redaction: batch ran=false reason=not_charging picked=0 upgraded=0 failed=0"));
        assertEquals("not_charging", metrics.snapshot().batchLastReason);
        assertTrue(metrics.snapshot().batchLastAt > 0);
    }

    @Test public void 成功日志格式_fixed() {
        twoRuleBlocks();
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        batch().runOnce();
        boolean found = false;
        for (String line : log.lines) {
            if (line.startsWith("I/redaction: batch ran=true reason=ok picked=2 upgraded=2 failed=0 cost=")
                    && line.endsWith("ms")) found = true;
        }
        assertTrue(log.lines.toString(), found);
        assertEquals("ok", metrics.snapshot().batchLastReason);
    }

    @Test public void shouldRunNow条件不满足时打日志_满足时返回true() {
        BatchRedactor b = batch();
        power.charging = false;
        assertFalse(b.shouldRunNow());
        assertTrue(log.lines.toString(), log.lines.toString().contains("batch ran=false reason=not_charging"));
        assertEquals(0, llm.calls.get());

        power.charging = true;
        power.screenOff = true;
        power.pct = 80;
        int before = log.lines.size();
        assertTrue(b.shouldRunNow());
        assertEquals("shouldRunNow 为 true 时不能打 ran=false", before, log.lines.size());
    }

    @Test public void debugForce跳过电源条件_日志reason为forced_debug() {
        twoRuleBlocks();
        cfg.set("redact.batch.debugForce", "true");
        power.charging = false;
        power.screenOff = false;
        power.pct = 5;
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertTrue(r.reason, r.ran);
        assertEquals("forced_debug", r.reason);
        assertEquals(2, r.upgraded);
        boolean found = false;
        for (String line : log.lines) {
            if (line.contains("batch ran=true reason=forced_debug picked=2")) found = true;
        }
        assertTrue(log.lines.toString(), found);
        assertEquals("forced_debug", metrics.snapshot().batchLastReason);
    }

    @Test public void debugForce仍然要求LlmService可用_不能绕过模型检查() {
        cfg.set("redact.batch.debugForce", "true");
        power.charging = false;
        ModelRedactor redactor = new ModelRedactor(() -> cfg, () -> null, log);
        BatchRedactor b = new BatchRedactor(() -> mem, () -> null, redactor,
                power, metrics, () -> cfg, log);
        BatchRedactor.Report r = b.runOnce();
        assertFalse(r.ran);
        assertTrue(r.reason, r.reason.contains("no_llm_service"));

        // LlmService 在但 health=FAILED 也不能跑
        llm.health = Health.failed("down");
        BatchRedactor.Report r2 = batch().runOnce();
        assertFalse(r2.ran);
        assertTrue(r2.reason, r2.reason.contains("llm_not_available"));
    }

    @Test public void debugForce仍然只处理RULE档() {
        twoRuleBlocks();
        mem.ruleBlocks.add(memBlock(9, "已是MODEL", RedactionLevel.MODEL));
        cfg.set("redact.batch.debugForce", "true");
        power.charging = false;
        llm.response = LlmResponse.ok("张伟\n李娜", "m");
        BatchRedactor.Report r = batch().runOnce();
        assertEquals("只应挑 RULE 两条", 2, r.picked);
        for (Integer level : mem.updatedLevels) assertEquals(RedactionLevel.MODEL, (int) level);
    }

    @Test public void 忙时显式记录busy() {
        BatchRedactor b = batch();
        BatchRedactor.Report r = b.markSkipped("busy");
        assertFalse(r.ran);
        assertEquals("busy", r.reason);
        assertTrue(log.lines.toString(), log.lines.toString().contains("batch ran=false reason=busy"));
        assertEquals("busy", metrics.snapshot().batchLastReason);
    }

    private static com.dsh.host.api.data.MemoryBlock memBlock(long episodeId, String text, int level) {
        com.dsh.host.api.data.MemoryBlock b = TestSupport.block(episodeId, text);
        b.redactionLevel = level;
        return b;
    }
}
