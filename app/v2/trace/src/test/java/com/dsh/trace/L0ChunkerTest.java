package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class L0ChunkerTest {

    private static Trace t(long id, String pkg, long t0, long t1) {
        Trace tr = new Trace();
        tr.id = id;
        tr.pkg = pkg;
        tr.t0 = t0;
        tr.t1 = t1;
        return tr;
    }

    @Test public void 同pkg间隔5分钟内合并为会话() {
        List<Trace> traces = new ArrayList<>();
        traces.add(t(1, "a", 0, 60_000));
        traces.add(t(2, "a", 120_000, 180_000));
        traces.add(t(3, "a", 600_000, 660_000));   // gap 7min >5min
        traces.add(t(4, "b", 700_000, 760_000));   // 换包
        List<L0Chunker.Session> s = L0Chunker.sessions(traces);
        assertEquals(3, s.size());
        assertEquals(2, s.get(0).size());
        assertEquals("a", s.get(0).pkg);
        assertEquals("b", s.get(2).pkg);
    }

    @Test public void 递归切最大空档且不超45分钟() {
        List<L0Chunker.Session> sessions = new ArrayList<>();
        // 构造 4 段，每段约 30min，段间空档不同：第2-3之间最大
        long base = 0;
        String[] pkgs = {"a", "b", "c", "d"};
        for (int i = 0; i < 4; i++) {
            List<Trace> traces = new ArrayList<>();
            traces.add(t(i * 10L + 1, pkgs[i], base, base + 30 * 60_000));
            traces.add(t(i * 10L + 2, pkgs[i], base + 31 * 60_000, base + 31 * 60_000));
            sessions.addAll(L0Chunker.sessions(traces));
            base += 30 * 60_000 + (i == 1 ? 20 * 60_000 : 2 * 60_000);
        }
        List<L0Chunker.Chunk> chunks = L0Chunker.chunks(sessions);
        assertTrue(chunks.size() >= 2);
        long sum = 0;
        for (L0Chunker.Chunk c : chunks) {
            // 规则：>45min 且 session 数 >2 才继续切；只有 1–2 个会话时无法再切
            assertTrue("块超 45min 且仍有 >2 会话: " + c.durationMs()
                            + " sessions=" + c.sessions.size(),
                    c.durationMs() <= 45 * 60_000 || c.sessions.size() <= 2);
            sum += c.traces.size();
        }
        assertEquals("覆盖全部 trace", 8, sum);
    }

    @Test public void 覆盖互斥_每个trace恰好一次() {
        List<Trace> traces = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long t0 = i * 10 * 60_000L;
            traces.add(t(i + 1, i % 2 == 0 ? "a" : "b", t0, t0 + 60_000));
        }
        List<L0Chunker.Session> sessions = L0Chunker.sessions(traces);
        List<L0Chunker.Chunk> chunks = L0Chunker.chunks(sessions);
        List<Long> seen = new ArrayList<>();
        for (L0Chunker.Chunk c : chunks) for (Trace tr : c.traces) seen.add(tr.id);
        assertEquals(20, seen.size());
        assertEquals(20, new java.util.HashSet<>(seen).size());
    }
}
