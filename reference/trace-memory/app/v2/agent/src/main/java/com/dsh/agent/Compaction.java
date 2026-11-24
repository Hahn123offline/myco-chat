package com.dsh.agent;

import com.dsh.models.ChatMessage;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;

import java.util.ArrayList;
import java.util.List;

/** 照 ICL compaction：按字符触发、按 tool 组切割、模型压成第一人称回忆。 */
public final class Compaction {

    public static final int TRIGGER_CHARS = 12_000;
    public static final int KEEP_RECENT_GROUPS = 12;
    public static final int MAX_CHARS = 1_200;
    public static final int MAX_TOKENS = 600;

    public interface Sink {
        void save(String summary, int dropped);
    }

    public static final class Result {
        public final boolean ok;
        public final boolean skipped;
        public final String summary;
        public final int dropped;
        public final String error;
        public final List<ChatMessage> messages;
        Result(boolean ok, boolean skipped, String summary, int dropped,
               String error, List<ChatMessage> messages) {
            this.ok = ok; this.skipped = skipped; this.summary = summary == null ? "" : summary;
            this.dropped = dropped; this.error = error == null ? "" : error;
            this.messages = messages;
        }
    }

    private Compaction() { }

    public static Result maybe(List<ChatMessage> in, LlmCaller caller, Sink sink) {
        int chars = 0;
        for (ChatMessage m : in) chars += text(m).length();
        if (chars < TRIGGER_CHARS) {
            return new Result(true, true, "", 0, "", in);
        }
        Compaction c = new Compaction();
        return c.compact(in, caller, sink);
    }

    private Result compact(List<ChatMessage> in, LlmCaller caller, Sink sink) {
        int sys = (!in.isEmpty() && "system".equals(in.get(0).role)) ? 1 : 0;
        int start = sys;
        List<int[]> groups = new ArrayList<>();
        while (start < in.size()) {
            int i = start;
            // 一组从一条 assistant 开始（或开头的 user），到下一个 assistant 前
            int end;
            if ("assistant".equals(in.get(i).role)) {
                end = i + 1;
                while (end < in.size() && !"assistant".equals(in.get(end).role)) end++;
            } else {
                end = i + 1;
                while (end < in.size() && !"assistant".equals(in.get(end).role)) end++;
            }
            groups.add(new int[]{i, end});
            start = end;
        }
        if (groups.size() <= KEEP_RECENT_GROUPS) {
            return new Result(true, true, "", 0, "", in);
        }
        int cutGroup = groups.size() - KEEP_RECENT_GROUPS;
        int cutIndex = groups.get(cutGroup)[0];
        List<ChatMessage> older = in.subList(sys, cutIndex);
        StringBuilder text = new StringBuilder();
        for (ChatMessage m : older) {
            text.append("user".equals(m.role) ? "用户：" : "我：")
                    .append(text(m)).append('\n');
        }
        String summary;
        try {
            ModelResponse r = caller.call(ModelRequest.text(
                    "把上面这段较早的对话，用你自己的口吻写成一小段回忆。\n"
                            + "保留事实、决定、没做完的事；不要评价，不要总结成报告。",
                    text.toString()).withMaxTokens(MAX_TOKENS).withThinking("disabled")
                    .withProvenance(java.util.Collections.emptyList(),
                            com.dsh.egress.EgressStage.DERIVED));
            if (r == null || !r.isOk() || r.text == null || r.text.trim().isEmpty()) {
                return new Result(false, false, "", 0, "empty-summary", in);
            }
            summary = r.text.trim();
            if (summary.length() > MAX_CHARS) summary = summary.substring(0, MAX_CHARS);
        } catch (Throwable t) {
            return new Result(false, false, "", 0, "summarize-fail: " + t.getMessage(), in);
        }
        if (sink != null) sink.save(summary, older.size());
        List<ChatMessage> out = new ArrayList<>();
        for (int k = 0; k < sys; k++) out.add(in.get(k));
        out.add(ChatMessage.user("【早前对话的压缩摘要】\n" + summary));
        out.addAll(in.subList(cutIndex, in.size()));
        try {
            TranscriptValidator.assertLegal(out);
        } catch (Throwable t) {
            return new Result(false, false, "", 0, "illegal-after-compaction: " + t.getMessage(), in);
        }
        return new Result(true, false, summary, older.size(), "", out);
    }

    private static String text(ChatMessage m) {
        if (m == null) return "";
        if (m.content != null) return m.content;
        return "";
    }
}
