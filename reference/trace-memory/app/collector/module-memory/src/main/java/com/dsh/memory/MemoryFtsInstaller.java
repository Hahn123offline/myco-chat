package com.dsh.memory;

/**
 * FTS5 索引安装/降级判定。**纯逻辑**，不依赖 android.database，可在 JVM 单测里
 * 用假 {@link Db} 模拟"建表失败"的降级路径。
 *
 * 平台事实（task-7 真机验收）：AOSP 的 SQLite 默认不带 FTS5，只带 FTS3/FTS4；
 * trigram tokenizer 还要 SQLite 3.34+。所以 Android 上大概率直接降级 LIKE。
 * 降级必须**把原因暴露出来**（searchEngine=LIKE + searchEngineReason），不能静默。
 *
 * 安装是幂等的，并处理半迁移状态：
 *  - 表不存在 → 建表 + 三触发器 + rebuild 回填；
 *  - 表存在但触发器缺失（上次安装中途失败）→ 补触发器 + rebuild 修复；
 *  - 任何一步失败 → **清理半成品**，回退 LIKE，并记录失败原因。
 */
public final class MemoryFtsInstaller {

    public static final String ENGINE_FTS5 = "FTS5";
    public static final String ENGINE_LIKE = "LIKE";

    /** LIKE 降级的常见原因（写进 searchEngineReason / 状态页 / 日志） */
    public static final String REASON_OK = "ok";
    public static final String REASON_FTS5_UNAVAILABLE = "fts5-unavailable";
    public static final String REASON_NO_TRIGRAM = "no-trigram";

    /** 安装结果：引擎 + 原因。引擎是 FTS5 时 reason 恒为 "ok"。 */
    public static final class Result {
        public final String engine;
        public final String reason;

        public Result(String engine, String reason) {
            this.engine = ENGINE_FTS5.equals(engine) ? ENGINE_FTS5 : ENGINE_LIKE;
            this.reason = reason == null || reason.isEmpty() ? REASON_OK : reason;
        }

        public boolean isFts5() { return ENGINE_FTS5.equals(engine); }

        @Override public String toString() { return engine + "(" + reason + ")"; }
    }

    /** 最小的 DB 抽象，便于单测注入失败环境 */
    public interface Db {
        boolean tableExists(String table);

        boolean triggerExists(String trigger);

        void execSQL(String sql) throws Exception;
    }

    private MemoryFtsInstaller() { }

    public static Result install(Db db) {
        if (db == null) return new Result(ENGINE_LIKE, "sql-error:null-db");
        try {
            boolean fresh = !db.tableExists(MemoryFts.TABLE);
            boolean missingTriggers = false;
            if (!fresh) {
                for (String name : MemoryFts.TRIGGER_NAMES) {
                    if (!db.triggerExists(name)) { missingTriggers = true; break; }
                }
            }
            if (fresh) {
                db.execSQL(MemoryFts.CREATE);
            }
            for (String trigger : MemoryFts.TRIGGERS) db.execSQL(trigger);
            if (fresh || missingTriggers) {
                // 新建库要回填；半迁移（表在、触发器缺）也要回填，否则旧行检索不到
                db.execSQL(MemoryFts.REBUILD);
            }
            return new Result(ENGINE_FTS5, REASON_OK);
        } catch (Throwable t) {
            String reason = reasonFor(t);
            cleanup(db);
            return new Result(ENGINE_LIKE, reason);
        }
    }

    /** 把底层异常归类成稳定、可读的 reason（不要只写一段 SQL 错误） */
    public static String reasonFor(Throwable t) {
        if (t == null) return "sql-error:unknown";
        String m = t.getMessage() == null ? "" : t.getMessage();
        String lower = m.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("tokenizer") || lower.contains("trigram")) return REASON_NO_TRIGRAM;
        if (lower.contains("no such module") || lower.contains("fts5")) return REASON_FTS5_UNAVAILABLE;
        String one = m.replace('\n', ' ').replace('\r', ' ').trim();
        if (one.length() > 160) one = one.substring(0, 160) + "…";
        return "sql-error:" + (one.isEmpty() ? t.getClass().getSimpleName() : one);
    }

    /** 清理半成品：先删触发器（否则残留触发器引用不存在的虚表会让 blocks 写入报错），再删虚表 */
    private static void cleanup(Db db) {
        for (String name : MemoryFts.TRIGGER_NAMES) {
            try { db.execSQL("DROP TRIGGER IF EXISTS " + name); } catch (Throwable ignored) { }
        }
        try { db.execSQL("DROP TABLE IF EXISTS " + MemoryFts.TABLE); } catch (Throwable ignored) { }
    }

    /** 给日志/状态页用的降级原因文案 */
    public static String degradedDetail(String engine) {
        return degradedDetail(engine, REASON_OK);
    }

    public static String degradedDetail(String engine, String reason) {
        String r = reason == null || reason.isEmpty() ? REASON_OK : reason;
        if (ENGINE_FTS5.equals(engine)) return "searchEngine=FTS5 reason=" + r;
        return "searchEngine=LIKE(" + r + ")：系统 SQLite 不支持 FTS5 trigram，已自动回退；"
                + "用 LIKE+LikeRanker 打分排序，检索质量低于 BM25";
    }
}
