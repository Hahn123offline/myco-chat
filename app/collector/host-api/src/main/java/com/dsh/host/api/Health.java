package com.dsh.host.api;

/** 模块健康状态。FAILED 必须带原因。 */
public final class Health {
    public enum State { OK, DEGRADED, FAILED, STOPPED }

    public final State state;
    public final String detail;

    private Health(State s, String d) {
        this.state = s;
        this.detail = d == null ? "" : d;
    }

    public static Health ok() { return new Health(State.OK, ""); }

    /** OK 也允许带 detail（例如 a11y=ACTIVE lastEventAgeMs=… episodesToday=…），不改变 isHealthy */
    public static Health ok(String detail) { return new Health(State.OK, detail); }

    public static Health degraded(String why) { return new Health(State.DEGRADED, why); }
    public static Health failed(String why) { return new Health(State.FAILED, why); }
    public static Health stopped() { return new Health(State.STOPPED, ""); }

    public boolean isHealthy() { return state == State.OK; }

    @Override
    public String toString() { return state + (detail.isEmpty() ? "" : "(" + detail + ")"); }
}
