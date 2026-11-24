package com.dsh.models;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /v1/chat/completions 编解码。纯函数、可单测。
 *
 * 支持两种形态：
 *  - 旧的 system/user(+单图) 快捷请求；
 *  - agent 转录：messages(system/user/assistant+tool_calls/tool) + tools。
 * 不写死任何厂商特有字段；usage 读取 prompt_cache_hit_tokens（没有就是 0）。
 */
public final class OpenAiChatCodec {

    private static final int ERROR_SNIPPET_MAX = 240;

    private OpenAiChatCodec() { }

    public static String buildRequest(String model, ModelRequest req) {
        StringBuilder b = new StringBuilder(512);
        b.append('{');
        b.append("\"model\":").append(Json.quote(model)).append(',');
        b.append("\"messages\":");
        if (req.hasMessages()) {
            b.append(messagesJson(req.messages));
        } else {
            b.append(legacyMessagesJson(req));
        }
        if (req.hasTools()) b.append(",\"tools\":").append(toolsJson(req.tools));
        if (req.hasTools()) b.append(",\"tool_choice\":\"auto\"");
        if (req.thinking != null && !req.thinking.isEmpty()) {
            b.append(",\"thinking\":{\"type\":").append(Json.quote(req.thinking)).append('}');
        }
        if (req.maxTokens > 0) b.append(",\"max_tokens\":").append(req.maxTokens);
        b.append(",\"temperature\":").append(Float.toString(req.temperature));
        b.append(",\"stream\":false");
        b.append('}');
        return b.toString();
    }

