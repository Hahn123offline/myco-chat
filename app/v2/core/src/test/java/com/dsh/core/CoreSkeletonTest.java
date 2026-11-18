package com.dsh.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** :core 骨架的契约测试：生命周期、作用域倒序释放、服务注册、事件总线、失败隔离。 */
public class CoreSkeletonTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    interface Echo { String echo(String s); }

    private static class StubModule implements Module {
        final String id;
        final List<String> log;
        final Runnable onInstall;
        int installs = 0;

        StubModule(String id, List<String> log, Runnable onInstall) {
            this.id = id;
            this.log = log;
            this.onInstall = onInstall;
        }

        @Override public String id() { return id; }
        @Override public String version() { return "test"; }
        @Override public List<Capability> requires() { return Collections.emptyList(); }

        @Override public void install(HostContext ctx) {
            installs++;
            if (onInstall != null) onInstall.run();
        }

        @Override public Health health() { return Health.ok(); }
    }

    private Host host(Scheduler scheduler) {
        SimpleEventBus bus = new SimpleEventBus();
        return new Host(bus, new SimpleLedger(10), new SimpleStore(tmp.getRoot()),
                new SimpleLog(), new ConfigStore(tmp.getRoot()),
                scheduler == null ? new Scheduler.NoopScheduler() : scheduler, null);
    }

    @Test public void 模块安装后服务可被其他模块require_卸载后撤销() {
        List<String> events = new ArrayList<>();
        Host host = host(null);
        host.register(new StubModule("a", events, null) {
            @Override public void install(HostContext ctx) {
                super.install(ctx);
                ctx.provide(Echo.class, s -> "a:" + s);
                ctx.effect(() -> events.add("a-disposed"));
            }
        });
        host.register(new StubModule("b", events, null) {
            @Override public void install(HostContext ctx) {
                super.install(ctx);
                Echo echo = ctx.require(Echo.class);
                assertNotNull(echo);
                events.add(echo.echo("hi"));
            }
        });
        host.startAll();
        assertEquals("a:hi", events.get(0));

        host.stopAll();
        assertEquals("a-disposed", events.get(events.size() - 1));
    }

    @Test public void 作用域倒序释放() {
        List<String> order = new ArrayList<>();
        Scope scope = new Scope("test");
        scope.add(() -> order.add("first"));
        scope.add(() -> order.add("second"));
        scope.add(() -> order.add("third"));
        scope.close();
        assertEquals(List.of("third", "second", "first"), order);
        assertEquals(0, scope.size());
    }

    @Test public void 已关闭的scope不再接受注册_立刻执行() {
        Scope scope = new Scope("test");
        scope.close();
        AtomicInteger disposed = new AtomicInteger();
        scope.add(disposed::incrementAndGet);
        assertEquals(1, disposed.get());
    }

    @Test public void 事件总线异步分发_同一事件多个订阅者() throws Exception {
        SimpleEventBus bus = new SimpleEventBus();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger got = new AtomicInteger();
        bus.subscribe(Ping.class, e -> { got.incrementAndGet(); latch.countDown(); });
        bus.subscribe(Ping.class, e -> { got.incrementAndGet(); latch.countDown(); });
        bus.publish(new Ping());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, got.get());
    }

    @Test public void 订阅者异常不影响其他订阅者() throws Exception {
        SimpleEventBus bus = new SimpleEventBus();
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe(Ping.class, e -> { throw new IllegalStateException("boom"); });
        bus.subscribe(Ping.class, e -> latch.countDown());
        bus.publish(new Ping());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test public void 单个模块install失败只标记自己_其他模块照常() {
        Host host = host(null);
        host.register(new StubModule("bad", new ArrayList<>(), () -> {
            throw new IllegalStateException("install boom");
        }));
        host.register(new StubModule("good", new ArrayList<>(), null));
        host.startAll();
        List<Host.Slot> slots = host.snapshot();
        assertEquals(2, slots.size());
        for (Host.Slot s : slots) {
            if (s.module.id().equals("bad")) {
                assertEquals(Health.State.FAILED, s.health.state);
                assertTrue(s.health.detail.contains("install boom"));
            } else {
                assertEquals(Health.State.OK, s.health.state);
            }
        }
    }

    @Test public void 周期任务通过注入的Scheduler生效_卸载后停止() {
        final Runnable[] task = new Runnable[1];
        final AutoCloseable[] handle = new AutoCloseable[1];
        final boolean[] closed = {false};
        Scheduler scheduler = (name, periodMs, r) -> {
            assertEquals("tick", name);
            assertEquals(1000L, periodMs);
            task[0] = r;
            handle[0] = () -> closed[0] = true;
            return handle[0];
        };
        AtomicInteger ticks = new AtomicInteger();
        Host host = host(scheduler);
        host.register(new StubModule("tick", new ArrayList<>(), null) {
            @Override public void install(HostContext ctx) {
                ctx.schedule("tick", 1000L, ticks::incrementAndGet);
            }
        });
        host.startAll();
        assertNotNull(task[0]);
        task[0].run();
        task[0].run();
        assertEquals(2, ticks.get());
        host.stopAll();
        assertTrue("scope 关闭必须取消调度", closed[0]);
    }

    @Test public void 配置热更新_按mtime重读() throws Exception {
        ConfigStore cs = new ConfigStore(tmp.getRoot());
        File f = cs.fileFor("guard");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "redact.batch.debugForce=false\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(cs.load("guard").getBoolean("redact.batch.debugForce", false));
        // mtime 精度可能只有秒：显式改内容 + 等待 mtime 变化
        Thread.sleep(1100);
        Files.write(f.toPath(), "redact.batch.debugForce=true\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(cs.load("guard").getBoolean("redact.batch.debugForce", false));
    }

    @Test public void SimpleLog文件兜底可写() throws Exception {
        File logFile = new File(tmp.getRoot(), "log/host.log");
        SimpleLog log = new SimpleLog(logFile, null);
        log.i("host", "hello");
        log.w("guard", "warn");
        assertTrue(logFile.exists());
        String content = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("I/host: hello"));
        assertTrue(content.contains("W/guard: warn"));
    }

    @Test public void 存储与能力账本() {
        SimpleStore store = new SimpleStore(tmp.getRoot());
        store.putString("k", "v");
        assertEquals("v", store.getString("k", ""));
        assertTrue(store.dir("trace").isDirectory());

        SimpleLedger ledger = new SimpleLedger(2);
        ledger.record("a", Capability.NETWORK, "t", "ok", 1);
        ledger.record("b", Capability.NETWORK, "t", "ok", 2);
        ledger.record("c", Capability.NETWORK, "t", "ok", 3);
        List<CapabilityLedger.Entry> recent = ledger.recent(10);
        assertEquals(2, recent.size());
        assertEquals("b", recent.get(0).moduleId);
        assertEquals("c", recent.get(1).moduleId);
    }

    static final class Ping extends Event { }
}
