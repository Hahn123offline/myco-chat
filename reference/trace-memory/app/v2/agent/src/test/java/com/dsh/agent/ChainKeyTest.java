package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;

public class ChainKeyTest {

    @Test public void 顺序无关且稳定() {
        String a = ChainKey.of(Arrays.asList(3L, 1L, 2L));
        String b = ChainKey.of(Arrays.asList(1L, 2L, 3L));
        assertEquals(a, b);
        assertEquals(40, a.length());
    }

    @Test public void 不同集合不同键() {
        assertNotEquals(ChainKey.of(Arrays.asList(1L, 2L)),
                ChainKey.of(Arrays.asList(1L, 2L, 3L)));
    }

    @Test public void 从csv解析并排序() {
        assertEquals(ChainKey.of(Arrays.asList(5L, 6L)),
                ChainKey.ofBlockCsv("6, 5, 5"));
    }
}
