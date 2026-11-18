package com.dsh.memory;

/** 命中结果（供渲染/评测）。 */
public final class RecallHit {
    public final long itemId;
    public final double score;
    public final double sim;
    public final double weight;
    public final double decay;
    public final boolean present;
    public final DetailLevel detailLevel;
    public final String namespace;
    public final String text;
    public final String pkg;
    public final String pageId;
    public final String shotRef;
    public final String pageTitle;
    public final long t0;
    public final String maskedText;
    public MaskMode maskMode;
    /** 评测/日志用：IDF 覆盖度与命中最大 IDF。 */
    public double coverage;
    public double maxIdf;

    public RecallHit(long itemId, double score, double sim, double weight, double decay,
                     boolean present, DetailLevel detailLevel, String namespace, String text,
                     String pkg, String pageId, String shotRef, String pageTitle, long t0) {
        this(itemId, score, sim, weight, decay, present, detailLevel, namespace, text,
                pkg, pageId, shotRef, pageTitle, t0, "");
    }

    public RecallHit(long itemId, double score, double sim, double weight, double decay,
                     boolean present, DetailLevel detailLevel, String namespace, String text,
                     String pkg, String pageId, String shotRef, String pageTitle, long t0,
                     String maskedText) {
        this.itemId = itemId; this.score = score; this.sim = sim; this.weight = weight;
        this.decay = decay; this.present = present; this.detailLevel = detailLevel;
        this.namespace = namespace; this.text = text == null ? "" : text;
        this.pkg = pkg == null ? "" : pkg;
        this.pageId = pageId == null ? "" : pageId;
        this.shotRef = shotRef == null ? "" : shotRef;
        this.pageTitle = pageTitle == null ? "" : pageTitle;
        this.t0 = t0;
        this.maskedText = maskedText == null ? "" : maskedText;
        this.maskMode = MaskMode.NONE;
    }

    private RecallHit(RecallHit h, String text, MaskMode mode) {
        this(h.itemId, h.score, h.sim, h.weight, h.decay, h.present, h.detailLevel,
                h.namespace, text, h.pkg, h.pageId, h.shotRef, h.pageTitle, h.t0,
                h.maskedText);
        this.maskMode = mode;
    }

    public RecallHit withMask(String newText, MaskMode mode) {
        return new RecallHit(this, newText, mode);
    }

    public RecallHit withText(String newText) {
        return new RecallHit(itemId, score, sim, weight, decay, present, detailLevel,
                namespace, newText, pkg, pageId, shotRef, pageTitle, t0, maskedText);
    }

    public boolean hasPointer() {
        return !pkg.isEmpty() && (!pageId.isEmpty() || !shotRef.isEmpty());
    }

    public boolean manual() { return MemoryPolicy.NS_MANUAL.equals(namespace); }
}
