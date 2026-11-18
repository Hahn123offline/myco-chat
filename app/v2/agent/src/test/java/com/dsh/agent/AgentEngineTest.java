package com.dsh.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dsh.models.Json;
import com.dsh.models.ModelResponse;
import com.dsh.models.ToolCall;

import org.junit.Test;

import java.util.Map;

/** 引擎四铁律：单循环、批次完整性、错误即结果、轮数上限。 */
public class AgentEngineTest {

    private static AgentTool echo(String name) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "echo"; }
            @Override public String parametersJson() { return "{\"type\":\"object\"}"; }
            @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                return ToolResult.ok("ok:" + args);
            }
        };
    }

    private static AgentEngine.Config cfg(int unused) {
        AgentEngine.Config c = new AgentEngine.Config();
        c.maxImages = 6;
        return c;
    }

    @Test public void 单循环_连续调三次工具后正常结束() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(echo("a")).add(echo("b"));
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(ScriptedCaller.call("c1", "a", "{}")),
                ScriptedCaller.toolCalls(ScriptedCaller.call("c2", "b", "{}")),
                ScriptedCaller.toolCalls(ScriptedCaller.call("c3", "a", "{}")),
                ScriptedCaller.finalText("done"));
        AgentEngine.Result r = new AgentEngine(store).run("s1", "sys", "hi", tools, caller,
                cfg(12));
        assertTrue(r.error, r.done);
        assertEquals("done", r.finalText);
        assertEquals(4, r.turns);
        assertEquals(4, store.events("s1").stream().filter(e -> "assistant".equals(e.type)).count());
    }

    @Test public void 批次完整性_两个call都拿到结果才下一轮() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(echo("a"));
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(
                        ScriptedCaller.call("c1", "a", "{}"),
                        ScriptedCaller.call("c2", "a", "{}")),
                ScriptedCaller.finalText("ok"));
        AgentEngine.Result r = new AgentEngine(store).run("s", "sys", "hi", tools, caller,
                cfg(12));
        assertTrue(r.error, r.done);
        assertEquals(2, caller.requests.size());
        long results = store.events("s").stream()
                .filter(e -> "tool_result".equals(e.type)).count();
        assertEquals("一批两个 call 必须恰好两条结果", 2, results);
        // 第二次请求里两个 tool 结果都必须在
        boolean c1 = false, c2 = false;
        for (com.dsh.models.ChatMessage m : caller.requests.get(1).messages) {
            if ("tool".equals(m.role) && "c1".equals(m.toolCallId)) c1 = true;
            if ("tool".equals(m.role) && "c2".equals(m.toolCallId)) c2 = true;
        }
        assertTrue(c1);
        assertTrue(c2);
    }

    @Test public void 错误即结果_未知工具与坏JSON不中止() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(echo("a"));
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(
                        ScriptedCaller.call("c1", "nope", "{}"),
                        ScriptedCaller.call("c2", "a", "{bad json")),
                ScriptedCaller.finalText("fixed"));
        AgentEngine.Result r = new AgentEngine(store).run("s", "sys", "hi", tools, caller,
                cfg(12));
        assertTrue(r.error, r.done);
        assertEquals(2, store.events("s").stream()
                .filter(e -> "tool_result".equals(e.type)).count());
        String payloads = store.events("s").toString();
        assertTrue(payloads.contains("未知工具"));
        assertTrue(payloads.contains("不是合法 JSON"));
    }

    @Test public void 无步数上限_20步以上仍可跑完() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(echo("a"));
        ModelResponse[] responses = new ModelResponse[26];
        for (int i = 0; i < responses.length - 1; i++) {
            responses[i] = ScriptedCaller.toolCalls(
                    ScriptedCaller.call("c" + i, "a", "{}"));
        }
        responses[responses.length - 1] = ScriptedCaller.finalText("done");
        AgentEngine.Result r = new AgentEngine(store).run("s", "sys", "hi", tools,
                new ScriptedCaller(responses), cfg(0));
        assertTrue(r.error, r.done);
        assertEquals("done", r.finalText);
        assertEquals(26, r.turns);
    }

    @Test public void 有终止工具时_没调用就结束判失败() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(echo("submit"));
        ScriptedCaller caller = new ScriptedCaller(ScriptedCaller.finalText("我完成了"));
        AgentEngine.Config c = cfg(12);
        c.terminalTool = "submit";
        c.requireTerminalTool = true;
        AgentEngine.Result r = new AgentEngine(store).run("s", "sys", "hi", tools, caller, c);
        assertFalse(r.done);
        assertTrue(r.error.contains("终止工具"));
    }

    @Test public void 工具超时返回错误结果而不是挂死() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(new AgentTool() {
            @Override public String name() { return "slow"; }
            @Override public String description() { return "slow"; }
            @Override public String parametersJson() { return "{\"type\":\"object\"}"; }
            @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                try { Thread.sleep(5_000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ToolResult.ok("late");
            }
        });
        ScriptedCaller caller = new ScriptedCaller(
                ScriptedCaller.toolCalls(ScriptedCaller.call("c1", "slow", "{}")),
                ScriptedCaller.finalText("after timeout"));
        AgentEngine.Config c = cfg(12);
        c.toolTimeoutMs = 100;
        AgentEngine.Result r = new AgentEngine(store).run("s", "sys", "hi", tools, caller, c);
        assertTrue("超时是结果，不是异常", r.done);
        assertTrue(store.events("s").toString().contains("工具执行超时"));
    }

    @Test public void 外部取消传播() {
        MemoryAgentStore store = new MemoryAgentStore();
        ToolRegistry tools = new ToolRegistry().add(echo("a"));
        AgentEngine.Config c = cfg(12);
        c.cancel = new java.util.concurrent.atomic.AtomicBoolean(true);
        AgentEngine.Result r = new AgentEngine(store).run("s", "sys", "hi", tools,
                new ScriptedCaller(ScriptedCaller.finalText("x")), c);
        assertTrue(r.done);
        assertTrue(r.finalText.contains("取消") || r.error.contains("取消"));
    }
}
