package com.dsh.masker;

import java.util.ArrayList;
import java.util.List;

/** 掩码输出：文本、文本 span、图片 bbox、审计分类。 */
public final class MaskedBlock {

    public final String text;
    public final List<MaskSpan> spans;
    public final List<MaskBBox> imageBboxes;
    public final List<MaskClassification> classifications;

    public MaskedBlock(String text, List<MaskSpan> spans, List<MaskBBox> imageBboxes,
                       List<MaskClassification> classifications) {
        this.text = text == null ? "" : text;
        this.spans = spans == null ? new ArrayList<>() : new ArrayList<>(spans);
        this.imageBboxes = imageBboxes == null ? new ArrayList<>() : new ArrayList<>(imageBboxes);
        this.classifications = classifications == null
                ? new ArrayList<>() : new ArrayList<>(classifications);
    }

    public boolean changed() { return !spans.isEmpty(); }
}
