package com.dsh.v2;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;

/** 一次性迁移：4 旧库 → files/host/memory.db；逐表断言，失败整库回滚。 */
public final class DbMigrator {

    private static final String TAG = "DSHL2";

    private DbMigrator() { }

    public static void migrateIfNeeded(Context app) {
        File trace = new File(app.getFilesDir(), "trace");
        File newDbFile = new File(new File(app.getFilesDir(), "host"), "memory.db");
        File chains = new File(trace, "chains.db");
        File day = new File(trace, "day_narrative.db");
        File agent = new File(trace, "agent.db");
        File oldMemory = new File(trace, "memory.db");
        File evidence = new File(trace, "evidence.db");
        File oldTrace = new File(trace, "trace.db");
        if (!evidence.exists() && oldTrace.exists()) evidence = oldTrace;
        boolean any = chains.exists() || day.exists() || agent.exists()
                || oldMemory.exists() || evidence.exists();
        if (!any) return;

        SQLiteDatabase db = MemoryDb.get(app).getWritableDatabase();
        boolean ok = true;
        String fail = "";
        try {
            ok &= copy(db, chains, new String[]{"chains", "l0_chunks",
                    "merge_judgments", "pipeline_runs"});
            ok &= copy(db, day, new String[]{"day_narratives"});
            ok &= copy(db, agent, new String[]{"agent_sessions", "agent_events"});
            ok &= copy(db, oldMemory, new String[]{"mem_items"});
            ok &= copy(db, evidence, new String[]{"trace_gaps"});
            if (!ok) throw new IllegalStateException(fail);
        } catch (Throwable t) {
            fail = t.getMessage() == null ? t.toString() : t.getMessage();
            Log.e(TAG, "db migration failed: " + fail, t);
            MemoryDb.migrationWarning = "迁移失败：" + fail;
            try { db.close(); } catch (Throwable ignored) { }
            MemoryDb.reset();
            if (newDbFile.exists()) newDbFile.delete();
            return;
        }
        // 成功：旧记忆库 rename 成 .migrated.bak；旧 trace.db 直接变成 evidence.db
        File evidenceTarget = new File(trace, "evidence.db");
        for (File f : new File[]{chains, day, agent, oldMemory}) {
            if (f != null && f.exists()) {
                File bak = new File(f.getParentFile(), f.getName() + ".migrated.bak");
                if (!bak.exists()) f.renameTo(bak);
            }
        }
        if (evidence != null && evidence.exists()) {
            if (evidence.equals(oldTrace) && !evidenceTarget.exists()) {
                evidence.renameTo(evidenceTarget);
            } else {
                File bak = new File(evidence.getParentFile(), evidence.getName() + ".migrated.bak");
                if (!bak.exists()) evidence.renameTo(bak);
            }
        }
        MemoryDb.migrationWarning = "";
        Log.i(TAG, "db migration done: " + newDbFile.getAbsolutePath());
    }

    private static boolean copy(SQLiteDatabase db, File src, String[] tables) {
        if (src == null || !src.exists()) return true;
        try {
            db.execSQL("ATTACH DATABASE ? AS src", new Object[]{src.getAbsolutePath()});
        } catch (Throwable t) {
            Log.e(TAG, "attach failed " + src, t);
            return false;
        }
        try {
            for (String t : tables) {
                int s = count(db, "src." + t);
                if (s < 0) continue; // 源库没有此表
                db.execSQL("INSERT OR IGNORE INTO main." + t + " SELECT * FROM src." + t);
                int d = count(db, "main." + t);
                Log.i(TAG, "migrate " + t + " src=" + s + " dst=" + d);
                if (s != d) {
                    MemoryDb.migrationWarning = "迁移失败：" + t
                            + " 源/目标行数不一致 (" + s + "/" + d + ")";
                    Log.e(TAG, MemoryDb.migrationWarning);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "copy failed " + src, t);
            return false;
        } finally {
            try { db.execSQL("DETACH DATABASE src"); } catch (Throwable ignored) { }
        }
    }

    private static int count(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable t) {
            return -1;
        } finally {
            if (c != null) c.close();
        }
    }
}
