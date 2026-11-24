package com.dsh.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * 前台通知：如实显示"正在记录 / 未在记录"，并带今日痕迹数。
 * 状态唯一计算者是 {@link RecordingStatusCenter}；通知/状态页/设置入口都调它。
 */
public final class HostService extends Service {

    private static final String CH = "trace_status";
    private static final String ALERT_CH = "trace_alert";
    private static final int NID = 2001;
    private static final int ALERT_NID = 2003;
    private static final long REFRESH_MS = 30_000L;
    private static final long WATCHDOG_MS = 5 * 60_000L;
    private static final String TAG = "DSHHost";

    /** 进程内是否已启动（同一进程；跨进程不需要，服务只在本进程） */
    private static volatile boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            try { refresh(); } catch (Throwable ignored) { }
            handler.postDelayed(this, REFRESH_MS);
        }
    };
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            try { checkWatchdog(); } catch (Throwable t) {
                Log.e(TAG, "watchdog failed: " + t.getClass().getSimpleName(), t);
            }
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

    /**
     * 幂等启动前台服务。任何一路调用失败都必须留痕，不能静默。
     * 注意：Android 12+ 后台启动 FGS 可能被限制；这里只负责尝试 + 打日志，
     * 真正在后台受限时由 a11y/开机路径（系统白名单）兜底。
     */
    public static void ensureRunning(Context c) {
        if (c == null) return;
        if (running) return;
        try {
            Intent i = new Intent(c, HostService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
            Log.i(TAG, "HostService start requested (running=" + running + ")");
        } catch (Throwable t) {
            Log.e(TAG, "HostService start FAILED: " + t.getClass().getSimpleName(), t);
        }
    }

    public static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        new Thread(() -> {
            try {
                if (!L0L1Pipeline.isRunning()) {
                    new ChainStore(getApplicationContext()).failOrphanRuns("process-restarted");
                }
            } catch (Throwable t) {
                Log.w(TAG, "orphan lease cleanup failed: " + t.getClass().getSimpleName());
            }
        }, "lease-cleanup").start();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, "痕迹记录状态",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        NotificationChannel alert = new NotificationChannel(ALERT_CH,
                "记录异常提醒", NotificationManager.IMPORTANCE_HIGH);
        alert.setShowBadge(true);
        alert.enableVibration(true);
        nm.createNotificationChannel(alert);
        startForeground(NID, build("DSH 痕迹记录", "启动中…"));
        handler.post(loop);
        handler.postDelayed(watchdog, WATCHDOG_MS);
        Log.i(TAG, "HostService onCreate (foreground)");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // 只刷新一次；周期循环由 onCreate 里的 loop 唯一持有，避免重复链条
        try { refresh(); } catch (Throwable ignored) { }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(loop);
        handler.removeCallbacks(watchdog);
        super.onDestroy();
        Log.w(TAG, "HostService onDestroy (foreground lost)");
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void refresh() {
        RecordingStatusCenter.Snapshot snap = RecordingStatusCenter.current(this);
        long today = snap.todayCount;
        RecordingStatus.State state = snap.state;
        if (state == RecordingStatus.State.ACTIVE) {
            Log.i(TAG, "notification: recording=ACTIVE today=" + today);
        } else {
            Log.w(TAG, "notification: recording=" + RecordingStatus.label(state)
                    + " today=" + today + " ago=" + snap.lastEventAgoMs
                    + " usageSwitches=" + snap.usageSwitches);
        }
        Notification n = build(RecordingText.title(state),
                RecordingText.text(state, today),
                RecordingText.actionable(state));
        getSystemService(NotificationManager.class).notify(NID, n);
        if (state != RecordingStatus.State.STALLED) {
            RecordingStatusCenter.clearSelfHealFailures();
            cancelAlert();
        }
    }

    /** 看门狗：STALLED → 自愈；连续 3 次失败 → 高优先级通知。 */
    private void checkWatchdog() {
        RecordingStatusCenter.Snapshot snap = RecordingStatusCenter.current(this);
        Log.i(TAG, "watchdog state=" + RecordingStatus.label(snap.state)
                + " ago=" + snap.lastEventAgoMs + " usageSwitches=" + snap.usageSwitches
                + " healFails=" + snap.selfHealFailures);
        if (snap.state == RecordingStatus.State.STALLED) {
            boolean attempted = TraceAccessibilityService.requestRebind("watchdog");
            RecordingStatusCenter.noteSelfHealFailure();
            int fails = RecordingStatusCenter.selfHealFailures();
            Log.w(TAG, "selfHealAttempt #" + fails + " attempted=" + attempted);
            if (fails >= 3) showAlert();
        } else {
            RecordingStatusCenter.clearSelfHealFailures();
            cancelAlert();
        }
    }

    private void showAlert() {
        if (RecordingStatusCenter.highPriorityShown()) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        Intent open = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, ALERT_CH)
                .setContentTitle("DSH 没有在记录 · 需要你手动开关一次无障碍")
                .setContentText("连续 3 次自愈失败；点这里去无障碍设置")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "连续 3 次自愈失败，事件流仍未恢复。请到无障碍设置里关一次再打开"
                                + "「DSH v2 痕迹记录」。"))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pi)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
        nm.notify(ALERT_NID, n);
        RecordingStatusCenter.setHighPriorityShown(true);
        Log.e(TAG, "ESCALATED: high-priority accessibility alert shown");
    }

    private void cancelAlert() {
        if (!RecordingStatusCenter.highPriorityShown()) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_NID);
        RecordingStatusCenter.setHighPriorityShown(false);
        Log.i(TAG, "high-priority accessibility alert cleared");
    }

    private Notification build(String title, String text) {
        return build(title, text, false);
    }

    private Notification build(String title, String text, boolean openA11ySettings) {
        Intent open = openA11ySettings
                ? new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                : new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }
}
