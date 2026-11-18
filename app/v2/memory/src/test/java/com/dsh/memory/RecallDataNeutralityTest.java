package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RecallDataNeutralityTest {

    @Test public void data_summary不参与召回打分() {
        long now = System.currentTimeMillis();
        MemItem a = new MemItem("chat", Kind.NOTE, null, "我在改代理问题",
                "{\"summary\":\"模型整理版本A\"}");
        MemItem b = new MemItem("chat", Kind.NOTE, null, "我在改代理问题",
                "{\"summary\":\"完全不同的一段整理B，甚至包含股票代码\"}");
        for (MemItem m : Arrays.asList(a, b)) {
            m.t0 = m.t1 = now;
            m.detailLevel = DetailLevel.FULL;
            m.weight = 1.0;
            m.evidence = Arrays.asList(new Evidence("chat", "x", now));
        }
        a.id = 1; b.id = 2;
        MemorySource src = q -> Arrays.asList(a, b);
        List<RecallHit> hits = RecallCore.recall(src,
                new RecallQuery().query("代理").limit(5));
        assertEquals(2, hits.size());
        assertEquals(hits.get(0).score, hits.get(1).score, 1e-9);
        assertTrue(hits.get(0).text.contains("我在改代理问题"));
    }
}
