// 验收 ID：CMP-25 CMP-26 CMP-27 CMP-28 CMP-29 CMP-30 CMP-31 CMP-32 CMP-33 CMP-34 CMP-35 CMP-36
//          CMP-37 CMP-38 CMP-39 CMP-40 CMP-76；ATT-15/16/17/18/20/21 的**呈现侧**
//
// composer.attach.rail / thumb / file-card / remove / retry（composer.md §3.8–§3.12）：
//  * 轨高 **72** = 项 64 + 上下各 4；项间 10、首尾 8；`LazyRow` 横滚（G-04 到边吸收）；
//  * 空轨**不渲染**（节点不存在，不是 0 高；CMP-26）；新增项自动滚末端（CMP-25）；
//  * 图片项 64×64：**整块 = 命中盒，内部无次级命中区**（G-11：64dp 内再压 ≥36 删除盒必然相交）
//    → 图片的移除走灯箱底部动作（CMP-30，P3-CMP-04 已登记）；
//  * 文件卡 240×64 三态（处理中/就绪/失败）：失败态左侧 196×64 = 重试、右侧 40×40 = 移除，
//    两者净距 4、**不相交**（CMP-34/38/39）；进度条只在有真实 progress 时显示并带 progressbar 语义；
//  * 状态只来自 `attachment.AttachmentDraftItem`（attachment.state.item 的语义真源），本文件不判状态；
//  * 颜色/字号走 MaterialTheme（base.theme 落地前的唯一临时真源；TODO(lead)：切 base.theme token）。
package dev.myco.android.ui.m5.composer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.attachment.AttachmentDraftItem
import dev.myco.android.ui.m5.attachment.AttachmentItemKind
import dev.myco.android.ui.m5.attachment.AttachmentItemState
import dev.myco.android.ui.m5.primitives.LocalOverlayBottomInset
import dev.myco.android.ui.m5.primitives.M5Lightbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 草稿附件轨（空轨**不渲染**）。
 *
 * @param items 状态真源 = attachment 状态机（顺序 = 添加顺序）
 * @param actionsEnabled 只读/提交中 → 移除与重试禁用（CMP-37）
 */
