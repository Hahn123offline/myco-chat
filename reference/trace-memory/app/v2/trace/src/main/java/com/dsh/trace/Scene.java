package com.dsh.trace;

/** 当前场景的累积状态（内存中，关闭时产出 {@link Trace}）。 */
public final class Scene {

    public final String pkg;
    /** 元数据：不参与切块判定（IME/弹窗类名会污染 activity，task-22） */
    public String activity;
    /** 元数据：不再参与切块判定（task-21 改由 HSV 画面切点） */
    public String pageId;
    public final long t0;
    public long lastEventAt;
    public String text = "";
    public String shotRef;
    /** 页面标题文本：只是字段，不参与 pageId 身份判定（task-20） */
    public String pageTitle = "";
    /** 该场景代表帧所在页面的 pkg；用于纠正通知/悬浮窗事件带错 pkg。 */
    public String pagePkg = "";
    /** 块内最清晰帧的 Laplacian 方差（选代表帧用） */
    public double bestSharpness = -1;
    public boolean bestHasText = false;
    /** 块内多帧；上限由 TraceAccumulator 控制。 */
    public final java.util.List<TraceFrame> frames = new java.util.ArrayList<>();

    Scene(String pkg, String activity, String pageId, long t0) {
        this.pkg = pkg == null ? "" : pkg;
        this.activity = activity == null ? "" : activity;
        this.pageId = pageId == null ? "" : pageId;
        this.t0 = t0;
        this.lastEventAt = t0;
    }
}
