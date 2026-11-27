# P4 玻璃材质（M5Glass v1）实现与验收报告

> 任务：把已验证的真玻璃材质做成可复用原子并接到浮层上（G-19 路线 B / G-21 / D-73…D-75）。
> 平台：Kotlin 2.0.21 / Compose UI 1.7.5 / minSdk 26 / compileSdk 35。
> 验证环境：`emulator-5554`（AVD myco35，**API 35，host GPU：AMD Radeon 890M / GLES 3.1，skiagl 硬件加速**），1080×2400，density 2.625。
> 依据：`docs/spec/00-global-rules.md` G-19/G-20/G-21；`docs/spec/decision-pack.md` §6.20（D-73…D-75）；`docs/spec/ref/backdrop-blur-deep.md`；`docs/reports/glass-lab-report.md` §12；`docs/spec/ref/liquid-glass-shaders.md` §4/§6（社区 shader 移植研究）+ `scratch/glass-shaders/m5glass-v1.agsl`。
> 状态：**构建通过、305 个单测通过、模拟器 4 项验收通过**。未 commit；未接 ChatScreen/Shell 接线（按约定留给 Lead）。

---

## 0. 一句话结论

三件原子（`GlassMaterial` / `GlassBackdrop` / `GlassSurface`）+ 两个 in-window 玻璃浮层（`M5GlassSheet` / `M5GlassDrawer`）已落地；AGSL 已按社区移植结论修掉 3 个结构性缺陷（向外采样→Snell 向内采样、窄差分→宽差分+中线 fade+比例色散、两层 effect→**单条 chain**），并额外修了一个只有实机才会暴露的 **outsets 越出捕获源层 → 边缘半透明**问题。**四项验收全部通过**：直通逐位一致（玻璃区 0/1,124,280 变化）、折射 A/B 内区 0 变化（maxDiff=0）、色散 R<G<B（4.75/7.85/11.00 px）、光向旋转 90° 后高光带从左边移到右边（左 −5.10 / 右 +4.94 亮度）。

**现有 `M5Sheet` / `M5Modal` / `M5Lightbox` 都是 `Dialog`（独立 window）→ 结构上不允许捕获背景（G-21），按规则保持 A 不透明，未伪造玻璃**；玻璃形态用同一 Compose 树内的新原语（见 §3、§7）。

---

## 1. 交付文件

| 文件 | 类型 | 内容 |
|---|---|---|
| `app-android/src/main/kotlin/dev/myco/android/ui/m5/primitives/GlassMaterial.kt` | 新建 | D-73 材质 token、`GlassCorners`（四角半径 + 圆角/超椭圆双模式，**clip 与 shader 的单一真源**）、`isGlassSupported()` / `rememberGlassSupported()` / `GlassAvailability`、`GlassStructuralLimits`（"独立 window 结构不允许"的代码真源） |
| `.../primitives/GlassBackdrop.kt` | 新建 | 背景捕获（`DrawScope.record` **扩展函数** + `drawLayer` 上屏）、`GlassBackdropState`（layer / origin / size / hasContent）、`LocalGlassBackdrop`、`GlassBackdrop { background } overlay={…}` 宿主 |
| `.../primitives/GlassSurface.kt` | 新建 | 玻璃绘制：逐边 outsets 裁背景 → `createChainEffect(shader, blur)` → AGSL v1（Snell 向内折射 / 宽差分法线+中线 fade / 比例色散 / 菲涅尔+方向性 Phong / 内阴影 / tint）；API/着色器失败 → A 不透明兜底 |
| `.../primitives/M5Sheet.kt` | 修改 | 头注释写明 `Dialog` 结构限制；新增 `M5GlassSheet`（in-window 玻璃 bottom sheet，含遮罩/G-18 点关/下滑关/返回键/spring 入场）与 `M5GlassDrawer`（in-window 玻璃抽屉，只圆右侧两角） |
| `.../primitives/M5Modal.kt` | 修改 | 仅注释：`Dialog` 独立 window → 结构不允许捕获 → 保持 A 不透明（不做半透明伪造） |
| `.../primitives/M5Lightbox.kt` | 修改 | 仅注释：同上，且灯箱语义=全屏黑底看图（D-75 不需要透出） |
| `.../primitives/M5GlassTest.kt` | 新建 | 最小验证入口（Activity + 页）：同一树内背景列表/高频条纹 + 玻璃 sheet/抽屉；intent extras 驱动主题/滚动/refract/band/blur/tint/disp/rim/shadow/光向/纯 blur/无遮罩 |
| `app-android/src/main/AndroidManifest.xml` | 修改 | 注册验证用 `M5GlassTestActivity`（不参与产品路径；可随时删） |
| `app-android/src/test/kotlin/dev/myco/android/ui/m5/primitives/GlassMaterialTest.kt` | 新建 | 9 项纯逻辑测试：D-73 token、API 33 门槛、软件渲染、着色器失败降级、独立 window 结构限制、四角半径取零 |
| `docs/reports/assets/emulator/p4-glass-*.png` | 新建 | 6 张产品形态截图 + 8 张验收证据图 |
| `docs/reports/assets/emulator/p4-glass-acc-verify.txt` | 新建 | 验收原始数字（本报告 §5 的来源） |
| `_ws/out/glass_verify.py` / `glass_shift.py` | 新建（工具） | 按玻璃 SDF 距离统计 diff（圆角处不会假阳性）；逐通道亚像素位移量测 |

