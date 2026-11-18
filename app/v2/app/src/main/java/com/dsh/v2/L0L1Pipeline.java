package com.dsh.v2;

import android.content.Context;
import android.util.Log;

import com.dsh.agent.ChainKey;
import com.dsh.egress.AppExclusionPolicy;
import com.dsh.agent.l1.L1Grouping;
import com.dsh.agent.l1.L1Session;
import com.dsh.agent.l1.L1Subchain;
import com.dsh.agent.l2.L2Session;
import com.dsh.egress.EgressTier;
import com.dsh.models.Capability;
import com.dsh.models.Quality;
import com.dsh.models.SimpleTask;
import com.dsh.trace.L0Chunker;
import com.dsh.trace.Trace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设备端 L0 → L1 → L2 流水线。
 *
 * 幂等：
 *  - chain dedup_key = sha1(sorted block_ids)，重复链跳过不重跑 L2
 *  - L0 chunk_key：已 done 的块直接跳过，不调模型（省钱）
 *  - pipeline_runs 执行租约：2 小时内 running 拒绝并发，超时视为僵尸可抢占
 */
public final class L0L1Pipeline {

    public static final String TAG = "DSHL2";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "l0l1-pipeline");
        t.setDaemon(true);
        return t;
    });

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    private L0L1Pipeline() { }

    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static boolean isRunning() { return RUNNING.get(); }

    public static void run(Context ctx, int hours, Callback cb) {
        run(ctx, hours, false, cb);
    }

    public static void run(Context ctx, int hours, boolean force, Callback cb) {
        IO.execute(() -> runBlocking(ctx, hours, force, cb));
    }

    /** 必须在后台线程调用；广播接收器用它，保证 onReceive 立即返回。 */
    public static void runBlocking(Context ctx, int hours, boolean force, Callback cb) {
        if (!RUNNING.compareAndSet(false, true)) {
            if (cb != null) cb.onDone(false, "拒绝启动：本进程已有流水线在运行");
            return;
        }
        try {
            runBody(ctx, hours, force, cb);
        } finally {
            RUNNING.set(false);
        }
    }

    private static void runBody(Context ctx, int hours, boolean force, Callback cb) {
            boolean ok = false;
            String msg;
            StringBuilder report = new StringBuilder();
            String runId = "run-" + new SimpleDateFormat("yyyyMMdd-HHmmss")
                    .format(new Date()) + "-" + Math.max(1, hours) + "h";
            ChainStore chains = new ChainStore(ctx);
            AgentDbStore agentDb = new AgentDbStore(ctx);
            boolean leased = false;
            long usageStart = usageFile(ctx).length();
            int chunkCount = 0, chainCount = 0, bodyOk = 0, fallbacks = 0,
                    skippedChunks = 0, skippedChains = 0;
            int fullChains = 0, partialChains = 0, needsChains = 0, maskingChains = 0;
            double ratioThreshold = ExcludedRatio.DEFAULT_THRESHOLD;
            String firstError = "";
            try {
                TraceApp app = TraceApp.get(ctx);
                TraceStore traceStore = new TraceStore(ctx);
                try {
                    String ts = app.configs().load("pipeline")
                            .getString("excludedRatioThreshold", "0.3");
                    ratioThreshold = Double.parseDouble(ts.trim());
                } catch (Throwable ignored) { }
                chains.ensureKeysAndCleanup(agentDb);
                int dupSuperseded = chains.supersedeDuplicateChains();
                chains.releaseSupersededDedupKeys();
                if (dupSuperseded > 0) {
                    report.append("cleanup superseded duplicates=")
                            .append(dupSuperseded).append('\n');
                }
                if (force) {
                    chains.forceReleaseRunning("force run requested");
                }
                long since = System.currentTimeMillis() - Math.max(1, hours) * 3600_000L;
                List<Trace> traces = traceStore.recentA11ySince(since, 5000);
                long from = traces.isEmpty() ? since : traces.get(0).t0;
                long to = traces.isEmpty() ? System.currentTimeMillis()
                        : traces.get(traces.size() - 1).t1;
                if (!chains.acquireLease(runId, hours, from, to)) {
                    msg = "拒绝启动：已有流水线在运行（2 小时内 running 租约）";
                    Log.w(TAG, msg);
                } else {
                    leased = true;
                    report.append("run=").append(runId)
                            .append(" hours=").append(hours)
                            .append(" blocks=").append(traces.size()).append('\n');
                    if (traces.isEmpty()) {
                        msg = "窗口内无新块，已跳过（不是失败）";
                        ok = true;
                        chains.finishLease(runId, "done", 0, 0, 0, 0, 0, 0, "");
                    } else {
                        List<L0Chunker.Session> sessions = L0Chunker.sessions(traces);
                        List<L0Chunker.Chunk> chunks = L0Chunker.chunks(sessions);
                        report.append("L0 chunks=").append(chunks.size())
                                .append(" sessions=").append(sessions.size()).append('\n');
                        String key = app.secrets() == null ? "" : app.secrets().keyFor("deepseek");
                        boolean hasKey = key != null && !key.isEmpty();
                        for (int ci = 0; ci < chunks.size(); ci++) {
                            chunkCount++;
                            L0Chunker.Chunk chunk = chunks.get(ci);
                            String chunkKey = ChainKey.of(chunk.blockIds());
                            if (chains.chunkDone(chunkKey)) {
                                skippedChunks++;
                                report.append("chunk#").append(ci)
                                        .append(" SKIP IDEMPOTENT done\n");
                                continue;
                            }
                            int superseded = chains.supersedeForBlocks(chunk.blockIds());
                            if (superseded > 0) {
                                report.append("chunk#").append(ci)
                                        .append(" superseded=").append(superseded).append('\n');
                            }
                            List<L0Chunker.Session> cs = chunk.sessions;
                            List<L1Session> ls = new ArrayList<>();
                            for (int i = 0; i < cs.size(); i++) {
                                L0Chunker.Session s = cs.get(i);
                                String head = s.traces.get(0).text == null ? ""
                                        : s.traces.get(0).text;
                                String tail = s.traces.get(s.traces.size() - 1).text == null
                                        ? "" : s.traces.get(s.traces.size() - 1).text;
                                if (AppExclusionPolicy.isExcluded(s.pkg)) {
                                    head = "[APP_EXCLUDED]";
                                    tail = "[APP_EXCLUDED]";
                                }
                                ls.add(new L1Session(i + 1, s.t0, s.t1, s.pkg, s.size(),
                                        head, tail));
                            }
                            com.dsh.agent.LlmCaller l1Caller = req -> {
                                if (!hasKey) {
                                    return com.dsh.models.ModelResponse.error("no api key", "l1");
                                }
                                return app.modelsModule().router().execute(
                                        new SimpleTask("l1.group",
                                                Collections.singleton(Capability.JSON),
                                                Quality.GOOD, EgressTier.WHITE), req);
                            };
                            L1Grouping.Result l1 = L1Grouping.group(ls, l1Caller);
                            if (l1.fallback) fallbacks++;
                            report.append("chunk#").append(ci).append(" sessions=")
                                    .append(cs.size()).append(" subchains=")
                                    .append(l1.subchains.size())
                                    .append(l1.fallback ? " FALLBACK=" + l1.reason : "")
                                    .append('\n');
                            boolean chunkFailed = false;
                            for (L1Subchain sc : l1.subchains) {
                                List<Trace> subTraces = new ArrayList<>();
                                for (Integer idx : sc.sessionIndexes) {
                                    if (idx >= 1 && idx <= cs.size()) {
                                        subTraces.addAll(cs.get(idx - 1).traces);
                                    }
                                }
                                if (subTraces.isEmpty()) continue;
                                long t0 = subTraces.get(0).t0;
                                long t1 = subTraces.get(subTraces.size() - 1).t1;
                                String ids = ids(subTraces);
                                String dedup = ChainKey.ofBlockCsv(ids);
                                long chainId = chains.insertIfAbsent(ci, chunkKey, sc.title,
                                        sc.sessionIndexes.toString(), ids, t0, t1,
                                        subTraces.size());
                                if (chainId < 0) {
                                    // 链级幂等：保留第一次的 body，不重复调模型
                                    chains.assignChunkKey(dedup, chunkKey);
                                    String st = chains.statusByDedup(dedup);
                                    if (!"done".equals(st)) chunkFailed = true;
                                    skippedChains++;
                                    report.append("  SKIP duplicate chain ").append(sc.title)
                                            .append(" status=").append(st).append('\n');
                                    continue;
                                }
                                chainCount++;
                                try {
                                    AndroidBlockSource src = new AndroidBlockSource(ctx,
                                            "chain-" + chainId, subTraces);
                                    long ex = src.excludedBlocks();
                                    long total = src.totalBlocks();
                                    double ratio = ExcludedRatio.ratio(ex, total);
                                    ExcludedRatio.Decision decision =
                                            ExcludedRatio.decide(ex, total, ratioThreshold);
                                    report.append("  classify chain#").append(chainId)
                                            .append(" excluded=").append(ex).append('/')
                                            .append(total).append(" ratio=")
                                            .append(String.format("%.2f", ratio))
                                            .append(" -> ").append(decision).append('\n');
                                    if (decision == ExcludedRatio.Decision.NEEDS_LOCAL_MODEL) {
                                        chains.markNeedsLocalModel(chainId, "excluded");
                                        needsChains++;
                                        String cat = AppExclusionPolicy.category(
                                                subTraces.get(0).pkg);
                                        report.append("  ").append(TimelinePlaceholder.line(
                                                        subTraces.get(0).t0,
                                                        subTraces.get(subTraces.size() - 1).t1,
                                                        cat))
                                                .append("\n");
                                        continue;
                                    }
                                    // REDACT_THEN_CLOUD：Masker 接入前绝不出网
                                    if (src.requiresMasking()) {
                                        chains.markNeedsMasker(chainId);
                                        maskingChains++;
                                        report.append("  chain#").append(chainId).append(' ')
                                                .append(sc.title)
                                                .append(" blocks=").append(subTraces.size())
                                                .append(" NEEDS_MASKER（Masker 前不出网）\n");
                                        continue;
                                    }
                                    String textSource = src.maskingBlocks() > 0
                                            ? "masked" : ExcludedRatio.textSource(decision);
                                    if (decision == ExcludedRatio.Decision.PARTIAL) {
                                        partialChains++;
                                    } else {
                                        fullChains++;
                                    }
                                    AgentDbStore store = new AgentDbStore(ctx);
                                    String sid = "l2chain-" + chainId + "-"
                                            + System.currentTimeMillis();
                                    store.ensureSession(sid, L2Session.KIND, "L2 chain",
                                            "chain_id=" + chainId + " title=" + sc.title);
                                    L2Session session = new L2Session("chain-" + chainId, src);
                                    com.dsh.agent.LlmCaller l2Caller = hasKey
                                            ? new com.dsh.agent.l2.RouterLlmCaller(
                                            app.modelsModule().router())
                                            : new MockLlmCaller();
                                    L2Session.Result r = session.run(sid, store, l2Caller);
                                    store.updateStatus(sid, r.done ? "done" : "failed", r.turns);
                                    String body = r.submission == null ? "" : r.submission.body;
                                    String keys = r.submission == null ? ""
                                            : r.submission.keyBlockIds.toString();
                                    chains.updateBody(chainId, body, keys, r.done, textSource);
                                    if (r.submission != null) {
                                        chains.setGapDeclared(chainId, r.submission.gapDeclared);
                                        chains.setOutcome(chainId, r.submission.outcome);
                                    }
                                    if (r.done && !body.isEmpty()) bodyOk++;
                                    if (!r.done) {
                                        chunkFailed = true;
                                        if (firstError.isEmpty()) firstError = r.error;
                                    }
                                    report.append("  chain#").append(chainId).append(' ')
                                            .append(sc.title).append(" blocks=")
                                            .append(subTraces.size()).append(" body=")
                                            .append(body.isEmpty() ? 0 : body.length())
                                            .append(r.done ? "" : " ERR=" + r.error).append('\n');
                                } catch (Throwable t) {
                                    chains.updateBody(chainId, "", "", false);
                                    chunkFailed = true;
                                    if (firstError.isEmpty()) {
                                        firstError = t.getClass().getSimpleName() + ": "
                                                + t.getMessage();
                                    }
                                    report.append("  chain#").append(chainId)
                                            .append(" ERROR ")
                                            .append(t.getClass().getSimpleName()).append('\n');
                                }
                            }
                            chains.markChunk(chunkKey, chunk.t0, chunk.t1,
                                    ids(chunk.traces), chunkFailed ? "partial" : "done");
                        }
                        report.append("chains=").append(chainCount)
                                .append(" bodyOk=").append(bodyOk)
                                .append(" l1Fallbacks=").append(fallbacks)
                                .append(" skippedChunks=").append(skippedChunks)
                                .append(" skippedChains=").append(skippedChains)
                                .append(" full=").append(fullChains)
                                .append(" partial=").append(partialChains)
                                .append(" needsLocal=").append(needsChains)
                                .append(" needsMasker=").append(maskingChains)
                                .append(" threshold=").append(ratioThreshold).append('\n');
                        report.append(usageSummary(ctx, usageStart));
                        long[] totals = usageTotals(ctx, usageStart);
                        ok = bodyOk > 0 || chainCount == 0
                                || (maskingChains > 0 && firstError.isEmpty());
                        chains.finishLease(runId, ok ? "done" : "failed", chunkCount,
                                chainCount, bodyOk, totals[0], totals[1], totals[2],
                                ok ? "" : (firstError.isEmpty() ? "部分子链失败" : firstError));
                        File dump = new File(new File(ctx.getFilesDir(), "trace"),
                                "l0l1_summary.txt");
                        try (OutputStreamWriter w = new OutputStreamWriter(
                                new FileOutputStream(dump), StandardCharsets.UTF_8)) {
                            w.write(report.toString());
                        }
                        msg = "run=" + runId + " chunks=" + chunkCount
                                + " skippedChunks=" + skippedChunks
                                + " chains=" + chainCount + " bodyOk=" + bodyOk
                                + (ok ? "" : " error=" + (firstError.isEmpty()
                                ? "部分子链失败" : firstError))
                                + " dump=" + dump.getAbsolutePath();
                    }
                }
            } catch (Throwable t) {
                msg = "pipeline failed: " + t.getClass().getSimpleName()
                        + ": " + t.getMessage();
                Log.e(TAG, msg, t);
                if (leased) {
                    try {
                        chains.finishLease(runId, "failed", -1, -1, -1, 0, 0, 0, msg);
                    } catch (Throwable ignored) { }
                }
            }
            if (cb != null) {
                final boolean f = ok;
                final String m = msg;
                cb.onDone(f, m);
            }
    }

    private static File usageFile(Context ctx) {
        return new File(new File(ctx.getFilesDir(), "host/audit"), "usage.log");
    }

    private static long[] usageTotals(Context ctx, long offset) {
        File f = usageFile(ctx);
        long prompt = 0, completion = 0, cache = 0;
        if (!f.exists()) return new long[]{0, 0, 0};
        try (FileInputStream in = new FileInputStream(f)) {
            long skipped = in.skip(offset);
            byte[] buf = new byte[(int) Math.max(1, f.length() - skipped)];
            int n = in.read(buf);
            String text = n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (!line.startsWith("usage task=")) continue;
                prompt += field(line, "prompt_tokens=");
                completion += field(line, "completion_tokens=");
                cache += field(line, "prompt_cache_hit_tokens=");
            }
        } catch (Throwable ignored) { }
        return new long[]{prompt, completion, cache};
    }

    private static String usageSummary(Context ctx, long offset) {
        File f = usageFile(ctx);
        if (!f.exists()) return "usage: none\n";
        StringBuilder out = new StringBuilder("usage (since pipeline start):\n");
        int l1 = 0, l2 = 0, calls = 0;
        long prompt = 0, completion = 0, cache = 0;
        try (FileInputStream in = new FileInputStream(f)) {
            long skipped = in.skip(offset);
            byte[] buf = new byte[(int) Math.max(1, f.length() - skipped)];
            int n = in.read(buf);
            String text = n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                if (!line.startsWith("usage task=")) continue;
                calls++;
                if (line.contains("task=l1.group")) l1++;
                if (line.contains("task=l2.extract")) l2++;
                prompt += field(line, "prompt_tokens=");
                completion += field(line, "completion_tokens=");
                cache += field(line, "prompt_cache_hit_tokens=");
            }
        } catch (Throwable ignored) { }
        double rate = prompt == 0 ? 0 : (100.0 * cache / prompt);
        out.append("calls=").append(calls).append(" l1=").append(l1)
                .append(" l2=").append(l2)
                .append(" prompt_tokens=").append(prompt)
                .append(" completion_tokens=").append(completion)
                .append(" cache_hit=").append(cache)
                .append(" cache_rate=").append(String.format("%.1f%%", rate)).append('\n');
        return out.toString();
    }

    private static long field(String line, String key) {
        int i = line.indexOf(key);
        if (i < 0) return 0;
        int j = i + key.length();
        int k = j;
        while (k < line.length() && Character.isDigit(line.charAt(k))) k++;
        try { return Long.parseLong(line.substring(j, k)); } catch (Throwable t) { return 0; }
    }

    private static String ids(List<Trace> traces) {
        StringBuilder b = new StringBuilder();
        for (Trace t : traces) {
            if (b.length() > 0) b.append(',');
            b.append(t.id);
        }
        return b.toString();
    }
}
