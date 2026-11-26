# M5.0 视觉技术验证（画廊）报告

> 日期：2026-11-26 · 状态：已上真机（7e7c5a68 / Android 15）· 入口：`adb shell am start -n dev.myco.android/.MainActivity --ez m5_gallery true`
> 设计契约：[90-m5-visual-system.md](../design/90-m5-visual-system.md)；画廊不是产品页面，只用于确认视觉语言。

## 1. 交付物

| 文件 | 内容 |
|---|---|
| `ui/m5/Superellipse.kt` | Lamé 超椭圆 `Shape`：只算 90° 角弧、6–24 段自适应采样、Path 按尺寸 LRU 缓存、等价性实现 |
| `ui/m5/Tokens.kt` | M5Palette（浅/深）、LightRig、4dp 间距、同心圆角、中文 6 阶字阶、settle/glide/snap 物理 |
| `ui/m5/NeoSurface.kt` | Raised / Inset / Float 三种新拟物表面；双/三层 BlurMaskFilter 阴影；暗色换色不反相 |
| `ui/m5/Glass.kt` | 液态玻璃 L0：tint + 内缘柔光 + 1.2dp 渐变菲涅尔边 + 镜面高光 + 投影；强度可调 |
| `ui/m5/Icons.kt` | 19 个 24dp/1.6dp 线性图标（Canvas 自绘，不引图标库），全部可 tint |
| `ui/m5/Gallery.kt` | 画廊页：形状 / 材质 / 玻璃 / 光照 / 图标 / 动效 / 输入区样板 七节 |
| `MainActivity.kt` | `--ez m5_gallery true` 进入画廊（不进产品导航） |

## 2. 技术结论（可直接指导 M5.1）

### 2.1 超椭圆可行，且应作为唯一圆角
- 只画 4 个 90° 角弧 + 直边，采样数随半径自适应（6–24）；56–300dp 尺寸下肉眼无棱。
- Path 缓存命中后 `createOutline` 无数学开销；clip/背景/阴影共用同一 Outline。
- n=4 的观感与 n=2（普通圆角）差异在 20dp 以上半径才明显；建议全局默认 n=4.0，胶囊用 `r = 高/2` 的超椭圆。
- **结论**：M5.1 删除 `RoundedCornerShape` 依赖可行。

### 2.2 新拟物阴影可用 API 26+ 的 BlurMaskFilter
- 不依赖 `RenderEffect`（API31+），`android.graphics.BlurMaskFilter` 在 minSdk 26 上即可绘制。
- 结构：宽柔影（offset×1.75 模糊）+ 紧影（offset×0.65）叠出"浮雕感"；亮影左上、暗影右下。
- offset 由半径推导并 clamp（3–13dp），避免小控件阴影过散 / 大面板阴影过脏。
- **暗色模式**：结构面用**中性光**（灰白高光），暖光 `#E09A42` 只出现在玻璃边缘——符合"暗色光照仅照亮液态玻璃"。
- **结论**：可用；M5.1 需要按尺寸缓存阴影位图（现在每次 draw 都走 blur，是 90th 18ms 的主因）。

### 2.3 液态玻璃 L0 已有一等观感，L1/L2 仍需单独验证
- L0（tint + 菲涅尔 + 高光 + 投影）在全版本可用，暗色下暖边 + 高光已经"像玻璃"。
- 明确不做的事：不引第三方模糊库；真背景模糊（L1）与 AGSL 折射（L2）留到 M5.1 评估，因为：
  - `RenderEffect`/`Modifier.blur` 只能模糊自身图层，不能模糊背后内容；
  - 真 backdrop 只有 Android 17 QPR2 实验 API 或 Haze 的自捕获管线；
  - Haze 需版本兼容评估（当前 Compose 1.7 / BOM 2024.10.01）。
- 玻璃面数量：画廊里单屏 4–5 个已开始有 GPU 压力，正式页面严格 ≤3。

