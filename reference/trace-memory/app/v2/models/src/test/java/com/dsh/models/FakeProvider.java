package com.dsh.models;

import com.dsh.egress.EgressGate;
import com.dsh.egress.EgressTier;
import com.dsh.egress.MemoryAudit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** 测试用 provider：记录调用次数与最后一次请求。 */
public final class FakeProvider implements ModelProvider {

    public final String id;
    public final ProviderKind kind;
    public final Set<Capability> caps;
    public final AtomicInteger calls = new AtomicInteger();
    public volatile ModelRequest lastRequest;
    public volatile ModelResponse reply;

    public FakeProvider(String id, ProviderKind kind, Capability... caps) {
        this.id = id;
        this.kind = kind;
        this.caps = caps == null ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(caps));
    }

    @Override public String id() { return id; }
    @Override public ProviderKind kind() { return kind; }
    @Override public Set<Capability> caps() { return caps; }
    @Override public int maxContext() { return 64000; }
    @Override public CostClass costClass() { return CostClass.CHEAP; }

    @Override public ModelResponse complete(ModelRequest request) {
        calls.incrementAndGet();
        lastRequest = request;
        if (reply != null) return reply;
        ModelResponse r = ModelResponse.ok("ok", "fake-model", id);
        return r.withUsage(10, 5, 7).withLatency(12);
    }

    public static EgressGate gate() { return new EgressGate(null, new MemoryAudit()); }

    public static SimpleTask task(Capability cap, Quality q, EgressTier tier) {
        return new SimpleTask("t", Collections.singleton(cap), q, tier);
    }
}
