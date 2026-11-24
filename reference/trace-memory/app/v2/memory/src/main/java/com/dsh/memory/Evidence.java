package com.dsh.memory;

/** 证据：一条记忆是怎么产生的。 */
public final class Evidence {
    public final String type;   // trace | chat | manual | backfill | feed
    public final String ref;
    public final Long t;

    public Evidence(String type, String ref, Long t) {
        this.type = type == null ? "" : type;
        this.ref = ref == null ? "" : ref;
        this.t = t;
    }
}
