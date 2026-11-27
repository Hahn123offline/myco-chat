// 验收 ID：CHAT-43 CHAT-44 CHAT-45 CHAT-46 CHAT-47 CHAT-48
//
// 轮内状态行（`docs/spec/panels/chat.md` §3.18 `chat.msg.status` / §3.19 `chat.msg.status.retry`）。
//
// 三个子形态：
//  * `turn-error`：错误点 8dp + 「本轮运行失败」+ 分类文案 + `code` 等宽；最小高 36dp（含重试 40dp）；
//    `role=status` 一次性播报；**retriable 只在显式 true 时给重试**（`false`/缺失 → 不给，CHAT-46）；
//  * `turn-max-tokens`：警告点 + 「已达到输出 token 上限」+ 建议，**无「继续」按钮**（core 无 continue，
//    Q-09 死按钮反证 / CHAT-47）；
//  * `interrupted`（被打断）**不是独立行**：正文末行尾追加「已停止」（§3.18 字段 1 / CHAT-43）；
//    本文件只提供 [InterruptedSuffixUi]，由回复 block 末尾内联。
//
// 重试语义（§3.19 / D-18=A）：**以同一 `requestId` 重发**、不新增版本；UI 只保证：
//  * 只在该错误 block 的 `retriable==true` 时渲染（40×40，G-16 密集图标档）；
//  * 点击后进入 loading/禁用，**在同一次在途请求内重复点击零命令**（本地 busy 守卫）；
//  * 真正携带 `requestId` 的命令由上层发（`ChatListCallbacks.onRetryTurn`），`TODO(lead)`：
//    现有投影模型里 `TurnErrorBlock` 没有 `requestId` 字段（core 幂等键已有 = D15，UI 未传），
//    需投影层补 `requestId` 后由上层拼命令。
package dev.myco.android.ui.m5.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.chat.model.TurnErrorBlock

/**
 * 状态行契约（§3.18 / CHAT-43/46/47，**纯数据 / JVM 单测覆盖**）。
 */
object StatusRowContract {
    /** §3.18 字段 2：错误行最小高 36dp（我方取值 P4 校准）。 */
    const val ERROR_MIN_HEIGHT_DP: Float = 36f

    /** §3.18 字段 2：含重试按钮时 40dp（G-16 密集图标档）。 */
    const val ERROR_WITH_RETRY_HEIGHT_DP: Float = 40f

    /** §3.18 字段 2：错误点 / 警告点直径 8dp（我方取值 P4 校准）。 */
    const val DOT_SIZE_DP: Float = 8f

    /** §3.18 字段 1：错误行标题。 */
    const val ERROR_TITLE: String = "本轮运行失败"

    /** §3.18 字段 2：输出上限文案（截断说明）。 */
    const val MAX_TOKENS_TITLE: String = "已达到输出 token 上限，回答被截断"

    /** §3.18 字段 2：输出上限建议（**不提供按钮**，Q-09）。 */
    const val MAX_TOKENS_HINT: String = "发送「继续」可让模型接着输出"

    /** §3.18 字段 1：被打断的正文末行尾标记。 */
    const val INTERRUPTED_LABEL: String = "已停止"

    /** §3.19 字段 2：重试键 40×40。 */
    const val RETRY_SIZE_DP: Float = 40f

    /** §3.19 字段 11：重试键可读名（含"不新增版本"的说明）。 */
    const val RETRY_DESCRIPTION: String = "重试，将重发同一请求，不新增版本"

    /** §3.19 字段 5：重试在途期间的本地防抖窗口（无上层在途信号时的保守近似）。
     *  `TODO(lead)`：上层给出真实 `retrying` 状态后替换本常量。 */
    const val RETRY_GUARD_MS: Long = 2000L

    /**
     * §3.18 字段 10 / CHAT-46：`retriable=false` 或字段缺失 → **不给重试按钮**。
     * 只有显式 `true` 才渲染（保守，不猜）。
     */
    fun showRetry(retriable: Boolean?): Boolean = retriable == true