### 2.4 动效物理已验证
- 拖动 1:1 跟手、松手把 `VelocityTracker`/draggable 末速度传给 spring：demo 里可见"松手自己滑到槽位"，没有 tween 停顿。
- **结论**：`PredictiveNavState.exit()` 内部换成同款物理即可，API 不变。

### 2.5 图标方案
- Canvas 自绘 + 24 网格 + `withTransform(scale)`，一份坐标系两端通用；19 个图标零依赖、可 tint、无 emoji。
- 现有页面里的 `☰ ‹ ▸ ▾ 🎤 ＋` 可在 M5.3 逐屏替换。

## 3. 真机性能（画廊页，滚动 6 次）

```
Total frames 484 · Janky 14 (2.89%)
50th 12ms / 90th 18ms / 99th 34ms
GPU 50th 5ms / 90th 9ms
```
- 结论：40+ 个带模糊阴影的 Surface 下仍可接受，但 90th 已超 60fps 预算。
- M5.1 必做：阴影尺寸缓存（RenderNode/bitmap）、列表行禁用浮雕、单屏模糊面 ≤8。

## 4. 已知待调（给用户拍板）

1. **暗色结构面浮雕对比偏弱**：暗底上暗影不可见，只剩亮影；可加极细口径顶光或提高表面明度差。
2. **浅色粉气泡偏饱和**：当前用 `#EFB9B3`（比色卡 `#FFD7D2` 深），是为可读性；可回色卡原值。
3. **玻璃 L0 深色透射偏"清"**：可加一层背景色 transmission（非纯 tint）。
4. **n 值**：n=4.0 自然；要更"方"可用 5–6，胶囊反之。

## 5. 下一步

- M5.1：tokens/primitives 全量 + 阴影缓存 + 设置/关于里的画廊入口；
- M5.3 样板：聊天页（顶栏/消息节奏/输入区浮岛）先做一屏，确认后铺开。

---

## 6. 2026-11-27 第二轮调整（用户反馈后）

1. **彩色投影**：`NeoSurface` 的影/亮影改为按面板自身色的饱和度混合——粉气泡=粉影、绿按钮=橄榄影，中性表面仍是派生中性影。暗色下彩色高光降到约 1/4，避免"发光按钮"违反"暗色光照只给玻璃"。
2. **图标均衡**：停止图标放大（6→7 单位）、记忆从尖锐四角星改为曲线 sparkle、归档重排（盖 3.6 高 + 体 8.8~19.2 + 手柄居中）、返回/展开/进入/删除整体光学居中；更多圆点 1.6→1.9。
3. **超椭圆按压**：自定义 press 指示——外阴影随按下收缩、内阴影渐入（凸起被按凹），release 用 spring(0.78) 回弹；波纹按触摸点扩散并裁切到超椭圆（`indication = null`，不再有矩形 raipple）。
4. **凸起优先**：分段控件、图标底、输入区附件/上下文胶囊全部改为 Raised；Inset 只保留给搜索输入、槽位/轨道等"凹陷语义"。
5. 真机验证：长按"发送"可看到按凹 + 波纹；像素采样确认粉气泡投影偏暖（#D5CCC6）、绿按钮投影偏绿（#D6DDC9）。
---

## 7. 2026-11-27 第三轮调整

1. **玻璃按钮按压**：`GlassSurface` 加 `onClick`，与 NeoSurface 共用超椭圆扩散波纹；按下时边缘菲涅尔变亮、镜面高光增强、投影收缩。
2. **同级阴影互不遮挡**：新增 `NeoSiblings(gap)` + `LocalNeoShadowBudget`——组内阴影最大外扩 = gap/2，按需缩放 offset/blur。画廊里分段键、图标底、工具行、节卡片都套上；间隙同时加大到 ≥10dp。
3. **输入区按钮统一高度**：附件/模型胶囊/深度思考/发送全部 40dp，`NeoSurface` 新增 `contentAlignment` 使胶囊内容垂直居中。
4. **玻璃认知对齐**：用户确认当前 L0 只是"描边发光"，真正质感要等折射（L2）——暂不追加近似，继续按"其他风格 + 布局"验证推进。