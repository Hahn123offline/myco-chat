package dev.myco.android.ui.m5

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * M5.0 视觉画廊：只用于验证形状/材质/玻璃/光照/图标/动效，不进产品导航。
 * 入口：`adb shell am start -n dev.myco.android/.MainActivity --ez m5_gallery true`
 */
@Composable
fun M5Gallery(dark: Boolean, onToggleDark: () -> Unit, onClose: () -> Unit) {
    M5Theme(dark) {
        val p = LocalM5.current.palette
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.bgTop, p.bg))),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                GalleryHeader(dark = dark, onToggleDark = onToggleDark, onClose = onClose)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { ShapeSection() }
                    item { MaterialSection() }
                    item { GlassSection() }
                    item { LightingSection() }
                    item { IconSection() }
                    item { MotionSection() }
                    item { ComposerSection() }
                    item {
                        Text(
                            "M5.0 · 技术验证画廊 · 用于确认视觉语言，不是最终页面",
                            style = M5Type.caption,
                            color = p.textTertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryHeader(dark: Boolean, onToggleDark: () -> Unit, onClose: () -> Unit) {
    val p = LocalM5.current.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoSurface(
            modifier = Modifier.size(40.dp),
            onClick = onClose,
            radius = 20.dp,
            contentPadding = PaddingValues(0.dp),
        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(M5Icons.back, Modifier.size(19.dp), p.text) } }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("M5 视觉画廊", style = M5Type.title, color = p.text)
            Text("0.1 · 技术验证", style = M5Type.caption, color = p.textTertiary)
        }
        NeoSurface(
            onClick = onToggleDark,
            radius = 18.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) { Text(if (dark) "深色" else "浅色", style = M5Type.label, color = p.text) }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalM5.current.palette
    // 同级节之间留足阴影预算：卡片阴影最多外扩 12dp（LazyColumn 间距 20dp）
    NeoSiblings(M5Space.xl) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(title, style = M5Type.title, color = p.text)
                if (subtitle != null) {
                    Text(subtitle, style = M5Type.caption, color = p.textTertiary, modifier = Modifier.padding(top = 2.dp))
                }
            }
            NeoSurface(
                modifier = Modifier.fillMaxWidth(),
                radius = M5Radius.card,
                contentPadding = PaddingValues(14.dp),
            ) {
                // 组内同级元素（分段键、图标底、工具行）共享半间距阴影预算，互不遮挡
                NeoSiblings(M5Space.xxl) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = M5Type.caption, color = LocalM5.current.palette.textTertiary, modifier = modifier)
}

@Composable
private fun ShapeSection() {
    Section("形状 · Superellipse", "圆角矩形版本的每角都是 90° 超椭圆弧；n 越大越方，n=2 退化为普通圆角") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(10.dp to 56.dp, 18.dp to 56.dp, 26.dp to 56.dp, 36.dp to 56.dp, 52.dp to 56.dp).forEach { (r, size) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NeoSurface(modifier = Modifier.size(size), radius = r, material = NeoMaterial.Raised) {}
                    Spacer(Modifier.height(4.dp))
                    Caption("r=${r.value.roundToInt()}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(2.5f, 3f, 4f, 6f).forEach { n ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NeoSurface(modifier = Modifier.size(64.dp), radius = 28.dp, exponent = n, material = NeoMaterial.Raised) {}
                    Spacer(Modifier.height(4.dp))
                    Caption("n=$n")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Box(Modifier.width(120.dp).height(44.dp).clip(SuperellipseShape(22.dp)).background(LocalM5.current.palette.moss)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("超椭圆胶囊", style = M5Type.label, color = Color(0xFF1B1B16))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Caption("squircle capsule")
            }
            Column {
                // 对照组：普通圆角（只用于比较，新 UI 不出现）
                Box(Modifier.width(120.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(LocalM5.current.palette.moss)) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("普通圆角", style = M5Type.label, color = Color(0xFF1B1B16))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Caption("对照 · RoundedCornerShape")
            }
        }
    }
}

@Composable
private fun MaterialSection() {
    val p = LocalM5.current.palette
    Section("材质 · 新拟物", "投影取面板自身颜色（粉=粉影、绿=橄榄影）；按钮优先凸起；按一下看「凸起按凹 + 超椭圆波纹」") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            NeoSurface(
                onClick = {},
                radius = 22.dp,
                fill = p.moss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    M5Icon(M5Icons.send, Modifier.size(17.dp), Color(0xFF1B1B16))
                    Spacer(Modifier.width(6.dp))
                    Text("发送", style = M5Type.label, color = Color(0xFF1B1B16))
                }
            }
            NeoSurface(
                modifier = Modifier.weight(1f),
                radius = 18.dp,
                material = NeoMaterial.Inset,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    M5Icon(M5Icons.search, Modifier.size(16.dp), p.textTertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("搜索会话或消息", style = M5Type.body, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // 凹陷分段 + 选中态凸起
        NeoSurface(
            modifier = Modifier.fillMaxWidth(),
            radius = 18.dp,
            material = NeoMaterial.Inset,
            contentPadding = PaddingValues(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("跟随", "浅色", "深色").forEachIndexed { i, label ->
                    val selected = i == 0
                    NeoSurface(
                        modifier = Modifier.weight(1f),
                        radius = 14.dp,
                        material = NeoMaterial.Raised,
                        fill = if (selected) p.moss else p.surface,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        onClick = {},
                    ) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            Text(
                                label,
                                style = M5Type.label,
                                color = if (selected) Color(0xFF1B1B16) else p.text,
                            )
                        }
                    }
                }
            }
        }
        // 用户消息气泡（Float）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            NeoSurface(
                modifier = Modifier.widthIn(max = 280.dp),
                radius = 20.dp,
                material = NeoMaterial.Float,
                fill = p.blush,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("今天有点累，但还想把 M5 推起来。", style = M5Type.body, color = Color(0xFF2A1512))
            }
        }
        // 同心圆角：32 → 20 → 12 → 6
        NeoSurface(
            modifier = Modifier.fillMaxWidth(),
            radius = 32.dp,
            material = NeoMaterial.Raised,
            contentPadding = PaddingValues(12.dp),
        ) {
            NeoSurface(
                modifier = Modifier.fillMaxWidth(),
                radius = 20.dp,
                material = NeoMaterial.Raised,
                fill = p.surfaceHigh,
                contentPadding = PaddingValues(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeoSurface(
                        radius = 12.dp,
                        material = NeoMaterial.Inset,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                    ) { Text("控件 12", style = M5Type.label, color = p.textSecondary) }
                    NeoSurface(
                        radius = 6.dp,
                        material = NeoMaterial.Inset,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) { Text("chip 6", style = M5Type.caption, color = p.textSecondary) }
                }
            }
        }
        Caption("同心圆角：32 → 20 → 12 → 6（子半径 = 父半径 − 间距）")
    }
}

