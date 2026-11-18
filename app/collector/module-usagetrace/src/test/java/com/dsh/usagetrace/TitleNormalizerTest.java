package com.dsh.usagetrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TitleNormalizerTest {

    @Test public void 去掉未读数() {
        assertEquals("微信", TitleNormalizer.normalize("微信(122)"));
        assertEquals("相亲相爱一家人。", TitleNormalizer.normalize("相亲相爱一家人。(25)"));
    }

    @Test public void 去掉首尾残渣() {
        assertEquals("26秋篮球1周一34", TitleNormalizer.normalize("く122 26秋篮球1周一34(39)8"));
    }

    @Test public void 拒绝纯数字与时间() {
        assertEquals("", TitleNormalizer.normalize("10:22"));
        assertEquals("", TitleNormalizer.normalize("83.5"));
        assertEquals("", TitleNormalizer.normalize("1f:13 o"));
    }

    @Test public void 错字纠正() {
        assertEquals("文件传输助手", TitleNormalizer.normalize("文件伎输助手"));
    }

    @Test public void 同一标题的不同OCR写法视为相同() {
        assertTrue(TitleNormalizer.sameTitle("文件传输助手", "文件伎输助手"));
        assertTrue(TitleNormalizer.sameTitle("26秋篮球1周一34", "26秋篮球1周一34(39)"));
        assertFalse(TitleNormalizer.sameTitle("深港驾校小米", "相亲相爱一家人"));
    }

    @Test public void 过短不成为标题() {
        assertEquals("", TitleNormalizer.normalize("微"));
        assertEquals("", TitleNormalizer.normalize(""));
    }
}
