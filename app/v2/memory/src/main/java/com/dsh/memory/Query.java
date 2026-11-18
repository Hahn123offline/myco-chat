package com.dsh.memory;

/** query 过滤条件。 */
public final class Query {
    public String namespace;
    public Kind kind;
    public Long from;
    public Long to;
    public boolean includeSuperseded;
    public int limit = 100;

    public Query namespace(String ns) { this.namespace = ns; return this; }
    public Query kind(Kind k) { this.kind = k; return this; }
    public Query window(Long from, Long to) { this.from = from; this.to = to; return this; }
    public Query includeSuperseded(boolean v) { this.includeSuperseded = v; return this; }
    public Query limit(int n) { this.limit = Math.max(1, n); return this; }
}
