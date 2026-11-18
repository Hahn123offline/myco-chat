package com.dsh.host.api.memory;

/**
 * 门控策略：决定一条记忆的 {@link EgressTier}。
 *
 * 实现必须是**纯规则/纯函数**（可热加载配置、不联网、不引模型）：
 * 本地只做门控，把"理解内容"的活留给云端。
 *
 * 强约定：拿不准就返回 GRAY，绝不默认放行。黑名单优先于白名单；
 * 白名单包名里出现敏感词（支付/订单/验证码…）必须降级成 GRAY。
 */
public interface EgressPolicy {

    /**
     * @param pkg       应用包名（可空；空 = 不知道来源，按保守处理）
     * @param activity  Activity 名（可空；当前记忆层不一定有，保留给更细规则）
     * @param title     页面标题（可空）
     * @param maskedText 已脱敏的正文（可空；注意这里已经是脱敏后文本，不是原文）
     */
    EgressTier tierFor(String pkg, String activity, String title, String maskedText);
}
