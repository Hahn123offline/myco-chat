package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.Health;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ModelRouterTest {

    private ModelRouter router(TestSupport.FakeConfig c, TestSupport.FakeFactory f,
                               TestSupport.FakeProbe probe) {
        return new ModelRouter(c, f, probe, new TestSupport.FakeLog());
    }

    private static LlmRequest req(String task, String user) {
        return LlmRequest.of(task, "", user);
    }

    // ---------------- 路由 ----------------

    @Test public void 按taskId选provider与model() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.title", "local:minicpm-v-4.7-1b");
        c.set("task.redact", "cloud:deepseek-chat");
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            LlmResponse title = TestSupport.await(r, req("title", "x"), 3000);
            assertTrue(title.error, title.isOk());
            assertEquals("local", f.lastSeen().name);
            assertEquals("minicpm-v-4.7-1b", f.lastSeen().model);

            LlmResponse redact = TestSupport.await(r, req("redact", "x"), 3000);
            assertTrue(redact.error, redact.isOk());
            assertEquals("cloud", f.lastSeen().name);
            assertEquals("deepseek-chat", f.lastSeen().model);
        } finally {
            r.close();
        }
    }

    @Test public void 未配置的task走default路由() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("default", "cloud:cheap-model");
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            LlmResponse resp = TestSupport.await(r, req("summarize", "x"), 3000);
            assertTrue(resp.error, resp.isOk());
            assertEquals("cloud", f.lastSeen().name);
            assertEquals("cheap-model", f.lastSeen().model);
        } finally {
            r.close();
        }
    }

    @Test public void 没有路由时返回error而不是乱猜一个provider() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            LlmResponse resp = TestSupport.await(r, req("unknown-task", "x"), 3000);
            assertFalse(resp.isOk());
            assertTrue(resp.error.contains("没有为 task=unknown-task"));
            assertEquals(0, f.seen.size());     // 一个 provider 都没创建
        } finally {
            r.close();
        }
    }

    @Test public void 配置热加载_同一router改配置后走新路由() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.title", "local:m1");
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            assertTrue(TestSupport.await(r, req("title", "x"), 3000).isOk());
            assertEquals("local", f.lastSeen().name);
            assertEquals("m1", f.lastSeen().model);

            // 热更新：只改配置，不重建模块
            c.set("task.title", "cloud:m2");
            c.set("provider.cloud.model", "m2");

            assertTrue(TestSupport.await(r, req("title", "x"), 3000).isOk());
            assertEquals("cloud", f.lastSeen().name);
            assertEquals("m2", f.lastSeen().model);
        } finally {
            r.close();
        }
    }

    @Test public void 没配置provider时诚实报错() {
        TestSupport.FakeConfig c = new TestSupport.FakeConfig();
        c.set("task.title", "local:m1");
        ModelRouter r = new ModelRouter(c, new TestSupport.FakeFactory(),
                new TestSupport.FakeProbe(true), new TestSupport.FakeLog());
        try {
            LlmResponse resp = TestSupport.await(r, req("title", "x"), 3000);
            assertFalse(resp.isOk());
            assertTrue(resp.error.contains("没有可用的 provider"));
        } finally {
            r.close();
        }
    }

    // ---------------- 超时 / 降级 ----------------

    @Test public void 请求超时按配置生效_并返回error() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("provider.cloud.timeoutMs", "80");
        c.set("task.title", "cloud:m1");
        TestSupport.FakeProvider slow = new TestSupport.FakeProvider("cloud");
        slow.sleepMs = 3_000;
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("cloud", slow);

        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            long t0 = System.nanoTime();
            LlmResponse resp = TestSupport.await(r, req("title", "x"), 3_000);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            assertFalse(resp.isOk());
            assertTrue(resp.error, resp.error.contains("超时"));
            assertTrue("耗时应该接近配置的 80ms 而不是 provider 的 3s，实际 " + ms,
                    ms < 1_500);
        } finally {
            r.close();
        }
    }

    @Test public void 失败按降级链换provider重试() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.redact", "local:m1,cloud:m2");
        TestSupport.FakeProvider local = new TestSupport.FakeProvider("local");
        local.fail = true;
        local.failMessage = "本地模型没起来";
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("local", local);

        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            LlmResponse resp = TestSupport.await(r, req("redact", "x"), 3_000);
            assertTrue(resp.error, resp.isOk());
            assertEquals("from:cloud", resp.text);
            assertEquals(1, local.calls.get());
            assertEquals(1, f.provider("cloud").calls.get());
            // 先 local 后 cloud
            assertEquals("local", f.seen.get(0).name);
            assertEquals("cloud", f.seen.get(1).name);
            assertEquals("m2", f.seen.get(1).model);
        } finally {
            r.close();
        }
    }

    @Test public void 降级链全失败时error里点名每一条() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.redact", "local:m1,cloud:m2");
        TestSupport.FakeProvider local = new TestSupport.FakeProvider("local");
        local.fail = true;
        local.failMessage = "本地挂了";
        TestSupport.FakeProvider cloud = new TestSupport.FakeProvider("cloud");
        cloud.fail = true;
        cloud.failMessage = "云端 401";
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("local", local);
        f.byName.put("cloud", cloud);

        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            LlmResponse resp = TestSupport.await(r, req("redact", "x"), 3_000);
            assertFalse(resp.isOk());
            assertTrue(resp.error, resp.error.contains("本地挂了"));
            assertTrue(resp.error, resp.error.contains("云端 401"));
        } finally {
            r.close();
        }
    }

    @Test public void 回调恰好一次_超时之后也不会再来一次() throws Exception {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("provider.cloud.timeoutMs", "60");
        c.set("task.title", "cloud:m1");
        TestSupport.FakeProvider slow = new TestSupport.FakeProvider("cloud");
        slow.sleepMs = 400;
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("cloud", slow);
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));

        try {
            final AtomicInteger callbacks = new AtomicInteger();
            final CountDownLatch done = new CountDownLatch(1);
            r.complete(req("title", "x"), resp -> {
                callbacks.incrementAndGet();
                done.countDown();
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            Thread.sleep(500);   // 等被 cancel 的 provider 线程自然结束
            assertEquals(1, callbacks.get());
        } finally {
            r.close();
        }
    }

    // ---------------- 本地并发 ----------------

    @Test public void 本地provider并发上限是1_超出的排队而不是同时跑() throws Exception {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.title", "local:m1");
        TestSupport.FakeProvider local = new TestSupport.FakeProvider("local");
        local.sleepMs = 80;
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("local", local);

        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            int n = 3;
            CountDownLatch done = new CountDownLatch(n);
            List<LlmResponse> outs = new CopyOnWriteArrayList<>();
            for (int i = 0; i < n; i++) {
                r.complete(req("title", "x" + i), resp -> {
                    outs.add(resp);
                    done.countDown();
                });
            }
            assertTrue("3 个请求都应完成", done.await(5, TimeUnit.SECONDS));
            assertEquals(n, outs.size());
            assertEquals(1, local.maxActive.get());   // 关键：从没同时跑两个
            assertEquals(n, local.calls.get());
        } finally {
            r.close();
        }
    }

    @Test public void 并发调用时complete立刻返回_不阻塞调用线程() throws Exception {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.title", "local:m1");
        TestSupport.FakeProvider local = new TestSupport.FakeProvider("local");
        local.sleepMs = 300;
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("local", local);
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            long t0 = System.nanoTime();
            r.complete(req("title", "x"), resp -> { });
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            assertTrue("complete() 必须立即返回，实际 " + ms + "ms", ms < 100);
        } finally {
            r.close();
        }
    }

    // ---------------- health ----------------

    @Test public void 本地provider没起来就是FAILED_不假装OK() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("providers", "local");           // 只有本地
        ModelRouter r = router(c, new TestSupport.FakeFactory(), new TestSupport.FakeProbe(false));
        try {
            r.probeNow();
            Health h = r.health();
            assertEquals(Health.State.FAILED, h.state);
            assertTrue(h.detail.contains("不可达"));
        } finally {
            r.close();
        }
    }

    @Test public void 探测失败原因进health_detail可诊断() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("providers", "local");
        TestSupport.FakeProbe probe = new TestSupport.FakeProbe(false);
        probe.kind = "connect-refused";
        probe.detail = "ConnectException: Connection refused";
        TestSupport.FakeLog log = new TestSupport.FakeLog();
        ModelRouter r = new ModelRouter(c, new TestSupport.FakeFactory(), probe, log);
        try {
            r.probeNow();
            Health h = r.health();
            assertEquals(Health.State.FAILED, h.state);
            assertTrue(h.detail, h.detail.contains("connect-refused"));
            assertTrue(h.detail, h.detail.contains("ConnectException"));
            boolean hasErrLog = false;
            for (String line : log.lines) {
                if (line.contains("DOWN") && line.contains("err=ConnectException")) hasErrLog = true;
            }
            assertTrue("DOWN 日志必须带异常类名: " + log.lines, hasErrLog);
        } finally {
            r.close();
        }
    }

    @Test public void probeAsync在models_io后台线程执行_不在调用线程() throws Exception {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("providers", "local");
        TestSupport.FakeProbe probe = new TestSupport.FakeProbe(true);
        ModelRouter r = new ModelRouter(c, new TestSupport.FakeFactory(), probe,
                new TestSupport.FakeLog());
        try {
            r.probeAsync();
            long deadline = System.currentTimeMillis() + 3_000;
            while (probe.lastThread.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue("探测应在 models-io 线程执行，实际: '" + probe.lastThread + "'",
                    probe.lastThread.startsWith("models-io"));
        } finally {
            r.close();
        }
    }

    @Test public void 本地探测通过时报OK() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        ModelRouter r = router(c, new TestSupport.FakeFactory(), new TestSupport.FakeProbe(true));
        try {
            r.probeNow();
            assertEquals(Health.State.OK, r.health().state);
        } finally {
            r.close();
        }
    }

    @Test public void 尚未探测时是DEGRADED_未知不等于健康() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        ModelRouter r = router(c, new TestSupport.FakeFactory(), new TestSupport.FakeProbe(true));
        try {
            Health h = r.health();
            assertEquals(Health.State.DEGRADED, h.state);
            assertTrue(h.detail.contains("尚未探测"));
        } finally {
            r.close();
        }
    }

    @Test public void 本地挂了但云端在时降级为DEGRADED() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        ModelRouter r = router(c, new TestSupport.FakeFactory(), new TestSupport.FakeProbe(false));
        try {
            r.probeNow();
            Health h = r.health();
            assertEquals(Health.State.DEGRADED, h.state);
            assertTrue(h.detail.contains("不可达"));
        } finally {
            r.close();
        }
    }

    @Test public void 真实调用失败后health会带上原因_成功即恢复() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.title", "local:m1");
        TestSupport.FakeProvider local = new TestSupport.FakeProvider("local");
        local.fail = true;
        local.failMessage = "推理进程崩了";
        TestSupport.FakeFactory f = new TestSupport.FakeFactory();
        f.byName.put("local", local);
        ModelRouter r = router(c, f, new TestSupport.FakeProbe(true));
        try {
            r.probeNow();
            TestSupport.await(r, req("title", "x"), 3000);
            Health h = r.health();
            assertEquals(Health.State.DEGRADED, h.state);
            assertTrue(h.detail.contains("推理进程崩了"));

            local.fail = false;
            TestSupport.await(r, req("title", "x"), 3000);
            assertEquals(Health.State.OK, r.health().state);
        } finally {
            r.close();
        }
    }

    @Test public void 没配置provider时health是未配置DEGRADED_不是故障FAILED() {
        ModelRouter r = new ModelRouter(new TestSupport.FakeConfig(), new TestSupport.FakeFactory(),
                new TestSupport.FakeProbe(true), new TestSupport.FakeLog());
        try {
            Health h = r.health();
            assertEquals(Health.State.DEGRADED, h.state);
            assertTrue(h.detail, h.detail.contains("no-providers-configured"));
            assertTrue(h.detail, h.detail.contains("providers"));
        } finally {
            r.close();
        }
    }

    @Test public void 关闭后health是STOPPED_调用立刻失败() {
        ModelRouter r = new ModelRouter(new TestSupport.FakeConfig(), new TestSupport.FakeFactory(),
                new TestSupport.FakeProbe(true), new TestSupport.FakeLog());
        r.close();
        assertEquals(Health.State.STOPPED, r.health().state);
        LlmResponse resp = TestSupport.await(r, req("title", "x"), 1000);
        assertFalse(resp.isOk());
        assertTrue(resp.error.contains("已关闭"));
    }

    @Test public void providers能列出当前配置() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        ModelRouter r = router(c, new TestSupport.FakeFactory(), new TestSupport.FakeProbe(true));
        try {
            assertNotNull(r.providers().get("local"));
            assertNotNull(r.providers().get("cloud"));
        } finally {
            r.close();
        }
    }
}
