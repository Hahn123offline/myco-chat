package com.dsh.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.dsh.models.Json;

/** 单向投影：记忆库内部搬运（2a: chains → mem_items event）。 */
public final class MemoryProjector {

    private MemoryProjector() { }

    /** 2a：done chains → namespace=event, kind=EPISODE, key=chain:<id>；幂等。 */
    public static int projectEvents(Context app) {
        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        int n = 0;
        try (Cursor c = db.rawQuery("SELECT id,title,body,t0,t1,n_blocks,outcome,"
                + "key_block_ids,text_source FROM chains WHERE status='done'", null)) {
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
                        + ",\"text_source\":" + Json.quote(c.getString(8)) + "}");
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
            }
        }
        android.util.Log.i("DSHL2", "project events: chains=" + n);
        return n;
    }
}
