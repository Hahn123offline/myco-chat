package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** adb 调试入口：am broadcast -a com.dsh.v2.action.L2_RUN --ei n 8 */
public final class L2DebugReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.L2_RUN";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        final PendingResult pending = goAsync();
        int n = intent.getIntExtra("n", 8);
        L2Runner.runLast(context.getApplicationContext(), n, (ok, message) -> {
            Log.i(L2Runner.TAG, "broadcast run done=" + ok + " " + message);
            try { pending.finish(); } catch (Throwable ignored) { }
        });
    }
}