@Composable
fun AttachmentRail(
    items: List<AttachmentDraftItem>,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    onRemove: (attachmentId: String) -> Unit = {},
    onRetry: (attachmentId: String) -> Unit = {},
    onPreview: (attachmentId: String) -> Unit = {},
) {
    // CMP-26：空轨时节点不存在（不是 0 高占位）
    if (items.isEmpty()) return

    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    var previewId by remember { mutableStateOf<String?>(null) }
    var lastCount by remember { mutableIntStateOf(items.size) }
    // 灯箱关闭后回焦触发缩略图（CMP-29：回原轨位置；平台只保证焦点陷阱）
    val previewTriggerFocus = remember { FocusRequester() }

    // CMP-25：新增项自动滚到末端（≤150ms）；已有轨不重置滚动起点
    LaunchedEffect(items.size) {
        if (items.size > lastCount) {
            runCatching { listState.animateScrollToItem(items.lastIndex) }
        }
        lastCount = items.size
    }
    // 预览中该项被移除 → 灯箱自动关闭（§3.9 字段 5）
    LaunchedEffect(items.size, previewId) {
        val id = previewId ?: return@LaunchedEffect
        if (items.none { it.id == id }) previewId = null
    }

    val imageItems = items.filter { it.kind == AttachmentItemKind.IMAGE }
    val imageIndexById = imageItems.mapIndexed { index, item -> item.id to index }.toMap()

    // 轨容器：Group 语义 + 数量播报（§3.8 字段 11）；容器本身不绑点击
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComposerLayout.RAIL_HEIGHT_DP.dp)
            .semantics {
                contentDescription = "草稿附件，共 ${items.size} 个"
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = ComposerLayout.RAIL_HORIZONTAL_PADDING_DP.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(ComposerLayout.RAIL_ITEM_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            // G-04：横向滚到两端吸收，不把残余速度传给竖向列表（原生 LazyRow 行为）
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                if (item.kind == AttachmentItemKind.IMAGE) {
                    val imageIndex = imageIndexById[item.id] ?: 0
                    AttachmentImageItem(
                        item = item,
                        imageIndex = imageIndex,
                        imageCount = imageItems.size,
                        focusRequester = if (previewId == item.id) previewTriggerFocus else null,
                        onClick = {
                            // 处理中点击无效（不打开半成品，§3.9 字段 5）；失败/就绪打开灯箱
                            if (item.state != AttachmentItemState.PROCESSING) {
                                previewId = item.id
                                onPreview(item.id)
                            }
                        },
                    )
                } else {
                    AttachmentFileCardItem(
                        item = item,
                        actionsEnabled = actionsEnabled,
                        onRetry = { onRetry(item.id) },
                        onRemove = { onRemove(item.id) },
                    )
                }
            }
        }

        // 图片移除 = 灯箱底部动作（P3-CMP-04：64dp 内放不下第二个不相交命中盒）
        val previewImageIndex = previewId?.let { imageIndexById[it] }
        if (previewId != null && previewImageIndex != null && imageItems.isNotEmpty()) {
            ImageLightboxWithRemove(
                images = imageItems,
                initialPage = previewImageIndex,
                returnFocus = previewTriggerFocus,
                onDismiss = { previewId = null },
                onRemove = { item ->
                    previewId = null
                    onRemove(item.id)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 图片项（64×64 整块命中）
// ---------------------------------------------------------------------------

@Composable
private fun AttachmentImageItem(
    item: AttachmentDraftItem,
    imageIndex: Int,
    imageCount: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val stateLabel = when (item.state) {
        AttachmentItemState.PROCESSING -> "处理中"
        AttachmentItemState.READY -> "已添加"
        AttachmentItemState.FAILED -> "添加失败"
    }
    Box(
        modifier = Modifier
            .size(ComposerLayout.RAIL_IMAGE_SIZE_DP.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineVariant, shape)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
            // 整块 64×64 = 布局盒 = 命中盒（无内部次级命中区，G-11）
            .clickable(
                enabled = item.state != AttachmentItemState.PROCESSING,
                role = Role.Button,
                onClickLabel = "预览附件图片",
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "附件图片，第 ${imageIndex + 1} 张，共 $imageCount 张"
                stateDescription = stateLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = rememberAttachmentThumbnail(item.uri)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            M5Icon(M5Icons.copy, Modifier.size(20.dp), colors.onSurfaceVariant)
        }

        when (item.state) {
            AttachmentItemState.PROCESSING -> {
                // 处理中：蒙层 + 14dp 转圈（不做假进度）
                Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.35f)))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.inverseOnSurface,
                    strokeWidth = 1.6.dp,
                )
            }
            AttachmentItemState.FAILED -> {
                Box(Modifier.fillMaxSize().background(colors.errorContainer.copy(alpha = 0.55f)))
                M5Icon(M5Icons.refresh, Modifier.size(18.dp), colors.error)
            }
            AttachmentItemState.READY -> Unit
        }
    }
}

