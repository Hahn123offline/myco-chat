package com.dsh.v2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.time.ZoneId;

/** 每日闹钟调度（AlarmManager；exact 权限拿不到降级为近似）。 */
public final class PipelineScheduler {

    public static final String TAG = "DSHL2";
    public static final String ACTION = "com.dsh.v2.action.PIPELINE_ALARM";
    private static final String PREFS = "pipeline_schedule";
    private static final int REQ = 2002;

    private PipelineScheduler() { }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context ctx) { return prefs(ctx).getBoolean("enabled", true); }

    public static int hour(Context ctx) { return prefs(ctx).getInt("hour", PipelineSchedule.DEFAULT_HOUR); }

    public static int minute(Context ctx) { return prefs(ctx).getInt("minute", PipelineSchedule.DEFAULT_MINUTE); }

    public static boolean requireConditions(Context ctx) {
        return prefs(ctx).getBoolean("require_conditions", false);
    }

    public static void save(Context ctx, boolean enabled, int hour, int minute,
                            boolean requireConditions) {
        prefs(ctx).edit()
                .putBoolean("enabled", enabled)
                .putInt("hour", Math.max(0, Math.min(23, hour)))
                .putInt("minute", Math.max(0, Math.min(59, minute)))
                .putBoolean("require_conditions", requireConditions)
                .apply();
        if (enabled) scheduleNext(ctx); else cancel(ctx);
    }

    public static long nextAt(Context ctx) { return prefs(ctx).getLong("next_at", 0); }

    public static String nextDescription(Context ctx) {
        return PipelineSchedule.describe(nextAt(ctx), ZoneId.systemDefault());
    }

    public static void scheduleNext(Context ctx) {
        cancel(ctx);
        if (!enabled(ctx)) return;
        long now = System.currentTimeMillis();
        long next = PipelineSchedule.nextTriggerMillis(now, hour(ctx), minute(ctx),
                ZoneId.systemDefault());
        Intent i = new Intent(ctx, PipelineAlarmReceiver.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
                Log.i(TAG, "alarm exact at " + next);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
                Log.i(TAG, "alarm inexact at " + next + " (no exact permission)");
            }
        } catch (Throwable t) {
            Log.e(TAG, "alarm schedule failed", t);
        }
        prefs(ctx).edit().putLong("next_at", next).apply();
    }

    public static void cancel(Context ctx) {
        Intent i = new Intent(ctx, PipelineAlarmReceiver.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null && pi != null) am.cancel(pi);
        prefs(ctx).edit().putLong("next_at", 0).apply();
    }

    /** 条件不满足时的 15 分钟重试（一次性）。 */
    public static void scheduleRetry(Context ctx) {
        Intent i = new Intent(ctx, PipelineAlarmReceiver.class).setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ + 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 15 * 60_000L, pi);
        } catch (Throwable t) {
            Log.e(TAG, "retry schedule failed", t);
        }
    }

    /** 默认只要求时间到；用户可要求「充电或息屏」。 */
    public static boolean conditionsMet(Context ctx) {
        if (!requireConditions(ctx)) return true;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            boolean screenOff = pm != null && !pm.isInteractive();
            android.os.BatteryManager bm = (android.os.BatteryManager)
                    ctx.getSystemService(Context.BATTERY_SERVICE);
            boolean charging = bm != null && bm.isCharging();
            return screenOff || charging;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean canExact(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        } catch (Throwable t) {
            return false;
        }
    }
}
