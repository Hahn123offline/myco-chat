package com.dsh.v2;

import android.content.Context;
import android.util.Log;

import com.dsh.egress.AppExclusionPolicy;
import com.dsh.trace.Trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 隐私自检：用真实 trace.db 里的排除 App 块验证占位/灰块，不调模型、不上云。 */
public final class PrivacySelfTest {

    private PrivacySelfTest() { }

    public static String run(Context ctx) {
        StringBuilder b = new StringBuilder();
        try {
            TraceStore store = new TraceStore(ctx);
            List<Trace> recent = store.recentA11y(600);
            Trace excluded = null;
            for (Trace t : recent) {
                if (AppExclusionPolicy.isExcluded(t.pkg)) { excluded = t; break; }
            }
            if (excluded == null) return "SKIP: 最近 600 条里没有排除 App 的块\n";
            List<Trace> one = new ArrayList<>();
            one.add(excluded);
            AndroidBlockSource src = new AndroidBlockSource(ctx, "selftest", one);
            boolean all = src.allExcluded();
            String text = src.blocks("selftest").get(0).maskedText;
            boolean gray = src.mosaic("selftest", java.util.Collections.singletonList(excluded.id))
                    != null && !src.mosaics().isEmpty()
                    && src.mosaics().get(0).skipped == 1;
            boolean textOk = "[APP_EXCLUDED]".equals(text);
            b.append("block=").append(excluded.id)
                    .append(" pkg=").append(excluded.pkg)
                    .append(" allExcluded=").append(all)
                    .append(" text=").append(text)
                    .append(" textOk=").append(textOk)
                    .append(" graySkipped=").append(gray)
                    .append('\n');
        } catch (Throwable t) {
            b.append("FAIL ").append(t.getClass().getSimpleName()).append(": ")
                    .append(t.getMessage()).append('\n');
        }
        File f = new File(new File(ctx.getFilesDir(), "trace"), "privacy_selftest.txt");
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(b.toString());
        } catch (Throwable ignored) { }
        Log.i("DSHL2", "privacy selftest: " + b.toString().replace('\n', ' '));
        return b.toString();
    }
}
