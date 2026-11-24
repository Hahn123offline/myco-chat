package com.dsh.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试用内存实现，不依赖 Android/SQLite。 */
public final class InMemoryMemoryStore extends AbstractMemoryStore {

    private final Map<Long, MemItem> rows = new LinkedHashMap<>();
    private long seq = 0;

    @Override protected MemItem findKey(String namespace, String key) {
        for (MemItem m : rows.values()) {
            if (!m.superseded && m.namespace.equals(namespace)
                    && key.equals(m.key)) return m;
        }
        return null;
    }

    @Override protected long insertRow(MemItem item) {
        item.id = ++seq;
        rows.put(item.id, copy(item));
        return item.id;
    }

    @Override protected void updateRow(MemItem item) {
        rows.put(item.id, copy(item));
    }

    @Override public MemItem get(long id) {
        MemItem m = rows.get(id);
        return m == null ? null : copy(m);
    }

    @Override public List<MemItem> query(Query q) {
        List<MemItem> out = new ArrayList<>();
        for (MemItem m : rows.values()) {
            if (q == null) { out.add(copy(m)); continue; }
            if (!q.includeSuperseded && m.superseded) continue;
            if (q.namespace != null && !q.namespace.equals(m.namespace)) continue;
            if (q.kind != null && q.kind != m.kind) continue;
            if (q.from != null && m.t0 < q.from) continue;
            if (q.to != null && m.t0 > q.to) continue;
            out.add(copy(m));
            if (out.size() >= (q.limit <= 0 ? 100 : q.limit)) break;
        }
        return out;
    }

    private static MemItem copy(MemItem m) {
        MemItem x = new MemItem(m.namespace, m.kind, m.key, m.text, m.data);
        x.id = m.id; x.weight = m.weight; x.recallCount = m.recallCount;
        x.lastRecalledAt = m.lastRecalledAt; x.t0 = m.t0; x.t1 = m.t1;
        x.createdAt = m.createdAt; x.updatedAt = m.updatedAt;
        x.superseded = m.superseded; x.evidence = new ArrayList<>(m.evidence);
        x.detailLevel = m.detailLevel;
        return x;
    }
}
