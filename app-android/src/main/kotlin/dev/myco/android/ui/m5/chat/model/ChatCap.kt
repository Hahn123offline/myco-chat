// CHAT-07, CHAT-50
package dev.myco.android.ui.m5.chat.model

/**
 * 过程组流式封顶换算（`docs/spec/panels/chat.md` §1.6）+ 键盘开合不变性。
 *
 * 规格公式（Compose）：
 * ```
 * cap = min(400.dp, 0.5f * AStable)
 * ```
 * `AStable` = **IME 收起时**聊天列表可视高 = 屏高 − 系统 insets − M2 顶栏高 − composer 高。
 * AStable 的组成由 M2/composer 提供（E-02），本面板不自己算；§1.6 的组成只用于估算表校验
 * （[ChatCapSpec.estimateAStableViewportHeightDp]）。
 *
 * **键盘开合不改变 cap**（§1.6："用 IME 收起的 AStable 计一次，组合期缓存"）——
 * 否则过程组高度突变、内容跳动（G-02 同类症状）。所以 [ChatCap] 一旦由 AStable 构造，
 * [withImeVisibility] 必然返回自身；只有屏幕/insets/顶栏/composer 变化（横竖屏、分屏）才重算，
 * 且重算**不做动画**（§2 状态 13）。
 *
 * 纯 JVM 类型，不依赖 Android/Compose（dp 用 Float 表达）。
 */
data class ChatCap(
    /** IME 收起时的列表可视高（AStable）。 */
    val stableViewportHeightDp: Float,
) {
    /** 组合期算一次（§1.6），之后不再随 IME 变化。 */
    val capDp: Float = ChatCapSpec.capDp(stableViewportHeightDp)

    /**
     * IME 开/合 → cap 不变（§1.6 / Q-05=A）。
     * 保留此 API 是为了让 UI 必须显式走这里，而不是把实时视口高重新塞进公式。
     */
    @Suppress("UNUSED_PARAMETER")
    fun withImeVisibility(imeVisible: Boolean): ChatCap = this

    /**
     * 屏幕尺寸/insets/顶栏/composer 变化（横屏、分屏、composer 模式切换）→ 重算 cap（非动画）。
     * 注意 AStable 仍按 **IME 收起** 口径取（键盘弹着时不要用当前可视高）。
     */
    fun withScreenChange(
        screenHeightDp: Float,
        systemInsetDp: Float,
        topBarDp: Float,
        composerHeightDp: Float,
    ): ChatCap = ChatCap(
        ChatCapSpec.estimateAStableViewportHeightDp(
            screenHeightDp = screenHeightDp,
            systemInsetDp = systemInsetDp,
            topBarDp = topBarDp,
            composerHeightDp = composerHeightDp,
        ),
    )
}

/** §1.6 的公式与估算（常量全部来自规格）。 */
object ChatCapSpec {
    /** 规格上限：`min(400dp, ...)`（DSH `C/ChatGroupSeat.module.css:86-92` E0）。 */
    const val MAX_CAP_DP: Float = 400f

    /** 规格比例：`0.5 × AStable`。 */
    const val VIEWPORT_RATIO: Float = 0.5f

    /** `cap = min(400dp, 0.5 × AStable)`；AStable ≤ 0（异常）时钳到 0，不产生负高度。 */
    fun capDp(stableViewportHeightDp: Float): Float {
        if (stableViewportHeightDp <= 0f) return 0f
        return (VIEWPORT_RATIO * stableViewportHeightDp).coerceAtMost(MAX_CAP_DP)
    }

    /**
     * §1.6 估算用：AStable = 屏高 − 系统 insets − M2 顶栏高 − composer 高（**IME 收起口径**）。
     *
     * 运行时真正的 AStable 由 M2/composer 提供（E-02）；本函数只服务于 §1.6 换算表与单测。
     */
    fun estimateAStableViewportHeightDp(
        screenHeightDp: Float,
        systemInsetDp: Float,
        topBarDp: Float,
        composerHeightDp: Float,
    ): Float = (screenHeightDp - systemInsetDp - topBarDp - composerHeightDp).coerceAtLeast(0f)
}
