package com.dsh.memory;

import com.dsh.host.api.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单测替身（不以 Test 结尾，JUnit 不收集）。 */
final class TestSupport {

    private TestSupport() { }

    /** 可变 Config：改 map 即"热更新" */
    static final class FakeConfig implements Config {
        final Map<String, String> m = new LinkedHashMap<>();

        FakeConfig set(String k, String v) { m.put(k, v); return this; }

        @Override public String getString(String k, String d) { return m.getOrDefault(k, d); }
        @Override public int getInt(String k, int d) {
            String v = m.get(k);
            try { return Integer.parseInt(v); } catch (Exception e) { return d; }
        }
        @Override public long getLong(String k, long d) {
            String v = m.get(k);
            try { return Long.parseLong(v); } catch (Exception e) { return d; }
        }
        @Override public boolean getBoolean(String k, boolean d) {
            String v = m.get(k);
            return v == null ? d : Boolean.parseBoolean(v);
        }
        @Override public boolean has(String k) { return m.containsKey(k); }
    }
}
