package com.dsh.v2;

import android.content.Context;

/** 记录开关的持久状态（Tile 与 TraceModule 共用）。 */
public final class RecordSwitch {
    public static final String PREFS = "dsh_state";
    public static final String KEY = "records.paused";

    private RecordSwitch() { }

    public static boolean paused(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static void setPaused(Context ctx, boolean paused) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, paused).apply();
        TraceModule m = TraceRuntime.get();
        if (m != null) m.setPaused(paused);
    }
}
