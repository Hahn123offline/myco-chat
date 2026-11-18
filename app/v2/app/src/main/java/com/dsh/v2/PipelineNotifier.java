package com.dsh.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** 流水线结果通知：成功/失败都必须可见。 */
public final class PipelineNotifier {

    public static final String CH = "pipeline_status";
    public static final int NID = 3002;

    private PipelineNotifier() { }

    public static void notifyResult(Context ctx, boolean ok, String message) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "夜间整理",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setShowBadge(true);
                nm.createNotificationChannel(ch);
            }
            String title = ok ? "DSH 夜间整理成功" : "DSH 夜间整理失败";
            String text = message == null ? "" : message;
            if (!ok) text = "失败：" + text;
            Intent open = new Intent(ctx, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(ctx, CH)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(NID, n);
        } catch (Throwable ignored) { }
    }
}
