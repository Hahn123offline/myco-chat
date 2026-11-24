package com.dsh.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.llm.LlmRequest;

import org.junit.Test;

/** 打标输出的规则校验（不做语义理解，只否决"能证明不是内容"的东西）。 */
public class LabelValidatorTest {

    @Test public void 合法短标签通过() {
        assertNull(LabelValidator.problem("影之刃零最新实机演示", "正文内容"));
        assertNull(LabelValidator.problem("  「这屏在讲怎么抽卡」  ", "正文内容"));
    }

    @Test public void 空与空白失败() {
        assertEquals("empty", LabelValidator.problem(null, "正文"));
        assertEquals("empty", LabelValidator.problem("   ", "正文"));
    }

    @Test public void 超长失败() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < LabelValidator.MAX_CHARS + 1; i++) b.append('字');
        assertEquals("too-long", LabelValidator.problem(b.toString(), "正文"));
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < LabelValidator.MAX_CHARS; i++) ok.append('字');
        assertNull(LabelValidator.problem(ok.toString(), "正文"));
    }

    @Test public void 纯标点失败() {
        assertEquals("punctuation-only", LabelValidator.problem("！！！。。。", "正文"));
    }

    @Test public void 与maskedText完全一样算复读失败() {
        assertEquals("echo", LabelValidator.problem("今天天气不错", "今天天气不错"));
        assertEquals("echo", LabelValidator.problem(" 今天天气不错 ", "今天天气不错"));
    }

    @Test public void clean去掉换行包裹引号并合并空白() {
        assertEquals("标题内容", LabelValidator.clean("  \"标题内容\"  "));
        assertEquals("第一行 第二行", LabelValidator.clean("第一行\n第二行"));
        assertEquals("", LabelValidator.clean(null));
    }
}
