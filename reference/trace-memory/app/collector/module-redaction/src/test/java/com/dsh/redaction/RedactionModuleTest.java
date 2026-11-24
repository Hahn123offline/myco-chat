package com.dsh.redaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.Health;
import com.dsh.host.api.data.Episode;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.events.EpisodeClosed;
import com.dsh.host.api.events.EpisodeRedactionUpgraded;
import com.dsh.host.api.events.EpisodeRedacted;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证两级脱敏的接线：
 *  - 实时层先发 RULE（不等模型），规则语义与 evidenceRef 透传不变；
 *  - 模型健康 + 结果可采纳 → 之后发 EpisodeRedactionUpgraded；
 *  - 模型不健康/不可用/返回垃圾 → 只有 RULE，绝不炸、绝不阻塞。
 */
public class RedactionModuleTest {

    private Episode episode(long id, String pkg, String text) {
        Episode e = new Episode();
        e.id = id;
        e.pkg = pkg;
        e.textSnippet = text;
        return e;
    }

    private static <T> List<T> eventsOf(TestSupport.FakeCtx ctx, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Object e : ctx.bus.published) {
            if (type.isInstance(e)) out.add(type.cast(e));
        }
        return out;
    }

    private static EpisodeRedacted redactedOf(TestSupport.FakeCtx ctx) {
        List<EpisodeRedacted> l = eventsOf(ctx, EpisodeRedacted.class);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    private static EpisodeRedactionUpgraded upgradeOf(TestSupport.FakeCtx ctx) {
        List<EpisodeRedactionUpgraded> l = eventsOf(ctx, EpisodeRedactionUpgraded.class);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    // ---------------- 实时层：规则级先落 ----------------

    @Test public void 脱敏事件原样透传evidenceRef() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        new RedactionModule().install(ctx);

        ctx.bus.publish(new EpisodeClosed(episode(42, "com.tencent.mm", "联系我 13812345678"),
                "trace:42"));

        EpisodeRedacted redacted = redactedOf(ctx);
        assertNotNull("应该发出 EpisodeRedacted", redacted);
        assertEquals("trace:42", redacted.evidenceRef);
        assertEquals(42, redacted.episodeId);
        // 既有脱敏语义不变：手机号被掩码
        assertTrue(redacted.maskedText.contains("138*****678"));
        assertEquals(RedactionLevel.RULE, redacted.redactionLevel);
    }

    @Test public void 没有证据引用时保持null_不伪造() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        new RedactionModule().install(ctx);

        ctx.bus.publish(new EpisodeClosed(episode(7, "com.android.mms", "普通文本")));

        EpisodeRedacted redacted = redactedOf(ctx);
        assertNotNull(redacted);
        assertNull(redacted.evidenceRef);
        assertEquals(RedactionLevel.RULE, redacted.redactionLevel);
    }

    // ---------------- 模型层：异步升级 ----------------

    @Test public void 模型健康且结果可采纳时补发升级事件() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        llm.response = LlmResponse.ok("张伟", "m");
        ctx.services.put(LlmService.class, llm);

        RedactionModule module = new RedactionModule();
        module.install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(5, "com.x", "张伟在深港驾校")));

        EpisodeRedacted rule = redactedOf(ctx);
        assertNotNull(rule);
        assertEquals(RedactionLevel.RULE, rule.redactionLevel);
        assertTrue(rule.maskedText.contains("张伟"));   // 规则层不认识人名

        EpisodeRedactionUpgraded up = upgradeOf(ctx);
        assertNotNull("模型补充成功应发升级事件", up);
        assertEquals(5, up.episodeId);
        assertEquals(RedactionLevel.MODEL, up.redactionLevel);
        assertTrue(up.maskedText.contains("张*"));
        assertEquals(rule.maskedText.length(), up.maskedText.length());
        assertEquals(1, module.modelUpgrades());
        assertEquals(1, llm.calls.get());
    }

    @Test public void 模型升级不破坏规则层掩码() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        llm.response = LlmResponse.ok("张伟", "m");
        ctx.services.put(LlmService.class, llm);

        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(9, "com.x", "联系张伟 13812345678")));

        EpisodeRedactionUpgraded up = upgradeOf(ctx);
        assertNotNull(up);
        assertTrue(up.maskedText.contains("张*"));
        assertTrue("规则掩码必须原样保留", up.maskedText.contains("138*****678"));
    }

    @Test public void 模型不健康时只落规则级且不调模型() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        llm.health = Health.degraded("本地模型未探测");
        ctx.services.put(LlmService.class, llm);

        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));

        assertNull(upgradeOf(ctx));
        assertEquals(0, llm.calls.get());
        assertEquals(RedactionLevel.RULE, redactedOf(ctx).redactionLevel);
    }

    @Test public void 没有LlmService时只落规则级_不炸() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));
        assertNotNull(redactedOf(ctx));
        assertNull(upgradeOf(ctx));
    }

    @Test public void 模型返回垃圾时保持RULE_不升级() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        llm.response = LlmResponse.ok("这段文本没有可用的隐私片段信息补充", "m");
        ctx.services.put(LlmService.class, llm);

        RedactionModule module = new RedactionModule();
        module.install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));

        assertNull(upgradeOf(ctx));
        assertEquals(1, module.modelRejected());
        assertEquals(0, module.modelUpgrades());
    }

    @Test public void 模型开关关闭时不调模型() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        ctx.config.set("redact.model.enabled", "false");
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        ctx.services.put(LlmService.class, llm);

        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));
        assertEquals(0, llm.calls.get());
        assertNull(upgradeOf(ctx));
    }

    @Test public void 超长文本跳过模型_保证实时性() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        ctx.config.set("redact.maxChars", "10");
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        ctx.services.put(LlmService.class, llm);

        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x",
                "这是一段明显超过十个字的文本内容")));
        assertEquals(0, llm.calls.get());
        assertNotNull(redactedOf(ctx));
    }

    @Test public void 模型返回错误时只落规则级() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        llm.response = LlmResponse.error("timeout 10s");
        ctx.services.put(LlmService.class, llm);

        RedactionModule module = new RedactionModule();
        module.install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));

        assertNull(upgradeOf(ctx));
        assertEquals(1, module.modelRejected());
    }

    @Test public void prompt优先读models配置_评测后写在模型配置里也能生效() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        ctx.modelsConfig.set("prompt.redact", "评测定的最终脱敏 prompt");
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        ctx.services.put(LlmService.class, llm);

        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));

        assertEquals(1, llm.requests.size());
        assertEquals("评测定的最终脱敏 prompt", llm.requests.get(0).systemPrompt);
        assertEquals(ModelRedactor.TASK_ID, llm.requests.get(0).taskId);
    }

    @Test public void prompt本模块配置优先于models配置() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        ctx.config.set("prompt.redact", "本模块覆盖的 prompt");
        ctx.modelsConfig.set("prompt.redact", "评测定的最终脱敏 prompt");
        TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();
        ctx.services.put(LlmService.class, llm);

        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(1, "com.x", "张伟在深港驾校")));
        assertEquals("本模块覆盖的 prompt", llm.requests.get(0).systemPrompt);
    }

    @Test public void 高隐私应用仍标high_既有语义不变() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        new RedactionModule().install(ctx);
        ctx.bus.publish(new EpisodeClosed(episode(3, "com.tencent.mm", "普通聊天")));
        EpisodeRedacted r = redactedOf(ctx);
        assertNotNull(r);
        assertEquals(EpisodeRedacted.TIER_HIGH, r.privacyTier);
    }

    // ---------------- 无安卓环境时不启批处理 ----------------

    @Test public void 无Context构造时不启批处理与观测调度() {
        TestSupport.FakeCtx ctx = new TestSupport.FakeCtx();
        RedactionModule module = new RedactionModule();
        module.install(ctx);
        assertNull(module.batch());
        assertNull(module.lastBatchReport());
        assertNotNull(module.metrics());   // 观测始终在，供单测/状态页
    }
}
