package com.dsh.agent;

import com.dsh.memory.FrameRef;
import com.dsh.memory.MemorySource;
import com.dsh.models.Json;

import java.util.Map;

/** read_frame：宿主提供已掩码图片；每轮最多 3 张，NEVER_CLOUD 拒绝。 */
public final class ReadFrameTool implements AgentTool {

    public static final int MAX_PER_TURN = 3;
    private final MemorySource source;
    private final Provenance provenance;

    public ReadFrameTool(MemorySource source) { this(source, null); }

    public ReadFrameTool(MemorySource source, Provenance provenance) {
        this.source = source;
        this.provenance = provenance;
    }

    @Override public String name() { return "read_frame"; }

    @Override public String description() {
        return "核对具体细节的标准动作。引用数字/人名/群名/标题/链接/代码前，先看这张图；"
                + "一次只取一张，每轮最多 3 张。看不清就如实说，不要按 OCR 转写猜。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{\"block_id\":{\"type\":\"integer\"}},"
                + "\"required\":[\"block_id\"]}";
    }

    @Override public String[] requiredArgs() { return new String[]{"block_id"}; }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        long id = args.get("block_id") instanceof Number
                ? ((Number) args.get("block_id")).longValue() : -1;
        if (id <= 0) return ToolResult.rejected("block_id 无效");
        if (ctx.imageBudget() != null && !ctx.imageBudget().tryAcquire()) {
            return ToolResult.rejected("本轮看图额度已用完（最多 3 张）");
        }
        FrameRef r = source.readFrame(id);
        if (r == null) return ToolResult.failed("该宿主不支持看图");
        if (r.blocked) return ToolResult.rejected(r.error.isEmpty()
                ? "这块不能看图" : r.error);
        if (r.jpeg == null || r.jpeg.length == 0) {
            return ToolResult.failed(r.error.isEmpty() ? "图片不可读" : r.error);
        }
        if (provenance != null) {
            provenance.markRecalled();
            provenance.setMaskStatus("image");
            if (!provenance.sources().contains("image")) provenance.sources().add("image");
        }
        return ToolResult.okImage("块 #" + id + " 的原始截图（已按应用策略掩码）。"
                + "请以这张图核对细节，不要只信 OCR。", r.jpeg, "low");
    }
}
