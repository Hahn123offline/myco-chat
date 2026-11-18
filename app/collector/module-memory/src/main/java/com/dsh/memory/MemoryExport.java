package com.dsh.memory;

import com.dsh.host.api.data.LabelStatus;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.memory.EgressTier;

import java.util.List;

/**
 * 调试导出：把记忆块写成 JSON 字符串。纯逻辑（不碰 android.*），可 JVM 单测。
 *
 * 为什么手写：不引第三方 JSON 依赖；只导出固定字段，格式稳定便于 adb 拉下来用脚本分析。
 */
public final class MemoryExport {

    /** labels 统计：total = ok+failed+skipped+pending（未知传 -1，JSON 里如实 -1） */
    public static final class LabelStats {
        public final long ok, failed, skipped, pending, total;

        public LabelStats(long ok, long failed, long skipped, long pending) {
            this.ok = ok;
            this.failed = failed;
            this.skipped = skipped;
            this.pending = pending;
            this.total = (ok < 0 || failed < 0 || skipped < 0 || pending < 0) ? -1 : ok + failed + skipped + pending;
        }

        public static LabelStats unknown() { return new LabelStats(-1, -1, -1, -1); }
    }

    private MemoryExport() { }

    /**
     * @param blocks    要导出的块（调用方已按 n 截断）
     * @param demoQuery 可选：用于验证"检索闭环"的查询词（空 = 不做检索自测）
     * @param demoHits  demoQuery 的检索命中（空 = 无/未传）
     */
    public static String json(List<MemoryBlock> blocks, String demoQuery, List<MemoryBlock> demoHits) {
        return json(blocks, demoQuery, demoHits, -1, -1);
    }

    /**
     * 带"条数对账"的导出：episodes 来自 usagetrace，blocks 来自 memory.db。
     * 差值 K≠0 表示有 episode 没有变成记忆块（脱敏失败/丢弃/规则跳过），必须能被看见。
     * 未知（拿不到 EpisodeStats）传 -1，JSON 里同样是 -1。
     */
    public static String json(List<MemoryBlock> blocks, String demoQuery, List<MemoryBlock> demoHits,
                              long episodesTotal, long blocksTotal) {
        return json(blocks, demoQuery, demoHits, episodesTotal, blocksTotal,
                MemoryFtsInstaller.ENGINE_LIKE, MemoryFtsInstaller.REASON_OK);
    }

    /**
     * 同上，并暴露实际检索引擎（FTS5 / LIKE）与降级原因 —— 降级不能静默。
     */
    public static String json(List<MemoryBlock> blocks, String demoQuery, List<MemoryBlock> demoHits,
                              long episodesTotal, long blocksTotal, String searchEngine) {
        return json(blocks, demoQuery, demoHits, episodesTotal, blocksTotal, searchEngine,
                MemoryFtsInstaller.REASON_OK);
    }

    /**
     * 完整版：引擎 + 原因；labels 未知（测试/兼容旧调用）。
     */
    public static String json(List<MemoryBlock> blocks, String demoQuery, List<MemoryBlock> demoHits,
                              long episodesTotal, long blocksTotal, String searchEngine,
                              String searchEngineReason) {
        return json(blocks, demoQuery, demoHits, episodesTotal, blocksTotal,
                searchEngine, searchEngineReason, LabelStats.unknown());
    }

