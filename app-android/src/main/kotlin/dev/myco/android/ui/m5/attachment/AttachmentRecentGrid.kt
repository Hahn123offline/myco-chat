// 验收 ID：ATT-01[candidate] ATT-03[candidate] ATT-04[candidate] ATT-05[candidate] ATT-07[candidate]
//          ATT-08[candidate] ATT-27[candidate 部分]
//
// **`[candidate]`（不进首版门禁，默认不接入）**：`attachment.source.grid` 最近照片条
// （attachment.md §3.6 + §1.5 + Lead 裁定 P-ATT-01：内联 + 最近照片整体为候选）。
//
// 形态（用户实拍 APP:13）：4 格等分、gap 4、正方形 `centerCrop`、圆角 12；每格右上 20dp 选中环
// （未选 = onSurface 描边 + 内圈半透明 surface；已选 = primary）；**选中环不是独立命中区**（点环 = 点格）。
//  * 4 张固定、**不横滚**（P-ATT-03 A）；横屏 h<480 整条隐藏（C9）；
//  * 加载 / 权限 / 空 / 失败 **同高替换**（零跳动，G-02 同族）；
//  * 不设客户端数量上限（P-ATT-04）；选中即入草稿轨由宿主回调执行（ATT-04/05）；
//  * 面板与键盘互斥、面板高度发布归候选面板宿主（composer 侧），本文件只管照片条本身。
//
// ⚠️ 媒体权限（READ_MEDIA_IMAGES）**未在 AndroidManifest 声明**（首版不需要：相册走系统 Photo Picker）。
// 候选转正时必须补权限 + 权限请求入口；在此之前 `rememberRecentPhotosState()` 恒为 PermissionRequired/Denied，
// 属预期（页面上不崩、给出引导条），不宣称候选已完成。
package dev.myco.android.ui.m5.attachment

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5Icons
import dev.myco.android.ui.m5.primitives.M5Button
import dev.myco.android.ui.m5.primitives.M5ButtonVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 系统最近照片的一张（候选形态；`uri` 只用于缩略图与入轨）。 */
data class RecentPhoto(
    val id: String,
    val uri: String,
    val displayName: String,
)

/** 最近照片条的六种状态（加载 / 有照片 / 空 / 失败 / 权限未请求 / 权限被拒）。 */
sealed interface RecentPhotosState {
    data object Loading : RecentPhotosState
    data class Loaded(val photos: List<RecentPhoto>) : RecentPhotosState
    data object Empty : RecentPhotosState
    data class Failed(val reason: String) : RecentPhotosState

    /** 从未请求过媒体权限（引导条：允许 / 改用相册）。 */
    data object PermissionRequired : RecentPhotosState

    /** 已拒绝/不再询问（引导条：去设置 / 改用相册）。 */
    data object PermissionDenied : RecentPhotosState
}

/** 候选：最近照片条的高度 = 单格边长（4 格等分）。 */
internal fun recentPhotoCellSize(cardWidth: Dp): Dp =
    ((cardWidth - 32.dp - 12.dp) / 4).coerceAtLeast(48.dp).coerceAtMost(112.dp)

/** 读系统最近 4 张图片（权限已授予时才查询；`enabled=false` 返回 Loading 占位）。 */
@Composable
fun rememberRecentPhotosState(
    enabled: Boolean = true,
    permissionRequested: Boolean = false,
): RecentPhotosState {
    val context = LocalContext.current
    val state by produceState<RecentPhotosState>(
        initialValue = RecentPhotosState.Loading,
        enabled,
        permissionRequested,
    ) {
        if (!enabled) {
            value = RecentPhotosState.Loading
            return@produceState
        }
        if (!hasMediaPermission(context)) {
            value = if (permissionRequested) {
                RecentPhotosState.PermissionDenied
            } else {
                RecentPhotosState.PermissionRequired
            }
            return@produceState
        }
        value = withContext(Dispatchers.IO) { queryRecentPhotos(context, limit = 4) }
    }
    return state
}

private fun hasMediaPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun queryRecentPhotos(context: Context, limit: Int): RecentPhotosState {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
    )
    return runCatching {
        val photos = mutableListOf<RecentPhoto>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext() && photos.size < limit) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                photos += RecentPhoto(
                    id = id.toString(),
                    uri = uri.toString(),
                    displayName = cursor.getString(nameIndex) ?: "image_$id",
                )
            }
        }
        if (photos.isEmpty()) RecentPhotosState.Empty else RecentPhotosState.Loaded(photos)
    }.getOrElse { RecentPhotosState.Failed(it.message ?: "最近照片加载失败") }
}

