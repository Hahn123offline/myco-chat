package com.dsh.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.dsh.agent.ChainKey;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * chains / pipeline_runs：幂等键 + 执行租约 + L0 chunk 幂等。
 */
public final class ChainStore {
    private final MemoryDb memoryDb;

    public android.database.sqlite.SQLiteDatabase getWritableDatabase() {
        return memoryDb.getWritableDatabase();
    }

    public android.database.sqlite.SQLiteDatabase getReadableDatabase() {
        return memoryDb.getReadableDatabase();
    }

    public void close() { }


    public static final String TAG = "DSHL2";
    public static final String DB_NAME = "chains.db";
    public static final int DB_VER = 8;
    public static final String TABLE = "chains";
    public static final String RUNS = "pipeline_runs";
    public static final long LEASE_MS = 2 * 3600_000L;

    public ChainStore(Context app) {
        memoryDb = MemoryDb.get(app);
    }

    public void onCreate(SQLiteDatabase db) {
        createChains(db);
        createAux(db);
    }

    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN dedup_key TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN chunk_key TEXT");
            createAux(db);
        }
        if (oldV < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN parent_id INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN child_ids TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN level INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN merge_why TEXT");
        }
        if (oldV < 4) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN text_source TEXT");
        }
        if (oldV < 5) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN outcome TEXT DEFAULT 'unknown'");
        }
        if (oldV < 6) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN gap_declared INTEGER DEFAULT 1");
        }
        if (oldV < 7) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN over_length INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN short_body INTEGER DEFAULT 0");
        }
        if (oldV < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS merge_judgments(" +
                    "a_id INTEGER, b_id INTEGER, verdict TEXT," +
                    "a_body_sha1 TEXT, b_body_sha1 TEXT, at INTEGER," +
                    "PRIMARY KEY(a_id,b_id))");
        }
    }

    private static void createChains(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "l0_index INTEGER, title TEXT, session_ids TEXT, block_ids TEXT," +
                "t0 INTEGER, t1 INTEGER, n_blocks INTEGER," +
                "status TEXT, body TEXT, key_block_ids TEXT, provisional INTEGER DEFAULT 0," +
                "created_at INTEGER, dedup_key TEXT, chunk_key TEXT," +
                "parent_id INTEGER DEFAULT 0, child_ids TEXT, level INTEGER DEFAULT 0," +
                "merge_why TEXT, text_source TEXT, outcome TEXT DEFAULT 'unknown'," +
                "gap_declared INTEGER DEFAULT 1, over_length INTEGER DEFAULT 0," +
                "short_body INTEGER DEFAULT 0, covered_ms INTEGER DEFAULT 0)");
    }

    private static void createAux(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + RUNS + "(" +
                "id TEXT PRIMARY KEY, status TEXT, started_at INTEGER, finished_at INTEGER," +
                "hours INTEGER, block_from INTEGER, block_to INTEGER," +
                "l0_chunks INTEGER, chains INTEGER, bodies INTEGER," +
                "prompt_tokens INTEGER, completion_tokens INTEGER, cache_hit INTEGER," +
                "error TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS l0_chunks(" +
                "chunk_key TEXT PRIMARY KEY, t0 INTEGER, t1 INTEGER, block_ids TEXT," +
                "status TEXT, updated_at INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS merge_judgments(" +
                "a_id INTEGER, b_id INTEGER, verdict TEXT," +
                "a_body_sha1 TEXT, b_body_sha1 TEXT, at INTEGER," +
                "PRIMARY KEY(a_id,b_id))");
    }

    /** 填旧行 key + 去重 + 建唯一索引（可重入）。 */
    public synchronized void ensureKeysAndCleanup(AgentDbStore agent) {
        SQLiteDatabase db = getWritableDatabase();
        List<long[]> keepDelete = new ArrayList<>();
        Map<String, long[]> keepers = new LinkedHashMap<>();
        List<Long> deleteIds = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT id,block_ids,body,status,dedup_key,level FROM " + TABLE
                + " ORDER BY id ASC", null);
        while (c.moveToNext()) {
            long id = c.getLong(0);
            int level = c.getInt(5);
            // 父链绝不参与物理去重/删除；父链身份由 child_ids 管
            if (level > 0) continue;
            String blocks = c.getString(1);
            String body = c.getString(2);
            String dedup = c.getString(4);
            String key = ChainKey.ofBlockCsv(blocks);
            if (dedup == null || !dedup.equals(key)) {
                ContentValues v = new ContentValues();
                v.put("dedup_key", key);
                try {
                    db.update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
                } catch (android.database.sqlite.SQLiteConstraintException e) {
                    // 不改写已有行的身份；重复行由后续 cleanup 处理
                    Log.w(TAG, "dedup key collision on cleanup, left rows untouched");
                }
            }
            long[] prev = keepers.get(key);
            if (prev == null) {
                keepers.put(key, new long[]{id, body == null ? 0 : body.length()});
            } else {
                // 保留 body 非空且最长；同长度保留 id 小的
                long prevScore = prev[1] > 0 ? prev[1] : -1;
                long curScore = body == null || body.isEmpty() ? -1 : body.length();
                if (curScore > prevScore) {
                    deleteIds.add(prev[0]);
                    keepers.put(key, new long[]{id, body == null ? 0 : body.length()});
                } else {
                    deleteIds.add(id);
                }
            }
        }
        c.close();
        for (Long id : deleteIds) db.delete(TABLE, "id=?", new String[]{String.valueOf(id)});
        // 旧版本没有 chunk_key 的链条：按 l0_index 归入 legacy 组，保证合并 pass 仍可判断
        db.execSQL("UPDATE " + TABLE
                + " SET chunk_key='legacy:'||l0_index WHERE chunk_key IS NULL");
        // 让 legacy chunk_key 在 l0_chunks 里也有一致的 done 记录，避免两表漂移
        db.execSQL("INSERT OR IGNORE INTO l0_chunks(chunk_key,t0,t1,block_ids,status,updated_at) "
                + "SELECT chunk_key,MIN(t0),MAX(t1),'','done'," + System.currentTimeMillis()
                + " FROM " + TABLE + " WHERE chunk_key LIKE 'legacy:%' GROUP BY chunk_key");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_chains_dedup ON " + TABLE + "(dedup_key)");
        // 旧状态退休：needs_local_model → excluded_local
        db.execSQL("UPDATE " + TABLE
                + " SET status='excluded_local' WHERE status='needs_local_model'");
        if (agent != null) {
            for (Long id : deleteIds) agent.deleteSessionsForChain(id);
        }
        Log.i(TAG, "chains cleanup: kept=" + keepers.size() + " deleted=" + deleteIds.size());
    }

    /** 释放 superseded 行的 dedup 唯一键，允许同一批块重做时插入新链。 */
    public synchronized int releaseSupersededDedupKeys() {
        return getWritableDatabase().compileStatement(
                "UPDATE " + TABLE + " SET dedup_key=COALESCE(dedup_key,'')||'#superseded:'||id"
                        + " WHERE status='superseded' AND dedup_key NOT LIKE '%#superseded:%'")
                .executeUpdateDelete();
    }

    /** 已存在返回 -1；否则插入并返回 id。 */
    public synchronized long insertIfAbsent(int l0Index, String chunkKey, String title,
                                            String sessionIds, String blockIds,
                                            long t0, long t1, int nBlocks) {
        String dedup = ChainKey.ofBlockCsv(blockIds);
        Cursor c = getReadableDatabase().rawQuery("SELECT id,status FROM " + TABLE
                + " WHERE dedup_key=?", new String[]{dedup});
        if (c.moveToFirst()) {
            String oldStatus = c.getString(1);
            c.close();
            // 插入路径绝不改写已有行身份：规范 key 只属于 done 行
            if ("done".equals(oldStatus)) return -1;
            Log.w(TAG, "insert blocked: non-done row owns canonical key");
            return -1;
        } else {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("l0_index", l0Index);
        v.put("title", title);
        v.put("session_ids", sessionIds);
        v.put("block_ids", blockIds);
        v.put("t0", t0);
        v.put("t1", t1);
        v.put("n_blocks", nBlocks);
        v.put("status", "pending");
        v.put("created_at", System.currentTimeMillis());
        v.put("dedup_key", dedup);
        v.put("chunk_key", chunkKey);
        return getWritableDatabase().insert(TABLE, null, v);
    }

    public synchronized void updateBody(long id, String body, String keys, boolean ok) {
        updateBody(id, body, keys, ok, null);
    }

    public synchronized void updateBody(long id, String body, String keys, boolean ok,
                                        String textSource) {
        ContentValues v = new ContentValues();
        String b = body == null ? "" : body;
        v.put("body", b);
        v.put("key_block_ids", keys == null ? "" : keys);
        v.put("status", ok ? "done" : "failed");
        v.put("provisional", ok ? 0 : 1);
        v.put("over_length", b.length() > 700 ? 1 : 0);
        v.put("short_body", b.length() < 400 ? 1 : 0);
        if (textSource != null) v.put("text_source", textSource);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }

    /** partial 链模型没写缺口声明时标 false（body 仍接受）。 */
    public synchronized void setOutcome(long id, String outcome) {
        ContentValues v = new ContentValues();
        v.put("outcome", outcome == null ? "unknown" : outcome);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setGapDeclared(long id, boolean declared) {
        ContentValues v = new ContentValues();
        v.put("gap_declared", declared ? 1 : 0);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }

    /** NEVER_CLOUD 链：永远没有 body。旧 needs_local_model 已退休。 */
    public synchronized void markNeedsLocalModel(long id, String textSource) {
        ContentValues v = new ContentValues();
        v.putNull("body");
        v.put("status", "excluded_local");
        v.put("provisional", 1);
        v.put("text_source", textSource == null ? "excluded" : textSource);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }

    /** REDACT_THEN_CLOUD 链：Masker 接入前不出网、不生成 body。 */
    public synchronized void markNeedsMasker(long id) {
        ContentValues v = new ContentValues();
        v.putNull("body");
        v.put("status", "needs_masker");
        v.put("provisional", 1);
        v.put("text_source", "masked_pending");
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }

    /** 清理历史遗留：同一个 block 被多条 level=0 链覆盖时，只保留 id 最大（最新）的一条。 */
    public synchronized int supersedeDuplicateChains() {
        java.util.Map<Long, Long> newest = new java.util.HashMap<>();
        java.util.Map<Long, java.util.List<Long>> owners = new java.util.HashMap<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,block_ids FROM " + TABLE
                + " WHERE level=0 AND status NOT IN ('superseded')", null);
        while (c.moveToNext()) {
            long id = c.getLong(0);
            String csv = c.getString(1);
            if (csv == null) continue;
            for (String s : csv.split(",")) {
                try {
                    long b = Long.parseLong(s.trim());
                    newest.merge(b, id, Math::max);
                    owners.computeIfAbsent(b, k -> new java.util.ArrayList<>()).add(id);
                } catch (Throwable ignored) { }
            }
        }
        c.close();
        java.util.Set<Long> drop = new java.util.HashSet<>();
        for (java.util.Map.Entry<Long, java.util.List<Long>> e : owners.entrySet()) {
            if (e.getValue().size() < 2) continue;
            long keep = newest.get(e.getKey());
            for (Long id : e.getValue()) if (id != keep) drop.add(id);
        }
        if (drop.isEmpty()) return 0;
        Cursor p = getReadableDatabase().rawQuery("SELECT id,child_ids FROM " + TABLE
                + " WHERE level=1 AND status NOT IN ('superseded')", null);
        while (p.moveToNext()) {
            String csv = p.getString(1);
            if (csv == null) continue;
            for (String s : csv.split(",")) {
                try {
                    if (drop.contains(Long.parseLong(s.trim()))) {
                        drop.add(p.getLong(0));
                        break;
                    }
                } catch (Throwable ignored) { }
            }
        }
        p.close();
        int n = 0;
        for (Long id : drop) {
            ContentValues v = new ContentValues();
            v.put("status", "superseded");
            n += getWritableDatabase().update(TABLE, v,
                    "id=? AND status NOT IN ('superseded')",
                    new String[]{String.valueOf(id)});
        }
        if (n > 0) Log.w(TAG, "superseded " + n + " duplicate chain(s)");
        releaseSupersededDedupKeys();
        return n;
    }

    /** 块级重做：把与这些块有交集的旧链（含父链）标 superseded，再重新切分。 */
    public synchronized int supersedeForBlocks(java.util.List<Long> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) return 0;
        java.util.Set<Long> target = new java.util.HashSet<>(blockIds);
        java.util.Set<Long> superseded = new java.util.HashSet<>();
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id,block_ids FROM " + TABLE
                + " WHERE status NOT IN ('superseded')", null);
        while (c.moveToNext()) {
            String csv = c.getString(1);
            if (csv == null) continue;
            for (String s : csv.split(",")) {
                try {
                    if (target.contains(Long.parseLong(s.trim()))) {
                        superseded.add(c.getLong(0));
                        break;
                    }
                } catch (Throwable ignored) { }
            }
        }
        c.close();
        // 父链只要引用了被替换的子链，也一起 superseded
        if (!superseded.isEmpty()) {
            Cursor p = db.rawQuery("SELECT id,child_ids FROM " + TABLE
                    + " WHERE level=1 AND status NOT IN ('superseded')", null);
            while (p.moveToNext()) {
                String csv = p.getString(1);
                if (csv == null) continue;
                for (String s : csv.split(",")) {
                    try {
                        if (superseded.contains(Long.parseLong(s.trim()))) {
                            superseded.add(p.getLong(0));
                            break;
                        }
                    } catch (Throwable ignored) { }
                }
            }
            p.close();
        }
        int n = 0;
        for (Long id : superseded) {
            ContentValues v = new ContentValues();
            v.put("status", "superseded");
            n += db.update(TABLE, v, "id=? AND status NOT IN ('superseded')",
                    new String[]{String.valueOf(id)});
        }
        if (n > 0) Log.w(TAG, "superseded " + n + " chain(s) overlapping chunk blocks");
        releaseSupersededDedupKeys();
        return n;
    }

    public static final class Judgment {
        public final String verdict;
        public final String aSha;
        public final String bSha;
        Judgment(String v, String a, String b) { verdict = v; aSha = a; bSha = b; }
    }

    public synchronized Judgment judgment(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        Cursor c = getReadableDatabase().rawQuery("SELECT verdict,a_body_sha1,b_body_sha1"
                        + " FROM merge_judgments WHERE a_id=? AND b_id=?",
                new String[]{String.valueOf(lo), String.valueOf(hi)});
        Judgment j = null;
        if (c.moveToFirst()) j = new Judgment(c.getString(0), c.getString(1), c.getString(2));
        c.close();
        if (j != null && a > b) return new Judgment(j.verdict, j.bSha, j.aSha);
        return j;
    }

    public synchronized void putJudgment(int a, int b, String verdict,
                                         String aSha, String bSha) {
        ContentValues v = new ContentValues();
        v.put("a_id", Math.min(a, b));
        v.put("b_id", Math.max(a, b));
        v.put("verdict", verdict);
        v.put("a_body_sha1", a <= b ? aSha : bSha);
        v.put("b_body_sha1", a <= b ? bSha : aSha);
        v.put("at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("merge_judgments", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String sha1(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Throwable t) {
            return Integer.toHexString(String.valueOf(s).hashCode());
        }
    }

    public static final class Info {
        public final String status;
        public final String body;
        Info(String s, String b) { status = s == null ? "" : s; body = b == null ? "" : b; }
    }

    public synchronized Info info(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT status,body FROM " + TABLE + " WHERE id=?",
                new String[]{String.valueOf(id)});
        Info i = null;
        if (c.moveToFirst()) i = new Info(c.getString(0), c.getString(1));
        c.close();
        return i;
    }

    /** 调试/改算法后重算：所有父链 superseded，全部 parent_id 清 0。 */
    public synchronized void resetParents() {
        getWritableDatabase().execSQL("UPDATE " + TABLE
                + " SET dedup_key=COALESCE(dedup_key,'')||'#superseded:'||id"
                + " WHERE level>=1 AND status!='superseded'");
        ContentValues pv = new ContentValues();
        pv.put("status", "superseded");
        getWritableDatabase().update(TABLE, pv, "level>=1", null);
        ContentValues cv = new ContentValues();
        cv.put("parent_id", 0);
        getWritableDatabase().update(TABLE, cv, "parent_id>0", null);
        Log.w(TAG, "reset all parent chains (debug)");
    }

    /** 按 child_ids 找父链（不依赖可能被迁移改写的 dedup_key）。 */
    public synchronized long idByChildIds(String childIds) {
        if (childIds == null || childIds.isEmpty()) return -1;
        java.util.Set<Long> want = new java.util.LinkedHashSet<>();
        for (String s : childIds.split(",")) {
            try { want.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
        }
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,child_ids FROM " + TABLE + " WHERE level>0", null);
        long found = -1;
        while (c.moveToNext()) {
            String csv = c.getString(1);
            if (csv == null) continue;
            java.util.Set<Long> have = new java.util.LinkedHashSet<>();
            for (String s : csv.split(",")) {
                try { have.add(Long.parseLong(s.trim())); } catch (Throwable ignored) { }
            }
            if (have.equals(want)) { found = c.getLong(0); break; }
        }
        c.close();
        return found;
    }

    public synchronized String outcomeById(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT outcome FROM " + TABLE + " WHERE id=?",
                new String[]{String.valueOf(id)});
        String v = c.moveToFirst() && c.getString(0) != null ? c.getString(0) : "unknown";
        c.close();
        return v;
    }

    /** L3：当天所有完成的事件（level=0，status=done）。 */
    public synchronized List<ChainRow> doneEventsBetween(long from, long to) {
        return queryRows("SELECT id,title,body,t0,t1,n_blocks,block_ids,key_block_ids," +
                        "parent_id,level,covered_ms FROM " + TABLE +
                        " WHERE level=0 AND status='done' AND t0>=? AND t0<=? ORDER BY t0,id",
                new String[]{String.valueOf(from), String.valueOf(to)});
    }

    public synchronized long idByDedup(String dedup) {
        Cursor c = getReadableDatabase().rawQuery("SELECT id FROM " + TABLE
                + " WHERE dedup_key=?", new String[]{dedup == null ? "" : dedup});
        long id = c.moveToFirst() ? c.getLong(0) : -1;
        c.close();
        return id;
    }

    /** 父链被 superseded 时，子链 parent_id 必须清回 0，避免永久孤儿。 */
    public synchronized void supersedeParentAndClear(long parentId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT child_ids FROM " + TABLE
                + " WHERE id=?", new String[]{String.valueOf(parentId)});
        String children = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        ContentValues pv = new ContentValues();
        pv.put("status", "superseded");
        getWritableDatabase().update(TABLE, pv, "id=?",
                new String[]{String.valueOf(parentId)});
        getWritableDatabase().execSQL("UPDATE " + TABLE
                        + " SET dedup_key=COALESCE(dedup_key,'')||'#superseded:'||id"
                        + " WHERE id=?",
                new Object[]{parentId});
        if (children != null) {
            ContentValues cv = new ContentValues();
            cv.put("parent_id", 0);
            for (String s : children.split(",")) {
                try {
                    getWritableDatabase().update(TABLE, cv, "id=?",
                            new String[]{String.valueOf(Long.parseLong(s.trim()))});
                } catch (Throwable ignored) { }
            }
        }
    }

    public synchronized int clearSupersededParentLinks() {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,child_ids FROM " + TABLE
                + " WHERE status='superseded' AND level>0", null);
        int n = 0;
        while (c.moveToNext()) {
            String children = c.getString(1);
            if (children == null) continue;
            ContentValues cv = new ContentValues();
            cv.put("parent_id", 0);
            for (String s : children.split(",")) {
                try {
                    n += getWritableDatabase().update(TABLE, cv, "id=?",
                            new String[]{String.valueOf(Long.parseLong(s.trim()))});
                } catch (Throwable ignored) { }
            }
        }
        c.close();
        return n;
    }

    public synchronized boolean chunkDone(String chunkKey) {
        Cursor c = getReadableDatabase().rawQuery("SELECT status FROM l0_chunks"
                + " WHERE chunk_key=?", new String[]{chunkKey});
        boolean done = c.moveToFirst() && "done".equals(c.getString(0));
        c.close();
        return done;
    }

    public synchronized void markChunk(String chunkKey, long t0, long t1,
                                       String blockIds, String status) {
        ContentValues v = new ContentValues();
        v.put("chunk_key", chunkKey);
        v.put("t0", t0);
        v.put("t1", t1);
        v.put("block_ids", blockIds);
        v.put("status", status);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("l0_chunks", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** 该 chunk 下所有子链都 done（且至少一条）。 */
    public synchronized boolean chunkAllDone(String chunkKey) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), SUM(CASE WHEN status='done' THEN 1 ELSE 0 END) FROM "
                        + TABLE + " WHERE chunk_key=?", new String[]{chunkKey});
        boolean ok = false;
        if (c.moveToFirst()) {
            long total = c.getLong(0);
            long done = c.isNull(1) ? 0 : c.getLong(1);
            ok = total > 0 && done == total;
        }
        c.close();
        return ok;
    }

    public synchronized String statusByDedup(String dedupKey) {
        Cursor c = getReadableDatabase().rawQuery("SELECT status FROM " + TABLE
                + " WHERE dedup_key=? AND status!='superseded' ORDER BY id DESC LIMIT 1",
                new String[]{dedupKey});
        String s = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return s;
    }

    public synchronized void assignChunkKey(String dedupKey, String chunkKey) {
        ContentValues v = new ContentValues();
        v.put("chunk_key", chunkKey);
        getWritableDatabase().update(TABLE, v, "dedup_key=?", new String[]{dedupKey});
    }

    // ---------------- 执行租约 ----------------

    /** @return true = 拿到租约；false = 已有 2 小时内的 running。 */
    /** 进程重启后清理孤儿租约；只在确认本进程没有流水线时调用。 */
    public synchronized int failOrphanRuns(String reason) {
        ContentValues v = new ContentValues();
        v.put("status", "failed");
        v.put("finished_at", System.currentTimeMillis());
        v.put("error", reason == null ? "process-restarted" : reason);
        int n = getWritableDatabase().update(RUNS, v,
                "status='running'", null);
        if (n > 0) Log.w(TAG, "cleaned " + n + " orphan pipeline lease(s): " + reason);
        return n;
    }

    /** 调试：强制抢占当前 running 租约。 */
    public synchronized int forceReleaseRunning(String reason) {
        ContentValues v = new ContentValues();
        v.put("status", "failed");
        v.put("finished_at", System.currentTimeMillis());
        v.put("error", reason == null ? "force release" : reason);
        int n = getWritableDatabase().update(RUNS, v,
                "status='running'", null);
        if (n > 0) Log.w(TAG, "force released " + n + " pipeline lease(s)");
        return n;
    }

    public synchronized boolean acquireLease(String runId, int hours, long from, long to) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        Cursor c = db.rawQuery("SELECT id,started_at FROM " + RUNS
                + " WHERE status='running' ORDER BY started_at DESC", null);
        List<String> zombies = new ArrayList<>();
        boolean busy = false;
        while (c.moveToNext()) {
            long started = c.getLong(1);
            if (now - started < LEASE_MS) {
                busy = true;
                Log.w(TAG, "pipeline lease rejected: " + c.getString(0)
                        + " running for " + ((now - started) / 1000) + "s");
            } else {
                zombies.add(c.getString(0));
            }
        }
        c.close();
        if (busy) return false;
        for (String z : zombies) {
            ContentValues zz = new ContentValues();
            zz.put("status", "failed");
            zz.put("finished_at", now);
            zz.put("error", "zombie lease stolen");
            db.update(RUNS, zz, "id=?", new String[]{z});
            Log.w(TAG, "stole zombie lease " + z);
        }
        ContentValues v = new ContentValues();
        v.put("id", runId);
        v.put("status", "running");
        v.put("started_at", now);
        v.put("hours", hours);
        v.put("block_from", from);
        v.put("block_to", to);
        return db.insert(RUNS, null, v) >= 0;
    }

    public synchronized void finishLease(String runId, String status, int chunks, int chains,
                                         int bodies, long prompt, long completion, long cache,
                                         String error) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("finished_at", System.currentTimeMillis());
        if (chunks >= 0) v.put("l0_chunks", chunks);
        if (chains >= 0) v.put("chains", chains);
        if (bodies >= 0) v.put("bodies", bodies);
        v.put("prompt_tokens", prompt);
        v.put("completion_tokens", completion);
        v.put("cache_hit", cache);
        v.put("error", error == null ? "" : error);
        getWritableDatabase().update(RUNS, v, "id=?", new String[]{runId});
    }

    public synchronized Cursor lastRun() {
        return getReadableDatabase().rawQuery("SELECT * FROM " + RUNS
                + " ORDER BY started_at DESC LIMIT 1", null);
    }

    public static final class LastRun {
        public final String id;
        public final String status;
        public final long startedAt;
        public final long finishedAt;
        public final int chunks;
        public final int chains;
        public final int bodies;
        public final long prompt;
        public final long completion;
        public final long cache;
        public final String error;

        LastRun(String id, String status, long startedAt, long finishedAt, int chunks,
                int chains, int bodies, long prompt, long completion, long cache,
                String error) {
            this.id = id == null ? "" : id;
            this.status = status == null ? "" : status;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.chunks = chunks;
            this.chains = chains;
            this.bodies = bodies;
            this.prompt = prompt;
            this.completion = completion;
            this.cache = cache;
            this.error = error == null ? "" : error;
        }
    }

    public synchronized LastRun lastRunData() {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,status,started_at,finished_at,"
                + "l0_chunks,chains,bodies,prompt_tokens,completion_tokens,cache_hit,error FROM "
                + RUNS + " ORDER BY started_at DESC LIMIT 1", null);
        LastRun r = null;
        if (c.moveToFirst()) {
            r = new LastRun(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3),
                    c.getInt(4), c.getInt(5), c.getInt(6), c.getLong(7), c.getLong(8),
                    c.getLong(9), c.getString(10));
        }
        c.close();
        return r;
    }

    public synchronized LastRun lastSuccess() {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,status,started_at,finished_at,"
                + "l0_chunks,chains,bodies,prompt_tokens,completion_tokens,cache_hit,error FROM "
                + RUNS + " WHERE status='done' ORDER BY finished_at DESC LIMIT 1", null);
        LastRun r = null;
        if (c.moveToFirst()) {
            r = new LastRun(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3),
                    c.getInt(4), c.getInt(5), c.getInt(6), c.getLong(7), c.getLong(8),
                    c.getLong(9), c.getString(10));
        }
        c.close();
        return r;
    }

    public synchronized int consecutiveFailures() {
        Cursor c = getReadableDatabase().rawQuery("SELECT status FROM " + RUNS
                + " ORDER BY started_at DESC LIMIT 5", null);
        int n = 0;
        while (c.moveToNext()) {
            if ("done".equals(c.getString(0))) break;
            n++;
        }
        c.close();
        return n;
    }

    public Cursor all() {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE + " ORDER BY id", null);
    }

    /** 合并 pass 用的只读快照。 */
    public static final class ChainRow {
        public final long id;
        public final String title;
        public final String body;
        public final long t0;
        public final long t1;
        public final int nBlocks;
        public final String blockIds;
        public final String keyBlockIds;
        public final long parentId;
        public final int level;
        public final long coveredMs;

        ChainRow(long id, String title, String body, long t0, long t1, int nBlocks,
                 String blockIds, String keyBlockIds, long parentId, int level) {
            this(id, title, body, t0, t1, nBlocks, blockIds, keyBlockIds, parentId,
                    level, 0);
        }

        ChainRow(long id, String title, String body, long t0, long t1, int nBlocks,
                 String blockIds, String keyBlockIds, long parentId, int level,
                 long coveredMs) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.t0 = t0;
            this.t1 = t1;
            this.nBlocks = nBlocks;
            this.blockIds = blockIds == null ? "" : blockIds;
            this.keyBlockIds = keyBlockIds == null ? "" : keyBlockIds;
            this.parentId = parentId;
            this.level = level;
            this.coveredMs = coveredMs;
        }
    }

    public synchronized List<ChainRow> childrenForChunk(String chunkKey) {
        return queryRows("SELECT id,title,body,t0,t1,n_blocks,block_ids,key_block_ids,"
                        + "parent_id,level,covered_ms FROM " + TABLE
                        + " WHERE chunk_key=? AND level=0 AND status='done' ORDER BY id",
                new String[]{chunkKey});
    }

    public synchronized List<ChainRow> parentsForChunk(String chunkKey) {
        return queryRows("SELECT id,title,body,t0,t1,n_blocks,block_ids,key_block_ids,"
                        + "parent_id,level,covered_ms FROM " + TABLE
                        + " WHERE chunk_key=? AND level=1 ORDER BY id",
                new String[]{chunkKey});
    }

    public synchronized List<ChainRow> childrenOfParent(long parentId) {
        return queryRows("SELECT id,title,body,t0,t1,n_blocks,block_ids,key_block_ids,"
                        + "parent_id,level,covered_ms FROM " + TABLE
                        + " WHERE parent_id=? AND level=0 ORDER BY t0,id",
                new String[]{String.valueOf(parentId)});
    }

    public synchronized List<ChainRow> parents() {
        return queryRows("SELECT id,title,body,t0,t1,n_blocks,block_ids,key_block_ids,"
                        + "parent_id,level,covered_ms FROM " + TABLE
                        + " WHERE level=1 ORDER BY id", new String[]{});
    }

    private List<ChainRow> queryRows(String sql, String[] args) {
        List<ChainRow> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(row(c));
        c.close();
        return out;
    }

    private static ChainRow row(Cursor c) {
        return new ChainRow(c.getLong(0), c.getString(1), c.getString(2),
                c.getLong(3), c.getLong(4), c.getInt(5), c.getString(6),
                c.getString(7), c.getLong(8), c.getInt(9), c.getLong(10));
    }

    /** 创建父链（dedup=union block ids）；已存在返回 -1。 */
    public synchronized long insertParent(String chunkKey, String title, String childIds,
                                          String blockIds, long t0, long t1, String why) {
        // 父链身份 = sha1(排序后的 child_ids)，内容变化不影响身份
        String dedup = ChainKey.ofBlockCsv(childIds);
        Cursor c = getReadableDatabase().rawQuery("SELECT id,status FROM " + TABLE
                + " WHERE dedup_key=?", new String[]{dedup});
        if (c.moveToFirst()) {
            String oldStatus = c.getString(1);
            c.close();
            // 插入路径绝不改写已有行身份：规范 key 只属于 done 行
            if ("done".equals(oldStatus)) return -1;
            Log.w(TAG, "insert blocked: non-done row owns canonical key");
            return -1;
        } else {
            c.close();
        }
        int maxChildLevel = -1;
        for (String s : childIds.split(",")) {
            long cid;
            try { cid = Long.parseLong(s.trim()); } catch (Throwable t) { continue; }
            Cursor cc = getReadableDatabase().rawQuery(
                    "SELECT level,parent_id FROM " + TABLE + " WHERE id=?",
                    new String[]{String.valueOf(cid)});
            if (cc.moveToFirst()) {
                maxChildLevel = Math.max(maxChildLevel, cc.getInt(0));
                if (cc.getInt(1) > 0) { cc.close(); return -1; } // 一个子链只能有一个父链
            }
            cc.close();
        }
        if (maxChildLevel >= 2) return -1; // 深度上限 3 层：level 0/1/2
        ContentValues v = new ContentValues();
        v.put("l0_index", -1);
        v.put("title", title);
        v.put("session_ids", "");
        v.put("block_ids", blockIds);
        v.put("t0", t0);
        v.put("t1", t1);
        v.put("n_blocks", blockIds.split(",").length);
        v.put("status", "pending");
        v.put("created_at", System.currentTimeMillis());
        v.put("dedup_key", dedup);
        v.put("chunk_key", chunkKey);
        v.put("parent_id", 0);
        v.put("child_ids", childIds);
        v.put("level", maxChildLevel + 1);
        v.put("merge_why", why);
        return getWritableDatabase().insert(TABLE, null, v);
    }

    public synchronized void setParent(long childId, long parentId) {
        ContentValues v = new ContentValues();
        v.put("parent_id", parentId);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(childId)});
    }

    public synchronized void updateParentBody(long id, String body) {
        ContentValues v = new ContentValues();
        v.put("body", body == null ? "" : body);
        v.put("status", "done");
        v.put("provisional", 0);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateParentBody(long id, String body, String keyIds) {
        updateParentBody(id, body, keyIds, ChainOutcome.UNKNOWN.dbValue);
    }

    public synchronized void updateParentBody(long id, String body, String keyIds,
                                              String outcome) {
        ContentValues v = new ContentValues();
        String b = body == null ? "" : body;
        v.put("body", b);
        v.put("key_block_ids", keyIds == null ? "" : keyIds);
        v.put("outcome", outcome == null ? ChainOutcome.UNKNOWN.dbValue : outcome);
        v.put("status", "done");
        v.put("provisional", 0);
        v.put("over_length", b.length() > 800 ? 1 : 0);
        v.put("short_body", b.length() < 400 ? 1 : 0);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(id)});
    }
}
