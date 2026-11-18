package com.dsh.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内存事件日志（JVM/测试）。seq 按 session 递增。 */
public final class MemoryAgentStore implements AgentStore {

    private final Map<String, List<AgentEvent>> sessions = new LinkedHashMap<>();

    @Override public synchronized long append(String sessionId, String type, String payload) {
        List<AgentEvent> list = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        long seq = list.size() + 1;
        list.add(new AgentEvent(seq, type, payload, System.currentTimeMillis()));
        return seq;
    }

    @Override public synchronized List<AgentEvent> events(String sessionId) {
        List<AgentEvent> list = sessions.get(sessionId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public synchronized int size(String sessionId) {
        List<AgentEvent> list = sessions.get(sessionId);
        return list == null ? 0 : list.size();
    }
}
