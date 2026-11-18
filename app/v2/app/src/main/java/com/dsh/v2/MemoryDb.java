package com.dsh.v2;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.dsh.memory.MemorySchema;

import java.io.File;

/** 记忆库单一大 helper：可携带，所有记忆级表共用一个 user_version。 */
public final class MemoryDb extends SQLiteOpenHelper {

    public static final String DB_NAME = "memory.db";
    public static final int DB_VER = 10;

    private static volatile MemoryDb instance;
    /** 迁移未完成/可疑时非空；状态页与日志可见，绝不静默当新设备。 */
    public static volatile String migrationWarning = "";

    public static void checkMigrationState(Context app) {
        try {
            File dir = new File(app.getFilesDir(), "trace");
            boolean old = new File(dir, "chains.db").exists()
                    || new File(dir, "agent.db").exists()
                    || new File(dir, "day_narrative.db").exists()
                    || new File(dir, "memory.db").exists();
            if (!old) return;
            android.database.sqlite.SQLiteDatabase db = get(app).getReadableDatabase();
            int n = 0;
            for (String t : new String[]{"chains", "agent_sessions", "day_narratives", "mem_items"}) {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + t, null);
                if (c.moveToFirst()) n += c.getInt(0);
                c.close();
            }
            if (n == 0) {
                migrationWarning = "迁移未完成：旧库还在但记忆库为空";
                android.util.Log.e("DSHL2", migrationWarning);
            }
        } catch (Throwable t) {
            migrationWarning = "迁移状态检查失败: " + t.getMessage();
            android.util.Log.e("DSHL2", migrationWarning, t);
        }
    }

    public static synchronized void reset() {
        if (instance != null) {
            try { instance.close(); } catch (Throwable ignored) { }
        }
        instance = null;
    }

    public static MemoryDb get(Context app) {
        if (instance == null) {
            synchronized (MemoryDb.class) {
                if (instance == null) instance = new MemoryDb(app.getApplicationContext());
            }
        }
        return instance;
    }

    public MemoryDb(Context app) {
        super(app, new File(new File(app.getFilesDir(), "host"), DB_NAME).getAbsolutePath(),
                null, DB_VER);
        File dir = new File(app.getFilesDir(), "host");
        if (!dir.exists()) dir.mkdirs();
    }



    @Override public void onCreate(SQLiteDatabase db) {
        createAll(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        createAll(db);
    }

    /** 幂等建表；各 store 可复用。 */
    public static void createAll(SQLiteDatabase db) {
        db.execSQL(MemorySchema.CREATE_TABLE);
        db.execSQL(MemorySchema.CREATE_UNIQUE_KEY);
        db.execSQL(MemorySchema.CREATE_INDEX_NS);

        db.execSQL("CREATE TABLE IF NOT EXISTS chains(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "l0_index INTEGER, title TEXT, session_ids TEXT, block_ids TEXT," +
                "t0 INTEGER, t1 INTEGER, n_blocks INTEGER," +
                "status TEXT, body TEXT, key_block_ids TEXT, provisional INTEGER DEFAULT 0," +
                "created_at INTEGER, dedup_key TEXT, chunk_key TEXT," +
                "parent_id INTEGER DEFAULT 0, child_ids TEXT, level INTEGER DEFAULT 0," +
                "merge_why TEXT, text_source TEXT, outcome TEXT DEFAULT 'unknown'," +
                "gap_declared INTEGER DEFAULT 1, over_length INTEGER DEFAULT 0," +
                "short_body INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS pipeline_runs(" +
                "id TEXT PRIMARY KEY, status TEXT, started_at INTEGER, finished_at INTEGER," +
                "hours INTEGER, block_from INTEGER, block_to INTEGER," +
                "l0_chunks INTEGER, chains INTEGER, bodies INTEGER," +
                "prompt_tokens INTEGER, completion_tokens INTEGER, cache_hit INTEGER, error TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS l0_chunks(" +
                "chunk_key TEXT PRIMARY KEY, t0 INTEGER, t1 INTEGER, block_ids TEXT," +
                "status TEXT, updated_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS merge_judgments(" +
                "a_id INTEGER, b_id INTEGER, verdict TEXT," +
                "a_body_sha1 TEXT, b_body_sha1 TEXT, at INTEGER, PRIMARY KEY(a_id,b_id))");

        db.execSQL("CREATE TABLE IF NOT EXISTS day_narratives(" +
                "day TEXT PRIMARY KEY, narrative TEXT, segments TEXT, mainlines TEXT," +
                "open_threads TEXT, gaps TEXT, unmentioned_event_ids TEXT," +
                "event_ids TEXT, input_sha1 TEXT, index_partial INTEGER DEFAULT 0," +
                "validator_error TEXT, model TEXT, prompt_tokens INTEGER," +
                "completion_tokens INTEGER, created_at INTEGER, updated_at INTEGER," +
                "repaired INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS agent_sessions(" +
                "id TEXT PRIMARY KEY, kind TEXT, created_at INTEGER, updated_at INTEGER," +
                "status TEXT, turn_count INTEGER NOT NULL DEFAULT 0," +
                "system_prompt TEXT, meta TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS agent_events(" +
                "session_id TEXT NOT NULL, seq INTEGER NOT NULL, type TEXT NOT NULL," +
                "payload TEXT, at INTEGER NOT NULL, PRIMARY KEY(session_id, seq))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_events_session ON agent_events(session_id, seq)");

        // 缺口是记忆级事实，随设备走
        db.execSQL("CREATE TABLE IF NOT EXISTS trace_gaps(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, t0 INTEGER NOT NULL, t1 INTEGER," +
                "reason TEXT NOT NULL, closed INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gap_t0 ON trace_gaps(t0)");
    }
}
