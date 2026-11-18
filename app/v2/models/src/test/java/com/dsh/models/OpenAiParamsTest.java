package com.dsh.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/** 官方参数：thinking 开关、image detail、reasoning_content。 */
public class OpenAiParamsTest {

    @Test public void thinking关闭写入请求() {
        String body = OpenAiChatCodec.buildRequest("deepseek-flash",
                ModelRequest.text("s", "u").withThinking("disabled"));
        assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"));
    }

    @Test public void 图片low_detail写入请求() {
        String body = OpenAiChatCodec.buildRequest("deepseek-flash",
                new ModelRequest("", "看图", new byte[]{1}, "low", 0, 0.2f,
                        Collections.emptyList(), Collections.emptyList(), null));
        assertTrue(body.contains("\"detail\":\"low\""));
    }

    @Test public void 解析reasoning_content() {
        ModelResponse r = OpenAiChatCodec.parseResponse(
                "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"思考...\","
                        + "\"tool_calls\":[{\"id\":\"c\",\"type\":\"function\",\"function\":"
                        + "{\"name\":\"read_frame\",\"arguments\":\"{}\"}}]},"
                        + "\"finish_reason\":\"tool_calls\"}]}", "m");
        assertTrue(r.isOk());
        assertEquals("思考...", r.reasoningContent);
        assertEquals(1, r.toolCalls.size());
    }

    @Test public void 空内容length错误带诊断() {
        ModelResponse r = OpenAiChatCodec.parseResponse(
                "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"abc\"},"
                        + "\"finish_reason\":\"length\"}]}", "m");
        assertTrue(r.error.contains("finish_reason=length"));
        assertTrue(r.error.contains("reasoning_chars=3"));
        assertTrue(r.error.contains("提高 max_tokens"));
    }
}
