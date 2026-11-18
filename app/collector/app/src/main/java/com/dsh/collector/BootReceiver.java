package com.dsh.collector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启：拉起宿主前台服务。 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            ctx.startForegroundService(new Intent(ctx, HostService.class));
        } catch (Throwable ignored) { }
    }
}
