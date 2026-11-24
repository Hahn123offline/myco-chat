package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.egress.EgressGate;
import com.dsh.egress.EgressStage;
import com.dsh.egress.EgressTier;
import com.dsh.egress.MemoryAudit;

import org.junit.Test;

import java.util.Collections;

public class ModelRouterProvenanceTest {

    private static ModelRouter router(FakeProvider cloud, MemoryAudit audit) {
        return new ModelRouter(Collections.singletonList(cloud), false,
                new EgressGate(null, audit), null);
    }

    @Test public void 微信RAW请求上云必须拒绝() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = router(cloud, new MemoryAudit());
        ModelResponse resp = r.execute(
                FakeProvider.task(Capability.JSON, Quality.GOOD, EgressTier.WHITE),
                ModelRequest.text("sys", "微信聊天内容")
                        .withProvenance(Collections.singletonList("com.tencent.mm"),
                                EgressStage.RAW));
        assertFalse(resp.isOk());
        assertTrue(resp.error, resp.error.contains("provenance"));
        assertEquals("拒绝后绝不调用 provider", 0, cloud.calls.get());
    }

    @Test public void 微信MASKED允许() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = router(cloud, new MemoryAudit());
        ModelResponse resp = r.execute(
                FakeProvider.task(Capability.JSON, Quality.GOOD, EgressTier.WHITE),
                ModelRequest.text("sys", "脱敏后的内容")
                        .withProvenance(Collections.singletonList("com.tencent.mm"),
                                EgressStage.MASKED));
        assertTrue(resp.error, resp.isOk());
        assertEquals(1, cloud.calls.get());
    }

    @Test public void 空来源RAW拒绝() {
        FakeProvider cloud = new FakeProvider("cloud", ProviderKind.CLOUD, Capability.JSON);
        ModelRouter r = router(cloud, new MemoryAudit());
        ModelResponse resp = r.execute(
                FakeProvider.task(Capability.JSON, Quality.GOOD, EgressTier.WHITE),
                ModelRequest.text("sys", "未知来源"));
        assertFalse(resp.isOk());
        assertEquals(0, cloud.calls.get());
    }
}
