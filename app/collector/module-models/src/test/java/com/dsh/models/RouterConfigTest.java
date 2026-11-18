package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class RouterConfigTest {

    @Test public void 解析两个provider与各自的默认值() {
        RouterConfig rc = RouterConfig.parse(TestSupport.twoProviders());

        assertTrue(rc.problems.toString(), rc.problems.isEmpty());
        assertEquals(2, rc.providers.size());

        ProviderConfig local = rc.providers.get("local");
        assertEquals(ProviderConfig.Type.LOCAL, local.type);
        assertTrue(local.local());
        assertEquals("http://127.0.0.1:8080", local.baseUrl);
        assertEquals("minicpm-v-4.7-1b", local.model);
        assertEquals(RouterConfig.DEFAULT_LOCAL_TIMEOUT_MS, local.timeoutMs);
        assertEquals("", local.apiKey);

        ProviderConfig cloud = rc.providers.get("cloud");
        assertEquals(ProviderConfig.Type.OPENAI, cloud.type);
        // 尾部 '/' 被规范化
        assertEquals("https://api.example.com", cloud.baseUrl);
        assertEquals("https://api.example.com/v1/chat/completions", cloud.chatEndpoint());
        assertEquals("deepseek-chat", cloud.model);
        assertEquals(RouterConfig.DEFAULT_CLOUD_TIMEOUT_MS, cloud.timeoutMs);
    }

    @Test public void local的baseUrl和model可以缺省_用回环地址与小模型默认() {
        TestSupport.FakeConfig c = new TestSupport.FakeConfig();
        c.set("providers", "local");
        c.set("provider.local.type", "local");
        RouterConfig rc = RouterConfig.parse(c);
        assertTrue(rc.problems.toString(), rc.problems.isEmpty());
        assertEquals(RouterConfig.DEFAULT_LOCAL_BASE_URL, rc.providers.get("local").baseUrl);
        assertEquals(RouterConfig.DEFAULT_LOCAL_MODEL, rc.providers.get("local").model);
    }

    @Test public void type写错就进problems_不猜也不悄悄降级() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("provider.cloud.type", "azureish");
        RouterConfig rc = RouterConfig.parse(c);

        assertFalse(rc.providers.containsKey("cloud"));
        assertTrue(rc.providers.containsKey("local"));
        assertTrue(rc.problems.get(0).contains("provider.cloud.type"));
    }

    @Test public void 云端缺baseUrl进problems() {
        TestSupport.FakeConfig c = new TestSupport.FakeConfig();
        c.set("providers", "cloud");
        c.set("provider.cloud.type", "openai");
        c.set("provider.cloud.baseUrl", "");
        RouterConfig rc = RouterConfig.parse(c);
        assertFalse(rc.providers.containsKey("cloud"));
        assertTrue(rc.problems.toString().contains("baseUrl"));
    }

    @Test public void 没配置providers时problems说清楚() {
        RouterConfig rc = RouterConfig.parse(new TestSupport.FakeConfig());
        assertTrue(rc.providers.isEmpty());
        assertTrue(rc.problems.get(0).contains("providers"));
    }

    @Test public void 路由按taskId精确命中_读不到才用default() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.title", "local:minicpm-v-4.7-1b");
        c.set("task.redact", "cloud:deepseek-chat");
        c.set("default", "cloud:cheap-model");

        List<RouteTarget> title = RouterConfig.route(c, "title");
        assertEquals(1, title.size());
        assertEquals("local", title.get(0).provider);
        assertEquals("minicpm-v-4.7-1b", title.get(0).model);

        List<RouteTarget> unknown = RouterConfig.route(c, "summarize");
        assertEquals("cloud", unknown.get(0).provider);
        assertEquals("cheap-model", unknown.get(0).model);
    }

    @Test public void 降级链与省略model的写法() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        c.set("task.redact", "local, cloud:deepseek-chat ,, ");
        List<RouteTarget> chain = RouterConfig.route(c, "redact");
        assertEquals(2, chain.size());
        assertEquals("local", chain.get(0).provider);
        assertNull(chain.get(0).model);       // 用 provider 默认模型
        assertEquals("cloud", chain.get(1).provider);
        assertEquals("deepseek-chat", chain.get(1).model);
    }

    @Test public void 完全没有路由时返回空链() {
        TestSupport.FakeConfig c = TestSupport.twoProviders();
        assertTrue(RouterConfig.route(c, "title").isEmpty());
    }
}
