package com.dsh.contract;

/**
 * 检索请求与结果。结果里必须带 {@link Freshness}——
 * 记忆有滞后时，调用方要能说"我这边还没处理到 X 点"，而不是把"没查到"说成"没有"。
 */
public final class Recall {

    private Recall() { }

    /** 检索请求。全部字段可空；组合语义由实现决定，但必须在 Coverage 里声明实际范围。 */
    public static final class Request {
        /** 自然语言或关键词。空串表示按 object/时间浏览。 */
        public final String query;
        /** 对象过滤（如群名）。null = 不过滤。 */
        public final String object;
        /** 时间范围，毫秒；0 表示不限制。 */
        public final long from;
        public final long to;
        /** 包名过滤，null = 不过滤。 */
        public final String pkg;
        /** 返回条数上限，&lt;=0 用实现默认值。 */
        public final int limit;
        /**
         * agent 是否在场（observing 的 visit 过滤）。
         * null = 不按在场性过滤；TRUE/FALSE 时只返回对应在场性的观察。
         */
        public final Boolean agentPresent;

        public Request(String query, String object, long from, long to,
                       String pkg, int limit, Boolean agentPresent) {
            this.query = query == null ? "" : query;
            this.object = object;
            this.from = from;
            this.to = to;
            this.pkg = pkg;
            this.limit = limit;
            this.agentPresent = agentPresent;
        }

        public static Request of(String query) {
            return new Request(query, null, 0L, 0L, null, 0, null);
        }
    }

    /** 检索结果。 */
    public static final class Result {
        /**
         * 人可读结论。**空串表示"没找到"，不等于"没有发生"**——
         * 调用方必须结合 {@link #coverage} 与 {@link #freshness} 决定措辞。
         */
        public final String answer;
        /** 命中的 line / object_message id，供下钻 readBlock/readFrame。 */
        public final long[] ids;
        /** 本次结果覆盖了什么、空档在哪。 */
        public final Coverage coverage;
        /** 数据新鲜度。非空。 */
        public final Freshness freshness;

        public Result(String answer, long[] ids, Coverage coverage, Freshness freshness) {
            this.answer = answer == null ? "" : answer;
            this.ids = ids == null ? new long[0] : ids;
            this.coverage = coverage == null ? Coverage.unknown() : coverage;
            this.freshness = freshness == null ? Freshness.unknown() : freshness;
        }
    }

    /** 覆盖声明：让调用方能判断"没查到"是否可信。 */
    public static final class Coverage {
        /** 实际查询的时间范围（可能由实现补全，例如按 query 里的"昨天"）。 */
        public final long scopeFrom;
        public final long scopeTo;
        /** 有记录的毫秒数。 */
        public final long coveredMs;
        /** 范围内的空档（起止毫秒对），可为空。 */
        public final long[][] gaps;
        /** 有内容但因 limit 被截断的条数；0 = 没截断。 */
        public final int truncated;
        /** 本次展示的是哪一段（人可读，例如"最近 60 条"）。 */
        public final String shownRange;

        public Coverage(long scopeFrom, long scopeTo, long coveredMs,
                        long[][] gaps, int truncated, String shownRange) {
            this.scopeFrom = scopeFrom;
            this.scopeTo = scopeTo;
            this.coveredMs = coveredMs;
            this.gaps = gaps == null ? new long[0][] : gaps;
            this.truncated = truncated;
            this.shownRange = shownRange == null ? "" : shownRange;
        }

        public static Coverage unknown() {
            return new Coverage(0L, 0L, 0L, null, 0, "");
        }
    }
}
