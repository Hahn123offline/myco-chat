package com.dsh.memory;

/**
 * 检索/打标 SQL 的纯逻辑部分（可在 JVM 单测里断言；真库行为由 verify_pipeline.md 在设备上验）。
 *
 * 检索约定（对应 MemoryQuery.search 的契约）：
 *  - 匹配字段：title / masked_text / label（label 也有匹配，但排序权重低，见 LikeRanker）；
 *  - 中文子串：LIKE 对 UTF-8 文本按子串匹配；
 *  - 英文大小写不敏感：SQLite 的 LIKE 对 ASCII 默认不敏感，这里额外 LOWER() 明确表达；
 *  - 时间倒序：ORDER BY t_start DESC；
 *  - 用户输入的 `%` / `_` 按字面量处理：先转义，再 `ESCAPE '\'`。
 */
public final class MemorySql {

    /** 用户输入里的 LIKE 元字符按字面量处理 */
    public static final char LIKE_ESCAPE = '\\';

    public static final String SEARCH_WHERE =
            "(LOWER(masked_text) LIKE LOWER(?) ESCAPE '\\'"
                    + " OR LOWER(title) LIKE LOWER(?) ESCAPE '\\'"
                    + " OR LOWER(COALESCE(" + MemorySchema.COL_LABEL + ",'')) LIKE LOWER(?) ESCAPE '\\')";

    public static final String TIME_DESC = " ORDER BY t_start DESC LIMIT ";

    public static final String BY_TIER_WHERE = "WHERE " + MemorySchema.COL_EGRESS_TIER + "=?";

    /**
     * 待云端打标：**SQL 层就写死 WHITE**（隐私不变量），且 label 为空、未被 SKIPPED。
     * 从旧到新处理（先清历史积压）。
     */
    public static final String NEEDS_LABEL_WHERE =
            "WHERE " + MemorySchema.COL_EGRESS_TIER + "=?"
                    + " AND " + MemorySchema.COL_LABEL + " IS NULL"
                    + " AND " + MemorySchema.COL_LABEL_STATUS + "<>" + com.dsh.host.api.data.LabelStatus.SKIPPED;

    private MemorySql() { }

    public static String searchSql(int limit) {
        return "SELECT * FROM " + MemorySchema.TABLE + " WHERE " + SEARCH_WHERE
                + TIME_DESC + Math.max(1, limit);
    }

    public static String searchSqlWithTier(int limit) {
        return "SELECT * FROM " + MemorySchema.TABLE + " WHERE " + SEARCH_WHERE
                + " AND " + MemorySchema.COL_EGRESS_TIER + "=?"
                + TIME_DESC + Math.max(1, limit);
    }

    public static String byEgressTierSql(int limit) {
        return "SELECT * FROM " + MemorySchema.TABLE + " " + BY_TIER_WHERE
                + TIME_DESC + Math.max(1, limit);
    }

    public static String countByEgressTierSql() {
        return "SELECT COUNT(*) FROM " + MemorySchema.TABLE + " " + BY_TIER_WHERE;
    }

    public static String blocksNeedingLabelSql(int limit) {
        return "SELECT * FROM " + MemorySchema.TABLE + " " + NEEDS_LABEL_WHERE
                + " ORDER BY t_start ASC LIMIT " + Math.max(1, limit);
    }

    public static String countByLabelStatusSql() {
        return "SELECT COUNT(*) FROM " + MemorySchema.TABLE
                + " WHERE " + MemorySchema.COL_LABEL_STATUS + "=?";
    }

    /** 拼 LIKE 模式；会转义 `\` `%` `_`，并在调用处配 ESCAPE '\' */
    public static String searchPattern(String text) {
        if (text == null) return "%";
        return "%" + escapeLike(text.trim()) + "%";
    }

    /** search 的匹配字段数（title / masked_text / label），SQL 里 3 个 LIKE 占位符 */
    public static final int SEARCH_PATTERN_COUNT = 3;

    public static String escapeLike(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == LIKE_ESCAPE || c == '%' || c == '_') b.append(LIKE_ESCAPE);
            b.append(c);
        }
        return b.toString();
    }
}
