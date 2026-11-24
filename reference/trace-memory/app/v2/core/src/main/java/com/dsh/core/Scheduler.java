package com.dsh.core;

/**
 * 周期调度抽象。核心库保持纯 JVM：Android 端用 Handler/Looper 实现，
 * 测试/桌面用 {@link ThreadScheduler} 或 {@link NoopScheduler}。
 */
public interface Scheduler {

    /**
     * 每 periodMs 跑一次 task。返回的句柄 close() 后不再触发。
     * 实现必须保证 task 抛异常不会杀死调度循环（由调用方/实现兜住）。
     */
    AutoCloseable schedule(String name, long periodMs, Runnable task);

    /** 什么都不做（纯逻辑测试/无调度环境） */
    final class NoopScheduler implements Scheduler {
        @Override public AutoCloseable schedule(String name, long periodMs, Runnable task) {
            return () -> { };
        }
    }

    /** 简单 JVM 守护线程调度（桌面/测试用；Android 端应注入 Handler 实现） */
    final class ThreadScheduler implements Scheduler {
        @Override public AutoCloseable schedule(String name, long periodMs, Runnable task) {
            final boolean[] stopped = {false};
            Thread t = new Thread(() -> {
                while (!stopped[0]) {
                    try {
                        Thread.sleep(Math.max(1, periodMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (stopped[0]) return;
                    try {
                        task.run();
                    } catch (Throwable ignored) {
                        // 单次失败不能杀死循环
                    }
                }
            }, "dsh-sched-" + name);
            t.setDaemon(true);
            t.start();
            return () -> stopped[0] = true;
        }
    }
}
