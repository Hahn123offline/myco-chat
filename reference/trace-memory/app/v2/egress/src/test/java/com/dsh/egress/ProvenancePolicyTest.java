package com.dsh.egress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ProvenancePolicyTest {

    @Test public void 微信RAW拒绝_MASKED允许() {
        assertFalse(ProvenancePolicy.allows(
                Collections.singletonList("com.tencent.mm"), EgressStage.RAW, true));
        assertTrue(ProvenancePolicy.allows(
                Collections.singletonList("com.tencent.mm"), EgressStage.MASKED, true));
    }

    @Test public void 支付宝MASKED仍拒绝_DERIVED允许() {
        assertFalse(ProvenancePolicy.allows(
                Collections.singletonList("com.eg.android.AlipayGphone"),
                EgressStage.MASKED, true));
        assertTrue(ProvenancePolicy.allows(
                Collections.singletonList("com.eg.android.AlipayGphone"),
                EgressStage.DERIVED, true));
    }

    @Test public void 空来源RAW拒绝_DERIVED允许() {
        assertFalse(ProvenancePolicy.allows(Collections.emptyList(),
                EgressStage.RAW, true));
        assertFalse(ProvenancePolicy.allows(Collections.emptyList(),
                EgressStage.MASKED, true));
        assertTrue(ProvenancePolicy.allows(Collections.emptyList(),
                EgressStage.DERIVED, true));
    }

    @Test public void 混合来源取最严() {
        assertFalse(ProvenancePolicy.allows(Arrays.asList(
                "tv.danmaku.bili", "com.tencent.mm"), EgressStage.RAW, true));
        assertTrue(ProvenancePolicy.allows(Arrays.asList(
                "tv.danmaku.bili", "com.tencent.mm"), EgressStage.MASKED, true));
        assertFalse(ProvenancePolicy.allows(Arrays.asList(
                "tv.danmaku.bili", "com.eg.android.AlipayGphone"),
                EgressStage.MASKED, true));
    }
}
