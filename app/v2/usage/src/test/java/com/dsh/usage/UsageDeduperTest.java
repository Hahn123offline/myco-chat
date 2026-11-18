package com.dsh.usage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** 两路去重：同 pkg + 时间重叠 >80% 的 usage 痕迹跳过。 */
public class UsageDeduperTest {

    private static UsageSampler.Span span(String pkg, long t0, long t1) {
        return new UsageSampler.Span(pkg, t0, t1);
    }

    private static UsageDeduper.Covered cover(String pkg, long t0, long t1) {
        return new UsageDeduper.Covered(pkg, t0, t1);
    }

    @Test public void 完全重叠跳过() {
        assertTrue(UsageDeduper.shouldSkip(span("a", 1_000, 11_000),
                Arrays.asList(cover("a", 1_000, 11_000))));
    }

    @Test public void 重叠达到80percent跳过() {
        assertTrue(UsageDeduper.shouldSkip(span("a", 1_000, 11_000),
                Arrays.asList(cover("a", 3_000, 11_000))));   // 8000/10000
    }

    @Test public void 重叠不足80percent不跳过() {
        assertFalse(UsageDeduper.shouldSkip(span("a", 1_000, 11_000),
                Arrays.asList(cover("a", 6_000, 11_000))));   // 5000/10000
    }

    @Test public void 不同包名不跳过() {
        assertFalse(UsageDeduper.shouldSkip(span("a", 1_000, 11_000),
                Arrays.asList(cover("b", 1_000, 11_000))));
    }

    @Test public void 空区间直接跳过() {
        assertTrue(UsageDeduper.shouldSkip(span("a", 1_000, 1_000), Arrays.asList()));
        assertTrue(UsageDeduper.shouldSkip(null, Arrays.asList()));
    }

    @Test public void 无覆盖集合不跳过() {
        assertFalse(UsageDeduper.shouldSkip(span("a", 1_000, 11_000), null));
    }
}
