package com.dsh.models;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用会话消息（agent 引擎用）。旧的 system/user 快捷请求仍可直接用 ModelRequest.text。
 * 图片只允许出现在 user 消息；tool 消息只带 tool_call_id。
 * thinking 模式下 assistant 的 reasoning_content 必须在后续带 tools 的请求里回传。
 */
public final class ChatMessage {

    public final String role;            // system | user | assistant | tool
    public final String content;
    /** user 可选一张图 */
    public final byte[] jpeg;
    /** 图片 detail：low / high */
    public final String imageDetail;
    /** assistant 的 tool_calls */
    public final List<ToolCall> toolCalls;
    /** tool 消息必须带 */
    public final String toolCallId;
    /** assistant thinking 模式的思维链（带 tools 时必须回传） */
    public final String reasoningContent;

    private ChatMessage(String role, String content, byte[] jpeg, String imageDetail,
                        List<ToolCall> toolCalls, String toolCallId, String reasoningContent) {
        this.role = role;
        this.content = content == null ? "" : content;
        this.jpeg = jpeg;
        this.imageDetail = imageDetail;
        this.toolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null, null, null);
    }

    public static ChatMessage userImage(String content, byte[] jpeg) {
        return userImage(content, jpeg, null);
    }

    public static ChatMessage userImage(String content, byte[] jpeg, String detail) {
        return new ChatMessage("user", content, jpeg, detail, null, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> calls) {
        return assistant(content, calls, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> calls,
                                        String reasoningContent) {
        return new ChatMessage("assistant", content, null, null, calls, null,
                reasoningContent);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, null, null, toolCallId, null);
    }

    public boolean hasImage() { return jpeg != null && jpeg.length > 0; }

    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

    /** 出网闸替换正文时保留角色/图片/tool_call_id/思维链。 */
    public ChatMessage withContent(String newContent) {
        return new ChatMessage(role, newContent, jpeg, imageDetail, toolCalls,
                toolCallId, reasoningContent);
    }
}
