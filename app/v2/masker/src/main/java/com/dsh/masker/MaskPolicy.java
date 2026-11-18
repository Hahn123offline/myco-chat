package com.dsh.masker;

/** 掩码策略：默认保守，amount/name 默认不掩，尾部保留可配。 */
public final class MaskPolicy {

    public boolean maskCard = true;
    public boolean maskIdCard = true;
    public boolean maskPhone = true;
    public boolean maskCode = true;
    public boolean maskEmail = true;
    /** 金额对记忆有用，默认不掩 */
    public boolean maskAmount = false;
    /** 姓名默认不掩 */
    public boolean maskName = false;

    public int keepCardTail = 4;
    public int keepIdHead = 4;
    public int keepIdTail = 2;
    public int keepPhoneHead = 3;
    public int keepPhoneTail = 4;
    /** 图片 bbox 外扩像素，防止 OCR 贴边露出边缘 */
    public int bboxPadding = 3;

    public static MaskPolicy defaults() { return new MaskPolicy(); }

    public MaskPolicy copy() {
        MaskPolicy p = new MaskPolicy();
        p.maskCard = maskCard; p.maskIdCard = maskIdCard; p.maskPhone = maskPhone;
        p.maskCode = maskCode; p.maskEmail = maskEmail; p.maskAmount = maskAmount;
        p.maskName = maskName;
        p.keepCardTail = keepCardTail; p.keepIdHead = keepIdHead;
        p.keepIdTail = keepIdTail; p.keepPhoneHead = keepPhoneHead;
        p.keepPhoneTail = keepPhoneTail; p.bboxPadding = bboxPadding;
        return p;
    }
}
