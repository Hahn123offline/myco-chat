package com.dsh.models;

import com.dsh.host.api.Capability;
import com.dsh.host.api.CapabilityLedger;
import com.dsh.host.api.Config;
import com.dsh.host.api.Event;
import com.dsh.host.api.EventBus;
import com.dsh.host.api.HostContext;
import com.dsh.host.api.Log;
import com.dsh.host.api.Store;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单测替身集合。全部不连网、不碰安卓 API。
 * 文件名不以 Test 结尾，JUnit 不会把它当测试类收集。
 */
final class TestSupport {

    private TestSupport() { }

    // ---------------- 配置 ----------------

    /** 可变 Config：改 map 即"热更新"，用于验证路由器每次调用都重新读配置。 */
    static final class FakeConfig implements Config {
        private final Map<String, String> m = new LinkedHashMap<>();

        FakeConfig set(String k, String v) { m.put(k, v); return this; }
        FakeConfig remove(String k) { m.remove(k); return this; }

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

    /** 两个 provider 的标准配置（local 本地小模型 + cloud 云端） */
    static FakeConfig twoProviders() {
        FakeConfig c = new FakeConfig();
        c.set("providers", "local,cloud");
        c.set("provider.local.type", "local");
        c.set("provider.local.baseUrl", "http://127.0.0.1:8080");
        c.set("provider.local.model", "minicpm-v-4.7-1b");
        c.set("provider.cloud.type", "openai");
        c.set("provider.cloud.baseUrl", "https://api.example.com/");
        c.set("provider.cloud.apiKey", "sk-test");
        c.set("provider.cloud.model", "deepseek-chat");
        return c;
    }

    // ---------------- 日志 / 探测 ----------------

    static final class FakeLog implements Log {
        final List<String> lines = new CopyOnWriteArrayList<>();
        @Override public void i(String tag, String msg) { lines.add("I/" + tag + ": " + msg); }
        @Override public void w(String tag, String msg) { lines.add("W/" + tag + ": " + msg); }
        @Override public void e(String tag, String msg, Throwable t) { lines.add("E/" + tag + ": " + msg); }
    }

    static final class FakeProbe implements ProviderProbe {
        volatile boolean up;
        volatile String kind = ProbeResult.OK;
        volatile String detail = "";
        volatile String lastThread = "";
        final List<String> probed = new CopyOnWriteArrayList<>();

        FakeProbe(boolean up) { this.up = up; }

        @Override public ProbeResult probe(ProviderConfig cfg) {
            probed.add(cfg.name);
            lastThread = Thread.currentThread().getName();
            if (up) return ProbeResult.up();
            return ProbeResult.down(kind, detail);
        }
    }

    // ---------------- HTTP 假传输 ----------------

    static final class FakeTransport implements HttpTransport {
        interface Handler {
            Response handle(String url, Map<String, String> headers, byte[] body) throws Exception;
        }

        volatile Handler postHandler;
        volatile Handler getHandler;
        final List<String> urls = new CopyOnWriteArrayList<>();
        volatile String lastUrl;
        volatile Map<String, String> lastHeaders;
        volatile byte[] lastBody;
        final AtomicInteger posts = new AtomicInteger();
        final AtomicInteger gets = new AtomicInteger();

        @Override public Response post(String url, Map<String, String> headers, byte[] body, int timeoutMs)
                throws Exception {
            posts.incrementAndGet();
            lastUrl = url;
            lastHeaders = headers;
            lastBody = body;
            urls.add(url);
            Handler h = postHandler;
            if (h == null) throw new IllegalStateException("测试没设置 postHandler");
            return h.handle(url, headers, body);
        }

        @Override public Response get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
            gets.incrementAndGet();
            lastUrl = url;
            lastHeaders = headers;
            urls.add(url);
            Handler h = getHandler;
            if (h == null) throw new IllegalStateException("测试没设置 getHandler");
            return h.handle(url, headers, null);
        }
    }

    static String okBody(String model, String content) {
        return "{\"model\":" + Json.quote(model)
                + ",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + Json.quote(content) + "}}]}";
    }

    // ---------------- provider 替身 ----------------

    static final class FakeProvider implements LlmProvider {
        final String label;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final List<String> models = new CopyOnWriteArrayList<>();
        volatile long sleepMs = 0;
        volatile boolean fail = false;
        volatile String failMessage = "假失败";

        FakeProvider(String label) { this.label = label; }

        @Override public LlmResponse complete(LlmRequest req) throws Exception {
            calls.incrementAndGet();
            int a = active.incrementAndGet();
            maxActive.accumulateAndGet(a, Math::max);
            try {
                if (sleepMs > 0) Thread.sleep(sleepMs);
                if (fail) return LlmResponse.error(failMessage);
                return LlmResponse.ok("from:" + label, label);
            } finally {
                active.decrementAndGet();
            }
        }
    }

    /** 按 provider 名返回假 provider；并记录路由器传给 factory 的 model（验证路由覆盖） */
    static final class FakeFactory implements ModelRouter.ProviderFactory {
        final Map<String, FakeProvider> byName = new ConcurrentHashMap<>();
        final List<ProviderConfig> seen = new CopyOnWriteArrayList<>();

        @Override public LlmProvider create(ProviderConfig cfg) {
            seen.add(cfg);
            return byName.computeIfAbsent(cfg.name, FakeProvider::new);
        }

        FakeProvider provider(String name) { return byName.get(name); }

        ProviderConfig lastSeen() {
            return seen.isEmpty() ? null : seen.get(seen.size() - 1);
        }
    }

    // ---------------- 宿主上下文替身（模块装配测试） ----------------

    static final class FakeHostContext implements HostContext {
        /** 非 final：测试里可以整体换一份，模拟 ConfigStore 按 mtime 重新 load 出新对象 */
        FakeConfig config = new FakeConfig();
        final FakeLog log = new FakeLog();
        final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
        final List<AutoCloseable> effects = new ArrayList<>();
        final List<Runnable> scheduled = new ArrayList<>();
        final List<Long> periods = new ArrayList<>();

        @Override public EventBus bus() { return null; }
        @Override public CapabilityLedger ledger() { return null; }
        @Override public Store store() { return null; }
        @Override public Config config() { return config; }
        @Override public Log log() { return log; }

        @Override public AutoCloseable effect(Runnable onDispose) {
            AutoCloseable c = onDispose::run;
            effects.add(c);
            return c;
        }

        @Override public <T extends Event> void on(Class<T> type, EventBus.Handler<T> h) { }

        @Override public void schedule(String name, long periodMs, Runnable task) {
            scheduled.add(task);
            periods.add(periodMs);
        }

        @Override public <T> void provide(Class<T> api, T impl) { services.put(api, impl); }

        @SuppressWarnings("unchecked")
        @Override public <T> T require(Class<T> api) { return (T) services.get(api); }

        /** 模拟宿主卸载：倒序关闭 effect（与 Scope 语义一致） */
        void disposeAll() {
            for (int i = effects.size() - 1; i >= 0; i--) {
                try { effects.get(i).close(); } catch (Exception ignored) { }
            }
        }
    }

    // ---------------- 工具 ----------------

    /** 同步等待一次 complete 的回调（测试专用，不用于生产调用方） */
    static LlmResponse await(LlmService svc, LlmRequest req, long timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        final LlmResponse[] out = new LlmResponse[1];
        svc.complete(req, r -> {
            out[0] = r;
            latch.countDown();
        });
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out[0] == null ? LlmResponse.error("测试等待回调超时") : out[0];
    }
}
