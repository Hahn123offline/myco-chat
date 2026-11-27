// 验收 ID：BP-63 BP-74 BP-48 BP-09 BP-10 BP-76（动效契约：G-10 / G-14 / BL-48）
//
// base.primitives 的动效 token 与"哪些不做高度动画"的策略助手。
//
// G-14：转场**一律 spring**，dampingRatio 0.86 / stiffness 420（禁止固定时长 tween 做位移）。
// G-10：240dp 是"超过多少就不许动画"的**上限**；浮层出现消失、chip/tab、设置帮助区、开关轨道、
//       列表项增删、图标切换**一律不做高度动画**；且必须能被系统「移除动画」整批关闭。
//
// 数值来源：`dev.myco.android.ui.m5.M5Motion` 已有同一组值（settle 0.86/420、snap 0.92/900），
// 这里只做**单一真源引用**，不复制第二份数字（G-00b：单一事实源）。
package dev.myco.android.ui.m5.primitives

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import dev.myco.android.ui.m5.M5Motion

/** spring token（G-14 / D-07：0.86 / 420 ≈ 300ms 等效；snap = M5 现有 0.92 / 900）。 */
object M5Spring {

    /** 位移/位移类转场唯一曲线：dampingRatio 0.86 / stiffness 420。 */
    val settle: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = 420f,
        visibilityThreshold = 0.5f,
    )

    /** 短距离收尾/指示器位移：0.92 / 900（M5 既有 snap token）。 */
    val snap: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.92f,
        stiffness = 900f,
        visibilityThreshold = 0.5f,
    )

    /** reduced-motion（系统「移除动画」）→ 瞬切；否则用 settle（BL-48/BP-74）。 */
    fun settleOrSnap(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) snap<Float>() else settle

    /** 与主题 token 的一致性自检（单测/启动断言用；防两处数字漂移）。 */
    @Suppress("UNCHECKED_CAST")
    fun matchesThemeTokens(): Boolean {
        val mine = settle as? SpringSpec<Float> ?: return false
        val theme = M5Motion.settle as? SpringSpec<Float> ?: return false
        return mine.dampingRatio == theme.dampingRatio && mine.stiffness == theme.stiffness
    }
}

/** 动效事件类别（G-10 §2.4 的"不该有动画"清单）。 */
enum class M5MotionKind {
    /** 浮层出现/消失（modal / sheet / menu / toast / lightbox）。 */
    OVERLAY_ENTER_EXIT,

    /** chip / pill / 分段控件出现消失。 */
    CHIP,

    /** 页签/chip 内容切换。 */
    TAB,

    /** `disclosure.fold-toggle` 折叠开关。 */
    FOLD_TOGGLE,

    /** `content-fold` 展开/收起。 */
    CONTENT_FOLD,

    /** 设置字段帮助区展开。 */
    SETTINGS_HELP,

    /** 开关轨道高度。 */
    SWITCH_TRACK,

    /** 列表项插入/删除。 */
    LIST_ITEM_INSERT_REMOVE,

    /** 复制成功等图标切换。 */
    ICON_SWAP,

    /** `disclosure.row` 展开（唯一允许在 ≤240dp 时做高度动画的例外）。 */
    DISCLOSURE_EXPAND,
}

/** 展开动效三选一（G-10 只约束"长内容不许高度动画"，不要求短内容必须有动画）。 */
enum class M5ExpansionMotion {
    /** 直接切换（>240dp 的默认，也用于 reduced-motion）。 */
    DIRECT_SWITCH,

    /** ≤120ms 淡入（可选替代，仍不算高度动画）。 */
    FADE_MAX_120MS,

    /** 纵向 spring（仅 ≤240dp 且仅 disclosure.row 允许）。 */
    HEIGHT_SPRING,
}

/** 动效策略（纯函数，可 JVM 单测）。 */
object M5MotionPolicy {

    /** G-10 阈值：展开后内容高度 > 本值 → 禁止高度动画。 */
    const val EXPAND_ANIMATION_THRESHOLD_DP: Float = 240f

    /** G-10 允许的最大淡入时长（毫秒）。 */
    const val FADE_MAX_MS: Int = 120

    /**
     * 该类事件是否允许**高度/尺寸**动画。
     * 只有 `disclosure.row` 在 ≤240dp 时可做纵向 spring；其它一律 false（G-10 明令 + §2.4 清单）。
     */
    fun allowsHeightAnimation(kind: M5MotionKind): Boolean = kind == M5MotionKind.DISCLOSURE_EXPAND

    /**
     * 展开动效判定：
     *  - reduced-motion → 直接切换（瞬时）；
     *  - 展开后高度 > 240dp → 直接切换（不再做高度动画；可选 ≤120ms 淡入）；
     *  - ≤240dp 且是 disclosure.row → 允许高度 spring。
     */
    fun expansionMotion(
        kind: M5MotionKind,
        expandedHeightDp: Float,
        reducedMotion: Boolean,
    ): M5ExpansionMotion = when {
        reducedMotion -> M5ExpansionMotion.DIRECT_SWITCH
        !allowsHeightAnimation(kind) -> M5ExpansionMotion.DIRECT_SWITCH
        expandedHeightDp > EXPAND_ANIMATION_THRESHOLD_DP -> M5ExpansionMotion.DIRECT_SWITCH
        else -> M5ExpansionMotion.HEIGHT_SPRING
    }

    /** 出现/消失是否允许动画（浮层/chip/tab/列表项一律只有位移或瞬时，不涉及高度）。 */
    fun allowsEnterAnimation(kind: M5MotionKind, reducedMotion: Boolean): Boolean =
        !reducedMotion && kind != M5MotionKind.SWITCH_TRACK
}

/** 系统「移除动画」开关（reduced-motion）。所有过渡都必须能被它整批关掉（G-10 / BL-48）。 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * 读系统动画时长倍率：`animator_duration_scale` 或 `transition_animation_scale` 为 0 → reduced。
 * ⚠️ 只在进入组合时读一次（通过 `remember`）：开发者选项在被改变后需重进页面；
 * TODO(lead)：若要动态跟随，用 LifecycleEventObserver 在 ON_RESUME 重新读（归 base.theme/shell 接线）。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val resolver = context.contentResolver
            val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            animator == 0f || transition == 0f
        }.getOrDefault(false)
    }
}

/** 在子树里提供 reduced-motion（通常由 Lead 的 shell 在根部包一次）。 */
@Composable
fun M5MotionProvider(
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion, content = content)
}

/** 当前 reduced-motion 状态（供各原子统一读，避免各自读系统设置）。 */
@Composable
fun reducedMotion(): Boolean = LocalReducedMotion.current

/** 动效相关的可读断言数据（供 debug 面板/Gallery 展示）。 */
@Immutable
data class M5MotionStatus(
    val reducedMotion: Boolean,
    val settleDampingRatio: Float = 0.86f,
    val settleStiffness: Float = 420f,
    val expandThresholdDp: Float = M5MotionPolicy.EXPAND_ANIMATION_THRESHOLD_DP,
)
