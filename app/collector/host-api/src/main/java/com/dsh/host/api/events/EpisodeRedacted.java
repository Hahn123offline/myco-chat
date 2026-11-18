package com.dsh.host.api.events;

import com.dsh.host.api.Event;
import com.dsh.host.api.data.RedactionLevel;

/**
 * 一段注意轨迹被脱敏后的结果。下游（记忆形成）只消费这个，不碰原文。
 * 注意：这里不包含任何原始敏感值，只有一个已脱敏文本和计数。
 */
public final class EpisodeRedacted extends Event {

    public static final String TIER_LOW = "low";
    public static final String TIER_HIGH = "high";

    public final long episodeId;
    public final String pkg;
    /** 脱敏后的文本片段（可安全进入记忆） */
    public final String maskedText;
    /** 命中的敏感类别与次数，如 "PHONE:2,VERIFY_CODE:1" */
    public final String hitSummary;
    /** low | high */
    public final String privacyTier;

    /**
     * 证据引用：由 usagetrace 生成的不透明 token（如 "trace:42"）。
     * 本模块（脱敏）只是原样透传，不解析、不改写。没有证据时为 null。
     */
    public final String evidenceRef;

    /**
     * 这次结果做到了哪一级脱敏，见 {@link RedactionLevel}。
     * 实时层先发 RULE；之后模型补充成功会再发一条 EpisodeRedactionUpgraded。
     */
    public final int redactionLevel;

    public EpisodeRedacted(long episodeId, String pkg, String maskedText,
                           String hitSummary, String privacyTier) {
        this(episodeId, pkg, maskedText, hitSummary, privacyTier, null, RedactionLevel.RULE);
    }

    public EpisodeRedacted(long episodeId, String pkg, String maskedText,
                           String hitSummary, String privacyTier, String evidenceRef) {
        this(episodeId, pkg, maskedText, hitSummary, privacyTier, evidenceRef, RedactionLevel.RULE);
    }

    public EpisodeRedacted(long episodeId, String pkg, String maskedText,
                           String hitSummary, String privacyTier, String evidenceRef,
                           int redactionLevel) {
        this.episodeId = episodeId;
        this.pkg = pkg;
        this.maskedText = maskedText;
        this.hitSummary = hitSummary;
        this.privacyTier = privacyTier;
        this.evidenceRef = evidenceRef;
        this.redactionLevel = RedactionLevel.isValid(redactionLevel) ? redactionLevel : RedactionLevel.RULE;
    }
}
