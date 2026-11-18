package com.dsh.v2;

import com.dsh.agent.LlmCaller;
import com.dsh.models.ChatMessage;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无真实 API key 时的本地 mock：从初始 user 文本解析块 id，
 * 先 read_mosaic 一次，再 submit_description。用于验证 Android 数据/拼图/落库链路。
 */
public final class MockLlmCaller implements LlmCaller {

    private static final Pattern ID = Pattern.compile("#(\\d+)\\s");
    private int turn;

    @Override public synchronized ModelResponse call(ModelRequest request) {
        turn++;
        if (turn == 1) {
            List<Long> ids = new ArrayList<>();
            for (ChatMessage m : request.messages) {
                if (!"user".equals(m.role)) continue;
                Matcher mt = ID.matcher(m.content);
                while (mt.find() && ids.size() < 16) {
                    try { ids.add(Long.parseLong(mt.group(1))); } catch (Throwable ignored) { }
                }
                if (!ids.isEmpty()) break;
            }
            StringBuilder args = new StringBuilder("{\"block_ids\":[");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) args.append(',');
                args.append(ids.get(i));
            }
            args.append("]}");
            return ModelResponse.ok("", "mock", "mock")
                    .withToolCalls(java.util.Collections.singletonList(
                            new ToolCall("mock-mosaic", "read_mosaic", args.toString())));
        }
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            body.append("用户打开应用后查看了页面内容，并使用搜索或列表操作继续浏览，最后回到原页面查看结果。");
        }
        String args = "{\"title\":\"调试链描述\",\"body\":\"" + body
                + "\",\"key_block_ids\":[" + firstId(request) + "]}";
        return ModelResponse.ok("", "mock", "mock")
                .withToolCalls(java.util.Collections.singletonList(
                        new ToolCall("mock-submit", "submit_description", args)));
    }

    private static String firstId(ModelRequest request) {
        for (ChatMessage m : request.messages) {
            if (!"user".equals(m.role)) continue;
            Matcher mt = ID.matcher(m.content);
            if (mt.find()) return mt.group(1);
        }
        return "1";
    }
}
