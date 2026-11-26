# 前端设计体系与安卓玻璃调研（2026-11-26）

> 对象：myco（Kotlin + Jetpack Compose，`minSdk 26 / compileSdk 35 / targetSdk 35`，BOM `2024.10.01`，Kotlin 2.0.21，AGP 8.7.3）。
> 口径：只读公开网页（未 clone / 未安装 / 未跑真机 / 未调模型 API）。GitHub 直连本机 `fetch failed`，改走官方文档、googlesource、Haze 文档站与搜索摘要。
> 证据标记：`[官方]` 官方文档/源码/发布说明 · `[库文档]` 三方库官方文档 · `[社区]` 社区实测/教程 · `[观察]` 我看参考截图的判断 · `[推断]` 我的判断，未实测。
> 目标：把「风格化」收敛成 token + 组件契约，把「强透明感」拆成可开关的能力等级，把「侧滑退出」改成可预测的返回栈。

---

## 0. 一页结论（≤20 行）

1. **动效留 3 个主档**：`press`（弹簧 0.6/800，约 120–180ms）、`enter`（弹簧 0.8/380，M3 Expressive spatial default）、`effects`（弹簧 1.0/1600，只给颜色/透明度）；另有 `gesture`（320ms tween，抽屉/Sheet 跟手）、`exit`（180ms）、`quick`（120ms）。禁止为了「质感」加弹跳。
2. **按压反馈 = ripple 为主 + 仅按钮做 scale 0.98**。列表行/消息行不缩放（会抖）；图标按钮用无界 ripple；触觉只给 3 个时刻（长按、危险确认、错误），其余静音。
3. **中文正文 16sp / 26sp（1.625）**，次级 14/22，辅助 13/20，标题 20/28。M3 默认 16/24（1.5）对中文偏紧，且 `lineHeight` 会被字体自带 padding 吃掉——必须 `PlatformTextStyle(includeFontPadding=false)`，单行标签再 `LineHeightStyle(trim=Both)`。
4. **列表项高度**：会话行 64–72dp（两行）、设置行 56dp、触摸目标 48dp、左右 16dp。图标 24dp + 文字间距 12–16dp；48dp 目标的公式是 `(48 − icon)/2 = 12dp` 内边距。
5. **玻璃结论：Compose 没有 backdrop blur**；`Modifier.blur` 是「自己的内容模糊」（API 31+ 均匀 / 33+ 渐变）`[官方]`。真背景模糊只能靠 **Haze**（捕获背景图层再模糊，Android 12+ 真模糊、以下退回 scrim）或 **Android 17 QPR2 的 `RenderNode.setBackdropRenderEffect`**（实验、仅新系统）`[官方][库文档]`。
6. **窗口模糊**（`Window.setBackgroundBlurRadius` / `blurBehindRadius`）只对「浮起的独立窗口」有意义（对话框/弹窗），API 31+，要求窗口 translucent+floating，且可能被设备/GPU/省电关闭——必须查 `WindowManager.isCrossWindowBlurEnabled()` `[官方]`。Compose 1.13 已把它做进 `DialogProperties` `[官方]`。
7. **myco 分级**：L0 半透明+边框（现在，默认）→ L1 Haze 真模糊（顶部栏/输入区/抽屉，可选开关，Android 12+）→ L2 高光/边缘折射点缀（1 个焦点元素）→ **L3 全液态不做**（逐像素折射需每帧采样背景 + AGSL，代价不值）。
8. **返回手势**：现在 `MainActivity` 用 `var screen` 手写状态、没有任何 `BackHandler`、manifest 也没 `enableOnBackInvokedCallback`——侧滑必然退出 App。修法是三步：manifest 开 predictive back → 把 `Screen` 换成返回栈 → 抽屉/Sheet 用 M3 自带 predictive back。**MVP 不做应用内边缘滑抽屉**（与系统返回手势冲突）。
9. **建议的 NOW 清单**：motion token + 按压层 + 中文行高/行高对齐 + 返回栈（4 件事，改 `Theme.kt`/`Components.kt`/`MainActivity.kt` 与 manifest，不动后端）。玻璃与 L2 放 M6。
10. **升级约束**：Compose 1.13 / material3 1.5.0 需要 Kotlin 2.2、compileSdk 37.1、AGP 9.2 `[官方]`；Haze 2.1 按最新 Compose 构建。**当前技术栈下真模糊要单独立一个升级 spike，不能顺手加依赖。**

---

## 1. 动效系统与 token

### 1.1 官方有什么（先对齐概念）

