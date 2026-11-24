package com.dsh.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * memory.db 的 schema 常量与升级 SQL 生成器。
 *
 * 为什么单独抽出来：`android.database.sqlite.*` 在 JVM 单测里是 stub（不能真建库），
 * 但"版本升级该执行哪些 SQL"是纯逻辑，可以 100% 单测。这样迁移至少有护栏。
 *
 * 版本历史：
 *  v1 初始（id/episode/t/pkg/title/masked/hit/schema）
 *  v2 + evidence_ref（不透明证据引用）
 *  v3 + redaction_level（0=RULE 1=MODEL，默认 0）+ redacted_at
 *  v4 + egress_tier（0=UNKNOWN 1=WHITE 2=GRAY 3=BLACK，默认 0=UNKNOWN）
 *  v5 + FTS5 检索索引（不是列：虚表 blocks_fts + 触发器，由 MemoryFtsInstaller 安装；
 *       老 SQLite 不支持 trigram 时自动回退 LIKE，DB_VER 仍然 +1）
 *  v6 + 云端打标：label / label_model / labeled_at / label_status（默认 0=PENDING）
 */
public final class MemorySchema {

    public static final int DB_VER = 6;
    public static final String TABLE = "blocks";

    public static final String COL_EVIDENCE_REF = "evidence_ref";
    public static final String COL_REDACTION_LEVEL = "redaction_level";
    public static final String COL_REDACTED_AT = "redacted_at";
    public static final String COL_EGRESS_TIER = "egress_tier";
    public static final String COL_LABEL = "label";
    public static final String COL_LABEL_MODEL = "label_model";
    public static final String COL_LABELED_AT = "labeled_at";
    public static final String COL_LABEL_STATUS = "label_status";

    /** 全新建库语句（与 upgradeSql 的最终形态必须一致：有单测断言列齐全） */
    public static final String CREATE_BLOCKS =
            "CREATE TABLE blocks(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "episode_id INTEGER, t_start INTEGER, t_end INTEGER," +
                    "pkg TEXT, title TEXT, masked_text TEXT," +
                    "privacy_tier TEXT, hit_summary TEXT, schema_ver INTEGER," +
                    COL_EVIDENCE_REF + " TEXT," +
                    COL_REDACTION_LEVEL + " INTEGER NOT NULL DEFAULT 0," +
                    COL_REDACTED_AT + " INTEGER," +
                    COL_EGRESS_TIER + " INTEGER NOT NULL DEFAULT 0," +
                    COL_LABEL + " TEXT," +
                    COL_LABEL_MODEL + " TEXT," +
                    COL_LABELED_AT + " INTEGER," +
                    COL_LABEL_STATUS + " INTEGER NOT NULL DEFAULT 0)";

    public static final List<String> CREATE_INDEXES = Collections.unmodifiableList(Arrays.asList(
            "CREATE INDEX idx_mt ON blocks(t_start)",
            "CREATE INDEX idx_mp ON blocks(pkg, t_start)"));

    public static final String ALTER_ADD_EVIDENCE_REF =
            "ALTER TABLE blocks ADD COLUMN " + COL_EVIDENCE_REF + " TEXT";

    public static final String ALTER_ADD_REDACTION_LEVEL =
            "ALTER TABLE blocks ADD COLUMN " + COL_REDACTION_LEVEL + " INTEGER NOT NULL DEFAULT 0";

    public static final String ALTER_ADD_REDACTED_AT =
            "ALTER TABLE blocks ADD COLUMN " + COL_REDACTED_AT + " INTEGER";

    public static final String ALTER_ADD_EGRESS_TIER =
            "ALTER TABLE blocks ADD COLUMN " + COL_EGRESS_TIER + " INTEGER NOT NULL DEFAULT 0";

    public static final String ALTER_ADD_LABEL =
            "ALTER TABLE blocks ADD COLUMN " + COL_LABEL + " TEXT";

    public static final String ALTER_ADD_LABEL_MODEL =
            "ALTER TABLE blocks ADD COLUMN " + COL_LABEL_MODEL + " TEXT";

    public static final String ALTER_ADD_LABELED_AT =
            "ALTER TABLE blocks ADD COLUMN " + COL_LABELED_AT + " INTEGER";

    public static final String ALTER_ADD_LABEL_STATUS =
            "ALTER TABLE blocks ADD COLUMN " + COL_LABEL_STATUS + " INTEGER NOT NULL DEFAULT 0";

    /**
     * 兜底列清单：onOpen 时用 PRAGMA table_info 逐个检查，缺哪列补哪列。
     * 顺序固定（LinkedHashMap），便于测试与日志可复现。
     */
    public static final Map<String, String> COLUMN_DDL;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(COL_EVIDENCE_REF, ALTER_ADD_EVIDENCE_REF);
        m.put(COL_REDACTION_LEVEL, ALTER_ADD_REDACTION_LEVEL);
        m.put(COL_REDACTED_AT, ALTER_ADD_REDACTED_AT);
        m.put(COL_EGRESS_TIER, ALTER_ADD_EGRESS_TIER);
        m.put(COL_LABEL, ALTER_ADD_LABEL);
        m.put(COL_LABEL_MODEL, ALTER_ADD_LABEL_MODEL);
        m.put(COL_LABELED_AT, ALTER_ADD_LABELED_AT);
        m.put(COL_LABEL_STATUS, ALTER_ADD_LABEL_STATUS);
        COLUMN_DDL = Collections.unmodifiableMap(m);
    }

    private MemorySchema() { }

    /**
     * 从旧版本升到 {@link #DB_VER} 需要执行的语句（纯函数，按顺序执行）。
     * 目标是幂等：重复执行会失败但被调用方吞掉，不会坏库。
     */
    public static List<String> upgradeSql(int oldV) {
        List<String> sql = new ArrayList<>();
        if (oldV < 2) sql.add(ALTER_ADD_EVIDENCE_REF);
        if (oldV < 3) {
            sql.add(ALTER_ADD_REDACTION_LEVEL);
            sql.add(ALTER_ADD_REDACTED_AT);
        }
        if (oldV < 4) sql.add(ALTER_ADD_EGRESS_TIER);
        if (oldV < 6) {
            sql.add(ALTER_ADD_LABEL);
            sql.add(ALTER_ADD_LABEL_MODEL);
            sql.add(ALTER_ADD_LABELED_AT);
            sql.add(ALTER_ADD_LABEL_STATUS);
        }
        return sql;
    }
}
