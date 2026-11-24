package com.dsh.masker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MaskerTest {

    private static MaskedBlock mask(String s) {
        return Masker.apply(MaskBlock.ofText(s), MaskPolicy.defaults());
    }

    @Test public void 银行卡保留后四位() {
        MaskedBlock m = mask("银行卡 6222 0212 3456 7890 转账");
        assertTrue(m.text.endsWith("7890 转账"));
        assertTrue(m.text.contains("****"));
        assertFalse(m.text.contains("6222 0212 3456"));
        assertEquals("card", m.classifications.get(0).kind);
    }

    @Test public void 身份证保留前四后二() {
        MaskedBlock m = mask("身份证 11010519491231002X");
        assertTrue(m.text.startsWith("身份证 1101"));
        assertTrue(m.text.endsWith("2X"));
    }

    @Test public void 手机号保留前三后四() {
        MaskedBlock m = mask("电话 13812345678");
        assertEquals("电话 138****5678", m.text);
    }

    @Test public void 验证码全涂() {
        MaskedBlock m = mask("验证码 123456，请勿泄露");
        assertTrue(m.text.contains("██████"));
        assertFalse(m.text.contains("123456"));
    }

    @Test public void 邮箱保留首字母和域名() {
        MaskedBlock m = mask("邮箱 zhang@qq.com");
        assertEquals("邮箱 z***@qq.com", m.text);
        MaskedBlock real = mask("送至邮箱:3123252096@qq.com");
        assertEquals("送至邮箱:3***@qq.com", real.text);
        assertFalse(real.text.contains("3123252096"));
    }

    @Test public void 普通聊天逐字节不变() {
        String s = "今天吃什么？B站评论区说这个视频不错，下次再看 123";
        MaskedBlock m = mask(s);
        assertEquals(s, m.text);
        assertTrue(m.spans.isEmpty());
        assertTrue(m.imageBboxes.isEmpty());
    }

    @Test public void amount默认不掩_策略可开() {
        MaskedBlock def = mask("转账 5000 元");
        assertTrue(def.text.contains("5000 元"));
        MaskPolicy p = MaskPolicy.defaults();
        p.maskAmount = true;
        MaskedBlock on = Masker.apply(MaskBlock.ofText("转账 5000 元"), p);
        assertFalse(on.text.contains("5000"));
    }

    @Test public void 尾部保留位数可配() {
        MaskPolicy p = MaskPolicy.defaults();
        p.keepCardTail = 2;
        MaskedBlock m = Masker.apply(MaskBlock.ofText("6222021234567890"), p);
        assertTrue(m.text.endsWith("90"));
    }

    @Test public void 超长输入不漏尾() {
        MaskedBlock phone = mask("电话 138123456789");
        assertFalse(phone.text.contains("138123456789"));
        assertFalse(phone.text.contains("13812345678"));

        MaskedBlock card = mask("卡号 62220212345678901234");
        assertFalse(card.text.contains("62220212345678901234"));

        MaskedBlock id = mask("身份证 11010519491231002X99");
        assertFalse(id.text.contains("11010519491231002X99"));

        MaskedBlock code = mask("验证码 1234567");
        assertFalse(code.text.contains("1234567"));
    }

    @Test public void B站评论普通数字不误伤() {
        String s = "这个视频 123456 播放量，11月3日 16:12 再看一次，评论区都在笑";
        MaskedBlock m = mask(s);
        assertEquals(s, m.text);
        assertTrue(m.spans.isEmpty());
    }

    @Test public void 单块文本掩码平均小于10ms() {
        String s = "银行卡 6222 0212 3456 7890 电话 13812345678 验证码 123456";
        for (int i = 0; i < 50; i++) mask(s);
        long t0 = System.nanoTime();
        int n = 300;
        for (int i = 0; i < n; i++) mask(s);
        long us = (System.nanoTime() - t0) / 1000 / n;
        assertTrue("avg " + us + "us", us < 10_000);
    }

    @Test public void 元素bbox输出并外扩() {
        MaskElement e = new MaskElement("a1", "电话 13812345678",
                10, 20, 210, 60, "PARAGRAPH", Collections.emptyList());
        MaskedBlock m = new Masker().mask(new MaskBlock("b", "", Arrays.asList(e)),
                MaskPolicy.defaults());
        assertEquals(1, m.imageBboxes.size());
        assertEquals(7, m.imageBboxes.get(0).x);       // 10 - padding3
        assertEquals(17, m.imageBboxes.get(0).y);
        assertEquals(206, m.imageBboxes.get(0).w);
    }

    @Test public void 图片bbox确实涂黑且外扩() {
        int w = 20, h = 20;
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFFFFFFFF);
        java.util.List<MaskBBox> boxes = Collections.singletonList(new MaskBBox(8, 8, 2, 2, "phone"));
        ImageMasker.paint(w, h, px, boxes, 3);
        assertEquals(ImageMasker.BLACK, px[5 * w + 5]);   // 外扩到 5,5
        assertEquals(ImageMasker.BLACK, px[10 * w + 10]);
        assertEquals(0xFFFFFFFF, px[14 * w + 14]);        // 外扩边界外保持
    }
}