> 未改：`MainActivity.kt`、`ChatScreen.kt`、`ui/m5/shell/**`、`ui/m5/chat/**`、`ui/m5/composer/**`、`GlassLab*`、`ui/m5/glass/**`（全部保持只读；与 glass-shaders 队友的文件无冲突）。

---

## 2. 实现结构

```
GlassBackdrop(Modifier.fillMaxSize()) {                 ← 背景捕获宿主（一个 Box）
    ChatContent()                                       ← 每帧：DrawScope.record 进源层 + drawLayer 上屏
} overlay = {
    M5GlassSheet(...) / M5GlassDrawer(...)              ← 同一 Compose 树内的浮层（能取到 LocalGlassBackdrop）
}
```

`GlassSurface` 每帧绘制流程（D-67 capture 主路径，单效果层）：

```
背景源层(source) ──裁"玻璃背后那块 + 逐边 outsets"──► effectLayer.record(...)
                                                        │  renderEffect = createChainEffect(shader, blur)
                                                        ▼
                                    shader( blur( captured backdrop ) )   ← API31 blur 内层 + API33 AGSL 外层
                                                        │
                translate(-outset) { drawLayer(effectLayer) } + drawRect(tint 4% 白)
                                                        │
                                                        ▼
                                        前景内容（文字/图标）保持锐利
```

- **坐标**：玻璃位置 = `onGloballyPositioned(positionInRoot)` + 调用方传入 `positionOffset`（父层若只用 `graphicsLayer` 位移，不传会滞后一帧；`M5GlassSheet/Drawer` 自带用 `Modifier.offset` 位移，故无需补偿）。
- **outsets（本轮的额外修复）**：模糊核在层边界外没有像素 → 需要外扩。但**外扩到捕获源层（屏幕）之外时，层里那圈是透明** → 纯 blur 档会把背后真实背景透出来（锐利条纹），shader 也采样到透明值。现按"玻璃边到源层边的可用距离"**逐边取 min**：贴屏幕边的方向不外扩，模糊在对齐边界上用 `CLAMP` 收口（= 真 backdrop 在屏幕边界的正确行为）。这也是 T1/T2 能拿到逐位 0 的前提。
- **形状单一真源**：`GlassCorners(topStart/topEnd/bottomEnd/bottomStart, shapeExponent)` 同时派生 `clip(shape)` 与 shader 的 `uRadii/uShapeN`；`shapeExponent=2` → `RoundedCornerShape`（当前产品）；`=4` 且四角相等 → `SuperellipseShape(exponent=4)`（设计系统目标）。调用方不单独传这两个值（G-21：裁剪轮廓必须 = 折射等距场轮廓）。
- **tint** 不进 shader（保持锐利），在玻璃层之后 `drawRect(Color.White α4%)`；**遮罩**通过 `backdropScrim` 在捕获层内重绘一次——`backdrop-filter` 语义下"面板之下已画过的所有层"都属于背景，遮罩在玻璃下面就必须算进背景，否则浅色主题下玻璃会亮成一块奶白板（实测踩过，见 `p4-glass-02/06`）。

