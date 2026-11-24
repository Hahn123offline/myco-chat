package com.dsh.usagetrace;

/**
 * 证据引用的**生产者**。token 形如 "trace:&lt;episodeId&gt;"，含义只在本模块内定义。
 *
 * 为什么不是路径：下游（脱敏 → 记忆）只应知道"有一段可追溯的证据"，
 * 不该知道截图/标题条落在哪个目录、命名是什么。要换存储布局时，
 * 只改本类与 EpisodeStore，下游代码不用动。
 *
 * 约束（写在这里当契约）：
 *  - 本 token 是不可解析的不透明字符串；下游可以存、可以原样传回，
 *    但不能按 ':' 切开去猜类型，更不能当文件路径。
 *  - 没有真正落库的 episode（id &lt;= 0）不给引用，返回 null —— 不编造。
 */
public final class EvidenceRef {

    public static final String SCHEME = "trace:";

    private EvidenceRef() { }

    /** @return "trace:&lt;id&gt;"；id &lt;= 0（尚未落库）时返回 null */
    public static String forEpisode(long episodeId) {
        if (episodeId <= 0) return null;
        return SCHEME + episodeId;
    }
}
