package com.dsh.v2;

import com.dsh.egress.EgressStage;

import java.util.ArrayList;
import java.util.List;

/** 会话 provenance：有召回注入 → MASKED；纯对话 → DERIVED。 */
public final class ChatProvenance implements java.util.function.Supplier<EgressStage> {
    public final List<String> sources = new ArrayList<>();
    public final List<String> strippedSources = new ArrayList<>();
    public volatile boolean recalled;
    public volatile int maskedMissing;
    public volatile String maskStatus = "";

    @Override public EgressStage get() {
        return recalled ? EgressStage.MASKED : EgressStage.DERIVED;
    }
}
