package com.dsh.host.api.llm;

/**
 * 一次模型调用的输入。
 *
 * 这里**只有"要做什么"，没有"发给谁"**：路由、provider、模型、超时都属于实现，
 * 调用方不关心也不用知道。不可变对象，构造用 {@link #builder(String)}。
 */
public final class LlmRequest {

    public static final int DEFAULT_MAX_TOKENS = 256;
    public static final float DEFAULT_TEMPERATURE = 0.2f;

    /** 任务标识，如 "title" / "redact" / "summarize"；模型路由器按它选 provider */
    public final String taskId;
    /** 系统提示词，可为空（空了就不发 system message） */
    public final String systemPrompt;
    /** 用户提示词 */
    public final String userPrompt;
    /**
     * 可选 JPEG 截图。多模态 provider 会作为 image_url 的 base64 data URI 发送；
     * 纯文本 provider 收到带图请求时按自己的协议处理（可能失败 → 交给降级链）。
     */
    public final byte[] jpeg;
    public final int maxTokens;
    public final float temperature;

    private LlmRequest(Builder b) {
        this.taskId = b.taskId;
        this.systemPrompt = b.systemPrompt;
        this.userPrompt = b.userPrompt;
        this.jpeg = b.jpeg;
        this.maxTokens = b.maxTokens;
        this.temperature = b.temperature;
    }

    /** 最常用形态：只要文本 */
    public static LlmRequest of(String taskId, String systemPrompt, String userPrompt) {
        return builder(taskId).systemPrompt(systemPrompt).userPrompt(userPrompt).build();
    }

    public static Builder builder(String taskId) {
        return new Builder(taskId);
    }

    public static final class Builder {
        private final String taskId;
        private String systemPrompt = "";
        private String userPrompt = "";
        private byte[] jpeg;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private float temperature = DEFAULT_TEMPERATURE;

        private Builder(String taskId) {
            if (taskId == null || taskId.trim().isEmpty()) {
                throw new IllegalArgumentException("taskId 不能为空");
            }
            this.taskId = taskId.trim();
        }

        public Builder systemPrompt(String v) { this.systemPrompt = v == null ? "" : v; return this; }
        public Builder userPrompt(String v) { this.userPrompt = v == null ? "" : v; return this; }
        public Builder jpeg(byte[] v) { this.jpeg = v; return this; }
        public Builder maxTokens(int v) { this.maxTokens = v; return this; }
        public Builder temperature(float v) { this.temperature = v; return this; }

        public LlmRequest build() { return new LlmRequest(this); }
    }

    @Override
    public String toString() {
        return "LlmRequest{task=" + taskId
                + ", sysLen=" + systemPrompt.length()
                + ", userLen=" + userPrompt.length()
                + ", jpeg=" + (jpeg == null ? 0 : jpeg.length) + "B"
                + ", maxTokens=" + maxTokens
                + ", temperature=" + temperature + "}";
    }
}
