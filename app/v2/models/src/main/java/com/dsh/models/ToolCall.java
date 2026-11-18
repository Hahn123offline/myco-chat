package com.dsh.models;

/** 模型返回的一次工具调用（OpenAI tools 协议）。 */
public final class ToolCall {

    public final String id;
    public final String name;
    /** 原始 JSON 参数串（可能不是合法 JSON；校验由 agent 工具层负责） */
    public final String argumentsJson;

    public ToolCall(String id, String name, String argumentsJson) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }

    @Override public String toString() {
        return "ToolCall{" + id + ", " + name + ", " + argumentsJson + "}";
    }
}
