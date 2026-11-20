package com.dsh.v2;

import android.content.Context;

import com.dsh.memory.Kind;
import com.dsh.memory.MemItem;

/** 每 5 分钟一条 health 心跳：记录状态中断本身就是证据。 */
public final class HealthHeartbeat {

    public static final String NS = "health";
    public static final long INTERVAL_MS = 5 * 60_000L;

    private HealthHeartbeat() { }

    public static void write(Context app) {
        if (app == null) return;
        try {
            androidMemoryStore(app);
        } catch (Throwable t) {
            android.util.Log.e("DSHL2", "health heartbeat failed", t);
        }
    }

    private static void androidMemoryStore(Context app) throws Exception {
        AndroidMemoryStore store = new AndroidMemoryStore(app);
        store.registerNamespace(NS, false);
        TraceModule m = TraceRuntime.get();
        String recording = m == null ? "UNAVAILABLE" : m.recordingState().label();
        boolean screen = true;
        long lastAgo = -1;
        long today = 0;
        if (m != null) {
            today = m.todayCount();
            lastAgo = -1; // statusLine 已包含 lastEventAgoMs，data 里不重复算
        }
        long lag = ProjectionHealth.lagMin(app);
        long now = System.currentTimeMillis();
        boolean health = "ACTIVE".equals(recording) && lag >= 0 && lag <= 10;
        String text = "recording=" + recording
                + " screenOn=" + screen
                + " a11y=" + (A11yHealth.isEnabled(app) ? "ok" : "off")
                + " lastEvent=" + lastAgo + "ms"
                + " tracesToday=" + today
                + " lastBlock=" + ProjectionHealth.lastBlock
                + " lastLineBlock=" + ProjectionHealth.lastLineBlock
                + " lagMin=" + lag
                + " health=" + (health ? "OK" : "RED");
        String data = "{\"recording\":\"" + recording + "\",\"screenOn\":" + screen
                + ",\"a11y\":" + A11yHealth.isEnabled(app)
                + ",\"lastEventAgoMs\":" + lastAgo
                + ",\"tracesToday\":" + today
                + ",\"lastBlock\":" + ProjectionHealth.lastBlock
                + ",\"lastLineBlock\":" + ProjectionHealth.lastLineBlock
                + ",\"lagMin\":" + lag + "}";
        MemItem item = new MemItem(NS, Kind.NOTE, "health:" + (now / INTERVAL_MS),
                text, data);
        item.t0 = now;
        item.t1 = now;
        item.weight = 0.2;
        store.put(item);
        android.util.Log.i("DSHL2", "health heartbeat " + text);
    }
}
