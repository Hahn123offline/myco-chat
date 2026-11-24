package com.dsh.agent;

import com.dsh.models.ChatMessage;
import com.dsh.models.Json;
import com.dsh.models.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每次 LLM 调用前从 append-only 事件重建转录：
 *  1. 还原完整转录（含 reasoning_content）
 *  2. 修复 call/result 配对：缺失补 NOT_EXECUTED，孤儿 result 丢弃
 *  3. 按「对话组」裁剪（一组 = 一条 assistant + 它的全部 tool 结果 + 图片），
 *     绝不切开 call/result
 *  4. 最后 {@link TranscriptValidator#assertLegal} 本地硬校验
 */
public final class TranscriptBuilder {

    private final AgentStore store;
    private final String sessionId;
    private final String systemPrompt;
    private final Map<Long, byte[]> artifacts;

    private final String compactionSummary;

    public TranscriptBuilder(AgentStore store, String sessionId, String systemPrompt,
                             Map<Long, byte[]> artifacts) {
        this(store, sessionId, systemPrompt, artifacts, "");
    }

    public TranscriptBuilder(AgentStore store, String sessionId, String systemPrompt,
                             Map<Long, byte[]> artifacts, String compactionSummary) {
        this.store = store;
        this.sessionId = sessionId;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.artifacts = artifacts == null ? new HashMap<>() : artifacts;
        this.compactionSummary = compactionSummary == null ? "" : compactionSummary;
    }

    public List<ChatMessage> build() {
        List<AgentEvent> events = store.events(sessionId);
        Map<String, ToolResult> results = new LinkedHashMap<>();
        Map<String, Long> imageRefs = new LinkedHashMap<>();
        Map<String, String> imageDetails = new LinkedHashMap<>();

        for (AgentEvent e : events) {
            if (!"tool_result".equals(e.type)) continue;
            Map<String, Object> p = Json.asObject(Json.parse(e.payload));
            if (p == null) continue;
            String id = Json.string(p.get("tool_call_id"));
            if (id == null) continue;
            ToolResult.Status st = parseStatus(Json.string(p.get("status")));
            String detail = Json.string(p.get("image_detail"));
            Object ref = p.get("image_ref");
            results.put(id, new ToolResult(st, Json.string(p.get("summary")),
                    Json.string(p.get("error")), null, detail));
            if (ref instanceof Number) imageRefs.put(id, ((Number) ref).longValue());
            if (detail != null) imageDetails.put(id, detail);
        }

        List<ChatMessage> full = new ArrayList<>();
        if (!systemPrompt.isEmpty()) full.add(ChatMessage.system(systemPrompt));
        for (AgentEvent e : events) {
            if ("user".equals(e.type)) {
                full.add(ChatMessage.user(e.payload));
            } else if ("assistant".equals(e.type)) {
                Map<String, Object> p = Json.asObject(Json.parse(e.payload));
                if (p == null) continue;
                List<ToolCall> calls = new ArrayList<>();
                List<Object> raw = Json.asArray(p.get("tool_calls"));
                if (raw != null) {
                    for (Object o : raw) {
                        Map<String, Object> c = Json.asObject(o);
                        if (c == null) continue;
                        calls.add(new ToolCall(Json.string(c.get("id")),
                                Json.string(c.get("name")), Json.string(c.get("arguments"))));
                    }
                }
                full.add(ChatMessage.assistant(Json.string(p.get("content")),
                        calls, Json.string(p.get("reasoning_content"))));
                // 关键顺序：先补齐这一轮所有 tool 结果，再追加图片。
                // 图片是 user 消息；夹在两个 tool 结果之间会让云端判定
                // "tool_calls 后跟的 tool messages 不足"（chain83 的 400 就来自这里）。
                for (ToolCall c : calls) {
                    if (c.id == null || c.id.isEmpty()) continue;
                    ToolResult r = results.get(c.id);
                    full.add(ChatMessage.tool(c.id, r == null
                            ? ToolResult.notExecuted("模型未执行该工具（进程中断/转录修复）").forModel()
                            : r.forModel()));
                }
                for (ToolCall c : calls) {
                    Long ref = imageRefs.get(c.id);
                    if (ref != null && artifacts.containsKey(ref)) {
                        full.add(ChatMessage.userImage("工具返回的图片", artifacts.get(ref),
                                imageDetails.get(c.id)));
                    }
                }
            }
        }
        // ICL compaction：最新摘要注回系统提示词之后、对话之前。
        if (!compactionSummary.isEmpty()) {
            int at = (!full.isEmpty() && "system".equals(full.get(0).role)) ? 1 : 0;
            full.add(at, ChatMessage.user("【早前对话的压缩摘要】\n" + compactionSummary));
        }
        TranscriptValidator.assertLegal(full);
        return full;
    }

    private static ToolResult.Status parseStatus(String s) {
        if (s == null) return ToolResult.Status.NOT_EXECUTED;
        try { return ToolResult.Status.valueOf(s); } catch (Throwable ignored) {
            return ToolResult.Status.NOT_EXECUTED;
        }
    }
}
