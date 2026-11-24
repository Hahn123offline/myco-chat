package com.dsh.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.dsh.agent.AgentEvent;
import com.dsh.agent.AgentStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * :agent 的 SQLite 事件存储：files/trace/agent.db。
 * 两层：agent_sessions（状态）+ agent_events（append-only 真源）。
 */
public final class AgentDbStore implements AgentStore {
    private final MemoryDb memoryDb;

    public android.database.sqlite.SQLiteDatabase getWritableDatabase() {
        return memoryDb.getWritableDatabase();
    }

    public android.database.sqlite.SQLiteDatabase getReadableDatabase() {
        return memoryDb.getReadableDatabase();
    }

    public void close() { }


    public static final String DB_NAME = "agent.db";
    public static final int DB_VER = 1;
    public static final String SESSIONS = "agent_sessions";
    public static final String EVENTS = "agent_events";

    private final File dbFile;

    public AgentDbStore(Context app) {
        memoryDb = MemoryDb.get(app);
        this.dbFile = new File(new File(app.getFilesDir(), "host"), "memory.db");
    }

    public File dbFile() { return dbFile; }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SESSIONS + "(" +
                "id TEXT PRIMARY KEY, kind TEXT, created_at INTEGER, updated_at INTEGER," +
                "status TEXT, turn_count INTEGER NOT NULL DEFAULT 0," +
                "system_prompt TEXT, meta TEXT)");
        db.execSQL("CREATE TABLE " + EVENTS + "(" +
                "session_id TEXT NOT NULL, seq INTEGER NOT NULL, type TEXT NOT NULL," +
                "payload TEXT, at INTEGER NOT NULL, PRIMARY KEY(session_id, seq))");
        db.execSQL("CREATE INDEX idx_events_session ON " + EVENTS + "(session_id, seq)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1 尚无迁移
    }

    public void ensureSession(String id, String kind, String systemPrompt, String meta) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM " + SESSIONS + " WHERE id=?",
                new String[]{id});
        boolean exists = c.moveToFirst();
        c.close();
        if (exists) return;
        ContentValues v = new ContentValues();
        long now = System.currentTimeMillis();
        v.put("id", id);
        v.put("kind", kind == null ? "" : kind);
        v.put("created_at", now);
        v.put("updated_at", now);
        v.put("status", "running");
        v.put("turn_count", 0);
        v.put("system_prompt", systemPrompt == null ? "" : systemPrompt);
        v.put("meta", meta == null ? "" : meta);
        db.insert(SESSIONS, null, v);
    }

    public void updateStatus(String id, String status, int turns) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("turn_count", turns);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(SESSIONS, v, "id=?", new String[]{id});
    }

    @Override public synchronized long append(String sessionId, String type, String payload) {
        SQLiteDatabase db = getWritableDatabase();
        long seq = 1;
        Cursor c = db.rawQuery("SELECT COALESCE(MAX(seq),0)+1 FROM " + EVENTS
                + " WHERE session_id=?", new String[]{sessionId});
        if (c.moveToFirst()) seq = c.getLong(0);
        c.close();
        ContentValues v = new ContentValues();
        v.put("session_id", sessionId);
        v.put("seq", seq);
        v.put("type", type);
        v.put("payload", payload == null ? "{}" : payload);
        v.put("at", System.currentTimeMillis());
        db.insert(EVENTS, null, v);
        ContentValues s = new ContentValues();
        s.put("updated_at", System.currentTimeMillis());
        db.update(SESSIONS, s, "id=?", new String[]{sessionId});
        return seq;
    }

    @Override public synchronized List<AgentEvent> events(String sessionId) {
        List<AgentEvent> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT seq,type,payload,at FROM " + EVENTS
                        + " WHERE session_id=? ORDER BY seq ASC", new String[]{sessionId});
        while (c.moveToNext()) {
            out.add(new AgentEvent(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return out;
    }

    /** 链被去重删除时，清理指向它的 agent 会话，避免悬空引用。 */
    public synchronized int deleteSessionsForChain(long chainId) {
        SQLiteDatabase db = getWritableDatabase();
        List<String> ids = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id FROM " + SESSIONS + " WHERE meta LIKE ?",
                new String[]{"%chain_id=" + chainId + "%"});
        while (c.moveToNext()) ids.add(c.getString(0));
        c.close();
        for (String id : ids) {
            db.delete(EVENTS, "session_id=?", new String[]{id});
            db.delete(SESSIONS, "id=?", new String[]{id});
        }
        return ids.size();
    }

    /** 把 session 元数据 + 全部事件 dump 成可读 txt（adb pull 验收用）。 */
    public File dumpSession(String sessionId) {
        File dir = dbFile.getParentFile();
        File out = new File(dir, "l2_transcript_"
                + sessionId.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".txt");
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8)) {
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id,kind,status,turn_count,created_at,updated_at,meta FROM "
                            + SESSIONS + " WHERE id=?", new String[]{sessionId});
            if (c.moveToFirst()) {
                w.write("session=" + c.getString(0) + " kind=" + c.getString(1)
                        + " status=" + c.getString(2) + " turns=" + c.getInt(3)
                        + " meta=" + c.getString(6) + "\n");
            }
            c.close();
            for (AgentEvent e : events(sessionId)) {
                w.write("#" + e.seq + " [" + e.type + "] " + e.payload + "\n");
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
