package com.dsh.host.api;

/** 强类型、异步分发的事件总线。订阅者抛异常不得影响其他订阅者。 */
public interface EventBus {

    <T extends Event> void subscribe(Class<T> type, Handler<T> handler);

    void unsubscribe(Handler<?> handler);

    void publish(Event event);

    interface Handler<T extends Event> {
        void on(T event);
    }
}
