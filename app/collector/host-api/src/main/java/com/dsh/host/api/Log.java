package com.dsh.host.api;

/** 最小日志接口。宿主统一收口，便于导出与状态页展示。 */
public interface Log {
    void i(String tag, String msg);
    void w(String tag, String msg);
    void e(String tag, String msg, Throwable t);
}
