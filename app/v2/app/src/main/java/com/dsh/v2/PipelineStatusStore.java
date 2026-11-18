package com.dsh.v2;

import android.content.Context;
import android.content.SharedPreferences;

/** 调度/上次运行状态的持久展示数据（失败也必须可见）。 */
public final class PipelineStatusStore {

    private static final String PREFS = "pipeline_status";

    private PipelineStatusStore() { }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context ctx, boolean ok, String message) {
        prefs(ctx).edit()
                .putBoolean("last_ok", ok)
                .putString("last_msg", message == null ? "" : message)
                .putLong("last_at", System.currentTimeMillis())
                .apply();
    }

    public static String lastMessage(Context ctx) {
        return prefs(ctx).getString("last_msg", "");
    }

    public static boolean lastOk(Context ctx) {
        return prefs(ctx).getBoolean("last_ok", false);
    }

    public static long lastAt(Context ctx) {
        return prefs(ctx).getLong("last_at", 0);
    }

    /** 窗口：上次成功运行到现在；无记录 24h；夹在 1–48h。 */
    public static int hoursSinceLastSuccess(Context ctx) {
        try {
            ChainStore.ChainRow ignored;
            ChainStore store = new ChainStore(ctx);
            ChainStore.LastRun last = store.lastSuccess();
            if (last == null || last.finishedAt <= 0) return 24;
            long hours = (System.currentTimeMillis() - last.finishedAt + 3599_999L) / 3600_000L;
            return (int) Math.max(1, Math.min(48, hours));
        } catch (Throwable t) {
            return 24;
        }
    }

    public static void saveFromLastRun(Context ctx, boolean ok, String message) {
        save(ctx, ok, message);
    }

    /** 给界面看：上次运行 + 下次运行（不包含下次时间，由 Scheduler 提供）。 */
    public static String describeLast(Context ctx) {
        StringBuilder b = new StringBuilder();
        try {
            ChainStore store = new ChainStore(ctx);
            ChainStore.LastRun last = store.lastRunData();
            if (last != null) {
                String status = "done".equals(last.status) ? "成功"
                        : "failed".equals(last.status) ? "失败"
                        : "running".equals(last.status) ? "运行中" : last.status;
                b.append("上次运行：").append(status)
                        .append(" · ").append(last.chains).append(" 条链")
                        .append(" · ").append(last.bodies).append(" 个 body");
                long miss = Math.max(0, last.prompt - last.cache);
                b.append("\nToken：命中 ").append(compact(last.cache))
                        .append(" / 未命中 ").append(compact(miss))
                        .append(" / 输出 ").append(compact(last.completion))
                        .append(" · ").append(CostEstimator.cny(last.prompt, last.cache,
                                last.completion));
                if (!last.error.isEmpty()) b.append("\n失败原因：").append(last.error);
                int fails = store.consecutiveFailures();
                if (fails >= 3) b.append("\n⚠ 连续失败 ").append(fails).append(" 次，请检查 API Key");
            } else if (lastAt(ctx) > 0) {
                b.append("上次运行：").append(lastOk(ctx) ? "成功" : "失败");
            } else {
                b.append("上次运行：未运行");
            }
            if (!lastMessage(ctx).isEmpty()
                    && (!lastOk(ctx) || last == null || lastAt(ctx) > last.finishedAt)) {
                b.append("\n最近触发：").append(lastMessage(ctx));
            }
        } catch (Throwable t) {
            b.append("上次运行状态读取失败：").append(t.getClass().getSimpleName());
        }
        return b.toString();
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }
}
