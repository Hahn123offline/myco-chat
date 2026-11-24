package com.dsh.v2;

import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

/** Quick Settings：记住当前这一帧（manual 块，present=true）。 */
public final class RememberTile extends TileService {

    @Override public void onClick() {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            TraceModule m = TraceRuntime.get();
            if (m != null) ok = m.rememberCurrentFrame();
            final boolean result = ok;
            Log.i("DSHHost", "remember tile result=" + result);
            android.os.Handler h = new android.os.Handler(
                    android.os.Looper.getMainLooper());
            h.post(() -> Toast.makeText(app, result ? "已记住这一帧" : "记住失败：无当前帧",
                    Toast.LENGTH_SHORT).show());
        }, "remember-tile").start();
    }
}
