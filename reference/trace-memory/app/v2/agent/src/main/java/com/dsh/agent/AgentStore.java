package com.dsh.agent;

import java.util.List;

/** 会话事件存储：append-only；真实实现可接 SQLite，测试用内存。 */
public interface AgentStore {
    long append(String sessionId, String type, String payload);
    List<AgentEvent> events(String sessionId);
}
