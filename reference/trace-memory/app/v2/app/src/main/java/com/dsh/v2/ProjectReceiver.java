package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 手动触发全量投影补齐：adb shell am broadcast -a com.dsh.v2.PROJECT */
public final class ProjectReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.dsh.v2.PROJECT";

    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            int events = MemoryProjector.projectEvents(app);
            MemoryProjector.projectTraceBlocks(app);
            MemoryProjector.projectDays(app);
            android.util.Log.i("DSHL2", "project broadcast: events=" + events);
            // TODO: 每日一次可接 WorkManager/闹钟；本轮只保留手动广播
        }, "project").start();
    }
}
