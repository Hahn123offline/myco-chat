package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class OpenAiChatCodecTest {

    private static LlmRequest textReq() {
        return LlmRequest.builder("title").systemPrompt("你是标题抽取器").userPrompt("看这页").build();
    }

    @Test public void 纯文本请求的user_content是字符串_并带system消息() {
        String body = OpenAiChatCodec.buildRequest("minicpm-v", textReq());
        Map<String, Object> root = Json.asObject(Json.parse(body));

        assertEquals("minicpm-v", root.get("model"));
        assertEquals(Boolean.FALSE, root.get("stream"));
        List<Object> messages = Json.asArray(root.get("messages"));
        assertEquals(2, messages.size());
        assertEquals("system", Json.get(messages.get(0), "role"));
        assertEquals("你是标题抽取器", Json.get(messages.get(0), "content"));
        assertEquals("user", Json.get(messages.get(1), "role"));
        assertEquals("看这页", Json.get(messages.get(1), "content"));
    }

    @Test public void 带图请求走content_parts_图片是base64_data_uri() {
        byte[] jpeg = new byte[]{1, 2, 3};
        LlmRequest req = LlmRequest.builder("title")
                .userPrompt("这页叫什么").jpeg(jpeg).build();
        String body = OpenAiChatCodec.buildRequest("minicpm-v", req);
        Map<String, Object> root = Json.asObject(Json.parse(body));
        List<Object> messages = Json.asArray(root.get("messages"));
        assertEquals(1, messages.size());
        List<Object> parts = Json.asArray(Json.get(messages.get(0), "content"));
        assertEquals(2, parts.size());
        assertEquals("text", Json.get(parts.get(0), "type"));
        assertEquals("这页叫什么", Json.get(parts.get(0), "text"));
        assertEquals("image_url", Json.get(parts.get(1), "type"));
        assertEquals("data:image/jpeg;base64,AQID",
                Json.get(Json.get(parts.get(1), "image_url"), "url"));
    }

    @Test public void 无system时不发system消息() {
        String body = OpenAiChatCodec.buildRequest("m", LlmRequest.of("redact", null, "x"));
        List<Object> messages = Json.asArray(Json.get(Json.parse(body), "messages"));
        assertEquals(1, messages.size());
        assertEquals("user", Json.get(messages.get(0), "role"));
    }

    @Test public void maxTokens与temperature被写进请求() {
        LlmRequest req = LlmRequest.builder("t").userPrompt("x").maxTokens(64).temperature(0.7f).build();
        Map<String, Object> root = Json.asObject(Json.parse(OpenAiChatCodec.buildRequest("m", req)));
        assertEquals(64L, ((Number) root.get("max_tokens")).longValue());
        assertEquals(0.7, (Double) root.get("temperature"), 1e-6);
    }

    @Test public void 解析正常响应() {
        String body = TestSupport.okBody("minicpm-v-4.7-1b", "深港驾校小米");
        LlmResponse r = OpenAiChatCodec.parseResponse(body, "fallback");
        assertTrue(r.isOk());
        assertEquals("深港驾校小米", r.text);
        assertEquals("minicpm-v-4.7-1b", r.model);
    }

    @Test public void 响应没有model字段时用请求的模型名() {
        String body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        assertEquals("m1", OpenAiChatCodec.parseResponse(body, "m1").model);
    }

    @Test public void 解析错误响应不抛异常() {
        LlmResponse r = OpenAiChatCodec.parseResponse(
                "{\"error\":{\"message\":\"invalid api key\",\"type\":\"auth\"}}", "m");
        assertFalse(r.isOk());
        assertTrue(r.error.contains("invalid api key"));
    }

    @Test public void 空choices返回error() {
        assertFalse(OpenAiChatCodec.parseResponse("{\"choices\":[]}", "m").isOk());
        assertFalse(OpenAiChatCodec.parseResponse("not json", "m").isOk());
    }

    @Test public void content数组形态也能取到文本() {
        String body = "{\"choices\":[{\"message\":{\"content\":["
                + "{\"type\":\"text\",\"text\":\"ab\"},{\"type\":\"text\",\"text\":\"cd\"}]}}]}";
        assertEquals("abcd", OpenAiChatCodec.parseResponse(body, "m").text);
    }

    @Test public void 错误摘要不会把整页响应塞进状态页() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 1000; i++) big.append('x');
        String s = OpenAiChatCodec.errorSnippet(big.toString());
        assertTrue(s.length() < 300);
        assertTrue(s.endsWith("…"));
        assertNull(Json.string(Json.parse("null")));
    }
}
