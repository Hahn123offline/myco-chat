package com.dsh.v2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.dsh.memory.AbstractMemoryStore;
import com.dsh.memory.Kind;
import com.dsh.memory.MemItem;
import com.dsh.memory.MemoryCodec;
import com.dsh.memory.MemorySchema;
import com.dsh.memory.Query;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Android 侧的 memory store；SQL 全部来自 :memory/MemorySchema。 */
public final class AndroidMemoryStore extends SQLiteOpenHelper implements com.dsh.memory.MemoryStore {

    public static final String DB_NAME = "memory.db";
    private final Store impl;

    public AndroidMemoryStore(Context app) {
        super(app, new File(new File(app.getFilesDir(), "trace"), DB_NAME).getAbsolutePath(),
                null, 2);
        impl = new Store();
    }

    private final class Store extends AbstractMemoryStore {
        @Override protected MemItem findKey(String namespace, String key) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT * FROM mem_items WHERE namespace=? AND key=? AND superseded=0 LIMIT 1",
                    new String[]{namespace, key})) {
                return c.moveToFirst() ? row(c) : null;
            }
        }

        @Override protected long insertRow(MemItem m) {
            long id = getWritableDatabase().insertOrThrow("mem_items", null, values(m, false));
            m.id = id;
            return id;
        }

        @Override protected void updateRow(MemItem m) {
            getWritableDatabase().update("mem_items", values(m, true), "id=?",
                    new String[]{String.valueOf(m.id)});
        }

        @Override public MemItem get(long id) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT * FROM mem_items WHERE id=?", new String[]{String.valueOf(id)})) {
                return c.moveToFirst() ? row(c) : null;
            }
        }

        @Override public List<MemItem> query(Query q) {
            StringBuilder sql = new StringBuilder("SELECT * FROM mem_items WHERE 1=1");
            List<String> args = new ArrayList<>();
            if (q == null) {
                sql.append(" AND superseded=0 ORDER BY t0,id LIMIT 100");
            } else {
                if (!q.includeSuperseded) sql.append(" AND superseded=0");
                if (q.namespace != null) { sql.append(" AND namespace=?"); args.add(q.namespace); }
                if (q.kind != null) { sql.append(" AND kind=?"); args.add(q.kind.name()); }
                if (q.from != null) { sql.append(" AND t0>=?"); args.add(String.valueOf(q.from)); }
                if (q.to != null) { sql.append(" AND t0<=?"); args.add(String.valueOf(q.to)); }
                sql.append(" ORDER BY t0,id LIMIT ").append(q.limit <= 0 ? 100 : q.limit);
            }
            List<MemItem> out = new ArrayList<>();
            try (Cursor c = getReadableDatabase().rawQuery(sql.toString(),
                    args.toArray(new String[0]))) {
                while (c.moveToNext()) out.add(row(c));
            }
            return out;
        }
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL(MemorySchema.CREATE_TABLE);
        db.execSQL(MemorySchema.CREATE_UNIQUE_KEY);
        db.execSQL(MemorySchema.CREATE_INDEX_NS);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL(MemorySchema.ALTER_DETAIL);
    }

    private static android.content.ContentValues values(MemItem m, boolean withId) {
        android.content.ContentValues v = new android.content.ContentValues();
        if (withId) v.put("id", m.id);
        v.put("namespace", m.namespace);
        v.put("kind", m.kind == null ? Kind.BLOCK.name() : m.kind.name());
        v.put("key", m.key);
        v.put("text", m.text);
        v.put("data", m.data);
        v.put("detail_level", m.detailLevel == null ? "FULL" : m.detailLevel.name());
        v.put("weight", m.weight);
        v.put("recall_count", m.recallCount);
        v.put("last_recalled_at", m.lastRecalledAt);
        v.put("t0", m.t0);
        v.put("t1", m.t1);
        v.put("created_at", m.createdAt);
        v.put("updated_at", m.updatedAt);
        v.put("evidence", MemoryCodec.evidenceJson(m.evidence));
        v.put("superseded", m.superseded ? 1 : 0);
        return v;
    }

    private static MemItem row(Cursor c) {
        MemItem m = new MemItem();
        m.id = c.getLong(c.getColumnIndexOrThrow("id"));
        m.namespace = c.getString(c.getColumnIndexOrThrow("namespace"));
        m.kind = Kind.valueOf(c.getString(c.getColumnIndexOrThrow("kind")));
        m.key = c.getString(c.getColumnIndexOrThrow("key"));
        m.text = c.getString(c.getColumnIndexOrThrow("text"));
        m.data = c.getString(c.getColumnIndexOrThrow("data"));
        try { m.detailLevel = com.dsh.memory.DetailLevel.valueOf(
                c.getString(c.getColumnIndexOrThrow("detail_level")).toUpperCase()); }
        catch (Throwable ignored) { m.detailLevel = com.dsh.memory.DetailLevel.FULL; }
        m.weight = c.getDouble(c.getColumnIndexOrThrow("weight"));
        m.recallCount = c.getInt(c.getColumnIndexOrThrow("recall_count"));
        m.lastRecalledAt = c.getLong(c.getColumnIndexOrThrow("last_recalled_at"));
        m.t0 = c.getLong(c.getColumnIndexOrThrow("t0"));
        m.t1 = c.getLong(c.getColumnIndexOrThrow("t1"));
        m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        m.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        m.evidence = MemoryCodec.parseEvidence(c.getString(c.getColumnIndexOrThrow("evidence")));
        m.superseded = c.getInt(c.getColumnIndexOrThrow("superseded")) != 0;
        return m;
    }

    @Override public long put(MemItem item) { return impl.put(item); }
    @Override public MemItem get(long id) { return impl.get(id); }
    @Override public List<MemItem> query(Query q) { return impl.query(q); }
    @Override public void boost(long id, double delta, double cap) { impl.boost(id, delta, cap); }
    @Override public void patch(long id, java.util.Map<String, Object> p) { impl.patch(id, p); }
    @Override public void remove(long id) { impl.remove(id); }
    @Override public void registerNamespace(String ns, boolean req) { impl.registerNamespace(ns, req); }
    @Override public boolean isNamespaceRegistered(String ns) { return impl.isNamespaceRegistered(ns); }
    @Override public boolean namespaceRequiresEvidence(String ns) {
        return impl.namespaceRequiresEvidence(ns);
    }
}
