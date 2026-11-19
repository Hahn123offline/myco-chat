package com.dsh.v2;

import android.content.Context;

import com.dsh.memory.DayRecall;
import com.dsh.memory.MemItem;
import com.dsh.memory.MemorySource;
import com.dsh.memory.Query;
import com.dsh.memory.RecallQuery;

import java.util.List;

/** Android 宿主：MemoryStore + DayNarrativeStore 适配。 */
public final class AndroidMemorySource implements MemorySource {

    private static volatile com.dsh.memory.IdfModel STATIC_IDF;
    private static volatile String WARM_STATE = "cold";
    private static volatile long WARM_MS;
    private final Context app;
    private final AndroidMemoryStore store;
    private final DayNarrativeStore days;

    public AndroidMemorySource(Context ctx) {
        app = ctx.getApplicationContext();
        store = new AndroidMemoryStore(ctx);
        store.registerNamespace("trace", true);
        store.registerNamespace("manual", true);
        store.registerNamespace("chat", true);
        store.registerNamespace("day", true);
        store.registerNamespace("event", true);
        days = new DayNarrativeStore(ctx);
    }

    /** App 启动后台预热；不阻塞主线程。 */
    public static void warmAsync(Context ctx) {
        if (STATIC_IDF != null) { WARM_STATE = "warm"; return; }
        WARM_STATE = "warming";
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                AndroidMemoryStore s = new AndroidMemoryStore(ctx);
                s.registerNamespace("trace", true);
                s.registerNamespace("manual", true);
                s.registerNamespace("chat", true);
                s.registerNamespace("day", true);
                s.registerNamespace("event", true);
                java.util.List<MemItem> all = s.query(
                        new Query().includeSuperseded(false).limit(10000));
                java.util.List<String> texts = new java.util.ArrayList<>();
                for (MemItem m : all) texts.add(m.text);
                STATIC_IDF = com.dsh.memory.IdfModel.fromTexts(texts);
                WARM_MS = System.currentTimeMillis() - t0;
                WARM_STATE = "warm";
            } catch (Throwable t) {
                WARM_STATE = "failed";
            }
        }, "recall-warm").start();
    }

    public static String recallState() {
        return "recall=" + WARM_STATE + (WARM_MS > 0 ? " (" + WARM_MS + "ms)" : "");
    }

    public long remember(String text, String kind, Long when, java.util.List<String> tags,
                         String sessionId, String userMessage) {
        return store.put(com.dsh.memory.MemoryWriter.remember(text, kind, when, tags,
                sessionId, 0, userMessage));
    }

    public AndroidMemoryStore store() { return store; }

    public java.util.List<MemItem> blocksBetween(long from, long to) {
        java.util.List<MemItem> out = new java.util.ArrayList<>();
        for (MemItem m : store.query(new Query().window(from, to).limit(10000))) {
            if ("chat".equals(m.namespace) || "manual".equals(m.namespace)) out.add(m);
        }
        return out;
    }

    public void putDay(String day, String summary, String data, long t0, long t1) {
        store.registerNamespace("day", true);
        MemItem m = new MemItem("day", com.dsh.memory.Kind.DAY, "day:" + day, summary, data);
        m.evidence = java.util.Collections.singletonList(
                new com.dsh.memory.Evidence("trace", "day:" + day, t0));
        m.weight = 1.0;
        m.t0 = t0;
        m.t1 = t1;
        m.detailLevel = com.dsh.memory.DetailLevel.FULL;
        store.put(m);
    }

    public String status() {
        java.util.List<MemItem> all = store.query(
                new Query().includeSuperseded(false).limit(10000));
        long dayStart = java.time.LocalDate.now().atStartOfDay(
                java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        int today = 0;
        for (MemItem m : all) if (m.createdAt >= dayStart) today++;
        String recent = "";
        for (int i = Math.max(0, all.size() - 3); i < all.size(); i++) {
            if (!recent.isEmpty()) recent += " … ";
            recent += com.dsh.memory.MemoryStatus.recentLine(all.get(i));
        }
        int closed = 0, appOnly = 0, unknown = 0;
        try {
            long dayEnd = dayStart + 86_400_000L;
            TraceStore ts = new TraceStore(app);
            java.util.List<com.dsh.trace.GapFinder.Span> spans = new java.util.ArrayList<>();
            java.util.List<com.dsh.trace.GapFinder.Span> content = new java.util.ArrayList<>();
            java.util.List<com.dsh.trace.GapFinder.Known> known = new java.util.ArrayList<>();
            for (com.dsh.trace.Trace t : ts.tracesBetween(dayStart, dayEnd, null)) {
                spans.add(new com.dsh.trace.GapFinder.Span(t.t0, t.t1));
                if ((t.text != null && !t.text.trim().isEmpty())
                        || (t.shotRef != null && !t.shotRef.trim().isEmpty())) {
                    content.add(new com.dsh.trace.GapFinder.Span(t.t0, t.t1));
                }
            }
            for (TraceStore.GapRow g : ts.gapsBetween(dayStart, dayEnd)) {
                known.add(new com.dsh.trace.GapFinder.Known(g.t0, g.t1, g.reason));
            }
            for (com.dsh.trace.GapFinder.Gap g : com.dsh.trace.GapFinder.find(
                    spans, content, known, dayStart, dayEnd)) {
                if ("recording_off".equals(g.reason)) closed++;
                else if ("app_only".equals(g.reason)) appOnly++;
                else unknown++;
            }
        } catch (Throwable ignored) { }
        boolean ev = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                "evidence.db").exists();
        return com.dsh.memory.MemoryStatus.format(all, today, closed, appOnly, unknown, recent)
                + (ev ? "" : "\n（其中 " + (closed + appOnly + unknown)
                + " 段的历史证据已不在本机）");
    }

    @Override public List<MemItem> candidates(RecallQuery q) {
        Query query = new Query().includeSuperseded(false).limit(10000);
        if (q != null && q.namespace != null) query.namespace(q.namespace);
        return store.query(query);
    }

    @Override public com.dsh.memory.IdfModel idf() {
        if (STATIC_IDF != null) return STATIC_IDF;
        try {
            java.util.List<MemItem> all = store.query(
                    new Query().includeSuperseded(false).limit(10000));
            java.util.List<String> texts = new java.util.ArrayList<>();
            for (MemItem m : all) texts.add(m.text);
            STATIC_IDF = com.dsh.memory.IdfModel.fromTexts(texts);
            return STATIC_IDF;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public com.dsh.memory.FrameRef readFrame(long blockId) {
        try {
            java.util.List<com.dsh.trace.Trace> xs = new TraceStore(app)
                    .tracesByIds(java.util.Collections.singletonList(blockId));
            if (xs.isEmpty()) return com.dsh.memory.FrameRef.error("块不存在: " + blockId);
            com.dsh.trace.Trace t = xs.get(0);
            com.dsh.egress.AppExclusionPolicy.Level level =
                    com.dsh.egress.AppExclusionPolicy.level(t.pkg);
            if (level == com.dsh.egress.AppExclusionPolicy.Level.NEVER_CLOUD) {
                return com.dsh.memory.FrameRef.blocked("这块属于敏感应用，不能看图");
            }
            AndroidBlockSource src = new AndroidBlockSource(app, "chat-frame",
                    java.util.Collections.singletonList(t));
            if (src.requiresMasking()) {
                return com.dsh.memory.FrameRef.blocked("掩码失败，拒绝发送原图");
            }
            byte[] jpeg = src.frame("chat-frame", blockId, 1600);
            if (jpeg == null || jpeg.length == 0) {
                return com.dsh.memory.FrameRef.blocked("拿不到掩码图，拒绝退回原图");
            }
            return com.dsh.memory.FrameRef.ok(jpeg);
        } catch (Throwable e) {
            return com.dsh.memory.FrameRef.error(e.getMessage());
        }
    }

    @Override public java.util.List<com.dsh.memory.LineItem> lines(
            com.dsh.memory.RecallQuery q) {
        java.util.List<com.dsh.memory.LineItem> out = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id,object_key,pkg,time,text,block_id,"
                + "shot_ref,line_no,y_top,kind FROM lines WHERE 1=1");
        java.util.List<String> args = new java.util.ArrayList<>();
        if (q != null && q.object != null && !q.object.isEmpty()) {
            sql.append(" AND (object_key=? OR object_key LIKE ?)");
            args.add(q.object); args.add("%" + q.object + "%");
        }
        if (q != null && q.from != null && !q.from.isEmpty()) {
            sql.append(" AND time>=?");
            args.add(String.valueOf(dayStart(q.from)));
        }
        if (q != null && q.to != null && !q.to.isEmpty()) {
            sql.append(" AND time<?");
            args.add(String.valueOf(dayStart(q.to) + 86400000L));
        }
        sql.append(" ORDER BY time LIMIT 5000");
        try (android.database.Cursor c = MemoryDb.get(app).getReadableDatabase()
                .rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                com.dsh.memory.LineItem l = new com.dsh.memory.LineItem();
                l.id = c.getLong(0); l.objectKey = c.getString(1); l.pkg = c.getString(2);
                l.time = c.getLong(3); l.text = c.getString(4); l.blockId = c.getLong(5);
                l.shotRef = c.getString(6); l.lineNo = c.getInt(7); l.yTop = c.getInt(8);
                l.kind = c.getString(9) == null ? "CONTENT" : c.getString(9);
                out.add(l);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static long dayStart(String day) {
        try {
            return java.time.LocalDate.parse(day).atStartOfDay(
                    java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Throwable t) { return 0; }
    }

    @Override public byte[] readShot(String shotRef) {
        try {
            java.io.File f = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                    shotRef);
            if (!f.exists()) return null;
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < b.length) {
                    int n = in.read(b, off, b.length - off);
                    if (n <= 0) break;
                    off += n;
                }
            }
            return b;
        } catch (Throwable t) { return null; }
    }

    @Override public com.dsh.memory.BlockRef readBlock(long id) {
        try {
            java.util.List<com.dsh.trace.Trace> xs = new TraceStore(app)
                    .tracesByIds(java.util.Collections.singletonList(id));
            if (xs.isEmpty()) return null;
            com.dsh.trace.Trace t = xs.get(0);
            com.dsh.memory.BlockRef r = new com.dsh.memory.BlockRef();
            r.id = t.id; r.text = t.text; r.maskedText = t.maskedText;
            r.pkg = t.pkg; r.t0 = t.t0; r.t1 = t.t1;
            r.shotRef = t.shotRef; r.pageId = t.pageId;
            return r;
        } catch (Throwable t) { return null; }
    }

    @Override public com.dsh.memory.DayDiagnostic diagnose(String day) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(day);
            long from = d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long to = d.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
            TraceStore ts = new TraceStore(app);
            java.util.List<com.dsh.trace.Trace> traces = ts.tracesBetween(from, to, null);
            long covered = 0;
            java.util.Map<String, Integer> pkgs = new java.util.LinkedHashMap<>();
            for (com.dsh.trace.Trace t : traces) {
                covered += Math.max(0, t.t1 - t.t0);
                String p = t.pkg == null ? "(unknown)" : t.pkg;
                pkgs.merge(p, 1, Integer::sum);
            }
            int events = 0;
            for (MemItem m : store.query(new Query().window(from, to).limit(10000))) {
                if ("event".equals(m.namespace)) events++;
            }
            int gaps = 0;
            try {
                gaps = com.dsh.trace.GapFinder.find(
                        new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                        new java.util.ArrayList<>(), from, to).size();
            } catch (Throwable ignored) { }
            return new com.dsh.memory.DayDiagnostic(day, traces.size(), covered, pkgs,
                    events, gaps);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override public boolean evidenceAvailable(String shotRef) {
        if (shotRef == null || shotRef.isEmpty()) return true;
        java.io.File trace = new java.io.File(app.getFilesDir(), "trace");
        if (!new java.io.File(trace, "evidence.db").exists()) return false;
        return new java.io.File(trace, shotRef).exists();
    }

    @Override public DayRecall day(String day) {
        DayNarrativeStore.Row r = days.get(day);
        if (r == null) return null;
        return new DayRecall(r.day, r.narrative, r.mainlines, r.gaps);
    }
}
