package com.dsh.agent;

import com.dsh.models.ChatMessage;
import com.dsh.models.Json;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 通用 Agent 会话引擎：单循环，不做预规划。
 *
 * 四条铁律：
 *  1. 只在「没有 tool_calls」时结束（有终止工具时还要求它被调用）
 *  2. 批次完整性：一 assistant 消息里的全部 tool_calls 都拿到结果才下一轮
 *  3. 错误即结果：坏 JSON/未知工具/schema 不符 → ToolResult 错误，不抛异常
 *  4. 单会话最多 12 轮 LLM
 */
public final class AgentEngine {

    public static final int DEFAULT_MAX_IMAGES = 6;
    public static final long DEFAULT_TOTAL_TIMEOUT_MS = 10 * 60_000L;

    public static final class Config {
        public int maxImages = DEFAULT_MAX_IMAGES;
        public long totalTimeoutMs = DEFAULT_TOTAL_TIMEOUT_MS;
        public long toolTimeoutMs = 30_000L;
        public int maxTokens = 0;
        public int excludedBlocks = 0;
        public String appPolicy = "REDACT_THEN_CLOUD";
        public int maskedSpans = 0;
        public String maskedKinds = "";
        /** provenance：payload 的材料来源与阶段（默认未知 + RAW，云端会被拒绝）。 */
        public java.util.List<String> sources = java.util.Collections.emptyList();
        public java.util.List<String> strippedSources = java.util.Collections.emptyList();
        public com.dsh.egress.EgressStage stage = com.dsh.egress.EgressStage.RAW;
        public java.util.function.Supplier<com.dsh.egress.EgressStage> stageSupplier;
        public java.util.function.Supplier<String> maskStatusSupplier;
        /** DeepSeek thinking：enabled/disabled/null */
        public String thinking = "enabled";
        public String terminalTool;          // null = 普通 agent；L2 用 submit_description
        public boolean requireTerminalTool = false;
        /** 压缩摘要落点；null 时只压不落库（测试/无记忆环境）。 */
        public Compaction.Sink compactionSink;
        /** 最新压缩摘要（跨轮注回上下文）。 */
        public java.util.function.Supplier<String> compactionSummarySupplier;
        /** 外部取消：设置 true 后引擎在下一个边界失败退出。 */
        public java.util.concurrent.atomic.AtomicBoolean cancel;
    }

