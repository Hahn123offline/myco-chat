package com.dsh.memory;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecallRankerTest {

    @Test public void 相关文本分数更高() {
        double a = RecallRanker.similarity("代理报错", "微信扫码代理报错，去看了 Clash");
        double b = RecallRanker.similarity("代理报错", "今天中午吃了面条");
        assertTrue(a > b);
    }

    @Test public void manual不衰减() {
        assertTrue(RecallRanker.decay(1000, "manual") == 1.0);
        assertTrue(RecallRanker.decay(1000, "trace") < 0.1);
    }
}