### 2.1 API 降级策略（D-74 / G-21，三档之外没有第四档）

| 条件 | 行为 |
|---|---|
| API ≥ 33 + 硬件加速 + AGSL 编译成功 | **真玻璃**（`GlassAvailability.FULL`；logcat `M5Glass: status=FULL error=null`） |
| API < 33（含 31–32：有 blur 但无 `RuntimeShader`） | **A 不透明**（`fallbackColor` = 主题 surface）。**不做"裸 blur + tint"**（D-74：实测像磨砂面板、无边缘弯折，属 G-21 半成品近似） |
| 软件渲染（Compose Preview / 关闭硬件加速） | A 不透明（`RenderEffect` 被忽略，模糊不会产生） |
| 着色器构建失败（该 GPU/驱动不支持） | A 不透明 + `GlassDiagnostics.status=SHADER_FAILED` + error 记录 |
| 无 `GlassBackdrop` 祖先（独立 window / 没包宿主） | A 不透明（`LocalGlassBackdrop == null`，不伪造） |

---

## 3. 三个 shader 缺陷的修复（按 Lead 指令 + 实机新增第 4 个）

| # | 缺陷（v0 `AGSL_PHYSICS_GLASS`） | v1 修复 | 证据 |
|---|---|---|---|
| 1 | `base = coord + 外向法线·f·refractPx` **向外采样**；层被隐式裁到 bounds，最外圈只能 clamp 拉边 | 改为 **Snell + 棱镜偏折向内采样**：`R = eta·I − (eta·cosI + √k)·N`，`offset = R.xy/(−R.z)·thickness`，`thickness = refractPx / tan(θi−θr)`；永远不越界 | T3b 实测位移在 band 内归零、内区 0；`p4-glass-acc2/3` |
| 2 | 法线差分 `e=0.75px` 太窄 → 宽扁玻璃中线硬缝；色散是独立 `dispPx`（refract=0 时也会动） | `e = clamp(band·0.3, 1, 16)px` + `axisFade = smoothstep(0.15, 0.8, |∇d|)`；色散改为**与位移成正比**：`ca = offset·uDispersion`，R 采样 `base−ca` / B 采样 `base+ca` / G 基准 | T3a/T3b：R<G<B，且 4.75/7.85/11.00 与公式预测 4.725/7.875/11.025 一致 |
| 3 | 两个 `graphicsLayer{renderEffect}`（blur 层 + shader 层）= 两个离屏 pass | **单条 chain**：`RenderEffect.createChainEffect(shaderEffect, blurEffect)`（javadoc 语义 `outer(inner(source))` = `shader(blur(content))`）；SDF 与 clip 同曲线（`uShapeN` 由 `GlassCorners` 单一真源派生） | T1：直通档与纯 blur **逐位一致**（玻璃区 0 变化），证明 chain 顺序/坐标/绑定名正确 |
| 4 | **（本轮实机新增）** outsets 越过捕获源层 → 层边缘透明：纯 blur 档把背后真实背景（锐利条纹）透出来，shader 采样到透明值；A/B diff 被污染成"整个边缘几十 px 都在变" | 逐边 outsets 取 `min(需求, 玻璃边到源层边的距离)`：贴屏幕边不外扩，`CLAMP` 收口 | 修复前 A/B 内区逐位不同 127k/1.02M；修复后 **0**（见 §5 T2） |

关于候选 shader 的两处**有意偏离**（都已写进代码注释）：
1. 增加 `uCenter` / `uHalfSize`：候选假设"玻璃体 = 整个层"；产品实现给模糊核留了 outsets，层比玻璃体大，玻璃矩形不居中，必须显式给中心与半尺寸。
2. 高光增加一项**方向性 Phong 镜面** `pow(max(dot(n2,L),0),3)·bandMask`：候选的 `rimLine` 是几何边亮线（刻意不随光向），只靠内阴影体现光向；验收「光向旋转 90° → 边缘高光沿边移动」需要高光本身有方向性。该式仍是 `N·L` 物理量（不是贴图/描边）。

---

## 4. 产品形态截图（`docs/reports/assets/emulator/`）

