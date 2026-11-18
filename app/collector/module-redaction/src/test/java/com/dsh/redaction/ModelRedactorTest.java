package com.dsh.redaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 模型层的核心：模型只能"补充"掩码，采纳与否由规则校验决定。
 * 这里用假 LlmService 喂各种垃圾，验证校验逻辑全部能拒。
 */
public class ModelRedactorTest {

    private static final String RULE_TEXT =
            "联系人张伟，电话138*****678，地址广东省深圳市南山区科技园路1号";
    private static final List<String> RULE_TOKENS = Collections.singletonList("138*****678");

    private TestSupport.FakeConfig cfg = new TestSupport.FakeConfig();
    private TestSupport.FakeLlmService llm = new TestSupport.FakeLlmService();

    private ModelRedactor redactor() {
        return new ModelRedactor(() -> cfg, () -> llm, new TestSupport.FakeLog());
    }

    private ModelRedactor.Result run(String modelOutput) {
        llm.response = LlmResponse.ok(modelOutput, "fake-model");
        return redactor().redactBlocking(RULE_TEXT, RULE_TOKENS, 2_000);
    }

    // ---------------- 正常采纳 ----------------

    @Test public void 列出人名_被采纳为MODEL级且只加掩码() {
        ModelRedactor.Result r = run("张伟");
        assertTrue(r.rejectReason, r.isModelLevel());
        assertEquals(1, r.addedMasks);
        assertEquals(RULE_TEXT.length(), r.text.length());     // 等长替换，不会变短
        assertTrue(r.text.contains("张*"));
        assertTrue(r.text.contains("138*****678"));            // 规则掩码原样保留
    }

    @Test public void 多行片段一次补充多个掩码() {
        ModelRedactor.Result r = run("- 张伟\n- 深圳市南山区科技园");
        assertTrue(r.rejectReason, r.isModelLevel());
        assertEquals(2, r.addedMasks);
        assertEquals(RULE_TEXT.length(), r.text.length());
        assertTrue(r.text.contains(MaskTokens.maskFragment("张伟")));
        assertTrue(r.text.contains(MaskTokens.maskFragment("深圳市南山区科技园")));
    }

    @Test public void 模型说NONE_视为看过但没有新增_升级为MODEL级() {
        ModelRedactor.Result r = run("NONE");
        assertTrue(r.isModelLevel());
        assertEquals(0, r.addedMasks);
        assertEquals(RULE_TEXT, r.text);       // 文本不变
    }

    @Test public void 新增掩码数量上限可配() {
        cfg.set("redact.maxNewMasks", "1");
        ModelRedactor.Result r = run("张伟\n科技园路\n广东省");
        assertTrue(r.isModelLevel());
        assertEquals(1, r.addedMasks);
    }

    // ---------------- 垃圾输出必须全部拒绝 ----------------

    @Test public void 空输出被拒() {
        ModelRedactor.Result r = run("   ");
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
        assertTrue(r.rejectReason.contains("empty"));
    }

    @Test public void 模型调用错误被拒() {
        llm.response = LlmResponse.error("timeout");
        ModelRedactor.Result r = redactor().redactBlocking(RULE_TEXT, RULE_TOKENS, 2_000);
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
        assertTrue(r.rejectReason.contains("timeout"));
    }

    @Test public void 模型服务不存在时返回RULE且不炸() {
        ModelRedactor r = new ModelRedactor(() -> cfg, () -> null, new TestSupport.FakeLog());
        ModelRedactor.Result res = r.redactBlocking(RULE_TEXT, RULE_TOKENS, 100);
        assertEquals(RedactionLevel.RULE, res.level);
        assertEquals(RULE_TEXT, res.text);
    }

    @Test public void 模型回吐整段文本_不算片段_拒绝() {
        ModelRedactor.Result r = run(RULE_TEXT);
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
    }

    @Test public void 模型回吐一段摘要_不是原文片段_拒绝() {
        ModelRedactor.Result r = run("这是一段包含联系人信息的文本，建议人工检查。");
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
        assertTrue(r.rejectReason.contains("no usable fragment"));
    }

    @Test public void 模型试图把掩码还原成明文_拒绝且规则掩码不动() {
        // 片段是原文里的数字串（规则掩码是 138*****678，模型无法看到明文，这里模拟它硬编）
        ModelRedactor.Result r = run("13812345678");
        assertEquals(RedactionLevel.RULE, r.level);
        assertTrue(r.text.contains("138*****678"));
        assertFalse(r.text.contains("13812345678"));
    }

