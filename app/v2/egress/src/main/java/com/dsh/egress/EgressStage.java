package com.dsh.egress;

/** payload 阶段：原始屏幕文本 / 已过 Masker / 模型生成的二次文本。 */
public enum EgressStage { RAW, MASKED, DERIVED }
