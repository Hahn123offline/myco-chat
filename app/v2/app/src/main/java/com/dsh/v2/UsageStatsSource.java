package com.dsh.v2;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import com.dsh.usage.UsageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * UsageStatsManager 封装：只读「前台 app 切换」事件，供 :usage 降级采样使用。
 *
 * 权限：PACKAGE_USAGE_STATS（用户在「使用情况访问」里手动授权）。
 * 拿不到授权时 queryEvents 返回空/受限，调用方必须先用 {@link #granted} 判断并如实上报。
 */
public final class UsageStatsSource {

    private UsageStatsSource() { }

    public static boolean granted(Context app) {
        try {
            AppOpsManager ops = (AppOpsManager) app.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), app.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static List<UsageEvent> events(Context app, long from, long to) {
        List<UsageEvent> out = new ArrayList<>();
        if (to <= from) return out;
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    app.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return out;
            UsageEvents events = usm.queryEvents(from, to);
            if (events == null) return out;
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                String pkg = e.getPackageName();
                long ts = e.getTimeStamp();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    out.add(new UsageEvent(pkg, ts, true));
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    out.add(new UsageEvent(pkg, ts, false));
                }
            }
        } catch (Throwable t) {
            // 未授权/系统异常：返回空，调用方会如实上报 granted=false
        }
        return out;
    }
}
