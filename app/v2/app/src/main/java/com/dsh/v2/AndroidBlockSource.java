package com.dsh.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.dsh.agent.l2.BlockSource;
import com.dsh.agent.l2.L2Block;
import com.dsh.egress.AppExclusionPolicy;
import com.dsh.trace.Trace;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android 版 BlockSource：trace.db 块 + files/trace/shots/*.jpg。
 *
 * 拼图：4×4 内，成品最长边 ≤1600；敏感块/缺图用灰块；不引 Glide/Coil。
 * 每次拼图记录尺寸，供 token 计费核对。
 */
public final class AndroidBlockSource implements BlockSource {

    public static final int MAX_EDGE = 1600;
    public static final int MAX_TILES = 16;
    public static final int MAX_GRID = 4;

    public static final class MosaicInfo {
        public final int width;
        public final int height;
        public final int tiles;
        public final int skipped;
        public final int bytes;

        MosaicInfo(int w, int h, int tiles, int skipped, int bytes) {
            this.width = w;
            this.height = h;
            this.tiles = tiles;
            this.skipped = skipped;
            this.bytes = bytes;
        }

        public String line() {
            return "mosaic width=" + width + " height=" + height
                    + " tiles=" + tiles + " skipped=" + skipped + " jpegBytes=" + bytes;
        }
    }

    private final Context app;
    private final String chainId;
    private final List<L2Block> blocks;
    private final Map<Long, Trace> byId = new HashMap<>();
    private final List<MosaicInfo> mosaics = new ArrayList<>();
    private final int excludedBlocks;
    private final int maskingBlocks;
    private final Map<Long, com.dsh.masker.MaskedBlock> maskedById = new HashMap<>();
    private final Map<Long, com.dsh.masker.MaskPolicy> maskPolicyById = new HashMap<>();
    private final Map<Long, TraceStore.PageBinding> bindingById = new HashMap<>();
    private int maskedSpans;
    private String maskedKinds = "";
    private boolean maskFailed;

    public AndroidBlockSource(Context app, String chainId, List<Trace> traces) {
        this.app = app.getApplicationContext();
        this.chainId = chainId;
        List<L2Block> list = new ArrayList<>();
        int excluded = 0, masking = 0;
        TraceStore pageStore = new TraceStore(this.app);
        StringBuilder kinds = new StringBuilder();
        for (Trace t : traces) {
            AppExclusionPolicy.Level level = AppExclusionPolicy.level(t.pkg);
            boolean never = level == AppExclusionPolicy.Level.NEVER_CLOUD;
            boolean redact = level == AppExclusionPolicy.Level.REDACT_THEN_CLOUD;
            if (never) excluded++;
            if (redact) masking++;
            boolean blocked = t.guard == 1 || never;
            String title = t.activity == null || t.activity.isEmpty()
                    ? (t.pageTitle == null ? "" : t.pageTitle) : t.activity;
            if (never) title = "[APP_EXCLUDED]";
            String text = never ? "[APP_EXCLUDED]"
                    : (t.text == null ? "" : t.text);
            if (redact) {
                // 注意：trace.maskedText 是 :guard 的掩码结果（只处理手机号/邮箱），
                // 不是 Masker 输出；不得用它判断能否上云。这里运行时重新跑 Masker。
                TraceStore.PageBinding bind = pageStore.bindingForTrace(t.t0, t.t1, t.pkg);
                List<PageElement> elements = bind == null ? new ArrayList<>()
                        : pageStore.pageElements(bind.t0, bind.pkg);
                com.dsh.masker.MaskedBlock mb = MaskerBridge.mask(elements, text,
                        com.dsh.masker.MaskPolicy.defaults());
                if (!MaskerBridge.guardPass(mb.text)) {
                    // 掩码后 guard 复检仍命中死线：拒绝整块出网
                    maskFailed = true;
                    text = "[BLOCKED_BY_EGRESS]";
                    blocked = true;
                } else {
                    text = mb.text;
                    maskedById.put(t.id, mb);
                    bindingById.put(t.id, bind);
                    maskedSpans += mb.spans.size();
                    for (com.dsh.masker.MaskSpan s : mb.spans) {
                        if (kinds.indexOf(s.kind) < 0) kinds.append(s.kind).append(',');
                    }
                }
            }
            list.add(new L2Block(t.id, t.pkg, title, text, t.t0, t.t1, blocked, never));
            byId.put(t.id, t);
        }
        this.blocks = list;
        this.excludedBlocks = excluded;
        this.maskingBlocks = masking;
        this.maskedKinds = kinds.toString();
    }

