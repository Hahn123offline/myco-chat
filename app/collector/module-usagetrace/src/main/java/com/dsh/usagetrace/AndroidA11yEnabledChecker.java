package com.dsh.usagetrace;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * 生产判据：AccessibilityManager 的"已启用服务列表"里是否有我们自己。
 *
 * 为什么还要看 Settings.Secure 字符串：极少数系统上 manager 列表会滞后/为空，
 * 而设置里确实写了我们。这里只是 OR 兜底；真正"有没有在干活"由
 * {@link A11yHealthMonitor} 的"最近事件"判据决定 —— 系统说启用但没事件只会是 SILENT，
 * 绝不会被当成 ACTIVE。
 */
public final class AndroidA11yEnabledChecker implements A11yEnabledChecker {

    private final Context app;

    public AndroidA11yEnabledChecker(Context app) { this.app = app; }

    @Override
    public boolean isOurServiceEnabled() {
        if (managerSaysEnabled()) return true;
        return settingsSayEnabled();
    }

    private boolean managerSaysEnabled() {
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

    private boolean settingsSayEnabled() {
        try {
            String s = Settings.Secure.getString(app.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            String me = app.getPackageName() + "/" + TraceAccessibilityService.class.getName();
            // 设置里既有全名也有简写形式（.TraceAccessibilityService），两种都认
            return s.contains(me) || s.contains(app.getPackageName() + "/." + shortName());
        } catch (Throwable t) {
            return false;
        }
    }

    private static String shortName() {
        String full = TraceAccessibilityService.class.getName();
        int dot = full.lastIndexOf('.');
        return dot < 0 ? full : full.substring(dot + 1);
    }
}