    private static String legacyMessagesJson(ModelRequest req) {
        StringBuilder b = new StringBuilder();
        b.append('[');
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
                    .append(Json.quote(jpegDataUri(req.jpeg)));
            if (req.imageDetail != null && !req.imageDetail.isEmpty()) {
                b.append(",\"detail\":").append(Json.quote(req.imageDetail));
            }
            b.append("}}");
            b.append(']');
        } else {
            b.append(Json.quote(user));
        }
        b.append('}');
        b.append(']');
        return b.toString();
    }

    private static String messagesJson(List<ChatMessage> messages) {
        StringBuilder b = new StringBuilder();
        b.append('[');
        boolean first = true;
        for (ChatMessage m : messages) {
            if (m == null) continue;
            if (!first) b.append(',');
            first = false;
            b.append('{').append("\"role\":").append(Json.quote(m.role));
            if ("tool".equals(m.role)) {
                b.append(",\"tool_call_id\":").append(Json.quote(m.toolCallId));
                b.append(",\"content\":").append(Json.quote(m.content));
            } else {
                b.append(",\"content\":");
                if (m.hasImage()) {
                    b.append('[');
                    b.append("{\"type\":\"text\",\"text\":").append(Json.quote(m.content)).append('}');
                    b.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":")
                            .append(Json.quote(jpegDataUri(m.jpeg)));
                    if (m.imageDetail != null && !m.imageDetail.isEmpty()) {
                        b.append(",\"detail\":").append(Json.quote(m.imageDetail));
                    }
                    b.append("}}");
                    b.append(']');
                } else {
                    b.append(Json.quote(m.content));
                }
                if (m.reasoningContent != null && !m.reasoningContent.isEmpty()) {
                    b.append(",\"reasoning_content\":").append(Json.quote(m.reasoningContent));
                }
                if (m.hasToolCalls()) {
                    b.append(",\"tool_calls\":[");
                    boolean cf = true;
                    for (ToolCall c : m.toolCalls) {
                        if (c == null) continue;
                        if (!cf) b.append(',');
                        cf = false;
                        b.append("{\"id\":").append(Json.quote(c.id))
                                .append(",\"type\":\"function\",\"function\":{")
                                .append("\"name\":").append(Json.quote(c.name))
                                .append(",\"arguments\":").append(Json.quote(c.argumentsJson))
                                .append("}}");
                    }
                    b.append(']');
                }
            }
            b.append('}');
        }
        b.append(']');
        return b.toString();
    }

    private static String toolsJson(List<ToolSpec> tools) {
        StringBuilder b = new StringBuilder();
        b.append('[');
        boolean first = true;
        for (ToolSpec t : tools) {
            if (t == null) continue;
            if (!first) b.append(',');
            first = false;
            b.append("{\"type\":\"function\",\"function\":{")
                    .append("\"name\":").append(Json.quote(t.name))
                    .append(",\"description\":").append(Json.quote(t.description))
                    .append(",\"parameters\":").append(t.parametersJson)
                    .append("}}");
        }
        b.append(']');
        return b.toString();
    }

    public static String jpegDataUri(byte[] jpeg) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    /** 解析响应并填入 usage 与 tool_calls；有 tool_calls 时 content 可为空。 */
    public static ModelResponse parseResponse(String body, String fallbackModel) {
        try {
            Object root = Json.parse(body);
            Map<String, Object> m = Json.asObject(root);
            if (m == null) return ModelResponse.error("响应不是 JSON 对象", fallbackModel);
            Object err = m.get("error");
            if (err != null) return ModelResponse.error("模型返回错误: " + errorText(err), fallbackModel);

            List<Object> choices = Json.asArray(m.get("choices"));
            if (choices == null || choices.isEmpty()) {
                return ModelResponse.error("响应缺少 choices", fallbackModel);
            }
            Object c0 = choices.get(0);
            Map<String, Object> message = Json.asObject(Json.get(c0, "message"));
            Object content = message == null ? null : message.get("content");
            String text = textOf(content);

            List<ToolCall> calls = new java.util.ArrayList<>();
            List<Object> rawCalls = message == null ? null : Json.asArray(message.get("tool_calls"));
            if (rawCalls != null) {
                for (Object raw : rawCalls) {
                    Map<String, Object> cm = Json.asObject(raw);
                    if (cm == null) continue;
                    String id = Json.string(cm.get("id"));
                    Object fn = cm.get("function");
                    String name = Json.string(Json.get(fn, "name"));
                    String args = Json.string(Json.get(fn, "arguments"));
                    if (name == null || name.isEmpty()) continue;
                    calls.add(new ToolCall(id == null ? "" : id, name,
                            args == null ? "{}" : args));
                }
            }
            if (text.isEmpty() && calls.isEmpty()) {
                String finish = Json.string(Json.get(c0, "finish_reason"));
                String reasoning = message == null ? null : Json.string(message.get("reasoning_content"));
                return ModelResponse.error("模型返回空内容且无 tool_calls"
                        + (finish == null ? "" : " finish_reason=" + finish)
                        + (reasoning == null || reasoning.isEmpty()
                        ? "" : " reasoning_chars=" + reasoning.length())
                        + (("length".equals(finish))
                        ? "（输出预算被思考过程吃光，需提高 max_tokens）" : ""),
                        fallbackModel);
            }

            String model = Json.string(m.get("model"));
            if (model == null || model.isEmpty()) model = fallbackModel;
            ModelResponse r = ModelResponse.ok(text, model, fallbackModel)
                    .withToolCalls(calls)
                    .withReasoning(Json.string(message == null ? null
                            : message.get("reasoning_content")));
            Map<String, Object> usage = Json.asObject(m.get("usage"));
            if (usage != null) {
                int prompt = intOf(usage.get("prompt_tokens"));
                int completion = intOf(usage.get("completion_tokens"));
                int cacheHit = intOf(usage.get("prompt_cache_hit_tokens"));
                if (cacheHit == 0) cacheHit = intOf(usage.get("cached_tokens"));
                r = r.withUsage(prompt, completion, cacheHit);
            }
            return r;
        } catch (Throwable t) {
            return ModelResponse.error("响应解析失败: " + t.getMessage(), fallbackModel);
        }
    }

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (Throwable ignored) { }
        }
        return 0;
    }

    static String textOf(Object content) {
        if (content instanceof String) return (String) content;
        List<Object> parts = Json.asArray(content);
        if (parts == null) return "";
        StringBuilder b = new StringBuilder();
        for (Object p : parts) {
            String t = Json.string(Json.get(p, "text"));
            if (t == null) t = Json.string(Json.get(p, "content"));
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

    public static String errorSnippet(String body) {
        if (body == null) return "";
        String s = body.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= ERROR_SNIPPET_MAX ? s : s.substring(0, ERROR_SNIPPET_MAX) + "…";
    }

    static String utf8(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
}
