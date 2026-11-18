package com.dsh.memory;

import java.util.List;

/** 六原语：put/get/query/boost/patch/remove。 */
public interface MemoryStore {
    long put(MemItem item);
    MemItem get(long id);
    List<MemItem> query(Query q);
    void boost(long id, double delta, double cap);
    void patch(long id, java.util.Map<String, Object> dataPatch);
    void remove(long id);
    /** 软删/纠正：superseded=1，可换 key（释放唯一索引），可合并 data 字段。 */
    void supersede(long id, String retiredKey, java.util.Map<String, Object> dataPatch);

    void registerNamespace(String namespace, boolean requireEvidence);
    boolean isNamespaceRegistered(String namespace);
    boolean namespaceRequiresEvidence(String namespace);
}
