package com.dsh.agent;

/** 工具更新 provenance 的最小接口；app/PC 各自实现。 */
public interface Provenance {
    java.util.List<String> sources();
    java.util.List<String> strippedSources();
    boolean recalled();
    void markRecalled();
    int maskedMissing();
    void addMaskedMissing(int n);
    String maskStatus();
    void setMaskStatus(String s);
    com.dsh.egress.EgressStage stage();
}
