package com.dsh.v2;

import android.content.Context;

import com.dsh.agent.AgentEngine;
import com.dsh.agent.ChatHost;
import com.dsh.agent.AgentStore;
import com.dsh.agent.LlmCaller;
import com.dsh.agent.ToolRegistry;
import com.dsh.models.Capability;
import com.dsh.models.Quality;
import com.dsh.models.SimpleTask;
import com.dsh.egress.EgressStage;
import com.dsh.egress.EgressTier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 单会话 chat：复用 AgentDbStore；prompt 三层 + recall v0；provenance 显式 DERIVED。 */
public final class ChatSession {

    public static final String ID = "chat";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-session");
        t.setDaemon(true);
        return t;
    });

    public interface Callback { void onDone(boolean ok, String message); }

    private ChatSession() { }

    public static void send(Context ctx, String text, Callback cb) {
        sendAs(ctx, ID, text, cb);
    }

    /** 指定会话 id（验收/多会话用）；默认仍为 chat。 */
    public static void sendAs(Context ctx, String sessionId, String text, Callback cb) {
        final String sid = sessionId == null || sessionId.trim().isEmpty() ? ID : sessionId;
        IO.execute(() -> {
            try {
                runBlocking(ctx, sid, text, cb);
            } catch (Throwable t) {
                if (cb != null) cb.onDone(false, "会话失败: " + t.getClass().getSimpleName());
            }
        });
    }

    private static void runBlocking(Context ctx, String sessionId, String text, Callback cb) {
        if (text == null || text.trim().isEmpty()) {
            if (cb != null) cb.onDone(false, "消息为空");
            return;
        }
        AgentDbStore db = new AgentDbStore(ctx);
        ChatProvenance provenance = new ChatProvenance();
        AndroidMemorySource memory = new AndroidMemorySource(ctx);
        ToolRegistry tools = new ToolRegistry();
        tools.add(new com.dsh.agent.RecallTool(memory, provenance,
                new AndroidMaskProvider(ctx)));
        tools.add(new com.dsh.agent.RememberTool(memory.store(), sessionId, text));
        tools.add(new com.dsh.agent.MemoryStatusTool(memory::status));
        tools.add(new com.dsh.agent.ReadBlockTool(memory));
        tools.add(new com.dsh.agent.ReadFrameTool(memory, provenance));
        tools.add(new com.dsh.agent.CorrectTool(memory.store()));
        tools.add(new com.dsh.agent.ForgetTool(memory.store()));

        AgentEngine.Config cfg = new AgentEngine.Config();
        cfg.thinking = "disabled";
        cfg.sources = provenance.sources;
        cfg.strippedSources = provenance.strippedSources;
        cfg.stageSupplier = provenance;
        cfg.maskStatusSupplier = () -> provenance.maskStatus;
        cfg.appPolicy = "DERIVED";
        cfg.maxImages = 3;
        cfg.compactionSummarySupplier = () -> {
            AndroidMemoryStore ms = new AndroidMemoryStore(ctx);
            java.util.List<com.dsh.memory.MemItem> xs = ms.query(
                    new com.dsh.memory.Query().namespace("compaction").limit(1000));
            return xs.isEmpty() ? "" : xs.get(xs.size() - 1).text;
        };
        cfg.compactionSink = (summary, dropped) -> {
            AndroidMemoryStore ms = new AndroidMemoryStore(ctx);
            ms.registerNamespace("compaction", false);
            com.dsh.memory.MemItem m = new com.dsh.memory.MemItem(
                    "compaction", com.dsh.memory.Kind.SUMMARY,
                    "sum:" + System.currentTimeMillis(), summary,
                    "{\"dropped\":" + dropped + "}");
            m.evidence = java.util.Collections.singletonList(
                    new com.dsh.memory.Evidence("chat", "compaction", System.currentTimeMillis()));
            m.weight = 1.0;
            m.t0 = m.t1 = System.currentTimeMillis();
            m.detailLevel = com.dsh.memory.DetailLevel.FULL;
            ms.put(m);
        };
        LlmCaller caller = req -> TraceApp.get(ctx).modelsModule().router().execute(
                new SimpleTask("chat", java.util.Collections.singleton(Capability.JSON),
                        Quality.GOOD, EgressTier.WHITE), req);
        ChatHost host = new ChatHost() {
            @Override public String sessionId() { return sessionId; }
            @Override public String systemPrompt() { return ChatSession.systemPrompt(ctx); }
            @Override public AgentStore store() { return db; }
            @Override public ToolRegistry tools() { return tools; }
            @Override public LlmCaller caller() { return caller; }
            @Override public AgentEngine.Config config() { return cfg; }
            @Override public void beforeRun() {
                db.ensureSession(sessionId, "chat", "", sessionId.equals(ID) ? "single=1" : "");
            }
            @Override public void afterRun(AgentEngine.Result r) {
                db.updateStatus(sessionId, r.done ? "active" : "failed", r.turns);
            }
        };
        com.dsh.agent.ChatLoop.run(host, text, (ok, msg) -> {
            if (cb != null) cb.onDone(ok, msg);
        });
    }

    public static String systemPrompt(Context ctx) {
        String persona = PromptAssembler.loadAsset(ctx, "prompt/persona.md");
        String sim = PromptAssembler.loadAsset(ctx, "prompt/sim.md");
        return PromptAssembler.assemble(persona, sim, facts(ctx));
    }

    private static String facts(Context ctx) {
        StringBuilder b = new StringBuilder();
        b.append("当前时间：").append(new java.util.Date()).append('\n');
        try {
            TraceStore ts = new TraceStore(ctx);
            b.append("今日痕迹数：").append(ts.countToday()).append('\n');
        } catch (Throwable ignored) { }
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.add(java.util.Calendar.DAY_OF_YEAR, -1);
            String day = String.format("%04d-%02d-%02d", c.get(java.util.Calendar.YEAR),
                    c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
            DayNarrativeStore store = new DayNarrativeStore(ctx);
            DayNarrativeStore.Row row = store.get(day);
            if (row != null && row.narrative != null && !row.narrative.isEmpty()) {
                b.append("最近日叙事（").append(day).append("）：\n").append(row.narrative)
                        .append('\n');
                if (row.mainlines != null && !row.mainlines.isEmpty()) {
                    b.append("主线：").append(row.mainlines).append('\n');
                }
            }
        } catch (Throwable ignored) { }
        return b.toString();
    }
}
