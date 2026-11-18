package com.dsh.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.events.EpisodeRedacted;
import com.dsh.host.api.memory.EgressTier;

import org.junit.Test;

public class MemoryMapperTest {

    private EpisodeRedacted ev(String pkg, String masked, String hits, String tier) {
        return new EpisodeRedacted(42L, pkg, masked, hits, tier);
    }

    private EpisodeRedacted ev(String pkg, String masked, String hits, String tier, String evidenceRef) {
        return new EpisodeRedacted(42L, pkg, masked, hits, tier, evidenceRef);
    }

    @Test public void 事件映射成块() {
        MemoryBlock b = MemoryMapper.from(
                ev("com.tencent.mm", "联系我 138*****678", "PHONE:1", "high"),
                "深港驾校小米", 1000, 90000);

        assertEquals(42L, b.episodeId);
        assertEquals("com.tencent.mm", b.pkg);
        assertEquals("深港驾校小米", b.title);
        assertEquals("联系我 138*****678", b.maskedText);
        assertEquals("PHONE:1", b.hitSummary);
        assertEquals("high", b.privacyTier);
        assertEquals(1000, b.tStart);
        assertEquals(90000, b.tEnd);
    }

    @Test public void 空字段有兜底() {
        MemoryBlock b = MemoryMapper.from(ev(null, null, null, null), null, 0, 0);
        assertEquals("", b.pkg);
        assertEquals("", b.title);
        assertEquals("", b.maskedText);
        assertEquals("low", b.privacyTier);
        assertEquals("", b.hitSummary);
    }

    @Test public void 脱敏后的文本原样保留_不再二次加工() {
        String masked = "[验证码已丢弃] 您的验证码已发送";
        MemoryBlock b = MemoryMapper.from(ev("com.android.mms", masked, "VERIFY_CODE:1", "high"),
                "", 5, 6);
        assertEquals(masked, b.maskedText);
    }

    @Test public void 证据引用原样透传_不解析不改写() {
        String ref = "trace:42";
        MemoryBlock b = MemoryMapper.from(ev("com.tencent.mm", "x", "", "low", ref),
                "标题", 1, 2);
        assertEquals(ref, b.evidenceRef);
    }

    @Test public void 没有证据时保持null_不伪造空串() {
        MemoryBlock b = MemoryMapper.from(ev("com.tencent.mm", "x", "", "low", null),
                "标题", 1, 2);
        assertNull(b.evidenceRef);
    }

    @Test public void 默认按规则级落库_redactedAt用事件时间() {
        EpisodeRedacted e = ev("com.tencent.mm", "x", "", "low");
        MemoryBlock b = MemoryMapper.from(e, "标题", 1, 2);
        assertEquals(RedactionLevel.RULE, b.redactionLevel);
        assertEquals(e.ts, b.redactedAt);
    }

    @Test public void 模型级等级透传() {
        EpisodeRedacted e = new EpisodeRedacted(42L, "com.tencent.mm", "张*和138*****678",
                "PHONE:1", "high", "trace:42", RedactionLevel.MODEL);
        MemoryBlock b = MemoryMapper.from(e, "标题", 1, 2);
        assertEquals(RedactionLevel.MODEL, b.redactionLevel);
        assertEquals(e.ts, b.redactedAt);
        assertEquals("trace:42", b.evidenceRef);
    }

    @Test public void 非法等级退化为RULE_不把不知道当成模型校验过() {
        EpisodeRedacted e = new EpisodeRedacted(42L, "p", "x", "", "low", null, 99);
        MemoryBlock b = MemoryMapper.from(e, "", 1, 2);
        assertEquals(RedactionLevel.RULE, b.redactionLevel);
    }

    @Test public void 出网档位由门控传入并透传() {
        EpisodeRedacted e = ev("tv.danmaku.bili", "公开内容", "", "low", "trace:1");
        MemoryBlock b = MemoryMapper.from(e, "影之刃零 攻略", 1, 2, EgressTier.WHITE);
        assertEquals(EgressTier.WHITE, b.egressTier);
        assertEquals(RedactionLevel.RULE, b.redactionLevel);
    }

    @Test public void 不传tier时是UNKNOWN_绝不默认WHITE() {
        MemoryBlock b = MemoryMapper.from(ev("com.tencent.mm", "x", "", "low"), "", 1, 2);
        assertEquals(EgressTier.UNKNOWN, b.egressTier);
    }

    @Test public void 脱敏等级与出网分级是两个独立维度_不互相推导() {
        // 已做模型级脱敏，但门控说永不出设备：两者并存，不因为 MODEL 就放行
        EpisodeRedacted e = new EpisodeRedacted(42L, "com.tencent.mm", "张*的 138*****678",
                "PHONE:1", "high", null, RedactionLevel.MODEL);
        MemoryBlock b = MemoryMapper.from(e, "聊天", 1, 2, EgressTier.BLACK);
        assertEquals(RedactionLevel.MODEL, b.redactionLevel);
        assertEquals(EgressTier.BLACK, b.egressTier);
        // 反过来：WHITE 也不代表脱敏做到模型级
        MemoryBlock w = MemoryMapper.from(ev("tv.danmaku.bili", "公开视频", "", "low"),
                "攻略", 1, 2, EgressTier.WHITE);
        assertEquals(RedactionLevel.RULE, w.redactionLevel);
        assertEquals(EgressTier.WHITE, w.egressTier);
    }
}
