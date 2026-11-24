package com.dsh.memory;

/**
 * FTS5 索引的 SQL 常量 + 查询转义（纯逻辑，JVM 可测）。
 *
 * 设计：
 *  - trigram tokenizer：默认 unicode61 会把整句中文当一个 token，子串检索不到；
 *  - 外部内容表（content='blocks', content_rowid='id'）：索引不双份存文本；
 *  - 三个触发器维护索引，不依赖应用层记得同步；
 *  - 用户 query 必须包成 FTS 短语（内部 " 双写），否则 - * ( 会被当语法炸。
 *
 * 降级：trigram 需要 SQLite 3.34+（Android 12/API31 是 3.32），
 * 建表失败由 {@link MemoryFtsInstaller} 捕获并退回 LIKE，状态由 searchEngine 暴露。
 */
public final class MemoryFts {

    public static final String TABLE = "blocks_fts";
    /** FTS 查询短于 3 个字符时 trigram 无法命中（3-gram），退回 LIKE */
    public static final int MIN_TRIGRAM_CHARS = 3;

    public static final String CREATE =
            "CREATE VIRTUAL TABLE " + TABLE + " USING fts5(" +
                    "title, masked_text, " +
                    "content='blocks', content_rowid='id', " +
                    "tokenize='trigram')";

    /** INSERT 触发器：新行进索引 */
    public static final String TRIGGER_INSERT =
            "CREATE TRIGGER IF NOT EXISTS blocks_fts_ai AFTER INSERT ON blocks BEGIN " +
                    "INSERT INTO " + TABLE + "(rowid, title, masked_text) " +
                    "VALUES (new.id, new.title, new.masked_text); END";

    /** DELETE 触发器：用 FTS5 'delete' 命令精确移除（外部内容表必须带旧值） */
    public static final String TRIGGER_DELETE =
            "CREATE TRIGGER IF NOT EXISTS blocks_fts_ad AFTER DELETE ON blocks BEGIN " +
                    "INSERT INTO " + TABLE + "(" + TABLE + ", rowid, title, masked_text) " +
                    "VALUES ('delete', old.id, old.title, old.masked_text); END";

    /** UPDATE 触发器：先删旧值再插新值（脱敏升级会 UPDATE masked_text） */
    public static final String TRIGGER_UPDATE =
            "CREATE TRIGGER IF NOT EXISTS blocks_fts_au AFTER UPDATE ON blocks BEGIN " +
                    "INSERT INTO " + TABLE + "(" + TABLE + ", rowid, title, masked_text) " +
                    "VALUES ('delete', old.id, old.title, old.masked_text); " +
                    "INSERT INTO " + TABLE + "(rowid, title, masked_text) " +
                    "VALUES (new.id, new.title, new.masked_text); END";

    public static final String[] TRIGGERS = {TRIGGER_INSERT, TRIGGER_DELETE, TRIGGER_UPDATE};

    /** 触发器名（用于检测半迁移状态 + 失败清理） */
    public static final String[] TRIGGER_NAMES = {"blocks_fts_ai", "blocks_fts_ad", "blocks_fts_au"};

    /** 对已有行回填索引 */
    public static final String REBUILD = "INSERT INTO " + TABLE + "(" + TABLE + ") VALUES('rebuild')";

    private MemoryFts() { }

    /**
     * 用户 query → FTS 短语。空输入返回空串（调用方直接返回空列表）。
     * 例：`foo "bar"` → `"foo ""bar"""`；`- * (` 也变成字面量短语，不会当语法。
     */
    public static String phrase(String query) {
        if (query == null) return "";
        String s = query.trim();
        if (s.isEmpty()) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** 是否值得走 FTS（trigram 至少要 3 个码点；空/短 query 走 LIKE） */
    public static boolean eligible(String query) {
        if (query == null) return false;
        String s = query.trim();
        return !s.isEmpty() && s.codePointCount(0, s.length()) >= MIN_TRIGRAM_CHARS;
    }

    /** FTS5 检索：MATCH + BM25 相关度排序（bm25 越小越相关），tie-break 时间倒序 */
    public static String searchSql(int limit) {
        return "SELECT b.*, bm25(" + TABLE + ") AS search_score FROM blocks b JOIN "
                + TABLE + " ON b.id = " + TABLE + ".rowid WHERE " + TABLE + " MATCH ? "
                + "ORDER BY bm25(" + TABLE + "), b.t_start DESC LIMIT " + Math.max(1, limit);
    }

    /** FTS5 检索 + 出网档位过滤（AND 组合） */
    public static String searchSqlWithTier(int limit) {
        return "SELECT b.*, bm25(" + TABLE + ") AS search_score FROM blocks b JOIN "
                + TABLE + " ON b.id = " + TABLE + ".rowid WHERE " + TABLE + " MATCH ? "
                + "AND b." + MemorySchema.COL_EGRESS_TIER + "=? "
                + "ORDER BY bm25(" + TABLE + "), b.t_start DESC LIMIT " + Math.max(1, limit);
    }
}
