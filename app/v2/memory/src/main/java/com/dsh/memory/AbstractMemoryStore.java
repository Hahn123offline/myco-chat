package com.dsh.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 六原语的可复用实现：校验/upsert/boost/patch/remove 规则都在这里，
 * Android 与桌面只实现行级持久化。
 */
public abstract class AbstractMemoryStore implements MemoryStore {

    private final Map<String, Boolean> namespaces = new HashMap<>();

    protected abstract MemItem findKey(String namespace, String key);
    protected abstract long insertRow(MemItem item);
    protected abstract void updateRow(MemItem item);

    @Override public void registerNamespace(String namespace, boolean requireEvidence) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace 为空");
        }
        namespaces.put(namespace, requireEvidence);
    }

    @Override public boolean isNamespaceRegistered(String namespace) {
        return namespaces.containsKey(namespace);
    }

    @Override public boolean namespaceRequiresEvidence(String namespace) {
        Boolean v = namespaces.get(namespace);
        return v != null && v;
    }

    @Override public long put(MemItem item) {
        MemoryPolicy.validatePut(this, item);
        long now = System.currentTimeMillis();
        if (item.createdAt <= 0) item.createdAt = now;
        item.updatedAt = now;
        if (item.evidence == null) item.evidence = new ArrayList<>();
        if (item.key != null && !item.key.isEmpty()) {
            MemItem old = findKey(item.namespace, item.key);
            if (old != null) {
                item.id = old.id;
                item.createdAt = old.createdAt;
                item.recallCount = old.recallCount;
                item.lastRecalledAt = old.lastRecalledAt;
                updateRow(item);
                return item.id;
            }
        }
        return insertRow(item);
    }

    @Override public void boost(long id, double delta, double cap) {
        MemItem m = get(id);
        if (m == null || m.superseded) return;
        m.weight = Math.min(cap, m.weight + Math.max(0, delta));
        m.recallCount++;
        m.lastRecalledAt = System.currentTimeMillis();
        m.updatedAt = System.currentTimeMillis();
        updateRow(m);
    }

    @Override public void patch(long id, Map<String, Object> dataPatch) {
        MemItem m = get(id);
        if (m == null) return;
        m.data = MemoryCodec.patchData(m.data, dataPatch);
        m.updatedAt = System.currentTimeMillis();
        updateRow(m);
    }

    @Override public void remove(long id) {
        MemItem m = get(id);
        if (m == null) return;
        m.superseded = true;
        m.updatedAt = System.currentTimeMillis();
        updateRow(m);
    }
}
