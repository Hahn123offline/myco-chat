package com.dsh.usagetrace;

import com.dsh.host.api.data.Episode;
import com.dsh.host.api.events.EpisodeClosed;

/**
 * 段落状态机：当前的"这次注意"什么时候开、什么时候关。
 * 纯逻辑，持久化与事件发布通过回调注入，便于单测。
 */
public final class EpisodeTracker {

    public interface Sink {
        void onOpen(Episode e);
        void onClose(Episode e);
        void onEvent(EpisodeClosed ev);
    }

    private final Sink sink;
    private final long minEpisodeMs;

    private Episode current;
    private long lastSeenAt = 0;

    public EpisodeTracker(Sink sink, long minEpisodeMs) {
        this.sink = sink;
        this.minEpisodeMs = minEpisodeMs;
    }

    /** 收到一次画面样本 */
    public synchronized Episode onSample(Scene scene, long now,
                                         String framePath, String titlebarPath,
                                         String textSnippet, String rule) {
        lastSeenAt = now;

        if (current != null) {
            Scene prev = new Scene(current.pkg, current.activity, current.title,
                    current.titleSrc, current.sceneKey, current.sceneConf);
            if (SceneResolver.sameScene(prev, scene)) {
                current.framesCount++;
                // 证据升级：先前没标题、现在有了 → 补上
                if (current.title.isEmpty() && !scene.title.isEmpty()) {
                    current.title = scene.title;
                    current.titleSrc = scene.titleSrc;
                }
                if (current.sceneConf < scene.conf) current.sceneConf = scene.conf;
                if (current.framePath == null && framePath != null) current.framePath = framePath;
                if (current.titlebarPath == null && titlebarPath != null) current.titlebarPath = titlebarPath;
                if (textSnippet != null && textSnippet.length() > current.textSnippet.length()) {
                    current.textSnippet = textSnippet;
                }
                return current;
            }
            close(now);
        }

        Episode e = new Episode();
        e.tStart = now;
        e.tEnd = now;
        e.dwellMs = 0;
        e.pkg = scene.pkg;
        e.activity = scene.activity;
        e.title = scene.title;
        e.titleSrc = scene.titleSrc;
        e.sceneKey = scene.key;
        e.sceneConf = scene.conf;
        e.titleConf = "ocr".equals(scene.titleSrc) ? 0.7f : ("node".equals(scene.titleSrc) ? 0.95f : 0.2f);
        e.framePath = framePath;
        e.titlebarPath = titlebarPath;
        e.textSnippet = textSnippet == null ? "" : textSnippet;
        e.framesCount = 1;
        e.mode = "event";
        e.rule = rule == null ? "default" : rule;
        current = e;
        sink.onOpen(e);
        return e;
    }

    /** 安静太久就收尾 */
    public synchronized void idleCheck(long now, long idleMs) {
        if (current != null && now - lastSeenAt >= idleMs) close(now);
    }

    public synchronized boolean close(long now) {
        if (current == null) return false;
        Episode e = current;
        current = null;
        if (now - e.tStart < minEpisodeMs) {
            // 太短：不落库，但把证据并回"下一条"没意义，直接丢弃并记账
            return false;
        }
        e.tEnd = now;
        e.dwellMs = now - e.tStart;
        sink.onClose(e);
        // 证据引用由生产者（本模块）给出；下游只当不透明 token 存与传。
        sink.onEvent(new EpisodeClosed(e, EvidenceRef.forEpisode(e.id)));
        return true;
    }

    public synchronized Episode current() { return current; }
}
