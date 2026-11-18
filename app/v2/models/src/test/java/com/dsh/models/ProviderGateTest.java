package com.dsh.models;

import static org.junit.Assert.*;

import com.dsh.egress.EgressGate;
import com.dsh.guard.Guard;

import org.junit.Test;

import java.util.Collections;

public class ProviderGateTest {

    private static ModelRequest req() {
        return ModelRequest.text("", "hi");
    }

    @Test public void 无gate的云端provider直接安全拒绝() throws Exception {
        OpenAiCompatProvider p = new OpenAiCompatProvider("x", "http://x", "k", "m",
                1000, Collections.emptySet(), 1000, CostClass.CHEAP, null);
        ModelResponse r = p.complete(req());
        assertFalse(r.isOk());
        assertTrue(r.error.contains("缺少 EgressGate"));
    }

    @Test public void 有gate但RAW空sources被拒绝() throws Exception {
        EgressGate gate = new EgressGate(new Guard(), e -> { });
        OpenAiCompatProvider p = new OpenAiCompatProvider("x", "http://x", "k", "m",
                1000, Collections.emptySet(), 1000, CostClass.CHEAP, null, gate, null);
        ModelResponse r = p.complete(req());
        assertFalse(r.isOk());
        assertTrue(r.error.contains("egress:"));
    }
}
