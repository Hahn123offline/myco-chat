# 真背景模糊：机制深挖与可落地路径

> 目的：把 Android/Compose 上能做到**真背景模糊（backdrop blur）**的路径逐条挖到底，给出可直接实现的方案与证据；并**解释清楚我们此前 `rememberGraphicsLayer` + `record { drawContent() }` + 兄弟节点 `drawLayer` 为什么画出空**。
>
> 取证日期：2026-11-27。对象：本仓库 `app-android`（Kotlin 2.0.21 / **Compose UI 1.7.5**（BOM 2024.10.01）/ minSdk 26 / compileSdk 35）。
>
> 证据分级：**E0** = 官方文档原文 / AOSP 源码行 / **本栈精确版本（1.7.5）的 androidx 源码行** / 本仓库实测；**E1** = 库作者一方文档、issue、benchmark（非平台方）；**E2** = 推测，**只进 §8**。
>
> 取证方法与落盘位置：本文所有 `文件:行` 引用都可在工作区复现——
> - **compose-ui 1.7.5 源码**：`scratch/raw/compose-1.7.5-src/`（自 Google Maven 下载 `ui-graphics-android-1.7.5-sources.jar` 解包）、`scratch/raw/compose-1.7.5-ui-src/`（`ui-android-1.7.5-sources.jar`）；
> - **Haze 1.1.1 源码**：`scratch/raw/haze111/`（GitHub blob API 逐文件取，tag 1.1.1）；Haze 1.5.4：`scratch/raw/haze154/`；
> - Android/AOSP 文档抓取件：`scratch/raw/*.txt|html`。
>
> 与既有文档的分工：[`android-blur-glass.md`](android-blur-glass.md) 是**能力底稿**（矩阵/门槛/降级）；本文是**机制深挖 + 失败 RCA + 可执行路径**。能力矩阵、液态玻璃四要素、iOS 对照不再重复。

---

## 0. 一页结论

### 0.1 五条路的一句话结论

| 路 | 一句话结论 | 最低门槛 | 成本量级 | 本栈可用 | 推荐 |
|---|---|---|---|---|---|
| **A. 自绘捕获 + RenderEffect**（= Haze 机制） | **能用，而且我们之前的"失败"是 record 重载用错（一行之差），不是引擎能力不足**；修好后不引第三方库、不升 Compose | 捕获：lib 1.7.0（本栈 OK）；**真模糊：API 31 + 硬件加速**；≤30 只能 scrim | 每块玻璃 = 背景多画 1 遍 + 1 次 blur pass | ✅ | **P0 主路径** |
| **B. 窗口级系统模糊** | **能用，但只对"浮动窗口"（Dialog/菜单）有效**；抽屉/BottomSheet 在同一个 Activity window 里，它糊不了窗口内某块区域；OEM 可能不支持/运行时关闭 | API 31 + `windowIsTranslucent` **且** `windowIsFloating` + OEM sysprop | 最低（系统合成器做） | ⚠️ 仅 Dialog | P2 加分项 |
| **C. RenderNode / SurfaceControl / PixelCopy** | **不能用于实时跟随**：Compose 的 `GraphicsLayer` 在 API 29+ 本身就是 `RenderNode`（手写无增益）；公开 `SurfaceControl` 没有模糊 API；PixelCopy 只能做快照（延迟 ≥1 帧 + 拷贝成本） | RenderNode 21+；PixelCopy 24+ | 快照可行、实时不可行 | ❌（除快照） | 不选 |
| **D. WebView 混合** | **不能采样 Compose 内容**：`backdrop-filter` 只在 WebView 自己的 backdrop root（文档树）里取背景，窗口里 WebView 下面的像素不在其中 | — | — | ❌ | 不选（除非整 UI 换 WebView） |
| **E. 优化** | 降采样收益**很小**（Haze 实测：Haze 成本 −5%~−14%）；真正有效的是**共享一次捕获 + 缓存 effect + 只对静止背景重绘** | 与所选路相同 | — | ✅ | 与 A 一起做 |

### 0.2 三句话

1. **根因一句话**：我们调用的是 `GraphicsLayer.record(density, layoutDirection, size, block)` **成员函数**，它不会把外层 `ContentDrawScope` 的 canvas 重定向到层里，于是 `drawContent()` 把背景画到了**窗口 canvas**，层的 display list 一直是空的——所以"直接 drawRect 能被兄弟画出来，drawContent 不行"。
2. **修复一句话**：改成 `DrawScope.record { }` **扩展**（`layer.record(IntSize(w,h)) { this@drawWithContent.drawContent() }`），并把绘制顺序保持"先录源层、玻璃从源层取像素"。Haze 1.1.1 的源码就是这么写的（它对 Compose 1.7.5 编译，见 §2.5）。
3. **产品含义**：按 [`decision-pack.md`](../decision-pack.md) D-54（真背景模糊只用于层叠浮层），路 A 的成本完全可接受——浮层打开时下层静止，源层**不需要每帧重录**。

---

## 1. 我们的失败 RCA（三方对照：Haze / 我们 / 最小片段）

### 1.1 三方代码对照

| 维度 | **Haze 1.1.1（E0 源码）** | **我们（E0：本仓库）** | **最小片段（修复）** |
|---|---|---|---|
| 录制位置 | `ContentDrawScope.draw()`（`DrawModifierNode`） | `Modifier.drawWithContent` 的 `onDraw` | 同 Haze |
| 录制调用 | `contentLayer.record { this@draw.drawContent() }` → **`DrawScope.record` 扩展**，2 个参数 | `layer.record(this, layoutDirection, IntSize(w, h)) { this@drawWithContent.drawContent() }` → **`GraphicsLayer.record` 成员**，4 个参数 | `layer.record(IntSize(w,h)) { this@drawWithContent.drawContent() }`（扩展） |
| `drawContent()` 的作用域 | 外层 `ContentDrawScope`（被扩展重定向后指向层） | 外层 `ContentDrawScope`（**未被重定向，指向窗口**） | 同 Haze（被重定向） |
| 层的 size | 扩展默认 `size.toIntSize()` | 显式传 `IntSize`（值本身没错） | 显式传（可加 outsets） |
| `CompositingStrategy` | 源层不设（Auto）；效果层因 `renderEffect` 自动离屏 | 未设（Auto） | 同 |
| 录制后是否立刻画 | `drawLayer(contentLayer)`（同一次 draw 内） | `drawLayer(layer)`（同一次 draw 内） | 同 |
| 跨节点 `drawLayer` | 不涉及（源层只在自己 draw 里画；效果层从 `HazeState` 拿源层、在**自己**的 draw 里 `drawLayer`） | 兄弟节点 `drawLayer` | 同 |
| 结果 | 正常 | **层空白** | 正常（**待真机验证**，见 §7/§8） |

**关键证据行（全部本机可复现；`GlassLabCapture.kt` 当时未提交、行号以取证快照为准，后续若被改写请按函数名定位）：**

- Haze 1.1.1 源节点：`scratch/raw/haze111/HazeNode.kt:77-102`，核心 4 行：
  ```kotlin
  contentLayer.record {            // 2 参数 → DrawScope.record 扩展
      this@draw.drawContent()
  }
  drawLayer(contentLayer)
  ```
- 我们的代码：`app-android/src/main/kotlin/dev/myco/android/ui/m5/glass/GlassLabCapture.kt:67-78`（`RecordInto`）与 `:155-177`（诊断 B）：
  ```kotlin
  capture.layer.record(this, layoutDirection, IntSize(w, h)) {   // 4 参数 → 成员函数
      this@drawWithContent.drawContent()
  }
  drawLayer(capture.layer)
  ```
- 诊断场景注释（`:84-89`）已经把结论写成了"record+drawLayer 在本机不可用"——**这个结论是误判**，见 §1.4。

### 1.2 机制：两套 `record` 到底差在哪（1.7.5 源码级）

Compose 1.7.5 里 `record` **有两个 API**，名字一样、语义不同：

**(a) 成员函数（我们用的）**——`scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/layer/GraphicsLayer.kt:373-378`

```kotlin
fun record(
    density: Density,
    layoutDirection: LayoutDirection,
    size: IntSize,
    block: DrawScope.() -> Unit
)
```

它的实现（`scratch/raw/compose-1.7.5-src/androidMain/androidx/compose/ui/graphics/layer/AndroidGraphicsLayer.android.kt:418-438`）只是把参数记到层上，然后 `impl.record(density, layoutDirection, this, clipDrawBlock)`。Android 侧（`GraphicsLayerV29.android.kt:235-257`）：

