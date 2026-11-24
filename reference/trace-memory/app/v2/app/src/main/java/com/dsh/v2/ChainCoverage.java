package com.dsh.v2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从证据库的块区间计算 covered_ms（并集长度）；缺失证据时保留 0。 */
public final class ChainCoverage {

    private ChainCoverage() { }

    public static void backfill(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        java.io.File ev = new java.io.File(new java.io.File(app.getFilesDir(), "trace"),
                "evidence.db");
        if (!ev.exists()) return;
        SQLiteDatabase edb = null;
        try {
            edb = SQLiteDatabase.openDatabase(ev.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            List<Object[]> rows = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT id,block_ids FROM chains", null)) {
                while (c.moveToNext()) rows.add(new Object[]{c.getLong(0), c.getString(1)});
            }
            int updated = 0;
            for (Object[] r : rows) {
                long id = (Long) r[0];
                String csv = (String) r[1];
                List<long[]> spans = new ArrayList<>();
                if (csv != null) {
                    for (String s : csv.split(",")) {
                        long bid;
                        try { bid = Long.parseLong(s.trim()); } catch (Throwable ignored) { continue; }
                        try (Cursor t = edb.rawQuery(
                                "SELECT t0,t1 FROM traces WHERE id=?", new String[]{String.valueOf(bid)})) {
                            if (t.moveToFirst()) spans.add(new long[]{t.getLong(0), t.getLong(1)});
                        }
                    }
                }
                long covered = union(spans);
                if (covered > 0) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put("covered_ms", covered);
                    db.update("chains", v, "id=?", new String[]{String.valueOf(id)});
                    updated++;
                }
            }
            android.util.Log.i("DSHL2", "chain coverage backfill updated=" + updated);
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "chain coverage backfill failed", t);
        } finally {
            if (edb != null) try { edb.close(); } catch (Throwable ignored) { }
        }
    }

    private static long union(List<long[]> spans) {
        if (spans.isEmpty()) return 0;
        spans.sort(Comparator.comparingLong(a -> a[0]));
        long total = 0, curS = spans.get(0)[0], curE = spans.get(0)[1];
        for (int i = 1; i < spans.size(); i++) {
            long s = spans.get(i)[0], e = spans.get(i)[1];
            if (s <= curE) curE = Math.max(curE, e);
            else { total += Math.max(0, curE - curS); curS = s; curE = e; }
        }
        return total + Math.max(0, curE - curS);
    }
}
