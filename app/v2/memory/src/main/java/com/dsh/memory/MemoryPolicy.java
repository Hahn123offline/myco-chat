package com.dsh.memory;

/** 在场判定与硬规则，集中在这里，不散落到调用方。 */
public final class MemoryPolicy {

    public static final String NS_MANUAL = "manual";

    /** manual 块不参与时间衰减（本轮只落策略，不接召回）。 */
    public static boolean noDecay(String namespace) {
        return NS_MANUAL.equals(namespace);
    }

    /** manual 块不参与质量地板过滤（1 秒也算）。 */
    public static boolean skipNoiseFilter(String namespace) {
        return NS_MANUAL.equals(namespace);
    }

    public static double recallBoost(String namespace) {
        return NS_MANUAL.equals(namespace) ? 1.25 : 1.0;
    }

    /** recall 的内容型回答只允许 FULL；APP_ONLY/IMAGE_ONLY 不能回答「在哪看到过 X」。 */
    public static boolean contentEvidence(MemItem m) {
        return m != null && m.detailLevel == DetailLevel.FULL;
    }

    private MemoryPolicy() { }

    /** present = 有用户主动产生的证据（chat / manual）；trace/backfill/feed 都不算。 */
    public static boolean present(MemItem m) {
        if (m == null || m.evidence == null) return false;
        for (Evidence e : m.evidence) {
            if (e != null && ("chat".equals(e.type) || "manual".equals(e.type))) return true;
        }
        return false;
    }

    public static void validatePut(MemoryStore store, MemItem item) {
        if (item == null) throw new IllegalArgumentException("item 为空");
        if (item.namespace == null || item.namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace 为空");
        }
        if (!store.isNamespaceRegistered(item.namespace)) {
            throw new IllegalArgumentException("namespace 未注册: " + item.namespace);
        }
        boolean textEmpty = item.text == null || item.text.trim().isEmpty();
        boolean dataEmpty = item.data == null || item.data.trim().isEmpty();
        if (textEmpty && dataEmpty) throw new IllegalArgumentException("text 和 data 不能都为空");
        if (store.namespaceRequiresEvidence(item.namespace)
                && (item.evidence == null || item.evidence.isEmpty())) {
            throw new IllegalArgumentException("requireEvidence namespace 必须有 evidence");
        }
    }

    private static boolean isRegistered(MemoryStore store, String ns) {
        return store.isNamespaceRegistered(ns);
    }
}
