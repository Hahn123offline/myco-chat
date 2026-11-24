package com.dsh.agent.l2;

import com.dsh.agent.AgentTool;
import com.dsh.agent.ImageBudget;
import com.dsh.agent.ToolContext;
import com.dsh.agent.ToolRegistry;
import com.dsh.agent.ToolResult;
import com.dsh.models.Json;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** L2 的四个工具（文案严格按规格；校验在工具内，错误以 ToolResult 返回）。 */
public final class L2Tools {

    public static final int MAX_MOSAIC_BLOCKS = 16;
    public static final int MAX_TITLE = 30;
    public static final int MIN_BODY = 0;
    public static final int MAX_BODY = 700;

    private final String chainId;
    private final BlockSource source;
    private final List<L2Block> blocks;
    private final Map<Long, L2Block> byId = new LinkedHashMap<>();
    private L2Submission submission;
    private boolean gapRetryUsed;
    private boolean bodyRetryUsed;

    public L2Tools(String chainId, BlockSource source) {
        this.chainId = chainId;
        this.source = source;
        this.blocks = source.blocks(chainId);
        for (L2Block b : blocks) byId.put(b.id, b);
    }

    public int excludedBlocks() { return source.excludedBlocks(); }

    public int maskedSpans() { return source.maskedSpans(); }

    public String maskedKinds() { return source.maskedKinds(); }

    /** 本批块的材料来源 pkg 并集（provenance）。 */
    public List<String> sources() {
        List<String> out = new ArrayList<>();
        for (L2Block b : blocks) {
            if (b.pkg != null && !b.pkg.isEmpty() && !out.contains(b.pkg)) out.add(b.pkg);
        }
        return out;
    }

    public L2Submission submission() { return submission; }

    public List<L2Block> blocks() { return blocks; }

