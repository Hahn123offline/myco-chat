package com.dsh.v2;

import com.dsh.guard.Guard;
import com.dsh.guard.GuardResult;
import com.dsh.masker.MaskBlock;
import com.dsh.masker.MaskElement;
import com.dsh.masker.MaskPolicy;
import com.dsh.masker.Masker;
import com.dsh.masker.MaskedBlock;

import java.util.ArrayList;
import java.util.List;

/** App 适配层：PageElement → Masker 纯数据；掩码后 guard 复检。 */
public final class MaskerBridge {

    private static final Guard GUARD = new Guard();

    private MaskerBridge() { }

    public static MaskedBlock mask(List<PageElement> elements, String fallbackText,
                                   MaskPolicy policy) {
        List<MaskElement> mes = new ArrayList<>();
        if (elements != null) {
            for (PageElement e : elements) {
                mes.add(new MaskElement(e.id, e.text, e.left, e.top, e.right, e.bottom,
                        e.role.name(), e.signals));
            }
        }
        return new Masker().mask(new MaskBlock("", fallbackText, mes),
                policy == null ? MaskPolicy.defaults() : policy);
    }

    public static MaskedBlock maskText(String text) {
        return new Masker().mask(MaskBlock.ofText(text), MaskPolicy.defaults());
    }

    /** 掩码后复检：另一套判定（Luhn/GB11643 等）兜底。 */
    public static boolean guardPass(String maskedText) {
        GuardResult r = GUARD.check(maskedText == null ? "" : maskedText);
        return !r.isBlocked();
    }
}
