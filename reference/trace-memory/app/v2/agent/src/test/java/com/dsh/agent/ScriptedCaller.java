package com.dsh.agent;

import com.dsh.models.ChatMessage;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.ToolCall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 按脚本返回响应的假 LLM；记录每次请求。 */
public final class ScriptedCaller implements LlmCaller {

    private final List<ModelResponse> responses;
    private final AtomicInteger index = new AtomicInteger();
    public final List<ModelRequest> requests = new ArrayList<>();

    public ScriptedCaller(ModelResponse... responses) {
        this.responses = new ArrayList<>(Arrays.asList(responses));
    }

    public static ModelResponse toolCalls(ToolCall... calls) {
        return ModelResponse.ok("", "fake", "fake").withToolCalls(Arrays.asList(calls));
    }

    public static ModelResponse finalText(String text) {
        return ModelResponse.ok(text, "fake", "fake");
    }

    public static ToolCall call(String id, String name, String args) {
        return new ToolCall(id, name, args);
    }

    public int calls() { return index.get(); }

    @Override public synchronized ModelResponse call(ModelRequest request) {
        requests.add(request);
        int i = index.getAndIncrement();
        if (i >= responses.size()) return ModelResponse.error("script exhausted", "fake");
        return responses.get(i);
    }

    public static int imageCount(ModelRequest r) {
        int n = 0;
        for (ChatMessage m : r.messages) if (m.hasImage()) n++;
        return n;
    }
}
