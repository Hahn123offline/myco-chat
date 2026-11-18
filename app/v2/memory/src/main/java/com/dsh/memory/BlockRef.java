package com.dsh.memory;

/** 原始块的只读详情：OCR 文本 + 掩码文本 + 截图指针。 */
public final class BlockRef {
    public long id;
    public String text = "";
    public String maskedText = "";
    public String pkg = "";
    public long t0;
    public long t1;
    public String shotRef = "";
    public String pageId = "";

    public boolean hasShot() { return shotRef != null && !shotRef.isEmpty(); }

    public String render() {
        StringBuilder b = new StringBuilder();
        b.append("block #").append(id).append("  pkg=").append(pkg)
                .append("  ").append(t0).append("–").append(t1);
        if (hasShot()) b.append("  shot=").append(shotRef).append("（有截图，可看图核对）");
        else b.append("  （无截图）");
        b.append("\npage_id=").append(pageId == null ? "" : pageId);
        b.append("\n[OCR]\n").append(text == null ? "" : text);
        if (maskedText != null && !maskedText.isEmpty() && !maskedText.equals(text)) {
            b.append("\n[masked]\n").append(maskedText);
        }
        return b.toString();
    }
}
