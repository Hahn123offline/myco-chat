package dev.myco.core.llm

import dev.myco.core.spec.Pricing
import dev.myco.core.spec.Usage

/**
 * 用量 → 成本。只在 provider 显式配置 pricing 时计算，否则返回 null（不猜价格）。
 * 公式：未命中输入 + 缓存命中输入 + 输出，各按每 1M token 单价折算。
 */
object UsageCost {
    fun compute(usage: Usage, pricing: Pricing?): Double? {
        pricing ?: return null
        val cached = usage.cachedTokens.coerceIn(0, usage.inputTokens)
        val uncached = usage.inputTokens - cached
        val inputPrice = pricing.inputPerMillion
        val cachedPrice = pricing.cachedInputPerMillion ?: inputPrice
        val outputPrice = pricing.outputPerMillion
        if (inputPrice == null && outputPrice == null) return null
        val total = uncached * (inputPrice ?: 0.0) +
            cached * (cachedPrice ?: 0.0) +
            usage.outputTokens * (outputPrice ?: 0.0)
        return total / 1_000_000.0
    }

    fun apply(usage: Usage, pricing: Pricing?): Usage =
        compute(usage, pricing)?.let { usage.copy(costUsd = it) } ?: usage
}
