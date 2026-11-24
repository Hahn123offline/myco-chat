package com.dsh.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 关闭 trace 时 text 为空、但有代表帧 → 同步补一次 OCR（调用方在 trace-persist 线程）。
 * 最多等 maxWaitMs，超时/失败返回 ""（绝不影响落盘）。
 */
final class OcrBackfill {

    private static volatile TextRecognizer recognizer;

    private OcrBackfill() { }

    static String resolve(Context app, String shotRef, long maxWaitMs) {
        if (shotRef == null || shotRef.isEmpty()) return "";
        File f = new File(new File(app.getFilesDir(), "trace"), shotRef);
        if (!f.isFile()) return "";
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp == null) return "";
            TextRecognizer rec = recognizer();
            if (rec == null) return "";
            Text text = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)),
                    Math.max(1L, maxWaitMs), TimeUnit.MILLISECONDS);
            return text == null || text.getText() == null ? "" : text.getText();
        } catch (Throwable t) {
            return "";
        } finally {
            if (bmp != null) bmp.recycle();
        }
    }

    private static TextRecognizer recognizer() {
        TextRecognizer r = recognizer;
        if (r == null) {
            synchronized (OcrBackfill.class) {
                if (recognizer == null) {
                    recognizer = TextRecognition.getClient(
                            new ChineseTextRecognizerOptions.Builder().build());
                }
                r = recognizer;
            }
        }
        return r;
    }
}
