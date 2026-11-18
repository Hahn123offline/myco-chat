package com.dsh.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.dsh.trace.Trace;

import java.io.File;
import java.util.Calendar;

/**
 * 痕迹的本地 SQLite（系统 SQLite；:memory 那轮再换 requery 拿 FTS5）。
 *
 * 表结构照规格 v1.0 §3：id/t0/t1/dwell_ms/pkg/activity/page_id/text/shot_ref/
 * guard/guard_hit/cloud_at/label + masked_text（上云用，本地 text 永远原文）。
 */
public final class TraceStore extends SQLiteOpenHelper {

    public static final String DB_NAME = "evidence.db";
    public static final int DB_VER = 6;
    public static final String TABLE = "traces";
    public static final String ELEMENTS = "page_elements";
    public static final String GAPS = "trace_gaps";

    private final File dbFile;
    private final android.content.Context app;
    public static final java.util.concurrent.atomic.AtomicLong GAP_WRITE_FAILURES =
            new java.util.concurrent.atomic.AtomicLong();

    public TraceStore(Context app) {
        this(app, new File(app.getFilesDir(), "trace"));
    }

    private TraceStore(Context app, File dir) {
        super(app, new File(dir, DB_NAME).getAbsolutePath(), null, DB_VER);
        if (!dir.exists()) dir.mkdirs();
        // 旧库迁移：trace.db → evidence.db（只 rename，不动内容）
        File old = new File(dir, "trace.db");
        File now = new File(dir, DB_NAME);
        if (old.exists() && !now.exists()) old.renameTo(now);
        this.dbFile = now;
        this.app = app.getApplicationContext();
    }

    public File dbFile() { return dbFile; }

