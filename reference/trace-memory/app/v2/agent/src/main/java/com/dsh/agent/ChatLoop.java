package com.dsh.agent;

/** 一轮对话编排：落 user 事件 → 跑 engine → 回调宿主。 */
public final class ChatLoop {

    public interface Callback { void onDone(boolean ok, String message); }

    private ChatLoop() { }

    public static AgentEngine.Result run(ChatHost host, String text, Callback cb) {
        if (text == null || text.trim().isEmpty()) {
            if (cb != null) cb.onDone(false, "消息为空");
            return null;
        }
        host.beforeRun();
        host.store().append(host.sessionId(), "user", text);
        AgentEngine.Result r = new AgentEngine(host.store()).run(host.sessionId(),
                host.systemPrompt(), "", host.tools(), host.caller(), host.config());
        host.afterRun(r);
        if (cb != null) {
            if (!r.error.isEmpty()) cb.onDone(false, r.error);
            else cb.onDone(true, r.finalText);
        }
        return r;
    }
}