    /** 行高：无重试 36；有重试 40。 */
    fun errorRowHeightDp(showRetry: Boolean): Float =
        if (showRetry) ERROR_WITH_RETRY_HEIGHT_DP else ERROR_MIN_HEIGHT_DP
}

/**
 * `turn-error` 行（§3.18）。
 *
 * 只读状态行：行本身不可点；唯一交互 = 右侧重试（`retriable=true` 才有）。
 * 失败/停止轮的过程**保持可见**（不渲染控制条）由分组层保证（CHAT-44）。
 */
@Composable
internal fun TurnErrorRowUi(
    block: TurnErrorBlock,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // CHAT-45：同一次在途请求内重复点击零命令（本地防抖守卫）。
    // TODO(lead)：权威的"在途"信号应由上层给（`RetryInFlight(turnId)`）；现在只能用 2s 防抖近似，
    // 因为本层收不到首个事件。block 内容变化/重建时重置，保证"失败后行保留可再点"（§3.19 字段 5）。
    var submitting by remember(block.key, block.text, block.code) { mutableStateOf(false) }
    LaunchedEffect(submitting) {
        if (!submitting) return@LaunchedEffect
        kotlinx.coroutines.delay(StatusRowContract.RETRY_GUARD_MS)
        submitting = false
    }
    val showRetry = StatusRowContract.showRetry(block.retriable)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StatusRowContract.errorRowHeightDp(showRetry).dp)
            // §3.18 字段 11：role=status，一次性播报（不重复循环）。
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = listOfNotNull(
                    StatusRowContract.ERROR_TITLE,
                    block.text.takeIf { it.isNotBlank() },
                    block.code?.takeIf { it.isNotBlank() },
                ).joinToString("，")
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = colors.error)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = StatusRowContract.ERROR_TITLE,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
            if (block.text.isNotBlank()) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (!block.code.isNullOrBlank()) {
                // 错误码等宽（`code: NETWORK_TIMEOUT` 的 code 部分）。
                Text(
                    text = "code: ${block.code}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (showRetry) {
            Spacer(Modifier.width(8.dp))
            RetryKeyUi(submitting = submitting) {
                if (submitting) return@RetryKeyUi
                submitting = true
                onRetry()
            }
        }
    }
}

/** 错误/警告点：8dp 圆，纯装饰（无语义；读屏由行文案承担）。 */
@Composable
private fun StatusDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(StatusRowContract.DOT_SIZE_DP.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * 「重试」键（§3.19）：40×40（G-16 密集图标档）、可读名含"不新增版本"、loading 时 `stateDescription=重试中`。
 */
@Composable
private fun RetryKeyUi(
    submitting: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(StatusRowContract.RETRY_SIZE_DP.dp)
            .clickable(
                enabled = !submitting,
                role = Role.Button,
                onClickLabel = StatusRowContract.RETRY_DESCRIPTION,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = StatusRowContract.RETRY_DESCRIPTION
                if (submitting) {
                    stateDescription = "重试中"
                    disabled()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onSurfaceVariant,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * `turn-max-tokens` 行（§3.18 / CHAT-47）：
 * 警告点 + 文案 + 建议；**无按钮**（不做「继续」，core 无 continue 命令 = 死按钮）。
 */
@Composable
internal fun MaxTokensRowUi() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StatusRowContract.ERROR_MIN_HEIGHT_DP.dp)
            .padding(vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = colors.tertiary)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = StatusRowContract.MAX_TOKENS_TITLE,
                style = MaterialTheme.typography.bodySmall,
                color = colors.tertiary,
            )
            Text(
                text = StatusRowContract.MAX_TOKENS_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * 被打断标记「已停止」：**追加在正文末行尾**（不是独立行，§3.18 字段 1 / CHAT-43）。
 * 由回复 block 在正文之后内联渲染（[TurnBlockUi] 调用）。
 */
@Composable
internal fun InterruptedSuffixUi() {
    Text(
        text = StatusRowContract.INTERRUPTED_LABEL,
        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
