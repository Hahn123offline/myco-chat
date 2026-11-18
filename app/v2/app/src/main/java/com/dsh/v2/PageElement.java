package com.dsh.v2;

import java.util.ArrayList;
import java.util.List;

/** 结构化页面元素（Masker 的输入；不含任何敏感判决）。 */
public final class PageElement {

    public enum Role { PARAGRAPH, LIST_ITEM, INPUT, BUTTON, TITLE, BADGE, OTHER }
    public enum Source { A11Y, OCR, BOTH }

    public final String id;
    public final String text;
    public final int left, top, right, bottom;   // 屏幕坐标
    public final Role role;
    public final Source source;
    public final double confidence;
    /** 只做提名：card_candidate / amount / sms_code / id_card / input / contact / page_sensitive */
    public final List<String> signals;

    public PageElement(String id, String text, int left, int top, int right, int bottom,
                       Role role, Source source, double confidence, List<String> signals) {
        this.id = id == null ? "" : id;
        this.text = text == null ? "" : text;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.role = role == null ? Role.PARAGRAPH : role;
        this.source = source == null ? Source.A11Y : source;
        this.confidence = confidence;
        this.signals = signals == null ? new ArrayList<>() : new ArrayList<>(signals);
    }

    public int width() { return Math.max(0, right - left); }
    public int height() { return Math.max(0, bottom - top); }

    public PageElement withSource(Source s, double conf, List<String> extraSignals) {
        List<String> sig = new ArrayList<>(signals);
        if (extraSignals != null) sig.addAll(extraSignals);
        return new PageElement(id, text, left, top, right, bottom, role, s, conf, sig);
    }
}
