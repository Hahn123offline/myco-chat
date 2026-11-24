package com.dsh.masker;

import java.util.ArrayList;
import java.util.List;

/** 待掩码的块：一段文本 + 可选的结构化元素。 */
public final class MaskBlock {

    public final String id;
    public final String text;
    public final List<MaskElement> elements;

    public MaskBlock(String id, String text, List<MaskElement> elements) {
        this.id = id == null ? "" : id;
        this.text = text == null ? "" : text;
        this.elements = elements == null ? new ArrayList<>() : new ArrayList<>(elements);
    }

    public static MaskBlock ofText(String text) {
        return new MaskBlock("", text, new ArrayList<>());
    }
}
