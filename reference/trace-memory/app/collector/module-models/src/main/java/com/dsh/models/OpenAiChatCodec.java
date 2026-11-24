package com.dsh.models;

import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 /v1/chat/completions 协议编解码。纯函数、可单测。
 *
 * 两种 user message 形态：
 *  - 无图 → content 是字符串（云端纯文本模型也吃这一套）；
 *  - 有图 → content 是 content part 数组（text + image_url 的 base64 data URI），
 *    这是 OpenAI 视觉协议，llama-server / MiniCPM-V 之类本地多模态端点也支持。
 */
public final class OpenAiChatCodec {

    private static final int ERROR_SNIPPET_MAX = 240;

    private OpenAiChatCodec() { }

    public static String buildRequest(String model, LlmRequest req) {
        StringBuilder b = new StringBuilder(320);
        b.append('{');
        b.append("\"model\":").append(Json.quote(model)).append(',');
        b.append("\"messages\":[");

        String sys = req.systemPrompt == null ? "" : req.systemPrompt;
        String user = req.userPrompt == null ? "" : req.userPrompt;
        boolean hasImage = req.jpeg != null && req.jpeg.length > 0;

        boolean first = true;
        if (!sys.isEmpty()) {
            b.append("{\"role\":\"system\",\"content\":").append(Json.quote(sys)).append('}');
            first = false;
        }
        if (!first) b.append(',');
        b.append("{\"role\":\"user\",\"content\":");
        if (hasImage) {
            b.append('[');
            b.append("{\"type\":\"text\",\"text\":").append(Json.quote(user)).append('}');
            b.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":")
                    .append(Json.quote(jpegDataUri(req.jpeg))).append("}}");
            b.append(']');
        } else {
            b.append(Json.quote(user));
        }
        b.append('}');
        b.append(']');

        if (req.maxTokens > 0) {
            b.append(",\"max_tokens\":").append(req.maxTokens);
        }
        b.append(",\"temperature\":").append(Float.toString(req.temperature));
        b.append(",\"stream\":false");
        b.append('}');
        return b.toString();
    }

    public static String jpegDataUri(byte[] jpeg) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    public static LlmResponse parseResponse(String body, String fallbackModel) {
        try {
            Object root = Json.parse(body);
            Map<String, Object> m = Json.asObject(root);
            if (m == null) return LlmResponse.error("响应不是 JSON 对象", fallbackModel);

            Object err = m.get("error");
            if (err != null) return LlmResponse.error("模型返回错误: " + errorText(err), fallbackModel);

            List<Object> choices = Json.asArray(m.get("choices"));
            if (choices == null || choices.isEmpty()) {
                return LlmResponse.error("响应缺少 choices", fallbackModel);
            }
            Object c0 = choices.get(0);
            Object content = Json.get(Json.get(c0, "message"), "content");
            String text = textOf(content);
            if (text.isEmpty()) return LlmResponse.error("模型返回空内容", fallbackModel);

            String model = Json.string(m.get("model"));
            if (model == null || model.isEmpty()) model = fallbackModel;
            return LlmResponse.ok(text, model);
        } catch (Throwable t) {
            return LlmResponse.error("响应解析失败: " + t.getMessage(), fallbackModel);
        }
    }

    /** content 可能是字符串，也可能是 content part 数组（[{type:text,text:...}]） */
    static String textOf(Object content) {
        if (content instanceof String) return (String) content;
        List<Object> parts = Json.asArray(content);
        if (parts == null) return "";
        StringBuilder b = new StringBuilder();
        for (Object p : parts) {
            String t = Json.string(Json.get(p, "text"));
            if (t == null) {
                // 少数服务端把 text 放在 content 字段里
                t = Json.string(Json.get(p, "content"));
            }
            if (t != null) b.append(t);
        }
        return b.toString();
    }

    static String errorText(Object err) {
        Map<String, Object> m = Json.asObject(err);
        if (m != null) {
            String msg = Json.string(m.get("message"));
            if (msg != null && !msg.isEmpty()) return msg;
        }
        String s = Json.string(err);
        return s == null ? String.valueOf(err) : s;
    }

    /** 非 2xx 响应体截一段进 error，避免把整页 HTML 塞进日志/状态页 */
    public static String errorSnippet(String body) {
        if (body == null) return "";
        String s = body.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= ERROR_SNIPPET_MAX ? s : s.substring(0, ERROR_SNIPPET_MAX) + "…";
    }

    static String utf8(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
}
