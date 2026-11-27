// 验收 ID：BP-02 BP-03 BP-04 BP-05 BP-06 BP-07 BP-08 BP-63 BP-73 BP-76
//
// base.primitives.disclosure.row（§3.1，本文件主线）+ disclosure.fold-toggle（§3.2，同族小原子）。
//
// 硬约束：
//  * G-02 折叠行**定高 32 + 尺寸隔离**：`height(32.dp)` + 摘要单行，摘要逐字变化不重排任何兄弟；
//  * G-03 形态 A（inline-uncapped）：展开**不封顶**、随外层会话滚、**没有内层滚动**；
//    头部 sticky 由 G-06 保证 —— 形态 A 的 sticky 必须由**外层滚动容器**提供（LazyColumn 的 stickyHeader），
//    所以本文件同时给 [DisclosureRow]（inline/普通 Column 用）和 [LazyListScope.disclosureStickyRow]（粘头用）；
//  * G-06 展开态头部吸附、背景遮住下方内容（否则多屏内容里"收起"入口不可达）；
//  * G-05 流式摘要单行（静态可 ellipsis；流式应渐隐，见 TODO(lead)）；
//  * G-08 运行态必须有读屏文案（"正在思考"一次）；
//  * G-10 展开/收起**不做高度动画**（>240dp 直接切换；≤240dp 的纵向 spring 是可选，本实现取直接切换）；
//  * D-11=C 的"流式期封顶（形态 D）"由使用方（chat）声明 cap，本原子**不写死** cap。
package dev.myco.android.ui.m5.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 行高（G-02 / G-16 过程行档）：折叠态恒 32dp（最小高，文字增高时由使用方放宽）。 */
internal val DisclosureRowHeight = 32.dp

/** 展开体缩进 = 图标 24 + gap 8（G-09 光学对齐）。 */
internal val DisclosureIndent = 32.dp

/** 横向 Bleed（路径 C）：命中盒 = 行宽 + 2×8，视觉内容贴回正文缘。 */
internal val DisclosureBleed = 8.dp

/**
 * 披露行（形态 A）。
 *
 * 结构：`[icon 24][gap 8][标题 13/20 secondary][chevron 16]`，整行可点（命中盒 = 行宽+2×8 × 32）。
 * 展开：下方 inline 挂载展开体（缩进 32），**随外层滚动**，不做内层滚动。
 *
 * 如果要"展开后头部 sticky"（G-06），用 [LazyListScope.disclosureStickyRow]；
 * 普通 `Column`/`verticalScroll` 里的 [DisclosureRow] 无法把头部钉住（Compose 没有 `position:sticky`
 * 的通用等价物）—— **已知实现缺口**，见回复「待 Lead 裁决」。TODO(lead)
 *
 * @param bleed 横向 Bleed；放在面板 16dp gutter 内时，调用方应把行外 gutter 减到 8，
 *              使视觉缘仍落在 16（命中盒会因此宽出 8）。
 */
@Composable
fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    summary: String? = null,
    running: Boolean = false,
    runningLabel: String = "正在思考",
    bleed: Dp = DisclosureBleed,
    headerBackground: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        DisclosureHeader(
            title = title,
            expanded = expanded,
            onToggle = onToggle,
            icon = icon,
            summary = summary,
            running = running,
            runningLabel = runningLabel,
            bleed = bleed,
            headerBackground = headerBackground,
        )
        if (expanded) {
            Column(Modifier.padding(start = DisclosureIndent, top = 4.dp, end = bleed, bottom = 8.dp)) {
                content()
            }
        }
    }
}

/**
 * 头部行（可独立使用；`stickyHeader` 里就放它）。
 * 命中盒 = 整行宽 × 32；点击任意 x（含 Bleed 8 区间）都切换（BP-03）。
 */
@Composable
fun DisclosureHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    summary: String? = null,
    running: Boolean = false,
    runningLabel: String = "正在思考",
    bleed: Dp = DisclosureBleed,
    headerBackground: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (headerBackground == Color.Unspecified) Color.Transparent else headerBackground
    Box(modifier.fillMaxWidth().background(bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起" else "展开",
                    onClick = onToggle,
                )
                .padding(horizontal = bleed)
                .height(DisclosureRowHeight)
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "已展开" else "已折叠"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            } else {
                Spacer(Modifier.size(24.dp))
            }
            Spacer(Modifier.size(8.dp))
            if (summary == null) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = title,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(8.dp))
                // G-05：流式摘要单行。TODO(lead)：流式期用 alpha 渐隐替代 ellipsis（需要 TextLayoutResult + shader）。
                Text(
                    text = summary,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) {
                // G-08：运行态必须有读屏文案（视觉隐藏的 1dp 节点，出现即播报一次）
                Box(
                    Modifier
                        .size(1.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = runningLabel
                        },
                )
            }
            Spacer(Modifier.size(8.dp))
            Chevron(down = !expanded, tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * LazyColumn 里的披露行：**头部走 `stickyHeader`（G-06 头部吸附）**，展开体是独立 item。
 *
 * 只在 `LazyColumn` 里可用（chat 会话流）。sticky 头必须有不透明底色把下方内容盖住。
 *
 * @param headerBackground sticky 头底色；不传则取 `MaterialTheme.colorScheme.surface`
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.disclosureStickyRow(
    itemKey: Any,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    summary: String? = null,
    running: Boolean = false,
    runningLabel: String = "正在思考",
    bleed: Dp = DisclosureBleed,
    headerBackground: Color = Color.Unspecified,
    bodyKey: Any = "$itemKey-body",
    body: @Composable ColumnScope.() -> Unit,
) {
    stickyHeader(key = itemKey) {
        val bg = if (headerBackground == Color.Unspecified) {
            MaterialTheme.colorScheme.surface
        } else {
            headerBackground
        }
        DisclosureHeader(
            title = title,
            expanded = expanded,
            onToggle = onToggle,
            icon = icon,
            summary = summary,
            running = running,
            runningLabel = runningLabel,
            bleed = bleed,
            headerBackground = bg,
        )
    }
    if (expanded) {
        item(key = bodyKey) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = DisclosureIndent, top = 4.dp, end = bleed, bottom = 8.dp),
            ) {
                body()
            }
        }
    }
}

/**
 * 「还有 N 行」折叠开关（§3.2 / BP-08）：
 * 整行可点、布局盒抬到 36（路径 D）；文案含隐藏行数；`stateDescription` 与文案同步翻转。
 */
@Composable
fun M5FoldToggle(
    hiddenLineCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = if (expanded) "收起" else "还有 $hiddenLineCount 行"
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "收起" else "展开", onClick = onToggle)
            .heightIn(min = 36.dp)
            .padding(horizontal = DisclosureBleed, vertical = 8.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (expanded) "已展开" else "已折叠"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(4.dp))
        Chevron(down = !expanded, tint = colors.onSurfaceVariant, size = 16.dp)
    }
}

/** 常显 chevron（D-09：无 hover 换图标；随开合翻向，瞬时切换无过渡）。 */
@Composable
private fun Chevron(
    down: Boolean,
    tint: Color,
    size: Dp = 16.dp,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            if (down) {
                moveTo(w * 0.22f, h * 0.38f)
                lineTo(w * 0.50f, h * 0.66f)
                lineTo(w * 0.78f, h * 0.38f)
            } else {
                moveTo(w * 0.22f, h * 0.62f)
                lineTo(w * 0.50f, h * 0.34f)
                lineTo(w * 0.78f, h * 0.62f)
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
