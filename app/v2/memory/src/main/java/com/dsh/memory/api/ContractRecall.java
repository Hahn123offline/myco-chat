package com.dsh.memory.api;

import com.dsh.contract.Freshness;
import com.dsh.contract.MemoryReader;
import com.dsh.contract.Recall;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import com.dsh.memory.BlockRef;
import com.dsh.memory.FrameRef;
import com.dsh.memory.LineItem;
import com.dsh.memory.MemorySource;
import com.dsh.memory.RecallCore;
import com.dsh.memory.RecallHit;
import com.dsh.memory.RecallQuery;

/**
 * 契约的纯 JVM 实现：把 {@link MemoryReader} 接到既有的 {@link RecallCore} 上。
 *
 * 放在 {@code com.dsh.memory.api} 下，与实现细节（RecallCore/RecallRanker/...）区分开：
 * 另一仓只需要这两个包里的东西。
 *
 * 宿主（Android）只需实现 {@link MemorySource} 并注入，本类不依赖 SQLite。
 */
public final class ContractRecall implements MemoryReader {

    private final MemorySource source;
    private final SimpleDateFormat dayFmt;

    public ContractRecall(MemorySource source) {
        this.source = source;
        this.dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        this.dayFmt.setTimeZone(TimeZone.getDefault());
    }

    @Override
    public Recall.Result recall(Recall.Request req) {
        if (req == null) req = Recall.Request.of("");
        RecallQuery q = new RecallQuery()
                .query(req.query)
                .object(req.object)
                .pkg(req.pkg)
                .limit(req.limit);
        if (req.from > 0) q.from(day(req.from));
        if (req.to > 0) q.to(day(req.to));

        List<RecallHit> hits;
        try {
            hits = RecallCore.recall(source, q);
        } catch (Throwable t) {
            hits = new ArrayList<>();
        }
        if (hits == null) hits = new ArrayList<>();

        long[] ids = new long[hits.size()];
        long minT = Long.MAX_VALUE, maxT = Long.MIN_VALUE, covered = 0L;
        for (int i = 0; i < hits.size(); i++) {
            RecallHit h = hits.get(i);
            ids[i] = h.itemId;
            if (h.t0 > 0) {
                minT = Math.min(minT, h.t0);
                maxT = Math.max(maxT, h.t0);
            }
            covered += Math.max(0L, h.coveredMs);
        }
        if (hits.isEmpty()) { minT = 0L; maxT = 0L; }

        int limit = req.limit > 0 ? req.limit : 8;
        boolean truncated = hits.size() >= limit;
        String shown = hits.isEmpty()
                ? "无命中"
                : (truncated ? ("最近 " + hits.size() + " 条（已截断）")
                             : (hits.size() + " 条"));

        long scopeFrom = req.from > 0 ? req.from : minT;
        long scopeTo = req.to > 0 ? req.to : maxT;
        Recall.Coverage cov = new Recall.Coverage(scopeFrom, scopeTo, covered,
                null, truncated ? 1 : 0, shown);

        String answer;
        try {
            answer = RecallCore.render(hits);
        } catch (Throwable t) {
            answer = "";
        }
        return new Recall.Result(answer, ids, cov, freshness());
    }

    @Override
    public BlockView readBlock(long blockId) {
        BlockRef b;
        try {
            b = source.readBlock(blockId);
        } catch (Throwable t) {
            return null;
        }
        if (b == null) return null;
        List<LineRef> lines = new ArrayList<>();
        if (b.text != null && !b.text.isEmpty()) {
            // BlockRef 只有拼接文本；按行拆开并保留 block 级位置信息
            String[] parts = b.text.split("\n");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) continue;
                lines.add(new LineRef(-1L, b.id, b.t0, parts[i], 0, 0, 0, b.shotRef));
            }
        }
        return new BlockView(b.id, b.t0, b.pkg, b.pageId, lines, b.shotRef);
    }

    @Override
    public FrameView readFrame(long frameId) {
        // 契约里的 frameId 当前映射到 blockId（实现限制，见 docs/INTERFACE.md 未决项）
        try {
            FrameRef f = source.readFrame(frameId);
            if (f == null) return FrameView.fail(FrameView.REASON_MISSING);
            if (f.blocked) {
                return FrameView.fail(FrameView.REASON_MASK_DENIED, f.error);
            }
            if (f.error != null && !f.error.isEmpty()) {
                return FrameView.fail(FrameView.REASON_UNAVAILABLE, f.error);
            }
            if (f.jpeg == null || f.jpeg.length == 0) {
                return FrameView.fail(FrameView.REASON_MISSING);
            }
            return FrameView.ok(f.jpeg);
        } catch (Throwable t) {
            return FrameView.fail(FrameView.REASON_UNAVAILABLE, String.valueOf(t.getMessage()));
        }
    }

    @Override
    public Freshness freshness() {
        long projected;
        try {
            projected = source.projectedUntil();
        } catch (Throwable t) {
            projected = 0L;
        }
        return Freshness.of(projected, System.currentTimeMillis());
    }

    private String day(long millis) {
        return dayFmt.format(new Date(millis));
    }
}
