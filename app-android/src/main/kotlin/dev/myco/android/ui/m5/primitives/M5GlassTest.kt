// 玻璃材质验证入口（**最小验证页**，不是产品页面；Lead 接线用 `M5GlassSheet` / `M5GlassDrawer`）。
//
// 为什么要一个独立 Activity：现有 `MainActivity` / `GlassLab*` 属他人写作用域（本任务禁止改），
// 而玻璃形态必须在**同一 Compose 树**里演示才有意义（Dialog 拿不到背景）。
//
// 启动（模拟器）：
//   adb shell am start -n dev.myco.android/.ui.m5.primitives.M5GlassTestActivity \
//       --ez glass_dark true --es glass_overlay sheet \
//       --ef glass_refract 3 --ef glass_blur 24 --ef glass_band 6 --ef glass_disp 1
//
// 参数（全部可选）：
//   glass_dark            true/false        深/浅主题
//   glass_overlay         sheet|drawer|none 展示哪个玻璃浮层
//   glass_scroll          int               背景列表 initialFirstVisibleItemIndex（证明模糊跟随）
//   glass_scroll_offset   int               背景列表 initialFirstVisibleItemScrollOffset
//   glass_refract/glass_band/glass_blur/glass_disp/glass_rim/glass_shadow/glass_tint
//                                            覆盖 D-73 token（diff 用 refract=0 对照）
package dev.myco.android.ui.m5.primitives

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.myco.android.ui.MycoTheme
import dev.myco.android.ui.ThemeMode
import kotlinx.coroutines.delay

/** 验证页参数（Intent extras → 纯数据；便于截图脚本复现）。 */
data class M5GlassTestArgs(
    val dark: Boolean = true,
    val overlay: String = "sheet",
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val material: GlassMaterial = GlassMaterial.Overlay,
    /** 背景形态：`list`（产品观感）/ `stripes`（高对比竖条纹，用于量测色散位移）。 */
    val background: String = "list",
    /** 光向（屏幕坐标）。默认 `DEFAULT_LIGHT_DIR`；D 验收旋转 90° 用。 */
    val lightDir: Offset = DEFAULT_LIGHT_DIR,
    /** G-21 直通等价验收：true = 不挂 shader，只 blur（与 chain 直通档逐位对比）。 */
    val pureBlur: Boolean = false,
    /** 验收用：不画遮罩、也不把遮罩算进 backdrop（色散位移量测需要干净的背景参照）。 */
    val noScrim: Boolean = false,
) {
    companion object {
        fun from(intent: Intent?): M5GlassTestArgs {
            if (intent == null) return M5GlassTestArgs()
            val base = GlassMaterial.Overlay
            val lightDeg = intent.getFloatExtra("glass_light_deg", 0f)
            return M5GlassTestArgs(
                dark = intent.getBooleanExtra("glass_dark", true),
                overlay = intent.getStringExtra("glass_overlay") ?: "sheet",
                scrollIndex = intent.getIntExtra("glass_scroll", 0),
                scrollOffset = intent.getIntExtra("glass_scroll_offset", 0),
                background = intent.getStringExtra("glass_bg") ?: "list",
                lightDir = if (lightDeg == 0f) DEFAULT_LIGHT_DIR else rotateLightDir(DEFAULT_LIGHT_DIR, lightDeg),
                pureBlur = intent.getBooleanExtra("glass_pureblur", false),
                noScrim = intent.getBooleanExtra("glass_noscrim", false),
                material = base.copy(
                    blur = intent.getFloatExtra("glass_blur", base.blur.value).dp,
                    tint = Color.White.copy(
                        alpha = intent.getFloatExtra(
                            "glass_tint",
                            GlassMaterial.DEFAULT_TINT_ALPHA,
                        ),
                    ),
                    refract = intent.getFloatExtra("glass_refract", base.refract.value).dp,
                    band = intent.getFloatExtra("glass_band", base.band.value).dp,
                    dispersionPx = intent.getFloatExtra("glass_disp", base.dispersionPx),
                    rim = intent.getFloatExtra("glass_rim", base.rim),
                    innerShadow = intent.getFloatExtra("glass_shadow", base.innerShadow),
                ),
            )
        }
    }
}

