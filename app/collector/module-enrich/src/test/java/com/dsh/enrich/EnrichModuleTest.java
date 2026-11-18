package com.dsh.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Health;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.data.LabelStatus;
import com.dsh.host.api.llm.LlmService;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import org.junit.Test;

/** 模块接线：依赖缺失不崩、health 如实、周期任务/立即轮都在后台线程。 */
public class EnrichModuleTest {

    private static EnrichModule module(TestSupport.FakeHostContext ctx,
                                       TestSupport.FakeLlm llm,
                                       TestSupport.FakeMem mem,
                                       EgressPolicy policy) {
        if (llm != null) ctx.services.put(LlmService.class, llm);
        if (mem != null) ctx.services.put(MemoryQuery.class, mem);
        if (policy != null) ctx.services.put(EgressPolicy.class, policy);
        return new EnrichModule(null, () -> true);
    }

    private static void await(BooleanSupplier cond, long ms) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (!cond.get() && System.currentTimeMillis() < deadline) Thread.sleep(10);
    }

    private interface BooleanSupplier { boolean get(); }

    @Test public void 模块元信息与依赖网络能力() {
        EnrichModule m = new EnrichModule(null, () -> true);
        assertEquals("enrich", m.id());
        assertTrue(m.requires().contains(Capability.NETWORK));
        assertEquals(Health.State.STOPPED, m.health().state);
    }

    @Test public void 拿不到LlmService也不崩_health为no_llm() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        EnrichModule m = module(ctx, null, new TestSupport.FakeMem(), TestSupport.policy());
        m.install(ctx);
        Health h = m.health();
        assertEquals(Health.State.DEGRADED, h.state);
        assertTrue(h.detail, h.detail.contains("no-llm"));
    }

    @Test public void 拿不到EgressPolicy时health如实_且不发任何请求() throws Exception {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文"));
        EnrichModule m = module(ctx, llm, mem, null);
        m.install(ctx);

        Health h = m.health();
        assertEquals(Health.State.DEGRADED, h.state);
        assertTrue(h.detail, h.detail.contains("no-egress-policy"));
        await(() -> m.lastReport() != null, 2_000);
        assertNotNull(m.lastReport());
        assertEquals("no-egress-policy", m.lastReport().skipReason);
        assertEquals(0, llm.requests.size());
    }

    @Test public void 没有WHITE块时health为no_whitelist() throws Exception {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.GRAY, "正文"));
        EnrichModule m = module(ctx, new TestSupport.FakeLlm(), mem, TestSupport.policy());
        m.install(ctx);

        Health h = m.health();
        assertEquals(Health.State.DEGRADED, h.state);
        assertTrue(h.detail, h.detail.contains("no-whitelist"));
    }

    @Test public void WHITE块会被后台线程打标_线程名enrich_worker() throws Exception {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "影之刃零 攻略正文"));
        EnrichModule m = module(ctx, llm, mem, TestSupport.policy());

        long t0 = System.currentTimeMillis();
        m.install(ctx);
        long installMs = System.currentTimeMillis() - t0;
        assertTrue("install 不能同步等模型（实际 " + installMs + "ms）", installMs < 2_000);

        await(() -> llm.requests.size() >= 1, 3_000);
        assertEquals(1, llm.requests.size());
        assertEquals("enrich-worker", llm.threads.get(0));
        assertEquals("影之刃零 攻略正文", llm.userPrompts.get(0));

        await(() -> m.lastReport() != null, 2_000);
        assertNotNull(m.lastReport());
        assertEquals(1, m.lastReport().ok);
        assertEquals(LabelStatus.OK, mem.blocks.get(0).labelStatus);
        assertEquals(Health.State.OK, m.health().state);
    }

    @Test public void 周期任务只提交_仍在后台线程执行() throws Exception {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        TestSupport.FakeLlm llm = new TestSupport.FakeLlm();
        TestSupport.FakeMem mem = new TestSupport.FakeMem();
        mem.blocks.add(TestSupport.block(1, EgressTier.WHITE, "正文1"));
        mem.blocks.add(TestSupport.block(2, EgressTier.WHITE, "正文2"));
        EnrichModule m = module(ctx, llm, mem, TestSupport.policy());
        m.install(ctx);

        assertEquals(1, ctx.scheduled.size());
        assertEquals("enrich", ctx.scheduleNames.get(0));
        assertEquals(Enricher.DEFAULT_PERIOD_MS, (long) ctx.periods.get(0));

        await(() -> llm.requests.size() >= 1, 3_000);
        int before = llm.requests.size();
        ctx.scheduled.get(0).run();          // 模拟主线程触发周期任务
        await(() -> llm.requests.size() > before, 3_000);
        assertTrue(llm.requests.size() > before);
        for (String thread : llm.threads) {
            assertEquals("周期任务里也不能在主线程调模型", "enrich-worker", thread);
        }
    }
}
