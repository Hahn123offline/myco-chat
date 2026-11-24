package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.dsh.models.Json;
import com.dsh.models.UrlConnectionTransport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断用：直接发原始 OpenAI 兼容请求，把完整响应 JSON 写文件（不经过 codec）。
 * adb: am broadcast -a com.dsh.v2.action.DIAGNOSE_MODEL -p com.dsh.v2
 */
public final class ModelDiagnoseReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.DIAGNOSE_MODEL";
    private static final String TAG = "DSHL2";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                run(context.getApplicationContext());
            } catch (Throwable t) {
                Log.e(TAG, "diagnose failed", t);
            } finally {
                try { pending.finish(); } catch (Throwable ignored) { }
            }
        }, "model-diagnose").start();
    }

    private void run(Context ctx) {
        TraceApp app = TraceApp.get(ctx);
        String key = app.secrets().keyFor("deepseek");
        File out = new File(new File(ctx.getFilesDir(), "trace"), "model_diag.txt");
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write("=== keyPresent=" + (!key.isEmpty()) + " base=https://api.deepseek.com ===\n");
            if (key.isEmpty()) {
                w.write("NO KEY\n");
                return;
            }
            // 1) test connection 的实际参数：max_tokens=16
            w.write(answer(ctx, key, "deepseek-flash", false, 16,
                    "You are a connectivity probe. Reply with the single word OK.", false) + "\n");
            // 2) flash 带 tools + 明确要求调用工具
            w.write(answer(ctx, key, "deepseek-flash", true, 0,
                    "You must call the read_frame tool with block_id 1. Do not reply with text.",
                    true) + "\n");
            // 3) chat 带 tools + 同样要求
            w.write(answer(ctx, key, "deepseek-chat", true, 0,
                    "You must call the read_frame tool with block_id 1. Do not reply with text.",
                    true) + "\n");
            // 4) flash 带 tools，max_tokens=16（testConnection 场景）
            w.write(answer(ctx, key, "deepseek-flash", true, 16,
                    "You are a connectivity probe. Reply with the single word OK.", false) + "\n");
            // 5) flash 带 tools + 明确要求工具 + max_tokens=16（thinking 可能吃光预算）
            w.write(answer(ctx, key, "deepseek-flash", true, 16,
                    "You must call the read_frame tool with block_id 1. Do not reply with text.",
                    false) + "\n");
            // 6) flash 带 tools + 明确要求工具 + max_tokens 不设（L2 实际形态）
            w.write(answer(ctx, key, "deepseek-flash", true, 0,
                    "You must call the read_frame tool with block_id 1. Do not reply with text.",
                    false) + "\n");
        } catch (Throwable t) {
            Log.e(TAG, "diagnose write failed", t);
        }
    }

    private String answer(Context ctx, String key, String model, boolean tools,
                          int maxTokens, String prompt, boolean forceTool) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + key);
            StringBuilder b = new StringBuilder();
            b.append("{\"model\":").append(Json.quote(model))
                    .append(",\"messages\":[{\"role\":\"user\",\"content\":")
                    .append(Json.quote(prompt)).append("}]");
            if (maxTokens > 0) b.append(",\"max_tokens\":").append(maxTokens);
            b.append(",\"temperature\":0.2,\"stream\":false");
            if (tools) {
                b.append(",\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"read_frame\","
                        + "\"description\":\"read one frame\",\"parameters\":{\"type\":\"object\","
                        + "\"properties\":{\"block_id\":{\"type\":\"integer\"}},\"required\":[\"block_id\"]}}}],"
                        + "\"tool_choice\":" + (forceTool ? "\"required\"" : "\"auto\""));
            }
            b.append('}');
            UrlConnectionTransport http = new UrlConnectionTransport();
            com.dsh.models.HttpTransport.Response r = http.post(
                    "https://api.deepseek.com/v1/chat/completions", headers,
                    b.toString().getBytes(StandardCharsets.UTF_8), 60_000);
            return "=== model=" + model + " tools=" + tools + " max_tokens=" + maxTokens
                    + " forceTool=" + forceTool + " ===\n"
                    + "HTTP " + r.status + "\n" + r.body + "\n";
        } catch (Throwable t) {
            return "=== model=" + model + " tools=" + tools + " ===\nEXCEPTION "
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + "\n";
        }
    }
}
