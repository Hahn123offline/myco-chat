package com.dsh.guard;

import java.util.Collections;
import java.util.List;

/** {@link Guard#check(String)} 的结果。 */
public final class GuardResult {

    public final Action action;
    /** 仅 BLOCK 时非 null：命中的死线类型（bank_card / id_card / payment_password / sms_code / cvc） */
    public final String blockedKind;
    /**
     * 给云端的文本：PASS = 原文；MASK = 掩码后文本；BLOCK = null（整条不上云）。
     */
    public final String maskedText;
    public final List<Hit> hits;

    GuardResult(Action action, String blockedKind, String maskedText, List<Hit> hits) {
        this.action = action;
        this.blockedKind = blockedKind;
        this.maskedText = maskedText;
        this.hits = Collections.unmodifiableList(hits);
    }

    public boolean isBlocked() { return action == Action.BLOCK; }

    public boolean isMasked() { return action == Action.MASK; }

    @Override public String toString() {
        return "GuardResult{" + action
                + (blockedKind == null ? "" : " kind=" + blockedKind)
                + ", hits=" + hits.size() + "}";
    }
}