    @Test public void 片段与规则掩码区间重叠_不替换_规则优先() {
        // "138" 是受保护区间的前缀：能被定位，但不允许覆盖规则掩码
        ModelRedactor.Result r = run("138");
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
        assertTrue(r.rejectReason.contains("overlap"));
    }

    @Test public void 片段本身是掩码形态_拒绝() {
        ModelRedactor.Result r = run("138*****678");
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
    }

    @Test public void 超长输出当作垃圾丢弃_防小模型回吐prompt() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append('x');
        ModelRedactor.Result r = run(sb.toString());
        assertEquals(RedactionLevel.RULE, r.level);
        assertTrue(r.rejectReason.contains("too long"));
    }

    // ---------------- 输出校验（可直接单测的纯逻辑） ----------------

    @Test public void 校验_变短被拒() {
        String why = ModelRedactor.validate("138*****678 剩余文本", "138*****678", 1, RULE_TOKENS);
        assertNotNull(why);
        assertTrue(why.contains("变短"));
    }

    @Test public void 校验_掩码被还原成明文被拒() {
        String why = ModelRedactor.validate("联系 138*****678", "联系 13812345678", 1, RULE_TOKENS);
        assertNotNull(why);
        assertTrue(why.contains("规则掩码被改动"));
    }

    @Test public void 校验_长度变化被拒() {
        String why = ModelRedactor.validate("abc", "abcd", 0, null);
        assertNotNull(why);
        assertTrue(why.contains("长度变化"));
    }

    @Test public void 校验_等长且掩码保留则通过() {
        String before = "张*和138*****678";
        String after = "张*和138*****678";
        assertEquals(null, ModelRedactor.validate(before, after, 1, RULE_TOKENS));
    }

    // ---------------- 请求参数 / 配置 ----------------

    @Test public void prompt与taskId来自配置_默认是极简占位() {
        run("NONE");
        assertEquals(ModelRedactor.TASK_ID, llm.requests.get(0).taskId);
        assertEquals(ModelRedactor.DEFAULT_PROMPT, llm.requests.get(0).systemPrompt);
        assertEquals(RULE_TEXT, llm.requests.get(0).userPrompt);
        assertEquals(0f, llm.requests.get(0).temperature, 0f);

        cfg.set("prompt.redact", "评测定的最终 prompt");
        run("NONE");
        assertEquals("评测定的最终 prompt", llm.requests.get(1).systemPrompt);
    }

    @Test public void maxTokens可配() {
        cfg.set("redact.maxTokens", "64");
        run("NONE");
        assertEquals(64, llm.requests.get(0).maxTokens);
    }

    @Test public void 阻塞版超时返回RULE_不会卡住批处理() {
        llm.delayMs = 400;
        long t0 = System.currentTimeMillis();
        ModelRedactor.Result r = redactor().redactBlocking(RULE_TEXT, RULE_TOKENS, 60);
        long ms = System.currentTimeMillis() - t0;
        assertEquals(RedactionLevel.RULE, r.level);
        assertTrue(r.rejectReason.contains("timeout"));
        assertTrue("应该按配置超时返回，实际 " + ms + "ms", ms < 1_000);
    }

    @Test public void LlmService抛异常也不外泄() {
        llm.throwOnComplete = true;
        ModelRedactor.Result r = redactor().redactBlocking(RULE_TEXT, RULE_TOKENS, 500);
        assertEquals(RedactionLevel.RULE, r.level);
        assertEquals(RULE_TEXT, r.text);
    }

    @Test public void 批量回复里的列表符号与引号被清理() {
        ModelRedactor.Result r = run("1. \"张伟\"\n- 科技园路");
        assertTrue(r.rejectReason, r.isModelLevel());
        assertEquals(2, r.addedMasks);
    }

    @Test public void 重复片段只计一次() {
        ModelRedactor.Result r = run("张伟\n张伟");
        assertTrue(r.isModelLevel());
        assertEquals(1, r.addedMasks);
    }

    @Test public void 规则层没有掩码时也能工作() {
        llm.response = LlmResponse.ok("张三", "m");
        ModelRedactor.Result r = redactor().redactBlocking("张三在这里",
                Arrays.asList(), 1_000);
        assertTrue(r.isModelLevel());
        assertEquals("张*在这里", r.text);
        assertEquals("张三在这里".length(), r.text.length());
    }
}
