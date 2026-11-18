package com.dsh.usagetrace;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * 提醒通知：无障碍被系统关掉时，只能**提醒用户**去开。
 *
 * 红线：不尝试 settings put / 任何绕过系统打开无障碍的做法（权限不允许，也不该做）。
 */
public final class A11yNotifier {

    public static final String CHANNEL = "a11y_alerts";
    public static final int ID = 2101;

    private A11yNotifier() { }

    public static void remind(Context app) {
        if (app == null) return;
        try {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            // IMPORTANCE_DEFAULT：优先级高于前台通知的 MIN，用户能看到并点开
            NotificationChannel ch = new NotificationChannel(CHANNEL, "无障碍提醒",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);

            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(app, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n = new Notification.Builder(app, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(A11yStatusText.alertTitle())
                    .setContentText(A11yStatusText.alertText())
                    .setStyle(new Notification.BigTextStyle().bigText(A11yStatusText.alertText()))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(ID, n);
        } catch (Throwable ignored) {
            // 通知失败不能影响宿主
        }
    }

    public static void cancel(Context app) {
        if (app == null) return;
        try {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ID);
        } catch (Throwable ignored) { }
    }
}
