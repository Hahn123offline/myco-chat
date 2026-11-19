package com.dsh.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LineChromeTest {

    @Test public void 顶部状态栏与底部工具条是CHROME() {
        assertEquals("CHROME", MemoryProjector.classify("14:18 5G 80%", 50, ""));
        assertEquals("CHROME", MemoryProjector.classify("发送", 2400, ""));
    }

    @Test public void 正文行是CONTENT() {
        assertEquals("CONTENT", MemoryProjector.classify("先把整条路跑通", 1000, "零杯（）"));
    }

    @Test public void 纯符号键盘行是CHROME() {
        assertEquals("CHROME", MemoryProjector.classify("W E R T Y", 1100, ""));
    }
}
