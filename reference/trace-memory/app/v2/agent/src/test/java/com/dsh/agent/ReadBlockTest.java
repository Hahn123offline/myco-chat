package com.dsh.agent;

import static org.junit.Assert.*;

import com.dsh.memory.BlockRef;
import com.dsh.memory.MemorySource;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReadBlockTest {

    private static ToolResult call(MemorySource src, Object ids) {
        Map<String, Object> a = new HashMap<>();
        a.put("ids", ids);
        return new ReadBlockTool(src).execute(a, new ToolContext(null, new HashMap<>()));
    }

    @Test public void 正常返回含截图路径() {
        MemorySource src = q -> Collections.emptyList();
        MemorySource withBlock = new MemorySource() {
            @Override public java.util.List<com.dsh.memory.MemItem> candidates(
                    com.dsh.memory.RecallQuery q) { return Collections.emptyList(); }
            @Override public BlockRef readBlock(long id) {
                if (id != 7) return null;
                BlockRef r = new BlockRef();
                r.id = 7; r.text = "原始 OCR"; r.maskedText = "掩码"; r.pkg = "tv.danmaku.bili";
                r.t0 = 1; r.t1 = 2; r.shotRef = "shots/a.jpg"; r.pageId = "p1";
                return r;
            }
        };
        ToolResult ok = call(withBlock, Collections.singletonList(7));
        assertTrue(ok.error, ok.isOk());
        assertTrue(ok.summary.contains("原始 OCR"));
        assertTrue(ok.summary.contains("shots/a.jpg"));
        assertTrue(ok.summary.contains("有截图"));
    }

    @Test public void 不存在id明确报错() {
        MemorySource src = q -> Collections.emptyList();
        ToolResult r = call(src, Collections.singletonList(999));
        assertFalse(r.isOk());
        assertTrue(r.error.contains("不存在"));
    }
}
