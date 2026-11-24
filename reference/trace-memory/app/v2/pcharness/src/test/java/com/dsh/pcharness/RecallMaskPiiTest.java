package com.dsh.pcharness;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.guard.Guard;
import com.dsh.masker.MaskBlock;
import com.dsh.masker.MaskPolicy;
import com.dsh.masker.Masker;
import com.dsh.memory.DetailLevel;
import com.dsh.memory.Evidence;
import com.dsh.memory.Kind;
import com.dsh.memory.MaskProvider;
import com.dsh.memory.MemItem;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;

import org.junit.Test;

/** PII 语义：手机号可掩码；卡号 guard BLOCK 必须整条占位符。 */
public class RecallMaskPiiTest {

    private static RecallHit hit(String text) {
        MemItem m = new MemItem("trace", Kind.BLOCK, "k", text, "{\"pkg\":\"com.tencent.mm\"}");
        m.id = 1;
        m.detailLevel = DetailLevel.FULL;
        m.t0 = m.t1 = System.currentTimeMillis();
        m.evidence.add(new Evidence("trace", "1", m.t0));
        return new RecallHit(1, 1, 1, 1, 1, false, DetailLevel.FULL, "trace", text,
                "com.tencent.mm", "p", "s", "", m.t0);
    }

    private static final MaskProvider MASKER = (id, pkg, t0, t1, raw) -> {
        String masked = new Masker().mask(MaskBlock.ofText(raw), MaskPolicy.defaults()).text;
        return new Guard().check(masked).isBlocked()
                ? com.dsh.memory.Masked.none()
                : new com.dsh.memory.Masked(masked, com.dsh.memory.MaskMode.TEXT_ONLY);
    };

    @Test public void FULL与TEXT_ONLY掩码一致() {
        String raw = "电话 13812345678 微信消息";
        String full = new Masker().mask(new com.dsh.masker.MaskBlock("", raw,
                java.util.Collections.singletonList(new com.dsh.masker.MaskElement(
                        "t", raw, 0, 0, 100, 100, "PARAGRAPH",
                        java.util.Collections.emptyList()))),
                MaskPolicy.defaults()).text;
        String textOnly = new Masker().mask(MaskBlock.ofText(raw),
                MaskPolicy.defaults()).text;
        org.junit.Assert.assertEquals(full, textOnly);
    }

    @Test public void 手机号被掩码() {
        RecallCore.Sanitized s = RecallCore.sanitize(
                java.util.Collections.singletonList(hit("电话 13812345678 你好")), MASKER);
        String injected = s.hits.get(0).text;
        assertFalse(injected.contains("13812345678"));
        assertTrue(s.sources.contains("com.tencent.mm"));
    }

    @Test public void 卡号整条占位符() {
        RecallCore.Sanitized s = RecallCore.sanitize(
                java.util.Collections.singletonList(hit("卡号 4111111111111111")), MASKER);
        String injected = s.hits.get(0).text;
        assertTrue(injected.contains("拦截") || injected.contains("缺少有效掩码文本"));
        assertFalse(s.sources.contains("com.tencent.mm"));
        assertTrue(s.strippedSources.contains("com.tencent.mm"));
    }
}
