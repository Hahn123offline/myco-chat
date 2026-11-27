// 验收 ID：ATT-09 ATT-10 ATT-11 ATT-12 ATT-13 ATT-14 ATT-27 ATT-28（首版容器与三入口）
//
// attachment.panel.sources（首版 = **BottomSheet 三入口**，Lead 裁定 P-ATT-01 / D-03 / D-15）：
//  * 容器 = `base.primitives.bottom-sheet`（M5Sheet：把手 / 标题「添加附件」+ 关闭钮 44 / 内滚 / 回焦）；
//  * 内容 = 三行 **44dp** 来源（拍照 / 相册 / 文件）：图标 20 + gap 12 + 文字 14/20，无底部主按钮；
//  * 点行 → **sheet 先同帧关** → 起对应**系统 picker**（相机 / Photo Picker 多选 / 文件选择器多选）；
//  * 来源**不做事前禁用**（不探测设备能力）：相机不可用/权限拒绝 → 点击后 toast「相机不可用：…」；
//  * 返回落点：不重开 sheet；焦点回接线方传入的 `returnFocus`（composer 的 `+` 或草稿区）；
//  * intake 校验与三态状态机在 `AttachmentState.kt`（本文件只负责"把系统选中的 URI 变成 IncomingAttachment"，
//    限制值由宿主投影后传给状态机；本文件不写任何客户端上限常量，ATT-13）。
//
// `[candidate]`（内联面板 + 最近照片条）单独文件 `AttachmentRecentGrid.kt`，**默认不接入**。
package dev.myco.android.ui.m5.attachment

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.myco.android.ui.m5.M5Icon
import dev.myco.android.ui.m5.M5IconSpec
import dev.myco.android.ui.m5.primitives.M5Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 三入口的本地可见状态（接线方 `rememberAttachmentSheetState()` 持有，`+` 置 open）。 */
@Stable
class AttachmentSheetState {
    var visible: Boolean by mutableStateOf(false)
}

@Composable
fun rememberAttachmentSheetState(): AttachmentSheetState = remember { AttachmentSheetState() }

/**
 * 面板回调。
 *
 * @param onSourcesPicked 系统 picker 返回并已解析元数据的候选（**尚未过 intake**；由宿主调
 *   `ComposerController.addAttachments(sources, limits)` 做整批校验）
 * @param onError toast 文案（相机不可用 / 读取失败 / 不支持文件夹）
 * @param onCameraTempFile 拍照成功后的临时文件（**宿主在处理完成后负责删除**；取消/失败由本组件自删）
 */
data class AttachmentSheetCallbacks(
    val onSourcesPicked: (List<IncomingAttachment>) -> Unit = {},
    val onError: (String) -> Unit = {},
    val onCameraTempFile: (File) -> Unit = {},
)

/** 状态驱动版入口（推荐接线：`+` → `state.visible = true`）。 */
@Composable
fun AttachmentSheetM5(
    state: AttachmentSheetState,
    modifier: Modifier = Modifier,
    returnFocus: FocusRequester? = null,
    callbacks: AttachmentSheetCallbacks = AttachmentSheetCallbacks(),
) {
    AttachmentSheetM5(
        visible = state.visible,
        onDismissRequest = { state.visible = false },
        modifier = modifier,
        returnFocus = returnFocus,
        callbacks = callbacks,
    )
}

/**
 * 首版附件面板（BottomSheet 三入口 → 系统 picker）。
 *
 * ⚠️ **系统 launcher 声明在 `visible` 判断之外**：sheet 先关再起 picker 时组合不能把 launcher 注销。
 */
