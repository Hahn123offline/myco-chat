package com.dsh.redaction;

import com.dsh.host.api.Config;
import com.dsh.host.api.Log;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.llm.LlmRequest;
import com.dsh.host.api.llm.LlmResponse;
import com.dsh.host.api.llm.LlmService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 模型辅助脱敏（第二层）。**规则层永远先跑，模型只能补充掩码，不能推翻规则。**
 *
 * 工作方式（与"规则做校验、不做理解"的原则一致）：
 *  1. 输入是**已经过规则层脱敏的文本**，模型看到的不是原文（手机号/身份证已是 *****）；
 *  2. prompt 让模型"逐行列出隐私片段"（措辞可配，默认极简；最终版由评测决定）；
 *  3. 我们用**纯规则校验**决定要不要采纳模型的每一条输出：
 *       - 片段必须是入参文本里真实出现的连续子串（模型不能凭空造）；
 *       - 长度 2..N（太长是整段回吐/幻觉，丢）；
 *       - 不能含 '*'（已经掩过 / 掩码标记，丢）；
 *       - 和规则层已有掩码区间重叠的片段不能替换（span 保护，规则优先）；
 *       - 采纳后重新校验：文本长度不变、每个规则掩码区间原样保留、掩码数量只增不减。
 *  4. 任一校验失败 → 丢模型结果，返回 RULE 级原文。绝不抛异常、绝不阻塞实时主流程
 *     （异步回调；批处理走阻塞版但自带超时）。
 *
 * prompt 来源：优先 `prompt.redact` 配置项；缺省用最简单的占位（不在这里写死评测措辞）。
 */
public final class ModelRedactor {

    public static final String TASK_ID = "redact";

    /** 占位 prompt：只给最基本的格式（逐行列出），最终措辞由评测决定后写进配置 */
    public static final String DEFAULT_PROMPT = "逐行列出这段文本里的隐私片段；没有就输出 NONE。";

    /** 采纳结果 */
    public static final class Result {
        /** 最终文本：被拒绝时就是入参的 rule-masked 文本（原样） */
        public final String text;
        /** RedactionLevel.RULE / MODEL */
        public final int level;
        /** 模型这次新增了几个掩码 */
        public final int addedMasks;
        /** 非空 = 这次模型结果被拒绝/没采用的原因（可观测用） */
        public final String rejectReason;

        public Result(String text, int level, int addedMasks, String rejectReason) {
            this.text = text == null ? "" : text;
            this.level = level;
            this.addedMasks = addedMasks;
            this.rejectReason = rejectReason == null ? "" : rejectReason;
        }

        public boolean isModelLevel() { return level == RedactionLevel.MODEL; }

        @Override public String toString() {
            return "ModelResult{level=" + RedactionLevel.name(level)
                    + ", added=" + addedMasks
                    + (rejectReason.isEmpty() ? "" : ", rejected=" + rejectReason) + "}";
        }
    }

    public interface Callback {
        void onResult(Result r);
    }

    private final Supplier<Config> configs;
    private final Supplier<LlmService> llmService;
    private final Log log;

    public ModelRedactor(Supplier<Config> configs, Supplier<LlmService> llmService, Log log) {
        this.configs = configs;
        this.llmService = llmService;
        this.log = log;
    }

    /**
     * 异步版：实时层用。不阻塞调用线程；回调恰好一次，绝不让异常冒出去。
     *
     * @param ruleMaskedText 规则层脱敏后的文本（模型看到的输入）
     * @param ruleMaskedTokens 规则层已知的掩码 token（span 保护用；可空，内部仍会扫一遍形态）
     */
    public void redactAsync(String ruleMaskedText, List<String> ruleMaskedTokens, Callback cb) {
        final String text = ruleMaskedText == null ? "" : ruleMaskedText;
        try {
            if (text.isEmpty()) {
                cb.onResult(ruleOnly(text, "empty text"));
                return;
            }
            LlmService llm = llmService.get();
            if (llm == null) {
                cb.onResult(ruleOnly(text, "no LlmService"));
                return;
            }
            Config c = configs.get();
            String prompt = c.getString("prompt.redact", DEFAULT_PROMPT);
            int maxTokens = Math.max(16, c.getInt("redact.maxTokens", 128));
            LlmRequest req = LlmRequest.builder(TASK_ID)
                    .systemPrompt(prompt)
                    .userPrompt(text)
                    .maxTokens(maxTokens)
                    .temperature(0f)
                    .build();

            final long t0 = System.nanoTime();
            llm.complete(req, resp -> {
                Result r;
                try {
                    r = apply(text, resp, c, ruleMaskedTokens);
                } catch (Throwable t) {
                    r = ruleOnly(text, "apply threw: " + t.getClass().getSimpleName());
                }
                if (log != null) {
                    log.i("redaction", "model redact task=" + TASK_ID
                            + " latency=" + (System.nanoTime() - t0) / 1_000_000 + "ms " + r);
                }
                cb.onResult(r);
            });
        } catch (Throwable t) {
            // 任何意外都不能把实时链路带倒
            try { cb.onResult(ruleOnly(text, "redactAsync threw: " + t.getClass().getSimpleName())); }
            catch (Throwable ignored) { }
        }
    }