    public int excludedBlocks() { return excludedBlocks; }

    public int maskingBlocks() { return maskingBlocks; }

    public int maskedSpans() { return maskedSpans; }

    public String maskedKinds() { return maskedKinds; }

    /** REDACT 掩码失败（guard 复检仍 BLOCK）→ 不放行。 */
    public boolean requiresMasking() { return maskFailed; }

    public int totalBlocks() { return blocks.size(); }

    /** 是否所有块都被应用级排除（此时不生成 body，等本地模型）。 */
    public boolean allExcluded() {
        return !blocks.isEmpty() && excludedBlocks == blocks.size();
    }

    public boolean anyExcluded() { return excludedBlocks > 0; }

    @Override public boolean hasExcludedBlocks() { return anyExcluded(); }

    public List<MosaicInfo> mosaics() { return mosaics; }

    @Override public List<L2Block> blocks(String chainId) {
        return new ArrayList<>(blocks);
    }

    @Override public byte[] mosaic(String chainId, List<Long> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) return null;
        int n = Math.min(blockIds.size(), MAX_TILES);
        // 官方结论：同像素数横图更便宜 → 多列少行（优先 4 列）
        int cols = Math.min(MAX_GRID, Math.max(1, n));
        int rows = (int) Math.ceil(n / (double) cols);
        int tile = Math.max(64, Math.min(MAX_EDGE / Math.max(cols, rows), 400));
        int w = cols * tile;
        int h = rows * tile;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint gray = new Paint();
        gray.setColor(Color.rgb(160, 160, 160));
        Paint bg = new Paint();
        bg.setColor(Color.rgb(220, 220, 220));
        canvas.drawRect(0, 0, w, h, bg);
        boolean debugRedact = false;
        for (Long id : blockIds) {
            Trace t = byId.get(id);
            if (t != null && AppExclusionPolicy.level(t.pkg)
                    == AppExclusionPolicy.Level.REDACT_THEN_CLOUD) {
                debugRedact = true;
                break;
            }
        }
        Bitmap beforeOut = null;
        Canvas beforeCanvas = null;
        if (debugRedact) {
            beforeOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            beforeCanvas = new Canvas(beforeOut);
            beforeCanvas.drawRect(0, 0, w, h, bg);
        }
        int drawn = 0, skipped = 0;
        int i = 0;
        for (Long id : blockIds) {
            if (i >= MAX_TILES) break;
            int cx = (i % cols) * tile;
            int cy = (i / cols) * tile;
            Trace t = byId.get(id);
            Bitmap src = null;
            if (t != null && t.guard != 1 && t.shotRef != null) {
                AppExclusionPolicy.Level level = AppExclusionPolicy.level(t.pkg);
                if (level == AppExclusionPolicy.Level.NORMAL) {
                    src = decode(t.shotRef, tile * 2);
                } else if (level == AppExclusionPolicy.Level.REDACT_THEN_CLOUD) {
                    src = maskedImage(t, tile * 2);
                }
            }
            if (src == null) {
                canvas.drawRect(cx, cy, cx + tile, cy + tile, gray);
                if (beforeCanvas != null) beforeCanvas.drawRect(cx, cy, cx + tile, cy + tile, gray);
                if (t == null || t.guard == 1
                        || AppExclusionPolicy.level(t.pkg) != AppExclusionPolicy.Level.NORMAL) {
                    skipped++;
                }
            } else {
                Bitmap scaled = scaleCenter(src, tile, tile);
                canvas.drawBitmap(scaled, cx, cy, null);
                if (scaled != src) scaled.recycle();
                src.recycle();
                drawn++;
            }
            if (beforeCanvas != null && t != null && t.shotRef != null) {
                Bitmap raw = decode(t.shotRef, tile * 2);
                if (raw != null) {
                    Bitmap scaledRaw = scaleCenter(raw, tile, tile);
                    beforeCanvas.drawBitmap(scaledRaw, cx, cy, null);
                    if (scaledRaw != raw) scaledRaw.recycle();
                    raw.recycle();
                }
            }
            i++;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 80, bos);
        if (beforeOut != null) {
            try {
                File dir = new File(app.getFilesDir(), "trace/mosaic_debug");
                if (!dir.exists()) dir.mkdirs();
                try (FileOutputStream fo = new FileOutputStream(
                        new File(dir, "mosaic_before_last.jpg"))) {
                    beforeOut.compress(Bitmap.CompressFormat.JPEG, 80, fo);
                }
                try (FileOutputStream fo = new FileOutputStream(
                        new File(dir, "mosaic_after_last.jpg"))) {
                    fo.write(bos.toByteArray());
                }
                android.util.Log.i("DSHL2", "mosaic_debug saved chain=" + chainId
                        + " bytes=" + bos.size());
            } catch (Throwable ignored) { }
            beforeOut.recycle();
        }
        MosaicInfo info = new MosaicInfo(w, h, drawn, skipped, bos.size());
        mosaics.add(info);
        out.recycle();
        return bos.toByteArray();
    }

    @Override public byte[] frame(String chainId, long blockId, int maxEdge) {
        Trace t = byId.get(blockId);
        if (t == null || t.guard == 1 || t.shotRef == null) return null;
        int edge = Math.max(256, Math.min(maxEdge <= 0 ? MAX_EDGE : maxEdge, MAX_EDGE));
        Bitmap src;
        AppExclusionPolicy.Level level = AppExclusionPolicy.level(t.pkg);
        if (level == AppExclusionPolicy.Level.NORMAL) {
            src = decode(t.shotRef, edge);
        } else if (level == AppExclusionPolicy.Level.REDACT_THEN_CLOUD) {
            src = maskedImage(t, edge);
        } else {
            return null;
        }
        if (src == null) return null;
        Bitmap scaled = scaleCenter(src, edge, edge, true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, bos);
        if (scaled != src) scaled.recycle();
        src.recycle();
        return bos.toByteArray();
    }

    /** REDACT 图片：先按 bbox 涂黑原图，再缩放；没有 bbox 则拒绝发送图片（返回 null→灰块）。 */
    private Bitmap maskedImage(Trace t, int target) {
        com.dsh.masker.MaskedBlock mb = maskedById.get(t.id);
        // null = Masker 没跑成/guard 拒绝，必须拒绝图；
        // imageBboxes 为空 = 扫过且确认没有敏感内容，原图可安全放行。
        if (mb == null) return null;
        Bitmap bmp = decode(t.shotRef, Math.max(target, MAX_EDGE));
        if (bmp == null) return null;
        if (mb.imageBboxes.isEmpty()) return bmp;
        try {
            android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
            float sx = bmp.getWidth() / (float) Math.max(1, dm.widthPixels);
            float sy = bmp.getHeight() / (float) Math.max(1, dm.heightPixels);
            List<com.dsh.masker.MaskBBox> boxes = new ArrayList<>();
            for (com.dsh.masker.MaskBBox b : mb.imageBboxes) {
                boxes.add(new com.dsh.masker.MaskBBox(Math.round(b.x * sx),
                        Math.round(b.y * sy), Math.round(b.w * sx), Math.round(b.h * sy),
                        b.kind));
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            com.dsh.masker.ImageMasker.paint(w, h, px, boxes,
                    Math.round(3 * Math.max(sx, sy)));
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            out.setPixels(px, 0, w, 0, 0, w, h);
            bmp.recycle();
            return out;
        } catch (Throwable e) {
            bmp.recycle();
            return null;
        }
    }

    private Bitmap decode(String shotRef, int target) {
        try {
            File f = new File(app.getFilesDir(), "trace/" + shotRef);
            if (!f.exists()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            if (o.outWidth > target || o.outHeight > target) {
                while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 等比缩放后居中裁剪/letterbox 到目标框（保持白底美观：居中留边）。 */
    private static Bitmap scaleCenter(Bitmap src, int tw, int th) {
        return scaleCenter(src, tw, th, false);
    }

    private static Bitmap scaleCenter(Bitmap src, int tw, int th, boolean exact) {
        float scale = Math.min((float) tw / src.getWidth(), (float) th / src.getHeight());
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(src, w, h, true);
        if (exact && w == tw && h == th) return scaled;
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(scaled, (tw - w) / 2f, (th - h) / 2f, null);
        if (scaled != src) scaled.recycle();
        return out;
    }
}
