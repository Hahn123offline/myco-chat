# Android 真实模糊与液态玻璃：能力底稿

> 目的：给实现者（glass-lab）与产品负责人一份**可直接引用**的能力/门槛/坑清单。不写产品代码，只给骨架与结论。
>
> 取证日期：2026-11-27。取证对象：本仓库 `app-android`（minSdk 26 / target+compile 35 / Compose BOM 2024.10.01 → **compose-ui 1.7.5** / Kotlin 2.0.21，E0：`app-android/build.gradle.kts` 与本地 BOM pom）。旁证取 Android 官方文档、AOSP/AndroidX 源码、Haze 库源码与文档。
>
> 网络说明：本机直连 `developer.android.com` / `source.android.com` 超时，实际抓取走官方中文镜像 `developer.android.google.cn` / `source.android.google.cn`（同一站点同一内容）；正文链接给 canonical 地址。GitHub raw / api 可直连。
>
> 证据分级：**E0** = 官方文档原文 / 源码行 / 实测；**E1** = 库官方文档、issue、博客等一方但非平台方声明；**E2** = 推测（只出现在 §6，不进任何结论）。凡未取证的写"未取证"，不猜。

---

## 0. 一页结论（能做 / 不能做 / 建议）

### 0.1 能做

1. **in-app 真实背景模糊（API 31+）可以做，且不需要第三方库。** 平台没有"采样背景"的 API，但 Compose 1.7.0 起有 `rememberGraphicsLayer()` / `GraphicsLayer.record` / `DrawScope.drawLayer`（E0），可以把背景内容**再画一遍**到一个离屏层，给该层挂 `RenderEffect.createBlurEffect(...)`，再裁剪到玻璃区域。这正是 Haze 的实现方式（E0 源码）。
2. **液态玻璃的"模糊 + tint + 边缘高光 + 内阴影 + 噪声 + 超椭圆圆角"可以做到接近**，其中混合/着色部分甚至不需要 shader（纯绘制）。
3. **折射/透镜 + 色散（chromatic dispersion）只在 API 33+ 可做**，且必须用 `RuntimeShader`（AGSL）+ `RenderEffect.createRuntimeShaderEffect`，装在"重绘出来的背景层"上（E0：AGSL 用于 Android 13+；`createRuntimeShaderEffect` API 33）。
4. **窗口级（跨窗口）模糊**：`Window.setBackgroundBlurRadius` / `FLAG_BLUR_BEHIND + setBlurBehindRadius` 也是 API 31+，是唯一能糊"别的窗口（含别的 App）"的手段（E0），但**依赖 OEM 打开**（`ro.surface_flinger.supports_background_blur=1`）且可能被系统运行时关闭（省电、视频、开发者选项）。
5. **API 26–30 的"真模糊"只能靠自建捕获管线**：截图/`lockHardwareCanvas` 捕获 + CPU 模糊或自建 OpenGL（blur-cmp 走 API 24+ Dual Kawase，E1）；纯 Compose 路径在这些设备上是**无模糊**的。

### 0.2 明确不能做 / 不存在

1. **不存在"shader 采样背景"这回事。** `RenderEffect.createRuntimeShaderEffect` 的输入**只有**它所在 `RenderNode` 的内容：官方原文"passes the contents of the RenderNode that this RenderEffect is installed on as an input to the shader"（E0）。AGSL 是 Canvas 绘制/View 过滤里的一个 fragment 函数，没有 framebuffer/backdrop 访问（E0：AGSL 文档"works within the Android graphics rendering system to both customize painting within Canvas and filter View content"）。
2. **`Modifier.blur()` 不模糊背景。** 官方明示：它模糊"composable 自己画的内容"，是 surface/layer blur，区别于 backdrop blur（E0 原文）。
3. **没有"一个 API 直接把某块区域的背景模糊"**；窗口级模糊是"整个窗口的背后"或"窗口背景范围"，不能任意指定内部子区域，也不能多层玻璃各自糊。
4. **API < 31 没有 RenderEffect，`Modifier.blur()` 直接无效**（官方："On lower versions, Compose renders the content unblurred"；"Attempts to use this Modifier on older Android versions will be ignored"）。
5. **`FLAG_BLUR_BEHIND` 在 API 12 之前存在但没实现**：Android 12 才提供窗口模糊公开 API（E0）。

### 0.3 建议（给本仓库）

| 优先级 | 建议 | 依据 |
|---|---|---|
| P0 | 主路径用**自绘 GraphicsLayer 重绘 backdrop + `RenderEffect`**，不引第三方库（本栈 Compose 1.7.5 已具备所需 API） | §2.3 |
| P0 | 把"玻璃"实现拆成三层：`backdrop 捕获层` / `模糊+tint 效果层` / `前景内容层`，前三者用 `RenderEffect` 图（blend/colorFilter 链）做完，而不是叠多个 Composable | §2.3、§4 |
| P1 | 液态玻璃的"折射/色散"按 API 33 分支实现；31–32 只做"模糊+tint+rim"；≤30 走静态假玻璃 | §3、§5 |
| P1 | 玻璃数量限制在 1–3 块/屏；滚动列表上方最多 1 块；不把 `RenderEffect` 挂到 LazyColumn 的每个 item 上 | §4 |
| P2 | Dialog/Popup 的模糊先用**窗口级 API**（若做），不要期待同窗口重绘能拿到 Dialog 后面的内容（Dialog 是独立 window） | §2.5、§4.3 |
| P2 | 暂时不引 Haze：本栈（Kotlin 2.0.21 + Compose 1.7.5）能用的最高是 **Haze 1.1.1**，功能停在"模糊+tint+noise+progressive(31/32 近似)"，没有 glass；Haze 2.x 需要 Compose 1.9–1.12 | §2.6 |

---

## 1. 能力矩阵

> 列说明：「支持背景」= 能否模糊自己**后面**的内容；「多层叠加」= 玻璃叠玻璃是否可行及其附加成本；「性能风险」是相对量级（低/中/高/很高），不是绝对值。

