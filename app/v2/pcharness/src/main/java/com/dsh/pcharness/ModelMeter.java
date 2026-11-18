package com.dsh.pcharness;

import com.dsh.models.ModelResponse;

/** 评测用模型调用计量。 */
public final class ModelMeter {
    public static int calls;
    public static long latencyMs;
    public static int promptTokens;
    public static int completionTokens;
    public static final java.util.List<Long> latencies = new java.util.ArrayList<>();

    private ModelMeter() { }

    public static synchronized void record(ModelResponse r) {
        if (r == null) return;
        calls++;
        latencyMs += Math.max(0, r.latencyMs);
        latencies.add(Math.max(0, r.latencyMs));
        promptTokens += Math.max(0, r.promptTokens);
        completionTokens += Math.max(0, r.completionTokens);
    }

    public static synchronized void reset() {
        calls = 0; latencyMs = 0; promptTokens = 0; completionTokens = 0;
        latencies.clear();
    }

    /** 估算成本（DeepSeek 空闲档量级，美元）。 */
    public static double costUsd() {
        return promptTokens / 1_000_000.0 * 0.15
                + completionTokens / 1_000_000.0 * 0.60;
    }

    public static long percentile(double p) {
        if (latencies.isEmpty()) return 0;
        java.util.List<Long> xs = new java.util.ArrayList<>(latencies);
        java.util.Collections.sort(xs);
        int i = (int) Math.min(xs.size() - 1, Math.floor(p / 100.0 * xs.size()));
        return xs.get(Math.max(0, i));
    }
}
