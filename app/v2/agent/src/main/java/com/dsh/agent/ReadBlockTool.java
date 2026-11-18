package com.dsh.agent;

import com.dsh.memory.BlockRef;
import com.dsh.memory.MemorySource;
import com.dsh.models.Json;

import java.util.List;
import java.util.Map;

/** read_block：按块 id 下钻原始 OCR/masked/截图指针。 */
public final class ReadBlockTool implements AgentTool {

    private final MemorySource source;

    public ReadBlockTool(MemorySource source) { this.source = source; }

    @Override public String name() { return "read_block"; }

    @Override public String description() {
        return "按块 id 读取原始记录：OCR 文本、masked 文本、pkg、时间、shot_ref、page_id。"
                + "如果返回里带 shot_ref，说明有截图——OCR 不可信时细节应以截图为准。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{\"ids\":{\"type\":\"array\","
                + "\"items\":{\"type\":\"integer\"}}},\"required\":[\"ids\"]}";
    }

    @Override public String[] requiredArgs() { return new String[]{"ids"}; }

    @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        List<Object> ids = Json.asArray(args.get("ids"));
        if (ids == null) {
            Object one = args.get("ids");
            if (one instanceof Number) ids = java.util.Collections.singletonList(one);
        }
        if (ids == null || ids.isEmpty()) return ToolResult.rejected("ids 为空");
        StringBuilder b = new StringBuilder();
        int found = 0;
        for (Object o : ids) {
            long id = o instanceof Number ? ((Number) o).longValue() : -1;
            if (id <= 0) {
                if (o != null) try { id = Long.parseLong(String.valueOf(o)); }
                catch (Throwable ignored) { }
            }
            if (id <= 0) { b.append("block #").append(o).append("：id 无效\n"); continue; }
            BlockRef r = source.readBlock(id);
            if (r == null) {
                b.append("block #").append(id).append("：不存在或无证据库\n");
            } else {
                b.append(r.render()).append("\n");
                found++;
            }
        }
        if (found == 0) return ToolResult.failed("没有读到任何块\n" + b);
        return ToolResult.ok(b.toString().trim());
    }
}
