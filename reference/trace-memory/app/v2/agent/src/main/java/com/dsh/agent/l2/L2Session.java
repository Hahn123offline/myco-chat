package com.dsh.agent.l2;

import com.dsh.agent.AgentEngine;
import com.dsh.agent.AgentStore;
import com.dsh.agent.LlmCaller;
import com.dsh.agent.ToolRegistry;

import java.util.List;

/**
 * L2 抽取会话：L2 是通用引擎的第一个会话类型。
 *
 * 初始上下文只给纯文本块列表（稳定前缀 → 缓存命中）；图片只在模型主动
 * read_mosaic/read_frame 时才给，且 read_mosaic 一次最多 16 帧拼一张。
 */
public final class L2Session {

    public static final String KIND = "l2";
    public static final int MAX_INITIAL_BLOCKS = 40;

    public static final class Result {
        public final boolean done;
        public final L2Submission submission;
        public final String error;
        public final int turns;

        Result(boolean done, L2Submission submission, String error, int turns) {
            this.done = done;
            this.submission = submission;
            this.error = error == null ? "" : error;
            this.turns = turns;
        }
    }

    private final String chainId;
    private final L2Tools tools;

    public L2Session(String chainId, BlockSource source) {
        this.chainId = chainId;
        this.tools = new L2Tools(chainId, source);
    }

    public L2Tools tools() { return tools; }

    public Result run(String sessionId, AgentStore store, LlmCaller caller) {
        String initialUser = buildInitialUser();
        AgentEngine.Config cfg = new AgentEngine.Config();
        
        cfg.maxImages = 6;
        cfg.maxTokens = 0;             // 不设硬上限：软限制写在 prompt
        cfg.excludedBlocks = tools.excludedBlocks();
        cfg.appPolicy = cfg.excludedBlocks > 0 ? "APP_EXCLUDED" : "FULL_CLOUD";
        cfg.maskedSpans = tools.maskedSpans();
        cfg.maskedKinds = tools.maskedKinds();
        cfg.sources = tools.sources();
        cfg.stage = com.dsh.egress.EgressStage.MASKED;
        if (cfg.maskedSpans > 0) cfg.appPolicy = "REDACT_THEN_CLOUD";
        cfg.thinking = "enabled";
        cfg.terminalTool = "submit_description";
        cfg.requireTerminalTool = true;
        AgentEngine.Result r = new AgentEngine(store).run(sessionId, L2Prompts.SYSTEM,
                initialUser, tools.registry(), caller, cfg);
        return new Result(r.done, tools.submission(), r.error, r.turns);
    }

    /** 第一轮 user：纯文本块列表，不含图片。 */
    public String buildInitialUser() {
        List<L2Block> blocks = tools.blocks();
        StringBuilder b = new StringBuilder();
        b.append("以下是这一段时间的手机使用记录，共 ").append(blocks.size())
                .append(" 条。请为它写一段客观描述。\n\n");
        int n = Math.min(blocks.size(), MAX_INITIAL_BLOCKS);
        for (int i = 0; i < n; i++) b.append(tools.formatBlock(blocks.get(i))).append('\n');
        if (blocks.size() > n) {
            b.append("（初始列表已截断，还有 ").append(blocks.size() - n)
                    .append(" 条，需要时用 list_blocks 分页查看）\n");
        }
        return b.toString();
    }
}
