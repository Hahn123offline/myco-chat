# 液态玻璃着色器：社区实现与移植评估

> 目的：回答"社区已有实现里，哪些**真折射**可以移植到我们的 AGSL 路径上"，并给出**可编译的候选 shader**（[`scratch/glass-shaders/m5glass-v1.agsl`](../../../scratch/glass-shaders/m5glass-v1.agsl)）、Kotlin 调用骨架、移植差异矩阵与验证计划。
>
> 取证日期：2026-11-27。对象：本仓库 `app-android`（Kotlin 2.0.21 / Compose UI 1.7.5 / minSdk 26 / compileSdk 35），以及 `main` 分支的 6 个外部来源 + W3C 规范。
>
> **分工**：能力/门槛/降级梯度见 [`android-blur-glass.md`](android-blur-glass.md)；捕获层机制与失败 RCA 见 [`backdrop-blur-deep.md`](backdrop-blur-deep.md)。**本文只做一件事：社区 shader 的算法拆解 + 往我们 AGSL 路径上的移植判定。** 不重复能力矩阵，不复述捕获实现。
>
> 证据分级（沿用 [`README.md`](../README.md) §0）：
> - **E0** = 官方文档原文 / 源码 `文件:行` / 本仓库实机实测；
> - **E1** = 第三方 README / issue / 文档声称（未验证）；
> - **E2** = 推测 —— **本文不进任何结论**；确实只是推测的写在 §5"未取证"里。
>
> **本文没有做的事**：候选 shader **未在本机编译**（模拟器被另一路占用，本机也没有空闲真机）。因此 §4 给的是"按官方 AGSL 语法约束逐个构造检查过"的候选，不是"已验证能编译"的成品；§6 第一步就是 smoke compile。

---

## 0. 一页结论

### 0.1 能直接移植的是哪份

**没有一份可以"直接拿来用"，但有一份可以"按架构整段搬"，其余四份各贡献一段数学（另有 W3C 提案作对照）。**

