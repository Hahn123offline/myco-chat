package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** adb: am broadcast -a com.dsh.v2.action.RUN_PIPELINE -p com.dsh.v2 [--ei hours 6] [--ez force true] */
public final class PipelineReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.RUN_PIPELINE";

    /** 广播只负责收包，重活全部在专用后台线程；不占用主线程。 */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pipeline-receiver");
        t.setDaemon(true);
        return t;
    });

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        final int hours = intent.getIntExtra("hours", 2);
        final boolean force = intent.getBooleanExtra("force", false);
        final Context app = context.getApplicationContext();
        // 不持有 goAsync：广播必须在几十毫秒内返回，长任务由前台 HostService 保活
        EXEC.execute(() -> {
            try {
                L0L1Pipeline.runBlocking(app, hours, force, (ok, message) -> {
                    PipelineStatusStore.save(app, ok, message);
                    PipelineNotifier.notifyResult(app, ok, message);
                    Log.i(L0L1Pipeline.TAG, "pipeline done=" + ok + " " + message);
                });
            } catch (Throwable t) {
                Log.e(L0L1Pipeline.TAG, "pipeline receiver failed", t);
            }
        });
    }
}