@Composable
fun AttachmentSheetM5(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    returnFocus: FocusRequester? = null,
    callbacks: AttachmentSheetCallbacks = AttachmentSheetCallbacks(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraTempFile by remember { mutableStateOf<File?>(null) }

    fun deliver(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveSources(context, uris) }
            resolved.error?.let(callbacks.onError)
            if (resolved.sources.isNotEmpty()) callbacks.onSourcesPicked(resolved.sources)
        }
    }

    // —— 拍照（系统相机；临时文件写 cache/camera，FileProvider 只暴露该目录） ——
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraTempFile
        cameraTempFile = null
        if (!success || file == null || !file.exists() || file.length() == 0L) {
            file?.delete() // 取消/失败即删临时文件（ATT-09）
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val source = withContext(Dispatchers.IO) { resolveCameraSource(context, file) }
            if (source != null) {
                callbacks.onCameraTempFile(file)
                callbacks.onSourcesPicked(listOf(source))
            } else {
                file.delete()
                callbacks.onError("相机不可用：读取照片失败")
            }
        }
    }
    // —— 相册（系统 Photo Picker，多选；系统自处理媒体权限） ——
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> deliver(uris) }
    // —— 文件（系统选择器，多选；**无 accept/扩展名白名单**，C-M5-11） ——
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> deliver(uris) }

    fun launchCamera() {
        runCatching {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraTempFile = file
            takePicture.launch(uri)
        }.onFailure {
            cameraTempFile?.delete()
            cameraTempFile = null
            callbacks.onError("相机不可用：${it.message ?: "无法启动相机"}")
        }
    }

    // 关闭 + 回焦（M5Sheet 内部关闭路径会自己回焦；选行主动关时走这里，保证同一个落点）
    val dismissAndReturnFocus: () -> Unit = {
        onDismissRequest()
        if (returnFocus != null) runCatching { returnFocus.requestFocus() }
    }

    M5Sheet(
        visible = visible,
        onDismissRequest = dismissAndReturnFocus,
        title = "添加附件",
        modifier = modifier,
        returnFocus = returnFocus,
        // 3 行内容不滚 → 恒在顶 → 可下滑关（G-13：仅内滚在顶时）
        scrollAtTop = { true },
    ) {
        Text(
            text = "从相册、相机或文件中选择",
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AttachmentSourceRow(
            icon = CameraSourceIcon,
            label = "拍照",
            onClick = {
                // 先同帧关 sheet，再起系统相机（避免返回时 sheet 仍在的竞态）
                dismissAndReturnFocus()
                launchCamera()
            },
        )
        AttachmentSourceRow(
            icon = AlbumSourceIcon,
            label = "相册",
            onClick = {
                dismissAndReturnFocus()
                pickImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        AttachmentSourceRow(
            icon = FileSourceIcon,
            label = "文件",
            onClick = {
                dismissAndReturnFocus()
                pickFiles.launch(arrayOf("*/*"))
            },
        )
    }
}

/** 来源行：整行 44 高（G-16 行档），布局盒 = 命中盒，行间净距 0（不相邻不重叠）。 */
@Composable
private fun AttachmentSourceRow(
    icon: M5IconSpec,
    label: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        M5Icon(icon, Modifier.size(20.dp), colors.onSurface)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

// ---------------------------------------------------------------------------
// 系统 URI → IncomingAttachment（元数据解析；不写死限制）
// ---------------------------------------------------------------------------

private data class ResolvedSources(
    val sources: List<IncomingAttachment>,
    val error: String? = null,
)

private fun resolveSources(context: Context, uris: List<Uri>): ResolvedSources {
    val sources = mutableListOf<IncomingAttachment>()
    var firstError: String? = null
    for (uri in uris) {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (firstError == null) firstError = "不支持文件夹"
            continue
        }
        val meta = readNameAndSize(context, uri)
        if (meta == null) {
            if (firstError == null) firstError = "读取附件失败：$uri"
            continue
        }
        sources += IncomingAttachment(
            localId = UUID.randomUUID().toString(),
            name = meta.first,
            mime = mime ?: "application/octet-stream",
            sizeBytes = meta.second,
            kind = if (mime?.startsWith("image/") == true) AttachmentItemKind.IMAGE else AttachmentItemKind.FILE,
            uri = uri.toString(),
        )
    }
    return ResolvedSources(sources, firstError)
}

private fun resolveCameraSource(context: Context, file: File): IncomingAttachment? {
    if (!file.exists() || file.length() <= 0L) return null
    val mime = runCatching { context.contentResolver.getType(Uri.fromFile(file)) }.getOrNull()
        ?: "image/jpeg"
    return IncomingAttachment(
        localId = UUID.randomUUID().toString(),
        name = file.name,
        mime = mime,
        sizeBytes = file.length(),
        kind = AttachmentItemKind.IMAGE,
        uri = Uri.fromFile(file).toString(),
        temporary = true,
    )
}

/** 读 `OpenableColumns.DISPLAY_NAME/SIZE`；size 缺失时用 AssetFileDescriptor 长度兜底。 */
private fun readNameAndSize(context: Context, uri: Uri): Pair<String, Long>? {
    var name: String? = null
    var size: Long = -1L
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    }
    if (size < 0L) {
        size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: 0L
    }
    val resolvedName = name?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "附件"
    // 读不到内容也允许入队（size=0 → 后端错误兜底）；读失败由上层 toast 呈现
    return resolvedName to size.coerceAtLeast(0L)
}

// ---------------------------------------------------------------------------
// 图标（24dp 网格、1.6dp 线性；M5Icons 未覆盖相机/相册/文件）
// ---------------------------------------------------------------------------

private fun DrawScope.stroke(color: Color) = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val CameraSourceIcon = M5IconSpec("拍照") { color ->
    drawRoundRect(
        color = color,
        topLeft = Offset(3.5f, 7f),
        size = Size(17f, 12f),
        cornerRadius = CornerRadius(2.4f),
        style = stroke(color),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(8.5f, 4.5f),
        size = Size(7f, 3.5f),
        cornerRadius = CornerRadius(1.2f),
        style = stroke(color),
    )
    drawCircle(color, radius = 3.6f, center = Offset(12f, 13f), style = stroke(color))
}

private val AlbumSourceIcon = M5IconSpec("相册") { color ->
    drawRoundRect(
        color = color,
        topLeft = Offset(3.5f, 4.5f),
        size = Size(17f, 15f),
        cornerRadius = CornerRadius(2.4f),
        style = stroke(color),
    )
    drawCircle(color, radius = 2f, center = Offset(9f, 9.5f), style = stroke(color))
    val path = Path().apply {
        moveTo(5.5f, 17.5f)
        lineTo(10.5f, 12.5f)
        lineTo(13.5f, 15.5f)
        lineTo(16f, 13f)
        lineTo(18.5f, 17.5f)
    }
    drawPath(path, color, style = stroke(color))
}

private val FileSourceIcon = M5IconSpec("文件") { color ->
    val path = Path().apply {
        moveTo(6.5f, 3.5f)
        lineTo(14f, 3.5f)
        lineTo(18f, 7.5f)
        lineTo(18f, 20.5f)
        lineTo(6.5f, 20.5f)
        close()
    }
    drawPath(path, color, style = stroke(color))
    drawLine(color, Offset(14f, 3.5f), Offset(14f, 7.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(color, Offset(14f, 7.5f), Offset(18f, 7.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(color, Offset(9f, 12.5f), Offset(15.5f, 12.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(color, Offset(9f, 16f), Offset(15.5f, 16f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

/** 供 `AttachmentRecentGrid`（候选）复用的"系统 picker 三入口"文案口径。 */
internal val AttachmentSourceLabels: List<String> = listOf("拍照", "相册", "文件")