    public ToolRegistry registry() {
        ToolRegistry r = new ToolRegistry();
        r.add(new AgentTool() {
            @Override public String name() { return "list_blocks"; }
            @Override public String description() { return L2Prompts.LIST_BLOCKS; }
            @Override public String parametersJson() { return L2Prompts.LIST_SCHEMA; }
            @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                int offset = intArg(args, "offset", 0);
                int limit = intArg(args, "limit", 40);
                if (offset < 0) offset = 0;
                if (limit <= 0) limit = 40;
                int end = Math.min(blocks.size(), offset + limit);
                StringBuilder b = new StringBuilder();
                b.append("共 ").append(blocks.size()).append(" 条，返回 [")
                        .append(offset).append(",").append(end).append(")\n");
                for (int i = offset; i < end; i++) b.append(formatBlock(blocks.get(i))).append('\n');
                return ToolResult.ok(b.toString());
            }
        });
        r.add(new AgentTool() {
            @Override public String name() { return "read_mosaic"; }
            @Override public String description() { return L2Prompts.READ_MOSAIC; }
            @Override public String parametersJson() { return L2Prompts.MOSAIC_SCHEMA; }
            @Override public String[] requiredArgs() { return new String[]{"block_ids"}; }
            @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                List<Long> ids = longList(args.get("block_ids"));
                if (ids.isEmpty() || ids.size() > MAX_MOSAIC_BLOCKS) {
                    return ToolResult.failed("block_ids 需要 1–" + MAX_MOSAIC_BLOCKS + " 个");
                }
                List<Long> valid = new ArrayList<>();
                int blocked = 0;
                for (Long id : ids) {
                    L2Block b = byId.get(id);
                    if (b == null) continue;
                    if (b.blocked) { blocked++; continue; }   // 敏感块图片永不出网
                    valid.add(id);
                }
                if (valid.isEmpty()) {
                    return ToolResult.rejected("这些块都被出网闸屏蔽，没有可看的图片");
                }
                if (ctx.imageBudget() != null && !ctx.imageBudget().tryAcquire()) {
                    return ToolResult.rejected("看图次数已用完，请基于现有信息提交");
                }
                byte[] jpeg = source.mosaic(chainId, valid);
                if (jpeg == null || jpeg.length == 0) {
                    return ToolResult.failed("拼图失败");
                }
                return ToolResult.okImage("已拼成 1 张图（" + valid.size() + " 帧"
                        + (blocked > 0 ? "，跳过 " + blocked + " 个敏感块" : "") + "）",
                        jpeg, "low");
            }
        });
        r.add(new AgentTool() {
            @Override public String name() { return "read_frame"; }
            @Override public String description() { return L2Prompts.READ_FRAME; }
            @Override public String parametersJson() { return L2Prompts.FRAME_SCHEMA; }
            @Override public String[] requiredArgs() { return new String[]{"block_id"}; }
            @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                long id = longArg(args, "block_id", -1);
                if (id < 0 || !byId.containsKey(id)) return ToolResult.failed("块不存在: " + id);
                L2Block b = byId.get(id);
                if (b.blocked) return ToolResult.rejected("该块被出网闸屏蔽，图片不能发送");
                if (ctx.imageBudget() != null && !ctx.imageBudget().tryAcquire()) {
                    return ToolResult.rejected("看图次数已用完，请基于现有信息提交");
                }
                int maxEdge = intArg(args, "max_edge", 1600);
                byte[] jpeg = source.frame(chainId, id, maxEdge);
                if (jpeg == null || jpeg.length == 0) return ToolResult.failed("读帧失败");
                return ToolResult.okImage("已返回块 #" + id + " 的高清帧", jpeg);
            }
        });
        r.add(new AgentTool() {
            @Override public String name() { return "submit_description"; }
            @Override public String description() { return L2Prompts.SUBMIT; }
            @Override public String parametersJson() { return L2Prompts.SUBMIT_SCHEMA; }
            @Override public String[] requiredArgs() {
                return new String[]{"title", "body", "key_block_ids"};
            }
            @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
                String title = Json.string(args.get("title"));
                String body = Json.string(args.get("body"));
                if (title == null || title.trim().isEmpty()) title = "未命名";
                // 格式约束在程序侧归一化，不打回模型（只保留缺口声明这一条语义校验）
                title = truncateTitle(title.trim());
                if (body == null) body = "";
                body = keepBody(body);
                List<Long> keys = new ArrayList<>();
                List<Object> rawKeys = Json.asArray(args.get("key_block_ids"));
                if (rawKeys != null) {
                    for (Object o : rawKeys) {
                        if (!(o instanceof Number)) continue;
                        long k = ((Number) o).longValue();
                        if (byId.containsKey(k) && !keys.contains(k)) keys.add(k);
                        if (keys.size() >= 5) break;
                    }
                }
                if (keys.isEmpty()) {
                    // 程序侧兜底：从本链任意块里挑 1–5 个
                    for (L2Block b : blocks) {
                        keys.add(b.id);
                        if (keys.size() >= 5) break;
                    }
                }
                if (body.length() > MAX_BODY && !bodyRetryUsed) {
                    bodyRetryUsed = true;
                    return ToolResult.failed("你的输出 " + body.length()
                            + " 字，超过 " + MAX_BODY
                            + "。请压缩到 " + MAX_BODY
                            + " 以内，保留结论和结尾的范围限定句；不要截断。");
                }
                boolean gapDeclared = true;
                if (source.hasExcludedBlocks() && !hasGapDeclaration(body)) {
                    if (!gapRetryUsed) {
                        gapRetryUsed = true;
                        return ToolResult.failed("这条链含被排除 App 的内容，body 结尾必须明确说明"
                                + "「其中一段内容不在可处理范围内」。请只改结尾，再提交一次。");
                    }
                    gapDeclared = false;   // 重试一次仍未写：接受，但字段标记
                }
                submission = new L2Submission(title, body, keys, gapDeclared,
                        normalizeOutcome(Json.string(args.get("outcome"))));
                return ToolResult.ok("已提交描述：" + title);
            }
        });
        return r;
    }

    /** title 截断到最后一个完整标点/空格，避免在词中间切。 */
    static String truncateTitle(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= MAX_TITLE) return t;
        String cut = t.substring(0, MAX_TITLE);
        int p = lastBoundary(cut);
        return p > 0 ? cut.substring(0, p) : cut;
    }

    /** body 不再截断：超长由模型重试压缩，仍超保留全文并标记 over_length。 */
    static String keepBody(String s) {
        return s == null ? "" : s.trim();
    }

    private static int lastBoundary(String s) {
        for (int i = s.length() - 1; i > 0; i--) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '，' || c == '、'
                    || c == ' ' || c == '·' || c == ';' || c == '；') return i;
        }
        return -1;
    }

    public String formatBlock(L2Block b) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss");
        String text = b.excluded ? "[APP_EXCLUDED]"
                : b.blocked ? "[BLOCKED_BY_EGRESS]"
                : (b.maskedText.length() > 120 ? b.maskedText.substring(0, 120) + "…" : b.maskedText);
        return "#" + b.id + " " + f.format(new Date(b.t0)) + "-" + f.format(new Date(b.t1))
                + "  " + b.pkg + "  " + (b.title.isEmpty() ? "" : "UI=" + b.title + "  ")
                + "「" + text + "」";
    }

    static String normalizeOutcome(String s) {
        if (s == null) return "unknown";
        String x = s.trim().toLowerCase();
        if (x.equals("resolved") || x.equals("in_progress")
                || x.equals("no_action") || x.equals("unknown")) return x;
        return "unknown";
    }

    /** partial 链的结尾缺口声明（必须在正文末尾）。 */
    public static boolean hasGapDeclaration(String body) {
        if (body == null) return false;
        String t = body.trim();
        for (String p : new String[]{"不在可处理范围内", "不在视野内", "部分内容无法处理",
                "待本地处理", "内容不完整"}) {
            if (t.endsWith(p) || t.endsWith(p + "。") || t.endsWith(p + "，")
                    || t.endsWith(p + "…") || t.endsWith(p + "……")) return true;
        }
        return false;
    }

    private static int intArg(Map<String, Object> args, String key, int def) {
        Object o = args.get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        return def;
    }

    private static long longArg(Map<String, Object> args, String key, long def) {
        Object o = args.get(key);
        if (o instanceof Number) return ((Number) o).longValue();
        return def;
    }

    private static List<Long> longList(Object o) {
        List<Long> out = new ArrayList<>();
        List<Object> arr = Json.asArray(o);
        if (arr == null) return out;
        for (Object x : arr) if (x instanceof Number) out.add(((Number) x).longValue());
        return out;
    }
}
