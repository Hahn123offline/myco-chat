package com.dsh.memory;

/** recall 查询参数。 */
public final class RecallQuery {
    public String query;
    public String from;   // yyyy-MM-dd
    public String to;
    public String pkg;
    public String level;  // block | day
    public int limit = 8;
    public String namespace;

    public RecallQuery query(String q) { this.query = q; return this; }
    public RecallQuery from(String f) { this.from = f; return this; }
    public RecallQuery to(String t) { this.to = t; return this; }
    public RecallQuery pkg(String p) { this.pkg = p; return this; }
    public RecallQuery level(String l) { this.level = l; return this; }
    public RecallQuery limit(int n) { this.limit = Math.max(1, n); return this; }
    public RecallQuery namespace(String ns) { this.namespace = ns; return this; }
}
