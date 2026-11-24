package com.dsh.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.data.LabelStatus;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 云端打标核心逻辑：隐私不变量 + 校验 + 失败封顶 + 后台线程由 EnrichModuleTest 覆盖。 */
public class EnricherTest {

    private static Enricher enricher(TestSupport.FakeConfig cfg, TestSupport.FakeMem mem,
                                     TestSupport.FakeLlm llm, EgressPolicy policy,
                                     TestSupport.FakeLog log) {
        return new Enricher(() -> cfg, () -> mem, () -> llm, () -> policy,
                log, () -> true, ms -> { }, () -> 12345L);
    }

    private static EgressPolicy policy() { return TestSupport.policy(); }

    @Test public void 只发送WHITE_GRAY_BLACK_UNKNOWN一个字节都不发() {
        TestSupport.FakeConfig cfg = new TestSupport.FakeConfig();
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "影之刃零 攻略正文"));
        mem.blocks.add(TestSupport.block(2, EgressTier.GRAY, "普通正文"));
        mem.blocks.add(TestSupport.block(3, EgressTier.BLACK, "聊天正文"));
        mem.blocks.add(TestSupport.block(4, EgressTier.UNKNOWN, "未知正文"));

        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        TestSupport.FakeLog log = new TestSupport.FakeLog();
        Enricher.Report r = enricher(cfg, mem, llm, policy(), log).runOnce();

        assertEquals(1, r.ok);
        assertEquals(1, llm.requests.size());
        assertEquals("影之刃零 攻略正文", llm.userPrompts.get(0));
        assertEquals(LabelStatus.OK, mem.blocks.get(0).labelStatus);
        assertNotNull(mem.blocks.get(0).label);
        for (int i = 1; i < mem.blocks.size(); i++) {
            assertNull("非 WHITE 不能有 label", mem.blocks.get(i).label);
            assertEquals(LabelStatus.PENDING, mem.blocks.get(i).labelStatus);
        }
        assertTrue(log.has("picked=1 ok=1 failed=0"));
    }

    @Test public void 拿不到EgressPolicy就什么都不做_绝不默认放行() {
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        Enricher.Report r = enricher(new TestSupport.FakeConfig(), mem, llm, null,
                new TestSupport.FakeLog()).runOnce();
        assertEquals("no-egress-policy", r.skipReason);
        assertEquals(0, llm.requests.size());
        assertNull(mem.blocks.get(0).label);
    }

    @Test public void 拿不到LlmService就不做且不崩() {
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        Enricher.Report r = new Enricher(() -> new TestSupport.FakeConfig(), () -> mem,
                () -> null, EnricherTest::policy, new TestSupport.FakeLog(),
                () -> true, ms -> { }, () -> 1L).runOnce();
        assertEquals("no-llm", r.skipReason);
        assertEquals(LabelStatus.PENDING, mem.blocks.get(0).labelStatus);
    }

    @Test public void 模型输出空_超长_复读都算FAILED不落库() {
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "第一条正文"));
        mem.blocks.add(TestSupport.block(2, EgressTier.WHITE, "第二条正文"));
        mem.blocks.add(TestSupport.block(3, EgressTier.WHITE, "第三条正文"));

        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        llm.handler = req -> {
            if (req.userPrompt.startsWith("第一条")) return LlmResponse.ok("", "m");
            if (req.userPrompt.startsWith("第二条")) return LlmResponse.ok(repeat("长", 80), "m");
            return LlmResponse.ok(req.userPrompt, "m");   // 复读
        };
        Enricher.Report r = enricher(new TestSupport.FakeConfig(), mem, llm, policy(),
                new TestSupport.FakeLog()).runOnce();

        assertEquals(3, r.failed);
        assertEquals(0, r.ok);
        for (MemoryBlock b : mem.blocks) {
            assertNull("FAILED 不落 label", b.label);
            assertEquals(LabelStatus.FAILED, b.labelStatus);
        }
    }

    @Test public void 连续失败3次后SKIPPED_之后不再被选中() {
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        llm.handler = req -> LlmResponse.error("provider down");
        Enricher e = enricher(new TestSupport.FakeConfig(), mem, llm, policy(), new TestSupport.FakeLog());

        assertEquals(1, e.runOnce().failed);
        assertEquals(LabelStatus.FAILED, mem.blocks.get(0).labelStatus);
        e.runOnce();
        assertEquals(LabelStatus.FAILED, mem.blocks.get(0).labelStatus);
        Enricher.Report third = e.runOnce();
        assertEquals(1, third.failed);
        assertEquals("第三次失败应 SKIPPED", LabelStatus.SKIPPED, mem.blocks.get(0).labelStatus);

        Enricher.Report fourth = e.runOnce();
        assertEquals("SKIPPED 后不再被选中", 0, fourth.picked);
        assertEquals(3, llm.requests.size());
    }

    @Test public void 空正文直接失败_不发送() {
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "   "));
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        Enricher.Report r = enricher(new TestSupport.FakeConfig(), mem, llm, policy(),
                new TestSupport.FakeLog()).runOnce();
        assertEquals(0, llm.requests.size());
        assertEquals(1, r.failed);
        assertEquals(LabelStatus.FAILED, mem.blocks.get(0).labelStatus);
    }

    @Test public void 取数返回非WHITE也拒绝发送_防御性双闸() {
        TestSupport.FakeMem mem = new TestSupport.FakeMem() {
            @Override public List<MemoryBlock> blocksNeedingLabel(int limit) {
                // 模拟实现 bug：把 GRAY 也返回了。Enricher 必须自己再挡一次。
                return List.of(TestSupport.block(99, EgressTier.GRAY, "不该出网的正文"));
            }
        };
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        Enricher.Report r = enricher(new TestSupport.FakeConfig(), mem, llm, policy(),
                new TestSupport.FakeLog()).runOnce();
        assertEquals(0, llm.requests.size());
        assertEquals(0, r.picked);
    }

    @Test public void requireWifi时没有wifi就不发() {
        TestSupport.FakeConfig cfg = new TestSupport.FakeConfig().set("enrich.requireWifi", "true");
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        AtomicBoolean wifi = new AtomicBoolean(false);
        Enricher e = new Enricher(() -> cfg, () -> mem, () -> llm, EnricherTest::policy,
                new TestSupport.FakeLog(), wifi::get, ms -> { }, () -> 1L);
        assertEquals("no-wifi", e.runOnce().skipReason);
        assertEquals(0, llm.requests.size());
        wifi.set(true);
        assertEquals(1, e.runOnce().ok);
    }

    @Test public void disabled时整轮跳过() {
        TestSupport.FakeConfig cfg = new TestSupport.FakeConfig().set("enrich.enabled", "false");
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        assertEquals("disabled", enricher(cfg, mem, llm, policy(), new TestSupport.FakeLog())
                .runOnce().skipReason);
        assertEquals(0, llm.requests.size());
    }

    @Test public void prompt优先自身配置_否则回落models配置() {
        TestSupport.FakeConfig own = new TestSupport.FakeConfig();
        TestSupport.FakeConfig models = new TestSupport.FakeConfig()
                .set("prompt.label", "来自 models 的指令\n<maskedText>");
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        Enricher e = new Enricher(() -> own, () -> models, () -> mem, () -> llm,
                EnricherTest::policy, new TestSupport.FakeLog(), () -> true, ms -> { }, () -> 1L);

        e.runOnce();
        assertTrue(llm.systemPrompts.get(0), llm.systemPrompts.get(0).contains("来自 models 的指令"));

        // 复位成未打标，再跑一轮验证"自身配置优先"
        mem.blocks.get(0).label = null;
        mem.blocks.get(0).labelStatus = LabelStatus.PENDING;
        own.set("prompt.label", "来自 enrich 的指令\n<maskedText>");
        e.runOnce();
        assertTrue(llm.systemPrompts.get(1), llm.systemPrompts.get(1).contains("来自 enrich 的指令"));
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }
}
