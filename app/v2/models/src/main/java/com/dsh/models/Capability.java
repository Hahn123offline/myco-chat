package com.dsh.models;

/** 模型能力声明：调用方只说「我要什么能力」，不指定具体模型。 */
public enum Capability {
    VISION,
    TOOL_CALL,
    JSON,
    PROMPT_CACHE,
    LONG_CTX
}
