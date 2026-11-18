package com.dsh.usagetrace;

import android.os.SystemClock;

/** 生产时间源：System.currentTimeMillis + SystemClock.elapsedRealtime。 */
public final class SystemA11yTimeSource implements A11yTimeSource {

    @Override public long nowMs() { return System.currentTimeMillis(); }

    @Override public long uptimeMs() { return SystemClock.elapsedRealtime(); }
}
