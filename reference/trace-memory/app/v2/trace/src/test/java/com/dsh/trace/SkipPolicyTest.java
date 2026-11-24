package com.dsh.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 结构性跳过列表：只按包名，不做语义判断。 */
public class SkipPolicyTest {

    @Test public void 命中列表内包名() {
        SkipPolicy p = SkipPolicy.of("com.android.systemui", "com.dsh.v2");
        assertTrue(p.shouldSkip("com.android.systemui"));
        assertTrue(p.shouldSkip("com.dsh.v2"));
        assertEquals(2, p.size());
    }

    @Test public void 列表外与空包名不跳过() {
        SkipPolicy p = SkipPolicy.of("com.android.systemui");
        assertFalse(p.shouldSkip("tv.danmaku.bili"));
        assertFalse(p.shouldSkip("com.android.settings"));
        assertFalse(p.shouldSkip(null));
        assertFalse(p.shouldSkip(""));
    }

    @Test public void 空策略什么都不跳过() {
        SkipPolicy none = SkipPolicy.none();
        assertEquals(0, none.size());
        assertFalse(none.shouldSkip("com.android.systemui"));
    }
}
