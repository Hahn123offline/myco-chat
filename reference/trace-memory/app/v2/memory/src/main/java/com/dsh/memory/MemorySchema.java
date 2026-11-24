package com.dsh.memory;

/** 唯一一份建表/索引 SQL，Android 与桌面 store 共用。 */
public final class MemorySchema {
    public static final String TABLE = "mem_items";
    public static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS mem_items(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "namespace TEXT NOT NULL, kind TEXT NOT NULL, key TEXT," +
                    "text TEXT, data TEXT," +
                    "detail_level TEXT NOT NULL DEFAULT 'full'," +
                    "weight REAL NOT NULL DEFAULT 1.0," +
                    "recall_count INTEGER NOT NULL DEFAULT 0," +
                    "last_recalled_at INTEGER NOT NULL DEFAULT 0," +
                    "t0 INTEGER NOT NULL DEFAULT 0, t1 INTEGER NOT NULL DEFAULT 0," +
                    "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL," +
                    "evidence TEXT, superseded INTEGER NOT NULL DEFAULT 0)";
    public static final String CREATE_UNIQUE_KEY =
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_mem_ns_key ON mem_items(namespace,key)" +
                    " WHERE key IS NOT NULL";
    public static final String CREATE_INDEX_NS =
            "CREATE INDEX IF NOT EXISTS idx_mem_ns_t0 ON mem_items(namespace,t0)";
    public static final String ALTER_DETAIL =
            "ALTER TABLE mem_items ADD COLUMN detail_level TEXT NOT NULL DEFAULT 'full'";

    private MemorySchema() { }
}
