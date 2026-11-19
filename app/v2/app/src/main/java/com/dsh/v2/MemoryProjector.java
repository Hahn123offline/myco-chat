package com.dsh.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dsh.models.Json;

/** 单向投影：记忆库内部搬运（2a: chains → mem_items event）。 */
public final class MemoryProjector {

    private MemoryProjector() { }

    /** 3：day_narratives → namespace=day, kind=DAY；不依赖证据库，幂等。 */
    public static int projectDays(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        int n = 0;
        try (Cursor c = db.rawQuery("SELECT day,narrative,mainlines,open_threads,gaps,"
                + "repaired,index_partial FROM day_narratives", null)) {
            while (c.moveToNext()) {
                String day = c.getString(0);
                if (day == null || day.isEmpty()) continue;
                String mainlines = c.getString(2);
                String summary;
                if (mainlines != null && mainlines.length() > 2) {
                    summary = mainlines;
                } else {
                    String narr = c.getString(1);
                    summary = narr == null ? "" : narr;
                }
                if (summary.length() > 800) summary = summary.substring(0, 800);
                long t0;
                try { t0 = java.time.LocalDate.parse(day).atStartOfDay(
                        java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); }
                catch (Throwable t) { t0 = System.currentTimeMillis(); }
                long t1 = t0 + 86_400_000L - 1;
                ContentValues v = new ContentValues();
                v.put("namespace", "day");
                v.put("kind", "DAY");
                v.put("key", "day:" + day);
                v.put("text", summary);
                v.put("data", "{\"narrative\":" + Json.quote(c.getString(1))
                        + ",\"mainlines\":" + safeJson(mainlines)
                        + ",\"open_threads\":" + safeJson(c.getString(3))
                        + ",\"gaps\":" + safeJson(c.getString(4))
                        + ",\"repaired\":" + (c.getInt(5) != 0)
                        + ",\"index_partial\":" + c.getInt(6) + "}");
                v.put("detail_level", "FULL");
                v.put("weight", 1.0);
                v.put("recall_count", 0);
                v.put("last_recalled_at", 0);
                v.put("t0", t0);
                v.put("t1", t1);
                v.put("created_at", System.currentTimeMillis());
                v.put("updated_at", System.currentTimeMillis());
                v.put("evidence", "[{\"type\":\"trace\",\"ref\":\"day:" + day
                        + "\",\"t\":" + t0 + "}]");
                v.put("superseded", 0);
                long r = db.insertWithOnConflict("mem_items", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (r >= 0) n++;
            }
        }
        android.util.Log.i("DSHL2", "project days: " + n);
        return n;
    }


    /** 行视图：evidence.traces/page_elements → lines（只存文本+指针，幂等）。 */
    public static int projectLines(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        java.io.File ev = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                "evidence.db");
        if (!ev.exists()) return 0;
        long maxBlock = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(block_id),0) FROM lines", null)) {
            if (c.moveToFirst()) maxBlock = c.getLong(0);
        }
        SQLiteDatabase edb = null;
        int n = 0;
        android.util.Log.i("DSHL2", "project lines start maxBlock=" + maxBlock);
        try {
            edb = SQLiteDatabase.openDatabase(ev.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            try (Cursor t = edb.rawQuery("SELECT id,t0,pkg,page_title,object_key,shot_ref "
                    + "FROM traces WHERE id>? AND shot_ref IS NOT NULL AND shot_ref!='' "
                    + "ORDER BY id DESC LIMIT 500", new String[]{String.valueOf(maxBlock)})) {
                while (t.moveToNext()) {
                    db.beginTransaction();
                    long id = t.getLong(0);
                    long time = t.getLong(1);
                    String pkg = t.getString(2);
                    String title = t.getString(3);
                    String ok = t.getString(4);
                    String shot = t.getString(5);
                    if (ok == null || ok.isEmpty()) {
                        ok = (pkg == null ? "" : pkg) + "/" + (title == null ? "" : title);
                    }
                    try (Cursor e = edb.rawQuery(
                            "SELECT text,top,source FROM page_elements WHERE shot_ref=? "
                                    + "ORDER BY top,left", new String[]{shot})) {
                        int lineNo = 0;
                        while (e.moveToNext()) {
                            String text = e.getString(0);
                            if (text == null || text.trim().isEmpty()) continue;
                            String src = e.getString(2);
                            if (!"A11Y".equals(src) && !"BOTH".equals(src)
                                    && !"OCR".equals(src)) continue;
                            ContentValues v = new ContentValues();
                            v.put("object_key", ok);
                            v.put("pkg", pkg);
                            v.put("time", time);
                            v.put("text", text.trim());
                            v.put("block_id", id);
                            v.put("shot_ref", shot);
                            v.put("line_no", lineNo++);
                            v.put("y_top", e.getInt(1));
                            v.put("created_at", System.currentTimeMillis());
                            long r = db.insertWithOnConflict("lines", null, v,
                                    SQLiteDatabase.CONFLICT_IGNORE);
                            if (r >= 0) n++;
                        }
                    }
                    db.setTransactionSuccessful();
                    db.endTransaction();
                }
            }
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "project lines failed", t);
        } finally {
            try { db.endTransaction(); } catch (Throwable ignored) { }
            if (edb != null) try { edb.close(); } catch (Throwable ignored) { }
        }
        android.util.Log.i("DSHL2", "project lines: " + n + " maxBlock=" + maxBlock);
        return n;
    }

    private static String safeJson(String s) {
        return s == null || s.trim().isEmpty() ? "[]" : s;
    }

    /** 2b：done chains 的 key_block_ids → namespace=trace；证据缺失只计数。 */
    public static void projectTraceBlocks(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        int chains = 0;
        try (Cursor c = db.rawQuery("SELECT key_block_ids FROM chains WHERE status='done'", null)) {
            while (c.moveToNext()) {
                chains++;
                String csv = c.getString(0);
                if (csv == null) continue;
                for (String s : csv.split(",")) {
                    try { ids.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
                }
            }
        }
        int total = ids.size(), projected = 0, missing = 0;
        java.io.File ev = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                "evidence.db");
        SQLiteDatabase edb = null;
        if (ev.exists()) {
            try { edb = SQLiteDatabase.openDatabase(ev.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY); } catch (Throwable ignored) { }
        }
        if (edb == null) missing = total;
        else {
            try {
                for (long id : ids) {
                    try (Cursor t = edb.rawQuery("SELECT id,t0,t1,dwell_ms,pkg,page_id,page_title,"
                            + "text,shot_ref FROM traces WHERE id=?", new String[]{String.valueOf(id)})) {
                        if (!t.moveToFirst()) { missing++; continue; }
                        String text = t.getString(7);
                        String shot = t.getString(8);
                        String level = text != null && !text.trim().isEmpty() ? "FULL"
                                : shot != null && !shot.trim().isEmpty() ? "IMAGE_ONLY" : "APP_ONLY";
                        ContentValues v = new ContentValues();
                        v.put("namespace", "trace");
                        v.put("kind", "BLOCK");
                        v.put("key", "trace:" + id);
                        v.put("text", text == null ? "" : text);
                        v.put("data", "{\"pkg\":" + Json.quote(t.getString(4))
                                + ",\"page_id\":" + Json.quote(t.getString(5))
                                + ",\"page_title\":" + Json.quote(t.getString(6))
                                + ",\"shot_ref\":" + Json.quote(shot)
                                + ",\"dwell_ms\":" + t.getLong(3)
                                + ",\"t0\":" + t.getLong(1) + ",\"t1\":" + t.getLong(2) + "}");
                        v.put("detail_level", level);
                        v.put("weight", 1.0);
                        v.put("recall_count", 0);
                        v.put("last_recalled_at", 0);
                        v.put("t0", t.getLong(1));
                        v.put("t1", t.getLong(2));
                        v.put("created_at", System.currentTimeMillis());
                        v.put("updated_at", System.currentTimeMillis());
                        v.put("evidence", "[{\"type\":\"trace\",\"ref\":\"" + id
                                + "\",\"t\":" + t.getLong(1) + "}]");
                        v.put("superseded", 0);
                        long r = db.insertWithOnConflict("mem_items", null, v,
                                SQLiteDatabase.CONFLICT_IGNORE);
                        if (r >= 0) projected++;
                    }
                }
            } finally {
                try { edb.close(); } catch (Throwable ignored) { }
            }
        }
        android.util.Log.i("DSHL2", "project: chains=" + chains + " key_blocks_total=" + total
                + " projected=" + projected + " missing_evidence=" + missing);
    }

    /** 2a：done chains → namespace=event, kind=EPISODE, key=chain:<id>；幂等。 */
    public static int projectEvents(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        int n = 0;
        try (Cursor c = db.rawQuery("SELECT id,title,body,t0,t1,n_blocks,outcome,"
                + "key_block_ids,text_source,covered_ms FROM chains WHERE status='done'", null)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String title = c.getString(1);
                String body = c.getString(2);
                ContentValues v = new ContentValues();
                v.put("namespace", "event");
                v.put("kind", "EPISODE");
                v.put("key", "chain:" + id);
                v.put("text", (title == null ? "" : title) + "\n" + (body == null ? "" : body));
                v.put("data", "{\"t0\":" + c.getLong(3) + ",\"t1\":" + c.getLong(4)
                        + ",\"n_blocks\":" + c.getInt(5) + ",\"outcome\":"
                        + Json.quote(c.getString(6))
                        + ",\"key_block_ids\":" + Json.quote(c.getString(7))
                        + ",\"text_source\":" + Json.quote(c.getString(8))
                        + ",\"covered_ms\":" + c.getLong(9) + "}");
                v.put("detail_level", "FULL");
                v.put("weight", 1.0);
                v.put("recall_count", 0);
                v.put("last_recalled_at", 0);
                v.put("t0", c.getLong(3));
                v.put("t1", c.getLong(4));
                v.put("created_at", System.currentTimeMillis());
                v.put("updated_at", System.currentTimeMillis());
                v.put("evidence", "[{\"type\":\"trace\",\"ref\":\"chain:" + id
                        + "\",\"t\":" + c.getLong(3) + "}]");
                v.put("superseded", 0);
                long r = db.insertWithOnConflict("mem_items", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (r >= 0) n++;
                else db.update("mem_items", v, "namespace='event' AND key=?",
                        new String[]{"chain:" + id});
            }
        }
        android.util.Log.i("DSHL2", "project events: chains=" + n);
        return n;
    }
}