@Composable
private fun GlassSection() {
    val p = LocalM5.current.palette
    Section("材质 · 液态玻璃（L0）", "tint + 边缘菲涅尔 + 镜面高光 + 透射；真背景模糊 L1 与 AGSL 折射 L2 待 spike 验证") {
        // 背景：粉绿叠层，用来观察玻璃透射
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(SuperellipseShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(p.mossTint, p.blush, p.cream),
                        start = Offset(0f, 0f),
                        end = Offset(700f, 500f),
                    )
                ),
        ) {
            Box(Modifier.size(120.dp).padding(start = 30.dp, top = 18.dp).clip(SuperellipseShape(60.dp)).background(p.moss.copy(alpha = 0.5f)))
            Box(
                Modifier.size(90.dp).align(Alignment.BottomEnd).padding(16.dp)
                    .clip(SuperellipseShape(45.dp)).background(p.blush.copy(alpha = 0.55f)),
            )
            GlassSurface(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp).fillMaxWidth(),
                radius = 22.dp,
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("液态玻璃", style = M5Type.title, color = p.text)
                    Text(
                        "边缘是逐段衰减的菲涅尔，不是一条灰线；左上有一片镜面高光。",
                        style = M5Type.caption,
                        color = p.textSecondary,
                    )
                }
            }
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            radius = 22.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                M5Icon(M5Icons.search, Modifier.size(17.dp), p.textSecondary)
                Spacer(Modifier.width(10.dp))
                Text("玻璃搜索框（抽屉顶部）", style = M5Type.body, color = p.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassSurface(
                modifier = Modifier.weight(1f),
                radius = 18.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                onClick = {},
            ) { Text("玻璃按钮", style = M5Type.label, color = p.text) }
            NeoSurface(
                onClick = {},
                radius = 18.dp,
                fill = p.moss,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            ) { Text("实心主按钮", style = M5Type.label, color = Color(0xFF1B1B16)) }
        }
    }
}

@Composable
private fun LightingSection() {
    val p = LocalM5.current.palette
    Section("光照 · 边缘与高光", "同一 LightRig 下的强度档位；暗色模式只照亮玻璃，不给普通卡片加发光") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0.4f to "0.4", 0.8f to "0.8", 1.4f to "1.4").forEach { (i, label) ->
                Column(Modifier.weight(1f)) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().height(84.dp),
                        radius = 20.dp,
                        intensity = i,
                        contentPadding = PaddingValues(10.dp),
                    ) { Text("强度", style = M5Type.caption, color = p.textSecondary) }
                    Spacer(Modifier.height(4.dp))
                    Caption(label)
                }
            }
        }
        // 光源方向：左上的高光位置受 rig 影响（此处仅展示当前 rig）
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassSurface(
                modifier = Modifier.weight(1f).height(76.dp),
                radius = 18.dp,
                contentPadding = PaddingValues(10.dp),
            ) { Text("镜面高光 · 左上", style = M5Type.caption, color = p.textSecondary) }
            NeoSurface(
                modifier = Modifier.weight(1f).height(76.dp),
                radius = 18.dp,
                material = NeoMaterial.Raised,
                contentPadding = PaddingValues(10.dp),
            ) { Text("结构面 · 不发光", style = M5Type.caption, color = p.textSecondary) }
        }
    }
}

