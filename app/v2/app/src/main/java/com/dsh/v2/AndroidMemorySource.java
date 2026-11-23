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

    /**
     * 契约入口：另一仓只需要拿到这个接口，不需要知道 AndroidMemoryStore 的存在。
     */
    public com.dsh.contract.MemoryReader reader() {
        return new com.dsh.memory.api.ContractRecall(this);
    }

    public static com.dsh.contract.MemoryReader readerOf(Context ctx) {
        return new AndroidMemorySource(ctx).reader();
    }

    /**
     * 数据已处理到哪个时间点 = MAX(lines.time) 与 MAX(object_messages.t_last) 的较大值。
     *
     * 这两个前沿会分叉（实测差过 18 小时：lines 到 11-23 13:19，object_messages 停在 11-22 02:53），
     * 取较大值会让 freshness 显得乐观，所以同时提供 {@link #projectionLag()} 给排查用。
     * 取较大值是为了回答"我最多看到了多久之前的事"，而 object_messages 的滞后由 lag 字段暴露。
     */
    @Override public long projectedUntil() {
        long lines = 0L, objmsg = 0L;
        try {
            android.database.sqlite.SQLiteDatabase db =
                    MemoryDb.get(app).getReadableDatabase();
            try (android.database.Cursor c = db.rawQuery("SELECT MAX(time) FROM lines", null)) {
                if (c.moveToFirst() && !c.isNull(0)) lines = c.getLong(0);
            }
            try (android.database.Cursor c = db.rawQuery(
                    "SELECT MAX(t_last) FROM object_messages", null)) {
                if (c.moveToFirst() && !c.isNull(0)) objmsg = c.getLong(0);
            }
        } catch (Throwable ignored) { }
        return Math.max(lines, objmsg);
    }

    /** 两条投影前沿的分叉量（毫秒）。用于诊断"对象视图比文本层旧多少"。 */
    public long projectionLag() {
        long lines = 0L, objmsg = 0L;
        try {
            android.database.sqlite.SQLiteDatabase db =
                    MemoryDb.get(app).getReadableDatabase();
            try (android.database.Cursor c = db.rawQuery("SELECT MAX(time) FROM lines", null)) {
                if (c.moveToFirst() && !c.isNull(0)) lines = c.getLong(0);
            }
            try (android.database.Cursor c = db.rawQuery(
                    "SELECT MAX(t_last) FROM object_messages", null)) {
                if (c.moveToFirst() && !c.isNull(0)) objmsg = c.getLong(0);
            }
        } catch (Throwable ignored) { }
        if (lines <= 0 || objmsg <= 0) return -1L;
        return Math.max(0L, lines - objmsg);
    }

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
        // 原实现完全忽略 RecallQuery 的 from/to：不管问什么都捞 10000 条全表，
        // 再由 RecallCore 在内存里打分 —— 实测一次 recall 120 秒以上。
        // 这里至少把时间窗下推到 SQL；query/object/pkg 仍需上层过滤（见 TODO）。
        Query query = new Query().includeSuperseded(false).limit(CANDIDATE_LIMIT);
        if (q != null && q.namespace != null) query.namespace(q.namespace);
        if (q != null) {
            Long from = hasTime(q.from) ? parseMs(q.from) : null;
            Long to = hasTime(q.to) ? parseMs(q.to) + 86_400_000L : null;   // to 含当天
            if (from != null || to != null) query.window(from, to);
        }
        return store.query(query);
    }

    /** 候选上限：BM25 在内存里跑，条数直接决定延迟。 */
    private static final int CANDIDATE_LIMIT = 3000;

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

    @Override public java.util.List<com.dsh.memory.ObjectMessage> messages(String object, int limit) {
        java.util.List<com.dsh.memory.ObjectMessage> out = new java.util.ArrayList<>();
        if (object == null || object.isEmpty()) return out;
        try (android.database.Cursor c = MemoryDb.get(app).getReadableDatabase().rawQuery(
                "SELECT id,object_key,pkg,t_first,t_last,seen_count,sender,text,text_key,"
                        + "block_id,frame_id,side FROM object_messages "
                        + "WHERE object_key=? OR object_key LIKE ? "
                        + "ORDER BY t_first DESC LIMIT ?",
                new String[]{object, "%" + object + "%", String.valueOf(Math.max(1, limit))})) {
            while (c.moveToNext()) {
                com.dsh.memory.ObjectMessage m = new com.dsh.memory.ObjectMessage();
                m.id = c.getLong(0); m.objectKey = c.getString(1); m.pkg = c.getString(2);
                m.tFirst = c.getLong(3); m.tLast = c.getLong(4); m.seenCount = c.getInt(5);
                m.sender = c.getString(6); m.text = c.getString(7); m.textKey = c.getString(8);
                m.blockId = c.getLong(9); m.frameId = c.getLong(10);
                m.side = c.getString(11) == null ? "UNKNOWN" : c.getString(11);
                out.add(m);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    @Override public long[] messageStats(String object) {
        if (object == null || object.isEmpty()) return new long[]{0L, 0L, 0L};
        try (android.database.Cursor c = MemoryDb.get(app).getReadableDatabase().rawQuery(
                "SELECT COUNT(*),MIN(t_first),MAX(t_first) FROM object_messages "
                        + "WHERE object_key=? OR object_key LIKE ?",
                new String[]{object, "%" + object + "%"})) {
            if (c.moveToFirst()) return new long[]{c.getLong(0), c.getLong(1), c.getLong(2)};
        } catch (Throwable ignored) { }
        return new long[]{0L, 0L, 0L};
    }

    @Override public java.util.List<String> healthNotes(String from, String to) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            long a = from == null || from.isEmpty() ? 0 : parseMs(from);
            long b = to == null || to.isEmpty() ? Long.MAX_VALUE : parseMs(to);
            try (android.database.Cursor c = MemoryDb.get(app).getReadableDatabase().rawQuery(
                    "SELECT text FROM mem_items WHERE namespace='health' AND t0>=? AND t0<? "
                            + "ORDER BY t0", new String[]{String.valueOf(a), String.valueOf(b)})) {
                while (c.moveToNext()) out.add(c.getString(0));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static boolean hasTime(String s) {
        return s != null && s.matches(".*\\d{1,2}:\\d{2}.*");
    }

    private static long parseMs(String s) {
        try {
            String t = s.trim().replace(' ', 'T');
            if (t.length() > 10) {
                return java.time.LocalDateTime.parse(t.substring(0, 16))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            return java.time.LocalDate.parse(t).atStartOfDay(
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
