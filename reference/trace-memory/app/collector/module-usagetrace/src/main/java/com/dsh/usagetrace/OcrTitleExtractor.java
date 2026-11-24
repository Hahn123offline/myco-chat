package com.dsh.usagetrace;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.HardwareBuffer;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;

/** 标题条 OCR：只负责"把这一条标题条读成一句话"，其它都不管。 */
public final class OcrTitleExtractor {

    private static TextRecognizer rec;

    public interface Cb { void onTitle(String title, float conf); }

    private static synchronized TextRecognizer get() {
        if (rec == null) {
            rec = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        }
        return rec;
    }

    public static void extract(File titlebarFile, Cb cb) {
        try {
            Bitmap bm = BitmapFactory.decodeFile(titlebarFile.getAbsolutePath());
            if (bm == null) { cb.onTitle("", 0f); return; }
            final int w = bm.getWidth();
            InputImage in = InputImage.fromBitmap(bm, 0);
            get().process(in)
                    .addOnSuccessListener(t -> {
                        Result r = pick(t, w);
                        cb.onTitle(r.title, r.conf);
                    })
                    .addOnFailureListener(e -> cb.onTitle("", 0f));
        } catch (Exception e) {
            cb.onTitle("", 0f);
        }
    }

    private static final class Result {
        String title = "";
        float conf = 0f;
    }

    /** 标题通常在中间：用行框中心与图片中心的偏移打分 */
    private static Result pick(Text t, int imgW) {
        Result best = new Result();
        int bestScore = -1;
        float bestConf = 0f;
        for (Text.TextBlock b : t.getTextBlocks()) {
            for (Text.Line l : b.getLines()) {
                String norm = TitleNormalizer.normalize(l.getText());
                if (norm.isEmpty()) continue;
                int score = TitleNormalizer.cjkCount(norm) * 2 + (norm.length() <= 12 ? 3 : 0);
                android.graphics.Rect bb = l.getBoundingBox();
                if (bb != null && imgW > 0) {
                    float off = Math.abs(bb.centerX() - imgW / 2f) / imgW;
                    if (off < 0.18f) score += 6;
                    else if (off < 0.30f) score += 2;
                    else score -= 4;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best.title = norm;
                    best.conf = Math.min(0.9f, 0.5f + score / 20f);
                }
            }
        }
        return best;
    }
}