| 文件 | 看什么 |
|---|---|
| `p4-glass-01-dark-sheet.png` | 深色 + 玻璃 bottom sheet：背景真实模糊、top 圆角、4% tint、内容锐利 |
| `p4-glass-02-light-sheet.png` | 浅色同款（验证"遮罩算进 backdrop"后不再发白；对侧 scrim 0.32 仍可见） |
| `p4-glass-03-dark-sheet-scroll0.png` / `p4-glass-04-dark-sheet-scroll8.png` | 背景列表滚到第 0 / 第 8 项 + 40px：玻璃里模糊内容随之不同（证明取的是实时背景） |
| `p4-glass-05-dark-drawer.png` / `p4-glass-06-light-drawer.png` | 玻璃抽屉（只圆右侧两角，左边缘与屏幕齐平；右侧 16% 露出 scrim 后的背景） |

> 说明：验证页是临时入口（`--ez m5_glass` 家族之外的独立 Activity），页面里的背景列表/模型选项都是固定假数据；产品接线由 Lead 在 ChatScreen/Shell 里用 `GlassBackdrop` + `M5GlassSheet/M5GlassDrawer`（或直接 `GlassSurface`）完成。

---

## 5. 四项验收结果（全部在模拟器实测）

原始数字：`docs/reports/assets/emulator/p4-glass-acc-verify.txt`；判定脚本 `_ws/out/glass_verify.py`（按玻璃 SDF 距离统计，圆角处不做矩形内缩假阳性）。
固定条件：玻璃体 bbox = x 0..1079, y 1359..2399（1080×1041）；density 2.625；`blur 24dp=63px`、`band 6dp=15.75px`、`refract 3dp=7.875px`。A/B 档关闭 rim/innerShadow/色散以隔离"纯折射/纯色散"。

### T1 直通等价（chain 顺序/坐标/绑定名）✅

- A：`debugPureBlur=true`（只挂 blur，不挂 shader）；B：`refract=0, rim=0, shadow=0`（shader 直通分支）。
- **玻璃区域逐位不同像素 0 / 1,124,280，maxDiff = 0**；全图仅 44 px 不同，全部落在状态栏电池/信号图标（y=47..79, x=933..965，两次启动的抗锯齿差异）。
- 证据：`p4-glass-acc1-pureblur.png` / `p4-glass-acc1-passthrough.png`。

### T2 折射 A/B：`refract=0` vs `3dp`（边缘-only、内区逐位不变）✅

- 背景用 8px 高频条纹（保证四边都有对比度，才能检验"变化外接框 = 玻璃体本身"）。
- 变化像素（>2）**16,346 = 比较区域 1.26%（全图 0.63%、玻璃体 1.45%）**；
- 变化外接框 **x=2..1077 y=1366..2397（1076×1032）≈ 玻璃体本身**（差 2px 是 clip 抗锯齿边）；
- 变化像素到玻璃边界的距离 **max 11.0px，100.000% 在 band(15.75)+1px 以内**；
- **内区（SDF dist > band+maxDisp+2 = 25.62px）：逐位不同 0 / 1,017,912，maxDiff = 0**；max 单通道差 9（模糊内容梯度缓，7.9px 位移只有个位数差）。
- 对照（产品列表背景，同参数）：变化 2,258 px = 玻璃体 0.20%；内区 0 / maxDiff 0。
- 证据：`p4-glass-acc2-refract0.png` / `-refract3.png` / `-diff-edgeonly.png`。
- > 基线对比：glass-lab §12 的 physics 场景是 0.68% / 内区 0（那块玻璃面积占比小、背景是色块）；本次画面更大、条纹对比更强，**判据（边缘-only + 内区逐位 0）一致达标**。

### T3 色散：只在带内、内区 0、R<G<B（正常色散）✅

- T3a（`blur=0, band=6dp`，disp token 0 → 0.4px，ratio 0.05）：变化 2,271 px = 0.18%；外接框 = 玻璃体；到边界距离 max 15.0px（≤ band+1）；**内区逐位不同 0 / 1,017,912，maxDiff=0**。
  证据：`p4-glass-acc3-disp0.png` / `-disp.png` / `-diff-bandonly.png`。
