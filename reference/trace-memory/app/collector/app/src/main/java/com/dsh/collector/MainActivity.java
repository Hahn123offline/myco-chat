package com.dsh.collector;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.host.core.Host;
import com.dsh.usagetrace.A11yHealthMonitor;
import com.dsh.usagetrace.A11yState;
import com.dsh.usagetrace.A11yStatusText;
import com.dsh.usagetrace.UsageTraceModule;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 状态页：宿主 + 插件健康状况。启动即启动宿主。 */
public final class MainActivity extends Activity {

    private TextView tv;
    private TextView a11yBanner;
    private Button a11yButton;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // 顶部：无障碍告警 + 一键跳转（未在记录时最显眼）
        a11yBanner = new TextView(this);
        a11yBanner.setTextSize(16);
        a11yBanner.setTextColor(Color.rgb(200, 30, 30));
        a11yBanner.setVisibility(View.GONE);
        root.addView(a11yBanner);

        a11yButton = new Button(this);
        a11yButton.setText("去开启无障碍服务");
        a11yButton.setVisibility(View.GONE);
        a11yButton.setOnClickListener(v -> openA11ySettings());
        root.addView(a11yButton);

        tv = new TextView(this);
        tv.setTextSize(15);
        root.addView(tv);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        try { startForegroundService(new Intent(this, HostService.class)); } catch (Throwable ignored) { }
        tv.setOnClickListener(v -> {
            if (!isA11yOn()) openA11ySettings();
            else if (!ignoringBattery()) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable ignored) { }
            }
            tv.postDelayed(this::refresh, 800);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void openA11ySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable ignored) { }
    }

    /** 系统设置里是否写了我们（快速判断；真实健康以事件为准，见 refresh 里的 a11y 状态） */
    private boolean isA11yOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains("com.dsh.usagetrace");
        } catch (Throwable t) { return false; }
    }

    private boolean ignoringBattery() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) { return false; }
    }

    private static UsageTraceModule findUsageTrace(List<Host.Slot> slots) {
        for (Host.Slot s : slots) {
            if (s.module instanceof UsageTraceModule) return (UsageTraceModule) s.module;
        }
        return null;
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("DSH 宿主 ").append(AppHost.isStarted() ? "运行中" : "未运行").append("\n\n");

        List<Host.Slot> slots = AppHost.get(this).snapshot();
        UsageTraceModule ut = findUsageTrace(slots);
        A11yHealthMonitor.Result a11y = ut == null ? null : ut.a11ySnapshot();
        long episodesToday = ut == null ? -1 : ut.episodesToday();

        // 无障碍：如实显示 + 显眼提示 + 按钮
        if (a11y == null) {
            sb.append("无障碍健康：未知（usagetrace 模块未安装）\n");
            a11yBanner.setText("⚠ 未找到 usagetrace 模块，无法判断采集状态。");
            a11yBanner.setVisibility(View.VISIBLE);
            a11yButton.setVisibility(View.GONE);
        } else {
            sb.append("无障碍健康：").append(A11yStatusText.statusLine(
                    a11y.state, a11y.lastEventAgeMs, episodesToday)).append("\n");
            String banner = A11yStatusText.banner(a11y.state);
            a11yBanner.setText(banner);
            a11yBanner.setVisibility(banner.isEmpty() ? View.GONE : View.VISIBLE);
            a11yButton.setVisibility(a11y.state == A11yState.ACTIVE ? View.GONE : View.VISIBLE);
            // 修正按钮文案：DISABLED 是"去开"，SILENT 是"可能被杀，去检查"
            a11yButton.setText(a11y.state == A11yState.DISABLED
                    ? "去开启无障碍服务" : "检查无障碍服务状态");
        }
        sb.append("电池优化豁免：").append(ignoringBattery() ? "已允许" : "未允许（点此允许）").append("\n\n");

        sb.append("插件（").append(slots.size()).append("）\n");
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.US);
        for (Host.Slot s : slots) {
            sb.append("  · ").append(s.module.id())
                    .append("  v").append(s.module.version()).append("\n")
                    .append("     ").append(s.health).append("\n");
            if (s.installedAt > 0) {
                sb.append("     启动于 ").append(f.format(new Date(s.installedAt)))
                        .append("  重启 ").append(s.restarts).append(" 次\n");
            }
            appendRedactionStats(sb, s.module);
            appendMemoryStats(sb, s.module);
            sb.append("     声明能力：").append(s.module.requires()).append("\n\n");
        }
        sb.append("数据目录：Android/data/com.dsh.collector/files/trace/\n");
        tv.setText(sb.toString());
    }

    /** 脱敏链路的占用/耗电观测 + 批处理进度（用户明确要求可观测） */
    private void appendRedactionStats(StringBuilder sb, com.dsh.host.api.Module m) {
        if (!(m instanceof com.dsh.redaction.RedactionModule)) return;
        com.dsh.redaction.RedactionModule rm = (com.dsh.redaction.RedactionModule) m;
        try {
            com.dsh.redaction.Metrics metrics = rm.metrics();
            if (metrics != null) {
                sb.append("     脱敏观测：").append(metrics.summaryLine(metrics.snapshot())).append("\n");
            }
            if (rm.metricsPath() != null && !rm.metricsPath().isEmpty()) {
                sb.append("     metrics：").append(rm.metricsPath()).append("\n");
            }
            com.dsh.redaction.BatchRedactor.Report rep = rm.lastBatchReport();
            if (rep != null) {
                sb.append("     批处理：").append(rep.summary()).append("\n");
            }
            sb.append("     模型：请求 ").append(rm.modelRequests())
                    .append(" / 升级 ").append(rm.modelUpgrades())
                    .append(" / 拒绝 ").append(rm.modelRejected()).append("\n");
        } catch (Throwable ignored) {
            // 状态页不能因为观测读取失败而崩
        }
    }

    /** 记忆门控：episodes/blocks 对账 + 各出网档位数量 + 导出路径 */
    private void appendMemoryStats(StringBuilder sb, com.dsh.host.api.Module m) {
        if (!(m instanceof com.dsh.memory.MemoryModule)) return;
        try {
            sb.append("     记忆：").append(((com.dsh.memory.MemoryModule) m).debugSummary()).append("\n");
        } catch (Throwable ignored) {
        }
    }
}
