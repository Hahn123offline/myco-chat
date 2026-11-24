package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机 / 应用升级后拉起前台服务，保证"用户没打开 App 也在记"。
 * 无障碍服务本身不能被 app 主动启用（系统限制），这里只保证宿主进程有前台服务、不被冻结。
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        try {
            Log.i("DSHHost", "BootReceiver onReceive action=" + action);
            HostService.ensureRunning(context);
            if (PipelineScheduler.enabled(context)) PipelineScheduler.scheduleNext(context);
        } catch (Throwable t) {
            Log.e("DSHHost", "BootReceiver failed: " + t.getClass().getSimpleName(), t);
        }
    }
}