- T3b（探针：fine-stripe、blur=0、band=40dp=105px、disp token 3.15px→ratio 0.4；与"无玻璃背景"做逐通道一维互相关，亚像素）：
  - 平台段（x=5..20px）：**R=4.75、G=7.85、B=11.00 px**；全部 11 个采样位置均 **R<G<B**；x≈100px（= band 105px）归零；
  - 理论校验：`offset=7.875`（= `uMaxDisp` 上限），ratio 0.4 → 预测 R=4.725 / B=11.025，实测 4.75 / 11.00（误差 <0.05px）。

### T4 光向旋转 90°：边缘高光/内阴影沿边移动 ✅

- A：默认光向 (-0.707,-0.707)（左上主光，rim 35% / innerShadow 25%）；B：光向旋转 90°。
- 亮度变化 ΔL（B−A，正=变亮）：**左边带 −5.10 / 右边带 +4.94 / 顶边带 −0.01 / 底边带 −0.00**；
- 变化像素（|ΔL|>1）29,070；外接框 = 玻璃体；到边界距离 max 14.6px（≤ band+1）100% 在 band 内；**内区逐位不同 0 / 1,017,912，maxDiff=0**。
- → 高光从"上+左"转到"上+右"（左暗右亮、上下不变），证明高光由**法线 N·L** 算出而非贴图/等宽描边。
- 证据：`p4-glass-acc4-light-default.png` / `-rot90.png` / `-diff-rotate.png`（红=变亮、蓝=变暗：左蓝右红）。

---

## 6. 结构限制与已知缺口

### 6.1 结构不允许（G-21，不做近似）

| 位置 | 原因 | 处置 |
|---|---|---|
| `M5Sheet`（现有） | `Dialog` = 独立 window；捕获层只能在同一个 Compose 树/同一个 window 内取背景 | **保持 A 不透明**（`colors.surface`），不做半透明+blur 伪造；玻璃形态用同一树内的 `M5GlassSheet`（Lead 接线） |
| `M5Modal`（现有） | 同上 | 保持 A 不透明；若产品要"居中模态玻璃"，需改成 in-window overlay（焦点陷阱/返回键/遮罩命中要自己补）→ **待 Lead 裁决** |
| `M5Lightbox`（现有） | 同上 + 灯箱语义是全屏黑底看图（不需要透出） | 保持不透明黑底（D-75） |
| 行内菜单/`Popup`（D-51） | 独立 window，同 6.1-1 | 不本轮处理 |
| 真 3D 折射/镜头内放大/环境反射 | 只有一张 2D 背景纹理，无深度/第二视角/环境图（`liquid-glass-shaders §5.1`） | **结构不允许**，只做边缘带位移（法线×距离）；不引入深度/贴图近似 |
| 连续光谱色散 | 只有 RGB 三波段采样 | 三波段近似（R/G/B），公式来自正常色散 |

### 6.2 已知缺口 / 待真机确认（模拟器不能替代）

1. **性能**：只做了 host GPU 模拟器的功能验证，**未做帧时间/GPU 内存的真实测量**（模拟器数值无意义）。浮层场景预期便宜（背景静止、1 块玻璃 = 1 次捕获 + 1 条 chain），但需要真机 Macrobenchmark（P90 frame duration / frameOverrunMs），阈值见 `liquid-glass-shaders §6.3`。
2. **API 31/32 的"陈旧模糊"（Haze #77）不适用**：D-74 下 31/32 根本不启用玻璃；33+ 未复现冻结问题（本机 API 35）。
3. **低端 GPU 的 AGSL 编译成功率与半径上限**：本机 `FULL`；其他机型需 `GlassDiagnostics.status/error` + 降级验证（失败 → A 不透明，不崩）。
4. **视频/`AndroidView(SurfaceView/TextureView)` 背景**：捕获层能否拿到未验证（`liquid-glass-shaders §5.2-8`）→ 真机上该区域可能黑块/旧帧；若出现需该位置降级 A。
5. **动画中捕获坐标**：`M5GlassSheet/Drawer` 用 `Modifier.offset` 位移（placement 变化会派发 `onGloballyPositioned`），动画中跟手；若 Lead 用 `PanelHost`（父层 `graphicsLayer.translationX` 位移），**必须**把同一位移传 `GlassSurface(positionOffset=…)`，否则转场中玻璃会滞后一帧（停稳后准确）。
6. **性能优化未做**：`inputScale` 降采样（Haze 收益 5–14%）、隔帧更新、静态快照缓存均未启用——浮层场景够用，真机超标时再加（参数已在 `liquid-glass-shaders §6` 给出）。
7. **色散强度口径**：见 §7-1。
8. **RTL**：`GlassCorners` 的语义是 LTR 的物理角；RTL 下 `RoundedCornerShape(topStart…)` 会镜像而 shader 的 `uRadii` 不镜像。当前产品语言中文 LTR；若支持 RTL 需在调用侧镜像传参（未做，记缺口）。