| 东西 | 事实 | 证据 |
|---|---|---|
| M3 两套运动体系 | 旧体系 = easing + duration（`md.sys.motion.duration.*` / `easing.*`）；新体系 = **spring 物理 token**（damping + stiffness），M3 推荐用 spring | [m3 motion specs](https://m3.material.io/styles/motion/overview/specs) `[官方]` |
| Compose 的入口 | `MaterialTheme.motionScheme`：`defaultSpatialSpec()` / `fastSpatialSpec()` / `slowSpatialSpec()` + effects 对应物（空间动作用 spatial，颜色/透明度用 effects，后者不允许过冲） | [MaterialTheme 参考](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialTheme) `[官方]` |
| 具体数值（源码） | Expressive：spatial fast `0.6/800`、default `0.8/380`、slow `0.8/200`；effects `1.0/3800`、`1.0/1600`、`1.0/800`。Standard：spatial `0.9/1400`、`0.9/700`、`0.9/300` | [ExpressiveMotionTokens.kt](https://android.googlesource.com/platform/frameworks/support/+/78929c1b1cdb3ce6deec04b74da5a8cbb30d9360/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt) `[官方源码]` |
| 旧 token（兜底用） | duration 50/100/150/200/250/300/350/400/450/500/550/600/700/800/900/1000；emphasized `cubic-bezier(0.2,0,0,1)`、emphasized-decelerate `(0.05,0.7,0.1,1)`、emphasized-accelerate `(0.3,0,0.8,0.15)` | [MotionTokens.kt](https://android.googlesource.com/platform/frameworks/support/+/78929c1b1cdb3ce6deec04b74da5a8cbb30d9360/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/MotionTokens.kt) `[官方源码]` |
| Compose 动画 API | `animate*AsState`（单值）、`updateTransition`（多值同驱）、`Animatable`（串行/手势）、`AnimatedVisibility`（进出+`animateEnterExit`）、`AnimatedContent`（内容切换+`SizeTransform`）、`animateContentSize` | [Compose 动画速查](https://developer.android.com/develop/ui/compose/animation/quick-guide) `[官方]` |
| 默认 spec | Compose 大多数动画默认 spring，可中断、带速度 | 同上 `[官方]` |
| 主题默认方案 | `MaterialExpressiveTheme` 默认用 expressive motion scheme，组件内直接用 `MaterialTheme.motionScheme.*Spec()` 就能统一 | [M3 稳定公告](https://m3.material.io/blog/compose-material-3-is-stable) `[官方]` |
| 新东西（2026) | Compose 1.13 稳定 `DeferredTransition`、新增 `CapturedAnimatedVisibility`（退出时抓最后一帧，避免状态清空闪烁，退出帧耗时快 7–8%） | [Compose Nov'26 发布博客](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release) `[官方]` |

### 1.2 myco 动效 token（推荐，写进 `MycoMotion`）

只留 3 个主档（`press` / `enter` / `effects`）+ 3 个专用档（`gesture` / `exit` / `quick`）。**同一屏可见的时长种类 ≤3**。

| token | 值 | 用途 | 理由 |
|---|---|---|---|
| `press` | `spring(dampingRatio=0.6f, stiffness=800f)` | 按压缩放、chip 选中、开关滑块 | spatial fast，收束快不过冲 `[官方源码]` |
| `enter` | `spring(dampingRatio=0.8f, stiffness=380f)` | 气泡/列表项入场、Snackbar 进出、焦点光晕 | spatial default，M3 Expressive 推荐值 |
| `effects` | `spring(dampingRatio=1.0f, stiffness=1600f)`（或 `tween(120)`） | 颜色、alpha、边框亮度 | effects 系禁过冲；颜色渐变不要 spring 视觉抖动 |
| `gesture` | `tween(320, CubicBezierEasing(0.2f, 0f, 0f, 1f))` | 抽屉开合、Sheet 跟手/提交、预测返回提交 | 手势要可预测时长，弹簧在跟手场景不好对齐 |
| `exit` | `tween(180, CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))` | 元素消失（不要和 enter 同长） | emphasized-accelerate |
| `quick` | `tween(120, LinearEasing)` | 新内容块淡入、骨架 shimmer 周期 | 流式场景只允许这个 |

**场景表**（克制版）：

| 场景 | 入场 | 出场 | 备注 |
|---|---|---|---|
| 按压缩放 | scale 1→0.98，`press` | 松手回 1，`press` | 只用于 Button/发送键/可点卡片；列表行不缩放 |
| 列表项入场 | 首帧 `alpha 0→1` + `translateY 8dp→0`，`enter`；同屏 stagger 20ms、最多 6 项 | 不做出场（滚动回收） | 用 `AnimatedVisibility` 的首帧或 `animateItem`；**不要**每帧查可见性 |
| 抽屉开合 | `gesture` 320ms，内容从 -width 滑入，遮罩 alpha 0→0.32 | 同 | 预测返回时用 progress 驱动，不用 spring |
| Sheet | 交给 M3 `ModalBottomSheet`（自带 physics） | 同 | 只统一遮罩透明度 0.32 |
| Snackbar | 底部 8dp 上浮 + fade，200ms | fade，150ms | 时长用 `SnackbarDuration.Short`（约 4s） |
| 助手消息出现 | 整条 fade 120ms；**流式期间不逐字动画** | — | 逐 token 动画=每帧 relayout，掉帧 |
| 流式光标 | 0.53s 周期闪烁（`rememberInfiniteTransition`） | — | 只闪光标，不动文本 |
| 错误条 | 高度展开 + fade 200ms | 收缩 150ms | 展开前先测高，避免跳动 |
| 页面 push/pop | 300ms，右进右出 + 0.92 缩放 | 同 | 设置页；聊天→首页用 fade |

**实现注意**
- 用 lambda 版 modifier（`offset { }` / `graphicsLayer { }`）把动画留在 draw 阶段，避免 composition/relayout `[官方]`。
- `animateContentSize` 只用于「一轮回答从流式中到完成」的一次性收尾，不要每帧调用。
- 长按菜单/菜单展开用 120ms fade + 8dp 位移，不要 scale（菜单缩放会显得廉价）。
- 主题切换（浅/深）只允许 crossfade 220ms；不要给每个组件单独做颜色动画。

---

## 2. 按压 / 触摸反馈

### 2.1 官方能力

| API | 事实 | 证据 |
|---|---|---|
| ripple | `bounded=true` 裁在组件内、从触点扩散；`false` 从中心扩散（适合图标/圆形按钮）；`radius` 可固定；可关 press 视觉 | [material3 ripple](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary) `[官方]` |
| 全局注入 | `LocalIndication` 提供默认 indication，`Modifier.clickable`/组件都走它；M3 用 `ripple()`；`RippleTheme` 已废弃 | [LocalIndication/IndicationNodeFactory](https://developer.android.com/reference/kotlin/androidx/compose/foundation/IndicationNodeFactory) `[官方]` |
| 按压态 | `MutableInteractionSource` + `collectIsPressedAsState()` / `collectIsFocusedAsState()`，可驱动 scale / 边框 / 光晕 | [InteractionSource 参考](https://developer.android.com/reference/kotlin/androidx/compose/foundation/interaction/InteractionSource) `[官方]` |
| 触觉 | `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.*)`；类型已扩到 `Confirm`/`Reject`/`ToggleOn`/`ToggleOff`/`SegmentTick`/`GestureEnd`/`VirtualKey` 等 | [hapticfeedback 包](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/package-summary) `[官方]`；类型列表见 Compose 发布说明 |
| 新细节 | Compose 1.13 起 `Modifier.clickable` 自动播系统点击音效（尊重系统设置） | [Compose Nov'26 博客](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release) `[官方]` |
| 触摸目标 | M3 组件默认把可点区域补到 48dp（`LocalMinimumInteractiveComponentSize`） | [material3-common 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-material3) `[官方]` |

### 2.2 各组件策略（myco 定稿）

| 组件 | ripple | scale | 触觉 | 说明 |
|---|---|---|---|---|
| 主按钮（发送/确认） | bounded，颜色用 `onSurface` 8–12% | 按下 0.98，`press` | 发送不震（太频繁）；「危险确认」用 `Reject`/`Confirm` | scale 只作用在 `graphicsLayer` |
| 次按钮/文字按钮 | bounded | 0.98 | 无 | |
| 图标按钮（返回/更多/加号） | **unbounded**（圆形中心扩散） | 0.98（可选，同屏 ≤2 个） | 长按才震 | 参考产品里图标按钮多为「深色圆底 + 按下提亮」，我们保持 ripple + 可选底色 |
| 会话行/设置行 | bounded，整行 | **不缩放** | 长按 `LongPress` | 行内 `⋯` 独立按钮，也 unbounded |
| 消息气泡 | 长按出菜单：无 ripple，长按 300ms 后 `LongPress` 触觉 + 轻微底色变化 | 不缩放 | `LongPress` | 点击不做事时不要给 ripple（会误导可点） |
| 输入区 | 文本框自身无 ripple | 发送键 0.98 | 无 | 聚焦反馈 = 边框提亮/发光（现有 `GlassSurface(highlight)` 已具备，改成 `effects` 120ms） |
| 抽屉遮罩 | 无 | 无 | 无 | 点击关闭 |
| Sheet 拖拽 | 无 | 无 | 到锚点 `SegmentTick`（可选） | 用 M3 自带 |

**统一实现骨架**（写进 `ui/primitives/Pressable.kt`）：
```kotlin
val interaction = remember { MutableInteractionSource() }
val pressed by interaction.collectIsPressedAsState()
val scale by animateFloatAsState(if (pressed) 0.98f else 1f, MycoMotion.press, label = "press")
Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = LocalIndication.current) { onClick() })
```
- 自定义 indication 只在确实需要时写（如「按下变亮」的图标按钮）；**不要**为了统一而把 ripple 全局替换掉——Android 用户预期就是 ripple。
- 禁用态：`clickable(enabled=false)` + alpha 0.38，无 ripple。

### 2.3 与 iOS / DeepSeek / ChatGPT 的差异（[观察] 来自 `shots/` + [推断]）

| 平台/产品 | 按压语言 | 结论 |
|---|---|---|
| iOS / Liquid Glass | 没有 ripple；用透明度/灰色覆盖 + scale 微缩；玻璃表面还会随按压改变高光 | 不能照搬：Android 上瞬间变透明度会「闪」，且深色 OLED 上更明显 |
| DeepSeek（截图） | 深灰圆底图标按钮，按下提亮；输入区是深色大圆角卡片 + 胶囊 chip | 我们的图标按钮可以「无界 ripple + 按下提亮 8%」近似 |
| ChatGPT（截图） | 同样深灰圆底图标按钮；发送键是高对比实心圆，按下有明显颜色变化、几乎不缩放 | 主按钮强调颜色反馈，缩放只做很轻的 0.98 |
| DSH Go（截图） | 顶栏胶囊、底部输入胶囊、过程折叠行；整体几乎无装饰动效，信息密度优先 | 与「克制」方向一致：myco 不要把动效做成卖点 |

---

## 3. 排版、行高与光学对齐

### 3.1 中文正文行高

| 依据 | 结论 | 证据 |
|---|---|---|
| M3 默认 bodyLarge 16/24（1.5） | 对拉丁文合适，对中文偏紧 | M3 type scale `[官方]` |
| 移动端中文行距研究/标准草案 | 手机中文行间距宜 1.4–2 倍，舒适约 **1.6 倍** | 移动终端中文呈现工效学标准草案（ICS 13.180）`[社区/标准草案]` |
| 通用移动端排版建议 | 正文 ≥16sp，行高 1.4–1.6 | 社区排版参考 `[社区]` |
| iOS 对照 | iOS 正文 17pt，中文行高更松 | Apple HIG（经二手转述）`[社区]` |

**myco 字号/行高表**（`MycoType`，全部 `FontFamily.Default`，中文优先系统字体）：

| token | size/line | 字重 | 用途 |
|---|---|---|---|
| `message` | 16 / 26（1.625） | Normal | 聊天正文（中文长文主力） |
| `body` | 16 / 24（1.5） | Normal | 表单、设置值、单行正文 |
| `bodySmall` | 14 / 22（1.57） | Normal | 会话摘要、说明 |
| `caption` | 12 / 18 | Normal | 时间、计数、免责声明 |
| `section` | 13 / 20 | Medium | 分组头（置顶/今天…），可加 0.02em 字距 |
| `title` | 20 / 28 | SemiBold | 页面标题、空态标题 |
| `titleSmall` | 16 / 24 | Medium | 卡片标题、会话标题 |
| `button` | 14 / 20 | Medium | 按钮/标签 |
| `code` | 13 / 20 | Mono | 行内代码/代码块 |
| `numeric` | 继承 + `fontFeatureSettings = "tnum"` | — | 计时、token 数、金额（等宽数字不跳） |

段落规则：消息段落间距 8dp；块（列表/代码/引用）上下 12dp；列表项内标题与摘要间距 2–4dp；行内不要塞比正文大 4sp 以上的 span（会撑行）。

### 3.2 Compose 落地（必须写对的三个点）

```kotlin
val messageStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 16.sp,
    lineHeight = 26.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
```
1. **`includeFontPadding = false` 是前提**：不关掉字体自带上下 padding，`lineHeight` 设了也不严格生效 `[官方]`。
2. **`trim` 用在单行标签**：单行标题+图标、按钮文字、chip 用 `Trim.Both`，让行盒=字号盒，居中才准 `[官方]`；多行正文用 `Trim.None`（否则段首被削）。
3. **混排字号**：Compose 1.13 有 `LineHeightStyle.Mode.PerLine` 可逐行算高 `[官方]`；当前 BOM 没有，替代方案是把整段 `lineHeight` 取段内最大字号对应的值，禁止在 16sp 段落里塞 22sp 的 `SpanStyle` 而不改行高。

### 3.3 行内 icon 与文字「重心同轴」

问题本质：`Row(verticalAlignment = CenterVertically)` 居中的是**行盒**，不是字形视觉中心；中文行高 1.6 时上下 leading 不对称，icon 会显得偏下 1–2dp。

| 做法 | 适用 | 规则 |
|---|---|---|
| `CenterVertically` + `Trim.Both` | 单行文字 + icon，最常用 | 文本用单行 `titleSmall`/`body`，`trim=Both` 后行盒≈字形盒，视觉上就正 |
| `Modifier.alignByBaseline()` | 文字 vs 文字/数字（标题+时间、金额+单位） | icon 没有基线，不能直接与 icon 对齐 |
| `Modifier.offset(y = (-1).dp)` | 微调兜底 | 允许 ≤2dp，且必须写注释「字体相关微调」，换字体要重测 |
| 自定义 Row：icon 放进固定 24dp Box 再 `alignBy` 文本基线 | 需要精确光学对齐的顶栏 | 计算 `iconCenterY = baselineY - capHeight/2`，`capHeight ≈ 0.7 × fontSize` `[推断]` |

**尺寸公式**：
- 触摸目标 48dp、图标 24dp → 内边距 `(48−24)/2 = 12dp`；图标 20dp → 14dp。
- 列表行：左 16dp、右 16dp、icon 24dp、icon 与文字间距 12–16dp；行末 `chevron` 用 16–20dp、右 16dp。
- 行高：M3 列表 56（一行）/ 72（两行）/ 88（三行）`[官方]`（[M3 Lists](https://m3.material.io/components/lists/specs)）；myco 会话行取 **64–72dp**（标题 24 + 摘要 2 行 + 上下 10），设置行 **56dp**。
- 卡片内边距 16dp；气泡（用户）内 12dp 上下 / 14dp 左右；抽屉宽 82–85%（沿用 `80-ui-component-inventory.md`）；抽屉内边距 16dp。
- 触摸目标：所有可点元素 ≥48dp；小图标按钮用 `Modifier.minimumInteractiveComponentSize()` 或 Material3 默认补齐。

### 3.4 中英混排 / 数字与单位 / 省略号

| 场景 | 规则 | 说明 |
|---|---|---|
| 中英混排 | 不在代码里手插空格，靠系统字体排；中英之间视觉间距由字体本身决定 | 手工插空格会把「Kotlin 协程」变成不可搜索文本 `[推断]` |
| 数字与单位 | `31%`、`¥16.64`、`1分07秒` 不空格；时间列用 `numeric` 等宽数字 | 避免每帧宽度变化导致抖动 |
| 省略号 | UI 文案用单字符 `…`（不是 `...`）；列表截断用 `TextOverflow.Ellipsis` + `maxLines` | 双行摘要 `maxLines=2`；标题 `maxLines=1` |
| 中英标点 | 中文文案用全角标点；括号内英文用半角 | 与参考产品一致 |
| 截断位置 | 标题截尾；模型名/路径等标识类允许中段截断（`Text` 目前不支持，需自绘或省略） | P2 |

---

## 4. 全局 token 组织与低耦合边界

### 4.1 目录与职责（建议落地形状）

```
ui/tokens/  Color.kt Type.kt Space.kt Shape.kt Motion.kt Glass.kt Opacity.kt Elevation.kt IconSize.kt
ui/theme/   MycoTheme.kt（ColorScheme + Typography + Shapes + CompositionLocal）
ui/primitives/  Pressable.kt MycoButton.kt MycoCard.kt GlassSurface.kt MycoListItem.kt MycoSheet.kt MycoDrawer.kt
ui/domain/   SessionRow.kt MessageBlock.kt Composer.kt ProcessFold.kt
ui/screens/  ChatScreen.kt HistoryScreen.kt SettingsScreen.kt ...
```
三层：**tokens（可变）→ primitives（稳定）→ domain（业务）**；换皮只动 tokens + primitives 的外观参数，不动 domain。

### 4.2 CompositionLocal 选择

| Local | 类型 | 理由 |
|---|---|---|
| `LocalMycoColors`（已有） | `staticCompositionLocalOf` | 稀有变化；换主题时整棵重组可接受 |
| `LocalMycoType` / `LocalMycoMotion` | `staticCompositionLocalOf` | 同上 |
| `LocalMycoGlass`（能力，不是样式） | `staticCompositionLocalOf` | 存 `GlassCapability(level, enabled, maxRadius, perfMode)`；**能力**由设备/设置决定，组件只读 |
| `LocalMycoDensity`/fontScale | 不用自建，读系统 | 无障碍字号交给 sp 自动缩放 |

规则：**CompositionLocal 只放「跨层共享的只读上下文」**，不放可变交互状态（交互状态一律 hoist 到 screen/ViewModel）。

### 4.3 「可换 UI、不换交互」的边界

| 层 | 可以变 | 不能变 |
|---|---|---|
| tokens | 颜色、圆角、动效曲线、玻璃等级 | 语义名（`textPrimary`、`bg`…） |
| primitives | 外观、ripple 颜色、按压动画 | 交互契约：回调集合、状态枚举、语义动作、返回优先级 |
| domain | 布局密度、信息层级 | 状态机与数据契约（流式四态、抽屉语义、返回顺序） |

具体约束（写进 README/注释，评审时检查）：
1. domain/screens **禁止**直接读 `MaterialTheme.colorScheme`/硬编码 hex；只能经 `MycoTheme` 扩展 token。
2. 每个 primitive 必须暴露 `Modifier` + `interactionSource`（可选）+ 状态参数（default/pressed/selected/disabled/loading），即「状态完备」。
3. 玻璃是**能力**：`MycoGlassCapability` 由根节点解析一次；组件用 `if (glass.level >= L1 && glass.enabled)` 走真模糊分支，否则走 L0。不要在每个组件里判断 SDK_INT。
4. 换肤不影响导航/返回：返回栈与手势逻辑放 `ui/navigation/`（或接 Navigation 3），不放在视觉组件里。

---

## 5. 安卓毛玻璃 / 液态玻璃：能力、限制、实现、性能（重点）

### 5.1 能力矩阵（2026-11 现状）

| 能力 | API 下限 | 能做什么 | 不能做什么 | 证据 |
|---|---|---|---|---|
| `Modifier.blur` | 均匀 **API 31+**；渐变/自定义 shader **API 33+**；以下平台直接忽略 | 模糊「组件自己画的内容」（图片、标题、子布局） | **不能**模糊背后的内容（官方明确区分 surface blur / backdrop blur） | [Compose 模糊文档](https://developer.android.com/develop/ui/compose/graphics/blur/overview) `[官方]` |
| `BlurRadiusSpec`（渐变模糊） | 同上 | 垂直/水平/径向渐变、`BlurStop` 多段、自定义 `Shader` 驱动 alpha | 空间变化半径上限 **150px**（均匀无上限） | [BlurRadiusSpec 参考](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/blur/BlurRadiusSpec) `[官方]` |
| `graphicsLayer.renderEffect = BlurEffect` | API 31+ | 对自己这个图层做 RenderEffect（可组合 blur+offset 等） | 同样只看自己图层 | RenderEffect 文档 `[官方]` |
| **`RenderNode.setBackdropRenderEffect`** | **Android 17 QPR2（SDK 37.2）**，实验 | 对「已经画在节点后面的内容」应用效果——**真 backdrop** | 只在新系统；Haze 里是 opt-in 实验路径，需要 fallback | [RenderNode 参考](https://developer.android.com/reference/android/graphics/RenderNode) `[官方]`；Haze 2.0 公告 `[库文档]` |
| `Window.setBackgroundBlurRadius` | API 31+ | 模糊窗口背后的屏幕 **只在窗口边界内**；窗口需 translucent + floating | 全屏 Activity 没意义；部分设备/GPU 不支持；可被省电关闭 | [AOSP window blur](https://source.android.com/docs/core/display/window-blurs) `[官方]` |
| `LayoutParams.setBlurBehindRadius` + `FLAG_BLUR_BEHIND` | API 31+ | 模糊窗口背后**整屏** | 同上；运行时开关需查 `WindowManager.isCrossWindowBlurEnabled()` | 同上 + [WindowManager 参考](https://developer.android.com/reference/android/view/WindowManager) `[官方]` |
| Compose 1.13 `DialogProperties` | 1.13 / 平台 31+ | **Dialog/Popup 直接配 `backgroundBlurRadius` / `blurBehindRadius` / `scrimAlpha` / `windowShape`**——不用自己摸 Window | 只对 Dialog/Popup 窗口生效，不是窗口内任意 Composable | [Compose Nov'26 博客](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release) `[官方]` |
| AGSL `RuntimeShader` | API 33+ | 自写 shader：折射位移、色散、渐变边框（`Modifier.border` 1.13 起支持 `ShaderBrush`） | 采样不到「背后的屏幕内容」（除非配合离屏捕获） | [Compose 1.13 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-ui) `[官方]` |
| Haze | API 23+；**真模糊 API 31+**，31/32 有取舍；≤30 用 scrim，可选 RenderScript（实验、慢） | backdrop 真模糊：捕获背景层→定位→RenderEffect 模糊 + tint + noise；2.x 加 Glass（折射/高光） | 成本不低；多窗口/嵌套滚动/性能模式要调 | [Haze platforms](https://chrisbanes.github.io/haze/latest/blur/platforms/) `[库文档]` |

### 5.2 Haze 原理（为什么它能做「真背景模糊」）

1. `hazeSource(state)` 把背景内容注册进一个图形层（随滚动/动画记录位置与内容）；
2. `hazeEffect(state)` 在目标位置捕获这块内容、按坐标贴回，再用 `RenderEffect`（shader 多 pass）模糊，叠加 tint/noise；
3. 2.x 把 blur 拆成模块（`haze-blur`），2.1 起 `hazeGlass` 稳定：blur + 折射 + 光照 + tint，可响应 hover/focus/press，可合并/分离（`hazeGlassContainer`）`[库文档]`；
4. Android 12/12L：progressive blur 在高质量模式下多次绘制（贵），低质量模式退化为渐变遮罩；**API 31 有个坑**：任何 snapshot state 变化都会触发背景重捕获（哪怕动画无关），所以 API 31 上不要「一边模糊一边跑无关动画」`[库文档]`；
5. Android 17 QPR2：可切到新平台 backdrop 路径（`HazeInput.Backdrop`，实验，需要 fallback）；早期基准里原生路径的 CPU 帧时间反而**高于** Haze 原路（Haze 2.1 有输入共享优化）`[库文档]`。

**典型用法（1.x 语义，2.x 改为 `hazeBlur`/`hazeGlass` + style）**：
```kotlin
val haze = rememberHazeState()
Box(Modifier.fillMaxSize().hazeSource(haze)) { Content() }          // 背景注册
TopBar(Modifier.hazeEffect(haze) { /* blurRadius, tint, noise */ })  // 目标面
// Haze 2.x：Modifier.hazeBlur(input = HazeInput.Sources(haze), style = …)
```
约束：`hazeSource` 和 `hazeEffect` 之间不能隔独立窗口（Dialog 要单独处理），否则捕获不到 `[库文档]`。

**性能数字（Haze 官方基准，Pixel 6 / Pixel 8a，P90 CPU 帧）**：
- Haze 1 → 2：图片列表滚动 14.9ms → 9.3ms（−37%），卡片拖动 11.5 → 9.8ms `[库文档]`；
- Glass（Pixel 8a，60Hz 帧预算 16.7ms）：Adaptive 默认留 8.9ms 裕量；Quality 7.0ms；Performance 9.0ms `[库文档]`；
- 2.1：Glass 峰值 GPU 内存比 2.0.1 少约 7%，Blur `Quality` 少 ≥9% `[库文档]`；
- 社区经验：Haze 一般给每帧加 **2–6ms**，低端机容易掉帧 `[社区]`。

### 5.3 窗口模糊与「系统 UI 为什么看起来能毛玻璃」

- 系统通知栏/控制中心/多任务用的是 SystemUI 自己的窗口模糊与合成（AOSP 内部的 blur 管线），**第三方 App 拿不到同一套**；App 只有 5.1 表里的窗口级 API `[官方]`。系统的「材质」不是公开 API，所以别指望「调一个 flag 就和控制中心一样」。
- 窗口模糊不是「必然可用」：设备 GPU 限制、省电模式、视频隧道、`minimal post processing` 都会关掉它；**必须查 `isCrossWindowBlurEnabled()` 并监听变化**（Compose 1.13 提供 `WindowInfo.isCrossWindowBlurEnabled`）`[官方]`。
- 国产 ROM（MIUI/HyperOS、ColorOS 等）：系统组件模糊很普遍，但第三方浮动窗口的 cross-window blur 普遍**不可依赖**——这不是文档承诺的能力，只能运行时检测 + 降级 `[推断]`（未见官方逐厂说明；**必须真机验，不能写进设计前提**）。
- 对 myco 的含义：**抽屉/输入区在同一个 Activity 窗口里 → 窗口模糊帮不上**；只有把 Sheet/Dialog 做成独立对话框窗口才有戏（Compose 1.13 DialogProperties 正好覆盖这条）`[推断]`。

### 5.4 液态玻璃（iOS 26）在安卓上的近似

Apple 官方描述：Liquid Glass 是「半透明、像真实玻璃一样行为」的新材质，**实时渲染**、随运动产生**镜面高光**，并自适应内容与环境 `[官方]` [Apple Newsroom 2025-06](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/)。

| 液态玻璃要素 | 安卓可做性 | 做法 | 代价 |
|---|---|---|---|
| 背景模糊 | ✅ 可做（L1） | Haze（31+）；或 Compose 1.13 DialogProperties（独立窗口） | 每帧捕获+GPU pass |
| 着色 tint / 明暗自适应 | ✅ 可做 | Haze color effects + 主题色随背景亮度（需采样）| 低 |
| 边缘高光（specular） | ✅ 近似 | 1dp 渐变 border + 顶部左上高光 overlay；`Modifier.illuminate`/`Light`（Compose 1.14 alpha）[官方] | 低 |
| 边缘折射 / 色散（lensing） | ⚠️ 近似 | AGSL `RuntimeShader` 做 UV 偏移 + 轻微 chromatic aberration，套在 `Modifier.border` 的 `ShaderBrush`（1.13+）| API 33+，需调和 shader 与形状 |
| 随运动的高光 | ⚠️ 近似 | 高光位置绑定滚动/触摸/陀螺仪（`SensorManager`），1 个焦点元素 | 中；别全屏 |
| 真实逐像素折射背景 | ❌ 不建议 | 需要每帧捕获背景纹理再在 shader 里采样（自研离屏管线），只有 Android 17 QPR2 原生 backdrop 才可能低成本 | 高，低端机必炸 |
| 形变/合并（玻璃控件融合） | ✅ 可做 | Haze 2.1 `hazeGlassContainer`；或 `MorphPolygonShape`（1.13）做形状动画 | 中 |

结论：**「看起来像」能做到 80%（模糊+高光+边缘），「物理上正确」做不到**；不要承诺「真液态玻璃」。

### 5.5 myco 落地分级（定稿）

| 级别 | 内容 | API 下限 | 性能/发热 | 默认 | 降级 |
|---|---|---|---|---|---|
| **L0**（现在） | 半透明 tint（浅 `#F2FFFFFF` / 深 `#26FFFFFF`）+ 1dp 渐变边框 + 深色边缘发光（`E09A42` ≤16%）+ 阴影/高光 | 全版本（minSdk 26） | 接近 0 | ✅ 默认 | — |
| **L1** | Haze 真背景模糊：只给 **顶栏、输入区、抽屉遮罩下内容**（≤3 个面）；radius 16–24dp；Adaptive 性能模式；noise 2–3% | Android 12+；≤11 自动 scrim | 每帧 +2–6ms；滚动时 GPU 上升 | 开关默认「跟随设备」：API 31+ 且非低内存 → 开 | 关开关 / 低电量 / 低端机 → L0 |
| **L2** | 液态点缀：**只给输入卡片一个焦点元素**——顶部高光渐变 + 边缘 1dp 折射感边框（AGSL 33+ 或纯渐变）+ 极轻的色散；随滚动做 1 层高光位移 | 高光全版本；AGSL 33+ | 低（无逐帧捕获） | 深色主题默认开，浅色可选 | 直接省略，不影响布局 |
| **L3** | 全液态：所有面板折射/动态背景/联动形变 | 33+ + 自研管线 | 高，低端机必掉帧 | ❌ 不做 | — |

**开关与检测（`GlassCapability` 解析顺序）**：
```kotlin
if (!userEnabled) return GlassLevel.L0          // 用户关
if (SDK_INT < 31) return GlassLevel.L0          // 没有 RenderEffect
if (am.isLowRamDevice || pm.isPowerSaveMode) return GlassLevel.L0
if (Settings.Global.getFloat(cr, ANIMATOR_DURATION_SCALE) == 0f) return GlassLevel.L0
return GlassLevel.L1
```
- 窗口模糊另判：`WindowManager.isCrossWindowBlurEnabled()`（Compose 1.13 `WindowInfo.isCrossWindowBlurEnabled`），不可用就退回半透明+遮罩。
- 无障碍：Haze 2.x 有 `LocalGlassAccessibilitySettings`（减少透明度/提高对比度/可见边框）`[库文档]`；我们至少提供「减少透明度」直接落到 L0。

**工程约束（重要）**
- 当前 BOM 2024.10.01（Compose 1.7 / material3 1.3.x）+ Kotlin 2.0.21 + compileSdk 35；Compose 1.13 要求 Kotlin 2.2 / compileSdk 37.1 / AGP 9.2 `[官方]`。**真模糊（Haze 2.1）建议独立 spike**：先验证 Haze 2.1 与当前 Compose 版本是否兼容（Haze 按最新 Compose 发布，官方只承诺“最新稳定版”）`[库文档]`，不兼容就锁 Haze 1.x 或等升级。
- 模糊面数量与发热直接相关：**≤3 个面**、不要在消息气泡上做模糊、不要在 `LazyColumn` 里逐项模糊；滚动时不要同时跑与内容无关的动画（API 31 的坑）。
- 首帧 shader 编译会卡一次：冷启动时先以 L0 渲染，模糊在首帧后一拍开启（或接受一次掉帧）。

---

## 6. 预测性返回与边缘手势

### 6.1 现状诊断（代码事实）

- `MainActivity.kt`：`private sealed interface Screen { History / Chat / Settings }`，`var screen by remember { mutableStateOf<Screen>(Screen.History) }`，全文件 **0 个 `BackHandler`**；
- `AndroidManifest.xml`：`<application>` **没有** `android:enableOnBackInvokedCallback`；
- 结论：在聊天页/设置页侧滑返回 = 没有回调 → 系统直接退到桌面。用户说的「侧边拖拽退出 App」就是它 `[代码事实]`。

### 6.2 官方机制（2026 现状）

| 事实 | 证据 |
|---|---|
| `PredictiveBackHandler`（activity-compose **1.8.0+**）给一个 `Flow<BackEventCompat>`，含 `progress`、`swipeEdge` 等；collect 正常结束=提交，抛 `CancellationException`=取消 | [Compose 预测返回文档](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#compose-back) `[官方]` |
| 只想拦不想跟手 → `BackHandler`；回调栈内层优先，最后一个 enable 的生效 | 同上 `[官方]` |
| 系统动画（back-to-home / cross-activity / cross-task）Android 15 起默认开；**targetSdk 36 起 `enableOnBackInvokedCallback` 默认 true，且 `onBackPressed`/`KEYCODE_BACK` 不再分发** | [Android 16 行为变更](https://developer.android.com/about/versions/16/behavior-changes-16) `[官方]` |
| 退出码：`android:enableOnBackInvokedCallback="true"` 才拿系统预测动画；设 false 只关动画，`OnBackPressedCallback` 仍工作 | [predictive-back-setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup) `[官方]` |
| M3 组件自带：`ModalNavigationDrawer`/`ModalDrawerSheet`/`ModalBottomSheet`/`SearchBar` 在 material3 **1.3.0+** 支持预测返回（要把 `drawerState`/`sheetState` 传进去）；1.5.0 修了「抽屉打开中也能用返回」 | [Compose 预测返回文档](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#material-components) `[官方]` + material3 发布说明 |
| Navigation Compose 2.8+ 支持；新代码推荐 Navigation 3（`NavDisplay` + `predictivePopTransitionSpec` + `NavDisplay.PredictivePopTransitionKey`），**Nav3 1.0 已稳定** | [Nav3 动效文档](https://developer.android.com/guide/navigation/navigation-3/animate-destinations) `[官方]`；[Nav3 稳定公告](https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html) `[官方]` |
| 应用内边缘手势与系统返回冲突：系统返回从左右边缘内滑；可用 `setSystemGestureExclusionRects` 局部豁免，但只应对必要控件，不要整条边豁免 | [手势导航兼容](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav) `[官方]` |

### 6.3 myco 返回栈规则（定稿）

**优先级从内到外**（只有最内层 enabled 的回调执行）：

| 顺序 | 场景 | 行为 | 动画配方 |
|---|---|---|---|
| 1 | IME 打开 | 系统先收键盘（不拦） | — |
| 2 | 弹窗/菜单/Popup | 关弹层 | fade 120ms |
| 3 | 编辑未保存（重命名/表单） | 二次确认 | 确认框 |
| 4 | ModalBottomSheet | 关 Sheet | M3 自带 + 遮罩淡出 |
| 5 | 抽屉打开 | 关抽屉（**不是退 App**） | progress 跟手：`offsetX = -w*(1-p)`，遮罩 `alpha=0.32p` |
| 6 | 设置子页 | 回父页 | push 反向 300ms |
| 7 | 聊天页（根） | 退出 App（系统 back-to-home 预览） | 系统预测动画；根页面**不要**再拦 |
| 8 | 历史页（根） | 退出 App | 同上 |

> 与 `80-ui-component-inventory.md` 一致：聊天页返回=退出页面（历史在抽屉里），但**必须有预测返回预览**，让「退出」是用户看得见的结果，而不是误触惊吓。

### 6.4 接入步骤（按顺序做）

1. **manifest**：`<application android:enableOnBackInvokedCallback="true">`——官方口径是「Android 16 起 targetSdk 36 默认开启」，我们 targetSdk 35 不在默认范围内，所以**现在就显式写 true**（升 36 后保留也无害）。`[官方]`
2. **依赖**：`androidx.activity:activity-compose` 已有 1.9.2（≥1.8 满足 `PredictiveBackHandler`）；导航二选一——最快：现有 `screen` 换成 `List<Screen>` 返回栈 + shell 层 `PredictiveBackHandler`；正规：接 Navigation 3（重写导航，但拿到官方预测动画与 `NavDisplay` 转场）。
3. **shell 层统一拦截**（只此一处）：
```kotlin
PredictiveBackHandler(enabled = backStack.size > 1) { progress ->
    try { progress.collect { e -> drawerProgress = e.progress } ; backStack.removeLast() }
    catch (c: CancellationException) { drawerProgress = 0f }
}
```
4. **抽屉/Sheet**：直接用 M3 `ModalNavigationDrawer` + `rememberDrawerState`（传 `drawerState` 进 `ModalDrawerSheet`），预测返回由组件处理；**不要**自己写边缘手势。
5. **根页面**：不注册 `BackHandler`，让系统播 back-to-home。
6. **回归清单**（每项都要手测）：抽屉开着按返回、Sheet 开着按返回、设置子页返回、聊天根页返回、3 键导航返回键、手势导航左右边缘各拖一次、过程中取消拖拽（UI 必须回弹）。

### 6.5 边缘滑与 iOS 对比

- **iOS**：只有左边缘 interactive pop，无右侧；没有「预览主屏」概念；返回按钮常驻。Android 的返回是**系统级、双侧、可预览**的，语义更重（抽屉/弹层都靠它）。
- **Android 官方建议**：不要跟系统返回抢边缘手势；确实需要边缘可拖的控件（如绘图板）才用 gesture exclusion，且范围要小 `[官方]`。
- **myco 决定**：MVP **不做应用内边缘滑抽屉**（沿用 `80-ui-component-inventory.md` 的结论），只做「汉堡 + 预测返回关抽屉」。以后若要加：仅左边缘、拖拽 >16dp 才触发、只在抽屉关闭时注册 exclusion 区（宽度 ≤24dp），并接受「部分 ROM 会忽略」的事实。

---

## 7. 库 / 选型 / 许可证

| 库/能力 | 提供方 | 许可证 | 状态（2026-11） | 建议 |
|---|---|---|---|---|
| Compose UI / animation | AndroidX | Apache-2.0 | **1.13.0 稳定**（BOM 2026.11.01）；需 Kotlin 2.2、compileSdk 37.1、AGP 9.2 | 升级另立 spike；现在留在 2024.10.01 |
| Material 3 | AndroidX | Apache-2.0 | **1.5.0 稳定**（2026-11-18），M3 Expressive、`MotionScheme`、`material3-ripple` | 升级时一起升；当前 1.3.x 够用 |
| Activity Compose | AndroidX | Apache-2.0 | 1.9.2 已有；`PredictiveBackHandler` ≥1.8 | 现在就用 |
| Navigation 3 | AndroidX | Apache-2.0 | 1.0 稳定（2025-11） | 重做导航时首选；否则 Navigation Compose 2.8+ |
| `Modifier.blur` / `BlurRadiusSpec` | AndroidX Compose | Apache-2.0 | 1.13 稳定 | 不用额外库即可做「自己的内容」模糊 |
| **Haze 2.1.0** | Chris Banes | **Apache-2.0** | 2026-11-02 发布；Glass 稳定；Android 23+，真模糊 31+ | L1 的唯一现实选择；先验版本兼容 |
| Haze Glass（`haze-glass`） | 同上 | Apache-2.0 | 2.1 稳定 | L2 可借它的折射/高光，但建议只用 blur 模块控制复杂度 |
| Material Symbols（字体/矢量） | Google | Apache-2.0 | 持续 | 图标首选；`material-icons-extended` 已废弃并钉在 1.7.3（CMP 侧），Android 侧也别新增 |
| Coil 3 | Coil 社区 | Apache-2.0 | 维护中 | 附件/图片缩略图 |
| Lottie Compose | Airbnb | Apache-2.0 | 维护中 | 只在确有必要（空态动画）时用；不要用作页面转场 |
| `androidx.graphics:graphics-shapes` | AndroidX | Apache-2.0 | 1.1.0（material3 1.6 alpha 已依赖） | 需要多边形/形状动画时 |
| 各类「Glass UI Kit / 液态玻璃组件库」 | 社区 | 多为 MIT/Apache | 质量参差 | **不引入**：耦合导航/布局，违背低耦合 |

选型原则：**官方优先**；第三方只允许「单一职责、可替换」的库（Haze=模糊、Coil=图片、Lottie=矢量动画）。动效不需要库——3 个 spring/tween spec 手写即可；图标不要用「全家桶」大库。

---

## 8. 对 myco 的具体清单

### 8.1 token 总表（v1，可直接落 `ui/tokens/`）

| 组 | token | 值 |
|---|---|---|
| 间距（4dp 网格） | `space.xs/s/m/l/xl/xxl` | 4 / 8 / 12 / 16 / 24 / 32 |
| | `screenX` / `listX` / `cardPad` / `sectionGap` | 16 / 16 / 16 / 24 |
| 圆角 | `radius.control/card/sheet/pill` | 12 / 16 / 24 / 999 |
| 字号行高 | 见 §3.1（message 16/26、body 16/24、bodySmall 14/22、caption 12/18、title 20/28、code 13/20） | — |
| 动效 | `press` 0.6/800、`enter` 0.8/380、`effects` 1.0/1600、`gesture` 320ms emphasized、`exit` 180ms | 见 §1.2 |
| 透明度层级 | scrim 32%、tint 浅 95%/深 15%、hover/press 8%、disabled 38%、noise 2–3%、glow 8–16% | 统一常量，禁止散落 |
| 海拔 | 0 / 1 / 3 / 6dp（浅色阴影）；深色不用阴影，用 1dp 边框+发光 | 沿用现有 `GlassSurface` |
| 图标 | 16 / 20 / 24dp；触摸目标 48dp | 24 是默认 |
| 玻璃 | `level L0/L1/L2`、`blurRadius 16–24dp`、`perf Adaptive` | 见 §5.5 |

### 8.2 组件装饰规范

| 组件 | 规范 |
|---|---|
| 主按钮 | 高 40–48dp，圆角 12，`primary` 底 + `onPrimary` 字；按下 ripple+0.98；loading 时禁用 + 10dp 指示器 |
| 图标按钮 | 40–48dp 触摸区，24dp 图标；无界 ripple；深色下可加 8% 底色；仅返回/发送这类主操作做 0.98 轻缩放 |
| 会话行 | 64–72dp；左 16 icon/标题，右时间 `caption`；选中=整行 8% 主色 + 左侧 3dp 色条；未读 8dp 圆点；长按菜单；`⋯` 常驻 |
| 用户气泡 | 右对齐，最大宽 86%，圆角 16/16/4/16，内边距 14×12；按压不缩放，长按菜单 |
| 助手消息 | **全宽无气泡**；Markdown 块间距 12；代码块独立圆角卡；流式只闪光标 |
| 输入区 | 圆角 24，内边距 8，多行最高 6 行；聚焦=边框提亮 120ms；L1 时这里是最值得模糊的面 |
| 抽屉 | 宽 82–85%，右缘 1dp 高光；遮罩 32%；条目如上；底部账户区 72dp |
| Sheet | M3 `ModalBottomSheet`，顶角 24，拖拽把手 32×4；遮罩 32%；L1 可让 Sheet 窗口用系统模糊（1.13 DialogProperties） |
| Snackbar | 距底 16 + ime/nav insets；圆角 12；轻提示 4s，撤销 4s |
| 设置卡片 | 圆角 16，行 56dp，左 icon 24 + 右值 + chevron；分组间距 24；危险操作红字 + 二次确认 |

### 8.3 玻璃分级与开关

- 设置页新增：外观 → **「真模糊（实验）」** 开关（默认按 §5.5 解析），副标题写「Android 12+，低端机/省电模式自动关闭」；
- 实现：`GlassCapability` CompositionLocal；`GlassSurface` 内部 `if (cap.level>=L1) HazeSurface else L0Surface`；
- 降级：低内存/省电/关动画 → L0；系统窗口模糊关 → L0；L2 shader 失败 → 纯渐变；
- 监控：Debug 下 overlay 显示当前 level、帧耗时、是否 API 31（避免 API 31 重捕获坑）。

### 8.4 返回手势规则（写进 `ui/navigation/BackStack.kt`）

- 单一返回栈 `List<Screen>` + 抽屉/Sheet 状态入栈；
- 优先级见 §6.3；根页面不拦；
- 抽屉/Sheet 用 M3 自带 predictive back，不写边缘手势；
- 每条规则配一个 instrumentation 手势测试或至少手测清单（§6.4 第 6 条）。

### 8.5 现在做 / 以后做

**必须现在做（P0，不改后端）**
1. `MycoMotion` + `MycoType`（中文行高）+ `MycoOpacity/Elevation/IconSize` token；
2. `Pressable` 统一按压层 + 各组件 ripple/scale/触觉策略；
3. 行高/光学对齐修正：`includeFontPadding=false`、单行 `Trim.Both`、列表行 64/56、icon 公式；
4. manifest 开 predictive back + `screen` 状态换返回栈 + 根页面不拦（**这条直接解决用户痛点**）；
5. `GlassSurface` 加 `GlassCapability` 开关（默认 L0，先不做模糊）。

**以后做（P1–P2）**
1. Navigation 3 重写导航（拿官方预测动画/共享元素）；
2. Compose BOM 升级 spike（Kotlin 2.2 / AGP 9.2 / compileSdk 37.1）——顺带获得 `Modifier.blur` 渐变与 DialogProperties 模糊；
3. Haze L1 spike（先验兼容性 + 三面模糊真机帧率）；
4. L2 高光/边缘折射（AGSL 或渐变）；
5. 低端机基准与自动降级策略调参。

### 8.6 低端机降级

- 判据：`ActivityManager.isLowRamDevice()`、`PowerManager.isPowerSaveMode`、系统「移除动画」、API <31；
- 降级顺序：关 L2 shader → 关 L1 模糊（回 L0）→ 缩短动效到 100–150ms（仍保留 ripple/按压）→ 关闭列表 stagger；
- 任何情况下**不降级交互**：48dp 目标、返回栈、流式四态、错误重试保持。

---

## 9. 存疑 / 未实测

1. **Haze 2.1 与当前 Compose（BOM 2024.10.01 / Kotlin 2.0.21）是否兼容**：未验证。Haze 官方只说「按最新稳定 Compose 构建」；若必须升 Compose 1.13，则连带 Kotlin 2.2/AGP 9.2/compileSdk 37.1，是一次大迁移。**先做依赖兼容检查（不写代码，看 POM/metadata）**。
2. **国产 ROM 的窗口模糊/跨窗口模糊实测**：只有 AOSP 文档 + 推断，没有逐机型证据；`isCrossWindowBlurEnabled()` 在 MIUI/ColorOS 上的返回值需要真机测。
3. **ModalBottomSheet 是否走独立 Dialog 窗口**（决定 1.13 DialogProperties 模糊能否直接用在 Sheet）：未查到确定实现细节；`[推断]` 老版本是 Dialog，新版本可能改为窗口内 Popup，需看源码。
4. **API 31 的 Haze「无关动画也重捕获」**：官方文档明确存在，但代价「未被测量」；真机是否可感知未知。
5. **液态玻璃近似效果的主观验收**：没有样张对比实验；L2 需先做 1 个静态 mock（抓图给用户拍板），再决定是否写 shader。
6. **`Modifier.illuminate` / `Light`（Compose 1.14 alpha）**：还没稳定，不应作为 L2 的依赖，只能作为「未来可换实现」。
7. **参考产品按压细节**：DeepSeek/ChatGPT 的按压是 ripple 还是自绘 overlay，从截图无法 100% 判定（`[观察]/[推断]`），需要录屏帧分析。
8. **中文行高 1.6 在 13sp 辅助字号下**是否仍舒适（小字号 1.6 会显松）：建议真机上用 1.5–1.6 两档 A/B，本报告取 1.57（14/22）。
