package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PageParserMergeTest {

    @Test public void OCR与A11Y位置重叠时采用A11Y文本() {
        List<PageElement> a11y = Arrays.asList(new PageElement("a1", "微信扫码代理错误",
                100, 100, 500, 150, PageElement.Role.PARAGRAPH,
                PageElement.Source.A11Y, 1.0, null));
        PageParser.OcrLine ocr = new PageParser.OcrLine("公)微信扫一扫代理错误", 105, 105, 495, 150);
        List<PageElement> out = PageParser.mergeOcr(a11y, Arrays.asList(ocr), "");
        assertEquals(1, out.size());
        assertEquals("微信扫码代理错误", out.get(0).text);
        assertEquals(PageElement.Source.BOTH, out.get(0).source);
        assertEquals(105, out.get(0).left);
        assertTrue(out.get(0).confidence >= 1.0);
    }
}
