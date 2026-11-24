package com.dsh.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MergeCandidatesTest {

    private static final long DAY = 24 * 3600_000L;
    private static final long BASE = 1794930000000L; // 2026-11-18 00:00 local-ish

    private static MergeCandidates.Item item(long id, long hour, String title, String body) {
        return new MergeCandidates.Item(id, title, body,
                BASE + hour * 3600_000L, BASE + hour * 3600_000L + 60_000L,
                "com.tencent.mm", 0);
    }

    @Test public void 代理事件四段能连成候选图() {
        List<MergeCandidates.Item> items = new ArrayList<>();
        items.add(item(1, 10, "DeepSeek 代理报错", "ERR_PROXY_CONNECTION_FAILED 微信 代理"));
        items.add(item(2, 18, "微信代理错误", "微信 扫码 代理 错误"));
        items.add(item(3, 19, "微信代理实验", "按刚才说的 微信 代理 强制停止"));
        items.add(item(4, 23, "微信代理Clash流量", "Clash 微信 代理"));
        // 填充无关联链，避免小样本下 微信/代理 被 df>0.3N 误当成通用词
        for (int k = 0; k < 10; k++) {
            items.add(item(100 + k, 12 + k % 5, "第" + k + "号独特主题" + k,
                    "独立内容" + k));
        }
        List<MergeCandidates.Candidate> cs = MergeCandidates.generate(items);
        assertTrue(cs.size() >= 3);
        java.util.Set<Long> connected = new java.util.HashSet<>();
        for (MergeCandidates.Candidate c : cs) { connected.add(c.a); connected.add(c.b); }
        assertEquals(4, connected.size());
        for (MergeCandidates.Candidate c : cs) {
            assertTrue(c.why.contains("terms=") || c.why.contains("error_code=")
                    || c.why.contains("explicit_ref"));
        }
    }

    @Test public void 共享错误码直接高优先级候选() {
        List<MergeCandidates.Item> items = new ArrayList<>();
        items.add(item(1, 10, "排查代理连接失败", "net:ERR_PROXY_CONNECTION_FAILED"));
        items.add(item(2, 18, "排查微信扫码代理错误", "提示 PROXY_CONNECTION_FAILED"));
        List<MergeCandidates.Candidate> cs = MergeCandidates.generate(items);
        assertTrue(cs.size() >= 1);
        MergeCandidates.Candidate top = cs.get(0);
        assertTrue(top.score >= 10000);
        assertTrue(top.why.contains("error_code="));
    }

    @Test public void 跨天不生成候选() {
        List<MergeCandidates.Item> items = new ArrayList<>();
        items.add(item(1, 10, "微信代理", "微信 代理 错误"));
        items.add(new MergeCandidates.Item(2, "微信代理", "微信 代理 错误",
                BASE + DAY + 10 * 3600_000L, BASE + DAY + 10 * 3600_000L, "com.tencent.mm", 0));
        assertTrue(MergeCandidates.generate(items).isEmpty());
    }

    @Test public void 通用词不生成候选() {
        List<MergeCandidates.Item> items = new ArrayList<>();
        items.add(new MergeCandidates.Item(1, "今天打开应用",
                "今天打开一个应用，记录一下内容", BASE + 10 * 3600_000L,
                BASE + 10 * 3600_000L, "", 0));
        items.add(new MergeCandidates.Item(2, "今天查看记录",
                "今天查看一个记录，内容可以", BASE + 11 * 3600_000L,
                BASE + 11 * 3600_000L, "", 0));
        assertTrue(MergeCandidates.generate(items).isEmpty());
    }
}
