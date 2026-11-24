package com.dsh.usagetrace;

import com.dsh.host.api.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单测替身（不以 Test 结尾，JUnit 不收集）。 */
final class TestSupport {

    private TestSupport() { }

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

    /** 假时钟：nowMs/uptimeMs 可独立设置，避免真 Sleep */
    static final class FakeTime implements A11yTimeSource {
        volatile long now;
        volatile long uptime;

        FakeTime(long now, long uptime) { this.now = now; this.uptime = uptime; }

        @Override public long nowMs() { return now; }
        @Override public long uptimeMs() { return uptime; }
    }

    static final class FakeChecker implements A11yEnabledChecker {
        volatile boolean enabled;
        volatile boolean throwsOnCall = false;

        FakeChecker(boolean enabled) { this.enabled = enabled; }

        @Override public boolean isOurServiceEnabled() {
            if (throwsOnCall) throw new IllegalStateException("fake a11y manager boom");
            return enabled;
        }
    }
}