| # | 方案 | 最低 API | 需硬件加速 | 支持背景(backdrop) | 多层叠加 | 性能风险 | 证据 |
|---|---|---|---|---|---|---|---|
| A1 | `Modifier.blur(radius, edgeTreatment)`（自身内容） | **31**（31 以下无效） | 是 | ❌ 只糊自己 | 可，但每层 = 1 离屏层 + 1 blur pass | 中–高 | [E0](https://developer.android.com/develop/ui/compose/graphics/blur/overview) |
| A2 | `Modifier.graphicsLayer { renderEffect = BlurEffect(...) }` / `GraphicsLayer.renderEffect` | **31** | 是 | ❌ 只糊本层内容 | 同上；可用 `createChainEffect` 合成单图 | 中 | [E0](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer) |
| A3 | **自绘 backdrop**：`rememberGraphicsLayer` + `record` + `drawLayer` + 给层挂 RenderEffect（Haze 同款机制） | **31**（捕获/绘制可用更早，但模糊必须 31） | 是 | ✅ 同窗口、自己重绘的内容 | ✅（Haze 用 zIndex 排序源层） | **高**：源每帧变 → 每帧重绘 + 每层一次模糊 | [E0 源码](https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeSourceNode.kt) |
| A4 | `RenderEffect.createRuntimeShaderEffect(RuntimeShader, "content")`（AGSL） | **33** | 是 | ⚠️ 只能采样"所在层的内容"；配合 A3 才等于采样背景 | ✅ 但 shader 采样数 × 像素 | **很高** | [E0](https://developer.android.com/reference/android/graphics/RenderEffect#createRuntimeShaderEffect(android.graphics.RuntimeShader,java.lang.String)) |
| A5 | `Modifier.blur { BlurRadiusSpec.verticalGradient(...) }`（渐进模糊） | **33** + compose-ui **1.13.0-beta01** | 是 | ❌ 只糊自己 | 同上，成本更高（Haze 实测 31/32 ≈2×，33+ ≈+25%） | 高 | [E0](https://developer.android.com/develop/ui/compose/graphics/blur/overview)、[E0 版本](https://developer.android.com/jetpack/androidx/releases/compose-ui) |
| A6 | **Haze 1.x**（`hazeSource`/`hazeEffect`） | 31 真模糊；**32+ 默认开，31 默认关** | 是 | ✅ 同窗口；跨窗口要 recapture（贵） | ✅（zIndex、`canDrawArea`） | 高 | [E0 源码](https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/androidMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.android.kt)、[E1 issue#77](https://github.com/chrisbanes/haze/issues/77) |
| A7 | **Haze 2.x / 3.x + `haze-glass`**（折射驱动玻璃） | 33 全光学；31–32 降级；≤30 scrim/RenderScript(实验) | 是 | ✅ Sources；原生 Backdrop 需 **Android 17 / SDK 37.2** 且实验开关 | ✅（容器合并） | 很高 | [E0 库文档](https://github.com/chrisbanes/haze/blob/main/docs/blur/platforms.md) |
| A8 | **Cloudy**（skydoves）：`cloudy` / `cloudy(sky=)` / `liquidGlass` | 库 23+；自身模糊 31+；30- CPU fallback；`liquidGlass` 折射 **33+** | 是（CPU fallback 除外） | ✅（backdrop 需 31+，30- 要显式开 CPU） | ✅ | 中–高 | [E1 README](https://github.com/skydoves/Cloudy) |
| A9 | **blur-cmp**（OpenGL ES Dual Kawase） | **24** | 是 | ✅ 真 backdrop（含 TextureView/SurfaceView 捕获，非受保护内容） | ✅ | 高（~39 采样/像素；官方称 Pixel 9 ≈5ms） | [E1 README](https://github.com/ezoushen/blur-cmp) |
| A10 | `Window.setBackgroundBlurRadius(px)` + `windowIsTranslucent=true` | **31** | 是（系统合成器） | ✅ 跨窗口（含别的 App） | ❌ 一个 window 一个背景模糊，不可叠 | 系统级；建议半径 ≤80px，>150px 显著掉性能 | [E0](https://developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int))、[E0 AOSP](https://source.android.com/docs/core/display/window-blurs) |
| A11 | `FLAG_BLUR_BEHIND` + `LayoutParams.setBlurBehindRadius(px)` | **31**（flag 自 API 1 存在但无实现） | 是 | ✅ 整屏背后 | ❌ 同 A10 | 同上（推荐半径 ≤20px 做景深） | [E0](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setBlurBehindRadius(int))、[E0 AOSP](https://source.android.com/docs/core/display/window-blurs) |
| A12 | 截图/位图捕获 + CPU 模糊（`View.draw`→Bitmap、`PixelCopy`、`lockHardwareCanvas`） | 21+ / 26+（GPU 捕获） | ❌ CPU | ✅ 但**是快照**，滚动内容会"拖影" | ✅ 成本叠加 | 高（拷贝 + CPU + 同步） | [E1](https://github.com/ezoushen/blur-cmp)、[E1](https://github.com/Dimezis/BlurView) |
| A13 | 纯绘制假玻璃（半透 tint + 渐变 + 噪声 + rim/内阴影，无 shader 无采样） | **24/26**（无门槛） | ❌ | ❌ 只是近似 | ✅ 成本低 | 低 | [E0（scrim 兜底是官方库的既定做法）](https://github.com/chrisbanes/haze/blob/main/docs/blur/platforms.md) |

**矩阵行数：13 行。**

---

## 2. 逐方案详解

### 2.1 `Modifier.blur()`：语义、门槛、边缘

**语义（E0 官方原文）**
- "use `Modifier.blur` to blur the content drawn inside a composable, including images, shapes, text, and layout containers. Applying `Modifier.blur` renders the composable into a separate graphics layer."
- 并明确区分："`Modifier.blur` blurs the content that a composable draws itself … This is sometimes called a surface or layer blur, as distinct from a **backdrop blur**, which blurs whatever is drawn behind a composable." → **它不能糊背景**。
- 实现就是 `graphicsLayer { renderEffect = BlurEffect(...) }`（E0：[Blur.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/draw/Blur.kt)，`tileMode = Clamp / Decal` 随 `edgeTreatment`）。

**API 门槛（E0）**
- 均匀模糊：**Android 12 / API 31+**。
- 渐进模糊（`BlurRadiusSpec` / `BlurStop` / `Modifier.blur {}`）：**Android 13 / API 33+**，且 compose-ui **1.13.0-beta01+**（本仓库 1.7.5 没有）。
- 低版本："Compose renders the content unblurred, though `BlurredEdgeTreatment` shape clipping and alpha settings still apply."

**`BlurredEdgeTreatment` 三种（E0）**

| 值 | 行为 | 采样 |
|---|---|---|
| `Rectangle`（默认） | 裁到原内容矩形 | `TileMode.Clamp`（边缘像素延伸） |
| `BlurredEdgeTreatment(shape)` | 裁到指定 Shape | `TileMode.Clamp` |
| `Unbounded` | 不裁 | `TileMode.Decal`：边界外采样**透明黑** → 边缘发虚 |

> `TileMode.DECAL` 本身也是 **API 31** 才加（E0：[Shader.TileMode](https://developer.android.com/reference/android/graphics/Shader.TileMode)），所以低版本连"正确的外扩模糊"都表达不了。

**坑**
- 该层**显式裁切到内容边界**（官方：layer "is explicitly clipped to the content bounds"）：想让模糊/折射在边缘外扩，必须自己给内容加 padding/outsets（Compose 1.12 才有 `LayerOutsets`，E0 release notes，本栈没有）。
- 挂到 LazyColumn 每个 item 上 = 每个 item 一个离屏层 + 一次 blur（见 §4.2）。
- 骨架：

```kotlin
// API 31+ 才有实际效果；以下在 <31 上自动退化为不模糊（E0 官方行为）
Box(
  Modifier
    .size(180.dp)
    .blur(radius = 16.dp, edgeTreatment = BlurredEdgeTreatment(RoundedCornerShape(20.dp)))
) { /* 自己的内容 */ }
```

### 2.2 `graphicsLayer { renderEffect = ... }` / `GraphicsLayer.renderEffect`

- `GraphicsLayerScope.renderEffect` 官方注释：**"only supported on Android 12 and above. Attempts to use this Modifier on older Android versions will be ignored."**（E0 源码：[GraphicsLayerScope.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/graphics/GraphicsLayerScope.kt)）
- 只要 `renderEffect != null`（或 `ColorFilter != null`、`CompositingStrategy.Offscreen`、非 SrcOver blend），该 layer 就会被提升为离屏缓冲，并且**隐式裁切到 bounds**（E0：[GraphicsLayer.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer.kt) 注释）。
- 软件渲染：compose-ui **1.7.0-beta04** 起 "Disable software rendering support for the GraphicsLayer API"（E0 release notes）→ **Compose Preview / 软件 canvas 不要指望看到模糊**。
- `RenderEffect` 可组合成图：`createBlurEffect` / `createColorFilterEffect` / `createBlendModeEffect` / `createChainEffect`，全部 API 31（E0：[RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)）。**推荐把 tint、noise、mask、模糊合成在一张 effect 图里**，而不是叠多个 Composable（Haze 1.x 就是这么做的，E0：[RenderEffect.android.kt](https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/androidMain/kotlin/dev/chrisbanes/haze/RenderEffect.android.kt)）。
- 坑：**每帧重建 `RenderEffect` 会重建效果图**。半径/参数不变时应缓存 effect 对象（Haze 1.5.4 的 `getOrCreateRenderEffect` 即为此）。

```kotlin
val density = LocalDensity.current
val radiusPx = with(density) { 24.dp.toPx() }
val effect = remember(radiusPx) {
  if (Build.VERSION.SDK_INT >= 31) {
    android.graphics.RenderEffect.createBlurEffect(
      radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP,
    ).asComposeRenderEffect()
  } else null
}
// 注意：renderEffect 要求硬件加速 canvas；Preview/SwiftShader 下无效（E0）
Box(Modifier.graphicsLayer { renderEffect = effect }) { OwnContent() }
```

### 2.3 自绘 backdrop 模糊（本仓库主路径；= Haze 的机制）

**核心事实**：Compose/Android 没有"采样背景"的 API。唯一可行做法是——**把背景内容再绘制一次**到离屏层，然后对那个层做效果，再裁剪画到玻璃位置。Haze 的源码就是这个结构（E0）：

- `HazeSourceNode.draw()`：`graphicsContext.createGraphicsLayer()` → `contentLayer.record { drawContent() }` → `drawLayer(contentLayer)`；source 只负责"把背景画进层，并把层放进 `HazeState`"（[源码](https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeSourceNode.kt)）。
- `HazeEffectNode`：把源层内容画进自己的 `contentLayer`，设 `contentLayer.renderEffect = ...`，再 `drawLayer`（[RenderEffect.android.kt](https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/androidMain/kotlin/dev/chrisbanes/haze/RenderEffect.android.kt) / [HazeEffectNode.android.kt](https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/androidMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.android.kt)）。
- 硬件门槛在源码里写死：`canUseRenderEffect = Build.VERSION.SDK_INT >= 31 && drawContext.canvas.nativeCanvas.isHardwareAccelerated`（E0 行）；否则退回 scrim。

**为什么"跟随滚动"是天然的**：每帧源层随内容重绘一次（`record { drawContent() }` 在 draw 阶段执行），效果层再从这张层里取像素——所以滚动/动画不需要额外同步，代价是**背景每帧被画两遍**（一遍上屏、一遍进层）。Haze 文档也说明：稳定源可以复用处理结果，"Scrolling or animated backgrounds need fresh input"（E0 库文档）。

**骨架（Compose 1.7.5，`rememberGraphicsLayer`/`record`/`drawLayer` 都可用，E0：1.7.0-alpha05~07 release notes）**

```kotlin
@Composable
fun GlassScaffold(
  backdrop: @Composable () -> Unit,
) {
  val bg = rememberGraphicsLayer()        // 背景层：整个屏幕一次
  Box(Modifier.fillMaxSize()) {
    // 1) 背景：画进层 + 上屏
    Box(
      Modifier.fillMaxSize().drawWithContent {
        // 1.7.x 的 record 扩展形如 record(IntSize) { }（Haze 1.5.4 即此写法，E0）；
        // 1.8.0-beta01 还修过一次 `record { drawContent() }` 的 NPE（E0 release notes），
        // 实现前按实际版本核对签名，别照抄。
        bg.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
          this@drawWithContent.drawContent()
        }
        drawLayer(bg)
      }
    ) { backdrop() }

    // 2) 玻璃：位置/尺寸自由；把 bg 按自己的窗口坐标偏移再画进玻璃层
    GlassSurface(
      bg = bg,
      blurRadius = 24.dp,
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp),
    ) { /* 玻璃上的内容（图标/文字，保持锐利） */ }
  }
}

@Composable
private fun GlassSurface(
  bg: GraphicsLayer,
  blurRadius: Dp,
  shape: Shape,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val layer = rememberGraphicsLayer()
  var origin by remember { mutableStateOf(Offset.Zero) }
  val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }

  Box(
    modifier
      .onGloballyPositioned { origin = it.positionInRoot() }   // 需要与背景层同坐标系
      .clip(shape)
      .drawWithContent {
        // a) 把背景重绘进本层（按偏移对齐；背景层是整屏，所以平移 -origin）
        layer.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
          translate(-origin.x, -origin.y) { drawLayer(bg) }
        }
        // b) 效果：31+ 模糊；参数不变时不要每帧重建（见 §2.2 坑）
        if (Build.VERSION.SDK_INT >= 31) {
          layer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
        }
        layer.alpha = 0.92f
        drawLayer(layer)          // c) 模糊背景
        drawContent()             // d) 前景内容（锐利）
      }
  ) { content() }
}
```

> 这段是**骨架不是成品**：真实实现要处理 1) 裁剪/圆角后的边缘采样（Clamp 会拉边，需要给背景层留 outsets 或改用自绘 shader 的 clamp 采样）；2) 玻璃位置变化时避免整层失效；3) 多层玻璃时的绘制顺序与 z 序（§4）。可直接对照 Haze 1.5.4 的 `HazeEffectNode` 实现。
>
> 关键约束：`bg.record {}` 里面的 `drawContent()` **必须画的是同一批背景 Composable**（同一棵树里的），不能是"另一个窗口/另一个 ComposeView 的内容"。跨窗口只能靠窗口级模糊（§2.5）或 Haze Sources 的跨窗口 recapture（贵，§2.6）。

### 2.4 `RenderEffect` + `RuntimeShader`（AGSL）：折射/色散的唯一途径

**门槛（E0）**
- `RenderEffect`、`createBlurEffect`、`createBlendModeEffect`、`createChainEffect`：**API 31**。
- `RuntimeShader`：**API 33**（"Android 13 and above"）。`RenderEffect.createRuntimeShaderEffect`：**API 33**。
- 必须硬件加速（AGSL 文档："When Android issues a GPU accelerated drawing operation…"；Haze 源码把 `isHardwareAccelerated` 做前置判断，E0 行）。

**能不能采样背景？（用户理解 → 核实结果：正确）**
- 官方原文："Create a RenderEffect that executes the provided RuntimeShader and **passes the contents of the RenderNode that this RenderEffect is installed on as an input to the shader**."（E0）
- 也就是说：shader 里 `uniform shader content;` 拿到的是**你挂这个 effect 的那个层自己的内容**。要"折射背景"，必须先用 §2.3 把背景重绘进这个层，再挂 shader → 此时 `content` 就是背景像素。
- AGSL 在管线里的定位（E0）："shares much of its syntax with GLSL fragment shaders, but works within the Android graphics rendering system to both customize painting within `Canvas` and filter `View` content"——它不会读屏幕/帧缓冲，也没有 backdrop 采样入口。
- `TileMode` 影响边界采样：`Clamp` 拉边像素（不会透出透明黑）、`Decal` 采样透明黑（API 31+）。模糊+自绘时优先 `Clamp`；要"外扩羽化"才用 `Decal`。

**骨架（折射 + 色散；仅示意，未在本机编译）**

```kotlin
// 1) AGSL：按到边缘的距离做位移，边缘处位移最大（透镜感）
private const val REFRACT_AGSL = """
uniform shader content;      // 绑定名必须与 createRuntimeShaderEffect 第二参数一致
uniform float2 size;
uniform float  refractPx;    // 边缘最大位移
uniform float  edgeFrac;     // 作用区宽度（占短边比例）

half4 main(float2 coord) {
    float2 uv   = coord / size;
    float2 d    = min(uv, 1.0 - uv);          // 到四边的最小归一化距离
    float  t    = 1.0 - smoothstep(0.0, edgeFrac, min(d.x, d.y));
    float2 dir  = normalize(float2(0.5) - uv);
    float2 off  = dir * refractPx * t;        // 边缘向内/外推
    return content.eval(coord + off);
}
"""

// 2) 挂到"重绘出来的背景层"上（API 33+）
if (Build.VERSION.SDK_INT >= 33) {
    val shader = RuntimeShader(REFRACT_AGSL).apply {
        setFloatUniform("size", w, h)
        setFloatUniform("refractPx", 8.dp.toPx())
        setFloatUniform("edgeFrac", 0.30f)
    }
    // 注意：是 android.graphics.RenderEffect，不是 compose 的 RenderEffect 别名
    layer.renderEffect = android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
}
```

**坑**
- RuntimeShader **编译可能失败**（不同 GPU 驱动/语法限制），必须 try/catch 并降级——Haze 仓库专门有 `BlurRuntimeShaderFailure*Test`（E0：测试文件存在），说明这是现实问题。
- 采样数 = 像素数 × 每像素采样数：一个 `content.eval()` ≈ 1 次贴图采样；做 7 点色散就是 7×。Haze 的 Full chromatic aberration 用 7 位置重建，并明确标注更贵（E0：ADR-0003）。
- 半径/位移为 0 时**不要挂 shader**（纯浪费）；`createOffsetEffect(0,0)` 是 Haze 处理"半径 0"的做法（E0 源码）。
- shader 作用在 layer 上时坐标是**层内像素**，与 Compose 的 dp/density 无关 → uniform 传 px。

### 2.5 窗口级模糊（跨窗口；Dialog / BottomSheet / 弹窗）

**两个 API（E0，均 API 31）**

| API | 语义 | 关键要求 |
|---|---|---|
| `Window.setBackgroundBlurRadius(int px)` | 模糊"窗口背景范围"内的屏幕（磨砂玻璃背景） | ① `windowIsTranslucent = true`（否则模糊被不透明背景挡住）② 背景 drawable 用半透明色/圆角 ShapeDrawable 决定模糊区域与圆角 ③ 半径 80px ≈ 好磨砂 |
| `FLAG_BLUR_BEHIND` + `window.attributes.setBlurBehindRadius(int px)` | 模糊窗口背后的**整个屏幕**（景深） | 半径 20px ≈ 好景深；可与背景模糊组合 |

**AOSP 给的硬事实（E0）**
- "Window blurs, or **cross-window blurs**, are used to blur the screen behind the given window… also works when there's another app behind your window. This effect isn't the same as the blur render effect, which blurs the content inside the same window."
- "Cross-window blurring is an **expensive** operation, so it's supported only on **selected devices**."
- 半径：blur behind 20px / background blur 80px 是好值；**"Avoid blur radii higher than 150 px, as this will significantly impact performance."**
- 必须处理"模糊被关掉"的状态：`WindowManager.addCrossWindowBlurEnabledListener` + `isCrossWindowBlurEnabled()`；系统可能因省电/视频/开发者覆盖**运行时关闭**。
- OEM 侧：设备需要 `ro.surface_flinger.supports_background_blur = 1`，否则 API 调用**不报错、只是不出模糊**。
- 验证手段（E0）：开发者选项 "Allow window-level blurs"（找不到 = 设备不支持）、`adb shell wm disable-blur 1/0`。

**Dialog/Popup 的差异**
- Compose 的 `Dialog`/`Popup` 各是**独立 window**（E0：`DialogWindowProvider` 用于访问其 `Window`），因此**同窗口重绘拿不到窗外背景**；要么走窗口级模糊 API，要么把模糊层放在同一个 window 里（例如不用 Dialog，改用 window 内的浮层 Composable）。
- 本栈（Compose 1.7.5）**没有**声明式的 `DialogProperties.blurBehindRadius` / `backgroundBlurRadius` / `PopupProperties.blurBehindRadius`——这些是 compose-ui **1.13.0-alpha01** 才加的（E0 release notes）。本栈只能手动拿 window 设。
- 骨架：

```kotlin
@Composable
fun BlurredDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    val view = LocalView.current
    DisposableEffect(Unit) {
      val w = (view.parent as? DialogWindowProvider)?.window
      val listener = Consumer<Boolean> { enabled ->
        w?.setBackgroundBlurRadius(if (enabled) 80.px() else 0)
      }
      if (w != null && Build.VERSION.SDK_INT >= 31) {
        w.setBackgroundBlurRadius(80.px())           // 半径单位是 px（E0）
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        w.attributes = w.attributes.apply { setBlurBehindRadius(20.px()) }
        view.context.getSystemService(WindowManager::class.java)
          .addCrossWindowBlurEnabledListener(listener)
      }
      onDispose {
        w?.setBackgroundBlurRadius(0)
        view.context.getSystemService(WindowManager::class.java)
          .removeCrossWindowBlurEnabledListener(listener)
      }
    }
    content()
  }
}
```

> 需要的 theme：`android:windowIsTranslucent=true`（E0）；同时准备"模糊关闭"的外观（提高窗背景 alpha / 加大 dim），否则模糊被关时内容直接压在一起不可读（E0 给了具体建议值）。

### 2.6 Haze：原理、版本、限制

**原理（E0 源码）**
- `Modifier.hazeSource` 把一个子树画进 retained `GraphicsLayer`（§2.3 的机制）；`Modifier.hazeEffect` 把该层内容重绘进自己的层，挂 `RenderEffect`（blur + tint + noise + mask），裁剪后绘制。
- 1.0 起迁移到 Compose **1.7.0** 的 `GraphicsLayer` API，跨平台共用一套管线（E0：[FAQ](https://github.com/chrisbanes/haze/blob/1.5.4/docs/faq.md)）。
- 1.x 的 API：`hazeSource` / `hazeEffect` / `HazeMaterials`，`progressive` 支持线性/径向/自定义 brush；`inputScale` 降采样换性能。
- 2.x 拆分模块：`haze` / `haze-blur` / `haze-glass` / `haze-blur-materials`…；`haze-glass` 是"折射驱动"（refraction + depth blur + tint + Fresnel/ambient + specular + 圆角/超椭圆 + 色散 + 边缘阴影/描边 + 交互光）（E0：[glass 文档](https://github.com/chrisbanes/haze/blob/main/docs/effects/glass/index.md)）。
- 2.x 的 `HazeInput.Backdrop` 有"原生窗口 backdrop"路径：**Android 17（full SDK 37.2）**、硬件加速窗口、实验开关，且"**can't select individual sources, include content drawn later, or cross a dialog, popup, or window boundary**"（E0 文档）。

**版本兼容（E0：各 tag 的 `gradle/libs.versions.toml` + CHANGELOG）**

| Haze | 对应 Compose Multiplatform / Jetpack Compose | Kotlin | 对本仓库（CMP↔JC 1.7.5 / Kotlin 2.0.21） |
|---|---|---|---|
| 1.0.0 | CMP 1.7.0 | **2.0.21** | ✅ 同 Kotlin，最稳 |
| 1.1.1 | CMP 1.7.1 | **2.0.21** | ✅ 推荐上限 |
| 1.2.0 ~ 1.5.4 | CMP 1.7.3 | 2.1.0 | ⚠️ Compose 兼容，Kotlin 元数据跨小版本（未取证，§6） |
| 1.6.x | CMP 1.8.0 | 2.1.x | ❌ 需升 Compose 1.8 |
| 1.7.x | CMP 1.9.3 / JC 1.9.4 | 2.3.x | ❌ 需升 Compose 1.9 |
| 2.0.x | JC 1.11.x 起（由 CHANGELOG 的依赖升级条目推断，未逐 tag 取 toml） | 2.4.x | ❌ |
| 2.1.0（2026-11-02） | CMP 1.12.2 | 2.4.21 | ❌ |

> 1.6.x / 1.7.x / 2.1.0 三行来自对应 tag 的 `libs.versions.toml`（E0）；1.0.0/1.1.1/1.2.0 来自 tag toml（E0）；1.3.1/1.5.0/1.5.4 取 toml 确认同为 CMP 1.7.3 + Kotlin 2.1.0。2.0.x 行依据 CHANGELOG 中 "Jetpack Compose 1.11.1" 的依赖升级记录（E0 CHANGELOG），未逐版本验证。

**已知限制/坑（E0 库文档 + E1 issue）**
- **API 31 默认禁用 blur**：因 [issue #77](https://github.com/chrisbanes/haze/issues/77) "Blur frozen on Android API 31"（滚动时模糊冻结，API 32 正常）。Haze 2.1.0 才在更新日志里写 "Blur no longer shows stale content while scrolling on Android API 31"（E0 CHANGELOG），**即：API 31 上的陈旧模糊问题在 1.x 一直存在**。
- **API ≤30 默认不模糊**，画半透明 scrim；可开 RenderScript 实验路径（后台线程、忙时丢帧、比内容晚 1 帧以上）（E0 文档）。
- **跨窗口源会 recapture**：读另一个 window 的 source 时，任何 snapshot 状态变化（含与背景无关的动画）都重新捕获一次；API 31 + `Sources/Backdrop` 更糟，"A running animation therefore does this once per frame"，且"this cost has not been measured"（E0 文档）。
- **progressive 成本**：Android 33+ ≈ +25%，API 32 ≈ 2×（E0 1.5.4 文档）。
- **半径过大会色溢出/断层**：issue [#520](https://github.com/chrisbanes/haze/issues/520)（Samsung 机型 progressive 异常，结论指向半径过大导致 clamp/溢出）。
- **降采样捕获边缘泄漏**：issue [#1726](https://github.com/chrisbanes/haze/issues/1726)（capture 尺寸取整后右下边缘漏背景）。
- 截图测试坑：Robolectric 需 SDK 35+ 才支持 `TileMode.CLAMP`（E0 文档），否则边缘怪异——这提示**软件/测试环境的模糊渲染与真机不同**。

### 2.7 其它方案

- **Cloudy**（E1 README）：`Modifier.cloudy(radius)`（自身内容）/ `Modifier.cloudy(sky=…)`（backdrop，需 31+，30- 默认 scrim、可开 CPU）/ `Modifier.liquidGlass(…)`（SDF 边缘 + 法线折射 + 色散，**折射类参数 API 33+ 才生效**，32- 只有 tint/边缘/形状）。适合"想要现成 liquidGlass 而不想升 Compose"的评估对象，但库本身对 Compose 版本有要求（未取证，§6）。
- **blur-cmp**（E1 README）：**唯一明确写 minSdk 24 的真 backdrop 模糊**，OpenGL ES 2.0 Dual Kawase，`lockHardwareCanvas`（API 26+）GPU 捕获，能捕获非受保护的 TextureView/SurfaceView；API 31+ 时 uniform blur 走 `RenderEffect` 零拷贝。代价：每帧捕获 + ~39 采样/像素（作者称 Pixel 9 ≈5ms）。
- **Dimezis/BlurView**（E1）：老牌 View 方案（截图 + 模糊）；注意 RenderScript 已弃用（见 §5）。
- **RenderScript**：Android 12 起弃用，AGP 7.2 起移除支持（E0：[官方文档](https://developer.android.com/guide/topics/renderscript/compute)）→ **不要作为 ≤30 的方案**。

### 2.8 通用坑（跨方案，按严重度）

1. **API 31 的"陈旧模糊"**：Haze 1.x 在 API 31 上滚动时模糊冻结（E1 #77；E0 Haze 2.1.0 CHANGELOG 才修）。自绘方案理论上每帧重绘→不一定复现，但必须真机验证（§6）。
2. **窗口模糊静默失效**：OEM 未开 `ro.surface_flinger.supports_background_blur`、省电模式、视频播放、`wm disable-blur` 都会让模糊消失且**不报错**（E0 AOSP）→ 必须监听 `addCrossWindowBlurEnabledListener` 并准备无模糊外观。
3. **软件渲染不模糊**：Compose Preview / 软件 canvas 下 `RenderEffect` 无效（E0：GraphicsLayer 软件渲染 1.7.0-beta04 被禁用；Haze 用 `isHardwareAccelerated` 判断）→ 设计评审不能只看 Preview。
4. **多层 = 多次全屏重绘**：Haze 2.x 文档实测一个 feed 源被 7 个效果采样时每帧重放 7 次；开启 `inputSharing` 后 7→1，P90 CPU 帧时间只降 2–6%，但每个共享源多吃 **25–33 MiB** GPU 内存（E0 库文档）。→ 玻璃数量是硬约束。
5. **模糊半径不是免费的**：AOSP 明确 >150px 显著掉性能；Haze 要 try/catch `createBlurEffect` 的 `IllegalArgumentException`（"likely because this device does not support a blur radius of X dp"），说明**设备端存在半径上限**（E0 源码）。
6. **每帧重建 `RenderEffect` = 重建 RenderNode 效果图**（E0 机制：GraphicsLayer/scene 属性变更会重建层/效果）→ 参数不变就要缓存。
7. **`RenderEffect` 提升离屏层后隐式裁切**（E0：GraphicsLayer 注释）→ 边缘模糊/折射会被切，需要 outsets。
8. **列表滚动与模糊同帧**：源每帧变 → 每帧"重绘源 + 模糊"，主线程和 RenderThread 都吃；Haze 建议先减面积、再降质量（`Performance` 模式 = 降采样副本）、再用 mask 替代 progressive（E0 库文档）。
9. **色空间/暗色**：模糊在 sRGB 下叠加半透白 tint 会提亮暗部（机制属 E2，见 §6）；OLED 纯黑背景上"发灰"是社区常见反馈但**本次未取到一手证据**，不写进结论。
10. **测试与实机不一致**：Robolectric 需 SDK 35+ 才有 CLAMP（E0 Haze 文档）；SwiftShader 模拟器与真 GPU 差异大（§6 验证项）。

---

## 3. 液态玻璃四要素的 Android 近似

**iOS 侧事实（E1，Apple WWDC25 "Meet Liquid Glass" 口述/官方页）**
- 透镜（lensing）："能动态地折射和塑造光线"、"通过一种名为透镜的效果…透明物体对光线的折射弯曲"；材质"实时动态地折射、塑造和聚集光线"。
- 高光/反射："材质会通过即时倒映和加强光线来回应交互"；"材质会从内部发光，并在其他 Liquid Glass 元素上形成反射"。
- 自适应：每个分层"都会根据下方的内容不断调整"；"当文本在它下方滚动时，阴影会变得更明显"；"有一种为元素着色的新方法能根据下层内容的亮度生成一系列色调"。
- 形态/动态：圆角悬浮、形状随交互变形、按压有凝胶弹性。
- 注意：Apple 未公开任何数值参数（半径、色散强度、边缘宽度），Haze 的研究文档也明确"Apple's public sources do not establish the numeric dark-edge width, opacity, falloff…"（E1）。

**Android 可实现度（结论表）**

| 要素 | iOS 表现 | Android 能做到什么程度 | 实现手段 | 门槛 |
|---|---|---|---|---|
| 背景模糊/扩散 | 强扩散、随尺寸/内容变化 | ✅ 可接近；31+ 真模糊 | `createBlurEffect`（半径按尺寸缩放），或自绘双 pass 模糊 | **31** |
| 折射/透镜 | 边缘弯曲背景 | ⚠️ 33+ 可模拟（位移采样）；31–32 只能"看起来厚"（暗角/亮边） | AGSL `content.eval(coord + dir*strength)` | **33**（+31 的 RenderEffect 宿主） |
| 镜面高光/边缘光 | 移动高光、Fresnel 边缘 | ✅ 纯绘制可做（不需要 shader）：沿 shape 画渐变描边/径向高光 | `drawWithCache` + `Brush`/`Path`；`drawIntoCanvas` | 无 |
| 自适应色调 | 按背景亮度调 tint/明暗 | ⚠️ 部分：可采样背景平均亮度（降采样后读像素，或 `GraphicsLayer.toImageBitmap` 1.7.0-alpha06+），但每帧做代价大 | 低频采样（如背景变化/滚动停止时更新） | 31（模糊）/26（纯静态） |
| 色散（chromatic aberration） | 边缘彩虹 | ⚠️ 33+ 可做（多位置采样，成本 ×N） | AGSL 7 点采样 | 33，很贵 |
| 内阴影/边缘暗边/外阴影 | 分离感 | ✅ 纯绘制可做 | 多层 native canvas / `Paint.setShadowLayer` 受限，建议自绘渐变 | 无 |
| 超椭圆圆角（squircle） | 连续圆角 | ✅ 纯绘制（Path 三次贝塞尔近似） | Path + 自定义 Shape | 无 |
| 交互光/按压缩放 | 按压发光/变形 | ✅ 可做（纯动画 + 绘制） | `interactionSource` + `animateFloatAsState` | 无 |

**必须靠 shader 的**：折射位移、（真）色散、基于背景逐像素的色调映射。
**可以纯绘制的**：tint、rim/边缘光、内阴影、外阴影、噪声（一张可平铺 png + `Shader.TileMode.REPEAT`）、渐变亮带、超椭圆圆角、按压/悬停动画。

**对本仓库的落地建议**：把玻璃拆成
`(A) backdrop 层（重绘）→ (B) RenderEffect 图：blur → 颜色矩阵(饱和/亮度) → tint blend → noise`（B 在 31+ 才有），
`(C) 前景绘制层：rim / 内阴影 / 超椭圆 clip / 文字`（与 API 无关），
`(D) 33+ 追加：AGSL 折射/色散，挂在 (A) 上`。
这样 26–30 只失去 (A)(B)，(C) 全保留 → 降级视觉不至于崩（对齐 `GlassSpec.legacy` 的设计）。

---

## 4. 分层遮挡专题

### 4.1 玻璃叠玻璃

- **本质**：每块玻璃都要"把它下面所有东西重绘一遍 + 模糊一遍"。n 块玻璃 ≈ n 次全屏绘制 + n 次模糊 pass。
- 可行，但要 z 序：Haze 用 `zIndex` 决定"哪层能被谁采样"，且默认只采比自己 z 小的源（E0 1.5.4 文档）；自绘方案同理，需要维护"层列表 + 每层的 z/范围"。
- 成本证据（E1/E0 库文档，Pixel 6 / Pixel 8a）：
  - 9 块 Glass（`steadyFull9`）：移除 Glass 后 P90 CPU 帧时间 4.82ms；仅把捕获降采样并不能改善；成本在 RenderThread/Vulkan 提交（E0 ADR-0003）。
  - "保留缓存底 + 局部交互补丁"两种方案分别吃到 26.9ms / 12.2ms CPU P90（超 16.6ms 预算）；把交互编译进同一 fused shader 才降到 10.9ms（E0 ADR-0003）。
  - 合并两块相邻玻璃成一个 cluster：P90 余量反而少 3.7ms，峰值 GPU 内存 +4%（E0 库文档）→ **"合并"不是免费的优化**。
- 结论：**同屏玻璃数量 ≤3 是安全区，>5 需要专门降级**；不要为了"玻璃叠玻璃"再叠第三层（对齐 GlassSpec 的实验：最多两层对照即可）。

### 4.2 玻璃叠滚动列表

- 主风险：**源 = 列表**时，列表每滚动一帧，源层就要重绘一次，再模糊一次。列表本身已经贵（Compose 布局 + 文本），模糊是额外的一整遍 draw + 一次 GPU blur。
- 不要给 LazyColumn 的**每个 item** 挂 `RenderEffect`：每个 item 一个离屏层 → 层数量 × 内存 + 每层一次合成；Haze 的 benchmark 里 40–45 个可见成员 + container 的 P90 比独立成员高约 0.5ms，**且两者都没达到 60Hz 预算**（E0 库文档）。
- 推荐：
  - 只给**吸顶栏/底栏/单张卡片**做玻璃（1 块），列表本身不挂效果。
  - 玻璃区域尽量小（Haze 性能指南第一条："Reduce the affected area"）。
  - 用 **mask 渐变**代替 progressive blur（视觉接近、成本可忽略，E0 库文档）。
  - 若必须大面积：`inputScale`/降采样（Haze 1.x 建议 0.66 → 像素量 −55%，0.33 → 仅 11%；E0 文档），代价是细节变软。
  - 滚动中降低质量（`HazePerformanceMode.Performance` / 自行切半径）是可接受的产品策略；Haze 2.x 的 `Balanced` 在 9 块玻璃场景下比 `Quality` 省约 21% CPU P90（E0 库文档）。
- 帧预算参考（E1/E0 库文档，Pixel 8a/Android 17/60Hz）：Glass `Quality` P90 余量 7.2ms，`Balanced` 8.2ms，`Performance` 9.1ms；`Performance` 峰值 GPU 内存比 `Quality` 低约 14%。→ **手机上一个玻璃场景的可用预算大致是 7–9ms 余量**，不是"随便加"。

### 4.3 玻璃在浮层里（Dialog / Popup / BottomSheet）

| 场景 | 同窗口重绘能拿到背景吗 | 可用方案 | 代价/限制 |
|---|---|---|---|
| window 内的浮层 Composable（自绘 BottomSheet） | ✅ 能（同一棵树） | §2.3 自绘 | 与列表同窗口 → 成本叠加（§4.2） |
| Compose `Dialog` | ❌ 不能（独立 window） | ① 窗口级模糊 API（31+，OEM 依赖）② Haze Sources 跨窗口 recapture ③ 把对话框改成同窗口浮层 | ① 可能静默失效；② 每次 snapshot 变化都重捕获，未测量成本（E0 文档）；③ 交互/返回键语义要自己实现 |
| Compose `Popup` / DropdownMenu | ❌ 同上 | 同上；Compose 1.13 才有 `PopupProperties.blurBehindRadius` | 本栈只能手动设 window 属性 |
| 系统级 BottomSheetDialog | ❌ | 窗口级模糊（`windowIsTranslucent` + `setBackgroundBlurRadius`） | 需要主题改动；模糊关闭时要改 dim/alpha |

**结论**：**"玻璃在浮层里"是本项目最脆的一环**——同窗口能拿到背景但看不到窗外；跨窗口能糊窗外但依赖 OEM 且不能自定义光学。产品上建议：浮层用"半透 + 纯绘制玻璃质感"（不承诺真模糊），或浮层在同窗口内实现。

---

## 5. 降级策略（API 26–30）

**事实清单（全部 E0）**
- `RenderEffect` / `createBlurEffect` / `View.setRenderEffect` / `Modifier.blur` 的有效性：**API 31+**；低版本被忽略。
- `TileMode.DECAL`：API 31。
- `RuntimeShader` / `createRuntimeShaderEffect`：API 33。
- 窗口模糊（`setBackgroundBlurRadius` / `setBlurBehindRadius`）：API 31。
- `GraphicsLayer` 的 record/drawLayer 在 1.7.0 可用，但 **1.7.0-beta04 起禁用软件渲染支持**（即：无 RenderEffect 时它只能"搬运绘制指令"，不能模糊）。
- RenderScript：Android 12 弃用、AGP 7.2 移除支持 → 不可用。
- 窗口模糊 API 在 API 31 以下不存在（`Window#setBackgroundBlurRadius` "Added in API level 31"）。

**四档降级梯度（建议写进实现）**

| 档位 | 条件 | 视觉能力 | 实现 |
|---|---|---|---|
| L0 全光学 | API ≥33 + 硬件加速 | 模糊 + 折射 + 色散 + rim + 自适应 tint | §2.3 + §2.4 |
| L1 真模糊 | API 31–32 | 模糊 + tint + rim + 噪声；无折射/色散（shader 不合法时用 try/catch 落回这里） | §2.3，`renderEffect` 里只放 blur/colorFilter/blend |
| L2 半透假玻璃 | API 26–30（默认推荐） | 半透 tint + 竖向渐变 + 噪声 + rim + 内阴影（不采样背景） | 纯绘制（A13）；与 Haze 在 ≤30 的 scrim 策略一致（E0） |
| L3 捕获式模糊（可选） | API 26–30，且产品接受成本 | 真 backdrop 模糊但延迟/掉帧 | 截图/`lockHardwareCanvas` 捕获 + CPU 模糊（A12）或引入 blur-cmp（API 24+，E1） |

**代码骨架**

```kotlin
// 能力判定：与 Haze 源码一致（E0：SDK>=31 && canvas.isHardwareAccelerated）
internal fun canUseRenderEffect(sdkInt: Int, canvas: android.graphics.Canvas): Boolean =
    sdkInt >= 31 && canvas.isHardwareAccelerated

@Composable
fun Glass(radius: Dp, content: @Composable () -> Unit) {
    val sdk = Build.VERSION.SDK_INT
    when {
        sdk >= 31 -> RenderEffectGlass(radius, content)   // L1（33+ 内部再加折射，见 §2.4）
        else -> PaintedGlass(content)                      // L2：半透 + 渐变 + 噪声 + rim
    }
}

@Composable
fun PaintedGlass(content: @Composable () -> Unit) {
    Box(
        Modifier
            .drawWithCache {
                val base = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f)),
                )
                onDrawBehind {
                    drawRoundRect(base, cornerRadius = CornerRadius(28.dp.toPx()))
                    drawRoundRect(                        // rim：内侧 1dp 亮边
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(0.5f, 0.5f),
                        size = Size(size.width - 1f, size.height - 1f),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(28.dp))
    ) { content() }
}
```

**其它建议**
- 把"是否模糊"做成运行时 fact（不只按 SDK）：记录一次 `isCrossWindowBlurEnabled`、`createBlurEffect` 是否抛异常、AGSL 是否能编译，写进 GlassLab 的诊断条（对齐 `GlassLabDiag` 的 `agslStatus` / `crossWindowBlurResult`）。
- 若产品咬定"必须有真模糊"，可把 minSdk 抬到 31（份额数据未取证 → §6）；否则接受 L2。

---

## 6. 待验证（必须真机/原型才能定论）

> 以下全部**不进结论**。每条给出验证方法。

1. **OLED / 暗色下模糊"发灰"**（E2 假设，未取证一手证据）
   - 假设 a：白 tint/scrim 在半透层上把暗部提亮；假设 b：`DECAL` 采样透明黑后与背景合成导致边缘/整体偏灰（预乘 alpha）；假设 c：sRGB 空间模糊使低亮度区对比被压缩。
   - 验证：纯黑 `#000` 背景 + 白色文字，同一玻璃参数分别用 `Clamp`/`Decal`、`tintAlpha=0`/0.1/0.2 出 6 张真机截图 + 取值（`img_digest`/取色），对比 RGB 均值。
2. **OEM 窗口模糊可用率**：手头真机（含国产 ROM）上 `Settings > Developer options > Allow window-level blurs` 是否存在；`adb shell wm disable-blur` 的输出；`getprop ro.surface_flinger.supports_background_blur`。结论决定 A10/A11 能否作为 P1 方案。
3. **API 31 的陈旧/冻结模糊是否也影响自绘方案**：Haze 1.x 在 API 31 有 #77；自绘每次 draw 重绘理论上不同，但需 API 31 真机（或 31 系统镜像）录屏检查滚动 3 秒内模糊是否更新。
4. **SurfaceView / TextureView（视频、相机预览）内容能否被"重绘进层"捕获**：E1 旁证 blur-cmp 需要专门的捕获路径才能处理 SurfaceView/TextureView（说明常规路径不行）。验证：背景放 `AndroidView(VideoView)`，看玻璃区域是否出现"黑块/未模糊"。
5. **性能预算**：1/3/5 块玻璃 × 滚动列表，在 60Hz 与 120Hz 机上的 Macrobenchmark（frame overrun P90、CPU frame duration P90）；软件模拟器数据不可用（E0：软件渲染不模糊）。
6. **半径上限**：低端 GPU 上 `createBlurEffect` 抛 `IllegalArgumentException` 的实际半径阈值（Haze 源码有该 try/catch，说明真实存在）；以及 `RuntimeShader` 编译失败率（Haze 有 `BlurRuntimeShaderFailure` 测试）。
7. **色散/折射的采样成本**：AGSL 1× / 3× / 7× 采样的 P90 帧时；确认 33+ 设备（Adreno vs Mali）差异（E1：Gracker wiki 指出 GPU 差异大）。
8. **Compose 升级可行性**：升到 1.9/1.12 + Haze 2.1 的收益（`haze-glass` 全光学）vs 成本（Kotlin 2.4、BOM/AGP 升级、回归面）。这一步决定"自研 vs 用库"。
9. **Haze 1.1.1 vs 1.5.4 在本栈的真实兼容性**：Kotlin 2.0.21 编译器读 2.1.0 元数据是否 OK（E2）；验证方式：只加依赖编译一个最小 sample。
10. **层内存**：每块全屏玻璃层的 RGBA8 内存与 `LayerOutsets`（1.12 才有）缺失时的边缘裁切观感；在 3 块玻璃下是否触发 GPU 内存压力。
11. **（若考虑 minSdk 31）** 目标用户里 API 26–30 的实际占比 —— 本次未查（未取证），需产品侧数据。

---

## 附：证据链接汇总

**Android 官方**
- Compose 模糊总览（语义/API/EdgeTreatment/渐进模糊）：https://developer.android.com/develop/ui/compose/graphics/blur/overview
- `GraphicsLayerScope.renderEffect` / `GraphicsLayer.renderEffect`：https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer
- Compose UI release notes（1.7.0 GraphicsLayer/软件渲染、1.13 渐进模糊与 Dialog blur 属性）：https://developer.android.com/jetpack/androidx/releases/compose-ui
- `RenderEffect`：https://developer.android.com/reference/android/graphics/RenderEffect
- `RuntimeShader`：https://developer.android.com/reference/android/graphics/RuntimeShader
- `Shader.TileMode`（DECAL 为 API 31）：https://developer.android.com/reference/android/graphics/Shader.TileMode
- AGSL（Android 13+，管线定位）：https://developer.android.com/develop/ui/views/graphics/agsl
- `Window.setBackgroundBlurRadius`：https://developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int)
- `LayoutParams.setBlurBehindRadius` / `FLAG_BLUR_BEHIND`：https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setBlurBehindRadius(int)
- `WindowManager` 跨窗口模糊监听：https://developer.android.com/reference/android/view/WindowManager#isCrossWindowBlurEnabled()
- AOSP 窗口模糊（OEM 条件/半径建议/开关/验证）：https://source.android.com/docs/core/display/window-blurs
- `DialogWindowProvider`：https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogWindowProvider
- `DialogProperties`（1.13 才有 blur 属性）：https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties
- RenderScript 弃用：https://developer.android.com/guide/topics/renderscript/compute
- 硬件加速：https://developer.android.com/guide/topics/graphics/hardware-accel

**AndroidX 源码**
- `Blur.kt`（`Modifier.blur` 实现与文档）：https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/draw/Blur.kt
- `GraphicsLayer.kt`：https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer.kt
- `GraphicsLayerScope.kt`：https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/graphics/GraphicsLayerScope.kt

**Haze（库源码/文档，均一方）**
- 仓库：https://github.com/chrisbanes/haze ｜ 文档站：https://chrisbanes.github.io/haze
- 1.5.4 机制：`HazeSourceNode.kt` https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeSourceNode.kt ｜ `RenderEffect.android.kt` https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/androidMain/kotlin/dev/chrisbanes/haze/RenderEffect.android.kt ｜ `HazeEffectNode.android.kt` https://github.com/chrisbanes/haze/blob/1.5.4/haze/src/androidMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.android.kt
- 1.5.4 FAQ（API 30 scrim、API 31 默认关）：https://github.com/chrisbanes/haze/blob/1.5.4/docs/faq.md ｜ usage：https://github.com/chrisbanes/haze/blob/1.5.4/docs/usage.md
- 平台支持（31/33/≤30、RenderScript、API 31 recapture 坑）：https://github.com/chrisbanes/haze/blob/main/docs/blur/platforms.md
- 性能（预算、inputSharing、Glass 数值）：https://github.com/chrisbanes/haze/blob/main/docs/performance.md
- Glass 文档（光学要素、fallback 表）：https://github.com/chrisbanes/haze/blob/main/docs/effects/glass/index.md
- 核心概念（Backdrop 跨窗口限制、Dialogs）：https://github.com/chrisbanes/haze/blob/main/docs/core-concepts.md
- ADR-0003（Android fused glass renderer、实测帧时）：https://github.com/chrisbanes/haze/blob/main/docs/adr/0003-use-one-android-fused-glass-renderer.md
- issue #77（API 31 冻结）：https://github.com/chrisbanes/haze/issues/77 ｜ issue #520（大半径色溢出）：https://github.com/chrisbanes/haze/issues/520 ｜ issue #1726（降采样边缘泄漏）：https://github.com/chrisbanes/haze/issues/1726

**其它库 / 二手**
- Cloudy：https://github.com/skydoves/Cloudy
- blur-cmp（API 24+ OpenGL Dual Kawase）：https://github.com/ezoushen/blur-cmp
- Dimezis/BlurView：https://github.com/Dimezis/BlurView
- Gracker/android-internals-wiki（RenderEffect 成本分析，E1）：https://github.com/Gracker/android-internals-wiki/blob/master/src/part5-app/ch22-rendering-practice/08-runtime-effects-compose-canvas.md

**iOS Liquid Glass**
- WWDC25 "Meet Liquid Glass"（折射/高光/自适应口述，E1）：https://developer.apple.com/videos/play/wwdc2025/219/
- Adopting Liquid Glass：https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass
- HIG Materials：https://developer.apple.com/design/human-interface-guidelines/materials
- SwiftUI `Glass`（iOS 26 API）：https://developer.apple.com/documentation/swiftui/glass
