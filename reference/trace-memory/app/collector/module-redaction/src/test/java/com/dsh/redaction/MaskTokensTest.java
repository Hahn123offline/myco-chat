package com.dsh.redaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MaskTokensTest {

    @Test public void 识别手机号掩码区间() {
        List<MaskTokens.Span> spans = MaskTokens.scan("联系我 138*****678 就行");
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).text.contains("138*****678"));
        assertTrue(spans.get(0).text.contains("*"));
    }

    @Test public void 相邻两个掩码不合并成一个巨大的区间() {
        List<MaskTokens.Span> spans = MaskTokens.scan("138*****678 139*****679");
        assertEquals(2, spans.size());
        assertEquals("138*****678", spans.get(0).text);
        assertEquals("139*****679", spans.get(1).text);
    }

    @Test public void 验证码丢弃标记算受保护区间() {
        List<MaskTokens.Span> spans = MaskTokens.scan("您的" + MaskTokens.VERIFY_DROPPED + "已发送");
        assertEquals(1, spans.size());
        assertEquals(MaskTokens.VERIFY_DROPPED, spans.get(0).text);
    }

    @Test public void 没有掩码时返回空() {
        assertTrue(MaskTokens.scan("普通文本，没有星号").isEmpty());
        assertTrue(MaskTokens.scan("").isEmpty());
        assertTrue(MaskTokens.scan(null).isEmpty());
    }

    @Test public void 单个星号不当作受保护区间() {
        assertTrue(MaskTokens.scan("按 * 号查询").isEmpty());
    }

    @Test public void 片段掩码等长且保留首字符() {
        assertEquals("张*", MaskTokens.maskFragment("张伟"));
        assertEquals("深*****", MaskTokens.maskFragment("深港驾校小米"));
        assertEquals("张伟".length(), MaskTokens.maskFragment("张伟").length());
    }

    @Test public void 区间重叠判断() {
        MaskTokens.Span s = new MaskTokens.Span(5, 10, "xxxxx");
        assertTrue(s.overlaps(7, 8));
        assertTrue(s.overlaps(4, 6));
        assertFalse(s.overlaps(0, 5));
        assertFalse(s.overlaps(10, 12));
        assertTrue(MaskTokens.overlapsAny(java.util.Collections.singletonList(s), 9, 11));
        assertFalse(MaskTokens.overlapsAny(java.util.Collections.singletonList(s), 11, 12));
    }
}