    @Override public void onCreate(SQLiteDatabase db) {
        // 注意：traces.masked_text 是 :guard 的 maskedText（只处理手机号/邮箱）；
        // guard PASS 时等于原文。它不是 Masker 输出，**不得**用于判断内容能否上云。
        db.execSQL("CREATE TABLE " + TABLE + "(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "t0 INTEGER NOT NULL, t1 INTEGER NOT NULL, dwell_ms INTEGER NOT NULL," +
                "pkg TEXT, activity TEXT, page_id TEXT, page_title TEXT," +
                "text TEXT, shot_ref TEXT," +
                "guard INTEGER NOT NULL DEFAULT 0, guard_hit TEXT," +
                "cloud_at INTEGER NOT NULL DEFAULT 0, label TEXT, masked_text TEXT," +
                "source TEXT NOT NULL DEFAULT 'a11y')");
        db.execSQL("CREATE INDEX idx_t0 ON " + TABLE + "(t0)");
        db.execSQL("CREATE TABLE " + ELEMENTS + "(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "t0 INTEGER NOT NULL, pkg TEXT, shot_ref TEXT, elem_id TEXT, text TEXT," +
                "left INTEGER, top INTEGER, right INTEGER, bottom INTEGER," +
                "role TEXT, source TEXT, confidence REAL, signals TEXT)");
        db.execSQL("CREATE INDEX idx_elem_t0 ON " + ELEMENTS + "(t0)");
        db.execSQL("CREATE TABLE " + GAPS + "(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, t0 INTEGER NOT NULL, t1 INTEGER," +
                "reason TEXT NOT NULL, closed INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_gap_t0 ON " + GAPS + "(t0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v2：区分主路 a11y / 降级 usage
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE
                    + " ADD COLUMN source TEXT NOT NULL DEFAULT 'a11y'");
        }
        // v3：title 降为字段（不参与 pageId 身份）
        if (oldV < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN page_title TEXT");
        }
        if (oldV < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + ELEMENTS + "(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "t0 INTEGER NOT NULL, pkg TEXT, shot_ref TEXT, elem_id TEXT, text TEXT," +
                    "left INTEGER, top INTEGER, right INTEGER, bottom INTEGER," +
                    "role TEXT, source TEXT, confidence REAL, signals TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_elem_t0 ON " + ELEMENTS + "(t0)");
        }
        if (oldV < 5) {
            db.execSQL("ALTER TABLE " + ELEMENTS + " ADD COLUMN shot_ref TEXT");
        }
        if (oldV < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + GAPS + "(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, t0 INTEGER NOT NULL, t1 INTEGER," +
                    "reason TEXT NOT NULL, closed INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_gap_t0 ON " + GAPS + "(t0)");
        }
    }

    public static final class GapRow {
        public final long t0, t1;
        public final String reason;
        public final boolean closed;
        GapRow(long t0, long t1, String reason, boolean closed) {
            this.t0 = t0; this.t1 = t1; this.reason = reason; this.closed = closed;
        }
    }

    public long openGap(long t0, String reason) {
        if (!"recording_off".equals(reason) && !"unknown".equals(reason)
                && !"no_record".equals(reason) && !"app_only".equals(reason)) {
            throw new IllegalArgumentException("reason 只能是 recording_off / app_only / no_record");
        }
        ContentValues v = new ContentValues();
        v.put("t0", t0);
        v.put("reason", reason);
        v.put("closed", 0);
        try {
            return MemoryDb.get(app).getWritableDatabase().insert(GAPS, null, v);
        } catch (Throwable t) {
            GAP_WRITE_FAILURES.incrementAndGet();
            android.util.Log.e("DSHL2", "trace_gaps 写记忆库失败", t);
            return -1;
        }
    }

    public void closeOpenGaps(long t1) {
        ContentValues v = new ContentValues();
        v.put("t1", t1);
        v.put("closed", 1);
        try {
            MemoryDb.get(app).getWritableDatabase().update(GAPS, v, "closed=0", null);
        } catch (Throwable t) {
            GAP_WRITE_FAILURES.incrementAndGet();
            android.util.Log.e("DSHL2", "trace_gaps 关闭失败", t);
        }
    }

    public long lastTraceT1() {
        Cursor c = getReadableDatabase().rawQuery("SELECT MAX(t1) FROM " + TABLE, null);
        long v = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return v;
    }

    public java.util.List<GapRow> gapsBetween(long from, long to) {
        java.util.List<GapRow> out = new java.util.ArrayList<>();
        Cursor c = MemoryDb.get(app).getReadableDatabase().rawQuery(
                "SELECT t0,t1,reason,closed FROM " + GAPS
                        + " WHERE (t1 IS NULL OR t1>=?) AND t0<=? ORDER BY t0",
                new String[]{String.valueOf(from), String.valueOf(to)});
        while (c.moveToNext()) {
            out.add(new GapRow(c.getLong(0), c.isNull(1) ? 0 : c.getLong(1),
                    c.getString(2), c.getInt(3) != 0));
        }
        c.close();
        return out;
    }

    public long insert(Trace t) {        ContentValues v = new ContentValues();
        v.put("t0", t.t0);
        v.put("t1", t.t1);
        v.put("dwell_ms", t.dwellMs);
        v.put("pkg", t.pkg);
        v.put("activity", t.activity);
        v.put("page_id", t.pageId);
        v.put("page_title", t.pageTitle);
        v.put("text", t.text);
        v.put("shot_ref", t.shotRef);
        v.put("guard", t.guard);
        v.put("guard_hit", t.guardHit);
        v.put("cloud_at", t.cloudAt);
        v.put("label", t.label);
        v.put("masked_text", t.maskedText);
        v.put("source", t.source == null || t.source.isEmpty() ? "a11y" : t.source);
        return getWritableDatabase().insert(TABLE, null, v);
    }

    /** 用解析结果替换某次页面事件的元素；OCR 合并时覆盖 a11y-only 行。 */
    public void replacePageElements(long t0, String pkg, java.util.List<PageElement> elements) {
        replacePageElements(t0, pkg, "", elements);
    }

    /** 原子绑定：元素与 shotRef 同一次写入，指向同一帧。 */
    public void replacePageElements(long t0, String pkg, String shotRef,
                                    java.util.List<PageElement> elements) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(ELEMENTS, "t0=? AND pkg=?",
                    new String[]{String.valueOf(t0), pkg == null ? "" : pkg});
            if (elements != null) {
                for (PageElement e : elements) {
                    ContentValues v = new ContentValues();
                    v.put("t0", t0);
                    v.put("pkg", pkg == null ? "" : pkg);
                    v.put("shot_ref", shotRef == null ? "" : shotRef);
                    v.put("elem_id", e.id);
                    v.put("text", e.text);
                    v.put("left", e.left);
                    v.put("top", e.top);
                    v.put("right", e.right);
                    v.put("bottom", e.bottom);
                    v.put("role", e.role.name());
                    v.put("source", e.source.name());
                    v.put("confidence", e.confidence);
                    v.put("signals", e.signals.toString());
                    db.insert(ELEMENTS, null, v);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 最近一次「元素 + shotRef 同帧」的页面绑定。 */
    public static final class PageBinding {
        public final long t0;
        public final String pkg;
        public final String shotRef;
        PageBinding(long t0, String pkg, String shotRef) {
            this.t0 = t0;
            this.pkg = pkg == null ? "" : pkg;
            this.shotRef = shotRef == null ? "" : shotRef;
        }
    }

    public PageBinding latestPageBinding() {
        Cursor c = getReadableDatabase().rawQuery("SELECT t0,pkg,shot_ref FROM " + ELEMENTS
                + " WHERE shot_ref IS NOT NULL AND shot_ref!=''"
                + " ORDER BY t0 DESC LIMIT 1", null);
        PageBinding b = null;
        if (c.moveToFirst()) b = new PageBinding(c.getLong(0), c.getString(1), c.getString(2));
        c.close();
        return b;
    }

    /** 与某个 trace 块时间最接近的原子绑定（用于该块的文本/图片掩码）。 */
    public PageBinding bindingForTrace(long t0, long t1, String pkg) {
        Cursor c = getReadableDatabase().rawQuery("SELECT t0,pkg,shot_ref FROM " + ELEMENTS
                + " WHERE shot_ref IS NOT NULL AND shot_ref!='' AND pkg=?"
                + " AND t0 BETWEEN ? AND ? ORDER BY t0 DESC LIMIT 1",
                new String[]{pkg == null ? "" : pkg, String.valueOf(t0 - 2000),
                        String.valueOf(t1 + 2000)});
        PageBinding b = null;
        if (c.moveToFirst()) b = new PageBinding(c.getLong(0), c.getString(1), c.getString(2));
        c.close();
        return b;
    }

    /** 验收 dump 用。 */
    public java.util.List<PageElement> pageElements(long t0, String pkg) {
        java.util.List<PageElement> out = new java.util.ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT elem_id,text,left,top,right,bottom,"
                        + "role,source,confidence,signals FROM " + ELEMENTS
                        + " WHERE t0=? AND pkg=? ORDER BY id",
                new String[]{String.valueOf(t0), pkg == null ? "" : pkg});
        while (c.moveToNext()) {
            java.util.List<String> sig = new java.util.ArrayList<>();
            String s = c.getString(9);
            if (s != null && s.length() > 2) {
                for (String x : s.substring(1, s.length() - 1).split(",")) {
                    String t = x.trim();
                    if (!t.isEmpty()) sig.add(t);
                }
            }
            out.add(new PageElement(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3),
                    c.getInt(4), c.getInt(5),
                    PageElement.Role.valueOf(c.getString(6)),
                    PageElement.Source.valueOf(c.getString(7)),
                    c.getDouble(8), sig));
        }
        c.close();
        return out;
    }

    /** 一条链的 pkg 众数（block_ids 里出现最多的应用）。 */
    /** 一组 block id（CSV）的去重 pkg，provenance sources 用。 */
    public java.util.List<String> pkgsForCsv(String csv) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        StringBuilder in = new StringBuilder();
        for (String s : csv.split(",")) {
            String x = s.trim();
            if (x.isEmpty()) continue;
            if (in.length() > 0) in.append(',');
            in.append(x);
        }
        if (in.length() == 0) return out;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT pkg FROM " + TABLE + " WHERE id IN (" + in + ")", null);
        while (c.moveToNext()) {
            String p = c.isNull(0) ? "" : c.getString(0);
            if (!p.isEmpty() && !out.contains(p)) out.add(p);
        }
        c.close();
        return out;
    }

    public String pkgMode(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder in = new StringBuilder();
        for (Long id : ids) {
            if (in.length() > 0) in.append(',');
            in.append(id);
        }
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT pkg, COUNT(*) c FROM " + TABLE
                        + " WHERE id IN (" + in + ") GROUP BY pkg ORDER BY c DESC LIMIT 1", null);
        String pkg = c.moveToFirst() ? c.getString(0) : "";
        c.close();
        return pkg == null ? "" : pkg;
    }

    /** 指定来源、与 [from,to] 有重叠的痕迹（usage 去重用，只读最少字段） */
    public java.util.List<Trace> tracesBetween(long from, long to, String source) {
        java.util.List<Trace> out = new java.util.ArrayList<>();
        String sql = "SELECT id,t0,t1,pkg,source,text,shot_ref FROM " + TABLE
                + " WHERE t1>=? AND t0<=?" + (source == null ? "" : " AND source=?")
                + " ORDER BY t0";
        String[] args = source == null
                ? new String[]{String.valueOf(from), String.valueOf(to)}
                : new String[]{String.valueOf(from), String.valueOf(to), source};
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) {
            Trace t = new Trace();
            t.id = c.getLong(0);
            t.t0 = c.getLong(1);
            t.t1 = c.getLong(2);
            t.pkg = c.isNull(3) ? "" : c.getString(3);
            t.source = c.isNull(4) ? "a11y" : c.getString(4);
            t.text = c.isNull(5) ? "" : c.getString(5);
            t.shotRef = c.isNull(6) ? null : c.getString(6);
            out.add(t);
        }
        c.close();
        return out;
    }

    /** 自 sinceMs 起的 a11y 痕迹（时间升序）。 */
    public java.util.List<Trace> recentA11ySince(long sinceMs, int limit) {
        java.util.List<Trace> out = new java.util.ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,t0,t1,dwell_ms,pkg,activity,page_id,page_title,text,shot_ref,"
                        + "guard,guard_hit,cloud_at,label,masked_text,source FROM " + TABLE
                        + " WHERE source='a11y' AND t1>=? ORDER BY t0 ASC LIMIT ?",
                new String[]{String.valueOf(sinceMs), String.valueOf(Math.max(1, limit))});
        while (c.moveToNext()) out.add(readTrace(c));
        c.close();
        return out;
    }

    /** 最近 N 条 a11y 痕迹（按 id 倒序取，再按时间升序返回） */
    public java.util.List<Trace> recentA11y(int limit) {
        java.util.List<Trace> out = new java.util.ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,t0,t1,dwell_ms,pkg,activity,page_id,page_title,text,shot_ref,"
                        + "guard,guard_hit,cloud_at,label,masked_text,source FROM " + TABLE
                        + " WHERE source='a11y' ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))});
        while (c.moveToNext()) out.add(readTrace(c));
        c.close();
        java.util.Collections.reverse(out);       // 时间顺序
        return out;
    }

    /** 按 id 取块（顺序保持传入顺序） */
    public java.util.List<Trace> tracesByIds(java.util.List<Long> ids) {
        java.util.List<Trace> out = new java.util.ArrayList<>();
        if (ids == null || ids.isEmpty()) return out;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) in.append(',');
            in.append(ids.get(i));
        }
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,t0,t1,dwell_ms,pkg,activity,page_id,page_title,text,shot_ref,"
                        + "guard,guard_hit,cloud_at,label,masked_text,source FROM " + TABLE
                        + " WHERE id IN (" + in + ")", null);
        java.util.Map<Long, Trace> map = new java.util.HashMap<>();
        while (c.moveToNext()) {
            Trace t = readTrace(c);
            map.put(t.id, t);
        }
        c.close();
        for (Long id : ids) {
            Trace t = map.get(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    private static Trace readTrace(Cursor c) {
        Trace t = new Trace();
        t.id = c.getLong(0);
        t.t0 = c.getLong(1);
        t.t1 = c.getLong(2);
        t.dwellMs = c.getInt(3);
        t.pkg = c.isNull(4) ? "" : c.getString(4);
        t.activity = c.isNull(5) ? "" : c.getString(5);
        t.pageId = c.isNull(6) ? "" : c.getString(6);
        t.pageTitle = c.isNull(7) ? "" : c.getString(7);
        t.text = c.isNull(8) ? "" : c.getString(8);
        t.shotRef = c.isNull(9) ? null : c.getString(9);
        t.guard = c.getInt(10);
        t.guardHit = c.isNull(11) ? null : c.getString(11);
        t.cloudAt = c.getLong(12);
        t.label = c.isNull(13) ? null : c.getString(13);
        t.maskedText = c.isNull(14) ? "" : c.getString(14);
        t.source = c.isNull(15) ? "a11y" : c.getString(15);
        return t;
    }

    /** 今日（本地时区）痕迹数，给通知用 */
    public long countToday() {
        long start = startOfToday();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE t0>=?", new String[]{String.valueOf(start)});
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }

    public long count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }

    private static long startOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 碎片质量统计（只用于日志）：a11y 主路痕迹的 [blocks, short_le2s, short_le5s, noText, noShot]。
     * 切点/截图触发计数是运行时状态，由 TraceModule 拼接。
     */
    public long[] qualityStats() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*),"
                            + " SUM(CASE WHEN dwell_ms<2000 THEN 1 ELSE 0 END),"
                            + " SUM(CASE WHEN dwell_ms<5000 THEN 1 ELSE 0 END),"
                            + " SUM(CASE WHEN text IS NULL OR text='' THEN 1 ELSE 0 END),"
                            + " SUM(CASE WHEN shot_ref IS NULL THEN 1 ELSE 0 END)"
                            + " FROM " + TABLE + " WHERE source='a11y'", null);
            if (!c.moveToFirst()) return new long[]{0, 0, 0, 0, 0};
            return new long[]{
                    c.getLong(0),
                    c.isNull(1) ? 0 : c.getLong(1),
                    c.isNull(2) ? 0 : c.getLong(2),
                    c.isNull(3) ? 0 : c.getLong(3),
                    c.isNull(4) ? 0 : c.getLong(4)
            };
        } catch (Throwable t) {
            return new long[]{0, 0, 0, 0, 0};
        } finally {
            if (c != null) c.close();
        }
    }
}
