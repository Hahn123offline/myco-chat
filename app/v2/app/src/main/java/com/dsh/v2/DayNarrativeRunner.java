package com.dsh.v2;

import android.content.Context;
import android.util.Log;

import com.dsh.egress.EgressTier;
import com.dsh.models.Capability;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.Quality;
import com.dsh.models.SimpleTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** L3：一天一次，生成带 event_ids 指针的日叙事索引。 */
public final class DayNarrativeRunner {

    public static final String TAG = "DSHL2";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "day-narrative");
        t.setDaemon(true);
        return t;
    });

    public interface Callback { void onDone(boolean ok, String message); }

    private DayNarrativeRunner() { }

    public static void runYesterday(Context ctx, Callback cb) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -1);
        run(ctx, c, cb);
    }

    public static void run(Context ctx, Calendar day, Callback cb) {
        IO.execute(() -> runBlocking(ctx, day, cb));
    }

    private static void runBlocking(Context ctx, Calendar day, Callback cb) {
        try {
            Calendar start = (Calendar) day.clone();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_YEAR, 1);
            String dayKey = String.format("%04d-%02d-%02d",
                    start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1,
                    start.get(Calendar.DAY_OF_MONTH));
            if (!new java.io.File(new java.io.File(ctx.getFilesDir(), "trace"),
                    "evidence.db").exists()) {
                if (cb != null) cb.onDone(false, "历史证据缺失，无法重算");
                return;
            }
            ChainStore chains = new ChainStore(ctx);
            List<ChainStore.ChainRow> rows = chains.doneEventsBetween(
                    start.getTimeInMillis(), end.getTimeInMillis());
            if (rows.size() < 2) {
                if (cb != null) cb.onDone(true, "day narrative skip: done events=" + rows.size());
                return;
            }
            List<DayNarrativeBuilder.Event> events = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            TraceStore sourceStore = new TraceStore(ctx);
            StringBuilder ids = new StringBuilder();
            StringBuilder hashInput = new StringBuilder();
            for (ChainStore.ChainRow r : rows) {
                String oc = outcome(chains, r.id);
                DayNarrativeBuilder.Event ev = new DayNarrativeBuilder.Event(r.id, r.title,
                        r.body, r.t0, r.t1, r.nBlocks, oc);
                ev.coveredMs = r.coveredMs;
                events.add(ev);
                if (ids.length() > 0) ids.append(',');
                ids.append(r.id);
                for (String pkg : sourceStore.pkgsForCsv(r.blockIds)) {
                    if (!sources.contains(pkg)) sources.add(pkg);
                }
                hashInput.append(r.id).append('|').append(r.title).append('|')
                        .append(ChainStore.sha1(r.body)).append('|').append(oc).append('\n');
            }
            List<com.dsh.trace.GapFinder.Gap> programGaps =
                    programGaps(ctx, start.getTimeInMillis(), end.getTimeInMillis());
            List<String> gapTexts = new ArrayList<>();
            for (com.dsh.trace.GapFinder.Gap g : programGaps) gapTexts.add(g.text);
            List<String> gaps = gapTexts;
            TraceApp app = TraceApp.get(ctx);
            com.dsh.agent.LlmCaller caller = req -> app.modelsModule().router().execute(
                    new SimpleTask("day.narrative", java.util.Collections.singleton(Capability.JSON),
                            Quality.GOOD, EgressTier.WHITE), req);
            DayNarrativeBuilder.Parsed parsed = null;
            List<String> errors = new ArrayList<>();
            String firstError = "";
            AndroidMemorySource memory = new AndroidMemorySource(ctx);
            List<DayNarrativeBuilder.Memory> memories = new ArrayList<>();
            StringBuilder memHash = new StringBuilder();
            for (com.dsh.memory.MemItem m : memory.blocksBetween(
                    start.getTimeInMillis(), end.getTimeInMillis())) {
                String ev = "chat";
                if (m.evidence != null) for (com.dsh.memory.Evidence e : m.evidence) {
                    if (e != null && e.type != null && !e.type.isEmpty()) { ev = e.type; break; }
                }
                memories.add(new DayNarrativeBuilder.Memory(m.id, m.text, ev, m.t0));
                memHash.append(m.id).append('|').append(ChainStore.sha1(m.text)).append('\n');
            }
            String inputSha = ChainStore.sha1(hashInput.toString() + memHash);
            DayNarrativeStore store = new DayNarrativeStore(ctx);
            DayNarrativeStore.Row old = store.get(dayKey);
            if (old != null && inputSha.equals(old.inputSha1)) {
                Log.i(TAG, "day narrative: reuse/skip day=" + dayKey
                        + " input_sha1=" + inputSha);
                if (cb != null) cb.onDone(true, "day narrative: skip (input unchanged)");
                return;
            }
            String promptText = DayNarrativeBuilder.userPrompt(dayKey, events, gaps,
                    memories, "材料 " + events.size() + " 事件/" + memories.size() + " 条在场记忆");
            for (int attempt = 0; attempt < 2; attempt++) {
                String user = promptText + (errors.isEmpty() ? ""
                        : "\n\n上一次输出未通过校验：" + String.join("；", errors)
                        + "\n请修正后重新输出完整 JSON。");
                ModelResponse r = caller.call(ModelRequest.text(DayNarrativeBuilder.SYSTEM, user)
                        .withThinking("enabled")
                        .withProvenance(sources, com.dsh.egress.EgressStage.DERIVED));
                if (r == null || !r.isOk() || r.text == null) {
                    firstError = "model: " + (r == null ? "null" : r.error);
                    continue;
                }
                try {
                    parsed = DayNarrativeBuilder.parse(r.text);
                    parsed = DayNarrativeBuilder.repair(parsed, events);
                    errors = DayNarrativeBuilder.validate(parsed, events, gaps,
                            !memories.isEmpty());
                    if (errors.isEmpty()) break;
                    if (firstError.isEmpty()) firstError = String.join("；", errors);
                } catch (Throwable t) {
                    firstError = "parse: " + t.getMessage();
                    errors = new ArrayList<>();
                    errors.add("JSON 解析失败");
                }
            }
            int partial = parsed != null && errors.isEmpty() ? 0 : 1;
            if (partial == 0) firstError = "";
            if (parsed == null) {
                if (cb != null) cb.onDone(false, "day narrative failed: " + firstError);
                return;
            }
            String segJson = jsonSegments(parsed);
            String mainJson = jsonMainlines(parsed);
            String threadJson = jsonThreads(parsed);
            store.put(dayKey, parsed.narrative, segJson, mainJson, threadJson,
                    jsonStrings(gapTexts), jsonLongs(parsed.unmentioned), ids.toString(),
                    inputSha, partial, firstError, "deepseek", 0, 0, parsed.repaired);
            try {
                StringBuilder summary = new StringBuilder();
                for (DayNarrative.Mainline m : parsed.mainlines) {
                    if (summary.length() > 0) summary.append("；");
                    summary.append(m.title.isEmpty() ? m.text : m.title);
                }
                String summaryText = summary.length() > 800
                        ? summary.substring(0, 800) : summary.toString();
                memory.putDay(dayKey, summaryText,
                        dayData(parsed, gapTexts), start.getTimeInMillis(),
                        end.getTimeInMillis() - 1);
            } catch (Throwable t) {
                Log.w(TAG, "day memory write failed", t);
            }
            String warn = DayNarrativeBuilder.narrativeWarning(parsed.narrative);
            String cov = DayNarrativeBuilder.coverageWarning(parsed, events);
            Log.i(TAG, "day narrative: day=" + dayKey + " events=" + events.size()
                    + " mainlines=" + parsed.mainlines.size() + " partial=" + partial
                    + (warn.isEmpty() ? "" : " " + warn) + " " + cov + " err=" + firstError);
            if (cb != null) cb.onDone(true, "day narrative day=" + dayKey + " partial=" + partial);
        } catch (Throwable t) {
            Log.e(TAG, "day narrative failed", t);
            if (cb != null) cb.onDone(false, "day narrative failed: " + t.getClass().getSimpleName());
        }
    }

    private static String dayData(DayNarrativeBuilder.Parsed p, List<String> gaps) {
        StringBuilder b = new StringBuilder("{\"narrative\":")
                .append(com.dsh.models.Json.quote(p.narrative))
                .append(",\"mainlines\":").append(jsonMainlines(p))
                .append(",\"open_threads\":").append(jsonThreads(p))
                .append(",\"repaired\":").append(p.repaired)
                .append(",\"presence_notes\":[");
        for (int i = 0; i < p.presenceNotes.size(); i++) {
            if (i > 0) b.append(',');
            DayNarrativeBuilder.Presence x = p.presenceNotes.get(i);
            b.append("{\"text\":").append(com.dsh.models.Json.quote(x.text))
                    .append(",\"memory_ids\":").append(jsonLongs(x.memoryIds)).append('}');
        }
        return b.append("],\"gaps\":").append(jsonStrings(gaps)).append('}').toString();
    }

    private static String outcome(ChainStore chains, long id) {
        return chains.outcomeById(id);
    }

    private static List<com.dsh.trace.GapFinder.Gap> programGaps(
            Context ctx, long from, long to) {
        List<com.dsh.trace.GapFinder.Span> spans = new ArrayList<>();
        List<com.dsh.trace.GapFinder.Span> content = new ArrayList<>();
        List<com.dsh.trace.GapFinder.Known> known = new ArrayList<>();
        try {
            TraceStore ts = new TraceStore(ctx);
            for (com.dsh.trace.Trace t : ts.tracesBetween(from, to, null)) {
                spans.add(new com.dsh.trace.GapFinder.Span(t.t0, t.t1));
                boolean hasContent = (t.text != null && !t.text.trim().isEmpty())
                        || (t.shotRef != null && !t.shotRef.trim().isEmpty());
                if (hasContent) content.add(new com.dsh.trace.GapFinder.Span(t.t0, t.t1));
            }
            for (TraceStore.GapRow g : ts.gapsBetween(from, to)) {
                known.add(new com.dsh.trace.GapFinder.Known(g.t0, g.t1, g.reason));
            }
        } catch (Throwable ignored) { }
        return com.dsh.trace.GapFinder.find(spans, content, known, from, to);
    }

    private static String jsonMainlines(DayNarrativeBuilder.Parsed p) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < p.mainlines.size(); i++) {
            DayNarrative.Mainline m = p.mainlines.get(i);
            if (i > 0) b.append(',');
            b.append("{\"title\":").append(com.dsh.models.Json.quote(m.title))
                    .append(",\"text\":").append(com.dsh.models.Json.quote(m.text))
                    .append(",\"event_ids\":").append(jsonLongs(m.eventIds))
                    .append(",\"memory_ids\":").append(jsonLongs(m.memoryIds))
                    .append(",\"time_hint\":").append(com.dsh.models.Json.quote(m.timeHint))
                    .append('}');
        }
        return b.append(']').toString();
    }

    private static String jsonSegments(DayNarrativeBuilder.Parsed p) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < p.segments.size(); i++) {
            DayNarrative.Segment s = p.segments.get(i);
            if (i > 0) b.append(',');
            b.append("{\"text\":").append(com.dsh.models.Json.quote(s.text))
                    .append(",\"event_ids\":").append(jsonLongs(s.eventIds))
                    .append(",\"time_hint\":").append(com.dsh.models.Json.quote(s.timeHint))
                    .append('}');
        }
        return b.append(']').toString();
    }

    private static String jsonThreads(DayNarrativeBuilder.Parsed p) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < p.threads.size(); i++) {
            DayNarrative.Thread t = p.threads.get(i);
            if (i > 0) b.append(',');
            b.append("{\"text\":").append(com.dsh.models.Json.quote(t.text))
                    .append(",\"event_ids\":").append(jsonLongs(t.eventIds))
                    .append(",\"memory_ids\":").append(jsonLongs(t.memoryIds)).append('}');
        }
        return b.append(']').toString();
    }

    private static String jsonStrings(List<String> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(com.dsh.models.Json.quote(xs.get(i)));
        }
        return b.append(']').toString();
    }

    private static String jsonLongs(List<Long> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) b.append(',');
            b.append(xs.get(i));
        }
        return b.append(']').toString();
    }
}
