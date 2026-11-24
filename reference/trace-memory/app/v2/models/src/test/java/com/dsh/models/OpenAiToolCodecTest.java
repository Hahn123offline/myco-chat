package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** OpenAI tools 协议编解码：tool_calls / tools / 转录消息。 */
public class OpenAiToolCodecTest {

    @Test public void 转录取包含assistant_tool_calls与tool结果() {
        ModelRequest req = ModelRequest.chat(Arrays.asList(
                ChatMessage.system("sys"),
                ChatMessage.user("hi"),
                ChatMessage.assistant("", Collections.singletonList(
                        new ToolCall("c1", "read_mosaic", "{\"block_ids\":[1]}"))),
                ChatMessage.tool("c1", "[status=OK] image")
        ), Collections.singletonList(new ToolSpec("read_mosaic", "拼图", "{\"type\":\"object\"}")));
        String body = OpenAiChatCodec.buildRequest("deepseek-chat", req);
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"tool_call_id\":\"c1\""));
        assertTrue(body.contains("\"tools\""));
        assertTrue(body.contains("\"tool_choice\":\"auto\""));
    }

    @Test public void 解析tool_calls且content可为空() {
        String json = "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"\","
                + "\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":{"
                + "\"name\":\"read_frame\",\"arguments\":\"{\\\"block_id\\\":7}\"}}]}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,"
                + "\"prompt_cache_hit_tokens\":8}}";
        ModelResponse r = OpenAiChatCodec.parseResponse(json, "m");
        assertTrue(r.error, r.isOk());
        assertEquals(1, r.toolCalls.size());
        assertEquals("c1", r.toolCalls.get(0).id);
        assertEquals("read_frame", r.toolCalls.get(0).name);
        assertTrue(r.toolCalls.get(0).argumentsJson.contains("block_id"));
        assertEquals(8, r.promptCacheHitTokens);
    }

    @Test public void 用户图片消息序列化为content_parts() {
        ModelRequest req = ModelRequest.chat(Collections.singletonList(
                ChatMessage.userImage("看这张", new byte[]{1, 2})), Collections.emptyList());
        String body = OpenAiChatCodec.buildRequest("m", req);
        assertTrue(body.contains("image_url"));
        assertTrue(body.contains("data:image/jpeg;base64,"));
        assertFalse(body.contains("\"tools\""));
    }
}
