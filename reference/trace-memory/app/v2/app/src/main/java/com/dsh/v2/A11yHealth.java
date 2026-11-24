package com.dsh.v2;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 无障碍的低层信号：
 *  - {@link #onEvent()} / {@link #lastEventAt()}：我们**实际观察到**的事件时间（最可信）
 *  - {@link #isEnabled}：系统声明列表（MIUI 上可能不可靠，只在「从未收到事件」时用于区分提示）
 *
 * 「现在到底在不在记录」的唯一判据在 :trace 的 {@link com.dsh.trace.RecordingState}，
 * 通知/状态页/Health 三处都必须用它，不能各判各的。
 */
public final class A11yHealth {

    private static final AtomicLong LAST_EVENT_AT = new AtomicLong(0);

    private A11yHealth() { }

    /** 无障碍事件到达时调用（由 TraceAccessibilityService 每次事件调用） */
    public static void onEvent() {
        LAST_EVENT_AT.set(System.currentTimeMillis());
    }

    /** 最近一次事件时间（0 = 从未收到） */
    public static long lastEventAt() { return LAST_EVENT_AT.get(); }

    /** 系统声明：是否在已启用无障碍服务列表里（含 Settings.Secure 兜底） */
    public static boolean isEnabled(Context app) {
        if (managerSaysEnabled(app)) return true;
        return settingsSayEnabled(app);
    }

    private static boolean managerSaysEnabled(Context app) {
        try {
            AccessibilityManager am = (AccessibilityManager)
                    app.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) return false;
            List<AccessibilityServiceInfo> list =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (list == null) return false;
            String pkg = app.getPackageName();
            String cls = TraceAccessibilityService.class.getName();
            for (AccessibilityServiceInfo info : list) {
                if (info == null) continue;
                ResolveInfo ri = info.getResolveInfo();
                if (ri == null || ri.serviceInfo == null) continue;
                if (pkg.equals(ri.serviceInfo.packageName) && cls.equals(ri.serviceInfo.name)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean settingsSayEnabled(Context app) {
        try {
            String s = Settings.Secure.getString(app.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            String me = app.getPackageName() + "/" + TraceAccessibilityService.class.getName();
            return s.contains(me);
        } catch (Throwable t) {
            return false;
        }
    }
}
