package com.dsh.redaction;

import com.dsh.host.api.CapabilityLedger;
import com.dsh.host.api.Config;
import com.dsh.host.api.Event;
import com.dsh.host.api.EventBus;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.Log;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.Store;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单测替身集合（不以 Test 结尾，JUnit 不收集）。全部纯 JVM，不连网、不碰安卓 API。
 */
final class TestSupport {

    private TestSupport() { }

    // ---------------- 配置 / 日志 ----------------

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

    static final class FakeLog implements Log {
        final List<String> lines = new CopyOnWriteArrayList<>();
        @Override public void i(String tag, String msg) { lines.add("I/" + tag + ": " + msg); }
        @Override public void w(String tag, String msg) { lines.add("W/" + tag + ": " + msg); }
        @Override public void e(String tag, String msg, Throwable t) { lines.add("E/" + tag + ": " + msg); }
    }

    // ---------------- 模型服务替身 ----------------

    static final class FakeLlmService implements LlmService {
        volatile Health health = Health.ok();
        volatile LlmResponse response = LlmResponse.ok("NONE", "fake-model");
        volatile long delayMs = 0;
        volatile boolean throwOnComplete = false;
        final AtomicInteger calls = new AtomicInteger();
        final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

        @Override public void complete(LlmRequest req, Callback cb) {
            calls.incrementAndGet();
            requests.add(req);
            if (throwOnComplete) throw new IllegalStateException("fake llm boom");
            Runnable work = () -> {
                if (delayMs > 0) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                cb.onResult(response);
            };
            if (delayMs > 0) {
                Thread t = new Thread(work, "fake-llm");
                t.setDaemon(true);
                t.start();
            } else {
                work.run();
            }
        }

        @Override public Health health() { return health; }
    }

    // ---------------- MemoryQuery 替身 ----------------

    static final class FakeMem implements MemoryQuery {
        final List<MemoryBlock> ruleBlocks = new CopyOnWriteArrayList<>();
        final List<Long> updatedEpisodeIds = new CopyOnWriteArrayList<>();
        final List<String> updatedTexts = new CopyOnWriteArrayList<>();
        final List<Integer> updatedLevels = new CopyOnWriteArrayList<>();
        volatile int updateRows = 1;

        @Override public List<MemoryBlock> recent(int n) { return new ArrayList<>(ruleBlocks); }
        @Override public List<MemoryBlock> between(long a, long b) { return new ArrayList<>(ruleBlocks); }
        @Override public List<MemoryBlock> search(String text, int limit) { return new ArrayList<>(ruleBlocks); }
        @Override public long count() { return ruleBlocks.size(); }

        @Override public List<MemoryBlock> byRedactionLevel(int level, int limit) {
            List<MemoryBlock> out = new ArrayList<>();
            for (MemoryBlock b : ruleBlocks) {
                if (b != null && b.redactionLevel == level) {
                    out.add(b);
                    if (out.size() >= Math.max(1, limit)) break;
                }
            }
            return out;
        }

        @Override public long countByRedactionLevel(int level) {
            long n = 0;
            for (MemoryBlock b : ruleBlocks) if (b != null && b.redactionLevel == level) n++;
            return n;
        }

        @Override public int updateRedaction(long episodeId, String maskedText, int level, long ts) {
            updatedEpisodeIds.add(episodeId);
            updatedTexts.add(maskedText);
            updatedLevels.add(level);
            return updateRows;
        }

        @Override
        public List<MemoryBlock> byEgressTier(com.dsh.host.api.memory.EgressTier tier, int limit) {
            List<MemoryBlock> out = new ArrayList<>();
            if (tier == null) return out;
            for (MemoryBlock b : ruleBlocks) {
                if (b != null && b.egressTier == tier) {
                    out.add(b);
                    if (out.size() >= Math.max(1, limit)) break;
                }
            }
            return out;
        }

        @Override public String exportRecentJson(int n) { return "{}"; }

