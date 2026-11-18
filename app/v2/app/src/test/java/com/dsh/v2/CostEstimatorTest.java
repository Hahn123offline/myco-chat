package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CostEstimatorTest {

    @Test public void 缓存命中比未命中便宜() {
        double hit = CostEstimator.usd(100_000, 100_000, 0);
        double miss = CostEstimator.usd(100_000, 0, 0);
        assertTrue(hit < miss);
        assertEquals(0.0003, hit, 1e-9);
        assertEquals(0.015, miss, 1e-9);
    }

    @Test public void 输出与人民币格式() {
        assertEquals("¥0.00", CostEstimator.cny(0, 0, 0));
        assertTrue(CostEstimator.cny(1_000_000, 1_000_000, 1_000_000).startsWith("¥"));
    }
}