/**
 * 玻璃材质验证 Activity：背景列表 + 玻璃 sheet / 抽屉，全部在同一 Compose 树内。
 *
 * 只用于截图与 diff 验证；产品接线由 Lead 在 ChatScreen/Shell 里完成（本文件不被产品路径引用）。
 */
class M5GlassTestActivity : ComponentActivity() {
    private val insetsPx = mutableStateOf(InsetsPx(0, 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = M5GlassTestArgs.from(intent)
        // 状态栏/导航栏图标颜色跟随本页主题（本页用 MycoTheme 强制深/浅，不跟随系统）
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (args.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = if (args.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
        )
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            insetsPx.value = InsetsPx(bars.top, bars.bottom)
            windowInsets
        }
        setContent {
            val density = LocalDensity.current
            val i = insetsPx.value
            val topInset = with(density) { i.top.toDp() }
            val bottomInset = with(density) { i.bottom.toDp() }
            M5GlassTestScreen(
                args = args,
                topInset = topInset,
                bottomInset = bottomInset,
            )
            // 诊断落 logcat（报告证据）：`adb logcat -d -s M5Glass`
            LaunchedEffect(Unit) {
                delay(1200)
                Log.i(
                    "M5Glass",
                    "status=${GlassDiagnostics.status} error=${GlassDiagnostics.lastError} " +
                        "sdk=${android.os.Build.VERSION.SDK_INT}",
                )
            }
        }
    }
}

private data class InsetsPx(val top: Int, val bottom: Int)

/** 验证页（可独立预览/截图；不依赖 Activity）。 */
@Composable
fun M5GlassTestScreen(
    args: M5GlassTestArgs,
    topInset: Dp,
    bottomInset: Dp,
) {
    MycoTheme(if (args.dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        CompositionLocalProvider(
            LocalOverlayTopInset provides topInset,
            LocalOverlayBottomInset provides bottomInset,
        ) {
            GlassBackdrop(
                modifier = Modifier.fillMaxSize(),
                background = {
                    if (args.background == "stripes") {
                        StripeBackground(topInset = topInset)
                    } else {
                        DemoBackground(topInset = topInset, args = args)
                    }
                },
                overlay = {
                    when (args.overlay) {
                        "sheet" -> M5GlassSheet(
                            visible = true,
                            onDismissRequest = {},
                            title = "选择模型",
                            material = args.material,
                            lightDir = args.lightDir,
                            debugPureBlur = args.pureBlur,
                            debugNoScrim = args.noScrim,
                            buttons = {
                                M5Button(
                                    text = "取消",
                                    onClick = {},
                                    variant = M5ButtonVariant.TOOLBAR,
                                )
                                M5Button(text = "使用", onClick = {})
                            },
                        ) {
                            ModelOptionRows()
                        }

                        "drawer" -> M5GlassDrawer(
                            visible = true,
                            onDismissRequest = {},
                            material = args.material,
                            lightDir = args.lightDir,
                            debugPureBlur = args.pureBlur,
                            debugNoScrim = args.noScrim,
                        ) {
                            DrawerContent()
                        }

                        else -> Unit
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 演示内容（固定数据：截图/diff 必须逐像素可复现，禁止随机/时间相关元素）
// ---------------------------------------------------------------------------

private data class DemoRow(
    val title: String,
    val snippet: String,
    val tag: String,
    val accent: Color,
)

private val demoRows: List<DemoRow> = listOf(
    DemoRow("新会话", "开始一段新的对话…", "今天", Color(0xFFB0C591)),
    DemoRow("玻璃材质验收", "refract=3dp 的 diff 要边缘-only", "今天", Color(0xFFEFB9B3)),
    DemoRow("输入区重构", "单键三态 + 队列自动出队", "今天", Color(0xFFFFE3DE)),
    DemoRow("附件面板", "拖入、点击、相机三条入口", "昨天", Color(0xFF9CC7E8)),
    DemoRow("模型选择", "DeepSeek V4 / R1 切换", "昨天", Color(0xFFD6B7F0)),
    DemoRow("设置页信息架构", "分组、脏检查、二次确认", "昨天", Color(0xFFF2CFA0)),
    DemoRow("无限抽屉", "栈 + 邻接图 + 预测返回", "本周", Color(0xFFB9D7A8)),
    DemoRow("过程组折叠", "定高 + 兄弟零重排", "本周", Color(0xFFF0AFA8)),
    DemoRow("来源面板", "引用、角标、跳转", "本周", Color(0xFFA9C4E8)),
    DemoRow("图片灯箱", "双击缩放、缩放态禁翻页", "本周", Color(0xFFE8C2A9)),
    DemoRow("思考链展示", "流式遮罩渐隐，不用省略号", "更早", Color(0xFFC0C3E8)),
    DemoRow("消息操作条", "复制、重新生成、编辑", "更早", Color(0xFFABD9C9)),
    DemoRow("会话回收站", "已归档 / 已删除", "更早", Color(0xFFE8B8CE)),
    DemoRow("多会话管理", "置顶、重命名、批量", "更早", Color(0xFFC9D6A5)),
    DemoRow("首次启动", "空态建议卡", "更早", Color(0xFFA8C8D8)),
    DemoRow("长文本排版", "行高、字距、层级", "更早", Color(0xFFE6D2A8)),
) + List(6) { extend ->
    DemoRow(
        "扩展条目 ${extend + 1}",
        "用于验证滚动后玻璃里的背景更新",
        "更早",
        listOf(
            Color(0xFFB0C591),
            Color(0xFFEFB9B3),
            Color(0xFF9CC7E8),
            Color(0xFFD6B7F0),
            Color(0xFFF2CFA0),
            Color(0xFFABD9C9),
        )[extend % 6],
    )
}

@Composable
private fun DemoBackground(topInset: Dp, args: M5GlassTestArgs) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = args.scrollIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = args.scrollOffset.coerceAtLeast(0),
    )
    Column(Modifier.fillMaxSize().background(colors.background)) {
        // 顶栏（常驻元素保持 A 不透明，D-75；这里只是页面标题，不做玻璃）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset, start = 20.dp, end = 20.dp, bottom = 8.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("myco", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
            Spacer(Modifier.weight(1f))
            Text(
                "玻璃材质验证 · API ${android.os.Build.VERSION.SDK_INT}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(demoRows) { row ->
                DemoSessionRow(row)
            }
        }
    }
}

@Composable
private fun DemoSessionRow(row: DemoRow) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(row.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.title.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF1B1B16),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = row.tag,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelOptionRows() {
    val colors = MaterialTheme.colorScheme
    val rows = listOf(
        "DeepSeek V4" to "通用对话 · 256K 上下文",
        "DeepSeek R1" to "深度思考 · 128K 上下文",
        "DeepSeek V4 · 快速" to "低延迟 · 适合短问答",
    )
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (name, desc) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index == 1) colors.primary else colors.outlineVariant,
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DrawerContent() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("myco", style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            Text("+", style = MaterialTheme.typography.headlineMedium, color = colors.primary)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(demoRows.take(10)) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(row.accent),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        row.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }
}

/**
 * 高对比竖条纹背景：**只用于量测色散位移**（R 通道 vs B 通道的采样偏移）。
 * 横竖都放黑/白/纯色条纹，保证每个通道都有陡边可做互相关。
 */
@Composable
private fun StripeBackground(topInset: Dp) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset, start = 20.dp, end = 20.dp, bottom = 8.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("myco", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
            Spacer(Modifier.weight(1f))
            Text(
                "玻璃材质验证 · API ${android.os.Build.VERSION.SDK_INT}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // 高频条纹（8px）：任何 >1px 的采样位移都会改变像素，便于量测 offset(x)
            val stripe = 8f
            var x = 0f
            var i = 0
            while (x < size.width) {
                val color = when (i % 12) {
                    0 -> Color.White
                    1 -> Color.Black
                    2 -> Color.White
                    3 -> Color.Black
                    4 -> Color(0xFFE53935)
                    5 -> Color.Black
                    6 -> Color(0xFF1E88E5)
                    7 -> Color.Black
                    8 -> Color.White
                    9 -> Color(0xFF43A047)
                    10 -> Color.Black
                    else -> Color.White
                }
                drawRect(color, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripe, size.height))
                x += stripe
                i++
            }
        }
    }
}
