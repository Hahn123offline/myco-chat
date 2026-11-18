package com.dsh.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 单一所有权：模块注册的所有可撤销资源都放这里，关闭时倒序执行。
 * 这是"插件卸载后彻底消失"的机制保证，不靠模块自觉。
 */
public final class Scope implements AutoCloseable {

    private final Deque<AutoCloseable> items = new ArrayDeque<>();
    private boolean closed = false;
    private final String name;

    public Scope(String name) { this.name = name; }

    public synchronized AutoCloseable add(Runnable onDispose) {
        final boolean[] done = {false};
        AutoCloseable handle = () -> {
            if (done[0]) return;
            done[0] = true;
            if (onDispose != null) onDispose.run();
        };
        if (closed) {
            // 已关闭还往里加：立刻撤销，避免泄漏
            try { handle.close(); } catch (Exception ignored) { }
        } else {
            items.addLast(handle);
        }
        return handle;
    }

    @Override
    public synchronized void close() {
        closed = true;
        while (!items.isEmpty()) {
            AutoCloseable c = items.removeLast();   // 倒序
            try { c.close(); } catch (Throwable ignored) { }
        }
    }

    public synchronized int size() { return items.size(); }
    public String name() { return name; }
}
