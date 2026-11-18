package com.dsh.agent;

/** 工具执行上下文：看图次数预算 + 会话变量。 */
public final class ToolContext {

    private final ImageBudget imageBudget;
    private final java.util.Map<String, Object> vars;

    public ToolContext(ImageBudget imageBudget, java.util.Map<String, Object> vars) {
        this.imageBudget = imageBudget;
        this.vars = vars == null ? new java.util.HashMap<>() : vars;
    }

    public ImageBudget imageBudget() { return imageBudget; }

    public java.util.Map<String, Object> vars() { return vars; }
}
