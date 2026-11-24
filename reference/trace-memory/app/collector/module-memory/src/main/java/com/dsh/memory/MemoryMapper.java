package com.dsh.memory;

import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.events.EpisodeRedacted;
import com.dsh.host.api.memory.EgressTier;

/** 事件 → 记忆块。纯映射，可单测。 */
public final class MemoryMapper {

    private MemoryMapper() { }

    /** 兼容旧调用：不传 tier 时按 UNKNOWN（不评估），绝不默认 WHITE。 */
    public static MemoryBlock from(EpisodeRedacted ev, String title, long tStart, long tEnd) {
        return from(ev, title, tStart, tEnd, EgressTier.UNKNOWN);
    }

    public static MemoryBlock from(EpisodeRedacted ev, String title, long tStart, long tEnd,
                                   EgressTier egressTier) {
        MemoryBlock b = new MemoryBlock();
        b.episodeId = ev.episodeId;
        b.pkg = ev.pkg == null ? "" : ev.pkg;
        b.title = title == null ? "" : title;
        b.maskedText = ev.maskedText == null ? "" : ev.maskedText;
        b.privacyTier = (ev.privacyTier == null || ev.privacyTier.isEmpty()) ? "low" : ev.privacyTier;
        b.hitSummary = ev.hitSummary == null ? "" : ev.hitSummary;
        // 不透明 token：只存不解析（不知道也不关心 usagetrace 的目录布局）
        b.evidenceRef = ev.evidenceRef;
        // 脱敏等级与时间：批处理据此挑活、检索据此判断可信度
        b.redactionLevel = RedactionLevel.isValid(ev.redactionLevel)
                ? ev.redactionLevel : RedactionLevel.RULE;
        b.redactedAt = ev.ts;
        // 出网分级：独立维度（能不能出设备），由门控策略决定
        b.egressTier = egressTier == null ? EgressTier.UNKNOWN : egressTier;
        b.tStart = tStart;
        b.tEnd = tEnd;
        return b;
    }
}
