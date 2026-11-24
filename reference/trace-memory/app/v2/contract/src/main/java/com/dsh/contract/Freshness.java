package com.dsh.contract;

/**
 * 记忆新鲜度。
 *
 * 存在的理由是一个已经发生过的真实故障：object_messages 落后 18 小时，
 * 系统对一条确实存在的消息回答了"没看到"。分仓之后同样的错误会被
 * 对方写进对话记忆、被二次固化，所以滞后必须让调用方看得见。
 */
public final class Freshness {

    /** 滞后多久算"追不上"，默认 10 分钟。 */
    public static final long CATCHING_UP_MS = 10 * 60_000L;
    /** 采集/投影停摆：超过这么久没有任何新数据，判定 STALLED。 */
    public static final long STALLED_MS = 30 * 60_000L;

    public enum State {
        /** 数据基本追平，可当"最新"使用。 */
        OK,
        /** 正在追赶，最近的数据还没进库。 */
        CATCHING_UP,
        /** 采集或投影已经停了（无障碍被关、进程被杀），这段时间没有记忆。 */
        STALLED
    }

    /** 数据已处理到哪个时间点；0 = 从未处理过。 */
    public final long projectedUntil;
    /** now - projectedUntil，毫秒；{@link #projectedUntil} 为 0 时为 -1。 */
    public final long lagMs;
    public final State state;

    public Freshness(long projectedUntil, long lagMs, State state) {
        this.projectedUntil = projectedUntil;
        this.lagMs = lagMs;
        this.state = state == null ? State.STALLED : state;
    }

    public static Freshness unknown() {
        return new Freshness(0L, -1L, State.STALLED);
    }

    /** 由"已处理到哪"和"现在"直接推导，不需要额外状态，不会与数据不一致。 */
    public static Freshness of(long projectedUntil, long now) {
        if (projectedUntil <= 0L) return unknown();
        long lag = Math.max(0L, now - projectedUntil);
        State s;
        if (lag >= STALLED_MS) s = State.STALLED;
        else if (lag >= CATCHING_UP_MS) s = State.CATCHING_UP;
        else s = State.OK;
        return new Freshness(projectedUntil, lag, s);
    }

    /** 调用方（尤其 agent）在措辞前应问这一句。 */
    public boolean trustworthyForRecent() {
        return state == State.OK;
    }

    /** 人可读限定语，没有滞后时返回空串。 */
    public String caveat() {
        switch (state) {
            case OK:
                return "";
            case CATCHING_UP:
                return "我这边还没处理到最新（差约 " + (lagMs / 60_000L) + " 分钟）";
            case STALLED:
            default:
                return projectedUntil <= 0L
                        ? "这段时间没有记忆"
                        : "记录在 " + projectedUntil + " 之后就停了，之后没有记忆";
        }
    }
}
