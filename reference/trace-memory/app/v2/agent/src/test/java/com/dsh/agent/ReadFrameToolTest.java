package com.dsh.agent;

import static org.junit.Assert.*;

import com.dsh.memory.FrameRef;
import com.dsh.memory.MemorySource;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReadFrameToolTest {

    private static MemorySource fake(boolean blocked) {
        return new MemorySource() {
            @Override public java.util.List<com.dsh.memory.MemItem> candidates(
                    com.dsh.memory.RecallQuery q) { return Collections.emptyList(); }
            @Override public FrameRef readFrame(long id) {
                return blocked ? FrameRef.blocked("这块属于敏感应用，不能看图")
                        : FrameRef.ok(new byte[]{1, 2, 3});
            }
        };
    }

    private static ToolResult call(ToolContent c, MemorySource src, long id) {
        return new ReadFrameTool(src).execute(
                new HashMap<>(Map.of("block_id", id)), c.ctx);
    }

    private static final class ToolContent {
        final ToolContext ctx;
        ToolContent(ToolContext c) { ctx = c; }
    }

    @Test public void 每轮最多三张() {
        ImageBudget b = new ImageBudget(3);
        ToolContext ctx = new ToolContext(b, new HashMap<>());
        ReadFrameTool t = new ReadFrameTool(fake(false));
        for (int i = 0; i < 3; i++) {
            ToolResult r = t.execute(Map.of("block_id", 1L), ctx);
            assertTrue(r.isOk());
            assertNotNull(r.imageJpeg);
        }
        ToolResult fourth = t.execute(Map.of("block_id", 1L), ctx);
        assertFalse(fourth.isOk());
        assertTrue(fourth.error.contains("额度"));
    }

    @Test public void 敏感应用拒绝() {
        ToolContext ctx = new ToolContext(new ImageBudget(3), new HashMap<>());
        ToolResult r = new ReadFrameTool(fake(true)).execute(Map.of("block_id", 2L), ctx);
        assertFalse(r.isOk());
        assertTrue(r.error.contains("敏感应用"));
    }
}