    /**
     * 阻塞版：批处理用（在后台线程里跑）。超时/失败都返回 RULE 原文。
     * @param timeoutMs 等模型结果的上限；超过就放弃（不阻塞批处理队列）
     */
    public Result redactBlocking(String ruleMaskedText, List<String> ruleMaskedTokens, long timeoutMs) {
        final String text = ruleMaskedText == null ? "" : ruleMaskedText;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Result> out = new AtomicReference<>();
        final AtomicBoolean done = new AtomicBoolean(false);
        redactAsync(text, ruleMaskedTokens, r -> {
            if (done.compareAndSet(false, true)) {
                out.set(r);
                latch.countDown();
            }
        });
        try {
            if (!latch.await(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS)) {
                done.set(true);
                return ruleOnly(text, "model timeout " + timeoutMs + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            done.set(true);
            return ruleOnly(text, "interrupted");
        }
        Result r = out.get();
        return r == null ? ruleOnly(text, "no result") : r;
    }

    // ---------------- 校验与采纳（纯逻辑，全部可单测） ----------------

    Result apply(String text, LlmResponse resp, Config cfg, List<String> ruleMaskedTokens) {
        if (resp == null) return ruleOnly(text, "null response");
        if (!resp.isOk()) return ruleOnly(text, "model error: " + resp.error);

        String raw = resp.text == null ? "" : resp.text.trim();
        if (raw.isEmpty()) return ruleOnly(text, "empty output");
        if (isNone(raw)) return new Result(text, RedactionLevel.MODEL, 0, "");

        // 1) 明显是垃圾的大输出（比如小模型把 prompt/schema 回吐出来）→ 直接丢
        int maxOut = Math.max(200, text.length() * 3);
        if (raw.length() > maxOut) {
            return ruleOnly(text, "model output too long (" + raw.length() + " > " + maxOut + ")");
        }

        int maxFragLen = clamp(cfg.getInt("redact.maxFragmentChars", 40), 2, 200);
        int maxNew = clamp(cfg.getInt("redact.maxNewMasks", 20), 1, 500);

        List<String> candidates = parseCandidates(raw, text, maxFragLen);
        if (candidates.isEmpty()) return ruleOnly(text, "no usable fragment in model output");

        // 2) span 保护：规则层已有掩码区间（形态扫描 = 校验，不做理解）
        List<MaskTokens.Span> forbidden = new ArrayList<>(maskSpans(text, ruleMaskedTokens));
        int beforeMasks = forbidden.size();

        StringBuilder sb = new StringBuilder(text);
        int added = 0;
        for (String cand : candidates) {
            if (added >= maxNew) break;
            int from = 0;
            while (added < maxNew) {
                int idx = text.indexOf(cand, from);
                if (idx < 0) break;
                from = idx + Math.max(1, cand.length());
                int end = idx + cand.length();
                if (MaskTokens.overlapsAny(forbidden, idx, end)) continue;  // 规则优先，跳过
                sb.replace(idx, end, MaskTokens.maskFragment(cand));
                forbidden.add(new MaskTokens.Span(idx, end, cand));
                added++;
            }
        }
        if (added == 0) return ruleOnly(text, "no fragment applied (all overlap rule masks)");

        String finalText = sb.toString();

        // 3) 输出校验：变短 / 掩码被还原 / 掩码数量变少 → 丢
        String why = validate(text, finalText, beforeMasks, ruleMaskedTokens);
        if (why != null) return ruleOnly(text, why);

        return new Result(finalText, RedactionLevel.MODEL, added, "");
    }

    private List<String> parseCandidates(String modelOutput, String text, int maxFragLen) {
        List<String> list = new ArrayList<>();
        for (String rawLine : modelOutput.split("\\r?\\n")) {
            String s = cleanLine(rawLine);
            if (s.isEmpty()) continue;
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.equals("none") || lower.equals("no") || s.equals("无")
                    || s.equals("没有") || s.equals("无隐私片段")) {
                continue;
            }
            if (!isValidFragment(s, text, maxFragLen)) {
                if (log != null) log.w("redaction", "丢弃不可采纳的模型片段: " + s);
                continue;
            }
            if (!list.contains(s)) list.add(s);
        }
        return list;
    }

    /** 去掉列表符号/引号，但不动内容本身（片段可能是数字账号，不能去掉前导数字） */
    private static String cleanLine(String raw) {
        String s = raw == null ? "" : raw.trim();
        s = s.replaceFirst("^[-*•·–—]+\\s*", "");
        s = s.replaceFirst("^\\d{1,2}[.、)）]\\s+", "");
        s = s.trim();
        s = stripQuotes(s);
        return s.trim();
    }

    private static String stripQuotes(String s) {
        while (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            boolean pair = (a == '"' && b == '"') || (a == '\'' && b == '\'')
                    || (a == '“' && b == '”') || (a == '‘' && b == '’') || (a == '`' && b == '`');
            if (!pair) break;
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /** 片段合法性：只做可判定校验，不判断语义 */
    static boolean isValidFragment(String s, String text, int maxFragLen) {
        if (s == null) return false;
        int n = s.length();
        if (n < 2 || n > maxFragLen) return false;
        if (s.indexOf('*') >= 0) return false;                    // 已经是掩码/带掩码
        if (s.equals(MaskTokens.VERIFY_DROPPED)) return false;    // 固定标记不是原始片段
        if (n >= text.length()) return false;                     // 整段回吐不算片段
        return text.contains(s);
    }

    /** 输出校验（包内可见，单测直接断言"变短/掩码被还原/长度变化"都会被拒） */
    static String validate(String before, String after, int beforeMaskCount,
                           List<String> ruleMaskedTokens) {
        if (after.length() < before.length()) {
            return "text 变短 (" + after.length() + " < " + before.length() + ")";
        }
        if (after.length() != before.length()) {
            return "text 长度变化 (" + after.length() + " != " + before.length() + ")";
        }
        // 等长替换 ⇒ 位置不变，可以逐区间核对规则掩码没有被还原
        List<MaskTokens.Span> spans = maskSpans(before, ruleMaskedTokens);
        for (MaskTokens.Span s : spans) {
            if (s.end > after.length()) return "规则掩码区间越界";
            String oldSeg = before.substring(s.start, s.end);
            String newSeg = after.substring(s.start, s.end);
            if (!oldSeg.equals(newSeg)) {
                return "规则掩码被改动: '" + oldSeg + "' -> '" + newSeg + "'";
            }
        }
        int afterMaskCount = MaskTokens.scan(after).size();
        if (afterMaskCount < beforeMaskCount) {
            return "掩码数量变少 (" + afterMaskCount + " < " + beforeMaskCount + ")";
        }
        return null;
    }

    /** 规则层 token + 形态扫描的并集（确定性，不猜语义；去重防止同一区间被数两次） */
    private static List<MaskTokens.Span> maskSpans(String text, List<String> knownTokens) {
        List<MaskTokens.Span> spans = new ArrayList<>(MaskTokens.scan(text));
        if (knownTokens != null) {
            for (String token : knownTokens) {
                if (token == null || token.isEmpty()) continue;
                int from = 0;
                while (true) {
                    int idx = text.indexOf(token, from);
                    if (idx < 0) break;
                    spans.add(new MaskTokens.Span(idx, idx + token.length(), token));
                    from = idx + Math.max(1, token.length());
                }
            }
        }
        return MaskTokens.union(spans);
    }

    private static boolean isNone(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.equals("none") || t.equals("no") || t.equals("无") || t.equals("没有")
                || t.equals("无隐私片段") || t.equals("none.");
    }

    private static Result ruleOnly(String text, String reason) {
        return new Result(text, RedactionLevel.RULE, 0, reason);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