    /**
     * 完整版：引擎 + 原因 + 打标统计。真机排障时这些是"这个 build 到底跑没跑、降级没降级、
     * 云端消费到哪一步"的证据。
     */
    public static String json(List<MemoryBlock> blocks, String demoQuery, List<MemoryBlock> demoHits,
                              long episodesTotal, long blocksTotal, String searchEngine,
                              String searchEngineReason, LabelStats labels) {
        StringBuilder b = new StringBuilder(1024);
        b.append("{\n");
        b.append("  \"ts\": ").append(System.currentTimeMillis()).append(",\n");
        b.append("  \"dbVersion\": ").append(MemorySchema.DB_VER).append(",\n");
        b.append("  \"exported\": ").append(blocks == null ? 0 : blocks.size()).append(",\n");
        b.append("  \"searchEngine\": ").append(quote(searchEngine == null ? "LIKE" : searchEngine)).append(",\n");
        b.append("  \"searchEngineReason\": ")
                .append(quote(searchEngineReason == null ? "unknown" : searchEngineReason)).append(",\n");
        b.append("  \"tiers\": {");
        appendTierCounts(b, blocks);
        b.append("},\n");
        long diff = (episodesTotal >= 0 && blocksTotal >= 0) ? episodesTotal - blocksTotal : -1;
        b.append("  \"reconcile\": {\"episodes\": ").append(episodesTotal)
                .append(", \"blocks\": ").append(blocksTotal)
                .append(", \"diff\": ").append(diff).append("},\n");
        LabelStats ls = labels == null ? LabelStats.unknown() : labels;
        b.append("  \"labels\": {\"total\": ").append(ls.total)
                .append(", \"ok\": ").append(ls.ok)
                .append(", \"failed\": ").append(ls.failed)
                .append(", \"skipped\": ").append(ls.skipped)
                .append(", \"pending\": ").append(ls.pending).append("},\n");

        b.append("  \"demoQuery\": ").append(quote(demoQuery == null ? "" : demoQuery)).append(",\n");
        b.append("  \"demoHits\": [");
        if (demoHits != null) {
            for (int i = 0; i < demoHits.size(); i++) {
                if (i > 0) b.append(',');
                b.append('\n').append("    ").append(blockJson(demoHits.get(i)));
            }
            if (!demoHits.isEmpty()) b.append('\n').append("  ");
        }
        b.append("],\n");

        b.append("  \"blocks\": [");
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                if (i > 0) b.append(',');
                b.append('\n').append("    ").append(blockJson(blocks.get(i)));
            }
            if (!blocks.isEmpty()) b.append('\n').append("  ");
        }
        b.append("]\n}\n");
        return b.toString();
    }

    /** 单条块的 JSON（字段顺序固定，便于 diff） */
    public static String blockJson(MemoryBlock b) {
        if (b == null) return "null";
        StringBuilder s = new StringBuilder(256);
        s.append('{');
        s.append("\"id\":").append(b.id).append(',');
        s.append("\"episodeId\":").append(b.episodeId).append(',');
        s.append("\"tStart\":").append(b.tStart).append(',');
        s.append("\"tEnd\":").append(b.tEnd).append(',');
        s.append("\"pkg\":").append(quote(b.pkg)).append(',');
        s.append("\"title\":").append(quote(b.title)).append(',');
        s.append("\"maskedText\":").append(quote(b.maskedText)).append(',');
        s.append("\"egressTier\":").append(quote(name(b.egressTier))).append(',');
        s.append("\"redactionLevel\":").append(quote(RedactionLevel.name(b.redactionLevel))).append(',');
        s.append("\"redactedAt\":").append(b.redactedAt).append(',');
        s.append("\"label\":").append(b.label == null ? "null" : quote(b.label)).append(',');
        s.append("\"labelStatus\":").append(quote(LabelStatus.name(b.labelStatus))).append(',');
        s.append("\"score\":").append(Double.toString(b.searchScore)).append(',');
        s.append("\"evidenceRef\":").append(b.evidenceRef == null ? "null" : quote(b.evidenceRef));
        s.append('}');
        return s.toString();
    }

    static void appendTierCounts(StringBuilder b, List<MemoryBlock> blocks) {
        int unknown = 0, white = 0, gray = 0, black = 0;
        if (blocks != null) {
            for (MemoryBlock blk : blocks) {
                EgressTier t = blk == null || blk.egressTier == null ? EgressTier.UNKNOWN : blk.egressTier;
                switch (t) {
                    case WHITE: white++; break;
                    case GRAY: gray++; break;
                    case BLACK: black++; break;
                    default: unknown++; break;
                }
            }
        }
        b.append("\"UNKNOWN\":").append(unknown).append(',');
        b.append("\"WHITE\":").append(white).append(',');
        b.append("\"GRAY\":").append(gray).append(',');
        b.append("\"BLACK\":").append(black);
    }

    static String name(EgressTier t) {
        return t == null ? EgressTier.UNKNOWN.name() : t.name();
    }

    // ---------------- JSON 字符串转义（不引依赖） ----------------

    static String quote(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 8);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
        return b.toString();
    }
}
