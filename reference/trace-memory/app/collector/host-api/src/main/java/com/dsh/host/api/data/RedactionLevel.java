package com.dsh.host.api.data;

/**
 * 脱敏等级。用 int 而不是 enum：要落 SQLite、要向后兼容旧行（默认 RULE=0）。
 *
 *  - RULE  ：只过了规则层（格式 + 校验位）。可信，但漏人名/地址/账号这类规则写不出来的东西。
 *  - MODEL ：规则层之后又被模型补充过一轮；掩码只会比 RULE 多，不会少。
 *
 * 未识别/缺列一律按 RULE 处理 —— 不把"不知道"假装成"模型校验过"。
 */
public final class RedactionLevel {

    public static final int RULE = 0;
    public static final int MODEL = 1;

    private RedactionLevel() { }

    public static boolean isValid(int v) { return v == RULE || v == MODEL; }

    public static String name(int v) { return v == MODEL ? "MODEL" : "RULE"; }
}
