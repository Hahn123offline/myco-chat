package com.dsh.host.api;

/** 所有事件的基类。强类型，不用字符串键。 */
public abstract class Event {
    public final long ts = System.currentTimeMillis();
    /** 生产者模块 id，由宿主填充 */
    public String source = "";
}
