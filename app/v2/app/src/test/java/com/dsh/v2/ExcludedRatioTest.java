package com.dsh.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExcludedRatioTest {

    @Test public void 全排除或高占比_needsLocal() {
        assertEquals(ExcludedRatio.Decision.NEEDS_LOCAL_MODEL,
                ExcludedRatio.decide(20, 22, 0.3));
        assertEquals(ExcludedRatio.Decision.NEEDS_LOCAL_MODEL,
                ExcludedRatio.decide(3, 10, 0.3));   // 正好 0.3
        assertEquals(ExcludedRatio.Decision.NEEDS_LOCAL_MODEL,
                ExcludedRatio.decide(10, 10, 0.3));
    }

    @Test public void 低占比_partial() {
        assertEquals(ExcludedRatio.Decision.PARTIAL,
                ExcludedRatio.decide(1, 9, 0.3));
        assertEquals(ExcludedRatio.Decision.PARTIAL,
                ExcludedRatio.decide(2, 10, 0.3));   // 0.2
    }

    @Test public void 无排除_full() {
        assertEquals(ExcludedRatio.Decision.FULL, ExcludedRatio.decide(0, 10, 0.3));
        assertEquals(ExcludedRatio.Decision.FULL, ExcludedRatio.decide(0, 0, 0.3));
    }

    @Test public void 边界与比例() {
        assertEquals(0.0, ExcludedRatio.ratio(0, 10), 1e-9);
        assertEquals(0.3, ExcludedRatio.ratio(3, 10), 1e-9);
        assertEquals("partial", ExcludedRatio.textSource(ExcludedRatio.Decision.PARTIAL));
        assertEquals("excluded", ExcludedRatio.textSource(
                ExcludedRatio.Decision.NEEDS_LOCAL_MODEL));
    }
}
