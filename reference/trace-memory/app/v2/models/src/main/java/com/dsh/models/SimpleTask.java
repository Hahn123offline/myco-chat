package com.dsh.models;

import java.util.Collections;
import java.util.Set;

/** 简单任务描述（配置/UI/测试可直接构造）。 */
public final class SimpleTask implements ModelTask {

    private final String name;
    private final Set<Capability> need;
    private final Quality quality;
    private final com.dsh.egress.EgressTier egress;

    public SimpleTask(String name, Set<Capability> need, Quality quality,
                      com.dsh.egress.EgressTier egress) {
        this.name = name == null ? "unnamed" : name;
        this.need = need == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(need));
        this.quality = quality == null ? Quality.DRAFT : quality;
        this.egress = egress == null ? com.dsh.egress.EgressTier.WHITE : egress;
    }

    @Override public Set<Capability> need() { return need; }
    @Override public Quality quality() { return quality; }
    @Override public com.dsh.egress.EgressTier egress() { return egress; }
    @Override public String taskName() { return name; }
}