```kotlin
val recordingCanvas = renderNode.beginRecording()
canvasHolder.drawInto(recordingCanvas) {
    canvasDrawScope.drawContext.also {
        it.density = density; it.layoutDirection = layoutDirection
        it.graphicsLayer = layer; it.size = size; it.canvas = this   // ← 只改「层自己的」DrawScope
    }
    canvasDrawScope.block()
}
```

→ **block 的接收者是"层自己的" `DrawScope`**（canvas = `renderNode.beginRecording()`）。你在里面 `drawRect` 会进层；但你若调用**外层**的 `this@drawWithContent.drawContent()`，它用的是**外层** `ContentDrawScope` 的 `drawContext`（window canvas）→ 内容画到屏幕，层里什么都没有。

**(b) 扩展函数（Haze 用的）**——`scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/drawscope/DrawScope.kt:932-956`

```kotlin
fun GraphicsLayer.record(
    size: IntSize = this@DrawScope.size.toIntSize(),
    block: DrawScope.() -> Unit
) = record(
    this@DrawScope, this@DrawScope.layoutDirection, size
) {
    this@DrawScope.draw(                       // ← 关键：重定向「外层」DrawScope
        drawContext.density, drawContext.layoutDirection,
        drawContext.canvas, drawContext.size, drawContext.graphicsLayer,
        block
    )
}
```

而 `DrawScope.draw(density, layoutDirection, canvas, size, graphicsLayer, block)`（同文件 `:301-354`）做的事是：**暂存外层 `drawContext` 的 canvas/size/graphicsLayer → 换成层的 → 执行 block → 恢复**：

```kotlin
val prevCanvas = drawContext.canvas
val prevSize = drawContext.size
val prevLayer = drawContext.graphicsLayer
drawContext.apply { this.canvas = canvas; this.size = size; this.graphicsLayer = graphicsLayer }
canvas.save()
try { this.block() } finally { canvas.restore(); /* 恢复 prev* */ }
```

→ 此时 `this@drawWithContent.drawContent()` 走外层 `ContentDrawScope`，但它的 `drawContext.canvas` **已经指向层的 recording canvas**，所以子内容真的被画进了层。`ContentDrawScope.drawContent()` 的实现就是"用 `drawContext.canvas` 绘子节点"（`scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/drawscope/ContentDrawScope.kt:26-31`；`drawWithContent` 的 `ContentDrawScope` 由 `DrawWithContentModifier` 提供，`scratch/raw/compose-1.7.5-ui-src/commonMain/androidx/compose/ui/draw/DrawModifier.kt:398-424`）。

**一句话**：`record` 的扩展函数自带"canvas 重定向"，成员函数没有。**这就是全部差异。**

