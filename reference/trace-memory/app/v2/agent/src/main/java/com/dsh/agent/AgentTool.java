package com.dsh.agent;

import java.util.Map;

/** 工具契约：description 是模型唯一判断依据；参数 schema 由 parametersJson + requiredArgs 共同表达。 */
public interface AgentTool {

    String name();

    String description();

    /** JSON Schema 对象字符串（发给模型） */
    String parametersJson();

    default String[] requiredArgs() { return new String[0]; }

    /** 执行；错误通过 ToolResult 返回，不抛异常给引擎（引擎也会兜住） */
    ToolResult execute(Map<String, Object> args, ToolContext ctx);
}
