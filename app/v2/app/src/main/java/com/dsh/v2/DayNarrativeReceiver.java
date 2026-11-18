package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** adb: am broadcast -a com.dsh.v2.action.RUN_DAY [-e day 2026-11-17] */
public final class DayNarrativeReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.RUN_DAY";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        final Context app = context.getApplicationContext();
        String day = intent.getStringExtra("day");
        if (day == null || day.isEmpty()) {
            new Thread(() -> DayNarrativeRunner.runYesterday(app, (ok, msg) ->
                    Log.i(DayNarrativeRunner.TAG, "run_day done=" + ok + " " + msg)),
                    "day-run").start();
            return;
        }
        new Thread(() -> {
            try {
                String[] p = day.split("-");
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1,
                        Integer.parseInt(p[2]), 0, 0, 0);
                DayNarrativeRunner.run(app, c, (ok, msg) ->
                        Log.i(DayNarrativeRunner.TAG, "run_day done=" + ok + " " + msg));
            } catch (Throwable t) {
                Log.e(DayNarrativeRunner.TAG, "run_day bad day", t);
            }
        }, "day-run").start();
    }
}
