package com.dsh.usagetrace;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.view.Display;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 截图与标题条裁剪。只管"拍下来、裁好、存好、算个哈希"，不做任何决策。
 */
public final class ScreenGrabber {

    public static final class Result {
        public String framePath;
        public String titlebarPath;
        public long topHash;
        /** 给 OCR 用的文件；null 表示没拍 */
        public File titlebarFile;
    }

    public interface Cb { void onResult(Result r); }

    private static final int FRAME_W = 640;

    public static void grab(AccessibilityService svc, File dir, float titleRegion, Cb cb) {
        try {
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override public void onSuccess(AccessibilityService.ScreenshotResult r) {
                            HardwareBuffer hb = null;
                            try {
                                hb = r.getHardwareBuffer();
                                Bitmap bm = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
                                if (bm == null) { cb.onResult(null); return; }
                                Bitmap full = bm.copy(Bitmap.Config.ARGB_8888, false);
                                hb.close();
                                hb = null;

                                int w = full.getWidth(), h = full.getHeight();
                                int nw = Math.min(FRAME_W, w);
                                int nh = (int) ((float) h * nw / w);
                                Bitmap sc = Bitmap.createScaledBitmap(full, nw, nh, true);

                                if (!dir.exists()) dir.mkdirs();
                                String name = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                                        .format(new Date());

                                Result out = new Result();

                                File frame = new File(dir, name + ".jpg");
                                try (FileOutputStream fo = new FileOutputStream(frame)) {
                                    sc.compress(Bitmap.CompressFormat.JPEG, 70, fo);
                                }
                                out.framePath = frame.getAbsolutePath();

                                // 标题条：横向 20%–82%，纵向 4%–titleRegion
                                int x0 = (int) (sc.getWidth() * 0.20f);
                                int x1 = (int) (sc.getWidth() * 0.82f);
                                int y0 = (int) (sc.getHeight() * 0.04f);
                                int y1 = (int) (sc.getHeight() * Math.max(0.10f, Math.min(0.6f, titleRegion)));
                                if (x1 > x0 && y1 > y0) {
                                    Bitmap tb = Bitmap.createBitmap(sc, x0, y0, x1 - x0, y1 - y0);
                                    File tbf = new File(dir, name + "_tb.jpg");
                                    try (FileOutputStream fo = new FileOutputStream(tbf)) {
                                        tb.compress(Bitmap.CompressFormat.JPEG, 78, fo);
                                    }
                                    out.titlebarPath = tbf.getAbsolutePath();
                                    out.titlebarFile = tbf;
                                    out.topHash = dhash(tb);
                                }
                                cb.onResult(out);
                            } catch (Exception e) {
                                cb.onResult(null);
                            } finally {
                                if (hb != null) try { hb.close(); } catch (Exception ignored) { }
                            }
                        }

                        @Override public void onFailure(int errorCode) {
                            cb.onResult(null);
                        }
                    });
        } catch (Exception e) {
            cb.onResult(null);
        }
    }

    /** 9x8 灰度差分哈希：判断标题条有没有变，避免无谓 OCR */
    public static long dhash(Bitmap bmp) {
        Bitmap g = Bitmap.createScaledBitmap(bmp, 9, 8, true);
        int[] px = new int[9 * 8];
        g.getPixels(px, 0, 9, 0, 0, 9, 8);
        long h = 0;
        int bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int a = px[y * 9 + x], b = px[y * 9 + x + 1];
                int la = (((a >> 16) & 0xff) * 299 + ((a >> 8) & 0xff) * 587 + (a & 0xff) * 114) / 1000;
                int lb = (((b >> 16) & 0xff) * 299 + ((b >> 8) & 0xff) * 587 + (b & 0xff) * 114) / 1000;
                if (la > lb) h |= (1L << bit);
                bit++;
            }
        }
        return h;
    }
}
