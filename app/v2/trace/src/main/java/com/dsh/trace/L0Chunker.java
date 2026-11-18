package com.dsh.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * L0 结构切分（纯算法、零模型）：
 * 1) 同 pkg、相邻块间隔 ≤5min 合并为会话
 * 2) 递归在最大空闲间隔处二分，直到每块 ≤45min（且会话数 >2 才继续切）
 * 输出互斥、覆盖全部输入。
 */
public final class L0Chunker {

    public static final long SESSION_GAP_MS = 5 * 60_000L;
    public static final long MAX_CHUNK_MS = 45 * 60_000L;

    public static final class Session {
        public final String pkg;
        public final long t0;
        public final long t1;
        public final List<Trace> traces;

        Session(String pkg, long t0, long t1, List<Trace> traces) {
            this.pkg = pkg;
            this.t0 = t0;
            this.t1 = t1;
            this.traces = traces;
        }

        public int size() { return traces.size(); }
    }

    public static final class Chunk {
        public final long t0;
        public final long t1;
        public final List<Session> sessions;
        public final List<Trace> traces;

        Chunk(long t0, long t1, List<Session> sessions, List<Trace> traces) {
            this.t0 = t0;
            this.t1 = t1;
            this.sessions = sessions;
            this.traces = traces;
        }

        public long durationMs() { return t1 - t0; }

        public List<Long> blockIds() {
            List<Long> ids = new ArrayList<>();
            for (Trace t : traces) ids.add(t.id);
            return ids;
        }
    }

    private L0Chunker() { }

    public static List<Session> sessions(List<Trace> traces) {
        List<Session> out = new ArrayList<>();
        if (traces == null || traces.isEmpty()) return out;
        List<Trace> sorted = new ArrayList<>(traces);
        sorted.sort((a, b) -> Long.compare(a.t0, b.t0));
        String pkg = null;
        long t0 = 0, t1 = 0;
        List<Trace> cur = new ArrayList<>();
        for (Trace t : sorted) {
            if (pkg != null && pkg.equals(t.pkg) && t.t0 - t1 <= SESSION_GAP_MS) {
                cur.add(t);
                t1 = Math.max(t1, t.t1);
            } else {
                if (!cur.isEmpty()) out.add(new Session(pkg, t0, t1, cur));
                pkg = t.pkg;
                t0 = t.t0;
                t1 = t.t1;
                cur = new ArrayList<>();
                cur.add(t);
            }
        }
        if (!cur.isEmpty()) out.add(new Session(pkg, t0, t1, cur));
        return out;
    }

    public static List<Chunk> chunks(List<Session> sessions) {
        List<Chunk> out = new ArrayList<>();
        if (sessions == null || sessions.isEmpty()) return out;
        split(sessions, out);
        return out;
    }

    private static void split(List<Session> list, List<Chunk> out) {
        if (list.isEmpty()) return;
        long t0 = list.get(0).t0;
        long t1 = list.get(list.size() - 1).t1;
        if (t1 - t0 <= MAX_CHUNK_MS || list.size() <= 2) {
            List<Trace> traces = new ArrayList<>();
            for (Session s : list) traces.addAll(s.traces);
            out.add(new Chunk(t0, t1, new ArrayList<>(list), traces));
            return;
        }
        int cut = 0;
        long bestGap = -1;
        for (int i = 0; i < list.size() - 1; i++) {
            long gap = list.get(i + 1).t0 - list.get(i).t1;
            if (gap > bestGap) { bestGap = gap; cut = i; }
        }
        if (bestGap <= 0) {
            List<Trace> traces = new ArrayList<>();
            for (Session s : list) traces.addAll(s.traces);
            out.add(new Chunk(t0, t1, new ArrayList<>(list), traces));
            return;
        }
        split(new ArrayList<>(list.subList(0, cut + 1)), out);
        split(new ArrayList<>(list.subList(cut + 1, list.size())), out);
    }
}
