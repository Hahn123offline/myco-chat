package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.dsh.trace.Trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** adb: DUMP_PAGE → page_elements_dump.txt + 最新截图上的 bbox 标注 PNG。 */
public final class PageDumpReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.DUMP_PAGE";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        try {
            Context app = context.getApplicationContext();
            TraceStore store = new TraceStore(app);
            TraceStore.PageBinding binding = store.latestPageBinding();
            long t0 = binding == null ? 0 : binding.t0;
            String pkg = binding == null ? "" : binding.pkg;
            String shotRef = binding == null ? "" : binding.shotRef;
            List<PageElement> elems = t0 == 0 ? java.util.Collections.emptyList()
                    : store.pageElements(t0, pkg);
            StringBuilder b = new StringBuilder();
            b.append("page t0=").append(t0).append(" pkg=").append(pkg)
                    .append(" shotRef=").append(shotRef)
                    .append(" elements=").append(elems.size()).append('\n');
            for (PageElement e : elems) {
                b.append(e.id).append(' ').append(e.role).append(' ').append(e.source)
                        .append(" bbox=[").append(e.left).append(',').append(e.top).append(',')
                        .append(e.right).append(',').append(e.bottom).append(']')
                        .append(" conf=").append(e.confidence)
                        .append(" signals=").append(e.signals)
                        .append(" text=").append(e.text).append('\n');
            }
            File traceDir = new File(app.getFilesDir(), "trace");
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(new File(traceDir, "page_elements_dump.txt")),
                    StandardCharsets.UTF_8)) {
                w.write(b.toString());
            }
            if (!shotRef.isEmpty()) {
                File shot = shotRef.startsWith("shots/")
                        ? new File(app.getFilesDir(), "trace/" + shotRef)
                        : new File(new File(traceDir, "shots"), shotRef);
                Bitmap bmp = BitmapFactory.decodeFile(shot.getAbsolutePath());
                if (bmp != null) {
                    Bitmap out = bmp.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas canvas = new Canvas(out);
                    android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
                    // a11y/OCR bbox 都是屏幕坐标；截图若被缩放必须显式换算
                    float sx = bmp.getWidth() / (float) Math.max(1, dm.widthPixels);
                    float sy = bmp.getHeight() / (float) Math.max(1, dm.heightPixels);
                    Paint p = new Paint();
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3f);
                    p.setColor(Color.RED);
                    for (PageElement e : elems) {
                        canvas.drawRect(e.left * sx, e.top * sy, e.right * sx, e.bottom * sy, p);
                    }
                    File png = new File(traceDir, "page_bbox_" + t0 + ".png");
                    try (FileOutputStream fos = new FileOutputStream(png)) {
                        out.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    out.recycle();
                    bmp.recycle();
                    Log.i("DSHL2", "page dump png=" + png.getAbsolutePath()
                            + " scale=" + sx + "x" + sy);
                }
            }
            Log.i("DSHL2", "page dump: " + b.toString().replace('\n', ' '));
        } catch (Throwable t) {
            Log.e("DSHL2", "page dump failed", t);
        }
    }
}
