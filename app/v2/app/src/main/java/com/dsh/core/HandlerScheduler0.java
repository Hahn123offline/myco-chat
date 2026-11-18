package com.dsh.core;

import android.os.Handler;
import android.os.Looper;

/**
 * Android 端的 Scheduler：只在主线程做"轻任务"（tick/通知刷新）。
 * **重活（OCR/截图/落盘）不许直接放这里**，模块自己丢线程池。
 */
public final class HandlerScheduler0 implements Scheduler {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public AutoCloseable schedule(String name, long periodMs, Runnable task) {
        final boolean[] stopped = {false};
        final Runnable[] self = new Runnable[1];
        self[0] = () -> {
            if (stopped[0]) return;
            try {
                task.run();
            } catch (Throwable t) {
                android.util.Log.e("DSHHost", "scheduled " + name + " threw", t);
            }
            if (!stopped[0]) handler.postDelayed(self[0], Math.max(1, periodMs));
        };
        handler.postDelayed(self[0], Math.max(1, periodMs));
        return () -> {
            stopped[0] = true;
            handler.removeCallbacks(self[0]);
        };
    }
}
