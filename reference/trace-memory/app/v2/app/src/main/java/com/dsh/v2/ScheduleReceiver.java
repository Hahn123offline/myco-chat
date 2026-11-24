package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * adb 设置闹钟（UI 之外的自动化入口）：
 * am broadcast -a com.dsh.v2.action.SET_SCHEDULE -p com.dsh.v2 --ei hour 2 --ei minute 0 [--ez conditions true]
 */
public final class ScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.SET_SCHEDULE";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        int hour = intent.getIntExtra("hour", PipelineScheduler.hour(context));
        int minute = intent.getIntExtra("minute", PipelineScheduler.minute(context));
        boolean conditions = intent.getBooleanExtra("conditions",
                PipelineScheduler.requireConditions(context));
        boolean enabled = intent.getBooleanExtra("enabled", true);
        PipelineScheduler.save(context, enabled, hour, minute, conditions);
        Log.i(PipelineScheduler.TAG, "schedule enabled=" + enabled + " " + hour + ":"
                + minute + " next=" + PipelineScheduler.nextAt(context));
    }
}