        @Override public List<MemoryBlock> blocksNeedingLabel(int limit) { return new ArrayList<>(); }

        @Override public int updateLabel(long id, String label, String model, long ts, int status) { return 1; }

        @Override public long countByLabelStatus(int status) { return 0; }

        @Override public String searchEngine() { return "LIKE"; }
    }

    static MemoryBlock block(long episodeId, String maskedText) {
        MemoryBlock b = new MemoryBlock();
        b.episodeId = episodeId;
        b.maskedText = maskedText;
        b.redactionLevel = RedactionLevel.RULE;
        b.tStart = episodeId * 1000;
        return b;
    }

    // ---------------- 电源 / proc 替身 ----------------

    static final class FakePower implements PowerState {
        volatile boolean charging = true;
        volatile boolean screenOff = true;
        volatile int pct = 80;
        @Override public boolean isCharging() { return charging; }
        @Override public boolean isScreenOff() { return screenOff; }
        @Override public int batteryPercent() { return pct; }
    }

    static final class FakeProc implements Metrics.ProcSource {
        volatile String stat;
        volatile String status;

        @Override public String read(String path) {
            if (path == null) return null;
            if (path.endsWith("/stat")) return stat;
            if (path.endsWith("/status")) return status;
            return null;
        }
    }

    /** 造一行 /proc/self/stat；utime/stime 在字段 14/15 */
    static String statLine(long utimeTicks, long stimeTicks) {
        return "1234 (redaction test) S 1 2 3 4 5 6 7 8 9 10 "
                + utimeTicks + " " + stimeTicks + " 0 0 0";
    }

    static String statusLine(long rssKb, long hwmKb) {
        return "Name:\tredaction\nVmPeak:\t 999999 kB\nVmRSS:\t " + rssKb
                + " kB\nVmHWM:\t " + hwmKb + " kB\nThreads:\t8\n";
    }

    // ---------------- 宿主上下文替身 ----------------

    static final class FakeBus implements EventBus {
        final Map<Class<?>, List<Handler<?>>> subs = new ConcurrentHashMap<>();
        final List<Event> published = new CopyOnWriteArrayList<>();

        @Override public <T extends Event> void subscribe(Class<T> type, Handler<T> handler) {
            subs.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
        }

        @Override public void unsubscribe(Handler<?> handler) {
            for (List<Handler<?>> l : subs.values()) l.remove(handler);
        }

        @Override @SuppressWarnings("unchecked")
        public void publish(Event event) {
            published.add(event);
            List<Handler<?>> l = subs.get(event.getClass());
            if (l == null) return;
            for (Handler<?> h : l) ((Handler<Event>) h).on(event);
        }
    }

    static final class FakeCtx implements HostContext {
        final FakeConfig config = new FakeConfig();
        /** 模拟 models.properties（其它模块的配置段，只读） */
        final FakeConfig modelsConfig = new FakeConfig();
        final FakeLog log = new FakeLog();
        final FakeBus bus = new FakeBus();
        final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
        final List<AutoCloseable> effects = new ArrayList<>();

        @Override public EventBus bus() { return bus; }
        @Override public CapabilityLedger ledger() { return null; }
        @Override public Store store() { return null; }
        @Override public Config config() { return config; }
        @Override public Config configOf(String moduleId) {
            return "models".equals(moduleId) ? modelsConfig : config;
        }
        @Override public Log log() { return log; }

        @Override public AutoCloseable effect(Runnable onDispose) {
            AutoCloseable c = onDispose::run;
            effects.add(c);
            return c;
        }

        @Override public <T extends Event> void on(Class<T> type, EventBus.Handler<T> handler) {
            bus.subscribe(type, handler);
        }

        @Override public void schedule(String name, long periodMs, Runnable task) { }

        @Override public <T> void provide(Class<T> api, T impl) { services.put(api, impl); }

        @SuppressWarnings("unchecked")
        @Override public <T> T require(Class<T> api) { return (T) services.get(api); }
    }
}
