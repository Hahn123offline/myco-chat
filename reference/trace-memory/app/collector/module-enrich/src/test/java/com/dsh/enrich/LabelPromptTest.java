package com.dsh.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.llm.LlmRequest;

import org.junit.Test;

/** prompt 走配置、可覆盖；正文只作为 user prompt 发送（绝不含原文/截图）。 */
public class LabelPromptTest {

    @Test public void 默认prompt来自配置缺省值且要求简短禁止标签栏() {
        assertTrue(LabelPrompt.DEFAULT.contains("不超过 18 字"));
        assertTrue(LabelPrompt.DEFAULT.contains("严禁输出应用名"));
        assertTrue(LabelPrompt.DEFAULT.contains("<maskedText>"));
    }

    @Test public void 配置prompt_label优先于默认值() {
        TestSupport.FakeConfig c = new TestSupport.FakeConfig()
                .set("prompt.label", "自定义指令\n---\n<maskedText>");
        assertEquals("自定义指令\n---\n<maskedText>", LabelPrompt.template(c));
    }

    @Test public void 配置为空时回落默认值() {
        TestSupport.FakeConfig c = new TestSupport.FakeConfig().set("prompt.label", "   ");
        assertEquals(LabelPrompt.DEFAULT, LabelPrompt.template(c));
    }

    @Test public void 正文作为userPrompt发送_占位符不进system() {
        LlmRequest req = LabelPrompt.request(LabelPrompt.DEFAULT, "影之刃零 攻略", 48);
        assertEquals("label", req.taskId);
        assertEquals("影之刃零 攻略", req.userPrompt);
        assertFalse("system prompt 不应包含正文占位符", req.systemPrompt.contains("<maskedText>"));
        assertTrue(req.systemPrompt.contains("不超过 18 字"));
        assertEquals(0f, req.temperature, 0f);
        assertEquals(48, req.maxTokens);
    }
}
