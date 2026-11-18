package com.dsh.v2;

import android.content.Context;
import android.util.Log;

import com.dsh.agent.LlmCaller;
import com.dsh.agent.l2.L2Session;
import com.dsh.models.ModelResponse;
import com.dsh.trace.Trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * L2 调试入口：从 trace.db 取最近 N 条 a11y 块 → 跑一条 L2 会话 → dump 转录。
 * 不做 UI；MainActivity 按钮和 adb broadcast 都调这里。
 */
public final class L2Runner {

    public static final String TAG = "DSHL2";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "l2-runner");
        t.setDaemon(true);
        return t;
    });

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    private L2Runner() { }

    /** 异步跑；sessionId 固定包含时间戳，便于 adb pull。 */
    public static void runLast(Context app, int count, Callback cb) {
        IO.execute(() -> {
            boolean ok = false;
            String msg;
            try {
                if (!new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                        "evidence.db").exists()) {
                    if (cb != null) cb.onDone(false, "历史证据缺失，无法重算");
                    return;
                }
                TraceStore traceStore = new TraceStore(app.getApplicationContext());
                List<Trace> traces = traceStore.recentA11y(Math.max(1, Math.min(count, 16)));
                if (traces.isEmpty()) {
                    msg = "trace.db 没有 a11y 块";
                } else {
                    AgentDbStore store = new AgentDbStore(app.getApplicationContext());
                    String sessionId = "l2-" + new SimpleDateFormat("yyyyMMdd-HHmmss")
                            .format(new Date()) + "-n" + traces.size();
                    store.ensureSession(sessionId, L2Session.KIND,
                            "L2 extraction", "blocks=" + traces.size());
                    AndroidBlockSource source = new AndroidBlockSource(app, sessionId, traces);
                    double threshold = ExcludedRatio.DEFAULT_THRESHOLD;
                    try {
                        threshold = Double.parseDouble(TraceApp.get(app).configs()
                                .load("pipeline").getString("excludedRatioThreshold", "0.3"));
                    } catch (Throwable ignored) { }
                    ExcludedRatio.Decision decision = ExcludedRatio.decide(
                            source.excludedBlocks(), source.totalBlocks(), threshold);
                    if (decision == ExcludedRatio.Decision.NEEDS_LOCAL_MODEL) {
                        store.updateStatus(sessionId, "needs_local_model", 0);
                        File dump = store.dumpSession(sessionId);
                        ok = true;
                        msg = "needs_local_model（排除占比 "
                                + String.format("%.2f", ExcludedRatio.ratio(
                                source.excludedBlocks(), source.totalBlocks()))
                                + "，不生成 body） blocks=" + traces.size()
                                + " dump=" + dump.getAbsolutePath();
                        if (cb != null) cb.onDone(true, msg);
                        return;
                    }
                    L2Session session = new L2Session(sessionId, source);
                    TraceApp traceApp = TraceApp.get(app);
                    boolean hasKey = traceApp.secrets() != null
                            && !traceApp.secrets().keyFor("deepseek").isEmpty();
                    LlmCaller caller = hasKey
                            ? new com.dsh.agent.l2.RouterLlmCaller(traceApp.modelsModule().router())
                            : new MockLlmCaller();
                    if (!hasKey) Log.w(TAG, "no api key -> using MockLlmCaller (Android data/mosaic only)");
                    L2Session.Result r = session.run(sessionId, store, caller);
                    store.updateStatus(sessionId, r.done ? "done" : "failed", r.turns);
                    File dump = store.dumpSession(sessionId);
                    // 拼图尺寸审计（token 计费核对用）
                    File audit = new File(new File(app.getFilesDir(), "trace"),
                            "l2_mosaic_audit.log");
                    try (OutputStreamWriter w = new OutputStreamWriter(
                            new FileOutputStream(audit, true), StandardCharsets.UTF_8)) {
                        for (AndroidBlockSource.MosaicInfo mi : source.mosaics()) {
                            w.write(System.currentTimeMillis() + " session=" + sessionId
                                    + " " + mi.line() + "\n");
                            Log.i(TAG, "session=" + sessionId + " " + mi.line());
                        }
                    } catch (Throwable ignored) { }
                    ok = r.done;
                    msg = (r.done ? "done" : "failed: " + r.error)
                            + " blocks=" + traces.size() + " turns=" + r.turns
                            + " dump=" + dump.getAbsolutePath();
                    Log.i(TAG, msg);
                }
            } catch (Throwable t) {
                msg = "L2 跑失败: " + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : ": " + t.getMessage());
                Log.e(TAG, msg, t);
            }
            if (cb != null) {
                final boolean fok = ok;
                final String fmsg = msg;
                cb.onDone(fok, fmsg);
            }
        });
    }
}
