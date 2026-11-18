package com.dsh.host.api.events;

import com.dsh.host.api.Event;
import com.dsh.host.api.data.RedactionLevel;

/**
 * 已落库的 RULE 级记忆块被模型补充脱敏后发出（实时层的"升级"）。
 *
 * 为什么单独一个事件而不是再发一条 EpisodeRedacted：
 * 再发一条会让记忆层插出重复块。这条事件语义是"就地升级同一条块"，
 * 记忆层按 episodeId UPDATE；找不到（还没落库）就放过，批处理会再兜。
 *
 * 安全约定：maskedText 只允许比原来**掩码更多**，绝不还原原文。
 */
public final class EpisodeRedactionUpgraded extends Event {

    public final long episodeId;
    /** 升级后的文本（与旧文本等长或更长，掩码只增不减） */
    public final String maskedText;
    /** 见 {@link RedactionLevel}；正常是 MODEL */
    public final int redactionLevel;

    public EpisodeRedactionUpgraded(long episodeId, String maskedText, int redactionLevel) {
        this.episodeId = episodeId;
        this.maskedText = maskedText == null ? "" : maskedText;
        this.redactionLevel = RedactionLevel.isValid(redactionLevel) ? redactionLevel : RedactionLevel.MODEL;
    }
}