    private static final java.util.concurrent.ExecutorService TOOL_POOL =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "agent-tool");
                t.setDaemon(true);
                return t;
            });

    public static final class Result {
        public final boolean done;
        public final String finalText;
        public final String error;
        public final int turns;
        public final ToolResult terminalResult;

        Result(boolean done, String finalText, String error, int turns, ToolResult terminal) {
            this.done = done;
            this.finalText = finalText == null ? "" : finalText;
            this.error = error == null ? "" : error;
            this.turns = turns;
            this.terminalResult = terminal;
        }
    }

    private final AgentStore store;

    public AgentEngine(AgentStore store) {
        this.store = store;
    }

    /**
     * 运行/续跑一个会话。若 store 里已有 user 事件，则不重复添加初始 user（续跑）。
     */
    public Result run(String sessionId, String systemPrompt, String initialUser,
                      ToolRegistry tools, LlmCaller caller, Config config) {
        Config cfg = config == null ? new Config() : config;
        long start = System.currentTimeMillis();
        Map<Long, byte[]> artifacts = new HashMap<>();
        ImageBudget budget = new ImageBudget(cfg.maxImages);
        ToolContext toolContext = new ToolContext(budget, new HashMap<>());

        List<AgentEvent> existing = store.events(sessionId);
        boolean hasUser = false;
        for (AgentEvent e : existing) if ("user".equals(e.type)) { hasUser = true; break; }
        if (!hasUser) store.append(sessionId, "user", initialUser == null ? "" : initialUser);

        int turns = 0;
        ToolResult terminal = null;
        while (true) {
            if (cfg.cancel != null && cfg.cancel.get()) {
                String msg = "这一轮被外部取消了，没有继续执行。";
                store.append(sessionId, "assistant",
                        "{\"content\":" + Json.quote(msg) + ",\"tool_calls\":[]}");
                return new Result(true, msg, "会话被取消", turns, terminal);
            }
            if (System.currentTimeMillis() - start > cfg.totalTimeoutMs) {
                String msg = "这一轮因为总时长到了而被中断，没有继续执行。";
                store.append(sessionId, "assistant",
                        "{\"content\":" + Json.quote(msg) + ",\"tool_calls\":[]}");
                return new Result(true, msg, "会话总超时", turns, terminal);
            }
            List<ChatMessage> transcript;
            try {
                transcript = new TranscriptBuilder(store, sessionId, systemPrompt, artifacts,
                        cfg.compactionSummarySupplier == null ? ""
                                : cfg.compactionSummarySupplier.get()).build();
            } catch (Throwable t) {
                // 本地转录非法：绝不发云端，错误精确记录在本地
                store.append(sessionId, "error",
                        "{\"error\":" + Json.quote("transcript illegal: " + describe(t)) + "}");
                return new Result(false, "", "本地转录校验失败: " + describe(t), turns, terminal);
            }
            Compaction.Result compacted = Compaction.maybe(transcript, caller, cfg.compactionSink);
            if (!compacted.ok) {
                store.append(sessionId, "error",
                        "{\"error\":" + Json.quote("compaction failed: " + compacted.error) + "}");
                return new Result(false, "上下文压缩失败: " + compacted.error,
                        "compaction failed: " + compacted.error, turns, terminal);
            }
            if (!compacted.skipped) transcript = compacted.messages;
            com.dsh.egress.EgressStage stage = cfg.stageSupplier != null
                    ? cfg.stageSupplier.get() : cfg.stage;
            ModelRequest request = ModelRequest.chat(transcript, tools.specs())
                    .withMaxTokens(cfg.maxTokens)
                    .withThinking(cfg.thinking)
                    .withAppPolicy(cfg.excludedBlocks, cfg.appPolicy)
                    .withMasking(cfg.maskedSpans, cfg.maskedKinds)
                    .withProvenance(cfg.sources, cfg.strippedSources, stage)
                    .withMaskStatus(cfg.maskStatusSupplier == null ? ""
                            : cfg.maskStatusSupplier.get());
            ModelResponse reply;
            try {
                // 单次 LLM 调用硬超时：网络挂起时 session 总超时原来只在轮次间检查，
                // 会无限等待（真机 block 70 39 块 + 大图实测卡住 10 分钟以上）。
                reply = callWithTimeout(caller, request, cfg, start);
            } catch (TimeoutException te) {
                store.append(sessionId, "error", "{\"error\":\"LLM call timeout\"}");
                return new Result(false, "", "LLM 调用超时", turns, terminal);
            } catch (Throwable t) {
                store.append(sessionId, "error", "{\"error\":" + Json.quote(describe(t)) + "}");
                return new Result(false, "", "LLM 调用异常: " + describe(t), turns, terminal);
            }
            turns++;
            if (reply == null || !reply.isOk()) {
                String err = reply == null ? "LLM 返回 null" : reply.error;
                store.append(sessionId, "error", "{\"error\":" + Json.quote(err) + "}");
                return new Result(false, "", err, turns, terminal);
            }

            List<ToolCall> calls = reply.toolCalls;
            store.append(sessionId, "assistant",
                    assistantPayload(reply.text, reply.reasoningContent, calls));

            if (calls.isEmpty()) {
                if (cfg.requireTerminalTool && terminal == null) {
                    store.append(sessionId, "error",
                            "{\"error\":\"模型未调用终止工具 " + cfg.terminalTool + "\"}");
                    return new Result(false, reply.text, "模型未调用终止工具", turns, terminal);
                }
                return new Result(true, reply.text, "", turns, terminal);
            }

            // 批次完整性：先全部执行并落结果，再进入下一轮
            boolean terminalHit = false;
            for (ToolCall call : calls) {
                if (cfg.cancel != null && cfg.cancel.get()) {
                    store.append(sessionId, "error", "{\"error\":\"session cancelled\"}");
                    return new Result(false, "", "会话被取消", turns, terminal);
                }
                ToolResult r = runTool(tools, call, toolContext, cfg.toolTimeoutMs);
                long imageRef = 0;
                if (r.imageJpeg != null && r.imageJpeg.length > 0) {
                    imageRef = Math.abs((call.id + ":" + turns).hashCode());
                    artifacts.put(imageRef, r.imageJpeg);
                }
                store.append(sessionId, "tool_result",
                        toolResultPayload(call.id, r, imageRef));
                if (cfg.terminalTool != null && cfg.terminalTool.equals(call.name)
                        && r.isOk()) {
                    terminal = r;
                    terminalHit = true;
                }
            }
            if (terminalHit) {
                return new Result(true, "", "", turns, terminal);
            }
            if (budget.remaining() == 0 && cfg.maxImages > 0) {
                // 预算用尽不终止会话，后续 read_* 会返回工具错误让模型提交
            }
        }
    }

    private static final ExecutorService LLM_CALLS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-llm-call");
        t.setDaemon(true);
        return t;
    });

    private static final long LLM_CALL_TIMEOUT_MS = 180_000L;

    private static ModelResponse callWithTimeout(LlmCaller caller, ModelRequest req,
                                                 Config cfg, long start)
            throws Exception {
        long left = cfg.totalTimeoutMs - (System.currentTimeMillis() - start);
        long timeout = Math.max(10_000L, Math.min(LLM_CALL_TIMEOUT_MS, left));
        Future<ModelResponse> f = LLM_CALLS.submit(() -> caller.call(req));
        try {
            return f.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Future.cancel 中断不了 SocketInputStream.read；主动断连，释放线程
            try { com.dsh.models.UrlConnectionTransport.cancelAllActive(); } catch (Throwable ignored) { }
            f.cancel(true);
            throw e;
        }
    }

    private static ToolResult runTool(ToolRegistry tools, ToolCall call,
                                      ToolContext ctx, long timeoutMs) {
        if (timeoutMs <= 0) return tools.execute(call.name, call.argumentsJson, ctx);
        java.util.concurrent.Future<ToolResult> f;
        try {
            f = TOOL_POOL.submit(() -> tools.execute(call.name, call.argumentsJson, ctx));
        } catch (Throwable t) {
            return ToolResult.failed("工具提交失败: " + describe(t));
        }
        try {
            return f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            f.cancel(true);
            return ToolResult.failed("工具执行超时（" + timeoutMs + "ms）: " + call.name);
        } catch (InterruptedException ie) {
            f.cancel(true);
            Thread.currentThread().interrupt();
            return ToolResult.failed("工具执行被中断: " + call.name);
        } catch (Throwable t) {
            return ToolResult.failed("工具执行异常: " + describe(t));
        }
    }

    private static String assistantPayload(String content, String reasoning,
                                           List<ToolCall> calls) {
        StringBuilder b = new StringBuilder();
        b.append('{').append("\"content\":").append(Json.quote(content == null ? "" : content));
        if (reasoning != null && !reasoning.isEmpty()) {
            b.append(",\"reasoning_content\":").append(Json.quote(reasoning));
        }
        b.append(",\"tool_calls\":[");
        boolean first = true;
        for (ToolCall c : calls) {
            if (c == null) continue;
            if (!first) b.append(',');
            first = false;
            b.append('{').append("\"id\":").append(Json.quote(c.id))
                    .append(",\"name\":").append(Json.quote(c.name))
                    .append(",\"arguments\":").append(Json.quote(c.argumentsJson))
                    .append('}');
        }
        b.append("]}");
        return b.toString();
    }

    private static String toolResultPayload(String id, ToolResult r, long imageRef) {
        String summary = r.summary;
        StringBuilder b = new StringBuilder();
        b.append('{').append("\"tool_call_id\":").append(Json.quote(id))
                .append(",\"status\":").append(Json.quote(r.status.name()))
                .append(",\"summary\":").append(Json.quote(summary))
                .append(",\"error\":").append(Json.quote(r.error));
        if (imageRef != 0) b.append(",\"image_ref\":").append(imageRef);
        if (r.imageDetail != null && !r.imageDetail.isEmpty()) {
            b.append(",\"image_detail\":").append(Json.quote(r.imageDetail));
        }
        b.append('}');
        return b.toString();
    }

    private static String describe(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
    }
}
