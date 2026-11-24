package com.dsh.host.api.events;

import com.dsh.host.api.Event;
import com.dsh.host.api.data.Episode;

/** 一段注意轨迹结束并落库后发出。下游（脱敏/记忆）只订阅这个，不碰数据库。 */
public final class EpisodeClosed extends Event {
    public final Episode episode;

    /**
     * 证据引用：不透明 token，由生产者（usagetrace）生成，形如 "trace:&lt;episodeId&gt;"。
     *
     * 硬约定：其它模块只允许把它当字符串原样存 / 原样转发，**不得解析**，
     * 也不能从中推断文件系统路径或目录结构。这样记忆层就不再知道 usagetrace 的存储布局。
     * 没有可引用证据时为 null。
     */
    public final String evidenceRef;

    public EpisodeClosed(Episode e) { this(e, null); }

    public EpisodeClosed(Episode e, String evidenceRef) {
        this.episode = e;
        this.evidenceRef = evidenceRef;
    }
}