@Composable
private fun IconSection() {
    val p = LocalM5.current.palette
    val icons: List<Pair<String, M5IconSpec>> = listOf(
        "菜单" to M5Icons.menu,
        "返回" to M5Icons.back,
        "关闭" to M5Icons.close,
        "更多" to M5Icons.more,
        "搜索" to M5Icons.search,
        "新增" to M5Icons.plus,
        "发送" to M5Icons.send,
        "停止" to M5Icons.stop,
        "语音" to M5Icons.mic,
        "完成" to M5Icons.check,
        "展开" to M5Icons.chevronDown,
        "进入" to M5Icons.chevronRight,
        "思考" to M5Icons.clock,
        "搜索网页" to M5Icons.globe,
        "记忆" to M5Icons.spark,
        "设置" to M5Icons.settings,
        "删除" to M5Icons.trash,
        "归档" to M5Icons.archive,
        "复制" to M5Icons.copy,
    )
    Section("图标 · MycoIcons", "24dp 网格 / 1.6dp 线性 / 圆头；绘制时 tint，不再用文字或 emoji 代图标") {
        icons.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { (label, vector) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NeoSurface(
                            modifier = Modifier.size(52.dp),
                            radius = 16.dp,
                            material = NeoMaterial.Raised,
                        ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(vector, Modifier.size(22.dp), p.text) } }
                        Spacer(Modifier.height(4.dp))
                        Caption(label)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.width(52.dp)) }
            }
        }
    }
}

@Composable
private fun MotionSection() {
    val p = LocalM5.current.palette
    Section("动效 · 松手惯性", "拖动跟手 1:1；松手把速度传给 spring，不做定长补间（这是「手停了还顿一下」的修法）") {
        val scope = rememberCoroutineScope()
        var trackWidth by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val chipPx = with(density) { 56.dp.toPx() }
        val padPx = with(density) { 8.dp.toPx() }
        val offset = remember { Animatable(0f) }
        val maxOffset = (trackWidth - chipPx - padPx).coerceAtLeast(0f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(SuperellipseShape(22.dp))
                .background(p.sunken)
                .onSizeChanged { trackWidth = it.width.toFloat() }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset)) }
                    },
                    onDragStopped = { velocity ->
                        val slots = listOf(0f, maxOffset / 2f, maxOffset)
                        val target = when {
                            velocity > 380f -> slots.firstOrNull { it > offset.value + 4f } ?: maxOffset
                            velocity < -380f -> slots.lastOrNull { it < offset.value - 4f } ?: 0f
                            else -> slots.minByOrNull { kotlin.math.abs(it - offset.value) } ?: 0f
                        }
                        scope.launch {
                            offset.animateTo(target, M5Motion.glide, velocity.coerceIn(-4000f, 4000f))
                        }
                    },
                ),
        ) {
            Caption("← 拖动 →", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            NeoSurface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(56.dp)
                    .graphicsLayer(translationX = offset.value),
                radius = 18.dp,
                fill = p.moss,
            ) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("拖", style = M5Type.title, color = Color(0xFF1B1B16)) } }
        }
    }
}

@Composable
private fun ComposerSection() {
    val p = LocalM5.current.palette
    var text by remember { mutableStateOf("") }
    Section("输入区 · 初版样板", "Glass 大圆角浮岛：文本区 + 工具行 + 上下文胶囊，全部无描边") {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            radius = 26.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 输入区是主角：文本区至少 44dp 高，工具行压到 32dp
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = M5Type.body.copy(color = p.text),
                        cursorBrush = SolidColor(p.moss),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text("说点什么…", style = M5Type.body, color = p.textTertiary)
                                }
                                inner()
                            }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeoSurface(
                        modifier = Modifier.size(32.dp),
                        radius = 16.dp,
                        material = NeoMaterial.Raised,
                        onClick = {},
                    ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(M5Icons.plus, Modifier.size(16.dp), p.textSecondary) } }
                    NeoSurface(
                        modifier = Modifier.height(32.dp),
                        radius = 16.dp,
                        material = NeoMaterial.Raised,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                        onClick = {},
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            M5Icon(M5Icons.spark, Modifier.size(13.dp), p.textSecondary)
                            Spacer(Modifier.width(4.dp))
                            Text("DeepSeek", style = M5Type.caption, color = p.textSecondary)
                        }
                    }
                    NeoSurface(
                        modifier = Modifier.height(32.dp),
                        radius = 16.dp,
                        material = NeoMaterial.Raised,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                        onClick = {},
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            M5Icon(M5Icons.clock, Modifier.size(13.dp), p.moss)
                            Spacer(Modifier.width(4.dp))
                            Text("深度思考", style = M5Type.caption, color = p.textSecondary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    NeoSurface(
                        modifier = Modifier.size(32.dp),
                        onClick = {},
                        radius = 16.dp,
                        fill = p.moss,
                    ) { Box(Modifier.fillMaxSize(), Alignment.Center) { M5Icon(M5Icons.send, Modifier.size(16.dp), Color(0xFF1B1B16)) } }
                }
            }
        }
        Caption("文本区 ≥44dp，工具行按钮统一 32dp；发送键主态用 accent.moss 填充")
    }
}
