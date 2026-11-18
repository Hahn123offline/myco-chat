package com.dsh.models;

import com.dsh.egress.EgressTier;

import java.util.Set;

/** 一次模型调用的需求声明（调用方写这个，不写 provider 名）。 */
public interface ModelTask {

    /** 需要的 provider 能力 */
    Set<Capability> need();

    /** DRAFT / GOOD */
    Quality quality();

    /** 白/灰/黑出网等级，见 EgressTier */
    EgressTier egress();

    /** 观测用任务名，例如 "l2.body" */
    default String taskName() { return "unnamed"; }
}