/** 图片灯箱 + 底部「移除附件」动作（动作由 composer 提供，灯箱本体归 primitives）。 */
@Composable
private fun ImageLightboxWithRemove(
    images: List<AttachmentDraftItem>,
    initialPage: Int,
    returnFocus: FocusRequester?,
    onDismiss: () -> Unit,
    onRemove: (AttachmentDraftItem) -> Unit,
) {
    val bottomInset = LocalOverlayBottomInset.current
    val colors = MaterialTheme.colorScheme
    M5Lightbox(
        visible = true,
        onDismissRequest = onDismiss,
        pageCount = images.size,
        initialPage = initialPage,
        returnFocus = returnFocus,
    ) { index ->
        val item = images.getOrNull(index) ?: return@M5Lightbox
        Box(Modifier.fillMaxSize()) {
            val bitmap = rememberAttachmentThumbnail(item.uri)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "附件图片，第 ${index + 1} 张，共 ${images.size} 张",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            } else {
                M5Icon(
                    M5Icons.copy,
                    Modifier.align(Alignment.Center).size(48.dp),
                    Color.White.copy(alpha = 0.7f),
                )
            }
            // 底部动作：48 高 × ≥64 宽（G-16 主操作档）；避开展示器自带的"第 N/M"计数
            Text(
                text = "移除附件",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 64.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.scrim.copy(alpha = 0.45f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "移除附件",
                        onClick = { onRemove(item) },
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "移除附件"
                    },
                color = colors.error,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 文件卡（240×64 三态）
// ---------------------------------------------------------------------------

@Composable
private fun AttachmentFileCardItem(
    item: AttachmentDraftItem,
    actionsEnabled: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val failed = item.state == AttachmentItemState.FAILED
    val processing = item.state == AttachmentItemState.PROCESSING
    val canRetry = failed && item.retryable
    val borderColor = when {
        failed -> colors.error
        else -> colors.outlineVariant
    }

    // 卡体本身不可点（§3.10 字段 3）；可点是失败态左侧重试区 + 右上移除
    Box(
        modifier = Modifier
            .size(
                width = ComposerLayout.RAIL_FILE_CARD_WIDTH_DP.dp,
                height = ComposerLayout.RAIL_ITEM_HEIGHT_DP.dp,
            )
            .clip(shape)
            .background(colors.surface)
            .border(if (failed) 1.dp else 0.5.dp, borderColor, shape)
            .semantics {
                contentDescription = "文件 ${item.name}，${fileStateLabel(item)}"
                stateDescription = item.failureReason ?: fileStateLabel(item)
            },
    ) {
        // 正文两行（右侧留 44 给移除命中区）
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 44.dp, top = 8.dp, bottom = 8.dp)
                .alpha(if (processing) 0.65f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                M5Icon(M5Icons.archive, Modifier.size(28.dp), colors.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        failed && canRetry -> "上传失败，点此重试"
                        failed -> "上传失败"
                        else -> item.name
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (failed) colors.error else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        failed -> item.failureReason ?: "无法处理该文件"
                        processing -> "正在处理…"
                        else -> fileMeta(item)
                    },
                    style = FileMetaTextStyle,
                    color = if (failed) colors.error.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 处理中且**有真实 progress** 才显示进度条（没有 total 时不显假进度）
        val progress = item.progress
        if (processing && progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 5.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.surfaceVariant)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = progress.coerceIn(0f, 1f),
                            range = 0f..1f,
                        )
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(colors.primary),
                )
            }
        }

        // 失败态重试区：196×64（卡左），与移除区净距 4（几何见 ComposerGeometry.fileCardLayout）
        if (failed && canRetry) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(ComposerLayout.RAIL_FILE_CARD_WIDTH_DP.dp - 44.dp)
                    .fillMaxHeight()
                    .clickable(
                        enabled = actionsEnabled,
                        role = Role.Button,
                        onClickLabel = "重试 ${item.name}",
                        onClick = onRetry,
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = "重试 ${item.name}"
                        if (!actionsEnabled) {
                            disabled()
                            stateDescription = "正在提交，暂不能重试"
                        }
                    },
            )
        }

        // 移除：40×40 布局盒贴卡右（距右 4），常显（D-09 无 hover）
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(40.dp)
                .alpha(if (actionsEnabled) 1f else 0.38f)
                .clickable(
                    enabled = actionsEnabled,
                    role = Role.Button,
                    onClickLabel = "移除附件 ${item.name}",
                    onClick = onRemove,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "移除附件 ${item.name}"
                    if (!actionsEnabled) {
                        disabled()
                        stateDescription = "正在提交，暂不能移除"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            M5Icon(M5Icons.close, Modifier.size(20.dp), colors.onSurfaceVariant)
        }
    }
}

private val FileMetaTextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)

private fun fileStateLabel(item: AttachmentDraftItem): String = when (item.state) {
    AttachmentItemState.PROCESSING -> "处理中"
    AttachmentItemState.READY -> "已就绪"
    AttachmentItemState.FAILED -> "处理失败"
}

private fun fileMeta(item: AttachmentDraftItem): String {
    val ext = item.name.substringAfterLast('.', missingDelimiterValue = "").uppercase()
    val size = dev.myco.android.ui.m5.attachment.AttachmentIntake.formatBytes(item.sizeBytes)
    return if (ext.isBlank()) size else "$ext · $size"
}

// ---------------------------------------------------------------------------
// 缩略图解码（64dp 只按目标尺寸采样，不整图解码）
// ---------------------------------------------------------------------------

@Composable
private fun rememberAttachmentThumbnail(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { decodeThumbnail(context, uri, targetPx = 160) }
                    .getOrNull()
                    ?.asImageBitmap()
            }
        }
    }
    return bitmap
}

private fun decodeThumbnail(
    context: android.content.Context,
    uri: String,
    targetPx: Int,
): Bitmap? {
    val resolver = context.contentResolver
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while ((bounds.outWidth / sample) > targetPx * 2 && sample < 16) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** 一个极小的空占位（避免上层条件渲染时出现零尺寸测量）。 */
@Composable
internal fun AttachmentRailSpacer() {
    Spacer(Modifier.height(0.dp))
}