| 角色 | 来源 | 许可证 | 我们取什么 | 怎么用 |
|---|---|---|---|---|
| **架构骨架（主）** | [`himanshu-lal4/react-native-liquid-glassmorphism`](https://github.com/himanshu-lal4/react-native-liquid-glassmorphism) | **MIT**（可商用可闭源） | 捕获背景 → RenderNode → blur → AGSL 的**效果链顺序**、uniform 约定、`content.eval` 用法、宽差分法线 + 中线 fade、边缘保护带、色散比例、内阴影、预乘 alpha、API<33 降级策略（我们按 D-74 走 A 不透明，**不照搬**它的 blur+tint 档） | shader 骨架 + Kotlin 结构 |
| **数值/剖面参照（生产级）** | [`chrisbanes/haze`](https://github.com/chrisbanes/haze) `haze-glass` | **Apache-2.0**（可商用，需保留声明/NOTICE） | `circle/squircle/concave/lip` 剖面族、`exp(-t/0.225)(1-t²)²` 边缘拟合、`centerFade` 中线处理、位移上限 `0.5·min(w,h)` | 剖面数值与安全上限 |
| **折射数学（物理）** | [`archisvaze/liquid-glass`](https://github.com/archisvaze/liquid-glass) | **MIT** | 表面高度剖面 `h(t)` + 坡度 → Snell → 厚度位移（含 SVG `feDisplacementMap` 的编码方式） | 我们"每个像素怎么算"的物理依据 |
| **折射数学（可移植性最好）** | [`ruiqichenbiec/design-systems`](https://github.com/ruiqichenbiec/design-systems) `lens/` | **MIT** | 3D 法线 `(nxy, √(1-\|nxy\|²))`、`refract((0,0,-1), N, 1/1.45)`、`offset = ray.xy/(-ray.z)·thickness`、R/B 差分色散 | 候选的位移公式直接来源 |
| **剖面/梯度补充** | [`aaaa-zhen/glsl-playground`](https://github.com/aaaa-zhen/glsl-playground) | ⚠️ **未确认**：README 写 MIT，仓库**无 LICENSE 文件** | `circleMap(x)=1-√(1-x²)`、色散"四角强/轴线 0"的场 | 只取数学（公式不受版权保护），**不搬代码** |
| **标准侧对照** | W3C Filter Effects 2 + [svgwg#1142](https://github.com/w3c/svgwg/issues/1142) | 规范文本（W3C） | 只用于说明"为什么标准那边做不了、我们为什么能" | 对照，不提供代码 |

### 0.2 预计工作量

| 产出 | 量 | 说明 |
|---|---|---|
| 候选 AGSL `m5glass-v1.agsl` | **195 行**（其中注释 ~85 行；有效代码 ~110 行） | 已写好，**未编译** |
| Kotlin 调用骨架 | **~70 行**（一个 `buildM5GlassEffect()` + 一处 `graphicsLayer` 调用位） | 见 §4.3；不改捕获管线 |
| 相对现有基线（`GlassLabPhysics.kt` / 产品 `GlassSurface.kt` 的 `AGSL_GLASS`） | **重写 shader 主体 ~60 行** + 调用处 ~10 行 | 3 个必须改的点见 §4.5 |

### 0.3 许可证风险

- **可商用/可闭源**：RN 库 MIT、archisvaze MIT、design-systems MIT（GitHub API `license=mit` + 仓库根 `LICENSE`）。
- **可商用，但要合规**：Haze **Apache-2.0** —— 若我们把它的模板代码/数值直接搬进来，必须在仓库放 `NOTICE` 并声明出处。本文候选**没有逐行复制** Haze 代码，只用了它公开文档里描述的**数值**（剖面指数、上限比例），但仍建议在实现 PR 里写一行来源声明。
- **不要碰**：`aaaa-zhen/glsl-playground` 的**代码**（无 LICENSE 文件；README 的 MIT 只是宣称，E1）；ShaderToy 默认许可**未取证**（站点 403），**不采用**。
- 本文候选 shader 的公式来源均为"公开数学描述"，不构成对上述仓库代码的复制。

### 0.4 已确认的基线（我们自己的实测，E0 本仓库）

`GlassLabPhysics.kt`（`glass_scene=physics`）已经在实机验证过"边缘-only 像素位移"：

| 指标 | 数值 |
|---|---|
| `refract=0` vs `refract=3dp` 变化像素 | 16,546 / 2,419,200 = **0.68%**，外接框 874×394 = 玻璃体本身 |
| **内缩 60px 内区** | **0 / 206,596，maxDiff = 0（逐位相同）** |
| 色散 `disp=1→2px` | 变化 2,121 / 1,620,000 = **0.13%**，内区 **0 / 239,120，maxDiff=0** |

来源：[`docs/reports/glass-lab-report.md`](../../reports/glass-lab-report.md) §12.2。**M5Glass v1 的验收沿用同一判据**（§6），但要做到"边缘带内"必须修掉 §1.3 的三个缺陷。

---

## 1. 我们的目标模型（对照用）

### 1.1 管线（现状）

```
[背景 Composable]
   └─ DrawScope.record(IntSize) { drawContent() }        ← 每帧把背景重绘进 GraphicsLayer（CaptureHost/RecordInto）
        └─ drawLayer(captureLayer) 上屏
[玻璃 Composable]（产品：GlassSurface；实验场：GlassPanel / PhysicsGlassBody）
   └─ Box(clip = GlassCorners.toShape()，size = 玻璃像素尺寸)   ← 产品实现；实验场用 SuperellipseShape
        └─ drawBehind { translate(t) { drawLayer(captureLayer) } }   ← 只把"玻璃背后那块像素"搬进本层
             └─ graphicsLayer { renderEffect = blur }                ← 真模糊（API31+）  ┐ 现状是两个层；
                  └─ graphicsLayer { renderEffect = AGSL }           ← 折射/色散/高光（API33+） ┘ 候选合并为 1 个 chain
```
关键约束（都是既有事实，不重复取证）：
- **AGSL 采样不到窗口背景**，`content` 只能是"本 RenderNode 已渲染的内容"；必须先捕获再挂效果（[`backdrop-blur-deep.md`](backdrop-blur-deep.md) §0）。
- 玻璃层被提升为离屏层后**隐式裁切到 bounds**（[`android-blur-glass.md`](android-blur-glass.md) §2.2）→ **层里只有玻璃矩形内的背景像素，没有邻居像素**。这一条直接决定"位移只能向内"（§1.4）。
- 玻璃形状**当前产品实现**是 `RoundedCornerShape`（四角可不同半径的圆角矩形，`GlassMaterial.kt:87-92`），因为 sheet/抽屉需要只圆部分角；**设计系统目标** / 实验场是 `SuperellipseShape(radius, exponent=4)`：四条直边 + 每角 `|X|^n+|Y|^n=r^n` 的超椭圆角（`Superellipse.kt:85-127`，不是整圈 Lamé 超椭圆）。候选 shader 两种都支持（§1.4），**必须与 clip 同一条曲线**。

### 1.2 输入清单（目标态）

| 输入 | 来源 | 是否必需 |
|---|---|---|
| 捕获背景（一张纹理 = 本层内容） | `rememberGraphicsLayer` + `DrawScope.record` | ✅ 唯一纹理 |
| 形状 | `GlassCorners` 四角半径 + 形状模式（RoundedCornerShape / SuperellipseShape）+ 层尺寸 | ✅ 程序化 SDF |
| 光照方向 | token（`LightRig(azimuth=135°, elevation=55°)` 的屏幕投影，[`90-m5-visual-system.md`](../../design/90-m5-visual-system.md) §2.5） | ✅ 一个 `float2` |
| 法线贴图 / 高度图 / 深度 / 环境图 | —— | ❌ **不需要**（全部程序化） |

这就是我们的优势：**除一张背景纹理外没有任何贴图依赖**。社区实现里 RN 库为了任意轮廓会预生成 SDF/normal/mask **三张**贴图；Haze 有 merged field / 深度采样；archisvaze 预生成 displacement map + specular map 两张图。**我们都不需要**，因为形状是解析的、只有一种。

### 1.3 现有基线 `AGSL_PHYSICS_GLASS` 的三个缺陷（v1 必须修）

`GlassLabPhysics.kt:56-115` 是 G-21 合规版基线；**同一份 shader 已经进入产品实现** `app-android/src/main/kotlin/dev/myco/android/ui/m5/primitives/GlassSurface.kt:71-132`（扩展成四角半径 `radii`，其余逐行相同）。对照社区实现能看出三处**结构性**问题（不是参数问题），**两个文件都有**：

1. **位移方向朝外 → 采样被 clamp 拉边**：`base = coord + n * (f * refractPx)`（`:93`）。`n` 是外向法线，所以边缘像素采样到玻璃层**外面**，而层外没有内容，只能 `clamp` 到 `[0,size]` —— 结果是"最外一列像素被拉伸"而不是折射。RN / design-systems / Haze **全部向内采样**（从更内侧取像素，让边缘内容被放大包边）。**向内还有个天然好处：永远不越界，不需要 outsets。**
2. **法线差分步长太窄（±0.75px）→ 中线断裂**：`sdfNormal()` 用 `e=0.75`（`:74-81`）。SDF 梯度在**中线**（medial axis，两条边等距的脊）上会塌陷：宽扁玻璃的中线是水平线、胶囊的中线是纵向脊。±0.75px 时 `g≈0` 只在 1px 内发生 → 折射率在一个像素里从 1 掉到 0，形成**一条硬缝**。RN 用 `e = clamp(lensW*0.3, 1, 16)` + `conf=|∇d|` + `axisFade=smoothstep(0.15,0.8,conf)` 把塌陷变成几十像素的渐变（`LiquidGlassmorphismView.kt:1581-1625`）。
3. **色散是独立参数 `dispPx`，不随位移缩放**：`dither = n * (f * dispPx)`（`:96`）。物理上色散 = 偏折角随波长变化 ⇒ **与位移成正比**。RN 用 `ca = n2 * bend * iLens * 0.018`（≈位移的 1.8%，`:1689`）；design-systems 用 `split = n2*pow(edge,2.8)*dispersion*2.6`（`:159`）。独立的 `dispPx` 在 `refract=0` 时仍会动，违反"色散只在边缘且随折射"。

### 1.4 形状 SDF 的推导（候选 shader 的 `sdGlass`）

**先对齐一个事实**：产品当前的玻璃 clip 是 **`RoundedCornerShape`（四角可不同半径的圆角矩形）**，不是 `SuperellipseShape`——
`GlassMaterial.kt:81-96` 的 `GlassCorners.toShape()` 直接返回 `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)`，因为 sheet 只圆上两角、抽屉只圆右两角，而 `SuperellipseShape` 只支持**统一**半径。
设计系统文档的目标形状是 `SuperellipseShape(exponent=4)`（[`90-m5-visual-system.md`](../../design/90-m5-visual-system.md) §2.2）。**两者是不同曲线**，而 G-21 要求"clip 的轮廓 = shader 的等距场轮廓"。
所以候选 shader 做成**双模式**，用 `uShapeN` 切换，**必须在调用侧与 clip 同步**：

| `uShapeN` | SDF 角区公式 | 匹配的 Compose Shape |
|---|---|---|
| **2**（默认） | `length(q) - r`（精确圆角矩形，逐象限半径） | `RoundedCornerShape`（= 当前产品实现） |
| `n > 2`（如 4） | `F/\|∇F\|`，`F=(q.x/r)^n+(q.y/r)^n-1`（超椭圆角一阶距离） | `SuperellipseShape(radius, exponent=n)` |

两种模式**共用**的直边/核心区公式（设 `p = coord - half`、`q = |p| - (half - r)`，`r` 由象限选中）：

| 区域 | 条件 | 距离（负=内） |
|---|---|---|
| 核心区 | `q.x≤0 且 q.y≤0` | `max(q.x,q.y) - r`（到最近直边，精确） |
| 左右直边带 | `q.y≤0, q.x>0` | `q.x - r`（精确） |
| 上下直边带 | `q.x≤0, q.y>0` | `q.y - r`（精确） |
| 角区 | `q.x>0 且 q.y>0` | 见上表（n=2 精确；n>2 一阶） |

- 超椭圆角区是**一阶距离**（法线方向 = `∇F`，精确；距离值在 `F=0` 附近误差二阶）。它只在 `|d| ≲ band` 范围内被使用，band 外的误差被 ramp clamp 吸收。
- 超椭圆角区用 `max(dCorner, min(q.x,q.y)-r)` 兜底，保证角区 ↔ 直边带**连续**，且不会在角内侧给出虚假深值。
- 四角不同半径（`uRadii`）与 `GlassCorners.toFloatArray()` 同序 `(tl,tr,br,bl)`；**同一条边上的两个角半径必须相等**（sheet/抽屉都满足），否则 SDF 在象限分界线上不连续（`RoundedCornerShape` 本身也要求这样才有意义）。

**这条 SDF 是候选 shader 相对所有社区实现的唯一"自研"部分**（他们的 SDF 是单一圆角矩形或任意轮廓）。它保证"裁剪轮廓 = 折射轮廓"，这是 G-21 的硬要求。

### 1.5 G-21 合规声明（候选的每个像素）

| 输出量 | 公式 | 数据来源 |
|---|---|---|
| 距离 `d` / 法线 `n` | `sdGlass(p)`；`n=∇d`（宽差分） | 几何（形状参数） |
| 倒角坡度 `θi` | `tanθi = uSlope·(1-t)^1.65`，`t=distEdge/band` | 几何剖面（1D 高度场） |
| 折射方向/角度 `θr` | Snell：`sinθr = sinθi/IOR` | 物理 |
| 屏幕位移 | `thickness·tan(θi-θr)`（棱镜偏折） | 几何光学 |
| 色散 | `offset·uDispersion`，R 少偏/B 多偏 | 正常色散（`n_R<n_B`） |
| 边缘高光 | Schlick 菲涅尔 `0.04+0.96(1-nz)^5` + 细几何边 | 物理公式 |
| 内阴影 | `(1-smoothstep(0,1.5band,distEdge))·(0.35+0.65·背光权重)·uShadow` | 几何 + 光照方向 |
| 饱和度补偿 | luma 保持的 `mix` | 颜色科学（模糊掉饱和的补偿） |

**不含**：渐变贴图、径向光斑、噪声、描边、月牙、外发光。

---

## 2. 各来源逐一分析

### 2.1 `himanshu-lal4/react-native-liquid-glassmorphism`（最重要）

- 仓库：<https://github.com/himanshu-lal4/react-native-liquid-glassmorphism>（`main` @ 2026-11-27）
- **许可证：MIT**（仓库根 `LICENSE`，`Copyright (c) 2026 Himanshu Lal`）→ 可商用、可闭源。
- 关键文件：`android/src/main/java/com/liquidglassmorphism/LiquidGlassmorphismView.kt`（1813 行）、`SharedBackdrop.kt`、`GlassSdf.kt`、`docs/android-liquid-glass.md`。

#### 捕获 / 效果链（E0 源码）

与我们的架构**同构但实现路线不同**：

| 环节 | 它怎么做 | 行号 |
|---|---|---|
| 捕获 | 每帧 `onPreDraw` 里 `rootView.draw(softwareCanvas)` 把整棵树画进**降采样 2× 的 Bitmap**；用静态 flag `capturing` 跳过**所有**玻璃视图（避免自采样） | `SharedBackdrop.kt:55-90`；`LiquidGlassmorphismView.kt:909-942`、`:1016-1021` |
| 去重 | 6×5 稀疏采样 hash，背景没变就不重绘（`postInvalidateOnAnimation`） | `:926-970` |
| 上传 | Bitmap 画进硬件 `RenderNode`（`beginRecording`→`drawBitmap(srcRect,dstRect)`→`endRecording`） | `:1082-1092` |
| 效果链 | `createChainEffect(material, blur)` = **先 blur 再 shader** | `:1232`、`:1296` |
| 绑定 | `createRuntimeShaderEffect(shader, "content")`；额外输入用 `setInputShader("sdf"/"mask"/"grad", BitmapShader)` | `:1244-1246`、`:1294` |
| API<33 | 只挂 blur；再用 Canvas 画 frost/tint/specular/rim | `:1117-1127`、文件头 `:53-54` |
| 诊断 | `tier = agsl | blur | tint` 上报一次，编译失败有 `SHADER_COMPILE_FAILED` 事件 | `:1163-1171` |

> **对我们的直接含义**：它的捕获路线（软件 `root.draw` + Bitmap）**不要学** —— 我们已经验证过 Compose 的 `DrawScope.record` 路线（一次录制、N 块玻璃共享、跟随滚动），比软件重绘便宜得多。**要学的只有"效果链顺序 + 绑定方式 + 降级"**。

#### `GLASS_AGSL` 全文的关键段落（`LiquidGlassmorphismView.kt:1416-1811`）

它的 shader 有 4 个 `uniform shader`（`content` + `sdf`/`mask`/`grad`）、22 个 float uniform、1 个 `float4 iBodies[8]` 数组。**绝大部分是为"任意轮廓 + 多体融合 + 触摸/陀螺仪"服务的，我们不需要**。真正可移植的是这 5 段：

**① 形状 + 边缘斜坡（`:1449-1452`, `:1549-1573`）**

```glsl
float sdRoundRect(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;   // 标准圆角矩形 SDF
}
float lensW = max(28.0, iCorner * 1.4);        // 透镜带宽度 ∝ 圆角
float reflW = max(14.0, min(b.x, b.y) * 0.7);  // 镜面回折带宽度
float rim      = 1.0 - clamp(-d / lensW, 0.0, 1.0);     // 边缘 1 → 带内 0
float edgeBand = 1.0 - clamp(-d / reflW, 0.0, 1.0);
```

**② 宽差分法线 + 中线 fade（`:1581-1630`）** ← 我们最缺的一段

```glsl
float e = clamp(lensW * 0.3, 1.0, 16.0);        // 步长 = 带宽的 30%
float gx = sd(p + float2(e,0)) - sd(p - float2(e,0));
float gy = sd(p + float2(0,e)) - sd(p - float2(0,e));
float2 n2 = float2(gx,gy) / (length(float2(gx,gy)) + 1e-5);
float conf = length(float2(gx,gy)) / (2.0 * e);         // ≈ |∇d|，中线处 → 0
float axisFade = smoothstep(0.15, 0.8, conf);
float bend = rim * rim * axisFade;                      // 折射坡度
```

注释（原文）解释了为什么不能用窄步长：*"With `e = 1` that made `conf` … a 1px notch of zero refraction with full refraction either side, which is the hard line across the middle of a pill"*。**这正是我们基线的问题。**

**③ 向内位移 + 边缘保护带 + 色散（`:1630-1693`）**

```glsl
float edgeGuard = smoothstep(0.0, max(12.0, reflW * 0.18), -d);   // 最外缘 taper 到 0
float2 disp    = n2 * bend * iLens * edgeGuard;                   // 位移方向 = 法线
// 注意下一行：uv = 基准 - disp ⇒ 采样点被推向内侧（放大包边）
float2 uv      = magBase - disp - reflDisp;
float2 ca      = n2 * bend * iLens * 0.018;                       // 色散 = 位移的 1.8%
src.r = content.eval(clamp(uv + ca, 0.0, iResolution - 1.0)).r;
src.g = content.eval(clamp(uv,      0.0, iResolution - 1.0)).g;
src.b = content.eval(clamp(uv - ca, 0.0, iResolution - 1.0)).b;
```

**④ 视觉/光学后处理（`:1695-1761`）**：luma 保持的饱和度 `iSat`（实测对标 iOS：饱和 0.721×→1.32×，`:1695-1697`）、暗背景自适应提亮 `adapt`（`iLift + (1-luma)*iAdapt`，≤0.5）、tint mixer、dim 压暗、菲涅尔 `pow(1-N.z,3)`、`L·N` 镜面 `pow(·,10*iSpecSharp)`、背光侧内阴影 `rim²·(1-facing)·0.12`。

**⑤ 预乘 alpha（`:1796-1809`）**：`return half4(col * aa, aa)`；原文注释 *"AGSL blends with PREMULTIPLIED alpha, so scale the color too"*。

#### 输入依赖 / AGSL 可移植性 / 工作量 / 风险

| 项 | 结论 |
|---|---|
| 需要的纹理 | `content`（1 张）+ 任意轮廓时额外 3 张（sdf/mask/grad，CPU 预生成）。**我们只需 1 张**。 |
| 能否用"捕获背景 + 程序化 SDF" | ✅ 能。它的默认路径（圆角矩形）就是解析 SDF，不依赖贴图；贴图只为自定义轮廓。 |
| 直接套我们的捕获层？ | **算法能，代码不能**：① 它的 `iResolution`/`iLens` 是"视图像素"，我们的层是"玻璃像素"，语义一致但数值要按玻璃尺寸给；② 它用 `-disp`（向内），我们基线用 `+n*f`（向外）→ 必须改；③ 它假定 `content` 是"轻度磨砂的背景"，我们的是 blur 后的层 → 一致；④ 它的 `mask` 输出 alpha 用于自定义轮廓，我们用 Compose clip → 可删。 |
| AGSL 差异 | 它本身就是 AGSL 生产代码（E1：库作者声称在 API 33+ 真机跑通），所以**这段 shader 里没有 GLSL 专属写法**。它用到的 `for` 循环 + `break`、`float4 arr[8]`、`atan`、`exp`、`fract`、`half4 main(float2)` 都是 AGSL 可用（E1）。 |
| 工作量 | shader 主体（法线/位移/色散/高光/阴影）**~80 行改写**；Kotlin 捕获**不动**；效果链从 2 个 `graphicsLayer` 改成 1 个 chain。≈ 0.5 人日（含调参）。 |
| 风险 | ① `iLens`（位移像素）是按"视图 dp×density"调的，我们换到"玻璃层像素"要重调；② 它的 `rim=1-clamp(-d/lensW)` 与我们的超椭圆角形状不完全匹配；③ 代码 1800 行，**不要整段抄**（大部分是 merged body/触摸/陀螺仪，我们没有）。 |

---

### 2.2 `chrisbanes/haze` — `haze-glass`（最生产级的 AGSL 玻璃）

- 仓库：<https://github.com/chrisbanes/haze>（`main` @ 2026-11-27）；模块 `haze-glass/`
- **许可证：Apache-2.0**（仓库根 `LICENSE`）→ 可商用；搬运代码需保留 `NOTICE`/版权声明。
- 关键文件：`haze-glass/src/commonMain/.../GlassShaders.kt`（1999 行，AGSL 由 Kotlin 模板字符串生成）、`RefractionProfile.kt`、`SurfaceProfile.kt`、`GlassOptics.kt`；配套 `haze-blur` 有 `BlurRuntimeShaderFailure*Test`（说明"shader 编译失败"是现实问题）。

#### 光学模型（E0 源码）

它是**两层模型**：`SurfaceProfile` 决定剖面（`Circle/Squircle/Concave/Lip`），`RefractionProfile` 决定折射作用方式（`Surface` 按高度场梯度 / `Edge` 按到边距离）。

**剖面函数（`:923-945`）**

```glsl
float circleMap(float x)   { return 1.0 - sqrt(max(0.0, 1.0 - x*x)); }
float squircleMap(float t) { return 1.0 - smootherstep(t * sqrt(t)); }
float evaluateProfile(float t) {          // t = 归一化光学距离（0=边缘 → 1=带内界）
  float x = 1.0 - clamp(t, 0.0, 1.0);
  if (surfaceProfile == 1) return squircleMap(t);
  if (surfaceProfile == 2) return -circleMap(x);              // concave
  if (surfaceProfile == 3) { /* convex/concave 混合 = lip */ }
  return circleMap(x);                                        // 默认：圆顶（边缘最高）
}
```

**高度场（`:962-1002`）**

```glsl
float domeDistance(p, size, height) {       // 近似"穹顶"光学距离：中心 = height，边缘 = 0
  vec2 n = (p - size/2)/(size/2); vec2 s = n*n;
  float radius = sqrt(clamp(s.x + s.y - s.x*s.y, 0.0, 1.0));
  return max((1.0 - radius) * height, 0.0);
}
float surfaceHeight(distance, height) = evaluateProfile(clamp(distance/height,0,1)) * height;
```

**Edge 折射（"Empirical Clear fit"，`:1083-1138`）**

```glsl
float t = clamp(opticalDistance / edgeRefractionWidth, 0.0, 1.0);
float cutoff = max(1.0 - t*t, 0.0);
float profile = exp(-t / 0.225) * cutoff * cutoff;        // 边缘最强，~22.5% 带宽内快速衰减
float magnitude = foldedRefractionHeightNorm(profile, ...) *
                  min(strength * refractionScale, min(size.x,size.y) * 0.5);   // 位移硬上限
vec2 gradient = edgeRefractionGradient(localCoord, size, radii);
float centerFade = smootherstep(clamp(length(gradient)/0.5, 0.0, 1.0));       // 中线 fade
return -gradient / max(length(gradient), 1e-4) * centerFade * magnitude;     // 向内
```

它的 `edgeRefractionGradient`（`:1632`）不是数值差分，而是**解析的圆角矩形梯度**（角权重混合），并用 `blendWidth` 平滑。

#### 输入依赖 / 可移植性 / 工作量 / 风险

| 项 | 结论 |
|---|---|
| 需要的纹理 | `content` + 可选 `mask`（progressive）+ 可选深度（`sampleDepth`，它有深度合成路径）。**我们只用 1 张**。 |
| 需要环境图/深度吗 | 核心折射**不需要**；它的 `depth` 是加分项（背景深度分层），我们没有 → 不取。 |
| AGSL 可移植性 | 它就是 AGSL（Android 端）；但源码是 **Kotlin 模板字符串 + 生成器**（`buildRefractionDetail` 等），直接抽函数很费劲。 |
| 工作量 | 只取**数值与思路**：`evaluateProfile`（~10 行）、`exp(-t/0.225)` 边缘拟合（~5 行）、`centerFade`（~3 行）、`0.5·min` 上限（1 行）。≈ 0.25 人日。 |
| 风险 | ① 版本门槛：Haze 2.x 需要 Compose 1.9+ / Kotlin 2.3+（2.0.x 起实际是 JC 1.11 / Kotlin 2.4，见 [`android-blur-glass.md`](android-blur-glass.md) §2.6），**本栈（1.7.5 / 2.0.21）不能引库**；只能搬数学；② Apache-2.0 合规；③ 它的"高度场"语义与 archisvaze 的"物理高度"不同（它把边缘当最高点，是**经验拟合**而非几何光学）→ 我们只在"剖面形状"层面参考，位移公式仍用 §2.4 的物理版。 |

---

### 2.3 `archisvaze/liquid-glass`（SVG + WebGL 双实现）

- 仓库：<https://github.com/archisvaze/liquid-glass>（`main` @ 2026-11-27）
- **许可证：MIT**（仓库根 `LICENSE`，`Copyright (c) 2026 Archis Vaze`）→ 可商用可闭源。
- 文件：`index.html`（SVG `feDisplacementMap` 版，Chromium-only）、`webgl.html`（Three.js 全屏 GLSL 版）。

#### 渲染方式

- **SVG 版**：`backdrop-filter: url(#filter)` → `<feGaussianBlur>` → `<feImage>`（`displacement map`）→ `<feDisplacementMap in="blurred_source" in2="disp_map" scale=…>` → `feColorMatrix` saturate → 叠加预生成的 specular map（`index.html:383-397`）。`feDisplacementMap` 的规范语义是 `P'(x,y) ← P(x + scale·(XC−0.5), y + scale·(YC−0.5))`（[Filter Effects 1](https://www.w3.org/TR/filter-effects-1/)）。
- **WebGL 版**：全屏 fragment shader，`uBgTex` 单张背景纹理 + 16 tap 手写模糊（`webgl.html:252-277`）。

#### 核心算法（我们最需要的物理公式）

**表面高度剖面（`index.html:222-232`）**

```js
convex_squircle: x => pow(1 - pow(1-x, 4), 0.25)     // 四次"平顶"凸剖面
convex_circle  : x => sqrt(1 - (1-x)*(1-x))          // 四分之一圆剖面
concave        : x => 1 - sqrt(1 - (1-x)*(1-x))      // 凹
lip            : 凸/凹按 smootherstep 混合
```

x = 归一化"到边距离"（0=边缘 → 1=带内界），返回值 = 表面高度（0=边缘最薄）。

**射线折射剖面（`index.html:234-260`）** —— 最"物理"的一段，逐样本 Snell：

```js
eta = 1/ior;
function refract(nx, ny) {          // 标准 GLSL refract，I=(0,-1) 在剖面平面内
  const dot = ny, k = 1 - eta*eta*(1 - dot*dot);
  if (k < 0) return null;           // 全反射（空气→玻璃 eta<1，实际不会发生）
  const sq = sqrt(k);
  return [-(eta*dot + sq)*nx, eta - (eta*dot + sq)*ny];
}
for each sample x:
  y = heightFn(x); deriv = dh/dx;                 // 剖面斜率
  N = (-deriv, -1)/|…|                            // 2D 表面法线
  R = refract(N);                                 // 折射光线
  profile[i] = R.x/R.y * (y*bezelWidth + glassThickness);   // 到背面（平坦）的横向位移
```

即：**位移 = 折射光线在玻璃厚度内的横向偏移**。`R.x/R.y` 是偏折角的正切，乘以"该处玻璃厚度（弧高 + 基底厚度）"。

**WebGL 简化版（`webgl.html:297-317`）**

```glsl
float h  = surfaceHeight(t);                          // t = 到边距离/bezel
float dh = (surfaceHeight(t+0.001) - h) / 0.001;
float slopeAngle = atan(dh * (uThickness / bezel));   // 3D 坡度（高度按厚度缩放）
float sinR = clamp(sin(slopeAngle)/uIOR, -1.0, 1.0);
float thetaR = asin(sinR);
float displacement = h * uThickness * (tan(slopeAngle) - tan(thetaR));   // 棱镜偏折 × 局部厚度
vec2 grad = normalize(vec2(sd(p+eps)-sd, sd(p+eps)-sd));
vec2 offset = -grad * displacement / uResolution;     // 向内
```

**它没有色散**（WebGL 版只采一次；SVG 版只做一次位移）。色散在 §2.4/§2.5 里。

#### 输入依赖 / 可移植性 / 工作量 / 风险

| 项 | 结论 |
|---|---|
| 需要的纹理 | SVG 版：displacement map + specular map 两张预生成图；WebGL 版：1 张背景图。**我们可用"程序化 SDF + 一次 Snell"完全替代两张图**。 |
| 直接移植？ | 不能直接搬（WebGL/JS + 纹理 UV），但**公式可以直接抄进 AGSL**：`surfaceHeight`/坡度/Snell/厚度位移 在 AGSL 里是纯标量数学。 |
| AGSL 差异 | 它用 `texture2D`、`gl_FragCoord`、`vUv`、`resolution` 归一化、`precision highp float` —— 全部要改（§3）。 |
| 风险 | **边缘奇点**：`convex` 剖面在 x→0 时导数 → ∞，`h·tanθi` 在数学上发散（webgl 版靠 `dt` 离散化 + 数值碰巧压住）。移植时必须 **clamp 坡度** 或 **边缘 guard**（我们的候选同时用两者，见 §4）。 |
| 工作量 | 高度剖面 + Snell + 厚度位移 ≈ 40 行。≈ 0.25 人日。 |

---

### 2.4 `ruiqichenbiec/design-systems` — `lens/`（WebGL2）

- 仓库：<https://github.com/ruiqichenbiec/design-systems>（`main` @ 2026-11-27）
- **许可证：MIT**（GitHub API `license=mit`；**仓库根** `LICENSE` 1075B。注意：`lens/` 子目录**没有**独立 LICENSE，以根为准）
- 关键文件：`lens/lab/design-system/internal/renderer.js`（LENS shader，`:49-`）、`lens/START-HERE.md`。

#### 模型（E0 源码 `renderer.js:134-167`）

**它是最"干净"的可移植实现**：只做 3 件事，且全在 30 行内。

```glsl
// 1) 2D SDF 法线（数值差分，e = 0.7*scale）
vec2 n2 = normalize(vec2(surfaceDistance(p+vec2(e,0.))-surfaceDistance(p-vec2(e,0.)),
                         surfaceDistance(p+vec2(0.,e))-surfaceDistance(p-vec2(0.,e))) + vec2(.00001));
// 2) 倒角坡度 → 3D 法线
float bevel = min(min(b.x,b.y)*.48, 19.*uScale);
float edge  = clamp(1. + d/bevel, 0., 1.);          // d<0 内：边缘=1，带内界=0
float slope = pow(edge, 1.65) * .94;                // 坡度剖面（1.65 指数让弯曲集中在外圈）
vec2 normalXY = n2 * slope + faceNormal;            // faceNormal = 极浅的整面 dome
vec3 normal   = vec3(normalXY, sqrt(max(1. - dot(normalXY,normalXY), .001)));
// 3) Snell + 厚度位移
vec3  ray = refract(vec3(0.,0.,-1.), normal, 1./1.45);   // GLSL 内建 Snell
float thickness = (18. + uRefraction*57.) * uScale;      // px
vec2  offset = ray.xy / max(-ray.z, .05) * thickness * uRefraction;   // 向内
vec2  at = vPixel + offset;
// 4) 色散（只偏 R/B，G 为基准，避免整体偏色）
vec2 split = n2 * pow(edge, 2.8) * uDispersion * 2.6 * uScale;
c.r += (sampleScene(at+split).r - sampleScene(at).r) * .8;
c.b += (sampleScene(at-split).b - sampleScene(at).b) * .8;
```

**§2.3 与 §2.4 的差异**：archisvaze 用 1D 剖面算 `h·tanθ`；design-systems 直接构造 **3D 法线**再用 `refract()` + `ray.xy/(-ray.z)`。两者数学等价（都是棱镜偏折 = 厚度·tan(θi−θr)），但 design-systems 的写法**没有导数奇点**（`slope` 被 `n2` 的单位长度天然限制），更适合 AGSL。

#### 其它值得记录的点

- **玻璃叠玻璃**：它用 FBO ping-pong，"每块透镜折射**实际画面**（含前一块透镜），再贡献给后面的透镜"（文件头注释 `:1-6`）。这正是我们 glass-lab 报告 §5 的 **B 方案**（采样已合成结果）；我们当前是 A 方案（每块都采样原始背景，重叠区干净）。**要不要改成 B 是产品决策，不是 shader 移植问题。**
- `lens/START-HERE.md` 明示："Refraction samples the system canvas, not arbitrary DOM or third-party pages" —— 与我们的"捕获层"约束同构。
- 它的模糊是 shader 内 9 tap 手写（`blurred()`，`:94-108`）；我们不需要（blur 由 `RenderEffect` 硬件做，质量更好、更便宜）。

#### 输入依赖 / 可移植性 / 工作量 / 风险

| 项 | 结论 |
|---|---|
| 需要的纹理 | 1 张 `uScene`（全屏画面 FBO）。**与我们"捕获层"一一对应**。 |
| 直接移植？ | ✅ **公式层面几乎可以逐行翻译**，是全清单里移植成本最低的。 |
| AGSL 差异 | `#version 300 es`/`in/out`/`texture()`/`vPixel`（由顶点着色器传入）/`refract` 内建 —— 见 §3。关键：**注释掉顶点阶段后，`vPixel` 等价于 AGSL 的 `coord`**（AGSL 坐标就是像素坐标，左上原点）。 |
| 工作量 | 30 行公式 → AGSL ≈ 30 行（把 `refract()` 换成手写 Snell，因为 AGSL 是否提供 `refract` 内建未取证）。≈ 0.25 人日。 |
| 风险 | ① `mat2 uInverse`、旋转/拖拽/压力/能量全是交互态，不取；② `uZoom`（放大镜 mode）不取（见 §5 结构不允许）；③ 色散用"差分采样"（`sample(at+split)-sample(at)`）比直接三采样多 2 次采样，我们改成 3 采样。 |

---

### 2.5 `aaaa-zhen/glsl-playground` — `liquid-glass.html`

- 仓库：<https://github.com/aaaa-zhen/glsl-playground>（`main` @ 2026-11-27）；单文件 885KB（含内嵌数据），fragment shader 在 `:96-246`。
- **许可证：⚠️ 未确认**。仓库 7 个文件中**没有 LICENSE 文件**；README 末尾写 `## License / MIT`（E1 声称）。→ **可以取数学，不要搬代码**；商用前若要复用代码必须找作者确认。

#### 核心算法（E0 源码）

```glsl
// ① 解析 SDF 梯度（不用 4 次差分：比值/法线一步算出）
vec2 gradSdRoundRect(vec2 c, vec2 half_, float r){
  vec2 cc = abs(c) - (half_ - vec2(r));
  if (cc.x >= 0.0 || cc.y >= 0.0) return sign(c) * normalize(max(cc,0.0) + 1e-6);
  float gx = step(cc.y, cc.x);                      // 核心区：取最近轴
  return sign(c) * vec2(gx, 1.0 - gx);
}
// ② 圆柱/球面边缘 falloff（物理剖面）
float circleMap(float x){ return 1.0 - sqrt(max(1.0 - x*x, 0.0)); }
// ③ 折射：x = 1 - 到边距离/带 ; 边缘 x=1 → circleMap=1（位移最大）
float x = 1.0 - (-sd / max(refractionHeight, 1.0));
float d = circleMap(clamp(x, 0.0, 1.0)) * uRefraction;
vec2 push = vec2(grad.x, -grad.y) * d / iRes;       // 向内
// ④ 色散场：四角最强、中心/轴线为 0（苹果 chromatic_aberration_lpf 的做法）
float dispI = uAberration * ((c.x * c.y) / (uHalf.x * uHalf.y)) / 30.0;
vec2 disp = gradUV * d * dispI / iRes;
col.r = texture2D(iBlur, suv + disp).r;   // R 正偏
col.g = texture2D(iBlur, suv).g;          // G 基准（保证不整体偏色）
col.b = texture2D(iBlur, suv - disp).b;   // B 反偏
```

- 它**解析梯度**的思路很好，但 `sign(c)` 在 `c.x=0`（中线）处翻转 → 中线硬缝（同 §1.3 问题 2）。**我们不用它，改用宽差分 + axisFade**；只取 `circleMap` 与"色散场四角强"两个想法。
- 它有两张纹理（`iBg` 清晰背景 / `iBlur` 两 pass 高斯模糊结果）+ 一整套装饰（黑顶镜面、赤道亮线、Siri 波、整体 zoom）。**装饰全部不取**（G-21）。
- 它有 `uZoom` 整体放大（`suv = cuv + (suv-cuv)/uZoom`）——这是"参数化采样缩放"，不是从几何算出来的量，按 G-21 **不做**（与我们 §5 的"局部放大结构不允许"一致）。

#### 输入依赖 / 可移植性 / 工作量 / 风险

| 项 | 结论 |
|---|---|
| 纹理 | 2 张（清晰 + 模糊）。我们 1 张（blur 已由 RenderEffect 做）。 |
| 可移植性 | 公式可以，**代码不建议**（许可）。AGSL：`const float PI`、局部 `float w[5]` 数组、`mod`、`sign`、`step` 在 AGSL 的可用性未逐一取证 → 我们候选**一个都没用**。 |
| 工作量 | 取 `circleMap` + 色散场 ≈ 5 行。≈ 2 小时。 |
| 风险 | **许可证**（无 LICENSE 文件）；`gradSdRoundRect` 的中线不连续；大量装饰性内容。 |

---

### 2.6 标准侧：W3C Filter Effects + svgwg#1142（对照，不取代码）

- 规范：[Filter Effects Module Level 2](https://www.w3.org/TR/filter-effects-2/)（`backdrop-filter: none | <filter-value-list>`；`Backdrop Root Image` 定义见 §3）——**注意规范原文标注"does not yet have Working Group consensus"**（§2 开头）。`feDisplacementMap` 语义见 [Filter Effects 1](https://www.w3.org/TR/filter-effects-1/)：`P'(x,y) ← P(x + scale·(XC(x,y)−.5), y + scale·(YC(x,y)−.5))`。
- 提案：[w3c/svgwg#1142](https://github.com/w3c/svgwg/issues/1142)（2026-06-25 开，**open**）"Filter Effects: define interoperable backdrop displacement/refraction for 'liquid glass' UI"。要点：
  - 作者想要的写法是 `backdrop-filter: url(#refractive-glass-filter) blur(18px) saturate(140%)`，滤镜里是 `feImage(displacement map)` + `feDisplacementMap in="BackdropGraphic"`；
  - **现实中不可互操作**：`backdrop-filter: url(#svg-filter)` 对 `feDisplacementMap` 这类 SVG 滤镜在 WebKit 不工作（issue 引用 WebKit bug 245510），Chromium 部分可用 → 作者被迫 DOM 克隆 / WebGL 重建页面；
  - 提案方向：① 定义 `BackdropGraphic` 这类滤镜输入；② 完整规定 `backdrop-filter: url()` 支持哪些 primitive；③ 安全边界（无 JS 回读）；④ filter region/DPR；⑤ 甚至设想一个高层 `<feRefraction in=BackdropGraphic radius=… thickness=… indexOfRefraction=…>`（明确写"illustrative, not a concrete proposal"）；
  - WG 回复（longsonr）认为"feDisplacementMap 应该能用在 backdrop filter 上，只是实现没做"，倾向继续推进现有路径。
- **对我们的意义**：
  1. 我们 Android 侧做的正是 Web 侧**缺的那个东西**——"合成器安全的、单向的、对 backdrop 做位移"的能力。**不需要等标准**。
  2. 标准讨论里的坐标空间/滤镜区域/DPR 问题，在我们这里由"捕获层 + 层像素坐标"自然解决（§3 的坐标系差异）。
  3. 许可证：W3C 规范文本（document license），讨论 issue 文本（W3C 贡献者条款）→ **只引用，不复制代码**。

---

### 2.7 ShaderToy（未取证，不采用）

- 站点 `https://www.shadertoy.com/terms` 本机 **403**，未能取得许可条款 → **未确认**，按 G-00 不写进任何结论。
- 社区通行说法是 ShaderToy 默认 **CC BY-NC-SA 3.0（非商业）**，但**本条未取证**，不作为依据。
- 结论：**不采用**。它上面多数"玻璃折射"shader 的模型与 §2.4（屏幕空间法线 + IOR + 厚度位移）同构，我们已经有更明确许可的来源，没有必须用它的理由。

---

## 3. 移植差异矩阵（GLSL/WebGL → AGSL）

> 依据：**E0** = [RuntimeShader 官方 javadoc](https://developer.android.com/reference/android/graphics/RuntimeShader)（`android.graphics.RuntimeShader` 页面，含 "Android Graphics Shading Language" 专节）+ [AGSL 官方页](https://developer.android.com/develop/ui/views/graphics/agsl)（本机经 `developer.android.google.cn` 镜像取得）+ 本仓库已实机编译的 AGSL（`GlassLabPhysics.kt` / `GlassLabEffects.kt`）。**E1** = 第三方 AGSL 生产代码（RN 库、Haze）。**未确认** = 没取到一手依据的，候选 shader 一律避开。

| # | 特性 | GLSL / WebGL 写法 | AGSL 支持 | 需要改写的地方 |
|---|---|---|---|---|
| 1 | 坐标系 | `gl_FragCoord` 左下原点；WebGL 还要 `uv.y = 1-y` | **E0**：AGSL 的 `coord` 是 Canvas/RenderNode 坐标，**左上原点、y 向下**、单位=像素 | 删掉所有 `1.0 - uv.y`、删 `vUv`/`vPixel`；直接用 `coord` |
| 2 | 纹理采样 | `sampler2D` + `texture2D(tex, uv)`，uv∈[0,1] | **E0**：AGSL **不能采样纹理**；用 `uniform shader s; s.eval(pxCoord)`，坐标是**像素**不是归一化 uv，范围 `(0,0)`→`(w,h)` | `texture2D(iBlur, suv)` → `content.eval(suvPx)`；所有 `/uResolution` 归一化删掉；`1.0/resolution` 的步长改成 px |
| 3 | 多输入纹理 | 多个 `sampler2D` | **E0**：多个 `uniform shader` + `setInputShader(name, Shader)`；但 `createRuntimeShaderEffect(shader, "content")` 会**自动绑定名为 content 的那个**，其余要手动绑 | 我们只有 1 个 `content` → 无需 `setInputShader` |
| 4 | 顶点阶段/varying | `attribute/varying`，片元里拿插值 uv | **E0**：AGSL 只有 fragment 函数，没有顶点阶段、没有 varying | 删 `#version 300 es`、`in/out`；`vPixel` → `coord` |
| 5 | 输出 | `out vec4 outColor` / `gl_FragColor` | **E0**：入口 `main` 返回颜色；官方 javadoc 写 `vec4 main(vec2 canvas_coordinates)`；本仓库实际编译的是 `half4 main(float2)`（两者都出现于官方/生产代码） | `gl_FragColor = vec4(c,1)` → `return half4(c.r,c.g,c.b,1.0)` |
| 6 | 预乘 alpha | 通常是直 alpha | **E0**：AGSL 返回的颜色**必须预乘**：`[R*A,G*A,B*A,A]` | 若返回 alpha<1，必须 `col *= a`；我们层被 clip、alpha=1，故不涉及（但注释里写清） |
| 7 | 颜色空间 | 自己决定 | **E0**：AGSL 自动色彩管理（工作色空间=目标色空间）；`layout(color) uniform vec4` + `setColorUniform` 才会转换；提供 `toLinearSrgb/fromLinearSrgb` | 颜色 uniform 不要当普通 `float4` 传（我们候选不传颜色 uniform）；光照若要求物理精确应在线性空间加（§5 风险 9） |
| 8 | uniform 类型 | `bool`/`struct`/`sampler2D` 随便 | **E0**：原始 uniform 只有 `float`、`int`；外加 color（`layout(color)`）和 shader 两类；`vec2/3/4`、`mat2/3/4`、`float[]/int[]` 可用 | 用 `float` 0/1 代替 `bool`；结构体拆成平铺 uniform（我们候选 16 个 float/float2，无 bool/struct） |
| 9 | uniform 数组 | `uniform vec4 a[8]` | **E0**：`setFloatUniform(String, float[])` 支持数组（RN 用 `float4 iBodies[8]`——**E1**） | 不需要（我们单块玻璃） |
| 10 | 循环 | 任意 `for/while` | **E0**：AGSL 支持 `if-else/switch/for`，但**"存在一些限制，某些控制结构需要能在编译期求值的常量表达式"**；**E0**：不支持任何递归 | 候选 shader **0 个循环**（3 采样是写死的，不是循环）→ 直接绕开限制 |
| 11 | 导数 `dFdx/fwidth` | 可用 | **未确认**（AGSL 页/javadoc 未列出导数字语义） | 候选不用：法线来自 **SDF 差分**，不是屏幕空间导数 |
| 12 | `discard` | 可用 | **未确认** | 候选不用（RenderEffect 滤镜路径本就必须输出一个颜色） |
| 13 | 精度限定 | `precision highp float;` | 官方示例/生产 AGSL **都不写**这行；本仓库已编译的 shader 也不写 | 删 |
| 14 | SkSL 特有内建 | 无 | `select()`、`step()` 等 SkSL 写法在 AGSL 的可用性**未逐一取证** | 候选刻意只用**我们已实机编译过**的内建：`pow/sqrt/clamp/min/max/abs/length/dot/normalize/smoothstep/mix` + 类型构造 |
| 15 | `refract()` 内建 | GLSL 标准内建 | **未确认** AGSL 是否有 | 候选手写 Snell 公式（4 行，见 §4.2）—— 少一个依赖 |
| 16 | 主函数签名 | `void main()` | 必须返回颜色、参数是坐标 | `half4 main(float2 coord)` |

### 3.1 从四份参考代码里"逐行会报错"的地方（具体到行）

| 参考代码 | 行 | 在 AGSL 里必须怎么改 |
|---|---|---|
| `arch_webgl.html` | `:211` | `precision highp float;` → 删 |
| `arch_webgl.html` | `:212` `varying vec2 vUv;` → 删；`gl_FragCoord` 换成 `main` 的 `coord` |
| `arch_webgl.html` | `:249` `texture2D(uBgTex, uv)` → `content.eval(px)`（且删 `uv.y=1-uv.y`） |
| `arch_webgl.html` | `:305` `asin(sinR)` → 候选不需要（改用向量 Snell，只用到 `sqrt`） |
| `ds_renderer.js` | `:40` `#version 300 es` → 删 |
| `ds_renderer.js` | `:42-43` `in vec2 vPixel; out vec4 outColor;` → 删；`vPixel` → `coord` |
| `ds_renderer.js` | `:147` `refract(vec3(0,0,-1), normal, 1/1.45)` → 手写 Snell（§4.2 步骤 3） |
| `ds_renderer.js` | `:95` `texture(uScene, clamp(px/resolution, …))` → `content.eval(clamp(px, 0, size))` |
| `az_liquid-glass.html` | `:97` `precision highp float;` → 删 |
| `az_liquid-glass.html` | `:128` `const float PI` / `:258` 局部 `float w[5]` → 不要照搬（AGSL 的 const/局部数组未取证）；候选不用 |
| `az_liquid-glass.html` | `:160-161` `gl_FragCoord.xy` / `uvf = vec2(uv.x, 1-uv.y)` → `coord`；不翻 y |
| `az_liquid-glass.html` | `:117/:120` `sign()/step()` → 候选不用（改用宽差分 + `min/max`） |
| `rn LiquidGlassmorphismView.kt` | `:1466` `for (int i = 0; i < 8; i++) { … break; }` → 可直接用（**E1** 生产代码），但候选不需要 |
| `rn …kt` | `:1691-1693` `iResolution - 1.0`（float2 减标量，广播）→ 候选写成 `uSize - float2(1.0,1.0)`（更保守） |

---

## 4. 推荐方案：M5Glass（组合而成）

### 4.1 组合来源

| 组件 | 取自 | 为什么 |
|---|---|---|
| 效果链顺序（blur → shader，单一 chain） | RN 库 `:1232/:1296` | 与我们"捕获层"架构同构，且用 1 个 RenderEffect 图而不是 2 个 `graphicsLayer` |
| 形状 SDF（四角半径可配 + 圆角/超椭圆角双模式） | **自研**（对齐 `RoundedCornerShape` 与 `Superellipse.kt` 两族几何） | 与当前产品 clip 同一条曲线；所有社区实现都只有单一圆角矩形/任意轮廓 |
| 法线：宽差分 + `axisFade` | RN 库 `:1581-1630` | 唯一解决"中线硬缝"的方案，且已在生产验证 |
| 倒角坡度剖面 `pow(1-t,1.65)` | design-systems `:141` | 简单、无奇点、视觉上"弯曲集中在外圈" |
| Snell + 厚度位移（向内采样） | design-systems `:145-149`；archisvaze `webgl.html:302-306`、`index.html:234-260` 为其 1D 剖面版本 | 物理公式；`offset = thickness·tan(θi−θr)` 可解释 |
| 边缘保护带 `edgeGuard` | RN 库 `:1667` | 同时解决"最外缘采样越界/AA"与"掠射坡度发散" |
| 位移上限 `min(mag, maxDisp)` | Haze `:1102-1105`（`0.5·min(size)`） | 防极端参数花屏 |
| 色散：`offset·0.018`，R 少偏 / B 多偏 | RN 库 `:1689-1693`（比例 + R/B 只偏 G 基准） | 与位移成正比 = 物理；G 基准 = 不整体偏色 |
| 菲涅尔 | Schlick `0.04+0.96(1-cosθ)^5`（通用实时渲染公式） | 比 RN 的 `pow(1-N.z,3)` 更"有出处"；玻璃 F0≈0.04 |
| 内阴影 | RN 库 `:1747-1750`（背光侧权重） | 几何 + 光照方向，不是贴图 |
| **不取** | Haze 的 merged field/continuous corner/深度/交互；aza 的 zoom/黑顶/赤道线/Siri 波；archisvaze 的 specular map/displacement map 贴图；RN 的触摸放大/陀螺仪/多体融合 | 我们单块玻璃、无这些输入；其中"触摸光学放大"与"整体 zoom"按 G-21 属于参数化 warp，不做 |

### 4.2 完整候选 AGSL

> 文件：**[scratch/glass-shaders/m5glass-v1.agsl](../../../scratch/glass-shaders/m5glass-v1.agsl)**（195 行，含 uniform 注释）。
> 下面是该文件的**逐字全文**（§4.2 与 .agsl 内容必须一致：改一处改两处；以 .agsl 为准）。**尚未在本机编译**，见 §6.0。

```glsl
// ============================================================================
// m5glass-v1.agsl — myco M5 液态玻璃 / 真背景折射（AGSL，Android 13 / API 33+）
// ----------------------------------------------------------------------------
// 用途：挂在"已捕获的背景层"的 RenderEffect 图里，在真 blur 之后做屏幕空间折射。
//       效果链 = createChainEffect(shaderEffect(content), blurEffect)
//                即 result = shader(blur(capturedBackdrop))
//
// 坐标：AGSL 的 coord 是 Canvas / RenderNode 的像素坐标（左上原点，y 向下，
//       见 RuntimeShader 官方文档），因此 **不需要** GLSL/WebGL 里的 vUv.y 翻转，
//       也不需要把像素归一化成 uv：采样坐标直接用 px。
//
// 唯一输入：`content`（由 createRuntimeShaderEffect(shader, "content") 绑定到
//       "本层已渲染内容"= 按玻璃位置平移后画进来的捕获背景；层尺寸 = 玻璃像素尺寸）。
//       **不是**窗口背景，也不是 framebuffer —— AGSL 没有 backdrop 采样入口。
//
// 形状：必须与 Compose 的 clip 用**同一条曲线**（G-21）：
//   - uShapeN = 2  → 精确圆角矩形 SDF，匹配 androidx RoundedCornerShape
//                    （支持四角不同半径：sheet 只圆上两角、抽屉只圆右两角）；
//   - uShapeN = n  → 每角 |X|^n + |Y|^n = r^n 的超椭圆角（一阶距离），
//                    匹配本仓库 SuperellipseShape(radius, exponent=n)。
//   两种模式共用直边带/核心区的精确距离；只有角区公式不同。
//
// 数学来源（详见 docs/spec/ref/liquid-glass-shaders.md §2、§4）：
//   - 宽差分法线 + 中线 fade：himanshu-lal4/react-native-liquid-glassmorphism (MIT)；
//   - 坡度剖面 pow(1-t,1.65) + Snell + 厚度位移：ruiqichenbiec/design-systems lens (MIT)、
//     archisvaze/liquid-glass (MIT) 的贴片模型；
//   - 边缘保护带、色散比例、内阴影结构：react-native-liquid-glassmorphism (MIT)；
//   - 剖面族（circle/squircle）数值参照 chrisbanes/haze haze-glass (Apache-2.0)。
//   本项目只取数学公式与 AGSL 组织方式，未逐行复制上述代码。
//
// 设计约束（G-21，docs/spec/00-global-rules.md）：
//   每个像素的输出都必须能回答"从什么数据/公式算出来"：
//     d  = sdGlass(p)                    形状距离场（几何）
//     n  = ∇d（宽差分）                   表面法线（几何）
//     θi = atan(uSlope * ramp(t))         倒角坡度（几何剖面）
//     R  = Snell(I,N,1/IOR)               折射光线（物理）
//     offset = thickness * tan(θi-θr)     屏幕位移（棱镜偏折，几何光学）
//     色散 = offset * uDispersion         正常色散（n_R < n_B）
//     高光/内阴影 = Schlick 菲涅尔 / 背光权重（物理公式）
//   本文件不含任何渐变贴图、径向光斑、噪声、描边等"看起来像"的装饰。
// ============================================================================

uniform shader content;      // 已模糊的背景（本层内容），坐标 = 层内像素

uniform float2 uSize;        // 层像素尺寸 (W,H)。coord ∈ [0,uSize)
uniform float2 uHalf;        // uSize * 0.5（省一次除；必须与 uSize 同步）
uniform float4 uRadii;       // 四角半径 px：(tl, tr, br, bl) —— 与 GlassCorners.toFloatArray 同序
uniform float  uShapeN;      // 2 = 圆形角（匹配 RoundedCornerShape）；n>2 = 超椭圆角指数（匹配 SuperellipseShape）
uniform float  uBand;        // 倒角带宽度 px：折射/高光只在此带内非零（0 = 关）
uniform float  uSlope;       // 倒角最大坡度 tan(θ)（0..2；1.2 ≈ 50°）
uniform float  uThickness;   // 玻璃等效厚度 px：屏幕位移 = 厚度·tan(θi-θr)
uniform float  uRefract;     // 折射总强度 0..1（0 = 直通，供 A/B 像素 diff）
uniform float  uMaxDisp;     // 单像素采样位移上限 px（防边缘奇点/花屏）
uniform float  uDispersion;  // 色散系数：R/B 采样偏移 = 位移 * 系数（≈0.018）
uniform float  uIOR;         // 折射率 空气→玻璃（1.0..2.0，窗口玻璃 1.5）
uniform float  uEdgeGuard;   // 最外缘倒角缓冲 px：位移在此带内衰减到 0
uniform float2 uLightDir;    // 归一化光照方向（屏幕坐标，y 向下；左上光 ≈ (-0.7,-0.7)）
uniform float  uRim;         // 边缘镜面强度 0..1（菲涅尔 + 细亮边）
uniform float  uShadow;      // 内阴影强度 0..1（背光侧边沿压暗）
uniform float  uSat;         // 饱和度补偿（1 = 不变；玻璃透射通常 1.0..1.15）

// 按象限选角半径：p 以玻璃中心为原点，y 向下 ⇒ 上半 y<=0。
float cornerRadiusFor(float2 p, float4 radii) {
  if (p.y <= 0.0) {
    return p.x <= 0.0 ? radii.x : radii.y;      // 左上 / 右上
  }
  return p.x <= 0.0 ? radii.w : radii.z;        // 左下 / 右下
}

// ----------------------------------------------------------------------------
// sdGlass：四角可不同半径的玻璃距离场（负=内部）
//   p   = 相对中心的像素坐标；hs = 半尺寸；radii = 四角半径；n = 角指数
//   n=2 → 精确圆角矩形（与 RoundedCornerShape 同一条曲线，逐象限半径）
//   n>2 → 直边精确、角区 |X|^n+|Y|^n=r^n 的一阶距离（与 SuperellipseShape 同族）
// ----------------------------------------------------------------------------
float sdGlass(float2 p, float2 hs, float4 radii, float n) {
  float r = max(cornerRadiusFor(p, radii), 0.5);   // 0 半径会退化，给 0.5px 下限
  float2 q = abs(p) - hs + float2(r, r);           // 相对"角圆心矩形"的坐标
  if (q.x <= 0.0 && q.y <= 0.0) {
    return max(q.x, q.y) - r;                      // 核心区：到最近直边（两种模式都精确）
  }
  if (q.y <= 0.0) {
    return q.x - r;                                // 左右直边带
  }
  if (q.x <= 0.0) {
    return q.y - r;                                // 上下直边带
  }
  if (n <= 2.01) {
    return length(q) - r;                          // 角区：圆形角（精确）
  }
  // 角区：超椭圆角 |X|^n + |Y|^n = r^n 的一阶距离
  float a = q.x / r;
  float b = q.y / r;
  float f = pow(a, n) + pow(b, n) - 1.0;
  float g = n * sqrt(pow(a, 2.0 * n - 2.0) + pow(b, 2.0 * n - 2.0)) / r;
  float dCorner = f / max(g, 0.00001);
  // 用直边距离兜底：保证角区→直边带连续，且不会在角内侧给出虚假深值
  return max(dCorner, min(q.x, q.y) - r);
}

// 宽差分梯度：返回 (gx, gy, |∇d|)。
//   |∇d| ≈ 1 表示在普通边带；趋近 0 表示落在"中线"（medial axis，两条边等距的脊），
//   那里法线会翻转，必须用它把折射淡出（否则药丸/胶囊中间会出现一条硬缝）。
float3 sdfGradWide(float2 p, float2 hs, float4 radii, float n, float e) {
  float gx = sdGlass(p + float2(e, 0.0), hs, radii, n) - sdGlass(p - float2(e, 0.0), hs, radii, n);
  float gy = sdGlass(p + float2(0.0, e), hs, radii, n) - sdGlass(p - float2(0.0, e), hs, radii, n);
  float2 g = float2(gx, gy) / (2.0 * e);
  return float3(g.x, g.y, length(g));
}

half4 main(float2 coord) {
  // ---- 0) 纯模糊直通：A/B 验证基准（refract/高光/阴影全关时必须逐位等于 blur 结果）
  if (uRefract <= 0.0 && uRim <= 0.0 && uShadow <= 0.0) {
    return content.eval(clamp(coord, float2(0.0, 0.0), uSize - float2(1.0, 1.0)));
  }

  float2 p = coord - uHalf;

  // ---- 1) 形状距离场 + 表面法线 ----
  float d = sdGlass(p, uHalf, uRadii, uShapeN);   // <0 内部
  float distEdge = max(-d, 0.0);                  // 到边的距离 px

  float e = clamp(uBand * 0.3, 1.0, 16.0);        // 宽差分步长：band 的 30%，clamp 1..16px
  float3 g3 = sdfGradWide(p, uHalf, uRadii, uShapeN, e);
  float gl = max(length(g3.xy), 0.00001);
  float2 n2 = g3.xy / gl;                         // 外向法线（指向最近边）
  float axisFade = smoothstep(0.15, 0.8, g3.z);   // 中线 fade：|∇d| 小的脊上折射→0

  // ---- 2) 倒角剖面：边缘最陡，向内衰减到 0 ----
  //   t     = 0 在边缘，1 在带内界
  //   ramp  = 坡度份额（pow 1.65 让绝大部分弯曲集中在最外圈；数值取参考实现）
  //   guard = 最外 uEdgeGuard px 内把位移压到 0（相当于边缘 chamfer，
  //           同时避开边缘抗锯齿像素与"掠射奇点"）
  float t = clamp(distEdge / max(uBand, 1.0), 0.0, 1.0);
  float ramp = pow(1.0 - t, 1.65) * axisFade;
  float guard = smoothstep(0.0, max(uEdgeGuard, 0.001), distEdge);
  float bandMask = 1.0 - smoothstep(0.0, max(uBand, 1.0), distEdge);

  // 3D 表面法线：把 2D 法线按坡度倾斜，nz 由单位长度约束解出（不是猜的）
  float2 nxy = n2 * (uSlope * ramp * guard);
  float nz = sqrt(max(1.0 - dot(nxy, nxy), 0.001));
  float3 N = float3(nxy.x, nxy.y, nz);

  // ---- 3) Snell 折射 + 厚度位移（线模型：视线 I=(0,0,-1) 垂直入射平整背面）----
  //   R = eta*I - (eta*cosI + sqrt(k))*N,  eta = 1/IOR
  //   k = 1 - eta^2 * (1 - cosI^2)   （空气→玻璃 eta<1，k 恒 > 0）
  //   屏幕位移 = 厚度 * tan(θi - θr) = uThickness * (R.xy / -R.z)
  //     其中 θi = 倒角坡度角、θr = Snell 折射角；R.xy/-R.z 就是"偏折角"的正切，
  //     即经典棱镜偏折公式 —— 不是把 θr 当斜率用。
  //   方向：-R.xy ∥ nxy（向内），即"从更内侧采样"→ 边缘内容被向外放大/包边
  float3 I = float3(0.0, 0.0, -1.0);
  float eta = 1.0 / max(uIOR, 1.0);
  float ci = dot(N, I);
  float k = 1.0 - eta * eta * (1.0 - ci * ci);
  float3 R = eta * I - (eta * ci + sqrt(max(k, 0.0))) * N;
  float2 offset = (R.xy / max(-R.z, 0.05)) * (uThickness * uRefract);

  // 位移上限（软上限：只在极端处压回，不改变带内梯度形状）
  // 注意：这里刻意不用 SkSL 的 select()/step()，只用 min() —— 少一个"AGSL 是否支持"的问号。
  float mag = length(offset);
  offset *= min(1.0, uMaxDisp / max(mag, 0.00001));

  float2 lo = float2(0.0, 0.0);
  float2 hi = uSize - float2(1.0, 1.0);
  float2 base = clamp(coord + offset, lo, hi);

  // ---- 4) 正常色散：n_R < n_B ⇒ 红光偏折小于蓝光 ----
  //   R 采样点 = base - ca（位移更小），B = base + ca（位移更大），G 不偏（避免整体偏色）
  float2 ca = offset * uDispersion;
  half4 sR = content.eval(clamp(base - ca, lo, hi));
  half4 sG = content.eval(base);
  half4 sB = content.eval(clamp(base + ca, lo, hi));
  float3 col = float3(sR.r, sG.g, sB.b);

  // ---- 5) 饱和度补偿（模糊会掉饱和；真实玻璃透射只会更艳，不会更灰）----
  float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
  col = mix(float3(luma, luma, luma), col, uSat);

  // ---- 6) 光照：全部由几何 + 公式推出 ----
  float2 L = normalize(uLightDir);
  // Schlick 菲涅尔（玻璃 F0 ≈ 0.04）：只在倒角带内调制，平中央不参与
  float fres = 0.04 + 0.96 * pow(1.0 - nz, 5.0);
  // 细亮边：最外 ~uEdgeGuard 一圈的几何边（不依赖光照方向，边缘干净不结块）
  float rimLine = 1.0 - smoothstep(0.0, max(uEdgeGuard * 1.2, 1.0), distEdge);
  float rim = ((fres * bandMask) * 0.35 + rimLine * 0.65) * uRim;
  // 内阴影：背光侧更强，只作用于倒角带（几何推出，不是贴图）
  float back = clamp(0.5 - 0.5 * dot(n2, L), 0.0, 1.0);
  float shade = (1.0 - smoothstep(0.0, max(uBand * 1.5, 0.001), distEdge)) * (0.35 + 0.65 * back) * uShadow;

  col = col * (1.0 - shade) + float3(rim, rim, rim);
  col = clamp(col, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));

  // AGSL 要求预乘 alpha；本层被 Compose 裁剪到形状内，alpha 恒 1。
  return half4(col.r, col.g, col.b, 1.0);
}
```
### 4.3 Kotlin 调用骨架（`RuntimeShader` + `setFloatUniform` + chain effect）

> 目标：替换 `GlassLabPanel.kt:98-133` 里"两个 `graphicsLayer` 各挂一个 RenderEffect"的写法为**一条 effect 链**，其余捕获/绘制不动。
> 注意 3 个坑（都有 E0 依据）：① `createRuntimeShaderEffect(shader, "content")` 的第二参数**必须**等于 shader 里 `uniform shader content;` 的名字；② `createChainEffect(outer, inner)` 语义是 `result = outer(inner(source))`（javadoc 原文），所以外层给 shader、内层给 blur；③ `RenderEffect`/`RenderEffect` 是 `android.graphics` 的，挂到 Compose 要 `.asComposeRenderEffect()`。

```kotlin
package dev.myco.android.ui.m5.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/** M5Glass 的脚本参数（px 为单位；dp→px 在调用侧换算，renderScale != 1 时所有 px 参数同乘 renderScale）。 */
internal data class M5GlassParams(
    /** (tl, tr, br, bl) px，顺序 = GlassCorners.toFloatArray(density) */
    val radiiPx: FloatArray,
    /** 2 = 匹配 RoundedCornerShape（当前产品 clip）；4 = 匹配 SuperellipseShape(exponent=4)。**必须与 clip 一致** */
    val shapeN: Float = 2f,
    val bandPx: Float,
    val slope: Float = 1.2f,
    val thicknessPx: Float,
    val refract: Float = 1f,
    val maxDispPx: Float,
    val dispersion: Float = 0.018f,
    val ior: Float = 1.5f,
    val edgeGuardPx: Float,
    val lightDirX: Float = -0.707f,
    val lightDirY: Float = -0.707f,
    val rim: Float = 0.55f,
    val shadow: Float = 0.25f,
    val sat: Float = 1.05f,
)

/** 编译一次、复用；失败后不再重试（避免每帧抛异常）。 */
private object M5GlassShaderCache {
    private const val AGSL = """<<把 m5glass-v1.agsl 的文件内容原样贴进来>>"""
    private var shader: RuntimeShader? = null
    private var failed = false

    fun get(): RuntimeShader? {
        if (Build.VERSION.SDK_INT < 33 || failed) return null
        shader?.let { return it }
        return try {
            RuntimeShader(AGSL).also { shader = it }
        } catch (t: Throwable) {           // AGSL 编译失败在各 GPU/驱动上是现实问题（Haze 有专门测试类）
            failed = true
            GlassLabDiag.agslError = "M5Glass 编译失败: ${t.javaClass.simpleName}: ${t.message}"
            null
        }
    }
}

/**
 * 返回 null = 本设备不可用（API<33 / 编译失败 / 尺寸未就绪），调用方保持"只有 blur"的降级。
 * @param wPx,hPx 玻璃层的**像素**尺寸（= 捕获层被画进本层的尺寸，不是屏幕尺寸）
 */
internal fun buildM5GlassEffect(
    p: M5GlassParams,
    wPx: Int,
    hPx: Int,
    blurPx: Float,
): ComposeRenderEffect? {
    if (wPx <= 2 || hPx <= 2) return null
    val shader = M5GlassShaderCache.get() ?: return null
    return try {
        shader.setFloatUniform("uSize", wPx.toFloat(), hPx.toFloat())
        shader.setFloatUniform("uHalf", wPx * 0.5f, hPx * 0.5f)
        // 四角半径：与 clip 使用同一组数值（同一条边两角必须相等）
        shader.setFloatUniform("uRadii", p.radiiPx)
        shader.setFloatUniform("uShapeN", p.shapeN)
        shader.setFloatUniform("uBand", p.bandPx)
        shader.setFloatUniform("uSlope", p.slope)
        shader.setFloatUniform("uThickness", p.thicknessPx)
        shader.setFloatUniform("uRefract", p.refract)
        shader.setFloatUniform("uMaxDisp", p.maxDispPx)
        shader.setFloatUniform("uDispersion", p.dispersion)
        shader.setFloatUniform("uIOR", p.ior)
        shader.setFloatUniform("uEdgeGuard", p.edgeGuardPx)
        shader.setFloatUniform("uLightDir", p.lightDirX, p.lightDirY)
        shader.setFloatUniform("uRim", p.rim)
        shader.setFloatUniform("uShadow", p.shadow)
        shader.setFloatUniform("uSat", p.sat)

        // 第二参数 = shader 里 uniform 的名字，必须叫 "content"
        val material = RenderEffect.createRuntimeShaderEffect(shader, "content")
        if (blurPx <= 0.1f) {
            material.asComposeRenderEffect()
        } else {
            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            // result = outer(inner(source)) = shader(blur(layer content))
            RenderEffect.createChainEffect(material, blur).asComposeRenderEffect()
        }
    } catch (t: Throwable) {
        GlassLabDiag.agslError = "M5Glass 应用失败: ${t.javaClass.simpleName}: ${t.message}"
        null
    }
}
```

调用位（替换 `GlassPanel` 的 `texture()`，保持"先录背景再挂效果"的顺序）：

```kotlin
val density = LocalDensity.current
val px = IntSize(constraints.maxWidth, constraints.maxHeight)
val params = remember(px, spec) {
    with(density) {
        M5GlassParams(
            radiiPx = floatArrayOf(
                spec.cornerDp.dp.toPx(), spec.cornerDp.dp.toPx(),
                spec.cornerDp.dp.toPx(), spec.cornerDp.dp.toPx(),
            ),   // 真实调用方传 GlassCorners.toFloatArray(density)；shapeN 必须与 clip 同步
            shapeN = 2f,
            bandPx = 14.dp.toPx(),        // 起始值，见 §4.4
            thicknessPx = 12.dp.toPx(),
            maxDispPx = 4.dp.toPx(),
            edgeGuardPx = 1.5.dp.toPx(),
        )
    }
}
val chain = remember(px, params, blurPx, Build.VERSION.SDK_INT) {
    buildM5GlassEffect(params, px.width, px.height, blurPx)   // 参数不变 → 不重建 effect
}
val capture = LocalGlassCapture.current
Box(
    Modifier
        .fillMaxSize()
        .clip(shape)
        // 关键：graphicsLayer 在 drawBehind 左边 = 它是外层，效果作用于"右边画进去的内容"
        .then(if (chain != null) Modifier.graphicsLayer { renderEffect = chain } else Modifier)
        .drawBehind {
            if (capture != null) {
                val t = capture.originInRoot - posInRoot
                translate(t.x, t.y) { drawLayer(capture.layer) }
            }
        }
) { /* 前景内容：文字/图标，保持锐利 */ }
```

> 降级：`chain == null`（API<33 / 编译失败 / 着色器应用失败）时**不要保留"裸 blur"当玻璃**——按 **D-74**（`docs/spec/decision-pack.md` §6.20）"API 31–32 的裸 blur+tint 不算玻璃"，一律走 A 不透明 neumorphism（`GlassSurface` 的 `fallbackColor` 分支）。
> 注：`<<把 .agsl 内容原样贴进来>>` 不要手抄——实现时用 `const val` 三引号包住文件内容即可（Kotlin `"""` 内允许 `$`，若有未转义 `$` 需改成 `${'$'}`；本文件没有 `$`）。

### 4.4 参数起始值 + 与产品 token（D-73）的换算

**产品 token 已定**（`docs/spec/decision-pack.md` §6.20 D-73、`GlassMaterial.kt:40-56`）：`blur 24dp`（modal 32–40dp）/ `tint 4% 白` / `refract 3dp` / `band 6dp` / `dispersion 1px` / `rim 35%` / `innerShadow 25%`。
候选 shader 用的是**物理参数**（坡度/厚度/IOR），产品 token 用的是**像素参数**（refractPx/dispersionPx）。两者接口如下：

| 产品 token / 形状 | 候选 uniform | 换算 |
|---|---|---|
| `GlassCorners(tl,tr,br,bl)` | `uRadii`（float4） | `GlassCorners.toFloatArray(density)` **原样传**；与 `clip(corners.toShape())` 同一组值 |
| `corners.toShape()` = `RoundedCornerShape` | `uShapeN = 2.0` | 当前产品实现；**不能**传 4（否则折射轮廓与裁剪轮廓不是同一条曲线） |
| `band = 6dp` | `uBand = band.toPx()` | 直接 |
| `refract = 3dp`（**最大采样位移**） | `uMaxDisp = refract.toPx()`；`uThickness` 由 CPU 反解 | `uThickness = refractPx / tanDeviation(uSlope, uIOR)`，`tanDeviation(s,ior) = tan(atan(s) − asin(sin(atan(s))/ior))`（s=1.2/ior=1.5 时 ≈0.352）→ 顶点处最大位移 ≈ refractPx |
| `dispersion = 1px`（**绝对**位移） | `uDispersion = dispersionPx / refractPx` | D-73 是"1 物理 px"；在 3× 上 refractPx=9px → `uDispersion ≈ 0.111`。⚠️ 这个比例比参考资料大得多（RN=0.018）：D-73 的 1px 是在旧的"绝对色散"shader 上调的，**建议先用 0.018–0.05 试观感**，确认"色边几乎看不见"再决定是否放大到 0.111 |
| `rim = 0.35` | `uRim = 0.35` | 直接 |
| `innerShadow = 0.25` | `uShadow = 0.25` | 直接 |
| `tint = 4% 白` | —— | **不进 shader**（现有 `drawRect(tint)` 在产品层做，保持锐利；候选输出 alpha=1，叠加语义不变） |
| `blur = 24dp` | —— | **不进 shader**（`createChainEffect` 的 blur 内层） |

候选的**独立调参起始值**（不接产品 token 时；density=3 的 px 会 ×3）：

| 参数 | dp | px@3x | 说明 |
|---|---|---|---|
| `bandPx`（倒角带宽） | 6dp（D-73）～14dp（视觉最大） | 18～42 | 太小=折射像一条线；太大=整块都在弯，中央不"平" |
| `slope`（最大坡度 tanθ） | — | 1.2 | θ≈50°；≥1.5 边缘开始明显"压缩" |
| `thicknessPx`（等效厚度） | 由 refract 反解 | ≈9/0.352≈26 | 位移 = 厚度·tan(θi−θr) |
| `maxDispPx` | 3dp（D-73） | 9 | 位移硬上限；碰到说明厚度给大了 |
| `dispersion` | — | 0.018（推荐） | R/B 分离 = 位移的 1.8%；D-73 的 1px 对应 0.111（见上表警告） |
| `IOR` | — | 1.5 | 1.33 水 / 1.5 玻璃 / 1.7 重火石 |
| `edgeGuardPx` | 1.5dp | 4.5 | 最外倒角缓冲 |
| `rim` / `shadow` | — | 0.35 / 0.25（D-73） | 与产品 token 对齐 |
| `sat` | — | 1.05 | 大半径 blur 会掉饱和，小幅补 |

**验收前必须做的一件事**：先只绑 `uRefract=0, uRim=0, uShadow=0`，确认画面与"不加 shader 的 blur"**逐位一致**（§6 A 步）——这一步证明绑定/坐标/chain 顺序都对，再开参数。

### 4.5 与现有 `AGSL_PHYSICS_GLASS` 的 diff（3 个必须改的点）

| # | 现状（`GlassLabPhysics.kt`） | v1 | 原因 |
|---|---|---|---|
| 1 | `base = coord + n * (f * refractPx)`（`+`，向外） | `base = coord + offset`，`offset = R.xy/(-R.z)·thickness`（向内） | 层里没有玻璃外的像素；向外采样只能被 clamp 拉边（§1.3-1） |
| 2 | 法线差分 `e=0.75`，无色散比例 | `e=clamp(band*0.3,1,16)` + `axisFade=smoothstep(0.15,0.8,\|∇d\|)`；色散 `ca=offset*uDispersion` | 窄差分在宽扁玻璃/胶囊的中线上产生 1px 硬缝；独立 `dispPx` 在 refract=0 时仍会动（§1.3-2/3） |
| 3 | 两个 `graphicsLayer{renderEffect}` 分别挂 blur / shader | 一个 `createChainEffect(material, blur)` | 两次离屏 pass → 一次；顺序显式化（shader 必须采样**已模糊**的内容） |

**集成到产品实现 `primitives/GlassSurface.kt` 时多一条注意**：它把**模糊层**按 `1.5×blurPx` 外扩录制（`:264-274`），但**折射层**按 `(w,h)` 原尺寸录制（`:278-283`）——所以折射 shader 的 `content` 层里**只有玻璃矩形内的像素**，`uSize = (w,h)`。
这正是 §1.3-1 说的"只能向内位移"的硬边界：若把候选的 `base = coord + offset` 改回向外（`coord - offset`），最外圈会采到 clamp 的拉边像素。
若产品坚持要"向外"的观感（缩小/凸出），必须把折射层也外扩录制并在 CPU 侧把 `uSize`/坐标原点一起平移（可行但要改 `drawGlassBackdrop` 的第三段，成本 +1 遍层）——**候选 v1 不做**。

---

## 5. 风险与"结构不允许"

### 5.1 结构不允许（写进规格，不用近似凑）

| # | 做不到的事 | 原因（为什么结构性） | 替代 |
|---|---|---|---|
| 1 | **真 3D 折射 / 光线求交 / 局部放大（"镜头里的背景变大"）** | 我们只有**一张 2D 背景纹理**，没有场景深度、没有第二视角、没有几何。真折射需要"折射光线打在另一侧的哪个物体上"这个 3D 信息；只有深度图才能近似。屏幕空间做"以某点为中心放大"是参数化采样 warp，不是从数据算出来的量 → G-21 禁止 | 只做**边缘带位移**（法线 × 距离，几何量）。要真放大必须引入深度（Android 上没有背景深度 API）→ 不做 |
| 2 | **环境反射 / 倒影图像** | `content` 只有背景层，没有环境（cube map/IBL）。菲涅尔只能给"边缘亮度"，给不出"镜面里照到什么" | 菲涅尔只做边缘高光强度（候选 §4.2 步骤 6）；要真反射需美术提供 env 图并接受"不是真环境"→ 不做 |
| 3 | **跨窗口背景**（Dialog / Popup / 其他 App） | AGSL 只能采样本 RenderNode；捕获只能同窗口（Dialog 是独立 window） | 窗口级 `setBackgroundBlurRadius`+`FLAG_BLUR_BEHIND`（API31+，OEM 依赖）或改成同窗口浮层；见 [`android-blur-glass.md`](android-blur-glass.md) §2.5 |
| 4 | **连续光谱色散** | 只有 RGB 三通道采样，没有波长/Abbe 数/光谱积分 | 三波段近似（R/G/B），公式仍来自正常色散；文档里必须写"三波段"而不是"全光谱" |
| 5 | **API<33 的折射 / 31-32 的色散** | `RuntimeShader`/`createRuntimeShaderEffect` 是 API33；31-32 只有 `RenderEffect` 模糊 | 按 **D-74**：API<33 **一律不做玻璃**（A 不透明 neumorphism）。**不保留"裸 blur+tint"当玻璃**——实测那是磨砂面板、没有边缘弯折，属 G-21 禁止的半成品近似 |
| 6 | **向外位移（缩小/外扩）** | Compose 1.7.5 的 `graphicsLayer` 会把层**隐式裁到 bounds**，层里没有玻璃外侧的像素；`LayerOutsets` 要 Compose 1.12 | 只向内位移（候选就是这样）；若将来必须外扩，需要给层加 padding 重绘（成本↑），或升级 Compose |
| 7 | **同屏大量玻璃** | 每块玻璃 = 每帧重画背景 + 1 次 blur + shader（3 采样/px） | 同屏 ≤2–3 块（glass-lab 报告 §5）；滚动列表上方只放 1 块；玻璃在滚动场景里按决策 D-54 只用于瞬态浮层 |

### 5.2 风险（可做，但要盯着）

| # | 风险 | 依据 / 缓解 |
|---|---|---|
| 1 | **shader 编译失败**（不同 GPU 驱动） | Haze 有 `BlurRuntimeShaderFailureAndroidHostTest`；RN 有 `SHADER_COMPILE_FAILED` 事件 → 必须 try/catch + 记录 tier；失败时保持 blur（已写进骨架） |
| 2 | **性能**：3 采样/px + 5 次 SDF 调用/px | RN 同量级（3 采样 + 每次 2 次 SDF 的宽差分 ×2 轴）；我们的 SDF 比它便宜（无 8 体循环）。**本机未测真机帧预算**（glass-lab 是 host GPU 模拟器，P50 25ms 基线不可用）→ 必须真机 Macrobenchmark |
| 3 | **边缘 1–2px 的 AA** | 层被 Compose clip 到形状，边缘像素有 AA；`uEdgeGuard` 把最外几 px 的位移压到 0，避免"边缘像素被放大成锯齿"。真机看 1px 级 |
| 4 | **色散方向/强度** | 物理上 R 偏折小于 B（正常色散）；如果参考图观感相反（异常色散/装饰），按 G-21 不追参考图、保持物理 |
| 5 | **颜色空间/线性度** | 候选的高光是**在工作色空间直接相加**（与 RN 一致）。严格物理应在 `toLinearSrgb` 里加高光再 `fromLinearSrgb`（AGSL 官方文档有这两个 intrinsic）。候选没做：① 少 2 次转换；② 视觉差很小。**这是有意的近似，要写进实现备注**（如果做暗色模式下的高强度高光，可能出现"过曝发白"，那时再切线性） |
| 6 | **参数联动**：`band`↑ 会让 `e`↑（宽差分步长）→ 法线在中线附近更"糊" | `e` 已 clamp 到 ≤16px；band 超过 ~50px 时建议单独调 `e`（后续可拆成独立 uniform） |
| 7 | **`uSlope` 过大** | `|nxy| = uSlope·ramp` 可能 >1 → `dot(nxy,nxy)>1` → `nz` 被 `max(...,0.001)` 兜住，但 3D 法线会轻微失真；`uSlope` 建议 ≤1.5 |
| 8 | **捕获内容缺失**（SurfaceView/TextureView/视频、跨 Compose 树的 AndroidView） | RN 的软件捕获明确拿不到 SurfaceView/TextureView（README 错误事件里写死）；Compose `DrawScope.record` 对 AndroidView 的行为**未验证** → 玻璃区域可能出现"未更新的旧帧/空洞"。验证见 §6-H |
| 9 | **未在本机编译** | 候选只做了"语法/内建/约束"静态检查；§6 第 0 步是 smoke compile，失败就按报错删特性（我们一个循环/数组/bool/纹理都没有，可删的余地很小） |
| 10 | **许可证合规** | Haze Apache-2.0：若后续把它的模板代码/数值表复制进仓库，必须加 `NOTICE`；`aaaa-zhen` 未确认 → 禁止复制其代码 |
| 11 | **形状漂移：clip 与 SDF 不同步** | 当前产品 clip 是 `RoundedCornerShape`（§1.4），设计系统目标是 `SuperellipseShape`。候选用 `uShapeN` 同时支持两者，但**一旦调参时传错**（clip 圆角、SDF 传 4），折射带会落在裁剪轮廓之外/之内，观感是"边缘有一圈固定的亮/暗环"（G-21 违规）。缓解：`uShapeN` 与 `corners.toShape()` 在**同一个 `remember` 块里**派生，不允许调用方单独传 |

---

## 6. 验证计划（像素 diff 证明"真折射"）

> 目标：证明 **① 折射只在边缘带；② 内部逐位不变；③ 色散只在边缘且方向正确；④ 不依赖贴图/加亮**。
> 方法沿用 [`glass-lab-report.md`](../../reports/glass-lab-report.md) §12.2 的协议（本仓库已验证一次：0.68% / 内区 0）。

### 6.0 第 0 步：smoke compile（先证明能跑）

1. 在 GlassLab 里加一个 `glass_scene=m5glass`（或临时替换 `physics`），`try/catch` 状态写进 `GlassLabDiag.agslStatus`。
2. `adb logcat -s GlassLab` 确认：`status=可用`；若是 `失败`，按异常信息逐项删特性（先删 `uSat` 的 `mix`、再删 `pow(1-t,1.65)` 改 `(1-t)`、最后退化为"只有 Snell 位移"）。
3. 先用 `uRefract=0,uRim=0,uShadow=0` 出图，与"不加 shader 的 blur"对比：**必须逐位相同**（这一条同时验证 chain 顺序、坐标、绑定名）。

### 6.1 A/B 协议（核心判据）

固定背景（静态图/静态列表）、固定所有参数，只改一个：

```
A: uRefract=0,  uRim=0, uShadow=0            → 只有 blur（shader 走直通分支）
B: uRefract=1,  uRim=0, uShadow=0            → 只有折射（无高光/阴影，避免污染 diff）
C: uRefract=1,  uDispersion=0.018            → 折射 + 色散
D: uRefract=1,  uLightDir 旋转 90°            → 只验高光方向（证明高光由法线算）
```

逐像素 diff（A vs B）：

| 判据 | 期望 | 不通过说明 |
|---|---|---|
| 变化像素的外接框 | **= 玻璃体本身**（与 A 的玻璃 bbox 误差 0） | 变化溢出玻璃 → 有全屏位移/副作用 |
| 变化像素占比 | **上限 = 边缘带面积 ≈ 周长×band**（874×394、band 42px ≈ 2.5k×42 ≈ 10.6 万 px ≈ 玻璃面积 31%）；实际会明显小于上限（位移 <1px 的像素 diff=0）。参考基线：band=18px 时全屏变化 0.68%、玻璃面积约 4% | 太低 = shader 没生效；接近/超过上限 = 有 band 外的位移或全局改动 |
| **内缩 `band + maxDisp + 2px` 的内区** | **maxDiff = 0，逐位相同** | ≠0 → 有全局偏移/提亮/饱和度改动（`uSat≠1` 也算） |
| 最大单通道差 | 边缘处 10–80 量级 | 满屏 255 = 采样错（黑/白） |
| C vs B（色散） | 变化只在带内；内区 0；且 **R 通道位移 < B 通道位移**（沿法线方向比较，可用逐像素位移场测试） | R/B 反了 = 色散符号错（非正常色散） |
| D（光向旋转 90°） | 高光/内阴影沿边**整体移动约 90°**，且能预测（`back = 0.5-0.5·dot(n,L)`） | 高光不动 = 用了贴图/常量而非法线 |

### 6.2 采集与统计（脚本骨架）

```powershell
# 1) 抓两张 PNG（同一场景、同一参数，只改 uRefract）
adb shell screencap -p /sdcard/A.png ; adb pull /sdcard/A.png _ws\out\A.png
adb shell screencap -p /sdcard/B.png ; adb pull /sdcard/B.png _ws\out\B.png
# 2) 逐像素 diff（PowerShell + .NET，免装依赖）
Add-Type -AssemblyName System.Drawing
$a=[System.Drawing.Bitmap]::FromFile("_ws\out\A.png"); $b=[System.Drawing.Bitmap]::FromFile("_ws\out\B.png")
$cnt=0; $max=0; $minX=1e9; $minY=1e9; $maxX=-1; $maxY=-1
for($y=0;$y -lt $a.Height;$y++){ for($x=0;$x -lt $a.Width;$x++){
  $pa=$a.GetPixel($x,$y); $pb=$b.GetPixel($x,$y)
  $d=[Math]::Max([Math]::Abs($pa.R-$pb.R),[Math]::Max([Math]::Abs($pa.G-$pb.G),[Math]::Abs($pa.B-$pb.B)))
  if($d -gt 0){ $cnt++; if($d -gt $max){$max=$d}
    if($x -lt $minX){$minX=$x}; if($x -gt $maxX){$maxX=$x}
    if($y -lt $minY){$minY=$y}; if($y -gt $maxY){$maxY=$y} } } }
"changed=$cnt maxDiff=$max bbox=($minX,$minY)-($maxX,$maxY)"
# 3) 内区检查：只在玻璃 bbox 内缩 N px 的范围统计（另写循环；期望 maxDiff=0）
```

> 既有脚本：`_ws/out/glassdiff.ps1`（上一轮验证用），可直接改路径复用。

### 6.3 性能 / 真机

| 项 | 方法 | 通过线 |
|---|---|---|
| 帧时间 | `adb shell dumpsys gfxinfo dev.myco.android reset` → 静止 / 背景滚动 7s → 再 dump | 1 块玻璃 vs 0 块，P90 增量 ≤ 3ms（浮层场景）；滚动场景若 > 5ms → 降 `band`/关色散/降 renderScale |
| 同屏叠加 | 1 / 2 / 3 块 | ≤3 块（glass-lab §5：3 层以上发灰变脏） |
| 滚动跟随 | 录屏 3s 滚动 | 模糊与折射都跟随，无 1 帧以上滞后（API31 有"陈旧模糊"已知问题，33+ 未见） |
| 降级 | 模拟 API 33 以下（或临时把 SDK 判断改掉） | 走 blur+tint，不崩、不黑块 |
| 捕获边界 | 背景放 `AndroidView(VideoView)` / `SurfaceView` | 记录实际情况（可能拿不到 → §5.2-8），决定是否把该区域的玻璃降级 |

### 6.4 视觉验收（G-21 口径）

- `uRefract=0` 与 `uRefract=1` 的**并排截图**必须能指出"边缘带被弯折"，中心完全清晰（不能是"整块变亮/变糊"）。
- 关掉 `uRim`/`uShadow` 后，玻璃仍应有"边缘弯折"（折射）——如果关掉高光就"看起来不像玻璃"，说明我们在用高光冒充折射（G-21 反例）。
- 色散必须**几乎看不见**（RN 实测 iOS 玻璃只有 1.8% 位移分离）；如果 3dp 位移下色边肉眼明显 = 参数错。
- 不允许出现：外圈光晕、径向"镜头"、月牙、沿边等宽亮线（等宽线是描边，不是菲涅尔）。

---

## 附 A：证据与链接汇总

**一手（我们自己的实测/源码）**
- 捕获层 + blur + AGSL 折射基线：`app-android/src/main/kotlin/dev/myco/android/ui/m5/glass/GlassLabPhysics.kt`（`AGSL_PHYSICS_GLASS:56-115`、`buildPhysicsGlassEffect:123-155`）
- 捕获 API：`GlassLabCapture.kt`（`RecordInto:47-79`、`LocalGlassCapture:98`）
- 面板/效果链：`GlassLabPanel.kt:160-235`；`GlassLabEffects.kt`（`buildBlurPlusColorEffect:28-66`、`AGSL_LIQUID:165-195`）
- 形状：`Superellipse.kt:28-128`
- 像素 diff 实测：`docs/reports/glass-lab-report.md:320-349`
- 全局规则：`docs/spec/00-global-rules.md` G-19/G-21

**外部（按 §2 顺序）**
- RN：<https://github.com/himanshu-lal4/react-native-liquid-glassmorphism>｜`LICENSE`（MIT）｜`android/src/main/java/com/liquidglassmorphism/LiquidGlassmorphismView.kt`（`GLASS_AGSL:1416-1811`、`buildEffect:1229-1319`、`onPreDraw:909-942`、`SharedBackdrop.kt:55-90`）｜`docs/android-liquid-glass.md`
- Haze：<https://github.com/chrisbanes/haze>｜Apache-2.0｜`haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassShaders.kt`（`:21-190` 主 shader、`:923-945` 剖面、`:962-1002` 高度、`:1083-1138` 折射、`:1527-1560` 解析梯度、`:1632` 边缘梯度）｜`RefractionProfile.kt`｜`SurfaceProfile.kt`
- archisvaze：<https://github.com/archisvaze/liquid-glass>｜MIT｜`index.html:222-232`（剖面）、`:234-260`（Snell/厚度剖面）、`:262-307`（displacement map）、`:383-397`（SVG 滤镜图）｜`webgl.html:210-337`（GLSL 版）
- design-systems：<https://github.com/ruiqichenbiec/design-systems>｜MIT（API `license=mit` + 根 `LICENSE`）｜`lens/lab/design-system/internal/renderer.js:49-170`（LENS shader，关键在 `:134-167`）｜`lens/START-HERE.md`
- aaaa-zhen：<https://github.com/aaaa-zhen/glsl-playground>｜**未确认**（README 称 MIT，无 LICENSE 文件）｜`liquid-glass.html:96-246`（shader，关键 `:114-124`、`:172-196`）
- W3C：<https://www.w3.org/TR/filter-effects-2/>（`backdrop-filter` / Backdrop Root，未获 WG 共识）｜<https://www.w3.org/TR/filter-effects-1/>（`feDisplacementMap` 公式）｜<https://github.com/w3c/svgwg/issues/1142>（backdrop displacement 提案，open）

**Android 官方（AGSL 约束）**
- `RuntimeShader` javadoc（AGSL 差异、预乘 alpha、色彩管理、uniform 类型、`uniform shader`/`eval`/`setInputShader`）：<https://developer.android.com/reference/android/graphics/RuntimeShader>
- AGSL 总览（语法、无递归、for/switch 限制、部分控制结构需编译期常量）：<https://developer.android.com/develop/ui/views/graphics/agsl>
- `RenderEffect.createChainEffect(outer, inner)` = `outer(inner(source))`：<https://developer.android.com/reference/android/graphics/RenderEffect>
- `createRuntimeShaderEffect(shader, name)` 绑定本层内容：同上 `RenderEffect` 页

## 附 B：本文档与 `.agsl` 文件的同步约定

- 候选 shader 的**唯一权威副本**是 [`scratch/glass-shaders/m5glass-v1.agsl`](../../../scratch/glass-shaders/m5glass-v1.agsl)；§4.2 是它的镜像（省略了文件头注释）。
- 改参数/公式时：先改 `.agsl`，再同步 §4.2，最后跑 §6.1 的 A/B。
- 行号引用（`.kt:行`）对应 2026-11-27 的 `main`/本仓库状态；外部仓库更新后需重新核对。
