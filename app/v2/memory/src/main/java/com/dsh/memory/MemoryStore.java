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

    void registerNamespace(String namespace, boolean requireEvidence);
    boolean isNamespaceRegistered(String namespace);
    boolean namespaceRequiresEvidence(String namespace);
}
