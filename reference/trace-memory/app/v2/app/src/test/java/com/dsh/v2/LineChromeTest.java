package com.dsh.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LineChromeTest {

    @Test public void 顶部状态栏与底部工具条是CHROME() {
        assertEquals("CHROME", MemoryProjector.classify("14:18 5G 80%", 50, ""));
        assertEquals("CHROME", MemoryProjector.classify("发送", 2400, ""));
    }

    @Test public void 正文行是CONTENT() {
        assertEquals("CONTENT", MemoryProjector.classify("我们先把整条路跑通，再谈别的细节吧", 1000, "零杯（）"));
    }

    @Test public void 过短无标点是CHROME() {
        assertEquals("CHROME", MemoryProjector.classify("确实慢", 1000, ""));
        assertEquals("CHROME", MemoryProjector.classify("全店479人在拼", 1000, ""));
    }

    @Test public void 乱码构成判CHROME() {
        assertEquals("CHROME", MemoryProjector.classify("动sal.没93]:1", 1000, ""));
        assertEquals("CHROME", MemoryProjector.classify("21.1 4G", 1000, ""));
    }

    @Test public void 纯符号键盘行是CHROME() {
        assertEquals("CHROME", MemoryProjector.classify("W E R T Y", 1100, ""));
    }
}
