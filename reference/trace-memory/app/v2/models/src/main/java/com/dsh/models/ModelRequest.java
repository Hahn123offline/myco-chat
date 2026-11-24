package com.dsh.models;

import com.dsh.egress.EgressStage;

import java.util.Collections;
import java.util.List;

/** 一次模型请求。不包含 provider/密钥；密钥由 provider 从安全存储注入。 */
public final class ModelRequest {

    public final String systemPrompt;
    public final String userPrompt;
    /** 可选 JPEG；null = 纯文本请求 */
    public final byte[] jpeg;
    /** 图片 detail：low / high（OpenAI vision）；null = 不写 */
    public final String imageDetail;
    public final int maxTokens;
    public final float temperature;
    /** 非空时优先于 system/user 快捷字段（agent 转录） */
    public final List<ChatMessage> messages;
    /** 工具声明（agent 用） */
    public final List<ToolSpec> tools;
    /** DeepSeek 思考模式开关：enabled / disabled / null（服务端默认） */
    public final String thinking;
    /** 本请求内容里被应用级排除的块数（审计用） */
    public final int excludedBlocks;
    /** 三级策略标记（审计用，如 REDACT_THEN_CLOUD） */
    public final String appPolicy;
    /** Masker 掩掉的 span 数与 kinds（审计用） */
    public final int maskedSpans;
    public final String maskedKinds;
    /** 这段 payload 的材料来自哪些应用（空 = 未知）。 */
    public final List<String> sources;
    /** 被剥掉（占位符化）的来源，只进审计，不参与判定。 */
    public final List<String> strippedSources;
    /** payload 所处阶段。 */
    public final EgressStage stage;
    /** recall 的掩码状态（审计用）：FULL_MASKED/TEXT_ONLY_MASKED/PLACEHOLDER/BLOCKED。 */
    public final String maskStatus;

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg,
                        int maxTokens, float temperature) {
        this(systemPrompt, userPrompt, jpeg, null, maxTokens, temperature,
                Collections.emptyList(), Collections.emptyList(), null);
    }

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg, String imageDetail,
                        int maxTokens, float temperature, List<ChatMessage> messages,
                        List<ToolSpec> tools, String thinking) {
        this(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens, temperature,
                messages, tools, thinking, 0, "REDACT_THEN_CLOUD", 0, "");
    }

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg, String imageDetail,
                        int maxTokens, float temperature, List<ChatMessage> messages,
                        List<ToolSpec> tools, String thinking, int excludedBlocks,
                        String appPolicy) {
        this(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens, temperature,
                messages, tools, thinking, excludedBlocks, appPolicy, 0, "");
    }

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg, String imageDetail,
                        int maxTokens, float temperature, List<ChatMessage> messages,
                        List<ToolSpec> tools, String thinking, int excludedBlocks,
                        String appPolicy, int maskedSpans, String maskedKinds) {
        this(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens, temperature,
                messages, tools, thinking, excludedBlocks, appPolicy, maskedSpans, maskedKinds,
                Collections.emptyList(), EgressStage.RAW);
    }

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg, String imageDetail,
                        int maxTokens, float temperature, List<ChatMessage> messages,
                        List<ToolSpec> tools, String thinking, int excludedBlocks,
                        String appPolicy, int maskedSpans, String maskedKinds,
                        List<String> sources, EgressStage stage) {
        this(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens, temperature,
                messages, tools, thinking, excludedBlocks, appPolicy, maskedSpans,
                maskedKinds, sources, Collections.emptyList(), stage);
    }

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg, String imageDetail,
                        int maxTokens, float temperature, List<ChatMessage> messages,
                        List<ToolSpec> tools, String thinking, int excludedBlocks,
                        String appPolicy, int maskedSpans, String maskedKinds,
                        List<String> sources, List<String> strippedSources, EgressStage stage) {
        this(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens, temperature,
                messages, tools, thinking, excludedBlocks, appPolicy, maskedSpans,
                maskedKinds, sources, strippedSources, stage, "");
    }

    public ModelRequest(String systemPrompt, String userPrompt, byte[] jpeg, String imageDetail,
                        int maxTokens, float temperature, List<ChatMessage> messages,
                        List<ToolSpec> tools, String thinking, int excludedBlocks,
                        String appPolicy, int maskedSpans, String maskedKinds,
                        List<String> sources, List<String> strippedSources, EgressStage stage,
                        String maskStatus) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userPrompt = userPrompt == null ? "" : userPrompt;
        this.jpeg = jpeg;
        this.imageDetail = imageDetail;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.messages = messages == null ? Collections.emptyList() : messages;
        this.tools = tools == null ? Collections.emptyList() : tools;
        this.thinking = thinking;
        this.excludedBlocks = Math.max(0, excludedBlocks);
        this.appPolicy = appPolicy == null || appPolicy.isEmpty()
                ? "REDACT_THEN_CLOUD" : appPolicy;
        this.maskedSpans = Math.max(0, maskedSpans);
        this.maskedKinds = maskedKinds == null ? "" : maskedKinds;
        this.sources = sources == null ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(sources));
        this.strippedSources = strippedSources == null ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(strippedSources));
        this.stage = stage == null ? EgressStage.RAW : stage;
        this.maskStatus = maskStatus == null ? "" : maskStatus;
    }

    /** 设置 provenance；所有 withXxx 必须保留这两个字段。 */
    public ModelRequest withProvenance(List<String> sources, EgressStage stage) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, strippedSources, stage);
    }

    public ModelRequest withProvenance(List<String> sources, List<String> stripped,
                                       EgressStage stage) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, stripped, stage);
    }

    public ModelRequest withStrippedSources(List<String> stripped) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, stripped, stage, maskStatus);
    }

    public ModelRequest withMaskStatus(String status) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, strippedSources, stage, status);
    }

    public static ModelRequest text(String system, String user) {
        return new ModelRequest(system, user, null, 0, 0.2f);
    }

    public static ModelRequest chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        return new ModelRequest("", "", null, null, 0, 0.2f, messages, tools, null);
    }

    public ModelRequest withPrompts(String system, String user) {
        return new ModelRequest(system, user, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, stage);
    }

    public ModelRequest withMaxTokens(int n) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, n,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, stage);
    }

    public ModelRequest withThinking(String mode) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, mode, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, stage);
    }

    public ModelRequest withImageDetail(String detail) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, detail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                maskedSpans, maskedKinds, sources, stage);
    }

    public ModelRequest withAppPolicy(int excluded, String policy) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excluded, policy,
                maskedSpans, maskedKinds, sources, stage);
    }

    public ModelRequest withMasking(int spans, String kinds) {
        return new ModelRequest(systemPrompt, userPrompt, jpeg, imageDetail, maxTokens,
                temperature, messages, tools, thinking, excludedBlocks, appPolicy,
                spans, kinds, sources, stage);
    }

    public boolean hasMessages() { return messages != null && !messages.isEmpty(); }

    public boolean hasTools() { return tools != null && !tools.isEmpty(); }
}