---

## 7. 待 Lead 裁决

1. **D-73 的 `dispersion 1px` 在 v1 比例色散下的语义**：实现按"物理绝对位移"换算 `uDispersion = dispersionPx/refractPx`（3dp@2.625 → 0.127）。但社区参考（RN 1.8%、design-systems）认为 1.8%–5% 才是"几乎看不见的色边"，`liquid-glass-shaders §4.4` 明确提示 0.127 偏大、建议先 0.018–0.05 试观感。当前产品截图里色边不明显（模糊把 1px 级分离吃掉了），但换成高对比背景/小 blur 时会露出来。**建议把 token 改成"相对位移比例"（如 0.02–0.05）并更新 D-73**；本轮未擅自改 token。
2. **`uSat`（饱和度补偿）**：候选给 1.05，本轮固定 **1.0**——因为 G-21 的 A/B 验收要求内区**逐位不变**，任何 sat≠1 都会改内区像素。若产品要"透射更艳"，需要同意"内区不再逐位相等"的验收口径。
3. **modal 玻璃**：`M5Modal` 现为独立 window 结构不允许；是否值得改成 in-window overlay（要重做焦点陷阱/返回键/遮罩命中）请裁决。
4. **遮罩纳入 backdrop**（本轮已实现）：玻璃会显示出"被 scrim 压暗后的背景"，这与 Web `backdrop-filter`（遮罩在面板下面、属于 backdrop）一致，但比"只糊背景"更暗。请确认产品要这个口径（本轮截图已按此出图）。
5. **验证 Activity 去留**：`M5GlassTestActivity` 只用于验收/截图（manifest 里 `exported=true`，不参与产品路径）；Lead 接线完可删，或留作玻璃回归页。是否保留请裁决。
6. **接线形态**：抽屉/附件 sheet 改走 `M5GlassSheet/M5GlassDrawer` 还是 Lead 直接把 `GlassSurface` 放进 `PanelHost` 的 panel 内容（后者能复用 PanelHost 的栈/手势/转场）？两种都已支持；若走 PanelHost，请记得传 `positionOffset`。

---

## 8. 复现命令（模拟器）

```powershell
# 构建 + 安装
& 'E:\11-15\tools\gradle-8.9\bin\gradle.bat' -p E:\myco-chat :app-android:assembleDebug --console=plain -q
adb -s emulator-5554 install -r app-android\build\outputs\apk\debug\app-android-debug.apk

# 产品形态
adb shell am start -n dev.myco.android/.ui.m5.primitives.M5GlassTestActivity --ez glass_dark true  --es glass_overlay sheet
adb shell am start -n dev.myco.android/.ui.m5.primitives.M5GlassTestActivity --ez glass_dark false --es glass_overlay drawer

# T2 折射 A/B（证据图）
adb shell am start -n ...M5GlassTestActivity --ez glass_dark true --es glass_overlay sheet --es glass_bg stripes --ef glass_refract 0 --ef glass_rim 0 --ef glass_shadow 0 --ef glass_disp 0
adb shell am start -n ...M5GlassTestActivity --ez glass_dark true --es glass_overlay sheet --es glass_bg stripes --ef glass_refract 3 --ef glass_rim 0 --ef glass_shadow 0 --ef glass_disp 0
python _ws\out\glass_verify.py A.png B.png --rect 0,1359,1080,2400 --radii 68,68,0,0 --band 15.75 --maxdisp 7.875 --inset 60

# T4 光向旋转
... --ef glass_refract 3 --ef glass_disp 0
... --ef glass_refract 3 --ef glass_disp 0 --ef glass_light_deg 90

# 诊断：AGSL 是否真的生效
adb logcat -d -s M5Glass     # 期望 status=FULL error=null sdk=35
```
