package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.egress.EgressGate;
import com.dsh.egress.EgressTier;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/** 路由硬规则 + 出网闸 + usage。 */
public class ModelRouterTest {

    @Test public void BLACK请求没有LOCAL时失败_绝不回落云端() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = new ModelRouter(Collections.singletonList(cloud), true,
                FakeProvider.gate(), null);
        ModelRouter.Route route = r.route(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.BLACK));
        assertFalse(route.ok());
        assertTrue(route.error.contains("BLACK"));
        ModelResponse resp = r.execute(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.BLACK), ModelRequest.text("", "内容"));
        assertFalse(resp.isOk());
        assertEquals("BLACK 时绝不能调用云端", 0, cloud.calls.get());
    }

    @Test public void BLACK只用LOCAL() {
        FakeProvider local = new FakeProvider("local", ProviderKind.LOCAL, Capability.JSON);
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = new ModelRouter(Arrays.asList(cloud, local), true,
                FakeProvider.gate(), null);
        ModelRouter.Route route = r.route(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.BLACK));
        assertTrue(route.ok());
        assertEquals("local", route.provider.id());
    }

    @Test public void WHITE_GOOD走云端_DRAFT走本地() {
        FakeProvider local = new FakeProvider("local", ProviderKind.LOCAL, Capability.JSON);
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = new ModelRouter(Arrays.asList(local, cloud), false,
                FakeProvider.gate(), null);
        assertEquals("cloud", r.route(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.WHITE)).provider.id());
        assertEquals("local", r.route(FakeProvider.task(Capability.JSON,
                Quality.DRAFT, EgressTier.WHITE)).provider.id());
    }

    @Test public void 能力不满足时失败而非降级() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = new ModelRouter(Collections.singletonList(cloud), false,
                FakeProvider.gate(), null);
        ModelRouter.Route route = r.route(FakeProvider.task(Capability.VISION,
                Quality.GOOD, EgressTier.WHITE));
        assertFalse(route.ok());
        assertTrue(route.error.contains("没有可用项"));
    }

    @Test public void GRAY默认不上云_显式允许才上云() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        FakeProvider local = new FakeProvider("local", ProviderKind.LOCAL, Capability.JSON);
        ModelRouter strict = new ModelRouter(Collections.singletonList(cloud), false,
                FakeProvider.gate(), null);
        assertFalse(strict.route(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.GRAY)).ok());

        ModelRouter loose = new ModelRouter(Collections.singletonList(cloud), true,
                FakeProvider.gate(), null);
        assertTrue(loose.route(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.GRAY)).ok());

        ModelRouter withLocal = new ModelRouter(Arrays.asList(cloud, local), false,
                FakeProvider.gate(), null);
        assertEquals("local", withLocal.route(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.GRAY)).provider.id());
    }

    @Test public void BLOCK内容在出网闸被替换_云端provider不会被调用() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        MemoryAuditCapture usage = new MemoryAuditCapture();
        ModelRouter r = new ModelRouter(Collections.singletonList(cloud), false,
                new EgressGate(null, new com.dsh.egress.MemoryAudit()), usage);
        ModelResponse resp = r.execute(FakeProvider.task(Capability.JSON,
                        Quality.GOOD, EgressTier.WHITE),
                ModelRequest.text("", "卡号 4111 1111 1111 1111")
                        .withProvenance(Collections.emptyList(),
                                com.dsh.egress.EgressStage.DERIVED));
        assertFalse(resp.isOk());
        assertTrue(resp.error.contains("egress"));
        assertEquals(0, cloud.calls.get());
        assertEquals(1, usage.records.size());
        assertEquals(0, usage.records.get(0).promptTokens);
    }

    @Test public void usage记录缓存命中字段() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        cloud.reply = ModelResponse.ok("answer", "m", "cloud").withUsage(120, 30, 80);
        MemoryAuditCapture usage = new MemoryAuditCapture();
        ModelRouter r = new ModelRouter(Collections.singletonList(cloud), false,
                FakeProvider.gate(), usage);
        ModelResponse resp = r.execute(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.WHITE), ModelRequest.text("sys", "你好")
                .withProvenance(Collections.emptyList(),
                        com.dsh.egress.EgressStage.DERIVED));
        assertTrue(resp.error, resp.isOk());
        assertEquals(1, usage.records.size());
        assertEquals(120, usage.records.get(0).promptTokens);
        assertEquals(30, usage.records.get(0).completionTokens);
        assertEquals(80, usage.records.get(0).promptCacheHitTokens);
        assertEquals("t", usage.records.get(0).taskId);
    }

    @Test public void 云端provider缺少出网闸时安全拒绝() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = new ModelRouter(Collections.singletonList(cloud), false, null, null);
        ModelResponse resp = r.execute(FakeProvider.task(Capability.JSON,
                Quality.GOOD, EgressTier.WHITE), ModelRequest.text("", "x"));
        assertFalse(resp.isOk());
        assertTrue(resp.error.contains("EgressGate"));
        assertEquals(0, cloud.calls.get());
    }

    static final class MemoryAuditCapture implements UsageSink {
        final ArrayList<UsageRecord> records = new ArrayList<>();
        @Override public void record(UsageRecord r) { records.add(r); }
    }
}
