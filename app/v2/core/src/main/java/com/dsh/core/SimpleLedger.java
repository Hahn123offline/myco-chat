package com.dsh.core;

import com.dsh.core.Capability;
import com.dsh.core.CapabilityLedger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** 能力账本：内存环形缓冲 + 落库（落库下一版接 Store）。 */
public final class SimpleLedger implements CapabilityLedger {

    private final LinkedList<Entry> ring = new LinkedList<>();
    private final int cap;
    private volatile Sink sink;

    public interface Sink { void onRecord(Entry e); }

    public SimpleLedger(int capacity) { this.cap = capacity; }

    public void setSink(Sink s) { this.sink = s; }

    @Override
    public synchronized void record(String moduleId, Capability capability,
                                    String target, String outcome, long costMs) {
        Entry e = new Entry(System.currentTimeMillis(), moduleId, capability, target, outcome, costMs);
        ring.addLast(e);
        while (ring.size() > cap) ring.removeFirst();
        Sink s = sink;
        if (s != null) {
            try { s.onRecord(e); } catch (Throwable ignored) { }
        }
    }

    @Override
    public synchronized List<Entry> recent(int n) {
        int from = Math.max(0, ring.size() - n);
        return new ArrayList<>(ring.subList(from, ring.size()));
    }
}
