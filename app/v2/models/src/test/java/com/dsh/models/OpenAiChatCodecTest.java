package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OpenAiChatCodecTest {

    @Test public void 纯文本请求是通用OpenAI字段() {
        String body = OpenAiChatCodec.buildRequest("deepseek-chat",
                ModelRequest.text("sys", "hello"));
        assertTrue(body.contains("\"model\":\"deepseek-chat\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"stream\":false"));
        assertFalse("不能写死 DeepSeek 特有字段", body.contains("cache"));
    }

    @Test public void 图片请求使用content_parts() {
        String body = OpenAiChatCodec.buildRequest("vision-model",
                new ModelRequest("", "看图", new byte[]{1, 2, 3}, 100, 0.2f));
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("data:image/jpeg;base64,"));
        assertTrue(body.contains("\"max_tokens\":100"));
    }

    @Test public void 解析文本与usage含缓存命中() {
        String json = "{\"model\":\"m1\",\"choices\":[{\"message\":{\"content\":\"answer\"}}],"
                + "\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30,"
                + "\"prompt_cache_hit_tokens\":80}}";
        ModelResponse r = OpenAiChatCodec.parseResponse(json, "fallback");
        assertTrue(r.error, r.isOk());
        assertEquals("answer", r.text);
        assertEquals("m1", r.model);
        assertEquals(120, r.promptTokens);
        assertEquals(30, r.completionTokens);
        assertEquals(80, r.promptCacheHitTokens);
    }

    @Test public void usage缺失时缓存字段为0不猜() {
        ModelResponse r = OpenAiChatCodec.parseResponse(
                "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}", "m");
        assertTrue(r.isOk());
        assertEquals(0, r.promptCacheHitTokens);
    }

    @Test public void 错误响应不丢错误信息() {
        ModelResponse r = OpenAiChatCodec.parseResponse(
                "{\"error\":{\"message\":\"invalid key\"}}", "m");
        assertFalse(r.isOk());
        assertTrue(r.error.contains("invalid key"));
    }

    @Test public void 空内容当失败() {
        ModelResponse r = OpenAiChatCodec.parseResponse(
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}", "m");
        assertFalse(r.isOk());
    }
}
