package com.dsh.host.core;

import com.dsh.host.api.Config;

import java.util.Map;

public final class MapConfig implements Config {
    private final Map<String, String> m;
    public MapConfig(Map<String, String> m) { this.m = m; }

    @Override public String getString(String k, String d) { return m.getOrDefault(k, d); }
    @Override public int getInt(String k, int d) {
        try { return Integer.parseInt(m.get(k)); } catch (Exception e) { return d; }
    }
    @Override public long getLong(String k, long d) {
        try { return Long.parseLong(m.get(k)); } catch (Exception e) { return d; }
    }
    @Override public boolean getBoolean(String k, boolean d) {
        String v = m.get(k);
        return v == null ? d : Boolean.parseBoolean(v);
    }
    @Override public boolean has(String k) { return m.containsKey(k); }
}
