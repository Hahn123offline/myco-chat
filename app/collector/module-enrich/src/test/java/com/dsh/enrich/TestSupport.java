package com.dsh.enrich;

import com.dsh.host.api.Capability;
import com.dsh.host.api.CapabilityLedger;
import com.dsh.host.api.Config;
import com.dsh.host.api.Event;
import com.dsh.host.api.EventBus;
import com.dsh.host.api.Health;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.Log;
import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.Store;
import com.dsh.host.api.data.LabelStatus;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;
import com.dsh.host.api.memory.EgressPolicy;
import com.dsh.host.api.memory.EgressTier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** module-enrich 单测支撑：全部是假对象，不碰 Android/网络。 */
final class TestSupport {

    private TestSupport() { }

    static final class FakeConfig implements Config {
        final Map<String, String> map = new HashMap<>();

        FakeConfig() { }

        FakeConfig set(String k, String v) { map.put(k, v); return this; }

        @Override public String getString(String key, String def) {
            String v = map.get(key);
            return v == null ? def : v;
        }

        @Override public int getInt(String key, int def) {
            try { return Integer.parseInt(getString(key, String.valueOf(def))); }
            catch (Throwable t) { return def; }
        }

        @Override public long getLong(String key, long def) {
            try { return Long.parseLong(getString(key, String.valueOf(def))); }
            catch (Throwable t) { return def; }
        }

        @Override public boolean getBoolean(String key, boolean def) {
            String v = map.get(key);
            return v == null ? def : Boolean.parseBoolean(v);
        }

        @Override public boolean has(String key) { return map.containsKey(key); }
    }

    static final class FakeLog implements Log {
        final List<String> lines = new CopyOnWriteArrayList<>();

        @Override public void i(String tag, String msg) { lines.add("I/" + tag + ": " + msg); }
        @Override public void w(String tag, String msg) { lines.add("W/" + tag + ": " + msg); }
        @Override public void e(String tag, String msg, Throwable t) { lines.add("E/" + tag + ": " + msg); }

        boolean has(String needle) {
            for (String s : lines) if (s.contains(needle)) return true;
            return false;
        }
    }

    static class FakeMem implements MemoryQuery {
        final List<MemoryBlock> blocks = new CopyOnWriteArrayList<>();
        final List<String> updated = new CopyOnWriteArrayList<>();

        @Override public List<MemoryBlock> recent(int n) { return new ArrayList<>(blocks); }
        @Override public List<MemoryBlock> between(long a, long b) { return new ArrayList<>(blocks); }
        @Override public List<MemoryBlock> search(String text, int limit) { return new ArrayList<>(); }
        @Override public List<MemoryBlock> search(String text, int limit, EgressTier tier) { return new ArrayList<>(); }
        @Override public long count() { return blocks.size(); }
        @Override public List<MemoryBlock> byRedactionLevel(int level, int limit) { return new ArrayList<>(); }
        @Override public long countByRedactionLevel(int level) { return 0; }
        @Override public int updateRedaction(long episodeId, String maskedText, int level, long ts) { return 1; }
        @Override public String exportRecentJson(int n) { return "{}"; }
        @Override public String searchEngine() { return "LIKE"; }

        @Override public List<MemoryBlock> byEgressTier(EgressTier tier, int limit) {
            List<MemoryBlock> out = new ArrayList<>();
            for (MemoryBlock b : blocks) {
                if (tier != null && b.egressTier == tier) {
                    out.add(b);
                    if (out.size() >= Math.max(1, limit)) break;
                }
            }
            return out;
        }

        @Override public List<MemoryBlock> blocksNeedingLabel(int limit) {
            List<MemoryBlock> out = new ArrayList<>();
            for (MemoryBlock b : blocks) {
                if (b.egressTier == EgressTier.WHITE && b.label == null
                        && b.labelStatus != LabelStatus.SKIPPED) {
                    out.add(b);
                    if (out.size() >= Math.max(1, limit)) break;
                }
            }
            return out;
        }

        @Override public int updateLabel(long id, String label, String model, long ts, int status) {
            for (MemoryBlock b : blocks) {
                if (b.id == id) {
                    b.label = label;
                    b.labelModel = model;
                    b.labeledAt = ts;
                    b.labelStatus = status;
                    updated.add("id=" + id + " status=" + LabelStatus.name(status)
                            + " label=" + label);
                    return 1;
                }
            }
            return 0;
        }

        @Override public long countByLabelStatus(int status) {
            long n = 0;
            for (MemoryBlock b : blocks) if (b.labelStatus == status) n++;
            return n;
        }
    }

    static final class FakeLlm implements LlmService {
        interface Reply { LlmResponse reply(LlmRequest req); }

        volatile Reply handler = req -> LlmResponse.ok("这一屏在讲游戏攻略", "fake-model");
        final List<LlmRequest> requests = new CopyOnWriteArrayList<>();
        final List<String> threads = new CopyOnWriteArrayList<>();
        final List<String> userPrompts = new CopyOnWriteArrayList<>();
        final List<String> systemPrompts = new CopyOnWriteArrayList<>();

        @Override public void complete(LlmRequest req, Callback cb) {
            requests.add(req);
            threads.add(Thread.currentThread().getName());
            userPrompts.add(req == null ? "" : req.userPrompt);
            systemPrompts.add(req == null ? "" : req.systemPrompt);
            LlmResponse r;
            try {
                r = handler.reply(req);
            } catch (Throwable t) {
                r = LlmResponse.error("fake threw: " + t.getClass().getSimpleName());
            }
            cb.onResult(r);
        }

        @Override public Health health() { return Health.ok(); }
    }

    /** 最小 HostContext：config/log/require/schedule/effect 够 EnrichModule 用 */
    static final class FakeHostContext implements HostContext {
        final FakeConfig config = new FakeConfig();
        final FakeLog log = new FakeLog();
        final Map<Class<?>, Object> services = new LinkedHashMap<>();
        final List<String> scheduleNames = new CopyOnWriteArrayList<>();
        final List<Long> periods = new CopyOnWriteArrayList<>();
        final List<Runnable> scheduled = new CopyOnWriteArrayList<>();
        final List<Runnable> effects = new CopyOnWriteArrayList<>();

        @Override public EventBus bus() { return null; }
        @Override public CapabilityLedger ledger() { return null; }
        @Override public Store store() { return null; }
        @Override public Config config() { return config; }
        @Override public Log log() { return log; }

        @Override public AutoCloseable effect(Runnable onDispose) {
            effects.add(onDispose);
            return () -> { };
        }

        @Override public <T extends Event> void on(Class<T> type, EventBus.Handler<T> handler) { }

        @Override public void schedule(String name, long periodMs, Runnable task) {
            scheduleNames.add(name);
            periods.add(periodMs);
            scheduled.add(task);
        }

        @Override public <T> void provide(Class<T> api, T impl) { services.put(api, impl); }

        @SuppressWarnings("unchecked")
        @Override public <T> T require(Class<T> api) { return (T) services.get(api); }
    }

    static MemoryBlock block(long id, EgressTier tier, String maskedText) {
        MemoryBlock b = new MemoryBlock();
        b.id = id;
        b.episodeId = id;
        b.egressTier = tier;
        b.maskedText = maskedText;
        b.title = "标题" + id;
        b.redactionLevel = RedactionLevel.RULE;
        b.tStart = id * 1000;
        return b;
    }

    static List<Capability> networkOnly() {
        List<Capability> out = new ArrayList<>();
        out.add(Capability.NETWORK);
        return out;
    }

    static EgressPolicy policy() {
        return (pkg, masked, title, text) -> EgressTier.GRAY;
    }
}
