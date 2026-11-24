package com.dsh.memory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.dsh.host.api.MemoryQuery;
import com.dsh.host.api.data.LabelStatus;
import com.dsh.host.api.data.MemoryBlock;
import com.dsh.host.api.data.RedactionLevel;
import com.dsh.host.api.memory.EgressTier;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记忆块持久化 + 查询。本期只有"存 + 查 + 脱敏升级"，没有链聚合、没有向量。
 *
 * schema 迁移：SQL 与版本判断都在 {@link MemorySchema}（纯逻辑、可单测）；
 * 这里只负责执行。迁移走两条路，保证旧库一定能打开：
 *  1) onUpgrade(old→new)：正常版本升级路径；
 *  2) onOpen 里用 PRAGMA table_info 逐列兜底：即使 user_version 已经是最新、
 *     但列还没加上（迁移被打断 / 历史半迁移库），也补 ALTER TABLE。
 *
 * 检索：优先 FTS5 trigram + BM25（{@link MemoryFts}）；Android 的 SQLite 默认没有 FTS5，
 * 所以实际主路径是 LIKE 候选池 + {@link LikeRanker} 打分排序。引擎/原因由
 * {@link #searchEngine()} / {@link #searchEngineReason()} 暴露，绝不静默降级。
 */
public final class MemoryStore extends SQLiteOpenHelper implements MemoryQuery {

    public static final String NAME = "memory.db";
    public static final String EXPORT_FILE = "export_recent.json";

    /** 固定的直接日志 tag（不经过宿主 Log 封装）：adb logcat -s DSHFts 可见 */
    public static final String TAG_FTS = "DSHFts";

    private final File dir;
    /** 实际检索引擎："FTS5" / "LIKE"；在 onOpen 里探测/安装 */
    private volatile String searchEngine = MemoryFtsInstaller.ENGINE_LIKE;
    /** 降级原因：fts5-unavailable / no-trigram / sql-error:<msg> / ok */
    private volatile String searchEngineReason = "not-initialized";
    /** LIKE 候选池上限（配置 memory.search.candidateCap，默认 800） */
    private volatile int candidateCap = LikeRanker.DEFAULT_CANDIDATE_CAP;

    public MemoryStore(Context c) {
        this(c, dirOf(c));
    }

    private MemoryStore(Context c, File dir) {
        super(c, new File(dir, NAME).getAbsolutePath(), null, MemorySchema.DB_VER);
        this.dir = dir;
    }

    private static File dirOf(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        File f = new File(d, "memory");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /** 数据库与导出文件的目录（状态页/调试用） */
    public File dir() { return dir; }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(MemorySchema.CREATE_BLOCKS);
        for (String sql : MemorySchema.CREATE_INDEXES) db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        for (String sql : MemorySchema.upgradeSql(oldV)) execQuiet(db, sql);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // 兜底：版本号已经对、列却没加上的旧库（被打断的迁移）
        for (java.util.Map.Entry<String, String> e : MemorySchema.COLUMN_DDL.entrySet()) {
            if (!hasColumn(db, e.getKey())) execQuiet(db, e.getValue());
        }
        // FTS5 索引：建表失败（Android AOSP 默认无 FTS5 / 无 trigram）→ 回退 LIKE+Ranker。
        // 原因必须可见：searchEngineReason + 直接 Log tag DSHFts（不依赖宿主日志封装）。
        MemoryFtsInstaller.Result r = MemoryFtsInstaller.install(new MemoryFtsInstaller.Db() {
            @Override public boolean tableExists(String table) { return MemoryStore.tableExists(db, table); }
            @Override public boolean triggerExists(String trigger) { return MemoryStore.triggerExists(db, trigger); }
            @Override public void execSQL(String sql) { db.execSQL(sql); }
        });
        searchEngine = r.engine;
        searchEngineReason = r.reason;
        try {
            Log.i(TAG_FTS, "engine=" + r.engine + " reason=" + r.reason
                    + " dbVer=" + MemorySchema.DB_VER + " candidateCap=" + candidateCap);
        } catch (Throwable ignored) {
            // 某些 ROM 限制 logcat 时不能影响 DB 打开
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name=?",
                    new String[]{table});
            return c.moveToFirst();
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    private static boolean triggerExists(SQLiteDatabase db, String trigger) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger' AND name=?",
                    new String[]{trigger});
            return c.moveToFirst();
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String column) {
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(" + MemorySchema.TABLE + ")", null);
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameIdx))) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    private static void execQuiet(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (Throwable ignored) {
            // 列已存在（并发迁移）/重复执行：忽略；读取侧对缺列也有兜底
        }
    }

    public long insert(MemoryBlock b) {
        ContentValues v = new ContentValues();
        v.put("episode_id", b.episodeId);
        v.put("t_start", b.tStart);
        v.put("t_end", b.tEnd);
        v.put("pkg", b.pkg);
        v.put("title", b.title);
        v.put("masked_text", b.maskedText);
        v.put("privacy_tier", b.privacyTier);
        v.put("hit_summary", b.hitSummary);
        v.put("schema_ver", b.schemaVer);
        // 不透明 token 原样落库；null 存 NULL
        v.put(MemorySchema.COL_EVIDENCE_REF, b.evidenceRef);
        v.put(MemorySchema.COL_REDACTION_LEVEL, RedactionLevel.isValid(b.redactionLevel)
                ? b.redactionLevel : RedactionLevel.RULE);
        v.put(MemorySchema.COL_REDACTED_AT, b.redactedAt);
        v.put(MemorySchema.COL_EGRESS_TIER,
                b.egressTier == null ? EgressTier.UNKNOWN.code : b.egressTier.code);
        v.put(MemorySchema.COL_LABEL, b.label);
        v.put(MemorySchema.COL_LABEL_MODEL, b.labelModel);
        v.put(MemorySchema.COL_LABELED_AT, b.labeledAt);
        v.put(MemorySchema.COL_LABEL_STATUS, LabelStatus.isValid(b.labelStatus)
                ? b.labelStatus : LabelStatus.PENDING);
        long id = getWritableDatabase().insert(MemorySchema.TABLE, null, v);
        b.id = id;
        return id;
    }

    // ---------------- MemoryQuery ----------------

    @Override public List<MemoryBlock> recent(int n) {
        return query("SELECT * FROM blocks ORDER BY t_start DESC LIMIT " + Math.max(1, n));
    }

    @Override public List<MemoryBlock> between(long fromTs, long toTs) {
        return query("SELECT * FROM blocks WHERE t_start>=? AND t_start<=? ORDER BY t_start",
                String.valueOf(fromTs), String.valueOf(toTs));
    }

    @Override public List<MemoryBlock> search(String text, int limit) {
        return search(text, limit, null);
    }

    /**
     * FTS5 trigram + BM25 优先；Android 无 FTS5（默认）就走 LIKE 候选池 + {@link LikeRanker}
     * 打分排序。单次 FTS 查询失败也回退 LIKE；引擎状态如实保留，不静默改。
     */
    @Override public List<MemoryBlock> search(String text, int limit, EgressTier tier) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        int lim = Math.max(1, limit);
        String q = text.trim();

        if (MemoryFtsInstaller.ENGINE_FTS5.equals(searchEngine) && MemoryFts.eligible(q)) {
            try {
                String phrase = MemoryFts.phrase(q);
                if (tier == null) {
                    return query(MemoryFts.searchSql(lim), phrase);
                }
                return query(MemoryFts.searchSqlWithTier(lim), phrase, String.valueOf(tier.code));
            } catch (Throwable t) {
                // 单次查询失败（索引损坏等）：本次退回 LIKE；引擎状态如实保留
            }
        }

        // LIKE 主路径：先取足够大的候选池（不要过早 LIMIT 5），再在 Java 里按相关度排序
        int cap = LikeRanker.clampCap(candidateCap);
        String pattern = MemorySql.searchPattern(text);
        List<MemoryBlock> candidates;
        if (tier == null) {
            candidates = query(MemorySql.searchSql(cap), pattern, pattern, pattern);
        } else {
            candidates = query(MemorySql.searchSqlWithTier(cap), pattern, pattern, pattern,
                    String.valueOf(tier.code));
        }
        return LikeRanker.rank(candidates, q, lim);
    }

    @Override public String searchEngine() {
        // 触发第一次打开（onOpen 里探测/安装 FTS）
        try { getReadableDatabase(); } catch (Throwable ignored) { }
        return searchEngine;
    }

    /** 降级原因（ok / fts5-unavailable / no-trigram / sql-error:…） */
    public String searchEngineReason() {
        try { getReadableDatabase(); } catch (Throwable ignored) { }
        return searchEngineReason;
    }

    /** 配置热更新：LIKE 候选池上限 */
    public void setCandidateCap(int cap) {
        candidateCap = LikeRanker.clampCap(cap);
    }

    public int candidateCap() { return candidateCap; }

    @Override public long count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + MemorySchema.TABLE, null);
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }

    @Override public List<MemoryBlock> byRedactionLevel(int level, int limit) {
        // 批处理挑活：从旧到新，先处理最容易积压的
        return query("SELECT * FROM blocks WHERE " + MemorySchema.COL_REDACTION_LEVEL
                        + "=? ORDER BY t_start ASC LIMIT " + Math.max(1, limit),
                String.valueOf(level));
    }

    @Override public long countByRedactionLevel(int level) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + MemorySchema.TABLE + " WHERE "
                        + MemorySchema.COL_REDACTION_LEVEL + "=?",
                new String[]{String.valueOf(level)});
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }

    @Override public List<MemoryBlock> byEgressTier(EgressTier tier, int limit) {
        if (tier == null) return Collections.emptyList();
        return query(MemorySql.byEgressTierSql(limit), String.valueOf(tier.code));
    }

    /** 状态页/调试用：某档位的块数（不在 MemoryQuery 里，避免接口膨胀） */
    public long countByEgressTier(EgressTier tier) {
        if (tier == null) return 0;
        Cursor c = getReadableDatabase().rawQuery(
                MemorySql.countByEgressTierSql(), new String[]{String.valueOf(tier.code)});
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }

    @Override public int updateRedaction(long episodeId, String maskedText,
                                        int redactionLevel, long redactedAt) {
        if (!RedactionLevel.isValid(redactionLevel) || maskedText == null) return 0;
        ContentValues v = new ContentValues();
        v.put("masked_text", maskedText);
        v.put(MemorySchema.COL_REDACTION_LEVEL, redactionLevel);
        v.put(MemorySchema.COL_REDACTED_AT, redactedAt);
        // 只升不降：where 里卡 level < 目标等级，0 行 = 没找到 / 已经是更高等级
        return getWritableDatabase().update(MemorySchema.TABLE, v,
                "episode_id=? AND " + MemorySchema.COL_REDACTION_LEVEL + "<?",
                new String[]{String.valueOf(episodeId), String.valueOf(redactionLevel)});
    }

    // ---------------- 云端打标 ----------------

    /**
     * 待打标块：**只取 WHITE**（SQL 里写死），label 为空且未被 SKIPPED，从旧到新。
     * 这是出网不变式的第一道闸；调用方发送前还会再校验一次 egressTier。
     */
    @Override public List<MemoryBlock> blocksNeedingLabel(int limit) {
        return query(MemorySql.blocksNeedingLabelSql(limit),
                String.valueOf(EgressTier.WHITE.code));
    }

    @Override public int updateLabel(long id, String label, String model, long ts, int status) {
        if (!LabelStatus.isValid(status)) return 0;
        ContentValues v = new ContentValues();
        v.put(MemorySchema.COL_LABEL, label);
        v.put(MemorySchema.COL_LABEL_MODEL, model);
        v.put(MemorySchema.COL_LABELED_AT, ts);
        v.put(MemorySchema.COL_LABEL_STATUS, status);
        return getWritableDatabase().update(MemorySchema.TABLE, v,
                "id=?", new String[]{String.valueOf(id)});
    }

    @Override public long countByLabelStatus(int status) {
        Cursor c = getReadableDatabase().rawQuery(
                MemorySql.countByLabelStatusSql(), new String[]{String.valueOf(status)});
        long n = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return n;
    }

    // ---------------- 调试导出 ----------------

    @Override public String exportRecentJson(int n) {
        return exportRecentJson(n, "");
    }

    /**
     * 导出最近 n 条 + 可选"检索自测"（demoQuery 非空时把命中一并写进 JSON）。
     * 返回 JSON 字符串；不写文件。
     */
    public String exportRecentJson(int n, String demoQuery) {
        return exportRecentJson(n, demoQuery, -1);
    }

    /**
     * 带对账的导出：episodesTotal 来自 usagetrace（拿不到传 -1）。
     * blocksTotal 用本库 COUNT(*)，与正在导出的（可能被 n 截断的）列表区分开。
     */
    public String exportRecentJson(int n, String demoQuery, long episodesTotal) {
        List<MemoryBlock> blocks = recent(n);
        String q = demoQuery == null ? "" : demoQuery.trim();
        List<MemoryBlock> hits = q.isEmpty() ? Collections.emptyList() : search(q, 10);
        long blocksTotal;
        try { blocksTotal = count(); } catch (Throwable t) { blocksTotal = -1; }
        return MemoryExport.json(blocks, q, hits, episodesTotal, blocksTotal,
                searchEngine(), searchEngineReason(), labelStats());
    }

    /** 打标统计（导出/状态页）：总数为各状态之和；查询失败如实 -1 */
    public MemoryExport.LabelStats labelStats() {
        try {
            return new MemoryExport.LabelStats(
                    countByLabelStatus(LabelStatus.OK),
                    countByLabelStatus(LabelStatus.FAILED),
                    countByLabelStatus(LabelStatus.SKIPPED),
                    countByLabelStatus(LabelStatus.PENDING));
        } catch (Throwable t) {
            return MemoryExport.LabelStats.unknown();
        }
    }

    /**
     * 把导出 JSON 写到数据库同目录的 export_recent.json（adb 可拉）。
     * @return 文件；失败返回 null（调试功能失败不影响主流程）
     */
    public File writeExport(int n, String demoQuery) {
        return writeExport(n, demoQuery, -1);
    }

    public File writeExport(int n, String demoQuery, long episodesTotal) {
        try {
            String json = exportRecentJson(n, demoQuery, episodesTotal);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, EXPORT_FILE);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- 读取 ----------------

    private List<MemoryBlock> query(String sql, String... args) {
        List<MemoryBlock> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        while (c.moveToNext()) out.add(read(c));
        c.close();
        return out;
    }

    private MemoryBlock read(Cursor c) {
        MemoryBlock b = new MemoryBlock();
        b.id = c.getLong(c.getColumnIndexOrThrow("id"));
        b.episodeId = c.getLong(c.getColumnIndexOrThrow("episode_id"));
        b.tStart = c.getLong(c.getColumnIndexOrThrow("t_start"));
        b.tEnd = c.getLong(c.getColumnIndexOrThrow("t_end"));
        b.pkg = str(c, "pkg");
        b.title = str(c, "title");
        b.maskedText = str(c, "masked_text");
        b.privacyTier = str(c, "privacy_tier");
        b.hitSummary = str(c, "hit_summary");
        b.schemaVer = c.getInt(c.getColumnIndexOrThrow("schema_ver"));
        b.evidenceRef = strOrNull(c, MemorySchema.COL_EVIDENCE_REF);
        b.redactionLevel = intOr(c, MemorySchema.COL_REDACTION_LEVEL, RedactionLevel.RULE);
        b.redactedAt = longOr(c, MemorySchema.COL_REDACTED_AT, 0);
        b.egressTier = EgressTier.fromCode(intOr(c, MemorySchema.COL_EGRESS_TIER, EgressTier.UNKNOWN.code));
        b.label = strOrNull(c, MemorySchema.COL_LABEL);
        b.labelModel = strOrNull(c, MemorySchema.COL_LABEL_MODEL);
        b.labeledAt = longOr(c, MemorySchema.COL_LABELED_AT, 0);
        b.labelStatus = intOr(c, MemorySchema.COL_LABEL_STATUS, LabelStatus.PENDING);
        // FTS5 路径会多一列 search_score；LIKE 路径没有该列 → 0（表示没有相关性排序）
        int scoreIdx = c.getColumnIndex("search_score");
        if (scoreIdx >= 0 && !c.isNull(scoreIdx)) b.searchScore = c.getDouble(scoreIdx);
        return b;
    }

    private String str(Cursor c, String col) {
        int i = c.getColumnIndexOrThrow(col);
        return c.isNull(i) ? "" : c.getString(i);
    }

    /** 列不存在或值为 NULL 都返回 null：不把"没有"伪装成空串 */
    private String strOrNull(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return null;
        return c.getString(i);
    }

    private int intOr(Cursor c, String col, int def) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? def : c.getInt(i);
    }

    private long longOr(Cursor c, String col, long def) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? def : c.getLong(i);
    }
}
