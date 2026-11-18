package com.dsh.v2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.dsh.masker.ImageMasker;
import com.dsh.masker.MaskBBox;
import com.dsh.masker.MaskBlock;
import com.dsh.masker.MaskElement;
import com.dsh.masker.MaskPolicy;
import com.dsh.masker.Masker;
import com.dsh.masker.MaskedBlock;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** adb: MASK_PREVIEW → 用最近同帧页面元素生成掩码 before/after PNG。 */
public final class MaskPreviewReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.v2.action.MASK_PREVIEW";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        try {
            Context app = context.getApplicationContext();
            TraceStore store = new TraceStore(app);
            TraceStore.PageBinding bind = store.latestPageBinding();
            if (bind == null) return;
            List<PageElement> elements = store.pageElements(bind.t0, bind.pkg);
            List<MaskElement> mes = new ArrayList<>();
            for (PageElement e : elements) {
                mes.add(new MaskElement(e.id, e.text, e.left, e.top, e.right, e.bottom,
                        e.role.name(), e.signals));
            }
            MaskPolicy policy = MaskPolicy.defaults();
            MaskedBlock masked = new Masker().mask(new MaskBlock("preview",
                    bind.pkg + " t0=" + bind.t0, mes), policy);
            File traceDir = new File(app.getFilesDir(), "trace");
            File shot = new File(app.getFilesDir(), "trace/" + bind.shotRef);
            Bitmap bmp = BitmapFactory.decodeFile(shot.getAbsolutePath());
            if (bmp == null) return;
            File before = new File(traceDir, "mask_before.png");
            File after = new File(traceDir, "mask_after.png");
            try (FileOutputStream fo = new FileOutputStream(before)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
            float sx = bmp.getWidth() / (float) Math.max(1, dm.widthPixels);
            float sy = bmp.getHeight() / (float) Math.max(1, dm.heightPixels);
            List<MaskBBox> boxes = new ArrayList<>();
            for (MaskBBox b : masked.imageBboxes) {
                boxes.add(new MaskBBox(Math.round(b.x * sx), Math.round(b.y * sy),
                        Math.round(b.w * sx), Math.round(b.h * sy), b.kind));
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            ImageMasker.paint(w, h, px, boxes, Math.round(policy.bboxPadding * Math.max(sx, sy)));
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            out.setPixels(px, 0, w, 0, 0, w, h);
            try (FileOutputStream fo = new FileOutputStream(after)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            out.recycle();
            bmp.recycle();
        } catch (Throwable ignored) { }
    }
}