**旁证（E0，官方 release note，1.8.0-alpha08）**：
> "Fixed an occasional NPE when using `GraphicsLayer.record { this@ContentDrawScope.drawContent() }`. If you are recording `drawContent()` in this way, make sure to use the **`GraphicsLayer#record` extension function inside `DrawScope`**, and not the member function on `GraphicsLayer`. (I75fc0, b/389046242)"
> —— [compose-ui releases](https://developer.android.com/jetpack/androidx/releases/compose-ui)（本地抓取件 `scratch/raw/compose-ui-releases.txt:12049`）

### 1.3 为什么"三种写法均空"

对照 `GlassLabCapture.kt` 的三个实验：

| 实验 | 写法 | 层里有内容吗 | 为什么 |
|---|---|---|---|
| **A**（能画出绿块） | `direct.record(this, layoutDirection, IntSize) { drawRect(...) }` | ✅ 有 | `drawRect` 的接收者是**层自己的 DrawScope** → 指令进层；跨节点 `drawLayer` 也正常工作。**这一条已经证明"兄弟节点 drawLayer"没问题。** |
| **B**（空白） | `viaDrawContent.record(this, layoutDirection, IntSize) { this@drawWithContent.drawContent() }` | ❌ 空 | 成员函数不重定向外层 canvas，`drawContent()` 画到窗口 → 层 display list 空 |
| **C**（空白） | 整屏背景层用同样写法，再在别处 `scale(0.18) { drawLayer(bg.layer) }` | ❌ 空 | 同 B |

> 注意 B 的节点**自身看着"正常"**（内容确实画出来了，只不过画在窗口上）——这解释了为什么当时会误判成"只有记录节点自画才可见"。

### 1.4 结论纠偏（重要）

| 旧结论（`GlassLabCapture.kt` 注释 / `GlassLabOverview.kt:92`） | 实际（本文 E0） |
|---|---|
| "record+drawLayer 在本机拿不到内容" | **能拿到**；是 `record` 重载用错，层里没录进内容 |
| "只有记录它的那个节点自己画自己能看到内容" | 错。**跨节点 `drawLayer` 完全正常**（实验 A 已证）。B 节点"自画正常"是因为 `drawContent()` 画到了窗口 |
| "跨节点 drawLayer 不可用 → 只能用重绘式 backdrop" | 前半句错。重绘式 backdrop（`GlassLabPanel.kt:46-55` 的 `BackdropPainter`）**不是唯一解**，只是当时的 workaround；它的真实代价是"每块玻璃各自把背景 Composable 再画一遍"，而路 A 是"整屏捕获一次、N 块玻璃复用" |

### 1.5 最小修复 diff（按我们现有代码）

```diff
- // 旧：成员函数（4 参数）
- capture.layer.record(this, layoutDirection, IntSize(w, h)) {
-     this@drawWithContent.drawContent()
- }
+ // 新：DrawScope.record 扩展（2 参数）
+ capture.layer.record(IntSize(w, h)) {
+     this@drawWithContent.drawContent()
+ }
  drawLayer(capture.layer)
```

其它两处同理（`:104-110` 的整屏背景、`:160-164` 的诊断 B）。**不需要**换 `rememberGraphicsLayer()` → `LocalGraphicsContext.createGraphicsLayer()`（Haze 用后者只是因为它写在 `Modifier.Node` 里、不能用 `remember`）；**不需要**升 Compose；**不需要**设 `CompositingStrategy.Offscreen`（挂 `RenderEffect` 会自动离屏，见 §2.7）。

### 1.6 RCA 里仍待验证的点（E2 → §8）

- §1 的结论是**源码级演绎**（E0 级源码 + E0 级 API 语义），但"照 §2 改完在真机出模糊"属于**运行期事实**，本次不允许占模拟器 → 列入 §8 第 1 条，验证脚本已备好（`scratch/blur-mech/RcaProof.kt`）。
- 我们此前的三条失败写法是否还有第二个独立原因（例如 `GlassLabBackdrop` 的 `LaunchedEffect` 里读 `scrollPx` 导致录制时机问题、或 host GPU 切换前后的差异）→ §8 第 2 条。

---

## 2. 路径 A：Haze 最小移植（含可直接运行的骨架）

### 2.1 原理与数据流

```
        ┌────────────────────────── 一帧 ──────────────────────────┐
        │ 1. 背景子树 draw                                         │
        │    record { drawContent() }  ──►  sourceLayer (RenderNode)│
        │    drawLayer(sourceLayer)    ──►  上屏（原样）            │
        │ 2. 玻璃节点 draw（同一帧、更晚）                          │
        │    从 sourceLayer 按自身 root 坐标「裁剪 + 平移」重绘      │
        │      ──►  effectLayer(带 outsets 的离屏层)                │
        │    effectLayer.renderEffect = BlurEffect(radius, Clamp)   │
        │    effectLayer.alpha = …                                  │
        │    drawLayer(effectLayer) ──► 模糊背景                    │
        │    drawContent()          ──► 前景（保持锐利）            │
        └──────────────────────────────────────────────────────────┘
```

- **"跟随滚动"是天然的**：源层在**每一次 draw** 里重录，玻璃每帧从源层取像素，不需要任何额外同步。代价是背景内容每帧被画两遍（上屏一遍、进层一遍）。
- **"背景静止"时可以只录一次**：源不变 → 不需要重录（§6.2）。
- **不能跨 window**：`Dialog`/`Popup` 是独立 window，其内容不在源层所在的 draw 树里（`android-blur-glass.md` §2.5 已论证）。跨 window 只能走 §3。

### 2.2 最低 API 与能力门

| 能力 | 最低版本 | 说明 | 证据 |
|---|---|---|---|
| `GraphicsLayer` / `rememberGraphicsLayer` / `record` / `drawLayer` | compose-ui **1.7.0** | 本栈 1.7.5 ✅ | [compose-ui releases](https://developer.android.com/jetpack/androidx/releases/compose-ui) |
| `GraphicsLayer` 录制底层 | API 23+（29+ 走公开 `android.graphics.RenderNode`；23–28 走 `android.view.RenderNode`/View 层） | 本栈 minSdk 26 → 总能录 | `scratch/raw/compose-1.7.5-src/androidMain/.../GraphicsLayerV29.android.kt:44-51`、`GraphicsLayerV23.android.kt:48-55` |
| **模糊**（`RenderEffect`/`BlurEffect`） | **API 31** + 硬件加速 canvas | 31 以下 `renderEffect` 被忽略（落 scrim） | `GraphicsLayer.kt:340-351` 注释；`android-blur-glass.md` §2.2 |
| 软件渲染 | ❌ | 1.7.0-beta04 起禁用 Software GraphicsLayer | E0 release notes；`android-blur-glass.md` §2.2 |
| 跨 window 采样 | ❌ | 只能同 window 同 draw 树 | §3 |

### 2.3 可直接运行的骨架

**完整代码已写入 [`scratch/blur-mech/MinimalBackdropBlur.kt`](../../../scratch/blur-mech/MinimalBackdropBlur.kt)**（可直接贴进现有实验页）。核心 60 行：

```kotlin
// 1) 状态：一个源层服务 N 块玻璃
class BackdropBlurState {
    internal var sourceLayer: GraphicsLayer? = null
    internal var sourcePosInRoot: Offset = Offset.Zero
}
@Composable fun rememberBackdropBlurState(): BackdropBlurState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropBlurState().also { it.sourceLayer = layer } }
}

// 2) 背景源：录进层 + 上屏
fun Modifier.backdropSource(state: BackdropBlurState) = this
    .onGloballyPositioned { state.sourcePosInRoot = it.positionInRoot() }
    .drawWithContent {
        val w = size.width.roundToInt(); val h = size.height.roundToInt()
        state.sourceLayer?.takeIf { !it.isReleased && w > 0 && h > 0 }?.let { layer ->
            layer.record(IntSize(w, h)) {            // ✅ 扩展！等价 Haze HazeNode.kt:89-94
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
        } ?: drawContent()
    }

// 3) 玻璃：从源层裁一块 → blur → 贴回 → 前景
@Composable fun BlurGlass(
    state: BackdropBlurState, modifier: Modifier = Modifier,
    blurRadius: Dp = 24.dp, tint: Color = Color.White.copy(alpha = .1f),
    inputScale: Float = 1f, shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val effectLayer = rememberGraphicsLayer()
    var posInRoot by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    val s = inputScale.coerceIn(0.2f, 1f)
    val effect: RenderEffect? = remember(radiusPx, s) {
        if (Build.VERSION.SDK_INT >= 31) runCatching {
            androidx.compose.ui.graphics.BlurEffect(radiusPx * s, radiusPx * s, TileMode.Clamp)
        }.getOrNull() else null
    }
    Box(modifier.onGloballyPositioned { posInRoot = it.positionInRoot() }
            .clip(shape).drawWithContent {
        val src = state.sourceLayer
        if (effect == null || src == null || src.isReleased) { drawScrimFallback(tint); drawContent(); return@drawWithContent }
        val w = size.width.roundToInt(); val h = size.height.roundToInt()
        val out = (radiusPx * 1.5f).roundToInt().coerceAtLeast(1)   // 替代 1.12 才有的 LayerOutsets
        effectLayer.record(IntSize(((w + 2*out) * s).roundToInt(), ((h + 2*out) * s).roundToInt())) {
            scale(s, pivot = Offset.Zero) {
                translate(out + (posInRoot.x - state.sourcePosInRoot.x),
                          out + (posInRoot.y - state.sourcePosInRoot.y)) {
                    drawLayer(src)                     // 把「自己背后那块」重绘进来
                }
            }
        }
        effectLayer.renderEffect = effect
        effectLayer.alpha = 0.92f
        scale(1f / s, pivot = Offset.Zero) {
            translate(-out.toFloat(), -out.toFloat()) { drawLayer(effectLayer) }
        }
        drawRect(tint)                                  // 大半径 + 低 tint（D-55 方向）
        drawContent()
    }, content = content)
}
```

在线用法（对应 D-54 的浮层场景）：

```kotlin
val blur = rememberBackdropBlurState()
Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().backdropSource(blur)) { ChatListM5(...) }   // 背景：一次捕获
    AnimatedVisibility(drawerOpen) {                                        // 抽屉：玻璃
        BlurGlass(blur, Modifier.fillMaxHeight().width(300.dp)) { DrawerContent() }
    }
}
```

> 该骨架与 Haze 1.1.1 的 `HazeChildNode.drawEffectWithGraphicsLayer` 同构（`scratch/raw/haze111/HazeChildNode.kt:312-368`）：Haze 多做的是 ① 把层按 `size + blurRadius*2` 膨胀取背景（`:271`）② 用 `clipRect` 只画可见区（`:332-339`）③ 用 `Cache` 复用 `RenderEffect`（`:529,588-599`）④ 一整套 tint/noise/mask 的 effect 图。

**若选择直接用 Haze（可选，不是必须）**：

```kotlin
// 1.1.1 的版本目录就是 jetpack-compose = "1.7.5"、kotlin = "2.0.21"（scratch/raw/haze111/libs.versions.toml）→ 与本栈逐项对齐
dependencies { implementation("dev.chrisbanes.haze:haze:1.1.1") }

val haze = remember { HazeState() }
Box(Modifier.fillMaxSize().haze(haze)) { ChatListM5(...) }                     // 源
Box(Modifier.hazeChild(haze, backgroundColor = Color(0xFF12131A)) {            // 玻璃
    blurRadius = 24.dp
    tints = listOf(HazeTint(Color.White.copy(alpha = 0.10f)))
}) { Composer() }
```
> 1.1.x 的 API 名是 `haze`/`hazeChild`（1.2+ 才改名 `hazeSource`/`hazeEffect`）；`hazeChild` **必须**给不透明 `backgroundColor`（模糊层底下的保底色）。自研骨架与 Haze 的差异只在"边缘膨胀/缓存/着色链"，先把自研跑通再决定是否引入库更划算。

### 2.4 落地到 myco（按 D-54 的范围）

| 界面 | 形态 | 用哪条路 | 备注 |
|---|---|---|---|
| 抽屉（左/右） | in-window 滑入浮层 | **A** | 背景静止，源层只需在打开瞬间录一次（§6.2） |
| BottomSheet / 附件面板 / 灯箱 | in-window 浮层 | **A** | 同上 |
| 行内菜单（`/` 候选、长按菜单） | 现为 Popup 独立 window（D-51） | **A 用不了**；`Popup` 无公开窗口模糊入口 → 只能 **scrim**，或改 in-window overlay | 见 §3.4 |
| 模型/思考强度对话框 | Dialog 独立 window | **B**（若 OEM 支持）或 scrim | 见 §3 |
| 顶栏 / 输入区（常驻） | 滚动内容之上 | **不做真模糊**（D-54） | 用 scrim/tint |

### 2.5 本栈可行性判定

**结论：可行，不需要升版本。** 依据：
1. 1.7.5 有全部所需 API（§2.2），且扩展函数的"canvas 重定向"就在 1.7.5 的 `DrawScope.kt:932-956` 里（E0，本机解包的 sources jar）。**本条同时回答了 [`android-blur-glass.md`](android-blur-glass.md) §6 第 9 条"1.1.1 vs 1.5.4 兼容性"的一半：1.1.1 与本栈逐项对齐；1.5.4 的 Kotlin 2.1.0 元数据兼容性仍未取证，见 §8。**
2. Haze 1.1.1（就是用这套机制）的构建目录写的是 `jetpack-compose = "1.7.5"` + `kotlin = "2.0.21"`（`scratch/raw/haze111/libs.versions.toml`，E0）→ **同版本组合有第三方库在真实发布**，不是理论可行。
3. 我们的失败已定位到用法（§1），不是版本缺陷。

**"若 1.7.5 确实不行"的备选（按现在的证据不需要，但留档）**：
- 升级到 compose-ui 1.8.x+ 没有任何必需的 API；**收益只有 1.8.0-alpha08 的 NPE 修复**（就是 §1.2 那条 release note 对应的 b/389046242）。若真机验证发现 `record {} + drawContent()` 偶发 NPE/空白，升级 1.8.x 即可，影响面 = BOM（2024.10.01 → 2025.xx）+ 无 Kotlin 升级需求（1.8 仍支持 Kotlin 2.0.21；1.9+ 才要 2.1/2.2）。**不要**为了这个功能升到 1.9/1.12。
- workaround：把 `drawContent()` 换成"把背景内容包一层、在 record 块里对**背景 Composable 的引用**再画"不可行（Compose 无此能力）；唯一等价 workaround 是现成的"重绘式 backdrop"（每块玻璃各画一遍），但那是性能更差的方案，不推荐。

### 2.6 性能量级（只用实测数，E1）

Haze 官方 Macrobenchmark（Pixel 6，16 轮，P90 frame duration；`scratch/raw/haze154/docs__performance.md:34-60`）：

| 场景 | Haze 关闭 | Haze 开启 | 差 |
|---|---|---|---|
| Scaffold（顶栏 + 底栏两块玻璃 + 滚动列表） | 7.5 ms | 9.7 ms | **+29%** |
| Images List（每个 item 各自 source+effect） | 6.6 ms | 9.6 ms | +45% |
| Credit Card（可拖动玻璃，状态频繁失效） | 6.6 ms | 13.1 ms | **+98%** |
| Scaffold + `inputScale=0.5` | — | 9.6 ms | Haze 成本 −5% |
| Scaffold + progressive + `inputScale=0.5` | — | 9.4 ms | Haze 成本 −14% |

Haze 2.x 补充（Pixel 8a，`scratch/raw/haze/docs__performance.md:80-123, 163-167, 253-259`，E1）：
- `inputSharing`（多个 effect 共享一次源渲染）把内层源的每帧重放从 7 次降到 1 次：P90 CPU frame −2~6%、mean DrawFrames −6~8%；**每个共享源峰值 GPU 内存 +25–33 MiB**。
- 9 块 Glass 在 Pixel 6：`Balanced` 比 `Quality` 低约 21% CPU frame time（P90）。
- 原生 Backdrop（Android 17）比 Sources **CPU P90 高 23–46%**。

> 对我们（1–3 块浮层玻璃、背景静止）的定量结论需要真机测 → §8 第 5 条。

### 2.7 坑（每条给证据）

1. **`record` 用错重载 → 层空**（本次根因，§1）。
2. **软件渲染不产生任何模糊**：1.7.0-beta04 起禁用 GraphicsLayer 软件渲染（E0 release notes）；Haze 也显式判 `canvas.isHardwareAccelerated`（`scratch/raw/haze154/.../HazeEffectNode.android.kt:26-28`）。→ 模拟器必须开 host GPU；Compose Preview 永远看不到。
3. **半径过大可能直接抛异常**：`createBlurEffect` 在部分 GPU 上对过大半径抛 `IllegalArgumentException`；Haze 专门 try/catch 并提示"该设备不支持这个半径"（`scratch/raw/haze111/RenderEffect.android.kt:60-66`）。→ 必须 try/catch + 降级。
4. **边缘淡出/透明带**：模糊核在边界外没有像素可采。Haze 把层膨胀 `blurRadius*2`（`HazeChildNode.kt:271,318-340`）；本栈没有 1.12 的 `LayerOutsets`，只能像 §2.3 那样手工 `out = 1.5 × radius`。
5. **离屏层隐式裁剪**：`clip=false` 默认，但一旦用了 `renderEffect`/`ColorFilter`/`Offscreen`/非 `SrcOver` 混合，就**强制裁到 bounds**（`GraphicsLayer.kt:330-338`；实现见 `GraphicsLayerV29.android.kt:282-288`）。→ 效果层必须自己画大一圈，别指望 `clip=false`。
6. **每帧重建 `RenderEffect` = 每帧重建效果图**：必须 `remember(参数)` 缓存（Haze 用 `Cache.Builder<RenderEffectParams, RenderEffect>`，`HazeChildNode.kt:529,588-599`）。
7. **API 31 的"陈旧/冻结"历史问题**：Haze issue #77（"Blur frozen on Android API 31"，API 32 正常），Haze 2.1.0 才修（E1）；自绘每帧重录理论不同，但必须在 API 31 真机复验（§8 第 3 条）。
8. **`Modifier.graphicsLayer { renderEffect }` 画不了背景**：它只糊"本层内容"（`android-blur-glass.md` §2.1/§2.2 已论证）。自绘方案里它是"对已重绘的背景层挂效果"才成立，语义完全不同。
9. **绘制顺序/兄弟顺序**：源必须先录（先画）后取；玻璃在源之后。若玻璃在 LazyColumn item 里、源在列表内部，会出现"源还没录到就取"→ 1 帧空 → 闪烁。
10. **`alpha` 别加在内容上**：给 `effectLayer.alpha`（作用于"已模糊的背景"），不是给前景区加 alpha，否则文字也变半透。
11. **层要复用/正确释放**：`rememberGraphicsLayer()` 会随 composition dispose 自动 release（`scratch/raw/compose-1.7.5-ui-src/.../GraphicsLayerScope.kt:236-260`）；自己 `createGraphicsLayer()` 就必须自己 `releaseGraphicsLayer`（Haze 在 `drawEffectWithGraphicsLayer` 里 create+release，`HazeChildNode.kt:316,367`）。
12. **反复 `record` 同一层**是允许的（每次覆盖 display list），但**不要在 `record` 块里 `drawLayer` 这个正在录制的层自己**（递归）。
13. **`Modifier.blur` / `blur {}` 不是同一回事**：`Modifier.blur` 是"糊自己内容"，与 backdrop 无关（官方文档原文见 `android-blur-glass.md` §2.1）。
14. **`RenderEffect` 只能用 compose 类型挂**：`layer.renderEffect` 要 `androidx.compose.ui.graphics.RenderEffect`；要用 `RuntimeShader`（AGSL，API 33+）时需 `android.graphics.RenderEffect.asComposeRenderEffect()`（`scratch/raw/compose-1.7.5-src/androidMain/.../AndroidRenderEffect.android.kt:27`）。
15. **`Dialog`/`Popup` 拿不到窗外背景**（独立 window，§3）。

---

## 3. 路径 B：窗口级系统模糊（含骨架 + 限制）

### 3.1 两个 API 的语义差异（E0）

| API | 语义 | 覆盖范围 | 最低 API | 关键要求 |
|---|---|---|---|---|
| `Window.setBackgroundBlurRadius(int px)` | 半透明窗的**磨砂玻璃背景** | **仅窗口 bounds 内** | 31 | `windowIsTranslucent=true` **且** `windowIsFloating=true`；模糊区域轮廓由 window background drawable 决定（`ShapeDrawable` 给圆角） |
| `FLAG_BLUR_BEHIND` + `LayoutParams.setBlurBehindRadius(px)` | 模糊窗口背后的**整屏**（景深） | 全屏 | 31（flag 从 API 1 存在但无实现） | 同样的透明窗前提；可与前者组合 |

原文（E0）：
- "For the blur region to be visible, the window has to be translucent (see `R.attr.windowIsTranslucent`) **and floating** (see `R.attr.windowIsFloating`)." —— [`Window#setBackgroundBlurRadius`](https://developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int))（本地 `scratch/raw/ref-window.txt:14068-14070`）
- "Some devices might not support cross-window blur due to GPU limitations. It can also be disabled at runtime, e.g. during battery saving mode, when multimedia tunneling is used or when minimal post processing is requested. … no blur will be computed or drawn, resulting in a transparent window background." —— 同上 `:14076-14081`
- "Cross-window blurring is an expensive operation, so it's supported only on selected devices." / "For blur behind, a radius of 20 px creates a good depth of field effect, while a background blur radius of 80 px creates a good frosted glass effect. **Avoid blur radii higher than 150 px**." / 设备需 `ro.surface_flinger.supports_background_blur=1` / 验证开关：开发者选项 "Allow window-level blurs"、`adb shell wm disable-blur` —— [AOSP: Window blurs](https://source.android.com/docs/core/display/window-blurs)（本地 `scratch/raw/aosp-window-blurs.txt:586-943`）

### 3.2 能用于哪些界面（关键判定）

| 形态 | 是不是独立 window | 能否用 B | 说明 |
|---|---|---|---|
| Compose `Dialog` | ✅（`DialogWindowProvider.window`） | ✅ | 最适配；主题需 floating+translucent |
| Activity 全屏 | ✅（但**不是 floating**） | ⚠️ 仅 `FLAG_BLUR_BEHIND`（整屏景深）可用；`setBackgroundBlurRadius` 不可靠 | 全屏窗不是 floating → 背景模糊要么画满整屏、要么不生效（AOSP 故障排查："Background blur drawn fullscreen, not within the window bounds → Check `android:windowIsFloating`"），且仍需 translucent 主题才看得见 |
| Compose `Popup` / `PopupWindow` | ✅（独立 window） | ⚠️ 未取证 | `PopupWindow` 没有公开的 `Window` 句柄/模糊入口；Compose `Popup` 也没暴露。→ §8 |
| 抽屉 / BottomSheet / 灯箱（in-window） | ❌ | ❌ | 同 window，只能糊"整个窗口背后"，糊不了窗口内某块区域 |
| 把抽屉改成独立 `Dialog`（全屏、透明、无 dim、无动画） | 变成 ✅ | 可行 | 但要付下面 3.4 的窗口代价（焦点/返回/insets/命中/动画） |

### 3.3 骨架

完整代码：[`scratch/blur-mech/PathBWindowBlur.kt`](../../../scratch/blur-mech/PathBWindowBlur.kt)。要点（Compose 1.7.5 只能手动设，`DialogProperties` 的这些字段是 1.13 才有）：

```kotlin
Dialog(onDismissRequest = onDismiss) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= 31) {
            window.setBackgroundBlurRadius(80)                       // px，不是 dp
            val attrs = window.attributes
            attrs.setBlurBehindRadius(20)
            attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            window.attributes = attrs
        }
        onDispose { window?.setBackgroundBlurRadius(0) }
    }
    ...
}
```

主题（缺一项就会"调了 API 但看不见"）：

```xml
<style name="Theme.Myco.BlurryDialog" parent="Theme.Material3.DayNight.Dialog">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowIsFloating">true</item>
    <item name="android:windowBackground">@drawable/window_blur_bg</item>
</style>
<!-- window_blur_bg.xml: <shape><corners android:radius="28dp"/><solid android:color="#B814151C"/></shape> -->
```

### 3.4 把抽屉/sheet 做成独立窗口的代价（若真要）

- **焦点与 IME**：Dialog 窗口默认抢焦点；抽屉里的输入框、IME insets、`BackHandler` 都要重新接线（Compose Dialog 的返回键走 `onDismissRequest`）。
- **命中测试**：Dialog 窗口外的点按由它自己处理（`FLAG_NOT_TOUCH_MODAL` 之类），要手写"点外面关闭 + 事件穿透"才不丢交互。
- **动画**：窗口动画与 Compose 内动画不同步，滑入滑出容易"先白一帧/先糊一帧"。
- **insets**：`WindowInsets` 在独立窗口里是另一套值，状态栏/导航栏避让要重算。
- **可访问性/返回栈**：多一层 window 就多一层语义边界，TalkBack 焦点顺序会变。
- **结论**：**不建议**为抽屉/sheet 换独立窗口；只有"本来就是 Dialog"的界面（模型选择、思考强度）才顺带用 B。

### 3.5 性能与半径（E0）

- 系统合成器路径，app 侧几乎零成本；但它会让**整个窗口**（或整屏）参与模糊 → 半径越大，SurfaceFlinger/GPU 采样面积越大。
- 建议：blur behind 20 px / background blur 80 px；**>150 px 显著掉性能**（AOSP 原文）。
- 运行时可能被关（省电、视频隧道模式、开发者覆盖、TV 播放视频）。

### 3.6 静默失效的检测与降级（必做）

```kotlin
// ① 系统当前是否允许跨窗口模糊（API 31+）
val enabledNow = wm.isCrossWindowBlurEnabled
// ② 运行时变化
wm.addCrossWindowBlurEnabledListener(Consumer { enabled -> updateWindowForBlurs(enabled) })
// ③ 设备是否支持（无公开 API）：
//    adb shell getprop ro.surface_flinger.supports_background_blur  → 1
//    开发者选项里是否存在 "Allow window-level blurs"
```

降级两套外观（AOSP 示例给的数字，E0）：
- 模糊开：window background alpha 170（≈0.67）、dim 0.1；
- 模糊关：background alpha 255、dim 0.4。
否则"文字直接压在一起不可读"。

### 3.7 结论

**路 B 只作为"Dialog 类界面"的加分项**：性能最优、实现最省，但覆盖面窄（不能给抽屉/sheet 用）、依赖 OEM、会静默失效。**产品上必须与路 A 的 scrim 兜底共存**，且需要真机清单（§8 第 4 条）。

---

## 4. 路径 C：RenderNode / SurfaceControl / PixelCopy

### 4.1 RenderNode 能做什么、不能做什么

- **能**：录制**自己的**绘制指令（`beginRecording/endRecording`）、挂 `setRenderEffect`（API 31）、被 `canvas.drawRenderNode` 画到别处。
- **不能**：**采样别的节点/别人的内容**。`RenderNode` 的效果输入只有它自己的 display list（`RenderEffect` 官方注释："passes the contents of the RenderNode that this RenderEffect is installed on as an input to the shader"）。想"拿到背景"只有两条：① 重绘（=路 A）② 读回屏幕像素（PixelCopy，§4.4）。
- **拿不到 Compose 根节点的 RenderNode**：Compose 不暴露内部节点，`AndroidComposeView` 的 RenderNode 也不在公开 API 里 → 手写 RenderNode **没有任何路 A 做不到的能力**。

**关键事实（E0，本栈源码）**：Compose 的 `GraphicsLayer` 在 **API 29+ 就是 `android.graphics.RenderNode`**：
`scratch/raw/compose-1.7.5-src/androidMain/androidx/compose/ui/graphics/layer/GraphicsLayerV29.android.kt:44-51`（"GraphicsLayer implementation for Android Q+ that uses the public RenderNode API"，`private val renderNode: RenderNode = RenderNode("graphicsLayer")`），record/draw/effect 分别映射到 `beginRecording/endRecording`、`drawRenderNode`、`renderNode.setRenderEffect`（`:235-261, 291-297`）；API 23–28 才用 `android.view.RenderNode`/View 层（`GraphicsLayerV23.android.kt:48-55`）。

→ **结论：手写 `RenderNode` 是本栈已有能力的子集，路径 C 的"RenderNode 分支"没有独立价值。**

### 4.2 SurfaceControl / SurfaceFlinger

- app 侧公开 `SurfaceControl` API 里**没有**背景模糊方法：公开 reference 页（`scratch/raw/ref-surfacecontrol.txt`）grep 不到 blur / background blur 条目；页面里出现的 "Blur" 只是 `BlurMaskFilter`（Canvas 的遮罩模糊，不是背景）。
- 窗口模糊是 **system_server + SurfaceFlinger** 的能力（`BlurFilter.cpp` 在 render engine 里，`ro.surface_flinger.supports_background_blur` 门控），app **只能通过 Window API 间接使用**（§3）。AOSP 原文（E0）："If you have a customized render engine, ensure that your render engine implements the blurring logic. The default Android 12 render engine implements the blurring logic in `BlurFilter.cpp`."（`scratch/raw/aosp-window-blurs.txt:846-864`）
- 因此：**不存在"app 自己用 SurfaceControl 糊任意区域"的公开路径**（内部 `Transaction` 上的 blur 方法是 `@hide`/system API，需系统签名）。

### 4.3 新变量：Android 17（API 37.2）出现了**公开的** backdrop effect

这是本次调研最重要的"未来路径"发现（E0，官方 reference）：

- `View.setBackdropRenderEffect(RenderEffect)` —— "Configure the RenderEffect to apply to the backdrop contents of this View. … if `RenderEffect.createBlurEffect(…, RenderEffect, Shader.TileMode)` is provided, **the previous content behind this View will be blurred before this View is drawn**." **Added in version 37.2**（`scratch/raw/ref-view.txt:35110-35127`）
- `RenderNode.setBackdropRenderEffect(RenderEffect)` 同名同语义（.NET binding 文档："the previous content behind this RenderNode will be blurred before the RenderNode is drawn into the destination"，`net-android-37.0`；E1，§8 第 8 条复核）
- Haze 2.x 的 `HazeInput.Backdrop` native 路径属于同一类平台级 backdrop（文档描述与上述 API 语义一致，具体绑定未取证，E1）：需要 **Android 17（full SDK 37.2）+ 硬件加速窗口 + 实验开关**，且"**can't select individual sources, include content drawn later, or cross a dialog, popup, or window boundary**"（`scratch/raw/haze/docs__core-concepts.md`，E1）。

**对本栈的意义**：`compileSdk = 35` → 编译期用不了；要升到 compileSdk 37.2 + 目标设备是 Android 17 才有。而 DSH 的 CSS backdrop-filter 我们不可能为它把 minSdk/compileSdk 抬到 37.2 且放弃 26–36 设备 → **不作为本代方案**，只登记为"未来版本显式换实现"（§8 第 8 条）。

### 4.4 PixelCopy：延迟与抖动

- `PixelCopy`：API 24 类；`PixelCopy.request(Surface, Rect, Bitmap, OnPixelCopyFinishedListener, Handler)`（`PixelCopy.Request`/`Builder` 是 API 34 新形状）（E0：`scratch/raw/pixelcopy.html` 与 [reference](https://developer.android.com/reference/android/view/PixelCopy)）。
- 语义：**异步**把 surface 内容拷到 `Bitmap`，完成回调发生在之后的帧边界（E1）；整屏 1080×2400 RGBA ≈ 10 MB/次，GPU→CPU 回读会 stall。
- 因此：**不能跟随滚动**（至少 1–2 帧延迟 + 抖动 + 拷贝成本）；只能做**静态快照**。
- 快照不是没有用：按 D-54（浮层打开时下层静止），"打开浮层时抓一帧 → CPU/GPU 模糊 → 整个浮层生命周期复用"是**可接受的降级手段**（≤30 设备上想要"真模糊"时的兜底）。但它是"陈旧模糊"，背景里有视频/动画时会拖影（§6.2）。
- secure window（`FLAG_SECURE`）抓不到 → 需要透明度兜底（E1）。

### 4.5 结论

| 问题 | 结论 |
|---|---|
| 能不能实时跟随滚动？ | **不能**（PixelCopy 异步/拷贝；SurfaceControl 无公开 API；RenderNode 只能重绘自己的内容） |
| 有没有比路 A 更省的同窗口方案？ | **没有**（`GraphicsLayer` 就是 RenderNode；路 A 已经是"少画一遍"的下限） |
| 有没有别的窗口内容可用的方案？ | 只有窗口级（§3）与 Android 17 新 API（不可用） |
| PixelCopy 用在哪？ | 仅"打开浮层时抓一帧"的静态快照兜底（≤30 设备 / 极端低端机） |

---

## 5. 路径 D：WebView 混合（结论 + 证据）

### 5.1 结论

**在 Compose 上叠透明 WebView，`backdrop-filter` 采不到 Compose 的内容。** 原因不是"Android 不支持"，而是 **CSS `backdrop-filter` 的语义边界**：它只能采样**同一个 backdrop root（文档树内、元素之下）**已经绘制的内容；WebView 下面的 Android 像素根本不在这个文档里。

### 5.2 证据

1. **规范定义（E0，CSS Filter Effects Level 2）**：
   > "A Backdrop Root is formed, anywhere in the document, by an element in any of the following scenarios. … **The root element of the document (HTML)**. … An element with a `backdrop-filter` value other than none. …"
   > "Obvious triggers: The root element. **There is nothing above the root element**, so it must form a Backdrop Root."
   —— [drafts.csswg.org/filter-effects-2 §3/§3.2](https://drafts.csswg.org/filter-effects-2/#BackdropRoot)（本地 `scratch/raw/css-filter-effects-2.html`）
   → WebView 加载的页面里，`backdrop-filter` 元素的 backdrop 上限就是**页面根元素**；页面根之下**没有** Android 窗口像素这层"内容"。
2. **实现边界（E1，MDN 的 backdrop root 说明 + Chromium 行为）**：
   > "This means that if a parent element has `opacity: 0.9`, it becomes a backdrop root and any child's `backdrop-filter` will only blur the content between that parent and the child - **not the content behind the parent**."
   —— [MDN backdrop-filter](https://developer.mozilla.org/en-US/docs/Web/CSS/backdrop-filter)
   Chromium 只能在自己的合成树里回读"已经画过的层"（backdrop root 内），无法读 Android 窗口的目标 framebuffer。
3. **平台视图与宿主渲染树隔离（E1，社区一手问题）**：原生 WebView 是独立于宿主渲染树的层级——Flutter 的 `BackdropFilter` 无法穿透并糊到原生 WebView 的内容，反过来 `RenderEffect` 也只会作用于"它挂的那个层自己的内容"（§2 的机制再次印证）。所以"WebView 与 Compose 互相采样对方像素"在两边都不成立。
   —— [WebViewX 上 BackdropFilter 无模糊效果](https://www.volcengine.com/article/2268272)
4. **本仓库已确认的前提**（团队前置结论）：DSH 的模糊来自 WebView 内 Chromium 合成器的 `backdrop-filter`，Compose 没有等价物 → 那条能力**随"UI 在 WebView 里"而存在**，不能抽出来叠到 Compose 上。

### 5.3 那"把整个 UI 放 WebView"可行吗？

| 维度 | 评估 |
|---|---|
| 技术可行性 | ✅ 可行（DSH 手机端就是这么做的；Chromium 合成器里 backdrop-filter 是真能力） |
| 对 myco 意味着 | 重写整个 UI 技术栈：现有 8 份面板规格（`docs/spec/panels/*`）、Compose 动画/手势/无障碍/测试体系全部推倒；Kotlin 只做壳与桥接 |
| 性能 | 与现在同量级或更好（Chromium 合成器做 blur，不占 app 主线程）；但 WebView 首帧/内存成本更高 |
| 折中方案 | **"只把玻璃面板放 WebView"无效**（它采不到背景）；"把背景也放进这个 WebView"= 全屏 WebView = 上面那条 |
| 结论 | **不选**。除非产品决定"整个 UI 迁 Web"，否则这条路对本项目没有中间态 |

### 5.4 验证方法（若有人坚持要证据）

一个最小对照（10 分钟）：Compose 全屏彩色棋盘 → 上面叠 `AndroidView { WebView }`（透明背景）→ 页面里一个 `div { backdrop-filter: blur(24px) }`。
- 预期：div 里**什么都糊不到**（只有页面内自己画的东西能被糊）；
- 对照组：div 下面放一张 CSS 图片 → 能糊。
- 判据：前者无变化、后者有效 ⇒ 结论成立。→ §8 第 7 条。

---

## 6. 路径 E：优化清单（带宽/频率/共享/降级）

> 前提：这些优化只在**路 A**上有意义（B 是系统做的，C/D 不选）。

### 6.1 降采样（1/4、1/8 再放大）

**怎么做**（Haze 同款）：把"取背景"的层按 `inputScale` 缩小录制，`renderEffect` 的半径按 `radiusPx × inputScale` 传，绘制时再 `scale(1/inputScale)` 放大。

```kotlin
val s = 0.5f
effectLayer.record(IntSize((w * s).toInt(), (h * s).toInt())) {
    scale(s) { drawLayer(sourceLayer) }
}
effectLayer.renderEffect = BlurEffect(radiusPx * s, radiusPx * s, TileMode.Clamp)
scale(1f / s) { drawLayer(effectLayer) }
```

**Haze 的默认值（E0 源码，`scratch/raw/haze111/HazeChildNode.kt:548-562`）**：
```kotlin
HazeInputScale.Auto -> when {
    blurRadius < 7.dp -> 1f          // 小半径降采样太明显，关掉
    progressive != null -> 0.5f
    mask != null -> 0.5f
    else -> 0.3334f                  // 普通均匀模糊默认 1/3
}
```

**收益（实测，E1）**：`inputScale=0.5` 只降 **Haze 成本** 5%（普通）/14%（progressive），折到总帧时长只有 **3–5%**（`scratch/raw/haze154/docs__performance.md:9-12,46-60`）。
**画质损失**：硬边/文字会先糊成雾（我们背景是聊天正文 → 反正要糊，可接受）；但 1/8 会让 40px 大半径出现块状。
**建议**：**保持 1f，先不要降采样**；只有真机实测帧时间超标时才降到 0.5（不要 1/3——收益不值）。

### 6.2 频率：每帧 vs 隔帧 vs 停止后更新

| 策略 | 适用 | 代价 | 风险 |
|---|---|---|---|
| **每次 draw 重录**（Haze 默认） | 背景会动（滚动/动画） | 背景多画 1 遍/帧 | 无 |
| **只在源失效时重录**（`shouldAutoInvalidate=false` + 手动失效，Haze 的 `HazeSourceNode` 就是这么做的） | 背景静止（= D-54 的浮层场景） | 几乎为 0 | 源真变了却没失效 → 陈旧 |
| **打开浮层时抓一次快照**（源层录一次、浮层生命周期内不更新） | 浮层打开、下层静止且无视频 | 1 次重绘 | 背景里有视频/动画 → 拖影（"陈旧模糊"） |
| **停止滚动 N ms 后更新**（滚动中显示旧模糊） | 滚动内容之上的常驻玻璃（D-54 已排除） | 低频 | 滚动时模糊固定不动，观感错位 |

**落地建议**：
- 抽屉/ sheet /灯箱：**进入时录一次 + 关闭时恢复**（可用 `Snapshot`/条件 `drawWithContent` 控制），这能把路 A 的成本压到"打开一次 ≈ 2 次重绘"；
- 若浮层打开动画期间需要模糊跟随，就在动画期间每帧录（动画 200–300ms，只贵这一段）。

### 6.3 多块玻璃共享一次背景捕获

- 路 A 天然支持：`BackdropBlurState` 只持有一个源层，**N 块玻璃各自只做"裁剪 + 自己那次 blur pass"**（每块仍需一个 effect layer + 一次 RenderEffect 采样）。
- Haze 1.x 的 `HazeState.contentLayer` 就是单例源层；Haze 2.x 的 `inputSharing` 把这个能力显式化，实测 Pixel 8a：内层源每帧重放 7→1，P90 CPU frame −2~6%，**每个共享源 +25–33 MiB 峰值 GPU 内存**（E1）。
- 内存核算：一块全屏 RGBA8 源层 ≈ W×H×4B（1080×2400 ≈ 10 MB）；再乘"每块玻璃的 effect layer"（按玻璃尺寸，不是全屏）。
- 建议：**1 个源层 + ≤3 块玻璃**；不要把玻璃挂到 `LazyColumn` 每个 item 上（Haze 的 Images List 场景 P90 +45% 就是多 `RenderNode` 的代价）。

### 6.4 缓存

| 缓存对象 | 怎么做 | 证据 |
|---|---|---|
| `RenderEffect` 对象 | 参数不变就复用（`remember(params)` 或 Haze 的 `Cache.Builder`） | `HazeChildNode.kt:529,588-599`；否则每帧重建效果图 |
| 源层 | `rememberGraphicsLayer()` 复用，不要每帧 create/release | `GraphicsLayerScope.kt:236-260` |
| effect 层 | 同上；尺寸变化才重录 | 同 |
| 静态背景快照 | 浮层生命周期内只录一次 | §6.2 |
| 着色/噪声等绘制 | `drawWithCache` | Compose 常规做法 |

### 6.5 `graphicsLayer` flag 清单（会隐式改变行为的）

| flag | 与背景模糊的关系 |
|---|---|
| `renderEffect` | 非空 → **强制离屏**（`CompositingStrategy.Offscreen`）并**隐式裁到 bounds** → 自己留 outsets |
| `alpha < 1` | 也强制离屏（`transformCanvas` 里 `useSaveLayer`，`AndroidGraphicsLayer.android.kt:446-468`） |
| `ColorFilter != null` / `blendMode != SrcOver` | 同样强制离屏 → 多一次 buffer |
| `CompositingStrategy.Offscreen` | 显式离屏：只有你要做"整体 alpha/混合"时才需要；blur 场景已经被动离屏，别重复设 |
| `CompositingStrategy.ModulateAlpha` | 省离屏，但**与 renderEffect 冲突**时仍会离屏；不要用来自作聪明 |
| `clip` | 默认 false，但离屏时一定裁；显式 `clip=true` 只影响非离屏路径 |
| `topLeft` | 决定 `drawLayer` 时的落地位置（做 outsets 归位时用它） |
| 缩放/旋转/`pivot` | 只影响绘制，不影响录制；`drawLayer` 前改这些不会重录 |

### 6.6 降级门槛（帧时间判据）

- 预算：60Hz = 16.6ms/帧；120Hz = 8.3ms/帧。用 **Macrobenchmark `frameOverrunMs`**（>0 = 超时）与 CPU frame duration P90（E1，Haze 文档给的正是这两个指标）。
- 建议门槛（写进实现，具体值待真机校准 → §8 第 5 条）：
  1. 打开/关闭浮层的**动画期间**出现 `frameOverrun`；
  2. 或 P90 CPU frame duration 相对基线 **+2ms 以上**；
  3. 或设备明显低端（粗判手段与阈值待真机校准，见 §8 第 5 条）。
  → 依次降：**半径减半 → 面积缩小（只糊可见区）→ 玻璃数量减到 1 → 落 scrim（L2）**。
- **软硬件门槛**：`SDK_INT >= 31` + `canvas.isHardwareAccelerated`（Haze 同款判定，`HazeEffectNode.android.kt:26-28`）+ `createBlurEffect` 不抛异常（§2.7 坑 3）。三者任一失败 → L2 scrim。
- **绝不用软件渲染模拟器验收帧时间**（数值无意义）。

### 6.7 优化效果预期汇总

| 手段 | 预期收益 | 证据等级 |
|---|---|---|
| 共享捕获（1 源 N 玻璃） | 避免"每块玻璃各画一遍背景"；本质是 O(N)→O(1) 的背景重绘 | E0（机制）/ E1（Haze 数据） |
| 静态快照（浮层内不重录） | 浮层生命周期内背景重绘 ≈ 0 | E0（机制） |
| 缓存 RenderEffect / layer | 避免每帧重建效果图与分配 | E0（机制） |
| 降采样 0.5 | Haze 成本 −5%，总帧时长 −3~5% | E1 |
| 缩小面积/减少玻璃数 | 线性下降（blur pass 按像素） | E0/E1 |
| 隔帧更新 | 减半 | E0（机制），画质风险自担 |

---

## 7. 建议的验证顺序（谁先试、判据是什么）

> 前提：**不占用模拟器**（本次硬约束）；以下顺序按"先证机制、再证产品、最后证性能"排列，每步都有可判定的判据。

| # | 试什么 | 谁/在哪 | 判据（通过 = 继续，失败 = 停） | 对应脚本 |
|---|---|---|---|---|
| 1 | **RCA 证明**：A（member）vs B（extension）并排 | 真机（API 31+，硬件加速）优先；模拟器需 host GPU | **A 玻璃无模糊背景、B 有** → 机制成立，RCA 确认；A 也有 → 回到 §8 第 2 条 | `scratch/blur-mech/RcaProof.kt` |
| 2 | **最小实现**：抽屉 + 底部 sheet 两个场景 | 同上 | 滚动背景时模糊跟随（录屏逐帧，滞后 ≤1 帧）；四边无透明带；抽屉动画中不错位 | `MinimalBackdropBlur.kt` |
| 3 | **API 31 专项**：旧设备/31 系统镜像上滚动 3 秒 | 真机 API 31（若有） | 模糊不冻结/不陈旧（Haze #77 是否在自绘路径复现） | 同 2（加录屏） |
| 4 | **性能**：1/3 块玻璃 × 60Hz、120Hz | 真机（含一台低端）+ Macrobenchmark | `frameOverrunMs ≤ 0` 且 P90 CPU frame duration 增量在预算内；否则按 §6.6 降级 | 待写（复用 `GlassLabPerf.kt`） |
| 5 | **窗口级 B**：Dialog + OEM 检测 | 手头所有真机 | `getprop ro.surface_flinger.supports_background_blur`=1 且 `wm disable-blur` 允许的设备上看到模糊；`disable-blur 1` 后底色兜底可读 | `PathBWindowBlur.kt` |
| 6 | **降级分支**：API 26–30 / 软件渲染 | 老设备或 31- 镜像 | `<31` 或无硬件加速时**必须**落 scrim（不闪、不黑、不崩） | `MinimalBackdropBlur.kt` 的 `drawScrimFallback` |
| 7 | **WebView 反证**（只在有人质疑时做） | 任意设备 | 透明 WebView 里的 `backdrop-filter` 元素糊不到 Compose 背景（§5.4） | 临时 demo，不入库 |

**不建议的验证顺序**（浪费时间的坑）：
- ❌ 先做性能优化（降采样/隔帧）——机制没证实前，优化数字无意义；
- ❌ 先在软件渲染模拟器上看效果——1.7.0-beta04 起软件渲染不产生模糊；
- ❌ 先引 Haze 1.5.4/2.x——1.5.4 的 Kotlin 元数据是 2.1.0（本栈编译器 2.0.21，兼容性未取证），2.x 要 Compose 1.9+；自研 60 行更快。

---

## 8. 待真机验证（E2 only，不进任何结论）

| # | 假设（E2） | 验证方法 | 判据 | 影响哪条决策 |
|---|---|---|---|---|
| 1 | 按 §1.5 改一处后，真机/ host-GPU 模拟器上能出真模糊 | 跑 `RcaProof.kt` | A 空、B 有 | **路 A 是否成立（最高优先）** |
| 2 | 我们之前三次失败只有"record 重载"这一个原因 | 用修正后的 `RecordInto` 重跑原诊断场景（保留） | 原三处全部恢复出图 | 是否还有第二根因 |
| 3 | API 31 上自绘路径不会出现 Haze #77 式"模糊冻结" | API 31 设备滚动 3s 录屏 + 逐帧 diff | 模糊区域每帧变化 | 31 设备的降级策略 |
| 4 | 目标 OEM 里窗口模糊可用率 | 手头真机逐台跑 §3.6 三条命令 + 打开 Dialog | 支持/不支持比例 | 路 B 是否值得做 |
| 5 | 单块/三块玻璃的实际帧成本 | Macrobenchmark（P90 frame duration、frameOverrunMs） | 见 §6.6 门槛 | 半径/数量/降级阈值 |
| 6 | 低端 GPU 上 `createBlurEffect` 抛异常的半径阈值 | 从 8dp 递增到 64dp，捕获异常 | 抛出半径 R | 半径上限与 try/catch 兜底 |
| 7 | 透明 WebView 采不到 Compose 背景（§5 反证） | §5.4 的 10 分钟 demo | 无模糊 | 封死路 D 的最后争议 |
| 8 | Android 17（API 37.2）`View/RenderNode.setBackdropRenderEffect` 的真实行为 | 升 compileSdk 37.2 的小 demo + Android 17 设备 | 能否按 View bounds 糊背景、是否有 flag 门控 | 下一代是否换"平台原生 backdrop" |
| 9 | 背景里是 `AndroidView(VideoView/TextureView)` 时玻璃区域是什么 | 背景放视频，玻璃叠上 | 黑块/未模糊/正常 | 媒体场景的降级（scrim） |
| 10 | 降采样 0.5 / 0.3334 的画质可接受度 | 同一背景三档截图对比 | 主观 + 帧时间 | 是否启用降采样 |
| 11 | 层内存：1 源层 + 3 玻璃层的实际 GPU 内存 | Perfetto/GPU 内存快照 | 增量 MB | 玻璃数量上限 |
| 12 | Compose `Popup`（D-51 的 `/` 候选面板）能否设窗口模糊 | 反射拿 `PopupLayout` 的 `LayoutParams` 试 | 能/不能 | 行内菜单的玻璃方案 |

---

## 附：证据索引

### 本栈源码（E0，本机可复现）

| 主题 | 位置 |
|---|---|
| `DrawScope.record` 扩展（**重定向 canvas**） | `scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/drawscope/DrawScope.kt:932-956` |
| `DrawScope.draw(...)` 重定向实现 | 同上 `:301-354` |
| `GraphicsLayer.record` 成员函数 | `scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/layer/GraphicsLayer.kt:373-378` |
| 成员函数实现 / clip 包装 | `scratch/raw/compose-1.7.5-src/androidMain/androidx/compose/ui/graphics/layer/AndroidGraphicsLayer.android.kt:418-438` |
| 录制 = `beginRecording` + 改 `drawContext.canvas` | `scratch/raw/compose-1.7.5-src/androidMain/androidx/compose/ui/graphics/layer/GraphicsLayerV29.android.kt:235-257` |
| `GraphicsLayer` = `RenderNode`（API 29+） | 同上 `:44-51`；`GraphicsLayerV23.android.kt:48-55` |
| 离屏/裁剪触发条件 | `GraphicsLayerV29.android.kt:282-288`；`GraphicsLayer.kt:330-338` |
| `ContentDrawScope` 契约 | `scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/drawscope/ContentDrawScope.kt:26-31` |
| `drawWithContent` 实现 | `scratch/raw/compose-1.7.5-ui-src/commonMain/androidx/compose/ui/draw/DrawModifier.kt:398-424` |
| `rememberGraphicsLayer` 生命周期 | `scratch/raw/compose-1.7.5-ui-src/commonMain/androidx/compose/ui/graphics/GraphicsLayerScope.kt:236-260` |
| `BlurEffect` / `TileMode` | `scratch/raw/compose-1.7.5-src/commonMain/androidx/compose/ui/graphics/RenderEffect.kt:41-64`、`TileMode.kt:38-74` |
| `asComposeRenderEffect`（AGSL 互操作） | `scratch/raw/compose-1.7.5-src/androidMain/androidx/compose/ui/graphics/AndroidRenderEffect.android.kt:27` |
| 我们失败的三处写法 | `app-android/src/main/kotlin/dev/myco/android/ui/m5/glass/GlassLabCapture.kt:67-78,84-190` |
| 现有 workaround（重绘式 backdrop） | `app-android/src/main/kotlin/dev/myco/android/ui/m5/glass/GlassLabPanel.kt:46-55,120-140` |

### Haze 源码/文档（E0 源码，E1 文档）

| 主题 | 位置 |
|---|---|
| **1.1.1（对 Compose 1.7.5 + Kotlin 2.0.21）** 源节点 `record{}` | `scratch/raw/haze111/HazeNode.kt:77-102` |
| 1.1.1 版本目录（`jetpack-compose=1.7.5`, `kotlin=2.0.21`） | `scratch/raw/haze111/libs.versions.toml` |
| 1.1.1 效果节点（膨胀层/裁剪重绘/renderEffect/alpha） | `scratch/raw/haze111/HazeChildNode.kt:271,276-298,312-368` |
| 1.1.1 inputScale Auto（0.3334/0.5/<7dp 关闭） | `scratch/raw/haze111/HazeChildNode.kt:548-562` |
| 1.1.1 RenderEffect 缓存 | 同上 `:529,588-599` |
| 1.1.1 半径过大 try/catch | `scratch/raw/haze111/RenderEffect.android.kt:60-66` |
| 1.5.4 源节点（同机制） | `scratch/raw/haze154/.../HazeSourceNode.kt:130-161` |
| 1.5.4 硬件门槛判定 | `scratch/raw/haze154/.../HazeEffectNode.android.kt:25-42` |
| 1.5.4 性能实测（+29%/+45%/+98%、inputScale −5%~−14%） | `scratch/raw/haze154/docs__performance.md:34-60` |
| 2.x 性能（inputSharing、Backdrop 成本、Glass 档位） | `scratch/raw/haze/docs__performance.md` |
| 2.x native Backdrop（Android 17、跨窗口限制） | `scratch/raw/haze/docs__core-concepts.md` |

### Android/AOSP 官方（E0）

- Compose 模糊总览（`Modifier.blur` 只糊自己）：https://developer.android.com/develop/ui/compose/graphics/blur/overview
- compose-ui release notes（1.7.0 GraphicsLayer；**1.8.0-alpha08 的 record 扩展指引**）：https://developer.android.com/jetpack/androidx/releases/compose-ui
- `Window#setBackgroundBlurRadius`（translucent **+ floating**；运行时关闭）：https://developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int)
- `LayoutParams#setBlurBehindRadius` / `FLAG_BLUR_BEHIND`：https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setBlurBehindRadius(int)
- `WindowManager#isCrossWindowBlurEnabled` / `addCrossWindowBlurEnabledListener`：https://developer.android.com/reference/android/view/WindowManager#isCrossWindowBlurEnabled()
- AOSP 窗口模糊（半径 20/80、>150 不建议、sysprop、验证命令）:https://source.android.com/docs/core/display/window-blurs
- `RenderEffect`（只作用于自己所在层）:https://developer.android.com/reference/android/graphics/RenderEffect
- `View#setBackdropRenderEffect`（**Added in version 37.2**）:https://developer.android.com/reference/android/view/View#setBackdropRenderEffect(android.graphics.RenderEffect)
- `PixelCopy`:https://developer.android.com/reference/android/view/PixelCopy
- CSS Filter Effects L2 — Backdrop Root:https://drafts.csswg.org/filter-effects-2/#BackdropRoot
- MDN `backdrop-filter`（backdrop root 不透传）：https://developer.mozilla.org/en-US/docs/Web/CSS/backdrop-filter

### 其它（E1）

- Haze issue #77（API 31 冻结）：https://github.com/chrisbanes/haze/issues/77
- Haze ADR-0003（Android fused glass renderer）：https://github.com/chrisbanes/haze/blob/main/docs/adr/0003-use-one-android-fused-glass-renderer.md
- Android 17 `RenderNode.setBackdropRenderEffect`（.NET 绑定文档，佐证同名 API 存在）：https://learn.microsoft.com/dotnet/api/android.graphics.rendernode.setbackdroprendereffect
- WebView 独立渲染树（社区一手问题，佐证 D）：https://www.volcengine.com/article/2268272
