package com.dsh.agent;

/** 宿主适配：Android/PC 各自实现，ChatLoop 只依赖这里。 */
public interface ChatHost {
    String sessionId();
    String systemPrompt();
    AgentStore store();
    ToolRegistry tools();
    LlmCaller caller();
    AgentEngine.Config config();
    default void beforeRun() { }
    default void afterRun(AgentEngine.Result r) { }
}