/**
 * 最近照片条（候选；4 格 + 右上选中环，**点格 = 选中/反选**，环不是独立命中区）。
 *
 * @param selectedIds 已选中照片 id（由草稿轨回填；关闭面板不丢）
 * @param onToggle 选中/反选（宿主：入队/出队 + 环更新）
 * @param onRequestPermission 权限未请求时的「允许」入口（候选转正前为 no-op）
 * @param onOpenSettings 权限被拒时的「去设置」入口
 * @param onUseSystemPicker 「改用相册」→ 系统 Photo Picker
 * @param onRetry 查询失败重试
 */
@Composable
fun AttachmentRecentGrid(
    state: RecentPhotosState,
    selectedIds: Set<String>,
    onToggle: (RecentPhoto) -> Unit,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onUseSystemPicker: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    // C9：横屏 h<480 → 整条隐藏（含权限引导条）
    val configuration = LocalConfiguration.current
    if (configuration.screenHeightDp < 480) return

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val cell = recentPhotoCellSize(maxWidth)
        when (state) {
            is RecentPhotosState.Loaded -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.photos.take(4).forEachIndexed { index, photo ->
                    RecentPhotoCell(
                        photo = photo,
                        index = index,
                        total = state.photos.size,
                        size = cell,
                        selected = photo.id in selectedIds,
                        onClick = { onToggle(photo) },
                    )
                }
            }
            RecentPhotosState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { LoadingCell(cell) }
            }
            RecentPhotosState.PermissionRequired -> PermissionGuideRow(
                height = cell,
                title = "允许访问照片，可直接发送最近截图",
                primaryLabel = "允许",
                onPrimary = onRequestPermission,
                onUseSystemPicker = onUseSystemPicker,
            )
            RecentPhotosState.PermissionDenied -> PermissionGuideRow(
                height = cell,
                title = "照片权限被拒绝",
                primaryLabel = "去设置",
                onPrimary = onOpenSettings,
                onUseSystemPicker = onUseSystemPicker,
            )
            RecentPhotosState.Empty -> StatusRow(height = cell, text = "暂无最近照片")
            is RecentPhotosState.Failed -> StatusRow(
                height = cell,
                text = "最近照片加载失败",
                actionLabel = "重试",
                onAction = onRetry,
            )
        }
    }
}

/** 单格：正方形（边长 = cell）、圆角 12、`centerCrop`；右上 20dp 选中环。 */
@Composable
private fun RecentPhotoCell(
    photo: RecentPhoto,
    index: Int,
    total: Int,
    size: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            // 整格 = 命中盒；环不是独立命中区（G-11）
            .clickable(
                role = Role.Button,
                onClickLabel = if (selected) "取消选择第 ${index + 1} 张照片" else "选择第 ${index + 1} 张照片",
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "第 ${index + 1} 张照片，共 $total 张，${if (selected) "已选中" else "未选中"}"
            },
    ) {
        val bitmap = rememberRecentThumbnail(photo.uri)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(colors.surfaceVariant.copy(alpha = 0.6f)))
        }
        SelectionRing(
            selected = selected,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

/** 选中环：直径 20、描边 2、距右上 6；未选 = onSurface 描边 + 内圈半透明面；已选 = primary。 */
@Composable
private fun SelectionRing(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(20.dp)
            .background(colors.surface.copy(alpha = if (selected) 0.9f else 0.5f), CircleShape)
            .border(2.dp, if (selected) colors.primary else colors.onSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(10.dp).background(colors.primary, CircleShape))
        }
    }
}

@Composable
private fun LoadingCell(size: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.onSurface.copy(alpha = 0.12f)),
    )
}

/** 权限引导条：与照片条**同高替换**（零跳动），含两个 44 高按钮。 */
@Composable
private fun PermissionGuideRow(
    height: Dp,
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onUseSystemPicker: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        M5Icon(M5Icons.search, Modifier.size(18.dp), colors.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                M5Button(
                    text = primaryLabel,
                    variant = M5ButtonVariant.PRIMARY,
                    minWidth = 64.dp,
                    onClick = onPrimary,
                )
            }
        }
        M5Button(
            text = "改用相册",
            variant = M5ButtonVariant.SECONDARY,
            minWidth = 64.dp,
            onClick = onUseSystemPicker,
        )
    }
}

/** 空 / 失败态：同高替换 + 可选 44 高「重试」。 */
@Composable
private fun StatusRow(
    height: Dp,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        if (actionLabel != null && onAction != null) {
            M5Button(
                text = actionLabel,
                variant = M5ButtonVariant.SECONDARY,
                minWidth = 64.dp,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun rememberRecentThumbnail(uri: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                decodeSampled(context, uri, targetPx = 320)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private fun decodeSampled(context: Context, uri: String, targetPx: Int): Bitmap? {
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
