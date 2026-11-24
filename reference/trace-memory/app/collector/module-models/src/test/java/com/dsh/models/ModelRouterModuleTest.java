package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.Capability;
import com.dsh.host.api.Health;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import org.junit.Test;

public class ModelRouterModuleTest {

    @Test public void install把LlmService提供给宿主() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        ctx.config.set("providers", "local");
        ctx.config.set("provider.local.type", "local");
        ctx.config.set("task.title", "local:minicpm-v-4.7-1b");

        TestSupport.FakeTransport http = new TestSupport.FakeTransport();
        http.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("minicpm-v-4.7-1b", "深港驾校小米"));

        ModelRouterModule m = new ModelRouterModule(http, new TestSupport.FakeProbe(true));
        m.install(ctx);

        LlmService svc = ctx.require(LlmService.class);
        assertNotNull("install 必须 provide(LlmService.class)", svc);

        LlmResponse resp = TestSupport.await(svc, LlmRequest.of("title", "", "这页叫什么"), 3000);
        assertTrue(resp.error, resp.isOk());
        assertEquals("深港驾校小米", resp.text);
    }

    @Test public void 声明NETWORK能力且id版本可读() {
        ModelRouterModule m = new ModelRouterModule(new TestSupport.FakeTransport(),
                new TestSupport.FakeProbe(true));
        assertEquals("models", m.id());
        assertFalse(m.version().isEmpty());
        assertTrue(m.requires().contains(Capability.NETWORK));
    }

    @Test public void 没配置provider也能装上_health是未配置DEGRADED不是故障() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        ModelRouterModule m = new ModelRouterModule(new TestSupport.FakeTransport(),
                new TestSupport.FakeProbe(true));
        m.install(ctx);

        assertNotNull(ctx.require(LlmService.class));
        assertEquals(Health.State.DEGRADED, m.health().state);
        assertTrue(m.health().detail, m.health().detail.contains("no-providers-configured"));
    }

    @Test public void 探测通过后health变OK_探测不过则DEGRADED() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        ctx.config.set("providers", "local");
        ctx.config.set("provider.local.type", "local");

        TestSupport.FakeProbe probe = new TestSupport.FakeProbe(true);
        ModelRouterModule m = new ModelRouterModule(new TestSupport.FakeTransport(), probe);
        m.install(ctx);

        m.router().probeNow();
        assertEquals(Health.State.OK, m.health().state);

        probe.up = false;
        m.router().probeNow();
        assertEquals(Health.State.FAILED, m.health().state);
    }

    @Test public void 卸载后router关闭_health为STOPPED_调用立刻失败() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        ctx.config.set("providers", "local");
        ctx.config.set("provider.local.type", "local");
        ctx.config.set("task.title", "local:m1");

        TestSupport.FakeTransport http = new TestSupport.FakeTransport();
        http.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("m1", "ok"));

        ModelRouterModule m = new ModelRouterModule(http, new TestSupport.FakeProbe(true));
        m.install(ctx);
        ctx.disposeAll();   // 宿主卸载模块：倒序撤销 scope

        assertEquals(Health.State.STOPPED, m.health().state);
        LlmResponse resp = TestSupport.await(ctx.require(LlmService.class),
                LlmRequest.of("title", "", "x"), 1000);
        assertFalse(resp.isOk());
        assertTrue(resp.error.contains("已关闭"));
    }

    @Test public void 模块热加载_Config对象整体更换后走新配置() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        TestSupport.FakeConfig c1 = TestSupport.twoProviders();
        c1.set("task.title", "local:minicpm-v-4.7-1b");
        ctx.config = c1;

        TestSupport.FakeTransport http = new TestSupport.FakeTransport();
        http.postHandler = (url, headers, body) ->
                new HttpTransport.Response(200, TestSupport.okBody("m", "ok"));

        ModelRouterModule m = new ModelRouterModule(http, new TestSupport.FakeProbe(true));
        m.install(ctx);

        assertTrue(TestSupport.await(ctx.require(LlmService.class),
                LlmRequest.of("title", "", "x"), 3000).isOk());
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", http.urls.get(0));

        // 模拟 ConfigStore 按 mtime 重新 load 出一份**新对象**：
        // router 里面如果存的是 Config 快照，这里就会仍然走本地。
        TestSupport.FakeConfig c2 = TestSupport.twoProviders();
        c2.set("task.title", "cloud:m2");
        ctx.config = c2;

        assertTrue(TestSupport.await(ctx.require(LlmService.class),
                LlmRequest.of("title", "", "x"), 3000).isOk());
        assertEquals("https://api.example.com/v1/chat/completions", http.urls.get(1));
    }

    @Test public void install注册了周期健康探测_不联网阻塞启动() {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        ctx.config.set("providers", "local");
        ctx.config.set("provider.local.type", "local");
        ctx.config.set("health.probeMs", "7000");

        ModelRouterModule m = new ModelRouterModule(new TestSupport.FakeTransport(),
                new TestSupport.FakeProbe(true));
        m.install(ctx);

        assertEquals(1, ctx.scheduled.size());
        assertEquals(7000L, (long) ctx.periods.get(0));
    }

    @Test public void 定时探测任务只提交异步_实际探测在models_io线程() throws Exception {
        TestSupport.FakeHostContext ctx = new TestSupport.FakeHostContext();
        ctx.config.set("providers", "local");
        ctx.config.set("provider.local.type", "local");

        TestSupport.FakeProbe probe = new TestSupport.FakeProbe(true);
        ModelRouterModule m = new ModelRouterModule(new TestSupport.FakeTransport(), probe);
        m.install(ctx);

        // install 自己会异步探测一次；等它完成后再跑"定时任务"，避免计数混淆
        long deadline = System.currentTimeMillis() + 3_000;
        while (probe.lastThread.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertTrue(probe.lastThread, probe.lastThread.startsWith("models-io"));

        probe.lastThread = "";
        int before = probe.probed.size();
        ctx.scheduled.get(0).run();     // 模拟主线程触发的周期任务
        deadline = System.currentTimeMillis() + 3_000;
        while (probe.probed.size() <= before && System.currentTimeMillis() < deadline) Thread.sleep(10);

        assertTrue("定时任务必须真的触发探测", probe.probed.size() > before);
        assertTrue("探测不能在主线程跑（否则 NetworkOnMainThreadException），实际: "
                + probe.lastThread, probe.lastThread.startsWith("models-io"));
        assertFalse(probe.lastThread.contains("Test worker"));
        m.router().close();
    }
}
