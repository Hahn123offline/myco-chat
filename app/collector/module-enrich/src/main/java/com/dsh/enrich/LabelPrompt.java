package com.dsh.enrich;

import com.dsh.host.api.Config;
import com.dsh.host.api.llm.LlmRequest;

/**
 * 打标 prompt 的组装。**默认文案不硬编码在调用点**：可由配置 `prompt.label` 覆盖。
 *
 * 默认那条是真机数据上做 prompt 消融后效果最好的（见 task-9）：要求简短、禁止抄
 * 应用名/标签栏/按钮文字；不用 JSON schema（小模型会把字段说明当答案回吐）。
 * `<maskedText>` 是正文占位符；正文作为 user prompt 发送，system prompt 只留指令。
 */
public final class LabelPrompt {

    public static final String KEY_PROMPT = "prompt.label";

    public static final String DEFAULT =
            "用不超过 18 字说明这一屏在讲什么具体内容。\n"
                    + "严禁输出应用名、底部/顶部标签名（如「发现」「推荐」「首页」）、按钮文字、"
                    + "评论数、粉丝数、时间日期。\n"
                    + "只输出那一句内容本身，不要任何前缀或解释。\n"
                    + "---\n"
                    + "<maskedText>";

    private LabelPrompt() { }

    public static String template(Config c) {
        String t = c == null ? null : c.getString(KEY_PROMPT, DEFAULT);
        return t == null || t.trim().isEmpty() ? DEFAULT : t;
    }

    /** 组 LlmRequest：system=指令（去掉正文占位符），user=maskedText（绝不发原文/截图） */
    public static LlmRequest request(String template, String maskedText, int maxTokens) {
        String t = template == null || template.trim().isEmpty() ? DEFAULT : template;
        String instructions = t.replace("<maskedText>", "").trim();
        return LlmRequest.builder(Enricher.TASK_ID)
                .systemPrompt(instructions)
                .userPrompt(maskedText)
                .maxTokens(maxTokens)
                .temperature(0f)
                .build();
    }
}
