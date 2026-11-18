package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/** 中断恢复：文件事件日志重新加载后，转录自动补未执行并继续。 */
public class FileAgentStoreResumeTest {

    @Test public void 有call无result从文件恢复后补未执行并继续() throws Exception {
        File dir = Files.createTempDirectory("agent-store").toFile();
        FileAgentStore store = new FileAgentStore(dir);
        store.append("s1", "user", "hello");
        store.append("s1", "assistant",
                "{\"content\":\"\",\"tool_calls\":[{\"id\":\"c1\",\"name\":\"read_frame\","
                        + "\"arguments\":\"{}\"}]}");
        // 模拟进程被杀：重新 new 一个 store（同一目录）
        FileAgentStore reopened = new FileAgentStore(dir);
        assertEquals(2, reopened.events("s1").size());

        ToolRegistry tools = new ToolRegistry().add(new AgentTool() {
            @Override public String name() { return "finish"; }
            @Override public String description() { return "finish"; }
            @Override public String parametersJson() { return "{\"type\":\"object\"}"; }
            @Override public ToolResult execute(java.util.Map<String, Object> args, ToolContext ctx) {
                return ToolResult.ok("finished");
            }
        });
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText("resumed"));
        AgentEngine.Result r = new AgentEngine(reopened).run("s1", "sys", "hello", tools,
                caller, new AgentEngine.Config());
        assertTrue(r.error, r.done);
        assertEquals("resumed", r.finalText);
        boolean foundCall = false;
        boolean foundNotExecuted = false;
        for (com.dsh.models.ChatMessage m : caller.requests.get(0).messages) {
            if (m.hasToolCalls() && !m.toolCalls.isEmpty()
                    && "c1".equals(m.toolCalls.get(0).id)) foundCall = true;
            if ("tool".equals(m.role) && "c1".equals(m.toolCallId)
                    && m.content.contains("NOT_EXECUTED")) foundNotExecuted = true;
        }
        assertTrue("必须从文件恢复出 call", foundCall);
        assertTrue("缺失 result 必须补 NOT_EXECUTED", foundNotExecuted);
        // 文件里也补上了新的 assistant/tool 事件（用户事件不重复）
        long users = reopened.events("s1").stream().filter(e -> "user".equals(e.type)).count();
        assertEquals(1, users);
        assertFalse(reopened.events("s1").isEmpty());
    }
}
