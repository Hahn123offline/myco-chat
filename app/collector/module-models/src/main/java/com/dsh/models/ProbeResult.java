package com.dsh.models;

/**
 * 一次可达性探测的结果。**失败必须带可诊断原因**：
 * 真机排障时只打一个 baseUrl 等于无法定位（曾经因此漏掉
 * NetworkOnMainThreadException：主线程联网被系统禁止，异常被吞成 DOWN）。
 *
 * kind 分类（稳定、适合 grep）：
 *  - ok
 *  - main-thread-network ：NetworkOnMainThreadException（探测被错误地放在主线程）
 *  - connect-refused     ：ConnectException（服务没起/端口不通）
 *  - timeout             ：SocketTimeoutException
 *  - unknown-host        ：UnknownHostException
 *  - http-<code>         ：HTTP 非 2xx（带状态码）
 *  - exception-<Name>    ：其它异常
 *
 * detail：人读细节，异常时是 "类名: message"，HTTP 时是状态码 + body 片段。
 */
public final class ProbeResult {

    public static final String OK = "ok";

    public final boolean up;
    public final String kind;
    public final String detail;
    public final int httpStatus;

    private ProbeResult(boolean up, String kind, String detail, int httpStatus) {
        this.up = up;
        this.kind = kind == null || kind.isEmpty() ? (up ? OK : "unknown") : kind;
        this.detail = detail == null ? "" : detail;
        this.httpStatus = httpStatus;
    }

    public static ProbeResult up() {
        return new ProbeResult(true, OK, "", -1);
    }

    public static ProbeResult down(String kind, String detail) {
        return new ProbeResult(false, kind, detail, -1);
    }

    public static ProbeResult down(String kind, String detail, int httpStatus) {
        return new ProbeResult(false, kind, detail, httpStatus);
    }

    /** 把异常归类；detail 一定带异常类名 + message */
    public static ProbeResult of(Throwable t) {
        if (t == null) return down("exception-null", "unknown");
        String cls = t.getClass().getSimpleName();
        String msg = t.getMessage();
        String desc = cls + (msg == null || msg.isEmpty() ? "" : ": " + msg);
        String simple = cls.toLowerCase(java.util.Locale.ROOT);
        if (simple.contains("networkonmainthread")) return down("main-thread-network", desc);
        if (simple.contains("sockettimeout")) return down("timeout", desc);
        if (simple.contains("connectexception") || simple.contains("connect")) {
            return down("connect-refused", desc);
        }
        if (simple.contains("unknownhost")) return down("unknown-host", desc);
        return down("exception-" + cls, desc);
    }

    /** 给日志一行用：kind + detail，保证异常类名不会丢 */
    public String forLog() {
        if (up) return "ok";
        return "kind=" + kind + " err=" + (detail.isEmpty() ? "(no detail)" : detail);
    }

    @Override public String toString() { return forLog(); }
}
