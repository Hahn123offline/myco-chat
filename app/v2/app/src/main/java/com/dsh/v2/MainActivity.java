package com.dsh.v2;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.core.Config;
import com.dsh.models.Capability;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.ModelsConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 状态页 + 云端模型配置（API Key 只在手机上填，掩码显示）。 */
public final class MainActivity extends Activity {

    private TextView status;
    private TextView a11yBanner;
    private Button a11yFix;
    private TextView detail;
    private EditText keyInput;
    private EditText baseInput;
    private EditText modelInput;
    private CheckBox showKey;
    private TextView modelStatus;
    private TextView scheduleStatus;
    private TextView lastRunStatus;
    private EditText alarmHour;
    private EditText alarmMinute;
    private CheckBox scheduleConditions;
    private EditText exclusionInput;
    private TextView exclusionList;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "model-test");
        t.setDaemon(true);
        return t;
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);
        root.setGravity(Gravity.START);
        scroll.addView(root);

        status = new TextView(this);
        status.setTextSize(22);
        a11yBanner = new TextView(this);
        a11yBanner.setTextSize(18);
        a11yBanner.setTextColor(0xFFB00020);
        a11yBanner.setPadding(16, 16, 16, 16);
        a11yBanner.setText("⚠ 检测到无障碍已关闭，请重新开启");
        a11yFix = new Button(this);
        a11yFix.setText("立即去开启无障碍");
        a11yFix.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) { }
        });
        detail = new TextView(this);
        detail.setTextSize(16);
        detail.setPadding(0, 24, 0, 32);

        Button open = new Button(this);
        open.setText("去开启无障碍服务");
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) { }
        });
        Button refresh = new Button(this);
        refresh.setText("刷新状态");
        refresh.setOnClickListener(v -> { render(); renderModel(); renderSchedule(); });
        Button usage = new Button(this);
        usage.setText("去授权「使用情况访问」（降级记录）");
        usage.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Throwable ignored) { }
        });
        Button l2 = new Button(this);
        l2.setText("跑一条 L2（最近 8 条痕迹，调试）");
        l2.setOnClickListener(v -> {
            detail.setText("L2 运行中…（读 trace.db → 工具循环 → dump 转录）");
            L2Runner.runLast(this, 8, (ok, message) -> runOnUiThread(() ->
                    detail.setText((ok ? "L2 成功：\n" : "L2 失败：\n") + message
                            + "\n\n可用 adb 拉取 files/trace/l2_transcript_*.txt")));
        });

        root.addView(a11yBanner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(a11yFix, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(usage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(l2, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button chat = new Button(this);
        chat.setText("打开会话");
        chat.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        root.addView(chat, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------------- 夜间整理调度 ----------------
        TextView schedTitle = new TextView(this);
        schedTitle.setText("夜间整理（L0→L1→L2）");
        schedTitle.setTextSize(18);
        schedTitle.setPadding(0, 48, 0, 12);
        root.addView(schedTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scheduleStatus = new TextView(this);
        scheduleStatus.setTextSize(14);
        lastRunStatus = new TextView(this);
        lastRunStatus.setTextSize(14);
        lastRunStatus.setPadding(0, 8, 0, 8);

        alarmHour = new EditText(this);
        alarmHour.setHint("小时（0–23）");
        alarmHour.setInputType(InputType.TYPE_CLASS_NUMBER);
        alarmHour.setSingleLine(true);
        alarmMinute = new EditText(this);
        alarmMinute.setHint("分钟（0–59）");
        alarmMinute.setInputType(InputType.TYPE_CLASS_NUMBER);
        alarmMinute.setSingleLine(true);
        scheduleConditions = new CheckBox(this);
        scheduleConditions.setText("只在充电或息屏时运行（默认关：时间到就跑）");

        Button saveSchedule = new Button(this);
        saveSchedule.setText("保存闹钟时间");
        saveSchedule.setOnClickListener(v -> {
            try {
                int h = Integer.parseInt(alarmHour.getText().toString().trim());
                int m = Integer.parseInt(alarmMinute.getText().toString().trim());
                PipelineScheduler.save(this, true, h, m,
                        scheduleConditions.isChecked());
                renderSchedule();
            } catch (Throwable t) {
                scheduleStatus.setText("时间格式不对：小时/分钟需为数字");
            }
        });

        Button run24 = new Button(this);
        run24.setText("手动跑流水线（最近 24 小时）");
        run24.setOnClickListener(v -> runPipeline(24));
        Button run6 = new Button(this);
        run6.setText("手动跑流水线（最近 6 小时）");
        run6.setOnClickListener(v -> runPipeline(6));

        root.addView(scheduleStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(lastRunStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(alarmHour, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(alarmMinute, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scheduleConditions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(saveSchedule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(run24, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(run6, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------------- 隐私：应用级出网排除 ----------------
        TextView privTitle = new TextView(this);
        privTitle.setText("隐私：永不外发的应用");
        privTitle.setTextSize(18);
        privTitle.setPadding(0, 48, 0, 12);
        root.addView(privTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView privHint = new TextView(this);
        privHint.setText("默认排除微信/QQ/支付宝/银行/相册/密码管理器；名单只存本地。");
        privHint.setTextSize(12);
        root.addView(privHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        exclusionInput = new EditText(this);
        exclusionInput.setHint("包名，如 com.example.app");
        exclusionInput.setSingleLine(true);
        Button addExclusion = new Button(this);
        addExclusion.setText("设为「永不上云」");
        addExclusion.setOnClickListener(v -> setLevelFromInput(
                com.dsh.egress.AppExclusionPolicy.Level.NEVER_CLOUD));
        Button redactExclusion = new Button(this);
        redactExclusion.setText("设为「先掩码后上云」");
        redactExclusion.setOnClickListener(v -> setLevelFromInput(
                com.dsh.egress.AppExclusionPolicy.Level.REDACT_THEN_CLOUD));
        Button normalExclusion = new Button(this);
        normalExclusion.setText("设为「正常上云」");
        normalExclusion.setOnClickListener(v -> setLevelFromInput(
                com.dsh.egress.AppExclusionPolicy.Level.NORMAL));
        Button restoreExclusion = new Button(this);
        restoreExclusion.setText("恢复默认三级策略");
        restoreExclusion.setOnClickListener(v -> {
            AppExclusionStore.restore(this);
            renderExclusions();
        });
        exclusionList = new TextView(this);
        exclusionList.setTextSize(13);
        root.addView(exclusionInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(addExclusion, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(redactExclusion, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(normalExclusion, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(restoreExclusion, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(exclusionList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------------- 云端模型配置（OpenAI 兼容） ----------------
        TextView modelTitle = new TextView(this);
        modelTitle.setText("云端模型（OpenAI 兼容，默认 DeepSeek）");
        modelTitle.setTextSize(18);
        modelTitle.setPadding(0, 48, 0, 16);
        root.addView(modelTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        modelStatus = new TextView(this);
        modelStatus.setTextSize(14);
        modelStatus.setPadding(0, 0, 0, 12);

        keyInput = new EditText(this);
        keyInput.setHint("API Key（只保存在本机，不显示、不写日志）");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setSingleLine(true);
        // 允许长按系统粘贴菜单/键盘剪贴板；不要自定义长按把菜单吃掉。
        // 也不要用 FLAG_SECURE（会禁用剪贴板和截屏）。
        keyInput.setLongClickable(true);
        keyInput.setTextIsSelectable(true);

        showKey = new CheckBox(this);
        showKey.setText("显示 Key（编辑时临时明文，保存后仍只显示掩码）");
        showKey.setOnCheckedChangeListener((button, checked) -> {
            int start = keyInput.getSelectionStart();
            int end = keyInput.getSelectionEnd();
            keyInput.setInputType(InputType.TYPE_CLASS_TEXT | (checked
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            keyInput.setSingleLine(true);
            keyInput.setLongClickable(true);
            keyInput.setTextIsSelectable(true);
            if (start >= 0 && end >= 0) {
                try { keyInput.setSelection(Math.min(start, keyInput.length()),
                        Math.min(end, keyInput.length())); } catch (Throwable ignored) { }
            }
        });

        baseInput = new EditText(this);
        baseInput.setHint("base_url");
        baseInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        baseInput.setSingleLine(true);

        modelInput = new EditText(this);
        modelInput.setHint("model");
        modelInput.setSingleLine(true);

        Button saveModel = new Button(this);
        saveModel.setText("保存 Key / 配置");
        saveModel.setOnClickListener(v -> saveModelConfig(true));

        Button test = new Button(this);
        test.setText("测试连接（真发一次最小请求）");
        test.setOnClickListener(v -> testConnection());

        Button clearKey = new Button(this);
        clearKey.setText("清除已保存的 Key");
        clearKey.setOnClickListener(v -> {
            try {
                TraceApp app = TraceApp.get(this);
                if (app != null) app.secrets().clear("deepseek");
                keyInput.setText("");
                renderModel();
            } catch (Throwable ignored) { }
        });

        root.addView(modelStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(showKey, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(baseInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(modelInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(saveModel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(test, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(clearKey, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        handleDevIntent(getIntent());

        HostService.ensureRunning(this);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } catch (Throwable ignored) { }
        }
        render();
        renderModel();
        renderSchedule();
        renderExclusions();
        if (PipelineScheduler.enabled(this)) PipelineScheduler.scheduleNext(this);
    }

    @Override protected void onResume() {
        super.onResume();
        HostService.ensureRunning(this);
        render();
        renderModel();
        renderSchedule();
        renderExclusions();
    }

    private void renderExclusions() {
        try {
            java.util.Map<String, com.dsh.egress.AppExclusionPolicy.Level> all =
                    com.dsh.egress.AppExclusionPolicy.effectiveLevels();
            java.util.Map<String, com.dsh.egress.AppExclusionPolicy.Level> overrides =
                    com.dsh.egress.AppExclusionPolicy.overrides();
            int never = 0, redact = 0, normal = 0;
            for (com.dsh.egress.AppExclusionPolicy.Level l : all.values()) {
                if (l == com.dsh.egress.AppExclusionPolicy.Level.NEVER_CLOUD) never++;
                else if (l == com.dsh.egress.AppExclusionPolicy.Level.REDACT_THEN_CLOUD) redact++;
                else normal++;
            }
            exclusionList.setText("当前策略：永不上云 " + never + " 个，先掩码后上云 "
                    + redact + " 个，正常 " + normal + " 个\n用户覆盖："
                    + (overrides.isEmpty() ? "无" : overrides.toString()));
        } catch (Throwable t) {
            exclusionList.setText("策略读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void setLevelFromInput(com.dsh.egress.AppExclusionPolicy.Level level) {
        String pkg = exclusionInput.getText().toString().trim();
        if (pkg.isEmpty()) return;
        AppExclusionStore.setLevel(this, pkg, level);
        exclusionInput.setText("");
        renderExclusions();
    }

    private void renderSchedule() {
        try {
            alarmHour.setText(String.valueOf(PipelineScheduler.hour(this)));
            alarmMinute.setText(String.valueOf(PipelineScheduler.minute(this)));
            scheduleConditions.setChecked(PipelineScheduler.requireConditions(this));
            String exact = PipelineScheduler.canExact(this) ? "" : "（exact 权限未授予，用近似时间）";
            scheduleStatus.setText("下次运行：" + PipelineScheduler.nextDescription(this) + exact);
            lastRunStatus.setText(PipelineStatusStore.describeLast(this));
        } catch (Throwable t) {
            scheduleStatus.setText("调度状态读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void runPipeline(int hours) {
        lastRunStatus.setText("流水线运行中…（" + hours + "h 窗口）");
        L0L1Pipeline.run(this, hours, (ok, message) -> {
            PipelineStatusStore.save(this, ok, message);
            runOnUiThread(() -> {
                lastRunStatus.setText(PipelineStatusStore.describeLast(this)
                        + "\n" + (ok ? "完成：" : "失败：") + message);
                renderSchedule();
            });
            PipelineNotifier.notifyResult(this, ok, message);
        });
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void renderModel() {
        try {
            TraceApp app = TraceApp.get(this);
            if (app == null) return;
            String key = app.secrets().keyFor("deepseek");
            keyInput.setHint(key.isEmpty()
                    ? "API Key（只保存在本机，不显示、不写日志）"
                    : "已保存 " + SecretStore.mask(key) + "（留空表示不修改）");
            Config cfg = app.configs().load("models");
            if (baseInput.getText().toString().trim().isEmpty()) {
                baseInput.setText(cfg.getString("provider.deepseek.baseUrl",
                        ModelsConfig.DEFAULT_BASE_URL));
            }
            if (modelInput.getText().toString().trim().isEmpty()) {
                modelInput.setText(cfg.getString("provider.deepseek.model",
                        ModelsConfig.DEFAULT_MODEL));
            }
            Set<Capability> caps = app.modelsModule() == null
                    ? java.util.Collections.emptySet()
                    : app.modelsModule().caps("deepseek");
            String capText = caps.isEmpty() ? "未声明能力" : caps.toString();
            modelStatus.setText((key.isEmpty() ? "未填写 Key" : "已保存：" + SecretStore.mask(key))
                    + " · 能力：" + capText);
        } catch (Throwable t) {
            modelStatus.setText("模型配置读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void saveModelConfig(boolean showToast) {
        try {
            TraceApp app = TraceApp.get(this);
            if (app == null) return;
            String key = keyInput.getText().toString().trim();
            if (!key.isEmpty() && !app.secrets().save("deepseek", key)) {
                modelStatus.setText("Key 保存失败（Keystore 不可用）");
                return;
            }
            String base = baseInput.getText().toString().trim();
            String model = modelInput.getText().toString().trim();
            if (base.isEmpty()) base = ModelsConfig.DEFAULT_BASE_URL;
            if (model.isEmpty()) model = ModelsConfig.DEFAULT_MODEL;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("providers", "deepseek");
            values.put("provider.deepseek.kind", "cloud");
            values.put("provider.deepseek.baseUrl", base);
            values.put("provider.deepseek.model", model);
            values.put("provider.deepseek.caps", "JSON,PROMPT_CACHE,LONG_CTX");
            app.configs().save("models", values);
            if (app.modelsModule() != null) app.modelsModule().reload();
            keyInput.setText("");
            renderModel();
            if (showToast) {
                modelStatus.setText("已保存");
                renderModel();
            }
        } catch (Throwable t) {
            modelStatus.setText("保存失败：" + t.getClass().getSimpleName());
        }
    }

    private void testConnection() {
        try {
            TraceApp app = TraceApp.get(this);
            if (app == null) return;
            String typed = keyInput.getText().toString().trim();
            String key = typed.isEmpty() ? app.secrets().keyFor("deepseek") : typed;
            if (key == null || key.trim().isEmpty()) {
                modelStatus.setText("请先填写 API Key");
                return;
            }
            // 如果输入了新 key，先保存再重建 provider，保证测试用的是刚填的 key
            if (!typed.isEmpty()) {
                saveModelConfig(false);
            }
            modelStatus.setText("测试中…");
            io.execute(() -> {
                ModelResponse r;
                try {
                    r = app.modelsModule().testConnection("deepseek",
                            ModelRequest.text(
                                    "You are a connectivity probe. Reply with the single word OK.",
                                    "ping").withProvenance(java.util.Collections.emptyList(), com.dsh.egress.EgressStage.DERIVED).withThinking("disabled"));
                } catch (Throwable t) {
                    r = ModelResponse.error(t.getClass().getSimpleName(), "deepseek");
                }
                final ModelResponse out = r;
                runOnUiThread(() -> {
                    if (out.isOk()) {
                        modelStatus.setText("连接成功 · 延迟 " + out.latencyMs + "ms"
                                + " · 模型 " + out.model
                                + " · usage prompt=" + out.promptTokens
                                + " completion=" + out.completionTokens
                                + " cache_hit=" + out.promptCacheHitTokens);
                    } else {
                        modelStatus.setText("连接失败：" + out.error);
                    }
                });
            });
        } catch (Throwable t) {
            modelStatus.setText("测试失败：" + t.getClass().getSimpleName());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDevIntent(intent);
    }

    /** 开发钩子：MIUI 会丢 receiver 广播，adb 验证统一走 Activity。 */
    private void handleDevIntent(Intent intent) {
        if (intent == null) return;
        final String action = intent.getStringExtra("dev_action");
        if (action == null || action.isEmpty()) return;
        final String day = intent.getStringExtra("day");
        android.util.Log.i("DSHL2", "dev_action=" + action + " day=" + day);
        new Thread(() -> {
            try {
                if ("run_day".equals(action)) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    if (day != null && !day.isEmpty()) {
                        String[] p = day.split("-");
                        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1,
                                Integer.parseInt(p[2]), 0, 0, 0);
                    } else {
                        c.add(java.util.Calendar.DAY_OF_YEAR, -1);
                    }
                    DayNarrativeRunner.run(this, c, (ok, msg) ->
                            android.util.Log.i("DSHL2", "dev run_day done=" + ok + " " + msg));
                } else if ("chat_ask".equals(action)) {
                    String q = intent.getStringExtra("q");
                    if (q == null || q.trim().isEmpty()) q = "你好";
                    final String question = q;
                    android.util.Log.i("DSHL2", "chat_ask q=" + question);
                    appendHostLog("chat_ask q=" + question);
                    final long t0 = System.currentTimeMillis();
                    ChatSession.send(this, question, (ok, message) -> {
                        long ms = System.currentTimeMillis() - t0;
                        String head = "chat_answer ok=" + ok + " ms=" + ms + "\n";
                        android.util.Log.i("DSHL2", "chat_answer ok=" + ok + " ms=" + ms);
                        android.util.Log.i("DSHL2", "chat_answer " + (message == null ? "" : message));
                        appendHostLog(head + (message == null ? "" : message));
                    });
                } else if ("project".equals(action)) {
                    int e = MemoryProjector.projectEvents(this);
                    MemoryProjector.projectTraceBlocks(this);
                    int d = MemoryProjector.projectDays(this);
                    android.util.Log.i("DSHL2", "dev project done events=" + e + " days=" + d);
                }
            } catch (Throwable t) {
                android.util.Log.e("DSHL2", "dev_action failed " + action, t);
            }
        }, "dev-action").start();
    }

    private void appendHostLog(String text) {
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "host/log");
            if (!dir.exists()) dir.mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(dir, "host.log"), true)) {
                out.write((text + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) { }
    }

    private void render() {
        String stats = "";
        String usageStats = "";
        boolean usageGranted = false;
        RecordingStatusCenter.Snapshot snap = RecordingStatusCenter.current(this);
        try {
            TraceApp app = TraceApp.get(this);
            if (app != null && app.traceModule() != null) {
                stats = app.traceModule().statusLine();
            }
            if (app != null && app.usageModule() != null) {
                usageGranted = app.usageModule().granted();
                usageStats = app.usageModule().statusLine();
            }
        } catch (Throwable ignored) { }
        String usageHint = usageGranted ? "" :
                "\n\n未授权「使用情况访问」：a11y 断流时无法降级记录 app 使用时间段。点下面按钮授权。";
        status.setText(RecordingText.title(snap.state));
        boolean off = snap.state == RecordingStatus.State.OFF;
        a11yBanner.setVisibility(off ? View.VISIBLE : View.GONE);
        a11yFix.setVisibility(off ? View.VISIBLE : View.GONE);
        String why;
        switch (snap.state) {
            case STALLED:
                why = "无障碍服务还在，但已超过 10 分钟没有任何事件，且 usage 显示手机在使用。"
                        + "请重新开关一次无障碍服务（或重启手机后再开关）。";
                break;
            case OFF:
                why = "系统里无障碍服务未启用。请打开系统的无障碍设置开启「DSH v2 痕迹记录」。";
                break;
            case IDLE:
                why = "服务正常，只是最近 10 分钟没有新的页面事件（可能在息屏/休息）。";
                break;
            default:
                why = "无障碍事件在正常到达。";
        }
        StringBuilder readable = new StringBuilder();
        readable.append("记录状态：").append(RecordingText.title(snap.state)).append('\n');
        readable.append("最近事件：").append(RecordingStatus.readableAgo(snap.lastEventAgoMs))
                .append('\n');
        readable.append("今日块数：").append(snap.todayCount).append('\n');
        readable.append("最近一次整理：").append(lastOrganizeLine()).append('\n');
        readable.append("下次整理：").append(PipelineScheduler.nextDescription(this));
        String anomalies = anomalies();
        if (!anomalies.isEmpty()) readable.append("\n异常：").append(anomalies);
        detail.setText(why
                + (snap.state == RecordingStatus.State.STALLED && usageGranted
                    ? "\n已授权 UsageStats：断流期间会用降级模式记录 app 使用时间段。" : usageHint)
                + "\n\n" + readable
                + (stats.isEmpty() ? "" : "\n\n" + stats)
                + "\n\n" + AndroidMemorySource.recallState()
                + (MemoryDb.migrationWarning.isEmpty() ? ""
                    : "\n⚠ " + MemoryDb.migrationWarning + "（DEGRADED）")
                + (usageStats.isEmpty() ? "" : "\n" + usageStats));
    }

    private String lastOrganizeLine() {
        try {
            ChainStore store = new ChainStore(this);
            ChainStore.LastRun r = store.lastRunData();
            if (r == null) return "未运行";
            String status = "done".equals(r.status) ? "成功"
                    : "failed".equals(r.status) ? "失败" : r.status;
            return status + " · " + r.chains + " 条链 · "
                    + CostEstimator.cny(r.prompt, r.cache, r.completion);
        } catch (Throwable t) {
            return "读取失败";
        }
    }

    private String anomalies() {
        try {
            TraceApp app = TraceApp.get(this);
            if (app == null || app.host() == null) return "";
            StringBuilder bad = new StringBuilder();
            for (com.dsh.core.Host.Slot slot : app.host().snapshot()) {
                if (slot.health == null || slot.health.isHealthy()) continue;
                String id = slot.module == null ? "" : slot.module.id();
                // trace 的健康就是“有没有在记录”，已在最上面单独展示，不当成插件异常
                if ("trace".equals(id)) continue;
                if (bad.length() > 0) bad.append("、");
                bad.append(id.isEmpty() ? "?" : id);
            }
            return bad.length() == 0 ? "" : bad + " 插件异常";
        } catch (Throwable t) {
            return "";
        }
    }
}
