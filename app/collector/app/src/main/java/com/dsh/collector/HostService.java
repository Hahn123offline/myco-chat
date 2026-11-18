package com.dsh.collector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.dsh.host.api.Health;
import com.dsh.host.core.Host;
import com.dsh.usagetrace.A11yHealthMonitor;
import com.dsh.usagetrace.A11yState;
import com.dsh.usagetrace.A11yStatusText;
import com.dsh.usagetrace.UsageTraceModule;

import java.util.List;

/**
 * 宿主前台服务：保活 + 状态通知。真正的工作在 AppHost 里。
 *
 * 通知必须如实：无障碍未启用/无数据时**绝不能显示"正在记录"**。
 * 用户以为在记、实际没记，是这个产品最致命的失败模式。
 */
public final class HostService extends Service {

    /** 用新 channel id 是因为 Android 不允许修改已存在 channel 的 importance（旧的是 MIN） */
    private static final String CH = "host_status";
    private static final String OLD_CH = "host";
    private static final int NID = 1001;
    private static final long REFRESH_MS = 60_000L;

    private final android.os.Handler refreshHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            try { refresh(); } catch (Throwable ignored) { }
            refreshHandler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("DSHHost", "HostService.onCreate");
        NotificationManager nm = getSystemService(NotificationManager.class);
        try { nm.deleteNotificationChannel(OLD_CH); } catch (Throwable ignored) { }
        NotificationChannel ch = new NotificationChannel(CH, "宿主状态", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        startForeground(NID, build("DSH 宿主", "启动中…", null));
        try {
            AppHost.get(this).start();
            refresh();
            Log.i("DSHHost", "HostService started, refresh done");
        } catch (Throwable t) {
            Log.e("HostService", "start failed", t);
            startForeground(NID, build("DSH 未在记录", "启动失败：" + t.getMessage(), null));
        }
        // 周期刷新：用户中途关掉无障碍/服务僵死时，通知不能停在旧状态
        refreshHandler.postDelayed(refreshLoop, REFRESH_MS);
    }

    @Override
    public void onDestroy() {
        refreshHandler.removeCallbacks(refreshLoop);
        super.onDestroy();
    }

    private Notification build(String title, String text, A11yState state) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (state != null && state != A11yState.ACTIVE) {
            // 未在记录：允许展开看全文（BigText），不做成不可读的一行
            b.setStyle(new Notification.BigTextStyle().bigText(text));
        }
        return b.build();
    }

    /** 通知栏显示：无障碍真实状态优先于"插件数" */
    public void refresh() {
        int total = 0, bad = 0, degraded = 0;
        StringBuilder badIds = new StringBuilder();
        A11yState a11y = null;
        long ageMs = -1;
        long episodesToday = -1;
        try {
            List<Host.Slot> slots = AppHost.get(this).snapshot();
            total = slots.size();
            for (Host.Slot s : slots) {
                // 只有 FAILED 才算"异常"：DEGRADED 是"功能降级/未配置/没内容"，
                // 比如 models 没配 provider、enrich 还没有 WHITE 块——这些是正常状态，
                // 不该在通知里吓用户（状态页仍然如实显示）。
                if (s.health.state == Health.State.FAILED) {
                    bad++;
                    if (badIds.length() < 40) {
                        badIds.append(s.module.id()).append(":").append(s.health.state).append(" ");
                    }
                } else if (s.health.state == Health.State.DEGRADED) {
                    degraded++;
                }
                if (s.module instanceof UsageTraceModule) {
                    UsageTraceModule ut = (UsageTraceModule) s.module;
                    A11yHealthMonitor.Result r = ut.a11ySnapshot();
                    a11y = r.state;
                    ageMs = r.lastEventAgeMs;
                    episodesToday = ut.episodesToday();
                }
            }
        } catch (Throwable ignored) { }

        String title;
        String text;
        if (total == 0) {
            title = "DSH 未在记录";
            text = "宿主未启动";
        } else if (a11y == A11yState.DISABLED) {
            title = A11yStatusText.notificationTitle(a11y);
            text = A11yStatusText.notificationText(a11y, ageMs, episodesToday);
        } else if (a11y == A11yState.ENABLED_BUT_SILENT) {
            title = A11yStatusText.notificationTitle(a11y);
            text = A11yStatusText.notificationText(a11y, ageMs, episodesToday);
        } else if (a11y == null) {
            title = "DSH 状态未知";
            text = "未找到 usagetrace 模块";
        } else {
            title = A11yStatusText.notificationTitle(a11y);   // "DSH 正在记录"
            text = bad == 0
                    ? "正在记录（" + total + " 个插件正常"
                            + (degraded > 0 ? "，有 " + degraded + " 个功能降级" : "") + "）"
                    : "正在记录，但有 " + bad + " 个插件异常：" + badIds;
        }
        getSystemService(NotificationManager.class).notify(NID, build(title, text, a11y));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            AppHost.get(this).start();
            refresh();
        } catch (Throwable ignored) { }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
