package com.dsh.v2;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

/** day_narratives 存储：day 主键，重跑覆盖。 */
public final class DayNarrativeStore extends SQLiteOpenHelper {

    public static final String DB_NAME = "day_narrative.db";
    public static final int DB_VER = 3;
    public static final String TABLE = "day_narratives";

    public DayNarrativeStore(Context app) {
        super(app, new File(new File(app.getFilesDir(), "trace"), DB_NAME).getAbsolutePath(),
                null, DB_VER);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
                "day TEXT PRIMARY KEY, narrative TEXT, segments TEXT, mainlines TEXT," +
                "open_threads TEXT, gaps TEXT, unmentioned_event_ids TEXT," +
                "event_ids TEXT, input_sha1 TEXT, index_partial INTEGER DEFAULT 0," +
                "validator_error TEXT, model TEXT, prompt_tokens INTEGER," +
                "completion_tokens INTEGER, created_at INTEGER, updated_at INTEGER," +
                "repaired INTEGER DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN mainlines TEXT");
        }
        if (oldV < 3) {
            try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN repaired INTEGER DEFAULT 0"); }
            catch (Throwable ignored) { }
        }
    }

    public static final class Row {
        public final String day;
        public final String narrative;
        public final String segments;
        public final String mainlines;
        public final String openThreads;
        public final String gaps;
        public final String unmentioned;
        public final String eventIds;
        public final String inputSha1;
        public final int indexPartial;
        public final String error;
        public final boolean repaired;
        Row(String day, String narrative, String segments, String mainlines,
            String openThreads, String gaps, String unmentioned, String eventIds,
            String inputSha1, int partial, String error, boolean repaired) {
            this.day = day; this.narrative = narrative; this.segments = segments;
            this.mainlines = mainlines;
            this.openThreads = openThreads; this.gaps = gaps; this.unmentioned = unmentioned;
            this.eventIds = eventIds; this.inputSha1 = inputSha1;
            this.indexPartial = partial; this.error = error; this.repaired = repaired;
        }
    }

    public Row get(String day) {
        android.database.Cursor c = getReadableDatabase().rawQuery(
                "SELECT day,narrative,segments,mainlines,open_threads,gaps,unmentioned_event_ids,"
                        + "event_ids,input_sha1,index_partial,validator_error,repaired FROM " + TABLE
                        + " WHERE day=?", new String[]{day});
        Row r = null;
        if (c.moveToFirst()) {
            r = new Row(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getString(7),
                    c.getString(8), c.getInt(9), c.getString(10), c.getInt(11) != 0);
        }
        c.close();
        return r;
    }

    public void put(String day, String narrative, String segments, String mainlines,
                    String openThreads, String gaps, String unmentioned, String eventIds,
                    String inputSha1, int partial, String error, String model,
                    long prompt, long completion) {
        put(day, narrative, segments, mainlines, openThreads, gaps, unmentioned, eventIds,
                inputSha1, partial, error, model, prompt, completion, false);
    }

    public void put(String day, String narrative, String segments, String mainlines,
                    String openThreads, String gaps, String unmentioned, String eventIds,
                    String inputSha1, int partial, String error, String model,
                    long prompt, long completion, boolean repaired) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("day", day);
        v.put("narrative", narrative);
        v.put("segments", segments);
        v.put("mainlines", mainlines);
        v.put("open_threads", openThreads);
        v.put("gaps", gaps);
        v.put("unmentioned_event_ids", unmentioned);
        v.put("event_ids", eventIds);
        v.put("input_sha1", inputSha1);
        v.put("index_partial", partial);
        v.put("validator_error", error);
        v.put("model", model);
        v.put("prompt_tokens", prompt);
        v.put("completion_tokens", completion);
        v.put("repaired", repaired ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(TABLE, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }
}
