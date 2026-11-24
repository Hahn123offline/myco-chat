package com.dsh.v2;

import android.content.Context;
import android.util.Log;

import com.dsh.agent.l1.MergeGrouping;
import com.dsh.egress.EgressTier;
import com.dsh.models.Capability;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.Quality;
import com.dsh.models.SimpleTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 合并 pass（task-27）与父链 body 重写（task-29）。
 * 父链 body 400–800 字，禁止继承子链范围断言；子链任何字段都不改。
 */
public final class MergePass {

    public static final String TAG = "DSHL2";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "merge-pass");
        t.setDaemon(true);
        return t;
    });

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    private MergePass() { }

    public static void run(Context ctx, Callback cb) {
        IO.execute(() -> {
            try {
                TraceApp app = TraceApp.get(ctx);
                AgentDbStore agentDb = new AgentDbStore(ctx);
                new ChainStore(ctx).ensureKeysAndCleanup(agentDb);
                com.dsh.agent.LlmCaller judge = req -> app.modelsModule().router().execute(
                        new SimpleTask("merge.judge", Collections.singleton(Capability.JSON),
                                Quality.GOOD, EgressTier.WHITE), req);
                com.dsh.agent.LlmCaller bodyCaller = req -> app.modelsModule().router().execute(
                        new SimpleTask("l1.parent", Collections.singleton(Capability.JSON),
                                Quality.GOOD, EgressTier.WHITE), req);
                CrossWindowMerge.Result r = CrossWindowMerge.run(ctx, judge, bodyCaller);
                File dump = new File(new File(ctx.getFilesDir(), "trace"), "merge_summary.txt");
                try (OutputStreamWriter w = new OutputStreamWriter(
                        new FileOutputStream(dump), StandardCharsets.UTF_8)) {
                    w.write(r.report);
                }
                // 每轮合并成本/命中统计也追加到 l0l1_summary
                File summary = new File(new File(ctx.getFilesDir(), "trace"), "l0l1_summary.txt");
                try (OutputStreamWriter w = new OutputStreamWriter(
                        new FileOutputStream(summary, true), StandardCharsets.UTF_8)) {
                    w.write("merge " + r.report.replace('\n', ' ') + "\n");
                } catch (Throwable ignored) { }
                if (cb != null) cb.onDone(r.ok, r.report.replace('\n', ' ')
                        + " dump=" + dump.getAbsolutePath());
            } catch (Throwable t) {
                Log.e(TAG, "cross-window merge failed", t);
                if (cb != null) cb.onDone(false, "merge failed: " + t.getClass().getSimpleName());
            }
        });
    }

    /** 只重写已有父链 body，不重新合并、不产生新父链。 */
    public static void repairParents(Context ctx, Callback cb) {
        IO.execute(() -> merge(ctx, true, cb));
    }

    private static void merge(Context ctx, boolean repairOnly, Callback cb) {
        StringBuilder report = new StringBuilder();
        boolean ok = false;
        String msg;
        int parents = 0, groups = 0, skipped = 0, repaired = 0;
        try {
            TraceApp app = TraceApp.get(ctx);
            ChainStore chains = new ChainStore(ctx);
            chains.ensureKeysAndCleanup(new AgentDbStore(ctx));
            String key = app.secrets() == null ? "" : app.secrets().keyFor("deepseek");
            boolean hasKey = key != null && !key.isEmpty();
            if (!hasKey) {
                msg = "无 API key，合并/父链重写需要模型";
            } else {
                com.dsh.agent.LlmCaller caller = req -> app.modelsModule().router().execute(
                        new SimpleTask("l1.merge", Collections.singleton(Capability.JSON),
                                Quality.GOOD, EgressTier.WHITE), req);
                com.dsh.agent.LlmCaller bodyCaller = req -> app.modelsModule().router().execute(
                        new SimpleTask("l1.parent", Collections.singleton(Capability.JSON),
                                Quality.GOOD, EgressTier.WHITE), req);
                if (repairOnly) {
                    for (ChainStore.ChainRow parent : chains.parents()) {
                        List<ChainStore.ChainRow> rows = chains.childrenOfParent(parent.id);
                        if (rows.size() < 2) continue;
                        Sanitized sanitized = sanitizeRows(ctx, rows);
                        rows = sanitized.rows;
                        MergeGrouping.Group g = new MergeGrouping.Group(
                                indexes(rows.size()), parent.title, "repair");
                        BodyResult br = generateBody(bodyCaller, rows, g,
                                sanitized.spans, sanitized.kinds);
                        report.append("parent#").append(parent.id).append(' ')
                                .append(parent.title).append(" children=").append(rows.size())
                                .append(" oldLen=").append(parent.body.length());
                        if (br.body == null) {
                            skipped++;
                            report.append(" SKIP ").append(br.reason).append('\n');
                            continue;
                        }
                        String keys = keysOf(rows);
                        chains.updateParentBody(parent.id, br.body, keys,
                                ChainOutcome.inferFromBody(br.body).dbValue);
                        repaired++;
                        report.append(" newLen=").append(br.body.length())
                                .append(" keys=").append(keys).append('\n');
                    }
                    ok = repaired > 0 || skipped == 0;
                    File dump = new File(new File(ctx.getFilesDir(), "trace"),
                            "parent_body_repair.txt");
                    try (OutputStreamWriter w = new OutputStreamWriter(
                            new FileOutputStream(dump), StandardCharsets.UTF_8)) {
                        w.write(report.toString());
                    }
                    msg = "repaired=" + repaired + " skipped=" + skipped
                            + " dump=" + dump.getAbsolutePath();
                } else {
                    java.util.Map<String, List<ChainStore.ChainRow>> byChunk =
                            new java.util.LinkedHashMap<>();
                    android.database.Cursor c = chains.all();
                    while (c.moveToNext()) {
                        int levelIdx = c.getColumnIndex("level");
                        int ckIdx = c.getColumnIndex("chunk_key");
                        if (c.getInt(levelIdx) != 0) continue;
                        String ck = c.isNull(ckIdx) ? null : c.getString(ckIdx);
                        if (ck == null) continue;
                        ChainStore.ChainRow row = new ChainStore.ChainRow(
                                c.getLong(c.getColumnIndex("id")),
                                c.getString(c.getColumnIndex("title")),
                                c.getString(c.getColumnIndex("body")),
                                c.getLong(c.getColumnIndex("t0")),
                                c.getLong(c.getColumnIndex("t1")),
                                c.getInt(c.getColumnIndex("n_blocks")),
                                c.getString(c.getColumnIndex("block_ids")),
                                c.getString(c.getColumnIndex("key_block_ids")), 0, 0);
                        byChunk.computeIfAbsent(ck, k -> new ArrayList<>()).add(row);
                    }
                    c.close();
                    for (java.util.Map.Entry<String, List<ChainStore.ChainRow>> e
                            : byChunk.entrySet()) {
                        Sanitized sanitized = sanitizeRows(ctx, e.getValue());
                        List<ChainStore.ChainRow> rows = sanitized.rows;
                        if (rows.size() < 2) continue;
                        List<MergeGrouping.Item> items = items(rows);
                        MergeGrouping.Result mg = MergeGrouping.group(items, caller);
                        report.append("chunk=").append(e.getKey())
                                .append(" children=").append(rows.size())
                                .append(" groups=").append(mg.groups.size())
                                .append(mg.reason.isEmpty() ? "" : " reason=" + mg.reason)
                                .append('\n');
                        for (MergeGrouping.Group g : mg.groups) {
                            groups++;
                            BodyResult br = generateBody(bodyCaller, rows, g,
                                    sanitized.spans, sanitized.kinds);
                            if (br.body == null) {
                                skipped++;
                                report.append("  SKIP group ").append(g.title)
                                        .append(" ").append(br.reason).append('\n');
                                continue;
                            }
                            List<ChainStore.ChainRow> chosen = new ArrayList<>();
                            for (Integer i : g.children) chosen.add(rows.get(i));
                            String childIds = ids(chosen);
                            String blockIds = unionBlocks(chosen);
                            long t0 = Long.MAX_VALUE, t1 = Long.MIN_VALUE;
                            for (ChainStore.ChainRow r : chosen) {
                                t0 = Math.min(t0, r.t0);
                                t1 = Math.max(t1, r.t1);
                            }
                            long pid = chains.insertParent(e.getKey(), g.title, childIds,
                                    blockIds, t0, t1, g.why);
                            if (pid < 0) {
                                skipped++;
                                report.append("  SKIP parent exists ").append(g.title).append('\n');
                                continue;
                            }
                            chains.updateParentBody(pid, br.body, keysOf(chosen),
                                    ChainOutcome.inferFromBody(br.body).dbValue);
                            for (ChainStore.ChainRow r : chosen) chains.setParent(r.id, pid);
                            parents++;
                            report.append("  parent#").append(pid).append(' ')
                                    .append(g.title).append(" children=")
                                    .append(g.children.size()).append(" len=")
                                    .append(br.body.length()).append('\n');
                        }
                    }
                    File dump = new File(new File(ctx.getFilesDir(), "trace"),
                            "merge_summary.txt");
                    try (OutputStreamWriter w = new OutputStreamWriter(
                            new FileOutputStream(dump), StandardCharsets.UTF_8)) {
                        w.write(report.toString());
                    }
                    ok = true;
                    msg = "groups=" + groups + " parents=" + parents + " skipped=" + skipped
                            + " dump=" + dump.getAbsolutePath();
                }
            }
        } catch (Throwable t) {
            msg = "merge failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, msg, t);
        }
        if (cb != null) cb.onDone(ok, msg);
    }

    static final class BodyResult {
        final String body;
        final String reason;
        BodyResult(String body, String reason) {
            this.body = body;
            this.reason = reason == null ? "" : reason;
        }
    }

    static BodyResult generateBody(com.dsh.agent.LlmCaller caller,
                                           List<ChainStore.ChainRow> rows,
                                           MergeGrouping.Group g, int maskerSpans,
                                           String maskerKinds) {
        List<MergeGrouping.Item> items = items(rows);
        String last = "";
        int retryChars = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ModelResponse r = caller.call(ModelRequest.text(
                        ParentBodySpec.SYSTEM,
                        ParentBodySpec.userPrompt(items, g, retryChars))
                        .withProvenance(java.util.Collections.emptyList(), com.dsh.egress.EgressStage.DERIVED).withThinking("disabled")
                        .withMasking(maskerSpans, maskerKinds));
                if (r == null || !r.isOk()) {
                    last = "模型失败: " + (r == null ? "null" : r.error);
                    continue;
                }
                String body = ParentBodySpec.normalize(r.text);
                String err = ParentBodySpec.validate(body);
                if (err.isEmpty()) return new BodyResult(body, "");
                if (err.startsWith("太长")) {
                    // 绝不截断：第一次要求压缩，第二次仍超则保留全文并标记
                    if (attempt == 0) {
                        retryChars = body.length();
                        last = err;
                        continue;
                    }
                    Log.w(TAG, "parent body over_length=" + body.length()
                            + " accepted without truncation");
                    return new BodyResult(body, "over_length=" + body.length());
                }
                last = err;
            } catch (Throwable t) {
                last = "异常: " + t.getClass().getSimpleName();
            }
        }
        return new BodyResult(null, last);
    }

    private static final class Sanitized {
        final List<ChainStore.ChainRow> rows;
        final int spans;
        final String kinds;
        Sanitized(List<ChainStore.ChainRow> rows, int spans, String kinds) {
            this.rows = rows; this.spans = spans; this.kinds = kinds;
        }
    }

    /** 旧 body 也可能含敏感串：父链/合并 pass 的输入全部过 Masker + guard 复检。 */
    private static Sanitized sanitizeRows(Context ctx,
                                          List<ChainStore.ChainRow> rows) {
        List<ChainStore.ChainRow> out = new ArrayList<>();
        int spans = 0;
        StringBuilder kinds = new StringBuilder();
        try {
            TraceStore store = new TraceStore(ctx);
            for (ChainStore.ChainRow r : rows) {
                boolean excluded = false;
                List<Long> ids = new ArrayList<>();
                for (String s : r.blockIds.split(",")) {
                    try { ids.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
                }
                if (!ids.isEmpty()) {
                    for (com.dsh.trace.Trace t : store.tracesByIds(ids)) {
                        if (com.dsh.egress.AppExclusionPolicy.isExcluded(t.pkg)) {
                            excluded = true;
                            break;
                        }
                    }
                }
                String safeBody;
                if (excluded) {
                    safeBody = "[APP_EXCLUDED]";
                } else {
                    com.dsh.masker.MaskedBlock mb = MaskerBridge.maskText(r.body);
                    safeBody = MaskerBridge.guardPass(mb.text)
                            ? mb.text : "[BLOCKED_BY_EGRESS]";
                    spans += mb.spans.size();
                    for (com.dsh.masker.MaskSpan s : mb.spans) {
                        if (kinds.indexOf(s.kind) < 0) kinds.append(s.kind).append(',');
                    }
                }
                out.add(new ChainStore.ChainRow(r.id, r.title, safeBody, r.t0, r.t1,
                        r.nBlocks, r.blockIds, r.keyBlockIds, r.parentId, r.level));
            }
        } catch (Throwable t) {
            return new Sanitized(rows, 0, "");
        }
        return new Sanitized(out, spans, kinds.toString());
    }

    private static List<MergeGrouping.Item> items(List<ChainStore.ChainRow> rows) {        List<MergeGrouping.Item> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ChainStore.ChainRow r = rows.get(i);
            out.add(new MergeGrouping.Item(i, r.title, r.body, r.t0, r.t1, r.nBlocks));
        }
        return out;
    }

    private static List<Integer> indexes(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    private static String keysOf(List<ChainStore.ChainRow> rows) {
        List<String> keys = new ArrayList<>();
        for (ChainStore.ChainRow r : rows) keys.add(r.keyBlockIds);
        return ParentBodySpec.pickKeyIds(keys);
    }

    private static String ids(List<ChainStore.ChainRow> rows) {
        StringBuilder b = new StringBuilder();
        for (ChainStore.ChainRow r : rows) {
            if (b.length() > 0) b.append(',');
            b.append(r.id);
        }
        return b.toString();
    }

    private static String unionBlocks(List<ChainStore.ChainRow> rows) {
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        for (ChainStore.ChainRow r : rows) {
            for (String s : r.blockIds.split(",")) {
                try { ids.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
            }
        }
        StringBuilder b = new StringBuilder();
        for (Long id : ids) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return b.toString();
    }
}
