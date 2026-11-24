package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.data.MemoryBlock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** LIKE 主路径的相关度排序（Android 无 FTS5 时的 R@1 来源，478 条真实数据验证过）。 */
public class LikeRankerTest {

    private static MemoryBlock block(long id, String title, String body, long tStart) {
        MemoryBlock b = new MemoryBlock();
        b.id = id;
        b.title = title;
        b.maskedText = body;
        b.tStart = tStart;
        return b;
    }

    @Test public void 标题命中优先于正文命中() {
        MemoryBlock titleHit = block(1, "影之刃零 攻略", "普通正文", 100);
        MemoryBlock bodyHit = block(2, "随便什么标题", "今天看了影之刃零", 200);

        List<MemoryBlock> ranked = LikeRanker.rank(Arrays.asList(bodyHit, titleHit), "影之刃零", 5);
        assertEquals(1, ranked.get(0).id);
        assertTrue(LikeRanker.score(titleHit, "影之刃零") > LikeRanker.score(bodyHit, "影之刃零"));
    }

    @Test public void label也能被搜到但权重低于标题与正文() {
        MemoryBlock titleHit = block(1, "影之刃零 攻略", "正文", 100);
        MemoryBlock labelOnly = block(2, "随便标题", "正文", 200);
        labelOnly.label = "影之刃零";
        // 正文多次命中（长度归一后封顶 5 分）应高于 label 的 3 分
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 10; i++) body.append("影之刃零 ");
        while (body.length() < 100) body.append('x');
        MemoryBlock bodyHit = block(3, "另一个标题", body.toString(), 300);

        assertTrue(LikeRanker.score(labelOnly, "影之刃零") > 0);
        assertTrue("标签命中权重 3 低于标题一次命中 10",
                LikeRanker.score(titleHit, "影之刃零") > LikeRanker.score(labelOnly, "影之刃零"));
        assertTrue("正文强命中（封顶5）也高于标签",
                LikeRanker.score(bodyHit, "影之刃零") > LikeRanker.score(labelOnly, "影之刃零"));
    }

    @Test public void 正文命中按长度归一且封顶5分() {
        // 短正文 4 字命中 1 次：1 / (4/100+1) ≈ 0.9615
        MemoryBlock shortBody = block(1, "t", "影之刃零", 100);
        assertEquals(1.0 / (4 / 100.0 + 1.0), LikeRanker.score(shortBody, "影之刃零"), 0.01);

        // 长正文命中 10 次：10 / (100/100+1) = 5.0（正好封顶）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append("影之刃零 ");
        while (sb.length() < 100) sb.append('x');
        MemoryBlock longBody = block(2, "t", sb.toString(), 200);
        assertEquals(5.0, LikeRanker.score(longBody, "影之刃零"), 0.01);

        // 再快也超不过 5
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 100; i++) many.append("影之刃零 ");
        MemoryBlock capped = block(3, "t", many.toString(), 300);
        assertEquals(5.0, LikeRanker.score(capped, "影之刃零"), 0.01);
    }

    @Test public void 标题完全相等额外加2分() {
        MemoryBlock exact = block(1, "影之刃零", "正文", 100);
        MemoryBlock contains = block(2, "影之刃零 攻略", "正文", 100);
        double diff = LikeRanker.score(exact, "影之刃零") - LikeRanker.score(contains, "影之刃零");
        assertEquals(LikeRanker.TITLE_EXACT_BONUS, diff, 0.001);
    }

    @Test public void 同分按时间倒序() {
        MemoryBlock older = block(1, "影之刃零", "", 100);
        MemoryBlock newer = block(2, "影之刃零", "", 900);
        List<MemoryBlock> ranked = LikeRanker.rank(Arrays.asList(older, newer), "影之刃零", 2);
        assertEquals(2, ranked.get(0).id);
        assertEquals(1, ranked.get(1).id);
    }

    @Test public void 空query按时间倒序返回() {
        MemoryBlock a = block(1, "a", "x", 100);
        MemoryBlock b = block(2, "b", "y", 200);
        List<MemoryBlock> ranked = LikeRanker.rank(Arrays.asList(a, b), "   ", 10);
        assertEquals(2, ranked.get(0).id);
        assertEquals(1, ranked.get(1).id);
        assertEquals(0.0, LikeRanker.score(a, ""), 0.0);
    }

    @Test public void 取前limit条() {
        List<MemoryBlock> all = new ArrayList<>();
        for (int i = 1; i <= 10; i++) all.add(block(i, "影之刃零 " + i, "", 100 + i));
        assertEquals(3, LikeRanker.rank(all, "影之刃零", 3).size());
    }

    @Test public void 候选超上限由候选池配置控制_排序不丢相关项() {
        // 模拟：相关项在候选池内但不在"最近5条"里 → 打分排序能把它提到前面
        List<MemoryBlock> pool = new ArrayList<>();
        for (int i = 1; i <= 50; i++) pool.add(block(i, "无关 " + i, "正文", 1_000 + i));
        MemoryBlock needle = block(999, "影之刃零 攻略", "正文", 10);   // 最旧、但标题命中
        pool.add(needle);

        List<MemoryBlock> ranked = LikeRanker.rank(pool, "影之刃零", 5);
        assertEquals(999, ranked.get(0).id);
    }

    @Test public void 出现次数统计_不区分大小写且不重叠() {
        assertEquals(2, LikeRanker.countOccurrences("aaaa", "aa"));
        assertEquals(1, LikeRanker.countOccurrences("Hello World", "hello"));
        assertEquals(2, LikeRanker.countOccurrences("PS5 和 ps5", "ps5"));
        assertEquals(0, LikeRanker.countOccurrences(null, "x"));
        assertEquals(0, LikeRanker.countOccurrences("x", ""));
    }

    @Test public void 候选池上限收敛到安全范围() {
        assertTrue(LikeRanker.clampCap(0) >= 1);
        assertTrue(LikeRanker.clampCap(999_999) <= LikeRanker.MAX_CANDIDATE_CAP);
        assertEquals(LikeRanker.DEFAULT_CANDIDATE_CAP,
                LikeRanker.clampCap(LikeRanker.DEFAULT_CANDIDATE_CAP));
    }
}
