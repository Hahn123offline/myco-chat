package com.dsh.trace;

/**
 * 一条痕迹 = 同一个页面连续停留的一段时间（规格 v1.0 §2/§3）。
 *
 * 13 个规格字段 + 1 个内部字段：{@link #maskedText} 只给上云用，本地 text 永远原文。
 * id 由存储层回填（纯逻辑创建时为 0）。
 */
public final class Trace {

    public long id;
    public long t0;
    public long t1;
    public int dwellMs;
    public String pkg;
    public String activity;
    public String pageId;
    /** 页面标题文本：只是字段，不参与 pageId 身份判定（task-20） */
    public String pageTitle;
    /** 整屏 OCR 原文，截断 2000 字（本地存原文） */
    public String text;
    /** 截图引用（不透明字符串，如 "shots/xxx.jpg"） */
    public String shotRef;
    /** 0=通过 1=命中死线 */
    public int guard;
    /** 命中的死线类型（可空） */
    public String guardHit;
    /** 上次上云时间（可空） */
    public long cloudAt;
    /** 云端标签（可空） */
    public String label;
    /** 上云用文本：PASS=原文；MASK=掩码后；BLOCK=null。本地 text 不受影响。 */
    public String maskedText;
    /** 数据来源："a11y"（默认，轨迹主路）或 "usage"（UsageStats 降级，只有 pkg/t0/t1） */
    public String source = "a11y";
}
