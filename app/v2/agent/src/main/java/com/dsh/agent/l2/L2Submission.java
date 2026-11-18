package com.dsh.agent.l2;

import java.util.List;

/** L2 会话的产出（submit_description 的参数）。 */
public final class L2Submission {

    public final String title;
    public final String body;
    public final List<Long> keyBlockIds;
    /** partial 链是否真的写了缺口声明（模型没写时程序接受但标记 false）。 */
    public final boolean gapDeclared;
    /** resolved / in_progress / no_action / unknown；不进正文 */
    public final String outcome;

    public L2Submission(String title, String body, List<Long> keyBlockIds) {
        this(title, body, keyBlockIds, true, "unknown");
    }

    public L2Submission(String title, String body, List<Long> keyBlockIds, boolean gapDeclared) {
        this(title, body, keyBlockIds, gapDeclared, "unknown");
    }

    public L2Submission(String title, String body, List<Long> keyBlockIds, boolean gapDeclared,
                        String outcome) {
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.keyBlockIds = keyBlockIds == null
                ? java.util.Collections.emptyList() : new java.util.ArrayList<>(keyBlockIds);
        this.gapDeclared = gapDeclared;
        this.outcome = outcome == null || outcome.isEmpty() ? "unknown" : outcome;
    }
}
