package com.dsh.usagetrace;

/** 单个应用的采集策略。用配置表达，不用 if (pkg.equals(...)) 硬编码。 */
public final class CapturePolicy {

    public enum Mode {
        /** 只在场景变化时采（有节点树的原生 App） */
        EVENT,
        /** 定时采样（画布类、节点树被隐藏的应用） */
        TICK,
        /** 不采 */
        OFF
    }

    public final String pkg;
    public final Mode mode;
    /** TICK 模式下的采样间隔 */
    public final long tickMs;
    /** 是否对标题条跑 OCR */
    public final boolean ocr;
    /** OCR 没结果时，标题带占屏幕高度的比例上限（B站标题在下半屏） */
    public final float titleRegion;

    public CapturePolicy(String pkg, Mode mode, long tickMs, boolean ocr, float titleRegion) {
        this.pkg = pkg;
        this.mode = mode;
        this.tickMs = tickMs;
        this.ocr = ocr;
        this.titleRegion = titleRegion;
    }

    public static CapturePolicy def(String pkg) {
        return new CapturePolicy(pkg, Mode.EVENT, 0, false, 0.25f);
    }

    @Override
    public String toString() {
        return pkg + "{" + mode + ",tick=" + tickMs + ",ocr=" + ocr + ",region=" + titleRegion + "}";
    }
}
