package com.dsh.host.core;

import com.dsh.host.api.Event;
import com.dsh.host.api.EventBus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 强类型异步总线：订阅者抛异常不影响其他订阅者。 */
public final class SimpleEventBus implements EventBus {

    private final Map<Class<?>, List<Handler<?>>> subs = new ConcurrentHashMap<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "host-bus");
        t.setDaemon(true);
        return t;
    });
    private volatile Listener onError;

    public interface Listener { void onError(Throwable t, Event e); }

    public void setOnError(Listener l) { this.onError = l; }

    @Override
    public <T extends Event> void subscribe(Class<T> type, Handler<T> handler) {
        subs.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void unsubscribe(Handler<?> handler) {
        for (List<Handler<?>> list : subs.values()) list.remove(handler);
    }

    @Override
    public void publish(Event event) {
        exec.execute(() -> {
            List<Handler<?>> list = subs.get(event.getClass());
            if (list == null) return;
            for (Handler<?> h : list) {
                try {
                    ((Handler<Event>) h).on(event);
                } catch (Throwable t) {
                    if (onError != null) onError.onError(t, event);
                }
            }
        });
    }
}
