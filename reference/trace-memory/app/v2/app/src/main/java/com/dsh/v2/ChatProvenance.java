package com.dsh.v2;

import com.dsh.egress.EgressStage;

import java.util.ArrayList;
import java.util.List;

/** 会话 provenance：有召回注入 → MASKED；纯对话 → DERIVED。 */
public final class ChatProvenance implements java.util.function.Supplier<EgressStage>, com.dsh.agent.Provenance {
    public final List<String> sources = new ArrayList<>();
    public final List<String> strippedSources = new ArrayList<>();
    public volatile boolean recalled;
    public volatile int maskedMissing;
    public volatile String maskStatus = "";

    @Override public java.util.List<String> sources() { return sources; }
    @Override public java.util.List<String> strippedSources() { return strippedSources; }
    @Override public boolean recalled() { return recalled; }
    @Override public void markRecalled() { recalled = true; }
    @Override public int maskedMissing() { return maskedMissing; }
    @Override public void addMaskedMissing(int n) { maskedMissing += n; }
    @Override public String maskStatus() { return maskStatus; }
    @Override public void setMaskStatus(String s) { maskStatus = s == null ? "" : s; }
    @Override public EgressStage stage() { return get(); }

    @Override public EgressStage get() {
        return recalled ? EgressStage.MASKED : EgressStage.DERIVED;
    }
}
